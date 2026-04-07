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

    public TargetRiskAssessment assess(String targetUrl) {
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
                return review("Public IP target requires operator review", host, 60);
            }
            if (host.contains("xn--")) {
                return review("Punycode target requires operator review", host, 50);
            }
            if (host.length() > 200) {
                return review("Long hostname requires operator review", host, 40);
            }
        }

        return new TargetRiskAssessment(TargetRiskAssessment.Decision.SAFE, "Target accepted", 0, host);
    }

    private TargetRiskAssessment reject(String summary, String host) {
        return new TargetRiskAssessment(TargetRiskAssessment.Decision.REJECT, summary, 100, host);
    }

    private TargetRiskAssessment review(String summary, String host, int riskScore) {
        return new TargetRiskAssessment(TargetRiskAssessment.Decision.REVIEW, summary, riskScore, host);
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
