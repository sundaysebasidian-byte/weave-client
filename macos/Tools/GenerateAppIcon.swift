import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

guard CommandLine.arguments.count == 2 else {
    fatalError("usage: GenerateAppIcon <output.png>")
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
    fatalError("cannot create icon context")
}

context.setFillColor(CGColor(red: 23 / 255, green: 26 / 255, blue: 24 / 255, alpha: 1))
context.addPath(
    CGPath(
        roundedRect: CGRect(x: 48, y: 48, width: 928, height: 928),
        cornerWidth: 218,
        cornerHeight: 218,
        transform: nil
    )
)
context.fillPath()

context.setStrokeColor(CGColor(red: 64 / 255, green: 80 / 255, blue: 65 / 255, alpha: 1))
context.setLineWidth(3)
context.addPath(
    CGPath(
        roundedRect: CGRect(x: 70, y: 70, width: 884, height: 884),
        cornerWidth: 192,
        cornerHeight: 192,
        transform: nil
    )
)
context.strokePath()

func ribbonPath(_ builder: (CGMutablePath) -> Void) -> CGPath {
    let path = CGMutablePath()
    builder(path)
    return path
}

context.setLineCap(.round)
context.setLineJoin(.round)
context.setShadow(offset: CGSize(width: 0, height: -8), blur: 22, color: CGColor(gray: 0, alpha: 0.30))
context.setStrokeColor(CGColor(red: 247 / 255, green: 241 / 255, blue: 223 / 255, alpha: 1))
context.setLineWidth(116)
context.addPath(ribbonPath { path in
    path.move(to: CGPoint(x: 276, y: 300))
    path.addCurve(
        to: CGPoint(x: 512, y: 430),
        control1: CGPoint(x: 360, y: 165),
        control2: CGPoint(x: 470, y: 205),
    )
    path.addCurve(
        to: CGPoint(x: 754, y: 724),
        control1: CGPoint(x: 560, y: 690),
        control2: CGPoint(x: 682, y: 850),
    )
})
context.strokePath()
context.setShadow(offset: .zero, blur: 0, color: nil)

context.setStrokeColor(CGColor(red: 201 / 255, green: 217 / 255, blue: 111 / 255, alpha: 1))
context.setLineWidth(72)
context.addPath(ribbonPath { path in
    path.move(to: CGPoint(x: 732, y: 300))
    path.addCurve(
        to: CGPoint(x: 512, y: 430),
        control1: CGPoint(x: 648, y: 165),
        control2: CGPoint(x: 554, y: 205),
    )
    path.addCurve(
        to: CGPoint(x: 282, y: 724),
        control1: CGPoint(x: 464, y: 690),
        control2: CGPoint(x: 346, y: 850),
    )
})
context.strokePath()

context.setStrokeColor(CGColor(red: 255 / 255, green: 255 / 255, blue: 255 / 255, alpha: 0.72))
context.setLineWidth(13)
context.addPath(ribbonPath { path in
    path.move(to: CGPoint(x: 276, y: 300))
    path.addCurve(
        to: CGPoint(x: 512, y: 430),
        control1: CGPoint(x: 360, y: 165),
        control2: CGPoint(x: 470, y: 205),
    )
})
context.strokePath()

guard
    let image = context.makeImage(),
    let destination = CGImageDestinationCreateWithURL(
        URL(fileURLWithPath: CommandLine.arguments[1]) as CFURL,
        UTType.png.identifier as CFString,
        1,
        nil
    )
else {
    fatalError("cannot create icon output")
}
CGImageDestinationAddImage(destination, image, nil)
guard CGImageDestinationFinalize(destination) else {
    fatalError("cannot write icon")
}
