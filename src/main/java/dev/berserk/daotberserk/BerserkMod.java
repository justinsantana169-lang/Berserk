package dev.berserk.daotberserk;

import dev.berserk.daotberserk.net.BerserkStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BerserkMod implements ModInitializer {
    public static final String MOD_ID = "daot_berserk";

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(BerserkStatePayload.ID, BerserkStatePayload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(BerserkManager::tick);

        // Op-only test commands. Normal play uses the mod's own ability key (default: 9).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(CommandManager.literal("berserk")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("start").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String msg = BerserkManager.commandStart(p);
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))
                .then(CommandManager.literal("stop").executes(ctx -> {
                    BerserkManager.commandStop(ctx.getSource().getPlayerOrThrow());
                    return 1;
                }))
                .then(CommandManager.literal("reset").executes(ctx -> {
                    BerserkManager.commandReset(ctx.getSource().getPlayerOrThrow());
                    ctx.getSource().sendFeedback(() -> Text.literal("Berserk cooldown reset."), false);
                    return 1;
                }))
                .then(CommandManager.literal("info").executes(ctx -> {
                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                    String msg = "vehicle=" + (p.getVehicle() == null ? "none" : p.getVehicle().getClass().getName())
                            + " attackTitan=" + BerserkManager.isAttackTitan(p.getVehicle());
                    ctx.getSource().sendFeedback(() -> Text.literal(msg), false);
                    return 1;
                }))
            ));
    }
}
