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

context.setFillColor(CGColor(red: 32 / 255, green: 34 / 255, blue: 29 / 255, alpha: 1))
context.addPath(
    CGPath(
        roundedRect: CGRect(x: 48, y: 48, width: 928, height: 928),
        cornerWidth: 218,
        cornerHeight: 218,
        transform: nil
    )
)
context.fillPath()

context.setStrokeColor(CGColor(red: 56 / 255, green: 60 / 255, blue: 49 / 255, alpha: 1))
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

func ribbonPath(_ points: [CGPoint]) -> CGPath {
    let path = CGMutablePath()
    path.move(to: points[0])
    for point in points.dropFirst() {
        path.addLine(to: point)
    }
    return path
}

context.setLineCap(.round)
context.setLineJoin(.round)
context.setShadow(offset: CGSize(width: 0, height: -10), blur: 24, color: CGColor(gray: 0, alpha: 0.28))
context.setStrokeColor(CGColor(red: 247 / 255, green: 241 / 255, blue: 223 / 255, alpha: 1))
context.setLineWidth(118)
context.addPath(ribbonPath([
    CGPoint(x: 275, y: 700),
    CGPoint(x: 420, y: 305),
    CGPoint(x: 515, y: 555),
    CGPoint(x: 610, y: 305),
    CGPoint(x: 755, y: 700),
]))
context.strokePath()
context.setShadow(offset: .zero, blur: 0, color: nil)

context.setStrokeColor(CGColor(red: 214 / 255, green: 232 / 255, blue: 117 / 255, alpha: 1))
context.setLineWidth(70)
context.addPath(ribbonPath([
    CGPoint(x: 275, y: 700),
    CGPoint(x: 420, y: 305),
    CGPoint(x: 515, y: 555),
]))
context.strokePath()

context.setStrokeColor(CGColor(red: 142 / 255, green: 171 / 255, blue: 130 / 255, alpha: 1))
context.addPath(ribbonPath([
    CGPoint(x: 515, y: 555),
    CGPoint(x: 610, y: 305),
    CGPoint(x: 755, y: 700),
]))
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
