using System.Diagnostics;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Graphics.Imaging;
using Windows.Media.Capture;
using Windows.Media.Capture.Frames;
using Windows.Media.MediaProperties;

namespace Weave.Windows;

internal sealed class CameraQrScanner : IAsyncDisposable
{
    private readonly MediaCapture _capture = new();
    private MediaFrameReader? _reader;
    private int _busy;
    private bool _stopping;
    private long _last;
    public event Action<byte[], int, int>? Preview;
    public event Action<string>? Found;
    public async Task StartAsync()
    {
        await _capture.InitializeAsync(new MediaCaptureInitializationSettings
        {
            StreamingCaptureMode = StreamingCaptureMode.Video,
            MemoryPreference = MediaCaptureMemoryPreference.Cpu,
            SharingMode = MediaCaptureSharingMode.SharedReadOnly,
        });
        var source = _capture.FrameSources.Values.FirstOrDefault(item => item.Info.SourceKind == MediaFrameSourceKind.Color)
            ?? throw new InvalidOperationException("未找到可用摄像头，请改用二维码图片或链接");
        _reader = await _capture.CreateFrameReaderAsync(source, MediaEncodingSubtypes.Bgra8, new BitmapSize { Width = 640, Height = 480 });
        _reader.AcquisitionMode = MediaFrameReaderAcquisitionMode.Realtime;
        _reader.FrameArrived += FrameArrived;
        if (await _reader.StartAsync() != MediaFrameReaderStartStatus.Success)
            throw new InvalidOperationException("摄像头启动失败，请检查 Windows 相机权限或改用图片");
    }
    private void FrameArrived(MediaFrameReader sender, MediaFrameArrivedEventArgs args)
    {
        if (_stopping || Stopwatch.GetElapsedTime(_last).TotalMilliseconds < 250 || Interlocked.Exchange(ref _busy, 1) != 0) return;
        try
        {
            _last = Stopwatch.GetTimestamp();
            using var frame = sender.TryAcquireLatestFrame();
            var bitmap = frame?.VideoMediaFrame?.SoftwareBitmap;
            if (bitmap is null) return;
            using var converted = SoftwareBitmap.Convert(bitmap, BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore);
            var bytes = new byte[checked(converted.PixelWidth * converted.PixelHeight * 4)];
            converted.CopyToBuffer(bytes.AsBuffer());
            Preview?.Invoke(bytes, converted.PixelWidth, converted.PixelHeight);
            var text = QrTransfer.Decode(bytes, converted.PixelWidth, converted.PixelHeight);
            if (!_stopping && text is not null) { _stopping = true; Found?.Invoke(text); }
        }
        catch (Exception error) when (error is System.Runtime.InteropServices.COMException or ObjectDisposedException or InvalidOperationException) { }
        finally { Volatile.Write(ref _busy, 0); }
    }
    public async ValueTask DisposeAsync()
    {
        _stopping = true;
        if (_reader is not null)
        {
            _reader.FrameArrived -= FrameArrived;
            try { await _reader.StopAsync(); } catch (System.Runtime.InteropServices.COMException) { }
            _reader.Dispose();
        }
        _capture.Dispose();
    }
}
