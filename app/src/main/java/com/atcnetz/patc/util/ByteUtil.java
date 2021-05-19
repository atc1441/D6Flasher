package com.atcnetz.patc.util;


public class ByteUtil {
    protected static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String byteToBit(byte b) {
        return "" + ((byte) ((b >> 7) & 1)) + ((byte) ((b >> 6) & 1)) + ((byte) ((b >> 5) & 1)) + ((byte) ((b >> 4) & 1)) + ((byte) ((b >> 3) & 1)) + ((byte) ((b >> 2) & 1)) + ((byte) ((b >> 1) & 1)) + ((byte) ((b >> 0) & 1));
    }

    public static int bytes2IntIncludeSignBit(byte[] bytes) {
        if (bytes.length == 1) {
            return bytes[0];
        }
        if (bytes.length == 4) {
            return (((bytes[3] << 24) | ((bytes[2] << 24) >>> 8)) | ((bytes[1] << 24) >>> 16)) | ((bytes[0] << 24) >>> 24);
        }
        if (bytes.length == 2) {
            return (bytes[1] << 8) | ((bytes[0] << 24) >>> 24);
        }
        if (bytes.length == 3) {
            return ((bytes[2] << 16) | ((bytes[1] << 24) >>> 16)) | ((bytes[0] << 24) >>> 24);
        }
        return 0;
    }

    public static byte int2byte(int integer) {
        return (byte) (integer & 255);
    }

    public static byte[] getBooleanArray(byte b) {
        byte[] array = new byte[8];
        for (int i = 7; i >= 0; i--) {
            array[i] = (byte) (b & 1);
            b = (byte) (b >> 1);
        }
        return array;
    }



    public static byte[] intToByteBig(int i, int len) {
        if (len == 1) {
            byte[] abyte = new byte[len];
            abyte[0] = (byte) (i & 255);
            return abyte;
        }
        byte[] abyte = new byte[len];
        abyte[0] = (byte) ((i >>> 24) & 255);
        abyte[1] = (byte) ((i >>> 16) & 255);
        abyte[2] = (byte) ((i >>> 8) & 255);
        abyte[3] = (byte) (i & 255);
        return abyte;
    }

    public static int bytesToIntBig(byte[] bytes) {
        if (bytes.length == 1) {
            return bytes[0] & 255;
        }
        return ((((((bytes[0] & 255) << 8) | (bytes[1] & 255)) << 8) | (bytes[2] & 255)) << 8) | (bytes[3] & 255);
    }

    public static String bytesToString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        int length = bytes.length;
        for (int i = 0; i < length; i++) {
            stringBuilder.append(String.format("%02X", new Object[]{Byte.valueOf(bytes[i])}));
        }
        return stringBuilder.toString();
    }

    public static String bytesToStringFormat(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        int length = bytes.length;
        for (int i = 0; i < length; i++) {
            stringBuilder.append(String.format("%01X", new Object[]{Byte.valueOf(bytes[i])}));
        }
        return stringBuilder.toString();
    }

    public static int loword(int i) {
        return 65535 & i;
    }

    public static int hiword(int i) {
        return i >>> 8;
    }

    public static String binaryString2hexString(String bString) {
        if (bString == null || bString.equals("") || bString.length() % 8 != 0) {
            return null;
        }
        StringBuffer tmp = new StringBuffer();
        for (int i = 0; i < bString.length(); i += 4) {
            int iTmp = 0;
            for (int j = 0; j < 4; j++) {
                iTmp += Integer.parseInt(bString.substring(i + j, (i + j) + 1)) << ((4 - j) - 1);
            }
            tmp.append(Integer.toHexString(iTmp));
        }
        return tmp.toString();
    }

    public static String byteArrayToString(byte[] copyOfRange) {
        StringBuilder sb = new StringBuilder();
        for (byte b : copyOfRange) {
            String i = Integer.toHexString(b & 255);
            if (i.length() == 1) {
                i = "0" + i;
            }
            sb.append(i);
        }
        return sb.toString();
    }

    public static byte[] byteToBitArray(int b) {
        return new byte[]{(byte) ((b >> 7) & 1), (byte) ((b >> 6) & 1), (byte) ((b >> 5) & 1), (byte) ((b >> 4) & 1), (byte) ((b >> 3) & 1), (byte) ((b >> 2) & 1), (byte) ((b >> 1) & 1), (byte) ((b >> 0) & 1)};
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[(bytes.length * 2)];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 255;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[(j * 2) + 1] = hexArray[v & 15];
        }
        return new String(hexChars);
    }

    public static String bytesToString1(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        String format = "%02X ";
        int length = bytes.length;
        for (int i = 0; i < length; i++) {
            stringBuilder.append(String.format(format, new Object[]{Byte.valueOf(bytes[i])}));
        }
        return stringBuilder.toString();
    }

    public static byte[] hexToBytes(String hexStrings) {
        if (hexStrings == null || hexStrings.equals("")) {
            return null;
        }
        String hexString = hexStrings.toLowerCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] bytes = new byte[length];
        String hexDigits = "0123456789abcdef";
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            int h = hexDigits.indexOf(hexChars[pos]) << 4;
            int l = hexDigits.indexOf(hexChars[pos + 1]);
            if (h == -1 || l == -1) {
                return null;
            }
            bytes[i] = (byte) (h | l);
        }
        return bytes;
    }

    public static String byteAsciiToChar(int... ascii) {
        String str = "";
        for (int i : ascii) {
            str = str + ((char) i);
        }
        return str;
    }
}
