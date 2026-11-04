import Darwin
import WeaveCoreService

@main struct WeaveCoreServiceHost {
    static func main() async {
        do { try await CoreServiceHost.run() }
        catch {
            // Provider data, paths and credentials are never written to launchd logs.
            fputs("Weave core service could not start or finish shutdown.\n", stderr)
            exit(78)
        }
    }
}
