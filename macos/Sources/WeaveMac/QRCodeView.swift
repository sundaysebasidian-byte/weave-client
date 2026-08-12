import CoreImage.CIFilterBuiltins
import SwiftUI

struct QRCodeView: View {
    let value: String

    var body: some View {
        if let image = Self.generate(value) {
            Image(nsImage: image)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .accessibilityLabel("局域网传输二维码")
        } else {
            ContentUnavailableView("无法生成二维码", systemImage: "qrcode")
        }
    }

    private static func generate(_ value: String) -> NSImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: .init(scaleX: 8, y: 8)) else {
            return nil
        }
        let representation = NSCIImageRep(ciImage: output)
        let image = NSImage(size: representation.size)
        image.addRepresentation(representation)
        return image
    }
}
