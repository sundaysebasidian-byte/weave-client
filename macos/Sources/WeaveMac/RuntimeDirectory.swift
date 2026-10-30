import Darwin
import Foundation

/// The core inherits the lease on stdin. A live orphan therefore cannot be reclaimed by
/// another app instance after its parent crashes. Unknown files and legacy runtimes stay intact.
final class RuntimeDirectory: @unchecked Sendable {
    let url: URL
    let lease: FileHandle
    private let lock = NSLock()
    private var closed = false

    static var defaultRoot: URL {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            .appendingPathComponent("Weave/runtime-sessions", isDirectory: true)
    }

    init(root: URL = defaultRoot) throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        guard try root.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink != true else {
            throw WeaveMacError.message("运行目录不能使用符号链接")
        }
        let root = root.resolvingSymlinksInPath().standardizedFileURL
        url = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: false, attributes: [.posixPermissions: 0o700])
        let descriptor = open(url.appendingPathComponent(".lease").path, O_CREAT | O_EXCL | O_RDWR | O_NOFOLLOW, 0o600)
        guard descriptor >= 0 else { throw WeaveMacError.message("无法建立运行目录锁") }
        lease = FileHandle(fileDescriptor: descriptor, closeOnDealloc: true)
        guard flock(descriptor, LOCK_EX | LOCK_NB) == 0 else { throw WeaveMacError.message("无法锁定运行目录") }
        try Data("weave-runtime-v1".utf8).write(to: url.appendingPathComponent(".owner"), options: .atomic)
    }

    @discardableResult
    func removeAfterCoreExit() throws -> Bool {
        lock.lock()
        defer { lock.unlock() }
        guard !closed else { return true }
        guard Self.hasOnlyRuntimeFiles(url) else { return false }
        try FileManager.default.removeItem(at: url)
        try lease.close()
        closed = true
        return true
    }

    /// Only versioned, exclusively lockable sessions are eligible. No PID/name/time heuristic.
    static func reclaimOrphans(root: URL = defaultRoot) throws -> Int {
        guard FileManager.default.fileExists(atPath: root.path) else { return 0 }
        guard try root.resourceValues(forKeys: [.isSymbolicLinkKey]).isSymbolicLink != true else { return 0 }
        let root = root.resolvingSymlinksInPath().standardizedFileURL
        var removed = 0
        for directory in try FileManager.default.contentsOfDirectory(at: root, includingPropertiesForKeys: [.isDirectoryKey, .isSymbolicLinkKey]) {
            let values = try directory.resourceValues(forKeys: [.isDirectoryKey, .isSymbolicLinkKey])
            guard values.isDirectory == true, values.isSymbolicLink != true,
                  UUID(uuidString: directory.lastPathComponent) != nil, hasOnlyRuntimeFiles(directory),
                  (try? String(contentsOf: directory.appendingPathComponent(".owner"), encoding: .utf8)) == "weave-runtime-v1" else { continue }
            let fd = open(directory.appendingPathComponent(".lease").path, O_RDWR | O_NOFOLLOW)
            guard fd >= 0 else { continue }
            defer { close(fd) }
            guard flock(fd, LOCK_EX | LOCK_NB) == 0 else { continue }
            guard hasOnlyRuntimeFiles(directory) else { continue }
            try FileManager.default.removeItem(at: directory)
            removed += 1
        }
        return removed
    }

    private static func hasOnlyRuntimeFiles(_ directory: URL) -> Bool {
        guard let items = try? FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isSymbolicLinkKey, .isRegularFileKey]) else { return false }
        for item in items {
            guard let values = try? item.resourceValues(forKeys: [.isSymbolicLinkKey, .isRegularFileKey]), values.isSymbolicLink != true else { return false }
            if item.lastPathComponent == "providers" {
                guard let providers = try? FileManager.default.contentsOfDirectory(at: item, includingPropertiesForKeys: [.isRegularFileKey, .isSymbolicLinkKey]) else { return false }
                for provider in providers {
                    guard provider.pathExtension == "yaml", UUID(uuidString: provider.deletingPathExtension().lastPathComponent) != nil,
                          let metadata = try? provider.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey]),
                          metadata.isRegularFile == true, metadata.isSymbolicLink != true else { return false }
                }
            } else if ![".lease", ".owner", "config.yaml", "cache.db"].contains(item.lastPathComponent) || values.isRegularFile != true {
                return false
            }
        }
        return true
    }
}

struct MacRuntime: Sendable {
    let directory: RuntimeDirectory
    let mixedPort: UInt16
    let controllerPort: UInt16
    let secret: String
    let tunDevice: String
    let readinessListenPort: UInt16
    let readinessTargetPort: UInt16
}
