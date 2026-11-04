import Foundation

package enum WeaveMacError: LocalizedError {
    case message(String)

    package var errorDescription: String? {
        switch self {
        case let .message(message): message
        }
    }
}
