//go:build ignore

// Public, disposable test credentials. Run from this directory with Go's standard library.
package main

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"math/big"
	"os"
	"time"
)

func must(err error) {
	if err != nil {
		panic(err)
	}
}
func write(name, label string, data []byte) {
	must(os.WriteFile(name+".pem", pem.EncodeToMemory(&pem.Block{Type: label, Bytes: data}), 0644))
}
func pair(name string, key crypto.Signer) {
	template := &x509.Certificate{
		SerialNumber: big.NewInt(1), Subject: pkix.Name{CommonName: "public-test-fixture.invalid"},
		NotBefore: time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC), NotAfter: time.Date(2100, 1, 1, 0, 0, 0, 0, time.UTC),
		KeyUsage: x509.KeyUsageDigitalSignature, ExtKeyUsage: []x509.ExtKeyUsage{x509.ExtKeyUsageClientAuth},
	}
	cert, err := x509.CreateCertificate(rand.Reader, template, template, key.Public(), key)
	must(err)
	write(name+"-cert", "CERTIFICATE", cert)
	pkcs8, err := x509.MarshalPKCS8PrivateKey(key)
	must(err)
	write(name+"-pkcs8", "PRIVATE KEY", pkcs8)
}
func main() {
	r, err := rsa.GenerateKey(rand.Reader, 2048)
	must(err)
	pair("rsa", r)
	write("rsa-pkcs1", "RSA PRIVATE KEY", x509.MarshalPKCS1PrivateKey(r))
	e, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	must(err)
	pair("ecdsa", e)
	sec1, err := x509.MarshalECPrivateKey(e)
	must(err)
	write("ecdsa-sec1", "EC PRIVATE KEY", sec1)
	_, ed, err := ed25519.GenerateKey(rand.Reader)
	must(err)
	pair("ed25519", ed)
}
