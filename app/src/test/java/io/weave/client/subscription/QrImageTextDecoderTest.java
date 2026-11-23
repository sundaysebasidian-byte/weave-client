package io.weave.client.subscription;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.qrcode.QRCodeWriter;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class QrImageTextDecoderTest {
    private static final int SIZE = 320;
    private static final int BLACK = 0xff000000;
    private static final int WHITE = 0xffffffff;
    private static final String PAYLOAD = "https://example.invalid/subscription";

    @Test
    public void normalQrAndBarcodeRetainTheirText() throws Exception {
        BitMatrix qr = new QRCodeWriter().encode(PAYLOAD, BarcodeFormat.QR_CODE, SIZE, SIZE);
        assertEquals(PAYLOAD, QrImageTextDecoder.decode(image(qr, false)));

        String barcodeValue = "Weave local fixture";
        BitMatrix barcode = new Code128Writer().encode(barcodeValue, BarcodeFormat.CODE_128, SIZE, 100);
        assertEquals(barcodeValue, QrImageTextDecoder.decode(image(barcode, false)));
    }

    @Test
    public void invertedQrFromDarkImageRetainsItsText() throws Exception {
        BitMatrix qr = new QRCodeWriter().encode(PAYLOAD, BarcodeFormat.QR_CODE, SIZE, SIZE);
        assertEquals(PAYLOAD, QrImageTextDecoder.decode(image(qr, true)));
    }

    @Test
    public void solidLightOrDarkImagesStillReportNotFound() {
        int[] pixels = new int[SIZE * SIZE];
        for (int color : new int[]{BLACK, WHITE}) {
            Arrays.fill(pixels, color);
            try {
                QrImageTextDecoder.decode(new RGBLuminanceSource(SIZE, SIZE, pixels));
                fail("Uniform image must not produce a barcode");
            } catch (NotFoundException expected) {
                // Same no-code result as the normal-polarity reader.
            }
        }
    }

    private static RGBLuminanceSource image(BitMatrix matrix, boolean inverted) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = (matrix.get(x, y) ^ inverted) ? BLACK : WHITE;
            }
        }
        return new RGBLuminanceSource(width, height, pixels);
    }
}
