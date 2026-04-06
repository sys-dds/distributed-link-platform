package com.linkplatform.api.link.application;

import java.time.OffsetDateTime;

public interface PipelineControlStore {

    PipelineControl get(String pipelineName);

    PipelineControl pause(String pipelineName, String reason, OffsetDateTime updatedAt);

    PipelineControl resume(String pipelineName, OffsetDateTime updatedAt);

    PipelineControl recordForceTick(String pipelineName, OffsetDateTime occurredAt);

    PipelineControl recordRequeue(String pipelineName, OffsetDateTime occurredAt);

    PipelineControl recordRelaySuccess(String pipelineName, OffsetDateTime occurredAt);

    PipelineControl recordRelayFailure(String pipelineName, OffsetDateTime occurredAt, String failureReason);
}
