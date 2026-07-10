package net.packet;

import java.awt.*;
import java.util.Arrays;

public interface InPacket extends Packet {
    byte readByte();
    short readUnsignedByte();
    short readShort();
    int readInt();
    long readLong();
    Point readPos();
    String readString();
    byte[] readBytes(int numberOfBytes);
    void skip(int numberOfBytes);
    int available();
    void seek(int byteOffset);
    int getPosition();

    default byte[] getBytes(int maximumLength) {
        byte[] bytes = getBytes();
        return bytes.length <= maximumLength ? bytes : Arrays.copyOf(bytes, maximumLength);
    }
}
