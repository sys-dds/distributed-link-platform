package com.linkplatform.api.link.application;

import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class LinkTargetPolicyService {

    private final WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService;

    public LinkTargetPolicyService() {
        this.workspaceAbuseIntelligenceService = null;
    }

    public LinkTargetPolicyService(WorkspaceAbuseIntelligenceService workspaceAbuseIntelligenceService) {
        this.workspaceAbuseIntelligenceService = workspaceAbuseIntelligenceService;
    }

    public TargetRiskAssessment assess(String targetUrl) {
        return assess(null, targetUrl);
    }

    public TargetRiskAssessment assess(Long workspaceId, String targetUrl) {
        URI uri;
        try {
            uri = URI.create(targetUrl);
        } catch (IllegalArgumentException exception) {
            throw new UnsafeLinkTargetException("Target URL is invalid");
        }
        String scheme = normalize(uri.getScheme());
        if ("javascript".equals(scheme) || "data".equals(scheme) || "file".equals(scheme)) {
            return reject("Blocked target scheme", null);
        }

        String host = normalizeHost(uri.getHost());
        if ("http".equals(scheme) || "https".equals(scheme)) {
            if (host == null) {
                return reject("HTTP target host is required", null);
            }
            WorkspaceAbuseIntelligenceService.HostRuleEffect hostRuleEffect =
                    workspaceId == null || workspaceAbuseIntelligenceService == null ? WorkspaceAbuseIntelligenceService.HostRuleEffect.NONE
                            : workspaceAbuseIntelligenceService.hostRuleEffect(workspaceId, host);
            if (hostRuleEffect == WorkspaceAbuseIntelligenceService.HostRuleEffect.DENY) {
                return reject("Blocked target host", host);
            }
            if ("localhost".equals(host)) {
                return reject("Blocked local target host", host);
            }
            if (isIpLiteral(host)) {
                InetAddress address = parseLiteralIp(host);
                if (address == null) {
                    return reject("Target host is invalid", host);
                }
                if (isRejectedPrivateAddress(address)) {
                    return reject("Blocked private target host", host);
                }
                if (hostRuleEffect == WorkspaceAbuseIntelligenceService.HostRuleEffect.ALLOW) {
                    return safe(host);
                }
                if (workspaceId == null
                        || workspaceAbuseIntelligenceService == null
                        || workspaceAbuseIntelligenceService.policyForWorkspace(workspaceId).rawIpReviewEnabled()) {
                    return review("Public IP target requires operator review", host, 60);
                }
                return safe(host);
            }
            if (host.contains("xn--")) {
                if (hostRuleEffect == WorkspaceAbuseIntelligenceService.HostRuleEffect.ALLOW) {
                    return safe(host);
                }
                if (workspaceId != null
                        && workspaceAbuseIntelligenceService != null
                        && !workspaceAbuseIntelligenceService.policyForWorkspace(workspaceId).punycodeReviewEnabled()) {
                    return safe(host);
                }
                return review("Punycode target requires operator review", host, 50);
            }
            if (host.length() > 200) {
                if (hostRuleEffect == WorkspaceAbuseIntelligenceService.HostRuleEffect.ALLOW) {
                    return safe(host);
                }
                return review("Long hostname requires operator review", host, 40);
            }
            if (workspaceId != null
                    && workspaceAbuseIntelligenceService != null
                    && workspaceAbuseIntelligenceService.repeatedHostThresholdReached(workspaceId, host)
                    && hostRuleEffect != WorkspaceAbuseIntelligenceService.HostRuleEffect.ALLOW) {
                return review("Repeated target host requires operator review", host, 75);
            }
        }

        return safe(host);
    }

    private TargetRiskAssessment reject(String summary, String host) {
        return new TargetRiskAssessment(TargetRiskAssessment.Decision.REJECT, summary, 100, host);
    }

    private TargetRiskAssessment review(String summary, String host, int riskScore) {
        return new TargetRiskAssessment(TargetRiskAssessment.Decision.REVIEW, summary, riskScore, host);
    }

    private TargetRiskAssessment safe(String host) {
        return new TargetRiskAssessment(TargetRiskAssessment.Decision.SAFE, "Target accepted", 0, host);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String trimmed = host.trim();
        String normalized = trimmed.startsWith("[") && trimmed.endsWith("]")
                ? trimmed.substring(1, trimmed.length() - 1)
                : trimmed;
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return IDN.toASCII(normalized, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new UnsafeLinkTargetException("Target host is invalid");
        }
    }

    private boolean isIpLiteral(String host) {
        return host.matches("\\d+\\.\\d+\\.\\d+\\.\\d+") || host.contains(":");
    }

    private InetAddress parseLiteralIp(String host) {
        try {
            return InetAddress.getByName(host);
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isRejectedPrivateAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet6Address inet6) {
            byte first = inet6.getAddress()[0];
            return (first & 0xFE) == 0xFC;
        }
        if (address instanceof Inet4Address inet4) {
            byte[] octets = inet4.getAddress();
            int first = octets[0] & 0xFF;
            int second = octets[1] & 0xFF;
            return first == 10
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || first == 127
                    || (first == 169 && second == 254);
        }
        return false;
    }
}
