package io.weave.client.ui;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public final class AppPickerFilterCapacityTest {
    @Test public void retainsDuplicatesWhenStaleRoutesMakeTheHintTooSmall() {
        List<String> apps = new ArrayList<>(List.of("a", "b", "b", "C", "d"));
        List<String> routes = List.of("a", "a", "c", "x", "y", "z");

        List<String> result = AppPickerFilter.unrouted(
                apps, routes, Function.identity(), Function.identity());

        assertEquals(List.of("b", "b", "C", "d"), result);
        result.remove(0);
        assertEquals(List.of("a", "b", "b", "C", "d"), apps);
    }

    @Test public void handlesMostlyRoutedAndNoRoutes() {
        List<String> apps = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            apps.add("pkg." + i);
        }
        assertEquals(apps, AppPickerFilter.unrouted(
                apps, List.<String>of(), Function.identity(), Function.identity()));
        assertEquals(apps.subList(495, 500), AppPickerFilter.unrouted(
                apps, apps.subList(0, 495), Function.identity(), Function.identity()));
    }
}
