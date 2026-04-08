package com.linkplatform.api.owner.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class WebhookSigningService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ObjectMapper objectMapper;

    public WebhookSigningService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedSecret generateSecret() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        String plaintext = "whs_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(plaintext);
        return new GeneratedSecret(plaintext, hash.substring(0, Math.min(hash.length(), 12)), hash);
    }

    public DeliverySignature sign(
            String secretHash,
            int eventVersion,
            WebhookEventType eventType,
            String deliveryId,
            String workspaceSlug,
            JsonNode payload,
            OffsetDateTime occurredAt) {
        try {
            String timestamp = TIMESTAMP_FORMATTER.format(occurredAt.withOffsetSameInstant(ZoneOffset.UTC));
            String canonicalJson = objectMapper.writeValueAsString(payload);
            String signatureInput = timestamp + "." + canonicalJson;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretHash.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8)));
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-LinkPlatform-Signature", signature);
            headers.put("X-LinkPlatform-Timestamp", timestamp);
            headers.put("X-LinkPlatform-Event", eventType.value());
            headers.put("X-LinkPlatform-Event-Version", Integer.toString(eventVersion));
            headers.put("X-LinkPlatform-Delivery-Id", deliveryId);
            headers.put("X-LinkPlatform-Workspace-Slug", workspaceSlug);
            return new DeliverySignature(signature, timestamp, canonicalJson, headers);
        } catch (Exception exception) {
            throw new IllegalStateException("Webhook payload could not be signed", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte part : hash) {
                builder.append(String.format("%02x", part));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record GeneratedSecret(String plaintext, String prefix, String hash) {
    }

    public record DeliverySignature(String signature, String timestamp, String canonicalJson, Map<String, String> headers) {
    }
}
