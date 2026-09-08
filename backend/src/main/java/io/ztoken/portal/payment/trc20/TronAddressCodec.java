package io.ztoken.portal.payment.trc20;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/** 将 TronGrid 事件返回的 0x 账户地址统一为地址池使用的 Base58Check 格式。 */
final class TronAddressCodec {
    private static final char[] BASE58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();

    private TronAddressCodec() { }

    static String normalizeAccountAddress(String address) {
        if (address == null || !address.matches("(?i)^0x[0-9a-f]{40}$")) return address;
        byte[] account = HexFormat.of().parseHex(address.substring(2));
        byte[] payload = new byte[21];
        payload[0] = 0x41;
        System.arraycopy(account, 0, payload, 1, account.length);
        byte[] encoded = Arrays.copyOf(payload, 25);
        System.arraycopy(sha256(sha256(payload)), 0, encoded, payload.length, 4);
        return base58(encoded);
    }

    private static byte[] sha256(byte[] source) {
        try { return MessageDigest.getInstance("SHA-256").digest(source); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("Java 环境不支持 SHA-256", exception); }
    }

    private static String base58(byte[] source) {
        byte[] input = Arrays.copyOf(source, source.length);
        int zeroes = 0;
        while (zeroes < input.length && input[zeroes] == 0) zeroes++;
        char[] output = new char[input.length * 2];
        int outputStart = output.length;
        for (int inputStart = zeroes; inputStart < input.length;) {
            int remainder = 0;
            for (int index = inputStart; index < input.length; index++) {
                int value = remainder * 256 + (input[index] & 0xFF);
                input[index] = (byte) (value / 58);
                remainder = value % 58;
            }
            if (input[inputStart] == 0) inputStart++;
            output[--outputStart] = BASE58[remainder];
        }
        while (zeroes-- > 0) output[--outputStart] = BASE58[0];
        return new String(output, outputStart, output.length - outputStart);
    }
}
