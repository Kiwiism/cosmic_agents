package server.agents.diagnostics;

import java.util.ArrayList;
import java.util.List;

public final class MovementPacketDecoder {
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private MovementPacketDecoder() {
    }

    public static List<String> decode(byte[] data) {
        List<String> lines = new ArrayList<>();
        if (data == null) {
            lines.add("movement data is null");
            return lines;
        }
        if (data.length == 0) {
            lines.add("movement data is truncated before fragment count");
            return lines;
        }

        Cursor cursor = new Cursor(data);
        int fragmentCount = cursor.readUnsignedByte();
        lines.add("fragmentCount=" + fragmentCount);

        for (int index = 0; index < fragmentCount; index++) {
            if (!cursor.has(1)) {
                lines.add("fragment[" + index + "] truncated before command");
                break;
            }

            int command = cursor.readUnsignedByte();
            int required = payloadLength(command);
            if (required < 0) {
                lines.add("fragment[" + index + "] cmd=" + command
                        + " unknown; remaining payload length cannot be determined");
                break;
            }
            if (!cursor.has(required)) {
                lines.add("fragment[" + index + "] cmd=" + command + " truncated: need "
                        + required + " payload bytes, have " + cursor.remaining());
                break;
            }

            lines.add(decodeFragment(cursor, index, command));
        }

        if (cursor.remaining() > 0) {
            lines.add("trailingBytes=" + hex(cursor.readBytes(cursor.remaining())));
        }
        return lines;
    }

    public static String hex(byte[] data) {
        if (data == null) {
            return "<null>";
        }
        if (data.length == 0) {
            return "";
        }

        char[] output = new char[data.length * 3 - 1];
        for (int i = 0; i < data.length; i++) {
            int value = Byte.toUnsignedInt(data[i]);
            int offset = i * 3;
            output[offset] = HEX_DIGITS[value >>> 4];
            output[offset + 1] = HEX_DIGITS[value & 0x0F];
            if (i + 1 < data.length) {
                output[offset + 2] = ' ';
            }
        }
        return new String(output);
    }

    private static String decodeFragment(Cursor cursor, int index, int command) {
        String prefix = "fragment[" + index + "] cmd=" + command + ' ';
        return switch (command) {
            case 0, 5, 17 -> prefix + "absolute"
                    + " x=" + cursor.readShort()
                    + " y=" + cursor.readShort()
                    + " xWobble=" + cursor.readShort()
                    + " yWobble=" + cursor.readShort()
                    + " fh=" + cursor.readShort()
                    + " stance=" + stance(cursor.readUnsignedByte())
                    + " duration=" + cursor.readUnsignedShort();
            case 1, 2, 6, 12, 13, 16, 18, 19, 20, 22 -> prefix + "relative"
                    + " x=" + cursor.readShort()
                    + " y=" + cursor.readShort()
                    + " stance=" + stance(cursor.readUnsignedByte())
                    + " duration=" + cursor.readUnsignedShort();
            case 3, 4, 7, 8, 9, 11 -> prefix + "teleport"
                    + " x=" + cursor.readShort()
                    + " y=" + cursor.readShort()
                    + " xWobble=" + cursor.readShort()
                    + " yWobble=" + cursor.readShort()
                    + " stance=" + stance(cursor.readUnsignedByte());
            case 10 -> prefix + "changeEquip value=" + cursor.readUnsignedByte();
            case 14 -> prefix + "jumpDownUnknown payload=" + hex(cursor.readBytes(9));
            case 15 -> prefix + "jumpDown"
                    + " x=" + cursor.readShort()
                    + " y=" + cursor.readShort()
                    + " xWobble=" + cursor.readShort()
                    + " yWobble=" + cursor.readShort()
                    + " fh=" + cursor.readShort()
                    + " originFh=" + cursor.readShort()
                    + " stance=" + stance(cursor.readUnsignedByte())
                    + " duration=" + cursor.readUnsignedShort();
            case 21 -> prefix + "aranUnknown payload=" + hex(cursor.readBytes(3));
            default -> throw new IllegalStateException("Command length was not classified: " + command);
        };
    }

    private static int payloadLength(int command) {
        return switch (command) {
            case 0, 5, 17 -> 13;
            case 1, 2, 6, 12, 13, 16, 18, 19, 20, 22 -> 7;
            case 3, 4, 7, 8, 9, 11, 14 -> 9;
            case 10 -> 1;
            case 15 -> 15;
            case 21 -> 3;
            default -> -1;
        };
    }

    private static String stance(int value) {
        return switch (value) {
            case 14 -> "14(ladder-right)";
            case 15 -> "15(ladder-left)";
            case 16 -> "16(rope-right)";
            case 17 -> "17(rope-left)";
            default -> Integer.toString(value);
        };
    }

    private static final class Cursor {
        private final byte[] data;
        private int position;

        private Cursor(byte[] data) {
            this.data = data;
        }

        private boolean has(int length) {
            return length >= 0 && position <= data.length - length;
        }

        private int remaining() {
            return data.length - position;
        }

        private int readUnsignedByte() {
            return Byte.toUnsignedInt(data[position++]);
        }

        private short readShort() {
            int low = readUnsignedByte();
            int high = readUnsignedByte();
            return (short) (low | high << 8);
        }

        private int readUnsignedShort() {
            return Short.toUnsignedInt(readShort());
        }

        private byte[] readBytes(int length) {
            byte[] result = new byte[length];
            System.arraycopy(data, position, result, 0, length);
            position += length;
            return result;
        }
    }
}
