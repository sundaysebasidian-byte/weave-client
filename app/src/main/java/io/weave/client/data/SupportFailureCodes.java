package io.weave.client.data;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Projects the recovery vault's fixed categories into the existing diagnostic field. */
final class SupportFailureCodes {
    private static final String VALIDATION = "candidate_validation_failed";
    private static final String VALIDATION_PREFIX = VALIDATION + ":";
    private static final String PREVIOUS_RESTORED = "candidate_start_failed;previous_runtime_restored";
    private static final String BOTH_FAILED = "candidate_and_rollback_failed";
    private static final Pattern STARTUP = Pattern.compile("(?i)\\bS\\d{2}(?::[A-Z]{2}\\d{1,3})?\\b");

    private SupportFailureCodes() {}

    static String project(String failure, Set<String> runtimeCodes) {
        if (failure == null) return "unavailable";
        if (failure.equals(PREVIOUS_RESTORED)) return PREVIOUS_RESTORED;
        if (failure.equals(BOTH_FAILED)) return BOTH_FAILED;
        if (failure.startsWith(VALIDATION_PREFIX)) {
            String code = failure.substring(VALIDATION_PREFIX.length());
            return runtimeCodes.contains(code) ? VALIDATION_PREFIX + code : VALIDATION;
        }
        var startup = STARTUP.matcher(failure);
        return startup.find() ? startup.group().toUpperCase(Locale.ROOT) : "unavailable";
    }
}
