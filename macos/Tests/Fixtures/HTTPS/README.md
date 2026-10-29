# Isolated HTTPS fixture

The committed certificate and private key are intentionally public test data. Never use them for a real service. Tests start a loopback-only Python TLS server, explicitly trust this certificate for that request, and pin the fixture hostname to the listener. No OS trust store or Keychain is modified. Production uses the system certificate trust and the public-address socket policy.

Regenerate from this directory with `/usr/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -config certificate.conf -keyout test-only-key.pem -out test-only-cert.pem`. Commit the certificate and test key together. The certificate SAN intentionally covers only `https.fixture.invalid` so the wrong-host test must fail.
