package dev.berserk.daotberserk.client;

import dev.berserk.daotberserk.BerserkConfig;
import dev.berserk.daotberserk.net.BerserkStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/** Client side: just the red screen edges + timer bar. Activation uses Danny's AOT's own ability key. */
public class BerserkClient implements ClientModInitializer {
    private static boolean active;
    private static int remaining;
    private static int cooldown;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BerserkStatePayload.ID, (payload, context) -> {
            active = payload.active();
            remaining = payload.remaining();
            cooldown = payload.cooldown();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (active) remaining = Math.max(0, remaining - 1);
            else if (cooldown > 0) cooldown--;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            active = false; remaining = 0; cooldown = 0;
        });

        HudRenderCallback.EVENT.register(BerserkClient::renderHud);
    }

    private static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        if (active) {
            float t = mc.player.age + tick.getTickDelta(false);
            float pulse = 0.5f + 0.5f * MathHelper.sin(t * 0.35f);
            int a = (int) (45 + 60 * pulse);
            int edge = (a << 24) | 0xFF2200;
            int clear = 0x00FF2200;

            int band = h / 4;
            ctx.fillGradient(0, 0, w, band, edge, clear);
            ctx.fillGradient(0, h - band, w, h, clear, edge);

            int strips = 18, sw = Math.max(2, w / 40);
            for (int i = 0; i < strips; i++) {
                int sa = (int) (a * (1.0f - i / (float) strips));
                int col = (sa << 24) | 0xFF2200;
                ctx.fill(i * sw, 0, (i + 1) * sw, h, col);
                ctx.fill(w - (i + 1) * sw, 0, w - i * sw, h, col);
            }
        }

        if (active || cooldown > 0) {
            int bw = 90, bh = 6;
            int x = (w - bw) / 2;
            int y = h - 100; // above the hotbar and Danny's ability bar; adjust if it overlaps
            float frac = active
                    ? remaining / (float) BerserkConfig.DURATION_TICKS
                    : 1f - cooldown / (float) BerserkConfig.COOLDOWN_TICKS;
            ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xAA000000);
            ctx.fill(x, y, x + (int) (bw * frac), y + bh, active ? 0xFFFF3A00 : 0xFF777777);
            Text label = active
                    ? Text.literal("BERSERK").formatted(Formatting.RED, Formatting.BOLD)
                    : Text.literal("Recharging").formatted(Formatting.GRAY);
            ctx.drawCenteredTextWithShadow(mc.textRenderer, label, w / 2, y - 11, 0xFFFFFF);
        }
    }
}
