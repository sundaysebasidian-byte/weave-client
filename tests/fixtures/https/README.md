# Isolated HTTPS fixture

The committed certificate and private key are intentionally public test data. Never use them for a real service. macOS Swift tests and Android JVM tests start this loopback-only Python TLS server, explicitly trust this certificate for that request, and pin the fixture hostname to the listener. No OS trust store or Keychain is modified. Production uses the system certificate trust and the public-address destination policy. The UDP fixture returns deterministic A/AAAA answers for isolated core DNS tests; it does not contact external DNS servers.

Regenerate from this directory with `/usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -config certificate.conf -keyout test-only-key.pem -out test-only-cert.pem`. Commit the certificate and test key together and update the reviewed key fingerprint in `tools/audit-local-release.sh`. The certificate SAN intentionally covers only `https.fixture.invalid` so the wrong-host test must fail.
