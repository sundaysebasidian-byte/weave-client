package io.weave.client.subscription;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.ReaderException;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

/** Decodes local image pixels. Inverted colors are supported only for QR codes. */
final class QrImageTextDecoder {
    private QrImageTextDecoder() {}

    static String decode(LuminanceSource source) throws NotFoundException {
        MultiFormatReader reader = new MultiFormatReader();
        try {
            return reader.decode(new BinaryBitmap(new HybridBinarizer(source))).getText();
        } catch (NotFoundException ignored) {
            reader.reset();
        }
        NotFoundException normalFailure;
        try {
            return reader.decode(new BinaryBitmap(new GlobalHistogramBinarizer(source))).getText();
        } catch (NotFoundException notFound) {
            normalFailure = notFound;
            reader.reset();
        }
        // Dark screenshots can contain light modules on a dark background.
        try {
            return new QRCodeReader()
                    .decode(new BinaryBitmap(new HybridBinarizer(source.invert()))).getText();
        } catch (ReaderException ignored) {
            throw normalFailure;
        }
    }
}
