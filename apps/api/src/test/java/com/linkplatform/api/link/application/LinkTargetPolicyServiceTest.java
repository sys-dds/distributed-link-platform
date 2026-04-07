package com.linkplatform.api.link.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LinkTargetPolicyServiceTest {

    private final LinkTargetPolicyService service = new LinkTargetPolicyService();

    @Test
    void javascriptDataAndFileTargetsAreRejected() {
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("javascript:alert(1)").decision());
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("data:text/plain,hello").decision());
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("file:///tmp/secret").decision());
    }

    @Test
    void localhostPrivateAndLoopbackTargetsAreRejected() {
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("https://localhost/admin").decision());
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("https://127.0.0.1/admin").decision());
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("https://192.168.1.10/private").decision());
        assertEquals(TargetRiskAssessment.Decision.REJECT, service.assess("https://[::1]/private").decision());
    }

    @Test
    void rawPublicIpTargetIsFlaggedNotRejected() {
        TargetRiskAssessment assessment = service.assess("https://8.8.8.8/path");

        assertEquals(TargetRiskAssessment.Decision.REVIEW, assessment.decision());
        assertEquals("8.8.8.8", assessment.normalizedTargetHost());
    }

    @Test
    void punycodeTargetIsFlaggedNotRejected() {
        TargetRiskAssessment assessment = service.assess("https://xn--bcher-kva.example/path");

        assertEquals(TargetRiskAssessment.Decision.REVIEW, assessment.decision());
        assertTrue(assessment.summary().toLowerCase().contains("punycode"));
    }
}
