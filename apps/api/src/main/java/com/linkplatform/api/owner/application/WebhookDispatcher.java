package com.linkplatform.api.owner.application;

import com.linkplatform.api.runtime.LinkPlatformRuntimeProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class WebhookDispatcher {

    private final HttpClient httpClient;
    private final WebhookSigningService webhookSigningService;
    private final LinkPlatformRuntimeProperties runtimeProperties;

    public WebhookDispatcher(
            WebhookSigningService webhookSigningService,
            LinkPlatformRuntimeProperties runtimeProperties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(runtimeProperties.getWebhooks().getConnectTimeoutSeconds()))
                .build();
        this.webhookSigningService = webhookSigningService;
        this.runtimeProperties = runtimeProperties;
    }

    public DispatchResult dispatch(WebhookDeliveryStore.DispatchItem item) {
        try {
            OffsetDateTime occurredAt = OffsetDateTime.now();
            WebhookSigningService.DeliverySignature signature = webhookSigningService.sign(
                    item.subscription().signingSecretHash(),
                    item.subscription().eventVersion(),
                    item.delivery().eventType(),
                    Long.toString(item.delivery().id()),
                    item.delivery().workspaceSlug(),
                    item.delivery().payload(),
                    occurredAt);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(item.subscription().callbackUrl()))
                    .timeout(Duration.ofSeconds(runtimeProperties.getWebhooks().getRequestTimeoutSeconds()))
                    .POST(HttpRequest.BodyPublishers.ofString(signature.canonicalJson()));
            signature.headers().forEach(requestBuilder::header);
            requestBuilder.header("Content-Type", "application/json");
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new DispatchResult(response.statusCode(), response.body(), null);
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new DispatchResult(null, null, exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    public record DispatchResult(Integer httpStatus, String responseBody, String transportError) {
    }
}
