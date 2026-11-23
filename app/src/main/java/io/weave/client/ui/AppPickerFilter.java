package io.weave.client.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Filters routed packages while retaining launcher order. */
final class AppPickerFilter {
    private AppPickerFilter() {}

    static <A, R> List<A> unrouted(List<A> apps, List<R> routes,
            Function<A, String> appPackage, Function<R, String> routePackage) {
        Set<String> routed = new HashSet<>();
        for (R route : routes) routed.add(routePackage.apply(route));

        List<A> result = new ArrayList<>();
        for (A app : apps) {
            if (!routed.contains(appPackage.apply(app))) result.add(app);
        }
        return result;
    }
}
