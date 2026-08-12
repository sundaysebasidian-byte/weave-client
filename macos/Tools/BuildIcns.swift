import Foundation

guard CommandLine.arguments.count == 3 else {
    fatalError("usage: BuildIcns <iconset-directory> <output.icns>")
}

let directory = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
let entries = [
    ("icp4", "icon_16x16.png"),
    ("icp5", "icon_32x32.png"),
    ("icp6", "icon_32x32@2x.png"),
    ("ic07", "icon_128x128.png"),
    ("ic08", "icon_256x256.png"),
    ("ic09", "icon_512x512.png"),
    ("ic10", "icon_512x512@2x.png"),
]

var chunks = Data()
for (type, file) in entries {
    let png = try Data(contentsOf: directory.appendingPathComponent(file))
    chunks.append(Data(type.utf8))
    chunks.appendBigEndian(UInt32(png.count + 8))
    chunks.append(png)
}

var output = Data("icns".utf8)
output.appendBigEndian(UInt32(chunks.count + 8))
output.append(chunks)
try output.write(to: URL(fileURLWithPath: CommandLine.arguments[2]), options: .atomic)

private extension Data {
    mutating func appendBigEndian(_ value: UInt32) {
        append(UInt8((value >> 24) & 0xff))
        append(UInt8((value >> 16) & 0xff))
        append(UInt8((value >> 8) & 0xff))
        append(UInt8(value & 0xff))
    }
}
