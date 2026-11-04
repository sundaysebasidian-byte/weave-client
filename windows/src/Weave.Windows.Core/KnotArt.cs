namespace Weave.Windows.Core;

public enum KnotState
{
    /// <summary>Not connected: a pale, frosted ghost of the knot and an empty sun.</summary>
    Idle,
    /// <summary>Connecting: the ribbon weaves itself in; <c>progress</c> says how far.</summary>
    Connecting,
    /// <summary>Connected: the knot is tied and the sun is lit.</summary>
    Connected,
}

public readonly record struct KnotPalette(string K0, string K1, string K2, string Ink, string Sun, string Muted, bool Dark);

/// <summary>
/// The Weave mark as a living illustration: a trefoil ribbon with proper over/under crossings, a soft brush texture and a sun.
/// It is rasterised in managed code into premultiplied BGRA so it needs no graphics library, scales to any DPI and can be
/// re-rendered per theme or per animation frame. Geometry is cached; a render allocates one pixel buffer.
/// </summary>
public static class KnotRenderer
{
    public const int Segments = 720;
    private const double RibbonWidth = 0.105;   // of the square's side
    private const double EdgeWidth = 0.0042;
    private const double SunX = 0.80, SunY = 0.19, SunRadius = 0.055;
    private static readonly Lazy<Shape> Geometry = new(BuildShape);

    private sealed class Shape
    {
        public double[] X = [], Y = [], Z = [], T = [], NX = [], NY = [];
        public bool[] Over = [];
        public double MeanSegment;
        public int CrossingRuns;
    }

    /// <summary>Number of places where the ribbon passes over itself. A trefoil has three.</summary>
    public static int CrossingCount => Geometry.Value.CrossingRuns;

    private static Shape BuildShape()
    {
        const int n = Segments;
        var x = new double[n + 1]; var y = new double[n + 1]; var z = new double[n + 1]; var t = new double[n + 1];
        const double rotation = -0.5;
        double cr = Math.Cos(rotation), sr = Math.Sin(rotation), minX = 1e9, maxX = -1e9, minY = 1e9, maxY = -1e9;
        for (var i = 0; i <= n; i++)
        {
            var a = (double)i / n * Math.PI * 2;
            double px = Math.Sin(a) + 2 * Math.Sin(2 * a), py = Math.Cos(a) - 2 * Math.Cos(2 * a);
            x[i] = px * cr - py * sr; y[i] = px * sr + py * cr; z[i] = -Math.Sin(3 * a); t[i] = (double)i / n;
            minX = Math.Min(minX, x[i]); maxX = Math.Max(maxX, x[i]); minY = Math.Min(minY, y[i]); maxY = Math.Max(maxY, y[i]);
        }
        var scale = 0.62 / Math.Max(maxX - minX, maxY - minY);
        double cx = 0.46 - (minX + maxX) / 2 * scale, cy = 0.52 - (minY + maxY) / 2 * scale;
        for (var i = 0; i <= n; i++) { x[i] = cx + x[i] * scale; y[i] = cy + y[i] * scale; }

        var nx = new double[n + 1]; var ny = new double[n + 1]; double length = 0;
        for (var i = 0; i <= n; i++)
        {
            int a = Math.Max(0, i - 1), b = Math.Min(n, i + 1);
            double dx = x[b] - x[a], dy = y[b] - y[a], l = Math.Sqrt(dx * dx + dy * dy);
            if (l == 0) l = 1;
            nx[i] = -dy / l; ny[i] = dx / l;
            if (i < n) length += Math.Sqrt((x[i + 1] - x[i]) * (x[i + 1] - x[i]) + (y[i + 1] - y[i]) * (y[i + 1] - y[i]));
        }
        var mean = length / n;
        var radius = (int)Math.Ceiling(1.4 * RibbonWidth / mean);
        var over = new bool[n];
        for (var i = 0; i < n; i++)
            for (var j = i + 8; j < n; j++)
            {
                if (i + (n - j) < 8) continue;
                double d = (x[i + 1] - x[i]) * (y[j + 1] - y[j]) - (y[i + 1] - y[i]) * (x[j + 1] - x[j]);
                if (Math.Abs(d) < 1e-12) continue;
                var u = ((x[j] - x[i]) * (y[j + 1] - y[j]) - (y[j] - y[i]) * (x[j + 1] - x[j])) / d;
                var v = ((x[j] - x[i]) * (y[i + 1] - y[i]) - (y[j] - y[i]) * (x[i + 1] - x[i])) / d;
                if (u < 0 || u >= 1 || v < 0 || v >= 1) continue;
                var zi = z[i] * (1 - u) + z[i + 1] * u; var zj = z[j] * (1 - v) + z[j + 1] * v;
                var top = zi > zj ? i : j;
                for (var q = -radius; q <= radius; q++) over[((top + q) % n + n) % n] = true;
            }
        var runs = 0;
        for (var i = 0; i < n; i++) if (over[i] && !over[(i + n - 1) % n]) runs++;
        return new Shape { X = x, Y = y, Z = z, T = t, NX = nx, NY = ny, Over = over, MeanSegment = mean, CrossingRuns = runs };
    }

