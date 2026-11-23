package io.weave.client.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/** The progress panel never uses outcome names; terminal notices present them in encounter order. */
final class SubscriptionRefreshNoticeNames {
    private static final SubscriptionRefreshNoticeNames EMPTY =
            new SubscriptionRefreshNoticeNames(Collections.emptyList(), Collections.emptyList());

    private final List<String> failed;
    private final List<String> review;

    private SubscriptionRefreshNoticeNames(List<String> failed, List<String> review) {
        this.failed = failed;
        this.review = review;
    }

    List<String> failed() { return failed; }

    List<String> review() { return review; }

    static <T> SubscriptionRefreshNoticeNames collect(
            boolean running, List<T> results, Predicate<T> succeeded,
            Predicate<T> reviewRequired, Function<T, String> name) {
        if (running || results.isEmpty()) return EMPTY;
        List<String> failed = Collections.emptyList();
        List<String> review = Collections.emptyList();
        for (T result : results) {
            if (reviewRequired.test(result)) {
                if (review.isEmpty()) review = new ArrayList<>();
                review.add(name.apply(result));
            } else if (!succeeded.test(result)) {
                if (failed.isEmpty()) failed = new ArrayList<>();
                failed.add(name.apply(result));
            }
        }
        if (failed.isEmpty() && review.isEmpty()) return EMPTY;
        return new SubscriptionRefreshNoticeNames(failed, review);
    }
}
