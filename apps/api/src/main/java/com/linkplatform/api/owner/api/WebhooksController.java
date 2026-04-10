package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.WebhookEventType;
import com.linkplatform.api.owner.application.WebhookSubscriptionsService;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces/current/webhooks")
public class WebhooksController {

    private final OwnerAccessService ownerAccessService;
    private final WebhookSubscriptionsService webhookSubscriptionsService;

    public WebhooksController(
            OwnerAccessService ownerAccessService,
            WebhookSubscriptionsService webhookSubscriptionsService) {
        this.ownerAccessService = ownerAccessService;
        this.webhookSubscriptionsService = webhookSubscriptionsService;
    }

    @GetMapping
    public java.util.List<WebhookSubscriptionResponse> list(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_READ);
        return webhookSubscriptionsService.listSubscriptions(context).stream().map(WebhookSubscriptionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedWebhookSubscriptionResponse create(
            @RequestBody CreateWebhookSubscriptionRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        WebhookSubscriptionsService.CreatedSubscription created = webhookSubscriptionsService.createSubscription(
                context,
                requestBody == null ? null : requestBody.name(),
                requestBody == null ? null : requestBody.callbackUrl(),
                parseEventTypes(requestBody == null ? null : requestBody.eventTypes()),
                requestBody != null && Boolean.TRUE.equals(requestBody.enabled()));
        return new CreatedWebhookSubscriptionResponse(
                WebhookSubscriptionResponse.from(created.record()),
                created.plaintextSecret(),
                created.record().signingSecretPrefix());
    }

    @PatchMapping("/{subscriptionId}")
    public WebhookSubscriptionResponse update(
            @PathVariable long subscriptionId,
            @RequestBody UpdateWebhookSubscriptionRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        return WebhookSubscriptionResponse.from(webhookSubscriptionsService.updateSubscription(
                context,
                subscriptionId,
                requestBody == null ? null : requestBody.name(),
                requestBody == null ? null : requestBody.enabled(),
                parseEventTypes(requestBody == null ? null : requestBody.eventTypes())));
    }

    @PostMapping("/{subscriptionId}/rotate-secret")
    public CreatedWebhookSubscriptionResponse rotateSecret(
            @PathVariable long subscriptionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        WebhookSubscriptionsService.CreatedSubscription rotated = webhookSubscriptionsService.rotateSecret(context, subscriptionId);
        return new CreatedWebhookSubscriptionResponse(
                WebhookSubscriptionResponse.from(rotated.record()),
                rotated.plaintextSecret(),
                rotated.record().signingSecretPrefix());
    }

    @PostMapping("/{subscriptionId}/verify")
    public ResponseEntity<WebhookSubscriptionHealthResponse> verify(
            @PathVariable long subscriptionId,
            @RequestBody(required = false) VerifyWebhookSubscriptionRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        WebhookSubscriptionsService.VerificationAttempt attempt = webhookSubscriptionsService.verifySubscription(context, subscriptionId);
        ResponseEntity.BodyBuilder builder = attempt.verified() ? ResponseEntity.ok() : ResponseEntity.status(HttpStatus.CONFLICT);
        builder.header("X-LinkPlatform-Verification-Delivery-Id", Long.toString(attempt.deliveryId()));
        if (attempt.httpStatus() != null) {
            builder.header("X-LinkPlatform-Verification-Http-Status", Integer.toString(attempt.httpStatus()));
        }
        if (attempt.failureReason() != null) {
            builder.header("X-LinkPlatform-Verification-Failure", attempt.failureReason());
        }
        return builder.body(WebhookSubscriptionHealthResponse.from(attempt.record()));
    }

    @PostMapping("/{subscriptionId}/test-fire")
    public TestWebhookSubscriptionResponse testFire(
            @PathVariable long subscriptionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        var delivery = webhookSubscriptionsService.testFireSubscription(context, subscriptionId);
        int eventVersion = delivery.payload().path("eventVersion").asInt(WebhookEventType.CURRENT_EVENT_VERSION);
        return new TestWebhookSubscriptionResponse(delivery.id(), delivery.createdAt(), eventVersion);
    }

    @GetMapping("/{subscriptionId}/health")
    public WebhookSubscriptionHealthResponse health(
            @PathVariable long subscriptionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_READ);
        return WebhookSubscriptionHealthResponse.from(webhookSubscriptionsService.subscriptionHealth(context, subscriptionId));
    }

    @GetMapping("/{subscriptionId}/deliveries")
    public WebhookDeliveryPageResponse deliveries(
            @PathVariable long subscriptionId,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeRead(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_READ);
        return new WebhookDeliveryPageResponse(
                webhookSubscriptionsService.listDeliveries(context, subscriptionId).stream().map(WebhookDeliveryResponse::from).toList());
    }

    @PostMapping("/{subscriptionId}/deliveries/{deliveryId}/replay")
    public WebhookDeliveryResponse replay(
            @PathVariable long subscriptionId,
            @PathVariable long deliveryId,
            @RequestBody(required = false) ReplayWebhookDeliveryRequest requestBody,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest request) {
        WorkspaceAccessContext context = ownerAccessService.authorizeMutation(
                apiKey, authorizationHeader, workspaceSlug, request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), ApiKeyScope.WEBHOOKS_WRITE);
        return WebhookDeliveryResponse.from(webhookSubscriptionsService.replayDelivery(context, subscriptionId, deliveryId));
    }

    private Set<WebhookEventType> parseEventTypes(java.util.List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream().map(WebhookEventType::fromValue).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