    /// <summary>Premultiplied BGRA, <paramref name="size"/> x <paramref name="size"/> pixels, row-major, transparent background.</summary>
    public static byte[] Render(int size, KnotPalette palette, KnotState state, double progress = 1)
    {
        if (size < 16) throw new ArgumentOutOfRangeException(nameof(size));
        var shape = Geometry.Value;
        var ghost = state == KnotState.Idle;
        var last = ghost ? Segments : (int)Math.Floor(Math.Clamp(progress, 0, 1) * Segments);
        var canvas = new Canvas(size);
        var k0 = Rgb(palette.K0); var k1 = Rgb(palette.K1); var k2 = Rgb(palette.K2);
        var mute = Rgb(palette.Muted); var ink = Rgb(palette.Ink); var sun = Rgb(palette.Sun);
        var shade = palette.Dark ? new[] { 4 / 255.0, 7 / 255.0, 28 / 255.0 } : ink;
        double w = RibbonWidth * size, edge = EdgeWidth * size, ext = EdgeWidth * size;

        double[] ColorAt(int i)
        {
            var t3 = shape.T[i] * 3; var ix = (int)Math.Floor(t3) % 3; var f = t3 - Math.Floor(t3);
            double[][] k = [k0, k1, k2];
            var c = Mix(k[ix], k[(ix + 1) % 3], f);
            c = Mix(c, [1, 1, 1], palette.Dark ? (shape.Z[i] + 1) * 0.05 : (shape.Z[i] + 1) * 0.07);
            return ghost ? Mix(c, mute, 0.6) : c;
        }

        void Seg(int i, double offset, double width, double[] color, double alpha, double extension)
        {
            double ax = (shape.X[i] + shape.NX[i] * offset) * size, ay = (shape.Y[i] + shape.NY[i] * offset) * size;
            double bx = (shape.X[i + 1] + shape.NX[i + 1] * offset) * size, by = (shape.Y[i + 1] + shape.NY[i + 1] * offset) * size;
            canvas.Stroke(ax, ay, bx, by, width / 2, color, alpha, extension);
        }

        // Soft contact shadow under the whole ribbon (not for the ghost).
        if (!ghost && last > 2) canvas.Shadow(shape, last, w, palette.Dark ? [0, 0, 0] : ink, palette.Dark ? 0.55 : 0.26);

        var brush = new[] { -0.40, -0.27, -0.14, 0, 0.12, 0.26, 0.39 };
        void FillSeg(int i)
        {
            var c = ColorAt(i);
            Seg(i, 0, w, c, 1, ext);
            if (ghost) return;
            Seg(i, -w * 0.43 / size, w * 0.16, Mix(c, [8 / 255.0, 12 / 255.0, 50 / 255.0], 0.34), 0.28, 0);
            Seg(i, w * 0.43 / size, w * 0.16, Mix(c, [8 / 255.0, 12 / 255.0, 50 / 255.0], 0.24), 0.20, 0);
            Seg(i, 0, w * 0.34, Mix(c, [1, 1, 1], 0.22), 0.26, 0);
            for (var k = 0; k < brush.Length; k++)
            {
                var s = Math.Sin(i * (0.021 + k * 0.0037) + k * 2.17 + Math.Sin(i * 0.008 + k * 1.3) * 2.2);
                var lighter = s > 0;
                Seg(i, brush[k] * RibbonWidth, w * (0.05 + 0.045 * Math.Abs(Math.Sin(k * 3.1))),
                    lighter ? Mix(c, [1, 1, 1], 0.34) : Mix(c, [8 / 255.0, 12 / 255.0, 50 / 255.0], 0.30),
                    lighter ? 0.14 + 0.12 * s : 0.10 - 0.10 * s, 0);
            }
        }
        void Pass(bool over)
        {
            for (var i = 0; i < last; i++)
                if (shape.Over[i] == over) Seg(i, 0, w + edge * 2, Mix(ColorAt(i), shade, palette.Dark ? 0.75 : 0.5), 0.9, ext);
            for (var i = 0; i < last; i++)
                if (shape.Over[i] == over) FillSeg(i);
        }
        Pass(false); Pass(true);
        if (ghost) canvas.Scale(0.34);

        canvas.Sun(SunX * size, SunY * size, SunRadius * size, sun, state, Math.Clamp(progress, 0, 1), size);
        return canvas.ToBgra();
    }

