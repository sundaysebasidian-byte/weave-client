import Foundation
import Security

public enum BrokerIdentity {
    public static let serviceName = "io.weave.client.macos.core-service"
    public static let appIdentifier = "io.weave.client.macos"

    /// Only Developer ID applications from this executable's validated signer may communicate.
    /// Ad-hoc previews never invent a team identity or relax the requirement.
    public static func productionRequirement(peerIdentifier: String) throws -> String {
        var code: SecCode?
        guard SecCodeCopySelf([], &code) == errSecSuccess, let code else { throw BrokerFailure.unauthorized }
        var staticCode: SecStaticCode?
        guard SecCodeCopyStaticCode(code, [], &staticCode) == errSecSuccess, let staticCode else { throw BrokerFailure.unauthorized }
        var info: CFDictionary?
        guard SecCodeCopySigningInformation(staticCode, SecCSFlags(rawValue: kSecCSSigningInformation), &info) == errSecSuccess,
              let info = info as? [String: Any], let team = info[kSecCodeInfoTeamIdentifier as String] as? String else {
            throw BrokerFailure.unauthorized
        }
        let requirement = try developerIDRequirement(team: team, identifier: peerIdentifier)
        let ownRequirement = try developerIDRequirement(team: team, identifier: info[kSecCodeInfoIdentifier as String] as? String ?? "")
        var parsed: SecRequirement?
        guard SecRequirementCreateWithString(ownRequirement as CFString, [], &parsed) == errSecSuccess, let parsed,
              SecCodeCheckValidity(code, SecCSFlags(rawValue: kSecCSStrictValidate), parsed) == errSecSuccess else { throw BrokerFailure.unauthorized }
        return requirement
    }

    static func developerIDRequirement(team: String, identifier: String) throws -> String {
        guard team.utf8.count == 10, team.utf8.allSatisfy({ (65...90).contains($0) || (48...57).contains($0) }),
              !identifier.isEmpty, identifier.utf8.count <= 128,
              identifier.utf8.allSatisfy({ (65...90).contains($0) || (97...122).contains($0) || (48...57).contains($0) || $0 == 46 || $0 == 45 }) else {
            throw BrokerFailure.unauthorized
        }
        let requirement = "anchor apple generic and certificate leaf[field.1.2.840.113635.100.6.1.13] exists and certificate leaf[subject.OU] = \"\(team)\" and identifier \"\(identifier)\" and ! entitlement[\"com.apple.security.get-task-allow\"] exists"
        try validate(requirement)
        return requirement
    }

    static func validate(_ requirement: String) throws {
        var parsed: SecRequirement?
        guard !requirement.isEmpty, SecRequirementCreateWithString(requirement as CFString, [], &parsed) == errSecSuccess else { throw BrokerFailure.unauthorized }
    }
}
