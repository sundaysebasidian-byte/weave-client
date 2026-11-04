import Darwin
import Foundation
import WeaveCoreBroker

/// The stable inode is never unlinked or explicitly unlocked: inherited descriptors keep
/// the lock held if the service disappears before its core. Closing only our copy is safe.
final class ServiceMachineLease: Sendable {
    let handle: FileHandle

    init(root: URL) throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true, attributes: [.posixPermissions: 0o700])
        let directory = open(root.path, O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC)
        guard directory >= 0 else { throw BrokerFailure.unavailable }
        defer { Darwin.close(directory) }
        var metadata = stat()
        guard fstat(directory, &metadata) == 0, metadata.st_uid == geteuid(), metadata.st_mode & 0o7077 == 0 else {
            throw BrokerFailure.unauthorized
        }
        do { try ProtectedInstallationPath.validateMetadata(directory, owner: geteuid()) }
        catch { throw BrokerFailure.unauthorized }
        let fd = openat(directory, ".service-lease", O_CREAT | O_RDWR | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC, 0o600)
        guard fd >= 0 else { throw BrokerFailure.unavailable }
        let opened = FileHandle(fileDescriptor: fd, closeOnDealloc: true)
        guard fstat(fd, &metadata) == 0, metadata.st_mode & S_IFMT == S_IFREG,
              metadata.st_uid == geteuid(), metadata.st_mode & 0o7177 == 0,
              metadata.st_nlink == 1, metadata.st_size == 0 else { throw BrokerFailure.unauthorized }
        do { try ProtectedInstallationPath.validateMetadata(fd, owner: geteuid()) }
        catch { throw BrokerFailure.unauthorized }
        guard flock(fd, LOCK_EX | LOCK_NB) == 0 else {
            throw errno == EWOULDBLOCK ? BrokerFailure.busy : BrokerFailure.unavailable
        }
        handle = opened
    }

    func close() throws { try handle.close() }
}
