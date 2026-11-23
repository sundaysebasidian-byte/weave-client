package io.weave.client.ui;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class AppPickerFilterTest {
    @Test public void keepsInputOrderAndUsesExactPackageNames() {
        List<String> apps = List.of("pkg.z", "pkg.C", "pkg.a", "pkg.z", "pkg.b");
        List<String> routes = List.of("pkg.a", "pkg.c", "pkg.a", "pkg.b");
        assertEquals(List.of("pkg.z", "pkg.C", "pkg.z"),
                AppPickerFilter.unrouted(apps, routes, Function.identity(), Function.identity()));
    }

    @Test public void handlesEmptyInputs() {
        assertEquals(List.of(), AppPickerFilter.unrouted(List.<String>of(), List.of("pkg.a"),
                Function.identity(), Function.identity()));
        assertEquals(List.of("pkg.a"), AppPickerFilter.unrouted(List.of("pkg.a"), List.<String>of(),
                Function.identity(), Function.identity()));
    }

    @Test public void readsEveryPackageAtMostOnce() {
        List<String> apps = new ArrayList<>();
        for (int i = 0; i < 200; i++) apps.add("com.example.app" + i);
        List<String> routes = apps.subList(0, 100);
        AtomicInteger appReads = new AtomicInteger();
        AtomicInteger routeReads = new AtomicInteger();
        assertEquals(apps.subList(100, 200), AppPickerFilter.unrouted(apps, routes,
                app -> { appReads.incrementAndGet(); return app; },
                route -> { routeReads.incrementAndGet(); return route; }));
        assertEquals(apps.size(), appReads.get());
        assertEquals(routes.size(), routeReads.get());
    }
}
