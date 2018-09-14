package info.nukoneko.android.nfcreader.util;

final class MiscUtil {
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        StringBuilder ret = new StringBuilder();

        for (int i = 0; i < hexChars.length; i += 2) {
            ret.append(new String(new char[]{hexChars[i], hexChars[i + 1]}));
            if (hexChars.length - 2 != i) {
                ret.append(" ");
            }
        }

        return ret.toString();
    }
}
