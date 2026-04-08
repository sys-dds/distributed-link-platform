package com.linkplatform.api.owner.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record WorkspaceImportSummaryResponse(
        long linksCreated,
        long linksUpdated,
        long conflicts,
        long skipped,
        List<String> conflictDetails,
        List<String> skippedDetails) {

    static WorkspaceImportSummaryResponse from(JsonNode summaryJson) {
        if (summaryJson == null || !summaryJson.isObject()) {
            return new WorkspaceImportSummaryResponse(0, 0, 0, 0, List.of(), List.of());
        }
        return new WorkspaceImportSummaryResponse(
                summaryJson.path("linksCreated").asLong(0),
                summaryJson.path("linksUpdated").asLong(0),
                summaryJson.path("conflicts").asLong(0),
                summaryJson.path("skipped").asLong(0),
                toList(summaryJson.path("conflictDetails")),
                toList(summaryJson.path("skippedDetails")));
    }

    private static List<String> toList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return List.copyOf(values);
    }
}
