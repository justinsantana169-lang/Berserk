package dev.berserk.daotberserk.client;

import dev.berserk.daotberserk.BerserkConfig;
import dev.berserk.daotberserk.net.BerserkStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.lang.reflect.Method;

/** Client side: red screen edges + a flaming Berserk bar. Activation uses Danny's AOT's own ability key. */
public class BerserkClient implements ClientModInitializer {
    private static final int READY_TICKS = 70;

    // ---- bar layout (tweak these) ----
    private static final int BAR_W = 72;               // same width as Danny's stamina bar
    private static final int BAR_H = 9;
    private static final int GAP_ABOVE_STAMINA = 7;    // pixels between this bar and the stamina bar

    private static boolean active;
    private static int remaining;
    private static int cooldown;
    private static int readyFlash; // ticks left of the "BERSERK READY" flash
    private static int introFlash; // ticks left of the ignition flash

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(BerserkStatePayload.ID, (payload, context) -> {
            if (!active && payload.active()) introFlash = 14;
            if (!payload.active() && cooldown > 0 && payload.cooldown() <= 0) readyFlash = READY_TICKS;
            active = payload.active();
            remaining = payload.remaining();
            cooldown = payload.cooldown();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            int before = cooldown;
            if (active) remaining = Math.max(0, remaining - 1);
            else if (cooldown > 0) cooldown--;
            if (!active && before > 0 && cooldown <= 0) readyFlash = READY_TICKS;
            if (readyFlash > 0) readyFlash--;
            if (introFlash > 0) introFlash--;
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            active = false; remaining = 0; cooldown = 0; readyFlash = 0; introFlash = 0;
        });

