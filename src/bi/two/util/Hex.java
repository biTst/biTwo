package bi.two.util;

public class Hex {
    private static final char[] HEX_DIGITS_UPPER_CASE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    private static final char[] HEX_DIGITS_LOWER_CASE = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    public static String bytesToHexUpperCase(byte[] bytes) {
        return bytesToHexUpperCase(bytes, HEX_DIGITS_UPPER_CASE);
    }

    public static String bytesToHexLowerCase(byte[] bytes) {
        return bytesToHexUpperCase(bytes, HEX_DIGITS_LOWER_CASE);
    }

    private static String bytesToHexUpperCase(byte[] bytes, char[] array) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            sb.append(array[(aByte & 0xf0) >> 4]).append(array[aByte & 0xf]);
        }
        return sb.toString();
    }
}
