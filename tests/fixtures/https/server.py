import gzip
import http.server
import json
import pathlib
import os
import signal
import socket
import struct
import ssl
import sys
import threading
import time

root = pathlib.Path(sys.argv[1])
signal.signal(signal.SIGTERM, lambda *_: os._exit(0))
fixture = pathlib.Path(__file__).parent
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(fixture / 'test-only-cert.pem', fixture / 'test-only-key.pem')
log_lock = threading.Lock()
def record(event):
    with log_lock, (root / 'events.jsonl').open('a') as stream:
        stream.write(json.dumps(event) + '\n')
context.set_servername_callback(lambda sock, name, ctx: record({'event': 'sni', 'name': name}))

dns = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
dns.bind(('127.0.0.1', 0))
(root / 'dns-port.txt').write_text(str(dns.getsockname()[1]))
def answer_dns():
    while True:
        packet, peer = dns.recvfrom(4096)
        if len(packet) < 17:
            continue
        index = 12
        while index < len(packet) and packet[index]:
            index += packet[index] + 1
        if index + 5 > len(packet):
            continue
        kind = struct.unpack('!H', packet[index + 1:index + 3])[0]
        payload = socket.inet_pton(socket.AF_INET, '8.8.8.8') if kind == 1 else socket.inet_pton(socket.AF_INET6, '2606:4700:4700::1111')
        question = packet[12:index + 5]
        answer = b'\xc0\x0c' + struct.pack('!HHIH', kind, 1, 60, len(payload)) + payload
        dns.sendto(packet[:2] + struct.pack('!HHHHH', 0x8180, 1, 1, 0, 0) + question + answer, peer)
threading.Thread(target=answer_dns, daemon=True).start()

class Server(http.server.ThreadingHTTPServer):
    daemon_threads = True
    def get_request(self):
        sock, address = super().get_request()
        record({'event': 'accept'})
        try:
            return context.wrap_socket(sock, server_side=True), address
        except BaseException:
            sock.close()
            raise

class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.1'
    def log_message(self, *args):
        pass
    def do_GET(self):
        record({'event': 'request', 'path': self.path, 'host': self.headers.get('Host')})
        path = self.path.split('?', 1)[0]
        if path == '/hang':
            time.sleep(40)
            return
        if path == '/slow-body':
            self.send_response(200)
            self.send_header('Content-Length', '100')
            self.end_headers()
            self.wfile.write(b'p')
            self.wfile.flush()
            record({'event': 'body-start'})
            time.sleep(40)
            return
        if path in ['/redirect', '/loop', '/private', '/downgrade', '/credentials']:
            target = {'/redirect': '/success', '/loop': '/loop', '/private': 'https://localhost:9/',
                      '/downgrade': 'http://https.fixture.invalid:9/',
                      '/credentials': 'https://user:private-token@https.fixture.invalid:9/'}[path]
            self.send_response(302)
            self.send_header('Location', target)
            self.send_header('Content-Length', '0')
            self.end_headers()
            return
        body = b'proxies: []'
        if path == '/invalid-utf8':
            body = b'\xff'
        if path == '/gzip-bomb':
            body = gzip.compress(b'x' * 5242881)
        self.send_response(503 if path == '/server-error' else 200)
        if path == '/gzip-bomb':
            self.send_header('Content-Encoding', 'gzip')
        if path == '/huge-headers':
            self.send_header('X-Padding', 'x' * 70000)
        if path != '/chunked':
            self.send_header('Content-Length', '5242881' if path == '/too-large' else str(len(body)))
        else:
            self.send_header('Transfer-Encoding', 'chunked')
        self.end_headers()
        try:
            if path == '/chunked':
                self.wfile.write(b'8\r\nproxies:\r\n3\r\n []\r\n0\r\n\r\n')
            else:
                self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError, ssl.SSLError):
            pass

server = Server(('127.0.0.1', 0), Handler)
(root / 'port.txt').write_text(str(server.server_port))
server.serve_forever()
