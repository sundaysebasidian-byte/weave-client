import Darwin
import Foundation

package enum BoundedCommand {
    package static func run(executable: String, arguments: [String], timeout: TimeInterval = 5) throws -> String {
        // An anonymous file avoids pipe-buffer deadlocks, including a child holding stdout open.
        guard let output = tmpfile() else { throw WeaveMacError.message("无法创建命令输出缓冲区") }
        defer { fclose(output) }
        let handle = FileHandle(fileDescriptor: fileno(output), closeOnDealloc: false)
        let task = Process()
        let exited = DispatchSemaphore(value: 0)
        task.executableURL = URL(fileURLWithPath: executable)
        task.arguments = arguments
        task.standardOutput = handle
        task.standardError = handle
        task.terminationHandler = { _ in exited.signal() }
        try task.run()
        if exited.wait(timeout: .now() + timeout) == .timedOut {
            task.terminate()
            if exited.wait(timeout: .now() + 0.5) == .timedOut {
                if task.isRunning { kill(task.processIdentifier, SIGKILL) }
                _ = exited.wait(timeout: .now() + 1)
            }
            throw WeaveMacError.message("系统命令超时：\(URL(fileURLWithPath: executable).lastPathComponent)")
        }
        try handle.seek(toOffset: 0)
        let data = try handle.read(upToCount: 65_536) ?? Data()
        let text = String(decoding: data, as: UTF8.self)
        guard task.terminationStatus == 0 else {
            let lower = text.lowercased()
            if lower.contains("root") || lower.contains("authoriz") || lower.contains("permission") {
                throw WeaveMacError.message("macOS 拒绝修改系统代理；请允许网络设置更改")
            }
            throw WeaveMacError.message("系统命令失败：\(URL(fileURLWithPath: executable).lastPathComponent)（\(task.terminationStatus)）")
        }
        return text
    }
}
