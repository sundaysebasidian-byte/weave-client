namespace Weave.Windows.Core.Tests;

public class AppearanceTests
{
    [Fact]
    public void EveryPaletteIsComplete()
    {
        foreach (var theme in Appearance.Themes)
            foreach (var dark in new[] { false, true })
            {
                var c = theme.For(dark);
                var label = $"{theme.Name} {(dark ? "dark" : "light")}";
                Assert.Equal(3, c.Wash.Count);
                Assert.All(c.Wash, wash => Assert.Equal(8, wash.Length));
                Assert.Equal(3, c.Knot.Count);
                foreach (var hex in new[] { c.Base, c.Ink, c.Muted, c.Faint, c.Accent, c.Accent2, c.OnAccent, c.Sun, c.Success, c.Caution, c.Danger }.Concat(c.Knot))
                    Assert.Matches("^[0-9A-Fa-f]{6}$", hex);
                Assert.True(Appearance.Luminance(c.Base) > 0.5 == !dark, label + ": light themes sit on a light base, dark on a dark one");
            }
    }

    [Fact]
    public void TextAndFilledButtonsMeetContrast()
    {
        foreach (var theme in Appearance.Themes)
            foreach (var dark in new[] { false, true })
            {
                var c = theme.For(dark);
                var label = $"{theme.Name} {(dark ? "dark" : "light")}";
                Assert.True(Appearance.Contrast(c.Ink, c.Base) >= 7, label + " ink");
                Assert.True(Appearance.Contrast(c.Muted, c.Base) >= 4.5, label + " muted");
                Assert.True(Appearance.Contrast(c.OnAccent, c.Accent) >= 4.5, label + " label on button");
                Assert.True(Appearance.Contrast(c.OnAccent, c.Accent2) >= 4.5, label + " label on pressed button");
                Assert.True(Appearance.Contrast(c.Sun, c.Base) >= 2.5, label + " sun mark");
                foreach (var status in new[] { c.Success, c.Caution, c.Danger })
                    Assert.True(Appearance.Contrast(status, c.Base) >= 3, label + " status " + status);
            }
    }

    [Fact]
    public void SettingRoundTrips()
    {
        foreach (var mode in Enum.GetValues<AppearanceMode>())
            for (var theme = 0; theme < Appearance.Themes.Count; theme++)
            {
                var setting = new AppearanceSetting(theme, mode);
                Assert.Equal(setting, AppearanceSetting.Parse(setting.Serialize()));
            }
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("v2:99:light")]
    [InlineData("v2:1:sepia")]
    [InlineData("v2:1")]
    [InlineData("garbage")]
    [InlineData("-3")]
    [InlineData("42")]
    public void UnreadableTextFallsBackToDefault(string? text) => Assert.Equal(AppearanceSetting.Default, AppearanceSetting.Parse(text));

    [Fact]
    public void PreviousPaletteIndexKeepsItsBrightness()
    {
        // 0..7 = 浅色, 白绿, 深色, 素纸, 印象日出, 睡莲, 罂粟花田, 暮色花园. Only the old dark palette may map to dark.
        for (var index = 0; index < 8; index++)
        {
            var setting = AppearanceSetting.Parse(index.ToString());
            Assert.Equal(index == 2 ? AppearanceMode.Dark : AppearanceMode.Light, setting.Mode);
            Assert.InRange(setting.Theme, 0, Appearance.Themes.Count - 1);
        }
    }

    [Fact]
    public void SystemModeFollowsWindows()
    {
        var follow = new AppearanceSetting(0, AppearanceMode.System);
        Assert.True(follow.ResolveDark(true));
        Assert.False(follow.ResolveDark(false));
        Assert.False(new AppearanceSetting(0, AppearanceMode.Light).ResolveDark(true));
        Assert.True(new AppearanceSetting(0, AppearanceMode.Dark).ResolveDark(false));
    }
}

public class KnotTests
{
    private static KnotPalette PaletteOf(int theme, bool dark)
    {
        var c = Appearance.Themes[theme].For(dark);
        return new(c.Knot[0], c.Knot[1], c.Knot[2], c.Ink, c.Sun, c.Muted, dark);
    }

    private static long AlphaSum(byte[] bgra) { long sum = 0; for (var i = 3; i < bgra.Length; i += 4) sum += bgra[i]; return sum; }

    [Fact]
    public void TrefoilPassesOverItselfThreeTimes() => Assert.Equal(3, KnotRenderer.CrossingCount);

    [Fact]
    public void RenderHasTheRequestedShapeAndTransparentCorners()
    {
        var pixels = KnotRenderer.Render(160, PaletteOf(0, false), KnotState.Connected);
        Assert.Equal(160 * 160 * 4, pixels.Length);
        Assert.Equal(0, pixels[3]);                          // top-left corner
        Assert.Equal(0, pixels[^1]);                         // bottom-right corner
        Assert.True(pixels.Where((_, i) => i % 4 == 3 && pixels[i] == 255).Count() > 2500, "the ribbon is opaque");
    }

