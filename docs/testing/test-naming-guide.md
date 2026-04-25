# Test Naming Guide

Good tests describe protected behavior.

Examples:

- `reservedSlugCreationReturnsProblemDetails`
- `selfRedirectIsRejectedBeforePersistence`
- `secondRedirectUsesCacheHit`
- `redisUnavailableFallsBackToPrimaryStorage`
- `analyticsOutboxLeaseCanBeReclaimed`
- `poisonAnalyticsMessageIsParked`
- `projectionRebuildAlignsOwnerAnalytics`
- `duplicateCreateMutationReturnsOriginalResult`
- `quotaRaceDoesNotExceedPlanLimit`
- `wrongOwnerCannotReadWorkspaceLinks`
- `webhookCallbackValidationRejectsInvalidPayload`
- `queryDatasourceFailureFallsBackToPrimary`
- `noFailoverRuntimeFailsClosed`
- `workspaceImportRestorePreservesOwnership`

Back to [README](../../README.md).
