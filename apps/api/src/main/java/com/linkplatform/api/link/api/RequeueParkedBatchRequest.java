package com.linkplatform.api.link.api;

import java.util.List;

public record RequeueParkedBatchRequest(List<Long> ids) {
}
