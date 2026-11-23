package io.weave.client.ui;

import java.util.function.Predicate;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SensitiveClipboardCopyTest {
    @Test
    public void oldTimerCannotClearTheSameTextCopiedAgain() {
        TimedCopy older = copy("fixture text");
        TimedCopy newer = copy("fixture text");

        assertFalse(older.shouldClear().test(newer.clip()));
        assertTrue(newer.shouldClear().test(newer.clip()));
    }

    @Test
    public void matchingTextWithoutThisCopyTokenIsNotCleared() {
        TimedCopy owned = copy("fixture text");
        FakeClip copiedElsewhere = new FakeClip("fixture text", null);

        assertFalse(owned.shouldClear().test(copiedElsewhere));
    }

    @Test
    public void onlyTheCurrentTaggedCopyIsEligibleToClear() {
        TimedCopy owned = copy("fixture text");

        assertTrue(owned.shouldClear().test(owned.clip()));
        assertFalse(owned.shouldClear().test(new FakeClip("other text", null)));
    }

    private static TimedCopy copy(String text) {
        var copy = new SensitiveClipboardCopy();
        return new TimedCopy(new FakeClip(text, copy.token()), clip -> copy.owns(clip.token()));
    }

    private record FakeClip(String text, String token) {}
    private record TimedCopy(FakeClip clip, Predicate<FakeClip> shouldClear) {}
}
