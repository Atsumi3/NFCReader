package info.nukoneko.android.nfcreader.util;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MiscUtilTest {
    @Test
    public void testByte2HexValidValue() throws Exception {
        byte[] bytes = new byte[]{
                (byte) 0xFF, 0x02, 0x33, (byte) 0xFA, 0x23, 0x22
        };

        assertThat(MiscUtil.bytesToHex(bytes), is("FF 02 33 FA 23 22"));

        bytes = new byte[]{
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        };

        assertThat(MiscUtil.bytesToHex(bytes), is("00 00 00 00 00 00"));
    }

    @Test
    public void testByte2HexEmptyValue() throws Exception {
        byte[] bytes = new byte[]{};

        assertThat(MiscUtil.bytesToHex(bytes), is(""));
    }
}
