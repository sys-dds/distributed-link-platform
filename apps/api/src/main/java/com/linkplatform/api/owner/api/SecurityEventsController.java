package com.linkplatform.api.owner.api;

import com.linkplatform.api.owner.application.ApiKeyScope;
import com.linkplatform.api.owner.application.OwnerAccessService;
import com.linkplatform.api.owner.application.SecurityEventQuery;
import com.linkplatform.api.owner.application.SecurityEventRecord;
import com.linkplatform.api.owner.application.SecurityEventStore;
import com.linkplatform.api.owner.application.SecurityEventType;
import com.linkplatform.api.owner.application.WorkspaceAccessContext;
import com.linkplatform.api.runtime.ConditionalOnRuntimeModes;
import com.linkplatform.api.runtime.RuntimeMode;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/owner/security-events")
@ConditionalOnRuntimeModes({RuntimeMode.ALL, RuntimeMode.CONTROL_PLANE_API})
public class SecurityEventsController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final OwnerAccessService ownerAccessService;
    private final SecurityEventStore securityEventStore;

    public SecurityEventsController(
            OwnerAccessService ownerAccessService,
            SecurityEventStore securityEventStore) {
        this.ownerAccessService = ownerAccessService;
        this.securityEventStore = securityEventStore;
    }

    @GetMapping
    public SecurityEventPageResponse list(
            @RequestParam(name = "type", required = false) List<String> typeValues,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit,
            @RequestParam(required = false) String cursor,
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @RequestHeader(value = "X-Workspace-Slug", required = false) String workspaceSlug,
            HttpServletRequest httpServletRequest) {
        validateLimit(limit);
        WorkspaceAccessContext context = ownerAccessService.authorizeReadAny(
                apiKey,
                authorizationHeader,
                workspaceSlug,
                httpServletRequest.getMethod(),
                httpServletRequest.getRequestURI(),
                httpServletRequest.getRemoteAddr(),
                java.util.Set.of(ApiKeyScope.OPS_READ));
        SecurityEventQuery query = new SecurityEventQuery(
                parseTypes(typeValues),
                parseSince(since),
                limit,
                cursor);
        List<SecurityEventRecord> fetchedItems = securityEventStore.findEvents(context.workspaceId(), query);
        boolean hasMore = fetchedItems.size() > limit;
        List<SecurityEventRecord> pageItems = hasMore ? fetchedItems.subList(0, limit) : fetchedItems;
        String nextCursor = hasMore && !pageItems.isEmpty() ? encodeCursor(pageItems.getLast()) : null;
        return new SecurityEventPageResponse(
                pageItems.stream()
                        .map(item -> new SecurityEventResponse(
                                item.id(),
                                item.type().name().toLowerCase(Locale.ROOT),
                                item.occurredAt(),
                                item.summary(),
                                item.metadata()))
                        .toList(),
                nextCursor,
                limit);
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private List<SecurityEventType> parseTypes(List<String> typeValues) {
        if (typeValues == null || typeValues.isEmpty()) {
            return List.of();
        }
        List<SecurityEventType> parsed = new ArrayList<>();
        for (String typeValue : typeValues) {
            if (typeValue == null || typeValue.isBlank()) {
                continue;
            }
            for (String token : typeValue.split(",")) {
                String normalized = token.trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                try {
                    parsed.add(SecurityEventType.valueOf(normalized.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Type must be a valid security event type");
                }
            }
        }
        return parsed.stream().distinct().toList();
    }

    private OffsetDateTime parseSince(String since) {
        if (since == null || since.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(since.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("since must be a valid ISO-8601 timestamp");
        }
    }

    private String encodeCursor(SecurityEventRecord item) {
        String value = item.occurredAt() + "|" + item.id();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
