package com.linkplatform.api.link.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

public record AnalyticsRange(
        OffsetDateTime start,
        OffsetDateTime end,
        Duration duration,
        boolean comparePrevious) {

    private static final Duration MAX_RANGE = Duration.ofDays(90);
    private static final Duration MAX_HOURLY_RANGE = Duration.ofDays(7);

    public AnalyticsRange {
        if (start == null || end == null) {
            throw new IllegalArgumentException("from and to must be provided together");
        }
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("from must be strictly before to");
        }
        if (duration == null) {
            duration = Duration.between(start, end);
        }
        if (duration.compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("Analytics query range must be 90 days or less");
        }
    }

    public static AnalyticsRange optional(OffsetDateTime from, OffsetDateTime to, Boolean comparePrevious) {
        if (from == null && to == null) {
            return null;
        }
        return new AnalyticsRange(from, to, null, comparePrevious != null && comparePrevious);
    }

    public static AnalyticsRange required(OffsetDateTime from, OffsetDateTime to, Boolean comparePrevious) {
        return new AnalyticsRange(from, to, Duration.between(from, to), comparePrevious != null && comparePrevious);
    }

    public String validateGranularity(String granularity) {
        String normalized = normalizeGranularity(granularity);
        if ("hour".equals(normalized) && duration.compareTo(MAX_HOURLY_RANGE) > 0) {
            throw new IllegalArgumentException("hour granularity is only allowed for ranges up to 7 days");
        }
        if ("day".equals(normalized) && duration.compareTo(MAX_RANGE) > 0) {
            throw new IllegalArgumentException("day granularity is only allowed for ranges up to 90 days");
        }
        return normalized;
    }

    public OffsetDateTime previousStart() {
        return start.minus(duration);
    }

    public OffsetDateTime previousEnd() {
        return start;
    }

    private static String normalizeGranularity(String granularity) {
        String normalized = granularity == null ? "" : granularity.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hour", "day" -> normalized;
            default -> throw new IllegalArgumentException("granularity must be one of: hour, day");
        };
    }
}
