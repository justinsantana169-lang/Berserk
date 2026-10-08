package dev.berserk.daotberserk.net;

import dev.berserk.daotberserk.BerserkMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> rider: current berserk state for the HUD / screen effect. */
public record BerserkStatePayload(boolean active, int remaining, int cooldown) implements CustomPayload {
    public static final Id<BerserkStatePayload> ID =
            new Id<>(Identifier.of(BerserkMod.MOD_ID, "state"));
    public static final PacketCodec<RegistryByteBuf, BerserkStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.BOOL, BerserkStatePayload::active,
            PacketCodecs.VAR_INT, BerserkStatePayload::remaining,
            PacketCodecs.VAR_INT, BerserkStatePayload::cooldown,
            BerserkStatePayload::new);

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
