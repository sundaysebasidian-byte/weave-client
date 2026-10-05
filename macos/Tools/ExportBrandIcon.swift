import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

// Export the imagegen-repaired raster into a transparent, premultiplied RGBA canvas.
// The geometric silhouette removes generated edge flecks; it cannot repair a black matte
// baked into the artwork. That repair must happen in the source image before this export.
guard CommandLine.arguments.count == 4,
      let source = CGImageSourceCreateWithURL(URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL, nil),
      let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    fatalError("usage: ExportBrandIcon <repaired-source.png> <output.png> <alpha|opaque>")
}
let opaque = CommandLine.arguments[3] == "opaque"
let rect = CGRect(x: 0, y: 0, width: 1024, height: 1024)
guard let context = CGContext(data: nil, width: 1024, height: 1024, bitsPerComponent: 8,
    bytesPerRow: 4096, space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: (opaque ? CGImageAlphaInfo.noneSkipLast : CGImageAlphaInfo.premultipliedLast).rawValue |
        CGBitmapInfo.byteOrder32Big.rawValue) else {
    fatalError("cannot create RGBA export context")
}
context.clear(rect)
if opaque {
    context.setFillColor(CGColor(red: 251/255, green: 244/255, blue: 222/255, alpha: 1))
    context.fill(rect)
}
context.saveGState()
context.addPath(CGPath(roundedRect: rect, cornerWidth: 220, cornerHeight: 220, transform: nil))
context.clip()
context.interpolationQuality = .high
context.draw(image, in: rect)
context.restoreGState()
guard let exported = context.makeImage(),
      let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath: CommandLine.arguments[2]) as CFURL,
        UTType.png.identifier as CFString, 1, nil) else { fatalError("cannot create PNG destination") }
CGImageDestinationAddImage(destination, exported, nil)
guard CGImageDestinationFinalize(destination) else { fatalError("cannot write PNG") }
