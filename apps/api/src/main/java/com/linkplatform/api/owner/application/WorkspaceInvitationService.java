package com.linkplatform.api.owner.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WorkspaceInvitationStore workspaceInvitationStore;
    private final WorkspaceStore workspaceStore;
    private final SecurityEventStore securityEventStore;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final int expiryDays;

    public WorkspaceInvitationService(
            WorkspaceInvitationStore workspaceInvitationStore,
            WorkspaceStore workspaceStore,
            SecurityEventStore securityEventStore,
            JdbcTemplate jdbcTemplate,
            @Value("${link-platform.workspaces.invitation-expiry-days:7}") int expiryDays) {
        this.workspaceInvitationStore = workspaceInvitationStore;
        this.workspaceStore = workspaceStore;
        this.securityEventStore = securityEventStore;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = Clock.systemUTC();
        this.expiryDays = expiryDays;
    }

    @Transactional
    public CreatedInvitation createInvitation(WorkspaceAccessContext context, String email, WorkspaceRole role) {
        requireActiveWorkspace(context.workspaceId());
        String normalizedEmail = normalizeEmail(email);
        long existingOwnerId = requireExistingOwnerIdByEmail(normalizedEmail);
        if (workspaceStore.findActiveMembership(context.workspaceId(), existingOwnerId).isPresent()) {
            throw new IllegalArgumentException("Workspace membership already exists for owner email");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        String plaintextToken = generateToken();
        WorkspaceInvitationRecord record = workspaceInvitationStore.create(
                context.workspaceId(),
                normalizedEmail,
                role,
                sha256(plaintextToken),
                plaintextToken.substring(0, Math.min(12, plaintextToken.length())),
                WorkspaceInvitationStatus.PENDING,
                now.plusDays(expiryDays),
                now,
                context.ownerId());
        recordInvitationCreated(context, now);
        return new CreatedInvitation(record, plaintextToken);
    }

    @Transactional
    public WorkspaceInvitationRecord acceptInvitation(String plaintextToken, long acceptedByOwnerId) {
        WorkspaceInvitationRecord invitation = workspaceInvitationStore.findPendingByTokenHash(sha256(plaintextToken))
                .orElseThrow(() -> new IllegalArgumentException("Workspace invitation not found"));
        requireActiveWorkspace(invitation.workspaceId());
        OffsetDateTime now = OffsetDateTime.now(clock);
        requireMatchingOwnerEmail(invitation, acceptedByOwnerId);
        if (!invitation.expiresAt().isAfter(now)) {
            workspaceInvitationStore.markExpired(invitation.id());
            throw new IllegalArgumentException("Workspace invitation has expired");
        }
        workspaceStore.addMember(invitation.workspaceId(), acceptedByOwnerId, invitation.role(), now, invitation.createdByOwnerId());
        workspaceInvitationStore.markAccepted(invitation.id(), now, acceptedByOwnerId);
        recordInvitationAccepted(invitation, acceptedByOwnerId, now);
        return workspaceInvitationStore.findById(invitation.id()).orElseThrow();
    }

    @Transactional
    public WorkspaceInvitationRecord revokeInvitation(WorkspaceAccessContext context, long invitationId) {
        requireActiveWorkspace(context.workspaceId());
        WorkspaceInvitationRecord invitation = workspaceInvitationStore.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace invitation not found"));
        if (invitation.workspaceId() != context.workspaceId()) {
            throw new IllegalArgumentException("Workspace invitation not found");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (!workspaceInvitationStore.markRevoked(invitationId, now, context.ownerId())) {
            throw new IllegalArgumentException("Workspace invitation cannot be revoked");
        }
        recordInvitationRevoked(context, invitationId, now);
        return workspaceInvitationStore.findById(invitationId).orElseThrow();
    }

    private void requireMatchingOwnerEmail(WorkspaceInvitationRecord invitation, long acceptedByOwnerId) {
        if (!invitation.email().equalsIgnoreCase(requireExistingOwnerEmail(acceptedByOwnerId))) {
            throw new IllegalArgumentException("Workspace invitation email does not match accepting owner");
        }
    }

    private void recordInvitationCreated(WorkspaceAccessContext context, OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_INVITATION_CREATED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/invitations",
                null,
                "Workspace invitation created",
                now);
    }

    private void recordInvitationAccepted(
            WorkspaceInvitationRecord invitation,
            long acceptedByOwnerId,
            OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_INVITATION_ACCEPTED,
                acceptedByOwnerId,
                invitation.workspaceId(),
                null,
                "POST",
                "/api/v1/workspaces/invitations/accept",
                null,
                "Workspace invitation accepted",
                now);
    }

    private void recordInvitationRevoked(
            WorkspaceAccessContext context,
            long invitationId,
            OffsetDateTime now) {
        securityEventStore.record(
                SecurityEventType.WORKSPACE_INVITATION_REVOKED,
                context.ownerId(),
                context.workspaceId(),
                context.apiKeyHash(),
                "POST",
                "/api/v1/workspaces/current/invitations/" + invitationId + "/revoke",
                null,
                "Workspace invitation revoked",
                now);
    }

    private String requireExistingOwnerEmail(long ownerId) {
        String ownerKey = jdbcTemplate.queryForObject("SELECT owner_key FROM owners WHERE id = ?", String.class, ownerId);
        if (ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("Owner account not found");
        }
        return ownerKey;
    }

    private void requireActiveWorkspace(long workspaceId) {
        if (workspaceStore.isWorkspaceSuspended(workspaceId)) {
            throw new WorkspaceAccessDeniedException("Workspace is suspended");
        }
    }

    private long requireExistingOwnerIdByEmail(String email) {
        Long ownerId = jdbcTemplate.query(
                        "SELECT id FROM owners WHERE lower(owner_key) = lower(?)",
                        (resultSet, rowNum) -> resultSet.getLong("id"),
                        email)
                .stream()
                .findFirst()
                .orElse(null);
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner account not found for invited email");
        }
        return ownerId;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return "wsi_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte part : hash) {
                hex.append(String.format("%02x", part));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    public record CreatedInvitation(WorkspaceInvitationRecord record, String plaintextToken) {
    }
}
