import Darwin
import Foundation
import WeaveCoreBroker

enum CoreHostEnvironment {
    static func validateCurrentProcess() throws {
        try validate(userID: geteuid(), processID: getpid(), parentID: getppid(), groupID: getpgrp())
    }

    static func validate(userID: uid_t, processID: pid_t, parentID: pid_t, groupID: pid_t) throws {
        guard userID == 0, processID > 1, parentID == 1, groupID == processID else { throw BrokerFailure.unauthorized }
    }

    static func sourceBundle() throws -> URL {
        var path = [CChar](repeating: 0, count: 4 * Int(MAXPATHLEN))
        guard proc_pidpath(getpid(), &path, UInt32(path.count)) > 0 else { throw InstallationError.unsafePath }
        guard let end = path.firstIndex(of: 0),
              let value = String(bytes: path[..<end].map { UInt8(bitPattern: $0) }, encoding: .utf8) else {
            throw InstallationError.unsafePath
        }
        return try CoreHostBundle.sourceBundle(executable: URL(fileURLWithPath: value))
    }
}

enum CoreHostBundle {
    static let program = "Contents/MacOS/WeaveCoreServiceHost"
    static let plist = "Contents/Library/LaunchDaemons/" + BrokerIdentity.serviceName + ".plist"

    static func sourceBundle(executable: URL) throws -> URL {
        let bundle = executable.deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        guard executable.isFileURL, executable.host == nil || executable.host == "localhost",
              executable.path == executable.standardizedFileURL.path, bundle.pathExtension == "app",
              executable.path == bundle.appendingPathComponent(program).path else { throw InstallationError.unsafePath }
        return bundle
    }

    static func validate(_ bundle: URL) throws {
        let fd = open(bundle.appendingPathComponent(plist).path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
        guard fd >= 0 else { throw InstallationError.unsafePath }
        defer { close(fd) }
        var info = stat()
        guard fstat(fd, &info) == 0, info.st_mode & S_IFMT == S_IFREG,
              info.st_nlink == 1, info.st_size > 0, info.st_size <= 16_384 else { throw InstallationError.unsafePath }
        let data = try FileHandle(fileDescriptor: fd, closeOnDealloc: false).read(upToCount: 16_385) ?? Data()
        guard data.count == info.st_size else { throw InstallationError.changedCore }
        let value = try PropertyListSerialization.propertyList(from: data, options: [], format: nil)
        let policy: [String: Any] = [
            "Label": BrokerIdentity.serviceName, "BundleProgram": program,
            "MachServices": [BrokerIdentity.serviceName: true], "UserName": "root",
            "RunAtLoad": true, "KeepAlive": false, "AbandonProcessGroup": false,
            "ExitTimeOut": 5, "ProcessType": "Interactive",
        ]
        guard let dictionary = value as? NSDictionary, dictionary.isEqual(to: policy) else {
            throw InstallationError.invalidManifest
        }
    }
}

/// Owns only this process's termination sources. The Dispatch callbacks do not inherit
/// async main's actor isolation, and an early signal remains buffered during preparation.
package final class ServiceTermination: @unchecked Sendable {
    package let events: AsyncStream<Void>
    private let continuation: AsyncStream<Void>.Continuation
    private let sources: [DispatchSourceSignal]

    package init() {
        let pair = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        events = pair.stream
        continuation = pair.continuation
        sources = [SIGTERM, SIGINT].map { number in
            signal(number, SIG_IGN)
            let source = DispatchSource.makeSignalSource(signal: number, queue: .global())
            source.setEventHandler { @Sendable in pair.continuation.yield(()) }
            source.activate()
            return source
        }
    }

    deinit { sources.forEach { $0.cancel() }; continuation.finish() }
}

package enum CoreServiceHost {
    package static func run() async throws {
        guard CommandLine.arguments.count == 1 else { throw BrokerFailure.unauthorized }
        try CoreHostEnvironment.validateCurrentProcess()
        let termination = ServiceTermination()
        let installation = try ManagedCoreInstallation.prepare(sourceBundle: CoreHostEnvironment.sourceBundle())
        let service = try CoreService(installation: installation, enableTun: true)
        let listener = try BrokerListener { service.session(for: $0) }
        try await serve(service: service, listener: listener, termination: termination)
    }

    package static func serve(service: CoreService, listener: BrokerListener, termination: ServiceTermination) async throws {
        listener.resume()
        for await _ in termination.events { break }
        await listener.stop()
        guard await service.shutdown() else { throw BrokerFailure.stopFailed }
    }
}