    private static double[] Rgb(string hex)
    {
        var (r, g, b, _) = Appearance.ParseHex(hex);
        return [r / 255.0, g / 255.0, b / 255.0];
    }

    private static double[] Mix(double[] a, double[] b, double f) =>
        [a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f];

    /// <summary>Float premultiplied RGBA accumulation buffer with anti-aliased capsule strokes.</summary>
    private sealed class Canvas(int size)
    {
        private readonly float[] _px = new float[size * size * 4];

        public void Stroke(double ax, double ay, double bx, double by, double half, double[] color, double alpha, double ext)
        {
            double dx = bx - ax, dy = by - ay, len = Math.Sqrt(dx * dx + dy * dy);
            if (len < 1e-6) return;
            double ux = dx / len, uy = dy / len;
            var pad = half + ext + 1.5;
            int x0 = Math.Max(0, (int)Math.Floor(Math.Min(ax, bx) - pad)), x1 = Math.Min(size - 1, (int)Math.Ceiling(Math.Max(ax, bx) + pad));
            int y0 = Math.Max(0, (int)Math.Floor(Math.Min(ay, by) - pad)), y1 = Math.Min(size - 1, (int)Math.Ceiling(Math.Max(ay, by) + pad));
            for (var y = y0; y <= y1; y++)
                for (var x = x0; x <= x1; x++)
                {
                    double vx = x + 0.5 - ax, vy = y + 0.5 - ay;
                    var along = vx * ux + vy * uy;
                    var across = Math.Abs(vx * -uy + vy * ux);
                    var cover = Math.Clamp(half - across + 0.5, 0, 1) * Math.Clamp(along + ext + 0.5, 0, 1) * Math.Clamp(len + ext - along + 0.5, 0, 1);
                    if (cover <= 0) continue;
                    Blend(x, y, color, cover * alpha);
                }
        }

        private void Blend(int x, int y, double[] c, double a)
        {
            var i = (y * size + x) * 4;
            var keep = (float)(1 - a);
            _px[i] = _px[i] * keep + (float)(c[0] * a);
            _px[i + 1] = _px[i + 1] * keep + (float)(c[1] * a);
            _px[i + 2] = _px[i + 2] * keep + (float)(c[2] * a);
            _px[i + 3] = _px[i + 3] * keep + (float)a;
        }

        public void Scale(double factor)
        {
            var f = (float)factor;
            for (var i = 0; i < _px.Length; i++) _px[i] *= f;
        }

        /// <summary>Blurred, slightly lowered copy of the ribbon's footprint, drawn first so everything sits on it.</summary>
        public void Shadow(Shape shape, int last, double width, double[] color, double strength)
        {
            var m = Math.Max(32, size / 4);
            var mask = new float[m * m];
            var half = width * m / size / 2;
            var offset = 0.021 * m;
            for (var i = 0; i < last; i++)
            {
                double ax = shape.X[i] * m, ay = shape.Y[i] * m + offset, bx = shape.X[i + 1] * m, by = shape.Y[i + 1] * m + offset;
                double dx = bx - ax, dy = by - ay, len = Math.Sqrt(dx * dx + dy * dy);
                if (len < 1e-6) continue;
                double ux = dx / len, uy = dy / len, pad = half + 2;
                int x0 = Math.Max(0, (int)(Math.Min(ax, bx) - pad)), x1 = Math.Min(m - 1, (int)(Math.Max(ax, bx) + pad));
                int y0 = Math.Max(0, (int)(Math.Min(ay, by) - pad)), y1 = Math.Min(m - 1, (int)(Math.Max(ay, by) + pad));
                for (var y = y0; y <= y1; y++)
                    for (var x = x0; x <= x1; x++)
                    {
                        double vx = x + 0.5 - ax, vy = y + 0.5 - ay;
                        var along = vx * ux + vy * uy;
                        var across = Math.Abs(vx * -uy + vy * ux);
                        var cover = Math.Clamp(half - across + 0.5, 0, 1) * Math.Clamp(along + half + 0.5, 0, 1) * Math.Clamp(len + half - along + 0.5, 0, 1);
                        if (cover > mask[y * m + x]) mask[y * m + x] = (float)cover;
                    }
            }
            var radius = Math.Max(2, (int)Math.Round(0.026 * m));
            var tmp = new float[m * m];
            for (var pass = 0; pass < 3; pass++) { BoxBlur(mask, tmp, m, radius, true); BoxBlur(tmp, mask, m, radius, false); }
            for (var y = 0; y < size; y++)
                for (var x = 0; x < size; x++)
                {
                    float fx = (x + 0.5f) * m / size - 0.5f, fy = (y + 0.5f) * m / size - 0.5f;
                    int ix = Math.Clamp((int)Math.Floor(fx), 0, m - 2), iy = Math.Clamp((int)Math.Floor(fy), 0, m - 2);
                    float tx = Math.Clamp(fx - ix, 0, 1), ty = Math.Clamp(fy - iy, 0, 1);
                    var v = (mask[iy * m + ix] * (1 - tx) + mask[iy * m + ix + 1] * tx) * (1 - ty) +
                            (mask[(iy + 1) * m + ix] * (1 - tx) + mask[(iy + 1) * m + ix + 1] * tx) * ty;
                    if (v > 0.002f) Blend(x, y, color, v * strength);
                }
        }

