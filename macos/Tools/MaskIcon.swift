import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

guard CommandLine.arguments.count == 3 else {
    fatalError("usage: MaskIcon <input.png> <output.png>")
}

let inputURL = URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL
let outputURL = URL(fileURLWithPath: CommandLine.arguments[2]) as CFURL
guard
    let source = CGImageSourceCreateWithURL(inputURL, nil),
    let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
else {
    fatalError("cannot read input image")
}

let size = 1024
let colorSpace = CGColorSpaceCreateDeviceRGB()
guard let context = CGContext(
    data: nil,
    width: size,
    height: size,
    bitsPerComponent: 8,
    bytesPerRow: 0,
    space: colorSpace,
    bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
) else {
    fatalError("cannot create output context")
}

context.clear(CGRect(x: 0, y: 0, width: size, height: size))
context.saveGState()
context.addPath(
    CGPath(
        roundedRect: CGRect(x: 0, y: 0, width: size, height: size),
        cornerWidth: 188,
        cornerHeight: 188,
        transform: nil
    )
)
context.clip()
context.interpolationQuality = .high
context.draw(image, in: CGRect(x: 0, y: 0, width: size, height: size))
context.restoreGState()

guard
    let result = context.makeImage(),
    let destination = CGImageDestinationCreateWithURL(
        outputURL,
        UTType.png.identifier as CFString,
        1,
        nil
    )
else {
    fatalError("cannot create output image")
}
CGImageDestinationAddImage(destination, result, nil)
guard CGImageDestinationFinalize(destination) else {
    fatalError("cannot write output image")
}
