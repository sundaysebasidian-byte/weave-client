import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;

// Independent reference: same DataOutputStream + AES/GCM format as Android LanTransferCodec.
// Fixed nonce/key only for this test fixture, never shipped into application logic.
class AndroidTransferInterop {
    static void field(DataOutputStream stream, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.writeInt(bytes.length); stream.write(bytes);
    }
    public static void main(String[] args) throws Exception {
        var out = new ByteArrayOutputStream();
        var stream = new DataOutputStream(out);
        stream.write("WVLAN001".getBytes(StandardCharsets.US_ASCII)); stream.writeInt(1);
        field(stream, "测试订阅");
        field(stream, "https://example.com/sub");
        field(stream, "proxies: [{name: test, type: http, server: example.com, port: 443}]");
        byte[] key = new byte[32], nonce = new byte[12];
        for (int i = 0; i < key.length; i++) key[i] = (byte)i;
        for (int i = 0; i < nonce.length; i++) nonce[i] = (byte)i;
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD("weave-lan-transfer-v1".getBytes(StandardCharsets.US_ASCII));
        var packet = new ByteArrayOutputStream();
        packet.write("WVENC001".getBytes(StandardCharsets.US_ASCII)); packet.write(nonce); packet.write(cipher.doFinal(out.toByteArray()));
        Files.write(Path.of(args[0]), packet.toByteArray());
    }
}