    [Fact]
    public void PremultipliedChannelsNeverExceedAlpha()
    {
        var pixels = KnotRenderer.Render(128, PaletteOf(4, true), KnotState.Connected);
        for (var i = 0; i < pixels.Length; i += 4)
            Assert.True(pixels[i] <= pixels[i + 3] + 1 && pixels[i + 1] <= pixels[i + 3] + 1 && pixels[i + 2] <= pixels[i + 3] + 1);
    }

    [Fact]
    public void IdleIsMuchFainterThanConnected()
    {
        var palette = PaletteOf(0, false);
        var idle = AlphaSum(KnotRenderer.Render(128, palette, KnotState.Idle));
        var connected = AlphaSum(KnotRenderer.Render(128, palette, KnotState.Connected));
        Assert.True(idle > 0);
        Assert.True(idle < connected * 0.6);
    }

    [Fact]
    public void WeavingRevealsMoreRibbonAsProgressGrows()
    {
        var palette = PaletteOf(1, false);
        var quarter = AlphaSum(KnotRenderer.Render(128, palette, KnotState.Connecting, 0.25));
        var half = AlphaSum(KnotRenderer.Render(128, palette, KnotState.Connecting, 0.5));
        var full = AlphaSum(KnotRenderer.Render(128, palette, KnotState.Connecting, 1));
        Assert.True(quarter < half && half < full);
    }

    [Fact]
    public void RenderIsDeterministic() =>
        Assert.Equal(KnotRenderer.Render(96, PaletteOf(2, false), KnotState.Connected), KnotRenderer.Render(96, PaletteOf(2, false), KnotState.Connected));

    [Fact]
    public void EveryThemeRendersInBothModes()
    {
        for (var theme = 0; theme < Appearance.Themes.Count; theme++)
            foreach (var dark in new[] { false, true })
                Assert.True(AlphaSum(KnotRenderer.Render(96, PaletteOf(theme, dark), KnotState.Connected)) > 0);
    }
}

public class TelemetryTests
{
    [Theory]
    [InlineData(null, 0)]
    [InlineData(12, 4)]
    [InlineData(44, 4)]
    [InlineData(45, 3)]
    [InlineData(99, 3)]
    [InlineData(100, 2)]
    [InlineData(169, 2)]
    [InlineData(170, 1)]
    [InlineData(2400, 1)]
    public void SignalLevelFollowsDelay(int? milliseconds, int level) => Assert.Equal(level, LatencyScale.Level(milliseconds));

    [Fact]
    public void ToneSeparatesUntestedFromLost()
    {
        Assert.Equal(LatencyTone.Untested, LatencyScale.Tone(false, null));
        Assert.Equal(LatencyTone.Lost, LatencyScale.Tone(true, null));
        Assert.Equal(LatencyTone.Good, LatencyScale.Tone(true, 60));
        Assert.Equal(LatencyTone.Fair, LatencyScale.Tone(true, 150));
        Assert.Equal(LatencyTone.Slow, LatencyScale.Tone(true, 600));
    }

    [Theory]
    [InlineData(0L, "0.0", "KB/s")]
    [InlineData(2048L, "2.0", "KB/s")]
    [InlineData(150L * 1024, "150", "KB/s")]
    [InlineData(1024L * 1024, "1.0", "MB/s")]
    [InlineData(2_516_582L, "2.4", "MB/s")]
    [InlineData(250L * 1024 * 1024, "250", "MB/s")]
    [InlineData(-5L, "0.0", "KB/s")]
    public void RatesKeepDigitCountSteady(long bytes, string value, string unit) => Assert.Equal((value, unit), RateFormat.Parts(bytes));

    [Fact]
    public void TotalsAndDurationsAreReadable()
    {
        Assert.Equal(("512", "KB"), RateFormat.Total(512 * 1024));
        Assert.Equal(("42.0", "MB"), RateFormat.Total(42L << 20));
        Assert.Equal(("1.25", "GB"), RateFormat.Total((long)(1.25 * (1L << 30))));
        Assert.Equal("00:09", RateFormat.Duration(TimeSpan.FromSeconds(9)));
        Assert.Equal("12:08", RateFormat.Duration(TimeSpan.FromSeconds(728)));
        Assert.Equal("1:05:03", RateFormat.Duration(TimeSpan.FromSeconds(3903)));
    }

