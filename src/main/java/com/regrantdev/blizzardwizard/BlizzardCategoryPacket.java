package com.regrantdev.blizzardwizard;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

// Packet that sends the blizzard intensity category from server to client
// Category ranges from 1 (Light Flurry) to 5 (Severe Whiteout)
public record BlizzardCategoryPacket(int category) implements CustomPacketPayload {

    // Unique TYPE identifier for this packet
    public static final CustomPacketPayload.Type<BlizzardCategoryPacket> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(BlizzardTenki.MODID, "blizzard_category")
        );

    // StreamCodec for encoding/decoding the packet
    public static final StreamCodec<FriendlyByteBuf, BlizzardCategoryPacket> STREAM_CODEC =
        StreamCodec.of(
            BlizzardCategoryPacket::encode,  // How to write
            BlizzardCategoryPacket::decode   // How to read
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Encode: Write category to buffer (server → client)
    public static void encode(FriendlyByteBuf buf, BlizzardCategoryPacket packet) {
        buf.writeInt(packet.category); // Write the category (1-5)
    }

    // Decode: Read category from buffer (client receives)
    public static BlizzardCategoryPacket decode(FriendlyByteBuf buf) {
        int category = buf.readInt(); // Read the category
        return new BlizzardCategoryPacket(category);
    }

    // Handler: What to do when client receives this packet
    public static void handle(BlizzardCategoryPacket packet, IPayloadContext context) {
        // This runs on the client side
        context.enqueueWork(() -> {
            BlizzardTenkiClient.updateBlizzardCategory(packet.category);
        });
    }
}
