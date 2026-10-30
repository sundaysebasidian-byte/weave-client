import CryptoKit
import Darwin
import Foundation
import Security
import WeaveCoreBroker

enum InstallationError: Error, Equatable { case unsafePath, invalidManifest, invalidSignature, changedCore }

struct CoreIntegrityManifest: Codable, Equatable {
    static let identifier = "io.weave.client.macos.mihomo"
    let schema: Int
    let architecture: String
    let coreIdentifier: String
    let sourceCoreSHA256: String
    let signedCoreSHA256: String
    let coreCommit: String

    init(data: Data) throws {
        guard data.count <= 4096 else { throw InstallationError.invalidManifest }
        self = try JSONDecoder().decode(Self.self, from: data)
        func hex(_ value: String, count: Int) -> Bool {
            value.utf8.count == count && value.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) }
        }
        guard schema == 1, architecture == "arm64", coreIdentifier == Self.identifier,
              hex(sourceCoreSHA256, count: 64), hex(signedCoreSHA256, count: 64), hex(coreCommit, count: 40) else {
            throw InstallationError.invalidManifest
        }
    }
}

/// An installation check, not an execution lease. The host must serialize updates and
/// retain machine/runtime ownership before launching. Root service entry remains closed.
package enum CoreInstallation {
    package static func validateManagedBundle(_ bundle: URL) throws -> URL {
        try ProtectedInstallationPath.validateTree(bundle)
        let appRequirement = try BrokerIdentity.productionRequirement(peerIdentifier: BrokerIdentity.appIdentifier)
        let coreRequirement = try BrokerIdentity.productionRequirement(peerIdentifier: CoreIntegrityManifest.identifier)
        _ = try validateContents(bundle, appRequirement: appRequirement, coreRequirement: coreRequirement)
        return bundle.appendingPathComponent("Contents/Resources/mihomo")
    }

    // Requirements are injected only inside this module's tests; the package entry
    // above always derives the Developer ID requirements from its authenticated signer.
    static func validateContents(_ bundle: URL, appRequirement: String, coreRequirement: String) throws -> CoreIntegrityManifest {
        try validateSignature(bundle, requirement: appRequirement)
        let resources = bundle.appendingPathComponent("Contents/Resources")
        let manifest = try CoreIntegrityManifest(data: boundedData(resources.appendingPathComponent("core-integrity.json"), limit: 4096))
        let lock = try boundedData(resources.appendingPathComponent("core-lock.properties"), limit: 16_384)
        guard let lockText = String(data: lock, encoding: .utf8) else { throw InstallationError.invalidManifest }
        func locked(_ key: String) throws -> String {
            let values = lockText.split(separator: "\n").filter { $0.hasPrefix(key + "=") }
            guard values.count == 1 else { throw InstallationError.invalidManifest }
            return String(values[0].dropFirst(key.count + 1))
        }
        guard try locked("mihomo.darwin-arm64.sha256") == manifest.sourceCoreSHA256,
              try locked("mihomo.commit") == manifest.coreCommit else { throw InstallationError.invalidManifest }
        let core = resources.appendingPathComponent("mihomo")
        try validateSignature(core, requirement: coreRequirement)
        let descriptor = open(core.path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
        guard descriptor >= 0 else { throw InstallationError.unsafePath }
        defer { close(descriptor) }
        var info = stat()
        guard fstat(descriptor, &info) == 0, info.st_mode & S_IFMT == S_IFREG,
              info.st_mode & 0o111 != 0, info.st_size > 0, info.st_size <= 128 * 1024 * 1024 else { throw InstallationError.unsafePath }
        let handle = FileHandle(fileDescriptor: descriptor, closeOnDealloc: false)
        var digest = SHA256(); var length = 0
        while let data = try handle.read(upToCount: 64 * 1024), !data.isEmpty {
            length += data.count
            guard length <= info.st_size else { throw InstallationError.changedCore }
            digest.update(data: data)
        }
        guard length == info.st_size, digest.finalize().map({ String(format: "%02x", $0) }).joined() == manifest.signedCoreSHA256 else {
            throw InstallationError.changedCore
        }
        return manifest
    }

    private static func boundedData(_ url: URL, limit: Int) throws -> Data {
        let descriptor = open(url.path, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
        guard descriptor >= 0 else { throw InstallationError.unsafePath }
        defer { close(descriptor) }
        var info = stat()
        guard fstat(descriptor, &info) == 0, info.st_mode & S_IFMT == S_IFREG,
              info.st_size > 0, info.st_size <= limit else { throw InstallationError.invalidManifest }
        let handle = FileHandle(fileDescriptor: descriptor, closeOnDealloc: false)
        let data = try handle.read(upToCount: limit + 1) ?? Data()
        guard data.count == info.st_size else { throw InstallationError.invalidManifest }
        return data
    }

    private static func validateSignature(_ url: URL, requirement: String) throws {
        var code: SecStaticCode?; var parsed: SecRequirement?
        let flags = SecCSFlags(rawValue: kSecCSStrictValidate | kSecCSCheckAllArchitectures | kSecCSCheckNestedCode | kSecCSRestrictSymlinks)
        guard SecStaticCodeCreateWithPath(url as CFURL, [], &code) == errSecSuccess, let code,
              SecRequirementCreateWithString(requirement as CFString, [], &parsed) == errSecSuccess, let parsed,
              SecStaticCodeCheckValidity(code, flags, parsed) == errSecSuccess else { throw InstallationError.invalidSignature }
    }
}

enum ProtectedInstallationPath {
    // The owner override is internal to ordinary-user filesystem tests. Production
    // calls use root and never accept a UID or path from a broker request.
    static func validateTree(_ url: URL, owner: uid_t = 0) throws {
        let descriptor = try openValidated(url, owner: owner)
        defer { close(descriptor) }
        var count = 0; var bytes: Int64 = 0
        try walk(descriptor, owner: owner, depth: 0, count: &count, bytes: &bytes)
    }

    /// The caller owns the returned descriptor; every ancestor has been checked.
    static func openValidated(_ url: URL, owner: uid_t = 0) throws -> Int32 {
        guard url.isFileURL, url.host == nil || url.host == "localhost",
              url.path == url.standardizedFileURL.path, url.path.utf8.count <= 4096 else { throw InstallationError.unsafePath }
        var descriptor = open("/", O_RDONLY | O_DIRECTORY | O_CLOEXEC)
        guard descriptor >= 0 else { throw InstallationError.unsafePath }
        var transferred = false
        defer { if !transferred { close(descriptor) } }
        try validateMetadata(descriptor, owner: owner)
        let components = url.pathComponents.dropFirst()
        guard !components.isEmpty, components.count <= 64 else { throw InstallationError.unsafePath }
        for component in components {
            guard component != ".", component != ".." else { throw InstallationError.unsafePath }
            let next = openat(descriptor, component, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
            guard next >= 0 else { throw InstallationError.unsafePath }
            close(descriptor); descriptor = next
            try validateMetadata(descriptor, owner: owner)
        }
        transferred = true
        return descriptor
    }

    private static func walk(_ descriptor: Int32, owner: uid_t, depth: Int, count: inout Int, bytes: inout Int64) throws {
        var info = stat()
        guard fstat(descriptor, &info) == 0, depth <= 32 else { throw InstallationError.unsafePath }
        count += 1
        guard count <= 2048 else { throw InstallationError.unsafePath }
        if info.st_mode & S_IFMT == S_IFREG {
            guard info.st_size <= 512 * 1024 * 1024 - bytes else { throw InstallationError.unsafePath }
            bytes += info.st_size
            return
        }
        let copy = dup(descriptor)
        guard copy >= 0 else { throw InstallationError.unsafePath }
        guard let directory = fdopendir(copy) else { close(copy); throw InstallationError.unsafePath }
        defer { closedir(directory) }
        while true {
            errno = 0
            guard let entry = readdir(directory) else {
                guard errno == 0 else { throw InstallationError.unsafePath }
                return
            }
            let length = Int(entry.pointee.d_namlen)
            let name = withUnsafeBytes(of: entry.pointee.d_name) { bytes -> String? in
                guard length > 0, length < bytes.count else { return nil }
                return String(bytes: bytes.prefix(length), encoding: .utf8)
            }
            guard let name else { throw InstallationError.unsafePath }
            if name == "." || name == ".." { continue }
            let child = openat(descriptor, name, O_RDONLY | O_NOFOLLOW | O_CLOEXEC | O_NONBLOCK)
            guard child >= 0 else { throw InstallationError.unsafePath }
            defer { close(child) }
            try validateMetadata(child, owner: owner)
            try walk(child, owner: owner, depth: depth + 1, count: &count, bytes: &bytes)
        }
    }

    static func validateMetadata(_ descriptor: Int32, owner: uid_t) throws {
        var info = stat(); var filesystem = statfs()
        guard fstat(descriptor, &info) == 0, fstatfs(descriptor, &filesystem) == 0,
              filesystem.f_flags & UInt32(MNT_LOCAL) != 0,
              info.st_uid == 0 || info.st_uid == owner, info.st_mode & 0o6022 == 0,
              info.st_mode & S_IFMT == S_IFDIR || (info.st_mode & S_IFMT == S_IFREG && info.st_nlink == 1 && info.st_size >= 0) else {
            throw InstallationError.unsafePath
        }
        guard let acl = acl_get_fd_np(descriptor, ACL_TYPE_EXTENDED) else {
            // Darwin reports an absent extended ACL as ENOENT, not an empty list.
            guard errno == ENOENT else { throw InstallationError.unsafePath }
            return
        }
        defer { acl_free(UnsafeMutableRawPointer(acl)) }
        guard acl_valid(acl) == 0 else { throw InstallationError.unsafePath }
        let writeMask = [ACL_WRITE_DATA, ACL_APPEND_DATA, ACL_DELETE, ACL_DELETE_CHILD, ACL_WRITE_ATTRIBUTES,
                         ACL_WRITE_EXTATTRIBUTES, ACL_WRITE_SECURITY, ACL_CHANGE_OWNER].reduce(UInt64(0)) { $0 | UInt64($1.rawValue) }
        var entry: acl_entry_t?; var next = ACL_FIRST_ENTRY
        while acl_get_entry(acl, Int32(next.rawValue), &entry) == 0 {
            next = ACL_NEXT_ENTRY
            guard let entry else { throw InstallationError.unsafePath }
            var tag = ACL_UNDEFINED_TAG; var mask: acl_permset_mask_t = 0
            guard acl_get_tag_type(entry, &tag) == 0, acl_get_permset_mask_np(entry, &mask) == 0,
                  tag == ACL_EXTENDED_DENY || (tag == ACL_EXTENDED_ALLOW && mask & writeMask == 0) else {
                throw InstallationError.unsafePath
            }
        }
        guard errno == EINVAL else { throw InstallationError.unsafePath }
    }
}
