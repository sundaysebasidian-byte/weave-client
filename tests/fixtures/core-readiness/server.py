"""Loopback-only startup gate and synthetic HTTP proxy for native core tests."""
import http.server
import json
from pathlib import Path
import socketserver
import sys
import threading
import time

root = Path(sys.argv[1])


class Rules(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        (root / "requested").touch()
        deadline = time.monotonic() + 30
        while not (root / "release").exists():
            if time.monotonic() >= deadline:
                return
            time.sleep(0.02)
        body = b"payload:\n  - '+.weave-readiness.invalid'\n"
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class Upstream(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(5)
        first = self.rfile.readline(8192)
        if not first:  # A core health connection can close without a request.
            return
        while self.rfile.readline(8192) not in (b"\r\n", b"\n", b""):
            pass
        if first.startswith(b"CONNECT "):
            self.wfile.write(b"HTTP/1.1 200 Connection established\r\n\r\n")
            self.wfile.flush()
            first = self.rfile.readline(8192)
            while self.rfile.readline(8192) not in (b"\r\n", b"\n", b""):
                pass
        if not first:
            return
        body = b"weave-session-upstream"
        self.wfile.write(b"HTTP/1.1 200 OK\r\nContent-Length: " + str(len(body)).encode()
                         + b"\r\nConnection: close\r\n\r\n" + body)


class IncorrectProof(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(5)
        challenge = self.rfile.read(32)
        if self.server.kind == "echo":
            self.wfile.write(challenge)
        elif self.server.kind == "short":
            self.wfile.write(challenge[:8])
        else:
            self.rfile.read(1)  # The client must enforce its own timeout/cancellation.


class Server(socketserver.ThreadingTCPServer):
    daemon_threads = True


rules = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Rules)
upstream = Server(("127.0.0.1", 0), Upstream)
proofs = {kind: Server(("127.0.0.1", 0), IncorrectProof) for kind in ("echo", "short", "hang")}
for kind, server in proofs.items():
    server.kind = kind
for server in (rules, upstream, *proofs.values()):
    threading.Thread(target=server.serve_forever, daemon=True).start()
(root / "ports.json").write_text(json.dumps({"rules": rules.server_port,
                                           "upstream": upstream.server_address[1],
                                           **{kind: server.server_address[1] for kind, server in proofs.items()}}))
threading.Event().wait()
