import CoreImage.CIFilterBuiltins
import SwiftUI
import UIKit
@preconcurrency import Vision
import VisionKit
import WeaveCore

struct QRCodeImage: View {
    let value: String

    var body: some View {
        if let image = Self.generate(value) {
            Image(uiImage: image)
                .interpolation(.none)
                .resizable()
                .scaledToFit()
                .accessibilityLabel("局域网传输二维码")
        } else {
            ContentUnavailableView("无法生成二维码", systemImage: "qrcode")
        }
    }

    private static func generate(_ value: String) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(value.utf8)
        filter.correctionLevel = "M"
        guard let output = filter.outputImage?.transformed(by: .init(scaleX: 9, y: 9)) else {
            return nil
        }
        let context = CIContext(options: [.useSoftwareRenderer: false])
        guard let cgImage = context.createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

enum QRCodeDetector {
    static func detect(in data: Data) async throws -> String {
        guard data.count <= 20 * 1024 * 1024 else {
            throw WeaveError.message("二维码图片超过 20 MiB 限制")
        }
        guard let image = UIImage(data: data), let cgImage = image.cgImage else {
            throw WeaveError.message("无法读取二维码图片")
        }
        return try await withCheckedThrowingContinuation { continuation in
            let state = BarcodeContinuation(continuation)
            let request = VNDetectBarcodesRequest { request, error in
                if let error { state.fail(error); return }
                let value = (request.results as? [VNBarcodeObservation])?
                    .first(where: { $0.symbology == .qr })?
                    .payloadStringValue
                if let value { state.succeed(value) }
                else { state.fail(WeaveError.message("图片中没有识别到二维码")) }
            }
            request.symbologies = [.qr]
            DispatchQueue.global(qos: .userInitiated).async {
                do { try VNImageRequestHandler(cgImage: cgImage).perform([request]) }
                catch { state.fail(error) }
            }
        }
    }
}

private final class BarcodeContinuation: @unchecked Sendable {
    private let lock = NSLock()
    private var continuation: CheckedContinuation<String, Error>?

    init(_ continuation: CheckedContinuation<String, Error>) {
        self.continuation = continuation
    }

    func succeed(_ value: String) { finish(.success(value)) }
    func fail(_ error: Error) { finish(.failure(error)) }

    private func finish(_ result: Result<String, Error>) {
        lock.lock()
        defer { lock.unlock() }
        guard let continuation else { return }
        self.continuation = nil
        continuation.resume(with: result)
    }
}

#if targetEnvironment(macCatalyst)
struct CameraQRScanner: View {
    let onResult: (String) -> Void
    let onError: (String) -> Void

    var body: some View {
        ContentUnavailableView(
            "相机扫描只在 iPhone 上可用",
            systemImage: "qrcode.viewfinder"
        )
        .onAppear { onError("Mac Catalyst 不支持实时二维码扫描") }
    }
}
#else
struct CameraQRScanner: UIViewControllerRepresentable {
    let onResult: (String) -> Void
    let onError: (String) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    func makeUIViewController(context: Context) -> UIViewController {
        guard DataScannerViewController.isSupported,
              DataScannerViewController.isAvailable else {
            let controller = UIViewController()
            controller.view.backgroundColor = .systemBackground
            DispatchQueue.main.async { onError("此设备不支持实时二维码扫描") }
            return controller
        }
        let scanner = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.qr])],
            qualityLevel: .balanced,
            recognizesMultipleItems: false,
            isHighFrameRateTrackingEnabled: false,
            isPinchToZoomEnabled: true,
            isGuidanceEnabled: true,
            isHighlightingEnabled: true
        )
        scanner.delegate = context.coordinator
        DispatchQueue.main.async {
            do { try scanner.startScanning() }
            catch { onError(error.localizedDescription) }
        }
        return scanner
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        let parent: CameraQRScanner
        private var completed = false

        init(parent: CameraQRScanner) { self.parent = parent }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didTapOn item: RecognizedItem
        ) {
            accept(item)
        }

        func dataScanner(
            _ dataScanner: DataScannerViewController,
            didAdd addedItems: [RecognizedItem],
            allItems: [RecognizedItem]
        ) {
            if let item = addedItems.first { accept(item) }
        }

        private func accept(_ item: RecognizedItem) {
            guard !completed, case let .barcode(barcode) = item,
                  let payload = barcode.payloadStringValue else { return }
            completed = true
            parent.onResult(payload)
        }
    }
}
#endif
