package io.weave.client.data;

import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SupportFailureCodesTest {
    private static final Set<String> RUNTIME_CODES = Set.of("dns_configuration_failed", "subscription_parse_failed");

    @Test
    public void includesOnlyAnExactKnownValidationCode() {
        assertEquals("candidate_validation_failed:dns_configuration_failed",
                project("candidate_validation_failed:dns_configuration_failed"));
        assertEquals("candidate_validation_failed:subscription_parse_failed",
                project("candidate_validation_failed:subscription_parse_failed"));
        assertEquals("candidate_validation_failed", project("candidate_validation_failed:IllegalArgumentException"));
        assertEquals("candidate_validation_failed", project(
                "candidate_validation_failed:dns_configuration_failed https://example.test/private"));
        assertEquals("candidate_validation_failed", project("candidate_validation_failed:opaque-fixture-id"));
    }

    @Test
    public void distinguishesTheTwoFixedRollbackOutcomes() {
        assertEquals("candidate_start_failed;previous_runtime_restored",
                project("candidate_start_failed;previous_runtime_restored"));
        assertEquals("candidate_and_rollback_failed", project("candidate_and_rollback_failed"));
        assertEquals("unavailable", project("candidate_and_rollback_failed:other"));
    }

    @Test
    public void preservesLegacyStartupCodeAndUnknownFallback() {
        assertEquals("S02:WM1", project("s02:wm1 additional detail"));
        assertEquals("S02", project("S02:dns_configuration_failed"));
        assertEquals("unavailable", project("UnknownException"));
        assertEquals("unavailable", project(null));
    }

    private static String project(String raw) {
        return SupportFailureCodes.project(raw, RUNTIME_CODES);
    }
}
