import Darwin
import Foundation
import WeaveCoreBroker
import WeaveCoreService

/// An ordinary-user lifecycle fixture. It is never copied into the application bundle.
@main struct CoreLifecycleProbe {
    static func main() async throws {
        let arguments = CommandLine.arguments
        guard geteuid() != 0, arguments.count == 5, ["wait", "self-check"].contains(arguments[4]) else { exit(64) }
        let core = URL(fileURLWithPath: arguments[1])
        let root = URL(fileURLWithPath: arguments[2], isDirectory: true)
        let report = URL(fileURLWithPath: arguments[3])
        let service = try CoreService(executable: core, runtimeRoot: root, enableTun: false)
        let session = service.session(for: BrokerPeer(userID: getuid(), sessionID: 1, processID: getpid()))
        // Install the signal source before startup, including the configuration checker.
        signal(SIGTERM, SIG_IGN)
        let source = DispatchSource.makeSignalSource(signal: SIGTERM, queue: .global())
        let (signals, continuation) = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        // async main is MainActor-isolated; the Dispatch callback runs off that actor.
        source.setEventHandler { @Sendable in continuation.yield(()) }
        source.activate()
        defer { source.cancel(); continuation.finish() }
        do {
            let reply = await session.handle(.start(provider: "proxies: [{name: fixture, type: http, server: 127.0.0.1, port: 9}]", node: "fixture"))
            guard case .state(.running, let port) = reply, let port else {
                _ = await service.shutdown()
                try Data("start failed: \(reply)".utf8).write(to: report.appendingPathExtension("failure"))
                exit(1)
            }
            try JSONSerialization.data(withJSONObject: ["pid": getpid(), "pgid": getpgrp(), "mixedPort": port])
                .write(to: report, options: .atomic)
            if arguments[4] == "wait" { for await _ in signals { break } }
            guard await service.shutdown() else { exit(2) }
            try Data("stopped".utf8).write(to: report.appendingPathExtension("stopped"), options: .atomic)
        } catch {
            _ = await service.shutdown()
            throw error
        }
    }
}
