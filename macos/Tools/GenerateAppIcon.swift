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

func color(_ red: CGFloat, _ green: CGFloat, _ blue: CGFloat, _ alpha: CGFloat = 1) -> CGColor {
    CGColor(red: red / 255, green: green / 255, blue: blue / 255, alpha: alpha)
}

context.setFillColor(color(241, 235, 221))
context.addPath(
    CGPath(
        roundedRect: CGRect(x: 48, y: 48, width: 928, height: 928),
        cornerWidth: 218,
        cornerHeight: 218,
        transform: nil
    )
)
context.fillPath()

context.setStrokeColor(color(216, 207, 191, 0.92))
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

func stroke(_ path: CGPath, color: CGColor, width: CGFloat) {
    context.setStrokeColor(color)
    context.setLineWidth(width)
    context.addPath(path)
    context.strokePath()
}

context.setLineCap(.round)
context.setLineJoin(.round)
context.setShadow(offset: CGSize(width: 0, height: -8), blur: 22, color: CGColor(gray: 0, alpha: 0.18))
let indigo = color(36, 56, 92)
stroke(ribbonPath { path in
    path.move(to: CGPoint(x: 560, y: 515))
    path.addCurve(to: CGPoint(x: 460, y: 250), control1: CGPoint(x: 465, y: 445), control2: CGPoint(x: 395, y: 320))
    path.addCurve(to: CGPoint(x: 685, y: 285), control1: CGPoint(x: 525, y: 180), control2: CGPoint(x: 635, y: 210))
    path.addCurve(to: CGPoint(x: 630, y: 505), control1: CGPoint(x: 745, y: 375), control2: CGPoint(x: 690, y: 455))
}, color: indigo, width: 116)
stroke(ribbonPath { path in
    path.move(to: CGPoint(x: 520, y: 480))
    path.addCurve(to: CGPoint(x: 235, y: 500), control1: CGPoint(x: 430, y: 405), control2: CGPoint(x: 285, y: 390))
    path.addCurve(to: CGPoint(x: 420, y: 700), control1: CGPoint(x: 180, y: 625), control2: CGPoint(x: 300, y: 735))
    path.addCurve(to: CGPoint(x: 560, y: 535), control1: CGPoint(x: 520, y: 675), control2: CGPoint(x: 555, y: 590))
}, color: indigo, width: 116)
stroke(ribbonPath { path in
    path.move(to: CGPoint(x: 610, y: 500))
    path.addCurve(to: CGPoint(x: 850, y: 610), control1: CGPoint(x: 730, y: 445), control2: CGPoint(x: 845, y: 495))
    path.addCurve(to: CGPoint(x: 625, y: 760), control1: CGPoint(x: 855, y: 730), control2: CGPoint(x: 735, y: 820))
    path.addCurve(to: CGPoint(x: 540, y: 550), control1: CGPoint(x: 535, y: 710), control2: CGPoint(x: 515, y: 625))
}, color: indigo, width: 116)
context.setShadow(offset: .zero, blur: 0, color: nil)

stroke(ribbonPath { path in
    path.move(to: CGPoint(x: 300, y: 420))
    path.addCurve(to: CGPoint(x: 745, y: 760), control1: CGPoint(x: 430, y: 515), control2: CGPoint(x: 560, y: 650))
}, color: color(122, 169, 161), width: 72)
stroke(ribbonPath { path in
    path.move(to: CGPoint(x: 395, y: 760))
    path.addCurve(to: CGPoint(x: 760, y: 300), control1: CGPoint(x: 480, y: 620), control2: CGPoint(x: 605, y: 455))
}, color: color(181, 168, 200), width: 72)

context.setFillColor(color(220, 157, 146))
context.fillEllipse(in: CGRect(x: 745, y: 245, width: 50, height: 50))

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
