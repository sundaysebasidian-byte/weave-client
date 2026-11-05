using System.Globalization;

namespace Weave.Windows.Core;

public enum LatencyTone { Untested, Good, Fair, Slow, Lost }

/// <summary>Maps a measured delay onto the four-bar signal glyph and a colour tone. Pure, so the glyph and the colour never disagree.</summary>
public static class LatencyScale
{
    /// <summary>0 = untested or unreachable, 1 = slow ... 4 = excellent.</summary>
    public static int Level(int? milliseconds) => milliseconds switch { null => 0, < 45 => 4, < 100 => 3, < 170 => 2, _ => 1 };

    public static LatencyTone Tone(bool tested, int? milliseconds) =>
        !tested ? LatencyTone.Untested : milliseconds switch { null => LatencyTone.Lost, < 100 => LatencyTone.Good, < 200 => LatencyTone.Fair, _ => LatencyTone.Slow };
}

public static class RateFormat
{
    private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;

    /// <summary>"2.4", "MB/s". Large numbers lose the decimal so the digits never jitter in width.</summary>
    public static (string Value, string Unit) Parts(long bytesPerSecond)
    {
        var bytes = Math.Max(0, bytesPerSecond);
        if (bytes >= 1024 * 1024)
        {
            var mb = bytes / 1048576.0;
            return (mb >= 100 ? mb.ToString("F0", Invariant) : mb.ToString("F1", Invariant), "MB/s");
        }
        var kb = bytes / 1024.0;
        return (kb >= 100 ? kb.ToString("F0", Invariant) : kb.ToString("F1", Invariant), "KB/s");
    }

    public static string Format(long bytesPerSecond) { var (value, unit) = Parts(bytesPerSecond); return value + " " + unit; }

    /// <summary>Volume moved: "1.24", "GB".</summary>
    public static (string Value, string Unit) Total(long bytes)
    {
        bytes = Math.Max(0, bytes);
        if (bytes >= 1L << 30) return ((bytes / (double)(1L << 30)).ToString("F2", Invariant), "GB");
        if (bytes >= 1L << 20) { var mb = bytes / (double)(1L << 20); return (mb >= 100 ? mb.ToString("F0", Invariant) : mb.ToString("F1", Invariant), "MB"); }
        return ((bytes / 1024.0).ToString("F0", Invariant), "KB");
    }

    public static string Duration(TimeSpan span)
    {
        var total = (int)Math.Max(0, span.TotalSeconds);
        var h = total / 3600; var m = total / 60 % 60; var s = total % 60;
        return h > 0 ? $"{h}:{m:D2}:{s:D2}" : $"{m:D2}:{s:D2}";
    }
}

/// <summary>Fixed-length history of the traffic rate, newest last. Feeds the live chart.</summary>
public sealed class TrafficSeries(int capacity = 60)
{
    private readonly long[] _down = new long[capacity];
    private readonly long[] _up = new long[capacity];
    private int _count;

    public int Capacity => capacity;
    public int Count => _count;
    public long TotalDown { get; private set; }
    public long TotalUp { get; private set; }

    /// <summary>Records one sample and adds its volume to the session totals. <paramref name="seconds"/> is the time it represents.</summary>
    public void Add(long down, long up, double seconds = 2)
    {
        down = Math.Max(0, down); up = Math.Max(0, up);
        if (_count == capacity)
        {
            Array.Copy(_down, 1, _down, 0, capacity - 1);
            Array.Copy(_up, 1, _up, 0, capacity - 1);
            _count--;
        }
        _down[_count] = down; _up[_count] = up; _count++;
        TotalDown += (long)(down * seconds); TotalUp += (long)(up * seconds);
    }

    public void Clear() { _count = 0; TotalDown = 0; TotalUp = 0; }

