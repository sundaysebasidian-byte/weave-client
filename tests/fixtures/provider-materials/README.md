# Public provider material fixtures

These certificate/key pairs are synthetic, public test data with the subject `public-test-fixture.invalid`. They are never production identities or installed into Keychain/trust stores. Native core `-t` tests use them to verify RSA PKCS#1/PKCS#8, ECDSA SEC1/PKCS#8 and Ed25519 PKCS#8 loading, mismatch rejection, and the exact data bytes behind generated runtime paths.

Regenerate in this directory with `GO111MODULE=off GOTOOLCHAIN=local go run generate.go` (Go 1.27.1 for the checked-in set). Generation uses only the standard library and replaces these fixture files with new synthetic keys. The bundled Mihomo binary and its separately locked build toolchain are unchanged.

`ssh-encrypted.pem` is the same public RSA test key encrypted with the public fixture passphrase `weave-public-fixture`. Regenerate with `/usr/bin/openssl rsa -in rsa-pkcs1.pem -aes256 -passout pass:weave-public-fixture -out ssh-encrypted.pem` (LibreSSL 3.3.6). It verifies legacy SSH PEM headers and passphrase handling without file-path fallback or interactive prompts.

`server.py` is a loopback-only HTTP CONNECT fixture that requires a client certificate. The Swift tests use `python3` from PATH with OpenSSL 1.1.1 or newer (CI selects Python 3.13); macOS `/usr/bin/python3` may use an older LibreSSL that cannot validate these identities. Unsupported SSL runtimes fail explicitly. Tests check the exact peer-certificate SHA-256 and forwarded body for RSA, ECDSA and Ed25519, and reject a missing certificate. No requests leave loopback, and no OS trust settings are changed. The synthetic server identity uses `skip-cert-verify` only in this isolated fixture.

The release audit pins each public private-key fixture by exact path and SHA-256. Regeneration requires reviewing and updating those pins in `tools/audit-local-release.sh`; the surrounding directory is not exempt from credential scanning. `tools/test-local-release-audit.py` verifies the known, changed and unlisted cases in temporary copies.
