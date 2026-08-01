package net.encryption;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.ReplayingDecoder;
import config.YamlConfig;
import net.netty.InvalidPacketHeaderException;
import net.packet.ByteBufInPacket;

import java.util.List;

public class PacketDecoder extends ReplayingDecoder<Void> {
    private final MapleAESOFB receiveCypher;

    public PacketDecoder(MapleAESOFB receiveCypher) {
        this.receiveCypher = receiveCypher;
    }

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) {
        final int header = in.readInt();

        if (!receiveCypher.isValidHeader(header)) {
            throw new InvalidPacketHeaderException("Attempted to decode a packet with an invalid header", header);
        }

        final int packetLength = decodePacketLength(header);
        int configuredMax = YamlConfig.config.server.PACKET_MAX_FRAME_BYTES;
        int maxFrameBytes = configuredMax > 0 ? configuredMax : 32 * 1024;
        if (packetLength < Short.BYTES || packetLength > maxFrameBytes) {
            throw new CorruptedFrameException(
                    "Inbound packet length " + packetLength + " is outside 2.." + maxFrameBytes);
        }
        byte[] packet = new byte[packetLength];
        in.readBytes(packet);
        receiveCypher.crypt(packet);
        MapleCustomEncryption.decryptData(packet);
        out.add(new ByteBufInPacket(Unpooled.wrappedBuffer(packet)));
    }

    /**
     * @param header Packet header - the first 4 bytes of the packet
     * @return Packet size in bytes
     */
    private static int decodePacketLength(byte[] header) {
        return (((header[1] ^ header[3]) & 0xFF) << 8) | ((header[0] ^ header[2]) & 0xFF);
    }

    private static int decodePacketLength(int header) {
        int length = ((header >>> 16) ^ (header & 0xFFFF));
        length = ((length << 8) & 0xFF00) | ((length >>> 8) & 0xFF);
        return length;
    }
}