    public long LastDown => _count == 0 ? 0 : _down[_count - 1];
    public long LastUp => _count == 0 ? 0 : _up[_count - 1];
    public long DownAt(int index) => _down[index];
    public long UpAt(int index) => _up[index];

    /// <summary>Vertical scale shared by both lines: never below 256 KB/s so quiet links do not look busy.</summary>
    public double ScaleMax()
    {
        long peak = 256 * 1024;
        for (var i = 0; i < _count; i++) peak = Math.Max(peak, Math.Max(_down[i], _up[i]));
        return peak * 1.15;
    }

    public double[] NormalizedDown() => Normalize(_down);
    public double[] NormalizedUp() => Normalize(_up);

    private double[] Normalize(long[] source)
    {
        var scale = ScaleMax();
        var values = new double[_count];
        for (var i = 0; i < _count; i++) values[i] = source[i] / scale;
        return values;
    }
}

/// <summary>Geometry for the live chart: points laid out right-aligned, then a Catmull-Rom curve expressed as cubic Béziers.</summary>
public static class ChartMath
{
    public readonly record struct Pt(double X, double Y);
    public readonly record struct Cubic(Pt Control1, Pt Control2, Pt End);

    public static Pt[] Layout(IReadOnlyList<double> normalized, int capacity, double width, double height, double top, double bottom)
    {
        var points = new Pt[normalized.Count];
        var offset = capacity - normalized.Count;
        for (var i = 0; i < points.Length; i++)
            points[i] = new((i + offset) * width / Math.Max(1, capacity - 1), height - bottom - Math.Clamp(normalized[i], 0, 1) * (height - top - bottom));
        return points;
    }

    public static Cubic[] Smooth(IReadOnlyList<Pt> p)
    {
        if (p.Count < 2) return [];
        var curves = new Cubic[p.Count - 1];
        for (var i = 0; i < curves.Length; i++)
        {
            Pt p0 = p[Math.Max(0, i - 1)], p1 = p[i], p2 = p[i + 1], p3 = p[Math.Min(p.Count - 1, i + 2)];
            curves[i] = new(new(p1.X + (p2.X - p0.X) / 6, p1.Y + (p2.Y - p0.Y) / 6),
                new(p2.X - (p3.X - p1.X) / 6, p2.Y - (p3.Y - p1.Y) / 6), p2);
        }
        return curves;
    }
}

/// <summary>Delay measurements per node kept for this run only. Shows a trend once a node has been measured more than once.</summary>
public sealed class NodeHistory(int capacity = 12)
{
    private readonly Dictionary<string, List<int?>> _samples = new(StringComparer.Ordinal);

    public void Record(string nodeId, int? milliseconds)
    {
        if (!_samples.TryGetValue(nodeId, out var list)) _samples[nodeId] = list = new();
        list.Add(milliseconds);
        if (list.Count > capacity) list.RemoveAt(0);
    }

    public IReadOnlyList<int?> Samples(string nodeId) => _samples.TryGetValue(nodeId, out var list) ? list : Array.Empty<int?>();
    public (bool Tested, int? Last) Latest(string nodeId) =>
        _samples.TryGetValue(nodeId, out var list) && list.Count > 0 ? (true, list[^1]) : (false, null);
    public void Forget(string nodeId) => _samples.Remove(nodeId);
    public void Clear() => _samples.Clear();
}

public static class SubscriptionHealth
{
    /// <summary>
    /// Share of a subscription's nodes that answered in the last measurement; null while nothing has been measured.
    /// A subscription with no node measured yet is "unknown", not "bad".
    /// </summary>
    public static double? Fraction(IEnumerable<ProxyNode> nodes, NodeHistory history)
    {
        int total = 0, tested = 0, alive = 0;
        foreach (var node in nodes)
        {
            total++;
            var (done, last) = history.Latest(node.Id);
            if (!done) continue;
            tested++;
            if (last is not null) alive++;
        }
        return tested == 0 || total == 0 ? null : (double)alive / tested;
    }
}
