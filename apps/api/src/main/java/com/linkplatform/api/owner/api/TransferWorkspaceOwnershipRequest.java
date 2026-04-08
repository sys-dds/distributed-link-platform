package com.linkplatform.api.owner.api;

public record TransferWorkspaceOwnershipRequest(long fromOwnerId, long toOwnerId) {
}
