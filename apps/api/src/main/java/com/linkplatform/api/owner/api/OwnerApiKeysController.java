package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyLifecycleService;
import com.linkplatform.api.owner.application.AuthenticatedOwner;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.OwnerApiKeyRecord;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/api-keys")
public class OwnerApiKeysController {

    private final OwnerAccessService ownerAccessService;
    private final ApiKeyLifecycleService apiKeyLifecycleService;
    private final Clock clock;

    public OwnerApiKeysController(
            OwnerAccessService ownerAccessService,
            ApiKeyLifecycleService apiKeyLifecycleService) {
        this.ownerAccessService = ownerAccessService;
        this.apiKeyLifecycleService = apiKeyLifecycleService;
        this.clock = Clock.systemUTC();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedApiKeyResponse create(
            @RequestBody CreateApiKeyRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest servletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                servletRequest.getRemoteAddr());
        ApiKeyLifecycleService.CreatedApiKey created = apiKeyLifecycleService.createKey(
                owner,
                request == null ? null : request.label(),
                request == null ? null : request.expiresAt(),
                owner.ownerKey());
        return new CreatedApiKeyResponse(toResponse(created.record()), created.plaintextKey());
    }

    @GetMapping
    public List<OwnerApiKeyResponse> list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest servletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeRead(
                apiKey,
                authorizationHeader,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                servletRequest.getRemoteAddr());
        return apiKeyLifecycleService.listKeys(owner.id()).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping("/{keyId}/rotate")
    public CreatedApiKeyResponse rotate(
            @PathVariable long keyId,
            @RequestBody(required = false) RotateApiKeyRequest request,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest servletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                servletRequest.getRemoteAddr());
        ApiKeyLifecycleService.CreatedApiKey rotated = apiKeyLifecycleService.rotate(
                owner,
                keyId,
                request == null ? null : request.expiresAt(),
                owner.ownerKey());
        return new CreatedApiKeyResponse(toResponse(rotated.record()), rotated.plaintextKey());
    }

    @DeleteMapping("/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @PathVariable long keyId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            HttpServletRequest servletRequest) {
        AuthenticatedOwner owner = ownerAccessService.authorizeMutation(
                apiKey,
                authorizationHeader,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                servletRequest.getRemoteAddr());
        apiKeyLifecycleService.revoke(owner, keyId, owner.ownerKey());
    }

    private OwnerApiKeyResponse toResponse(OwnerApiKeyRecord record) {
        return new OwnerApiKeyResponse(
                record.id(),
                record.keyPrefix(),
                record.label(),
                record.createdAt(),
                record.lastUsedAt(),
                record.revokedAt(),
                record.expiresAt(),
                record.activeAt(OffsetDateTime.now(clock)));
    }
}
