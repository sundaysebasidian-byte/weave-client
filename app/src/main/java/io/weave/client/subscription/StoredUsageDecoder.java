package io.weave.client.subscription;

/** Recognizes canonical ASCII metadata written by SubscriptionUsage.encode. */
final class StoredUsageDecoder {
    private StoredUsageDecoder() {}

    static SubscriptionUsage decodeAscii(String value) {
        long upload = 0, download = 0, total = 0, amount = 0;
        int field = 0;
        boolean hasDigit = false;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == ',') {
                if (!hasDigit) return null;
                switch (field++) {
                    case 0: upload = amount; break;
                    case 1: download = amount; break;
                    case 2: total = amount; break;
                    default: return null;
                }
                hasDigit = false;
                amount = 0;
            } else {
                int digit = ch - '0';
                if (digit < 0 || digit > 9 || amount > (Long.MAX_VALUE - digit) / 10) return null;
                hasDigit = true;
                amount = amount * 10 + digit;
            }
        }
        if (field != 3 || !hasDigit) return null;
        return new SubscriptionUsage(upload, download, total, amount == 0 ? null : amount);
    }
}
