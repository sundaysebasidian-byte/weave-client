"""Loopback mutual-TLS HTTP proxy. All identities are public synthetic fixture data."""
import hashlib
import json
from pathlib import Path
import socketserver
import ssl
import sys

if not ssl.OPENSSL_VERSION.startswith("OpenSSL ") or ssl.OPENSSL_VERSION_INFO < (1, 1, 1):
    raise RuntimeError("The mutual-TLS fixture requires Python with OpenSSL 1.1.1 or newer, including Ed25519 support")

root = Path(sys.argv[1])
materials = Path(__file__).parent
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(materials / "rsa-cert.pem", materials / "rsa-pkcs8.pem")
context.verify_mode = ssl.CERT_REQUIRED
context.load_verify_locations(cadata="\n".join((materials / (name + "-cert.pem")).read_text()
                                            for name in ("rsa", "ecdsa", "ed25519")))


class Proxy(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(3)
        peer = self.connection.getpeercert(binary_form=True)
        if not peer:
            return
        (root / "peer.sha256").write_text(hashlib.sha256(peer).hexdigest())
        first = self.rfile.readline(8192)
        while self.rfile.readline(8192) not in (b"\r\n", b"\n", b""):
            pass
        if not first.startswith(b"CONNECT 198.51.100.42:"):
            return
        self.wfile.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
        self.wfile.flush()
        first = self.rfile.readline(8192)
        while self.rfile.readline(8192) not in (b"\r\n", b"\n", b""):
            pass
        if not first.startswith(b"GET /material-check "):
            return
        body = b"weave-client-certificate-verified"
        self.wfile.write(b"HTTP/1.1 200 OK\r\nContent-Length: " + str(len(body)).encode()
                         + b"\r\nConnection: close\r\n\r\n" + body)


class Server(socketserver.ThreadingTCPServer):
    daemon_threads = True

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(3)
        try:
            return context.wrap_socket(connection, server_side=True), address
        except Exception as error:
            with (root / "tls-errors.txt").open("a") as log:
                log.write(str(error) + "\n")
            connection.close()
            raise


server = Server(("127.0.0.1", 0), Proxy)
(root / "port.json").write_text(json.dumps(server.server_address[1]))
server.serve_forever()