        private static void BoxBlur(float[] src, float[] dst, int m, int r, bool horizontal)
        {
            var window = 2 * r + 1;
            for (var line = 0; line < m; line++)
            {
                float sum = 0;
                for (var k = -r; k <= r; k++) sum += src[Index(line, Math.Clamp(k, 0, m - 1), m, horizontal)];
                for (var pos = 0; pos < m; pos++)
                {
                    dst[Index(line, pos, m, horizontal)] = sum / window;
                    sum += src[Index(line, Math.Min(m - 1, pos + r + 1), m, horizontal)] - src[Index(line, Math.Max(0, pos - r), m, horizontal)];
                }
            }
        }
        private static int Index(int line, int pos, int m, bool horizontal) => horizontal ? line * m + pos : pos * m + line;

        public void Sun(double cx, double cy, double radius, double[] sun, KnotState state, double progress, int px)
        {
            var scale = px / 380.0; // dash and glow sizes were drawn for a 380 px square
            if (state == KnotState.Connected)
            {
                var reach = radius + 46 * scale;
                for (var y = Math.Max(0, (int)(cy - reach)); y <= Math.Min(size - 1, (int)(cy + reach)); y++)
                    for (var x = Math.Max(0, (int)(cx - reach)); x <= Math.Min(size - 1, (int)(cx + reach)); x++)
                    {
                        var d = Math.Sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy));
                        if (d <= radius + 1) continue;
                        var g = Math.Exp(-Math.Pow((d - radius) / (17 * scale), 2)) * 0.55;
                        if (g > 0.004) Blend(x, y, sun, g);
                    }
                for (var y = Math.Max(0, (int)(cy - radius - 2)); y <= Math.Min(size - 1, (int)(cy + radius + 2)); y++)
                    for (var x = Math.Max(0, (int)(cx - radius - 2)); x <= Math.Min(size - 1, (int)(cx + radius + 2)); x++)
                    {
                        var d = Math.Sqrt((x + 0.5 - cx) * (x + 0.5 - cx) + (y + 0.5 - cy) * (y + 0.5 - cy));
                        var cover = Math.Clamp(radius - d + 0.5, 0, 1);
                        if (cover <= 0) continue;
                        var h = Math.Sqrt((x + 0.5 - (cx - radius * 0.3)) * (x + 0.5 - (cx - radius * 0.3)) + (y + 0.5 - (cy - radius * 0.3)) * (y + 0.5 - (cy - radius * 0.3)));
                        Blend(x, y, Mix(Mix(sun, [1, 1, 1], 0.38), sun, Math.Clamp(h / (radius * 1.5), 0, 1)), cover);
                    }
                return;
            }
            // Empty sun: a dashed ring; while connecting it fills in as the ribbon is woven.
            var fill = state == KnotState.Connecting ? radius * progress : 0;
            for (var y = Math.Max(0, (int)(cy - radius - 3)); y <= Math.Min(size - 1, (int)(cy + radius + 3)); y++)
                for (var x = Math.Max(0, (int)(cx - radius - 3)); x <= Math.Min(size - 1, (int)(cx + radius + 3)); x++)
                {
                    double vx = x + 0.5 - cx, vy = y + 0.5 - cy, d = Math.Sqrt(vx * vx + vy * vy);
                    var ring = Math.Clamp(0.8 * scale + 0.5 - Math.Abs(d - radius), 0, 1);
                    if (ring > 0)
                    {
                        var arc = (Math.Atan2(vy, vx) + Math.PI) * radius / scale;
                        if (arc % 8 < 3) Blend(x, y, sun, ring * 0.55);
                    }
                    if (fill > 0) { var cover = Math.Clamp(fill - d + 0.5, 0, 1); if (cover > 0) Blend(x, y, sun, cover * 0.45); }
                }
        }

        public byte[] ToBgra()
        {
            var bytes = new byte[_px.Length];
            for (var i = 0; i < _px.Length; i += 4)
            {
                bytes[i] = ToByte(_px[i + 2]);       // B
                bytes[i + 1] = ToByte(_px[i + 1]);   // G
                bytes[i + 2] = ToByte(_px[i]);       // R
                bytes[i + 3] = ToByte(_px[i + 3]);   // A
            }
            return bytes;
        }
        private static byte ToByte(float v) => (byte)Math.Clamp((int)Math.Round(v * 255f), 0, 255);
    }
}
