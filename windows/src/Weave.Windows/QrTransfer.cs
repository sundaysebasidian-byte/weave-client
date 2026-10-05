using Weave.Windows.Core;
using Microsoft.UI.Xaml.Media.Imaging;
using System.Runtime.InteropServices.WindowsRuntime;
using Windows.Graphics.Imaging;
using Windows.Storage;
using ZXing;
using ZXing.Common;

namespace Weave.Windows;

internal static class QrTransfer
{
    public static async Task<WriteableBitmap> RenderAsync(string value)
    {
        var pixels = await Task.Run(() => new BarcodeWriterPixelData
        {
            Format = BarcodeFormat.QR_CODE,
            Options = new EncodingOptions { Width = 420, Height = 420, Margin = 3 },
        }.Write(value));
        var bitmap = new WriteableBitmap(pixels.Width, pixels.Height);
        using var stream = bitmap.PixelBuffer.AsStream();
        await stream.WriteAsync(pixels.Pixels);
        bitmap.Invalidate();
        return bitmap;
    }
    public static async Task<string> ReadAsync(string path)
    {
        if (new FileInfo(path).Length > 20 * 1024 * 1024) throw new InvalidDataException(L.T("二维码图片超过 20 MiB"));
        var file = await StorageFile.GetFileFromPathAsync(path);
        using var stream = await file.OpenReadAsync();
        var decoder = await BitmapDecoder.CreateAsync(stream);
        if ((long)decoder.PixelWidth * decoder.PixelHeight > 24_000_000) throw new InvalidDataException(L.T("二维码图片像素过大"));
        var scale = Math.Min(1.0, 1800.0 / Math.Max(decoder.PixelWidth, decoder.PixelHeight));
        var width = (uint)Math.Max(1, decoder.PixelWidth * scale);
        var height = (uint)Math.Max(1, decoder.PixelHeight * scale);
        var pixels = await decoder.GetPixelDataAsync(BitmapPixelFormat.Bgra8, BitmapAlphaMode.Ignore,
            new BitmapTransform { ScaledWidth = width, ScaledHeight = height },
            ExifOrientationMode.IgnoreExifOrientation, ColorManagementMode.DoNotColorManage);
        var data = pixels.DetachPixelData();
        return await Task.Run(() => Decode(data, (int)width, (int)height)) ?? throw new InvalidDataException(L.T("图片中未识别到二维码"));
    }
    public static string? Decode(byte[] data, int width, int height) => new BarcodeReaderGeneric
    {
        AutoRotate = true, Options = new DecodingOptions { TryHarder = true, PossibleFormats = new List<BarcodeFormat> { BarcodeFormat.QR_CODE } },
    }.Decode(new RGBLuminanceSource(data, width, height, RGBLuminanceSource.BitmapFormat.BGRA32))?.Text;
}
