package com.regrantdev.blizzardwizard;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

// Packet that sends BlizzAura positions from server to client
public record BlizzAuraPositionsPacket(Set<BlockPos> positions) implements CustomPacketPayload {

    // Unique TYPE identifier for this packet
    public static final CustomPacketPayload.Type<BlizzAuraPositionsPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(BlizzardTenki.MODID, "blizzaura_positions")
        );

    // StreamCodec for encoding/decoding the packet
    public static final StreamCodec<FriendlyByteBuf, BlizzAuraPositionsPacket> STREAM_CODEC =
        StreamCodec.of(
            BlizzAuraPositionsPacket::encode,  // How to write
            BlizzAuraPositionsPacket::decode   // How to read
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Encode: Write positions to buffer (server → client)
    public static void encode(FriendlyByteBuf buf, BlizzAuraPositionsPacket packet) {
        buf.writeInt(packet.positions.size()); // Write count
        for (BlockPos pos : packet.positions) {
            buf.writeBlockPos(pos); // Write each position
        }
    }

    // Decode: Read positions from buffer (client receives)
    public static BlizzAuraPositionsPacket decode(FriendlyByteBuf buf) {
        int count = buf.readInt(); // Read count
        Set<BlockPos> positions = new HashSet<>();
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos()); // Read each position
        }
        return new BlizzAuraPositionsPacket(positions);
    }

    // Handler: What to do when client receives this packet
    public static void handle(BlizzAuraPositionsPacket packet, IPayloadContext context) {
        // This runs on the client side
        context.enqueueWork(() -> {
            BlizzardTenkiClient.updateBlizzAuraPositions(packet.positions);
        });
    }
}
