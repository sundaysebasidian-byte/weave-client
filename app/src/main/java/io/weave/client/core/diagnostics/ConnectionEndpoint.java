package io.weave.client.core.diagnostics;

/** Text for the connection inspector, never an address used for dialing. */
final class ConnectionEndpoint {
    private ConnectionEndpoint() {}

    static String withPort(String host, String port) {
        if (host.indexOf(':') >= 0 && !(host.startsWith("[") && host.endsWith("]"))) {
            return "[" + host + "]:" + port;
        }
        return host + ":" + port;
    }
}
