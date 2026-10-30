import Darwin
import Foundation
import WeaveCoreBroker

struct InstallationCleanupFailure: Error {
    let directory: URL
    let preparationError: (any Error)?
    let cleanupError: any Error
}

/// A private, verified snapshot plus an installation lease. The host must retain it and
/// pass the lease to every checker/core so updates remain excluded after parent death.
package final class ManagedCoreInstallation: Sendable {
    let coreURL: URL
    let runtimeRoot: URL
    let lease: FileHandle
    private let ownership: ServiceMachineLease
    private let bundle: URL
    private let owner: uid_t
    private let authorizesRoot: Bool

    private init(bundle: URL, runtimeRoot: URL, ownership: ServiceMachineLease, authorizesRoot: Bool = false) {
        self.bundle = bundle
        owner = geteuid()
        self.authorizesRoot = authorizesRoot
        coreURL = bundle.appendingPathComponent("Contents/Resources/mihomo")
        self.runtimeRoot = runtimeRoot
        self.ownership = ownership
        lease = ownership.handle
    }

    /// Only a root host with a valid Developer ID identity may prepare this fixed store.
    /// No broker request supplies a destination, UID, signing requirement or core path.
    package static func prepare(sourceBundle: URL) throws -> ManagedCoreInstallation {
        try CoreHostEnvironment.validateCurrentProcess()
        let appRequirement = try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.appIdentifier, ownIdentifier: BrokerIdentity.serviceName)
        let coreRequirement = try BrokerIdentity.productionRequirement(peerIdentifier: CoreIntegrityManifest.identifier, ownIdentifier: BrokerIdentity.serviceName)
        let library = try ProtectedInstallationPath.openValidated(URL(fileURLWithPath: "/Library"))
        defer { close(library) }
        let helpers = try directory(parent: library, name: "PrivilegedHelperTools", owner: 0, mode: 0o755, privateOnly: false)
        close(helpers)
        let snapshot = try prepare(sourceBundle: sourceBundle,
                           storeRoot: URL(fileURLWithPath: "/Library/PrivilegedHelperTools/io.weave.client.macos", isDirectory: true),
                           owner: 0, appRequirement: appRequirement, coreRequirement: coreRequirement)
        try CoreHostBundle.validate(snapshot.bundle)
        let hostRequirement = try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.serviceName,
                                                                       ownIdentifier: BrokerIdentity.serviceName)
        try CoreInstallation.validateSignature(snapshot.bundle.appendingPathComponent(CoreHostBundle.program), requirement: hostRequirement)
        return ManagedCoreInstallation(bundle: snapshot.bundle, runtimeRoot: snapshot.runtimeRoot,
                                       ownership: snapshot.ownership, authorizesRoot: true)
    }

    func validateExecutionAuthority() throws {
        guard geteuid() == owner, lease.fileDescriptor >= 0,
              fcntl(lease.fileDescriptor, F_GETFD) >= 0 else { throw BrokerFailure.unauthorized }
        if owner == 0 {
            guard authorizesRoot else { throw BrokerFailure.unauthorized }
            try CoreHostEnvironment.validateCurrentProcess()
        }
    }

    // Internal only: ordinary-user tests supply their own private root and ad-hoc requirements.
    static func prepare(sourceBundle: URL, storeRoot: URL, owner: uid_t,
                        appRequirement: String, coreRequirement: String) throws -> ManagedCoreInstallation {
        guard owner == geteuid(), sourceBundle.isFileURL, sourceBundle.pathExtension == "app",
              sourceBundle.host == nil || sourceBundle.host == "localhost",
              sourceBundle.path == sourceBundle.standardizedFileURL.path,
              sourceBundle.path.utf8.count <= 4096,
              storeRoot.path == storeRoot.standardizedFileURL.path,
              !storeRoot.path.hasPrefix(sourceBundle.path + "/") else { throw InstallationError.unsafePath }
        let parent = try ProtectedInstallationPath.openValidated(storeRoot.deletingLastPathComponent(), owner: owner)
        defer { close(parent) }
        let store = try directory(parent: parent, name: storeRoot.lastPathComponent, owner: owner)
        defer { close(store) }
        let ownership = try ServiceMachineLease(root: storeRoot)
        let versions = try directory(parent: store, name: "versions", owner: owner)
        defer { close(versions) }
        let runtime = try directory(parent: store, name: "runtime", owner: owner)
        close(runtime)

        // The source is untrusted and may be writable. Read through no-follow descriptors,
        // copy only bounded regular files, then verify the private copy before publishing it.
        let source = open(sourceBundle.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard source >= 0 else { throw InstallationError.unsafePath }
        defer { close(source) }
        let stageName = ".staging-\(UUID().uuidString).app"
        guard mkdirat(store, stageName, 0o700) == 0 else { throw InstallationError.unsafePath }
        let stageURL = storeRoot.appendingPathComponent(stageName, isDirectory: true)
        var staged = true
        let installed: URL
        do {
            let stage = openat(store, stageName, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
            guard stage >= 0 else { throw InstallationError.unsafePath }
            defer { close(stage) }
            var count = 0; var bytes: Int64 = 0
            try copyDirectory(source, to: stage, depth: 0, count: &count, bytes: &bytes)
            try ProtectedInstallationPath.validateTree(stageURL, owner: owner)
            let manifest = try CoreInstallation.validateContents(stageURL, appRequirement: appRequirement, coreRequirement: coreRequirement)
            let key = try CoreInstallation.snapshotKey(stageURL)
            let name = key + ".app"
            installed = storeRoot.appendingPathComponent("versions/" + name, isDirectory: true)
            var existing = stat()
            if fstatat(versions, name, &existing, AT_SYMLINK_NOFOLLOW) == 0 {
                // Preserve and reject a corrupt/unknown existing version; never overwrite it.
                try ProtectedInstallationPath.validateTree(installed, owner: owner)
                guard try CoreInstallation.validateContents(installed, appRequirement: appRequirement, coreRequirement: coreRequirement) == manifest,
                      try CoreInstallation.snapshotKey(installed) == key else {
                    throw InstallationError.changedCore
                }
            } else {
                guard errno == ENOENT, renameatx_np(store, stageName, versions, name, UInt32(RENAME_EXCL)) == 0 else {
                    throw InstallationError.unsafePath
                }
                staged = false
                guard fsync(versions) == 0, fsync(store) == 0 else { throw InstallationError.unsafePath }
            }
        } catch {
            if staged { try removeStage(stageURL, preparationError: error) }
            throw error
        }
        if staged { try removeStage(stageURL, preparationError: nil) }
        return ManagedCoreInstallation(bundle: installed, runtimeRoot: storeRoot.appendingPathComponent("runtime"), ownership: ownership)
    }

    private static func removeStage(_ url: URL, preparationError: (any Error)?) throws {
        do { try FileManager.default.removeItem(at: url) }
        catch { throw InstallationCleanupFailure(directory: url, preparationError: preparationError, cleanupError: error) }
    }

    private static func directory(parent: Int32, name: String, owner: uid_t, mode: mode_t = 0o700,
                                  privateOnly: Bool = true) throws -> Int32 {
        guard !name.isEmpty, name != ".", name != "..", !name.contains("/") else { throw InstallationError.unsafePath }
        let created = mkdirat(parent, name, mode) == 0
        if !created, errno != EEXIST { throw InstallationError.unsafePath }
        let fd = openat(parent, name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard fd >= 0 else { throw InstallationError.unsafePath }
        do {
            try ProtectedInstallationPath.validateMetadata(fd, owner: owner)
            var info = stat()
            guard fstat(fd, &info) == 0, info.st_uid == owner,
                  !privateOnly || info.st_mode & 0o7077 == 0 else { throw InstallationError.unsafePath }
            if created, fsync(parent) != 0 { throw InstallationError.unsafePath }
            return fd
        } catch { close(fd); throw error }
    }

    private static func sourceMetadata(_ fd: Int32) throws -> stat {
        var info = stat(); var filesystem = statfs()
        guard fstat(fd, &info) == 0, fstatfs(fd, &filesystem) == 0,
              filesystem.f_flags & UInt32(MNT_LOCAL) != 0,
              info.st_mode & S_IFMT == S_IFDIR || (info.st_mode & S_IFMT == S_IFREG && info.st_nlink == 1 && info.st_size >= 0) else {
            throw InstallationError.unsafePath
        }
        return info
    }

    private static func copyDirectory(_ source: Int32, to target: Int32, depth: Int, count: inout Int, bytes: inout Int64) throws {
        guard try sourceMetadata(source).st_mode & S_IFMT == S_IFDIR, depth <= 32 else { throw InstallationError.unsafePath }
        count += 1
        guard count <= 2048 else { throw InstallationError.unsafePath }
        let copied = dup(source)
        guard copied >= 0 else { throw InstallationError.unsafePath }
        guard let directory = fdopendir(copied) else { close(copied); throw InstallationError.unsafePath }
        defer { closedir(directory) }
        while true {
            errno = 0
            guard let entry = readdir(directory) else {
                guard errno == 0, fsync(target) == 0 else { throw InstallationError.unsafePath }
                return
            }
            let length = Int(entry.pointee.d_namlen)
            let name = withUnsafeBytes(of: entry.pointee.d_name) { bytes -> String? in
                guard length > 0, length < bytes.count else { return nil }
                return String(bytes: bytes.prefix(length), encoding: .utf8)
            }
            guard let name else { throw InstallationError.unsafePath }
            if name == "." || name == ".." { continue }
            let input = openat(source, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
            guard input >= 0 else { throw InstallationError.unsafePath }
            defer { close(input) }
            let info = try sourceMetadata(input)
            if info.st_mode & S_IFMT == S_IFDIR {
                guard mkdirat(target, name, 0o700) == 0 else { throw InstallationError.unsafePath }
                let output = openat(target, name, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
                guard output >= 0 else { throw InstallationError.unsafePath }
                defer { close(output) }
                try copyDirectory(input, to: output, depth: depth + 1, count: &count, bytes: &bytes)
            } else {
                count += 1
                guard count <= 2048, info.st_size <= 512 * 1024 * 1024 - bytes else { throw InstallationError.unsafePath }
                bytes += info.st_size
                let output = openat(target, name, O_CREAT | O_EXCL | O_WRONLY | O_NOFOLLOW | O_CLOEXEC,
                                    info.st_mode & 0o111 == 0 ? 0o600 : 0o700)
                guard output >= 0 else { throw InstallationError.unsafePath }
                defer { close(output) }
                let reader = FileHandle(fileDescriptor: input, closeOnDealloc: false)
                let writer = FileHandle(fileDescriptor: output, closeOnDealloc: false)
                var length: Int64 = 0
                while let data = try reader.read(upToCount: 64 * 1024), !data.isEmpty {
                    length += Int64(data.count)
                    guard length <= info.st_size else { throw InstallationError.changedCore }
                    try writer.write(contentsOf: data)
                }
                guard length == info.st_size, fsync(output) == 0 else { throw InstallationError.changedCore }
            }
        }
    }
}
