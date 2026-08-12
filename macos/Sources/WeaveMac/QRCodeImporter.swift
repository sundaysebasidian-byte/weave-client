import AppKit
@preconcurrency import Vision

enum QRCodeImporter {
    @MainActor
    static func chooseAndRead() async throws -> String? {
        let panel = NSOpenPanel()
        panel.allowedContentTypes = [.image]
        panel.allowsMultipleSelection = false
        guard panel.runModal() == .OK, let url = panel.url else { return nil }
        guard let image = NSImage(contentsOf: url), let cgImage = image.cgImage(
            forProposedRect: nil,
            context: nil,
            hints: nil
        ) else {
            throw WeaveMacError.message("无法读取二维码图片")
        }
        return try await withCheckedThrowingContinuation { continuation in
            let request = VNDetectBarcodesRequest { request, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }
                let value = (request.results as? [VNBarcodeObservation])?
                    .first(where: { $0.symbology == .qr })?
                    .payloadStringValue
                if let value {
                    continuation.resume(returning: value)
                } else {
                    continuation.resume(throwing: WeaveMacError.message("图片中没有识别到二维码"))
                }
            }
            request.symbologies = [.qr]
            DispatchQueue.global(qos: .userInitiated).async {
                do {
                    try VNImageRequestHandler(cgImage: cgImage).perform([request])
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }
}
