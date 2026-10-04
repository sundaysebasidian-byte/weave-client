package io.weave.client.fixtures;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import java.io.*;

/** Test APK only; receives the owner's public Karing share on the isolated emulator. */
public final class KaringBackupFixtureReceiver extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String message;
        try {
            if (!Build.HARDWARE.equals("ranchu") || !Build.MODEL.startsWith("sdk_gphone") || !Intent.ACTION_SEND.equals(getIntent().getAction())) throw new SecurityException();
            Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null || !"content".equals(uri.getScheme()) || !"com.nebula.karing.flutter.share_provider".equals(uri.getAuthority())) throw new SecurityException();
            File file = new File(getExternalFilesDir(null), "WeaveKaringOfficialSynthetic109.zip");
            try (InputStream input = getContentResolver().openInputStream(uri); OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192]; int size = 0, count;
                while ((count = input.read(buffer)) >= 0) {
                    size += count; if (size > 5 * 1024 * 1024) throw new IOException("Fixture too large");
                    output.write(buffer, 0, count);
                }
            }
            message = "Official synthetic share captured: " + file.length() + " bytes";
        } catch (Exception error) { message = "Capture failed: " + error.getClass().getSimpleName(); }
        TextView text = new TextView(this); text.setText(message); setContentView(text);
    }
}