        HudRenderCallback.EVENT.register(BerserkClient::renderHud);
    }

    // ------------------------------------------------------------------ helpers

    private static int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private static int argb(int a, int r, int g, int b) {
        return (clamp(a) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int lerp(int a, int b, float t) { return (int) (a + (b - a) * t); }

    private static float hash(int i) {
        float s = MathHelper.sin(i * 12.9898f) * 43758.5453f;
        return s - (float) Math.floor(s);
    }

    // ------------------------------------------------------------------ rendering

    private static void renderHud(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        float pd = tick.getTickDelta(false);
        float t = mc.player.age + pd;

        if (active) drawVignette(ctx, w, h, t);
        if (active || cooldown > 0 || readyFlash > 0) drawBar(ctx, mc, w, h, t, pd);
    }

    private static void drawVignette(DrawContext ctx, int w, int h, float t) {
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

    // Danny's AOT computes where its stamina bar sits (it moves with armor/air rows), so ask it.
    private static Method rightBarYMethod;
    private static boolean rightBarYResolved;

    private static int staminaBarY(MinecraftClient mc, DrawContext ctx, int fallback) {
        if (!rightBarYResolved) {
            rightBarYResolved = true;
            try {
                rightBarYMethod = Class.forName("daot.FancyBar")
                        .getDeclaredMethod("rightBarY", MinecraftClient.class, DrawContext.class);
                rightBarYMethod.setAccessible(true);
            } catch (Throwable t) {
                rightBarYMethod = null;
            }
        }
        if (rightBarYMethod != null) {
            try {
                return (int) rightBarYMethod.invoke(null, mc, ctx);
            } catch (Throwable t) {
                rightBarYMethod = null;
            }
        }
        return fallback;
    }

    private static void drawBar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float t, float pd) {
        final int bw = BAR_W, bh = BAR_H;
        // mode 0 = berserk active, 1 = recharging, 2 = ready flash
        int mode = active ? 0 : (cooldown > 0 ? 1 : 2);
        float frac;
        if (mode == 0) frac = (remaining - pd) / (float) BerserkConfig.DURATION_TICKS;
        else if (mode == 1) frac = 1f - (cooldown - pd) / (float) BerserkConfig.COOLDOWN_TICKS;
        else frac = 1f;
        frac = MathHelper.clamp(frac, 0f, 1f);

        boolean burning = mode != 1;
        boolean low = mode == 0 && remaining < 100; // last 5 seconds
        float pulse = 0.5f + 0.5f * MathHelper.sin(t * 0.3f);

        // sit directly above Danny's stamina bar, flush with the right edge of the hotbar
        int staminaY = staminaBarY(mc, ctx, sh - 45);
        int x = sw / 2 + 91 - bw;
        int y = staminaY - bh - GAP_ABOVE_STAMINA;
        if (mode == 0) { // slight rage shake, more when time is almost up
            float j = low ? 1.0f : 0.5f;
            x += Math.round(MathHelper.sin(t * 2.3f) * j);
            y += Math.round(MathHelper.cos(t * 3.1f) * j * 0.6f);
        }

        // outer fire glow
        if (burning) {
            for (int i = 3; i >= 1; i--) {
                ctx.fill(x - i * 2, y - i * 2, x + bw + i * 2, y + bh + i * 2, argb((int) (8 + 12 * pulse), 255, 70, 0));
            }
        }

        // frame + background
        int border = burning ? argb(255, lerp(120, 255, pulse), lerp(20, 110, pulse), 0) : argb(255, 70, 60, 60);
        ctx.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, border);
        ctx.fillGradient(x, y, x + bw, y + bh, 0xFF1E0B08, 0xFF060202);

        // fill: deep red at the left -> white-hot yellow at the leading edge, flickering
        int fw = (int) (bw * frac);
        for (int cx = 0; cx < fw; cx += 2) {
            int ex = Math.min(cx + 2, fw);
            float hf = cx / (float) Math.max(1, fw);
            float fl = 0.88f + 0.12f * MathHelper.sin(t * 0.9f + cx * 0.25f);
            int r, g, b;
            if (burning) {
                float k = hf * hf;
                r = (int) (lerp(185, 255, k) * fl);
                g = (int) (lerp(18, 215, k) * fl);
                b = (int) (lerp(0, 70, k) * fl);
            } else {
                r = (int) (lerp(90, 190, hf) * 0.8f);
                g = (int) (lerp(35, 85, hf) * 0.8f);
                b = (int) (lerp(30, 35, hf) * 0.8f);
            }
            int top = argb(255, r * 115 / 100, g * 115 / 100, b * 115 / 100);
            int bot = argb(255, r * 45 / 100, g * 35 / 100, b * 35 / 100);
            ctx.fillGradient(x + cx, y, x + ex, y + bh, top, bot);
        }
        if (fw > 0) ctx.fill(x, y, x + fw, y + 1, argb(80, 255, 255, 255)); // glossy top edge
        for (int k = 1; k < 10; k++) ctx.fill(x + bw * k / 10, y, x + bw * k / 10 + 1, y + bh, 0x55000000);

        // animated flame tongues licking up off the bar
        if (burning) {
            for (int cx = 0; cx < fw; cx += 3) {
                float ph = t * 0.55f + cx * 0.37f;
                float n = (0.5f + 0.5f * MathHelper.sin(ph)) * (0.65f + 0.35f * MathHelper.sin(ph * 1.7f + cx));
                int th = (int) (2 + 6 * n * (mode == 2 ? 0.8f : 1f));
                int x2 = x + Math.min(cx + 2, fw);
                ctx.fillGradient(x + cx, y - th, x2, y, argb(0, 255, 190, 40), argb(210, 255, 80, 0));
                int ih = (int) (th * 0.55f);
                ctx.fillGradient(x + cx, y - ih, x2, y, argb(0, 255, 240, 120), argb(230, 255, 215, 70));
            }
        }

        // glowing leading edge
        if (fw > 0) {
            int lx = x + fw;
            ctx.fill(lx - 3, y - 2, lx + 3, y + bh + 2, argb(burning ? 38 : 16, 255, 200, 80));
            ctx.fill(lx - 1, y, lx + 1, y + bh, burning ? argb(255, 255, 245, 200) : argb(200, 230, 170, 120));
            if (burning) ctx.fillGradient(lx - 1, y - 9, lx + 1, y, argb(0, 255, 220, 100), argb(230, 255, 240, 170));
        }

        // rising embers
        if (burning && fw > 6) {
            for (int i = 0; i < 12; i++) {
                float ph = (t * (0.010f + (i % 5) * 0.004f) + i * 0.137f) % 1f;
                int px = x + (int) (fw * hash(i)) + (int) (MathHelper.sin(t * 0.2f + i) * 2);
                int ey = y - 2 - (int) (ph * 20);
                int sz = (i % 3 == 0) ? 2 : 1;
                ctx.fill(px, ey, px + sz, ey + sz, argb((int) (230 * (1f - ph)), 255, (int) (140 + 90 * (1f - ph)), 40));
            }
        }

        // warning blink in the last 5 seconds, ignition flash at the start
        if (low) {
            int a = (int) (80 * Math.max(0f, MathHelper.sin(t * 0.9f)));
            ctx.fill(x, y, x + bw, y + bh, argb(a, 255, 255, 255));
        }
        if (introFlash > 0) ctx.fill(x - 2, y - 2, x + bw + 2, y + bh + 2, argb(introFlash * 14, 255, 240, 200));

        // shine sweeping across the bar when the ability is ready again
        if (mode == 2) {
            float p = 1f - readyFlash / (float) READY_TICKS;
            int sx = x + (int) (p * (bw + 20)) - 8;
            int x1 = Math.max(sx, x), x2 = Math.min(sx + 6, x + bw);
            if (x2 > x1) ctx.fill(x1, y, x2, y + bh, argb(120, 255, 255, 255));
        }

        // text sits INSIDE the bar so the HUD stays compact
        TextRenderer tr = mc.textRenderer;
        int cxText = x + bw / 2;
        int ty = y + (bh - 8) / 2 + 1;
        if (mode == 0) {
            int secs = (int) Math.ceil(Math.max(0f, remaining - pd) / 20f);
            int col = low ? argb(255, 255, (int) (200 + 55 * pulse), (int) (170 + 85 * pulse)) : 0xFFFFFFFF;
            ctx.drawCenteredTextWithShadow(tr, Text.literal("BERSERK " + secs + "s"), cxText, ty, col);
        } else if (mode == 1) {
            ctx.drawCenteredTextWithShadow(tr, Text.literal("RECHARGE " + (cooldown / 20 + 1) + "s"), cxText, ty, 0xFFC9BDBD);
        } else {
            int a = readyFlash < 15 ? readyFlash * 255 / 15 : 255;
            if (a >= 8) {
                ctx.drawCenteredTextWithShadow(tr, Text.literal("READY").formatted(Formatting.BOLD),
                        cxText, ty, argb(a, 255, (int) (200 + 40 * pulse), 90));
            }
        }
    }
}