    [Fact]
    public void SeriesKeepsTheNewestSamplesAndTotals()
    {
        var series = new TrafficSeries(4);
        for (var i = 1; i <= 6; i++) series.Add(i * 1000, i * 10, 2);
        Assert.Equal(4, series.Count);
        Assert.Equal(6000, series.LastDown);
        Assert.Equal(60, series.LastUp);
        Assert.Equal(2 * (1000 + 2000 + 3000 + 4000 + 5000 + 6000), series.TotalDown);
        Assert.All(series.NormalizedDown(), value => Assert.InRange(value, 0, 1));
        series.Clear();
        Assert.Equal(0, series.Count);
        Assert.Equal(0, series.TotalDown);
    }

    [Fact]
    public void QuietTrafficDoesNotFillTheChart()
    {
        var series = new TrafficSeries(10);
        series.Add(2048, 1024);
        Assert.True(series.NormalizedDown()[0] < 0.02);
        series.Add(10L * 1024 * 1024, 0);
        Assert.True(series.NormalizedDown()[1] > 0.8);
    }

    [Fact]
    public void ChartLayoutIsRightAlignedAndInsideItsBox()
    {
        var points = ChartMath.Layout(new[] { 0.0, 0.5, 1.0 }, 5, 400, 100, 10, 6);
        Assert.Equal(3, points.Length);
        Assert.Equal(2 * 100, points[0].X);
        Assert.Equal(400, points[^1].X);
        Assert.Equal(94, points[0].Y);       // empty -> bottom padding
        Assert.Equal(10, points[^1].Y);      // full -> top padding
        var curves = ChartMath.Smooth(points);
        Assert.Equal(2, curves.Length);
        Assert.Equal(points[^1], curves[^1].End);
        Assert.Empty(ChartMath.Smooth(points.Take(1).ToArray()));
    }

    [Fact]
    public async Task ProbeReportsEachNodeAsItFinishes()
    {
        var reported = new System.Collections.Concurrent.ConcurrentBag<string>();
        var results = await ConnectionSampling.ProbeNodesAsync(new[] { "a", "b", "c", "a" },
            (name, _) => Task.FromResult<int?>(name == "b" ? null : name.Length * 10), CancellationToken.None, 2,
            (name, _) => reported.Add(name));
        Assert.Equal(3, results.Count);
        Assert.Equal(new[] { "a", "b", "c" }, reported.Order().ToArray());
    }

    [Fact]
    public void HistoryKeepsRecentSamplesAndDistinguishesUntested()
    {
        var history = new NodeHistory(3);
        Assert.Equal((false, (int?)null), history.Latest("n1"));
        foreach (var ms in new int?[] { 40, null, 50, 60 }) history.Record("n1", ms);
        Assert.Equal(new int?[] { null, 50, 60 }, history.Samples("n1"));
        Assert.Equal((true, (int?)60), history.Latest("n1"));
        history.Record("n2", null);
        Assert.Equal((true, (int?)null), history.Latest("n2"));
    }

    [Fact]
    public void HealthIsUnknownUntilSomethingIsMeasured()
    {
        var nodes = Enumerable.Range(1, 4).Select(i => new ProxyNode { Id = "n" + i, Name = "N" + i, Protocol = "vmess" }).ToArray();
        var history = new NodeHistory();
        Assert.Null(SubscriptionHealth.Fraction(nodes, history));
        history.Record("n1", 30); history.Record("n2", null); history.Record("n3", 70);
        Assert.Equal(2.0 / 3, SubscriptionHealth.Fraction(nodes, history)!.Value, 6);
    }
}

public class NodeRegionTests
{
    [Theory]
    [InlineData("\U0001F1EF\U0001F1F5 Tokyo 03", "Tokyo 03", "JP")]
    [InlineData("", "香港 IEPL 05", "HK")]
    [InlineData("", "US-LA-01", "US")]
    [InlineData("", "Frankfurt 1", "DE")]
    [InlineData("", "Premium 01", "")]
    [InlineData("", "新加坡 02", "SG")]
    [InlineData("\U0001F1FA\U0001F1F8 LA", "LA", "US")]
    public void RecognisesRegions(string raw, string name, string code) => Assert.Equal(code, NodeRegion.Code(raw, name));

    [Theory]
    [InlineData("东京 03", "JP", false, "东京")]
    [InlineData("HK 05", "HK", false, "香港")]
    [InlineData("HK 05", "HK", true, "Hong Kong")]
    [InlineData("IEPL 专线 香港 02", "HK", false, "香港")]
    [InlineData("Premium 01", "", false, "Premium 01")]   // only decoration words: keep the whole name
    [InlineData("01", "", false, "01")]
    [InlineData("Tokyo 03", "JP", false, "Tokyo")]
    public void TitleIsTheFirstMeaningfulWord(string name, string code, bool english, string title) =>
        Assert.Equal(title, NodeRegion.Title(name, code, english));
}
