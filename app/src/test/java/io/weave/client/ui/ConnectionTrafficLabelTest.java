package io.weave.client.ui;

import java.text.Bidi;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ConnectionTrafficLabelTest {
    private static String visual(String input, int direction) {
        Bidi bidi = new Bidi(input, direction);
        byte[] levels = new byte[input.length()];
        Character[] chars = new Character[input.length()];
        for (int i = 0; i < input.length(); i++) {
            levels[i] = (byte) bidi.getLevelAt(i);
            chars[i] = input.charAt(i);
        }
        Bidi.reorderVisually(levels, 0, chars, 0, chars.length);
        StringBuilder visible = new StringBuilder();
        for (char c : chars) {
            if (c != '\u2066' && c != '\u2069') visible.append(c == '\u00A0' ? ' ' : c);
        }
        return visible.toString();
    }

    @Test public void asymmetricUploadAndDownloadKeepOwnUnitInRtl() {
        String label = ConnectionTrafficLabel.format("128 B", "4.0 KB");
        assertEquals("↓ 4.0 KB · ↑ 128 B", visual(label, Bidi.DIRECTION_RIGHT_TO_LEFT));
    }

    @Test public void englishOrderingAndValuesStayUnchanged() {
        String label = ConnectionTrafficLabel.format("128 B", "4.0 KB");
        assertEquals("↑ 128 B · ↓ 4.0 KB", visual(label, Bidi.DIRECTION_LEFT_TO_RIGHT));
    }
}
