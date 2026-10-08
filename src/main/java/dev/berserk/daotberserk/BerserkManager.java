package dev.berserk.daotberserk;

import dev.berserk.daotberserk.net.BerserkStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-side state + effects for the Attack Titan's Berserk mode. */
public final class BerserkManager {
    private BerserkManager() {}

    /** Danny's AOT sends ability numbers 1..9 (key 1..9). The Berserk icon sits on 9. */
    public static final int BERSERK_ABILITY = 9;

    private static final Logger LOG = LoggerFactory.getLogger("daot_berserk");
    private static final String ATTACK_TITAN_CLASS = "daot.AttackTitanEntity";

    private static final class State {
        LivingEntity titan;
        int active;
        int cooldown;
    }

    /** Keyed by the riding player's UUID (so cooldown can't be dodged by re-shifting). */
    private static final Map<UUID, State> STATES = new HashMap<>();

    private static final List<RegistryEntry<StatusEffect>> TITAN_BUFFS = List.of(
            StatusEffects.STRENGTH, StatusEffects.SPEED, StatusEffects.RESISTANCE, StatusEffects.FIRE_RESISTANCE);

    private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.12f, 0.0f), 3.0f);
    private static final DustParticleEffect EYE_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.9f, 0.2f), 1.6f);

    // ------------------------------------------------------------------ queries (used by the mixin)

    public static boolean isBerserk(Object titan) {
        if (STATES.isEmpty()) return false;
        for (State s : STATES.values()) {
            if (s.active > 0 && s.titan == titan) return true;
        }
        return false;
    }

    public static boolean isAttackTitan(Entity e) {
        if (e == null) return false;
        for (Class<?> c = e.getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().equals(ATTACK_TITAN_CLASS)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ start / stop

    /** Called from the mixin when the player presses the Berserk ability key. */
    public static boolean tryStart(LivingEntity titan) {
        if (!(titan.getControllingPassenger() instanceof ServerPlayerEntity p)) return false;
        if (!(titan.getWorld() instanceof ServerWorld world)) return false;

        State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        if (s.active > 0) {
            p.sendMessage(Text.literal("Already berserk!").formatted(Formatting.RED), true);
            return false;
        }
        if (s.cooldown > 0) {
            p.sendMessage(Text.literal("Berserk recharging: " + (s.cooldown / 20 + 1) + "s")
                    .formatted(Formatting.GRAY), true);
            return false;
        }

        s.titan = titan;
        s.active = BerserkConfig.DURATION_TICKS;

        applyBuffs(titan);
        fillStamina(p.getUuid());
        sound(world, titan, SoundEvents.ENTITY_BLAZE_SHOOT, 8.0f, 0.5f);
        sound(world, titan, SoundEvents.ITEM_FIRECHARGE_USE, 8.0f, 0.4f);
        shockwave(world, titan);
        p.sendMessage(Text.literal("BERSERK!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        sync(p, s);
        return true;
    }

    public static String commandStart(ServerPlayerEntity p) {
        Entity v = p.getVehicle();
        if (!isAttackTitan(v) || !(v instanceof LivingEntity titan)) return "You must be riding the Attack Titan.";
        return tryStart(titan) ? "Berserk started." : "Couldn't start (cooldown / already active).";
    }

    public static void commandStop(ServerPlayerEntity p) {
        State s = STATES.get(p.getUuid());
        if (s != null && s.active > 0) end(p, s, false);
    }

    public static void commandReset(ServerPlayerEntity p) {
        State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
        s.cooldown = 0;
        sync(p, s);
    }

    // ------------------------------------------------------------------ per-tick

    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;
        Iterator<Map.Entry<UUID, State>> it = STATES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> e = it.next();
            State s = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());

            if (s.active > 0) {
                tickActive(p, s);
            } else if (s.cooldown > 0) {
                s.cooldown--;
                if (p != null && (s.cooldown == 0 || server.getTicks() % 20 == 0)) sync(p, s);
            }
            if (s.active <= 0 && s.cooldown <= 0) it.remove();
        }
    }

    private static void tickActive(ServerPlayerEntity p, State s) {
        LivingEntity titan = s.titan;
        boolean valid = p != null && titan != null && !titan.isRemoved() && titan.isAlive()
                && titan.getControllingPassenger() == p;
        if (!valid || !(titan.getWorld() instanceof ServerWorld world)) {
            end(p, s, false); // left the titan / died / disconnected
            return;
        }

        s.active--;
        int age = titan.age;

        if (s.active % 20 == 0) {
            applyBuffs(titan);
            if (BerserkConfig.HEAL_PERCENT_PER_SECOND > 0)
                titan.heal(titan.getMaxHealth() * BerserkConfig.HEAL_PERCENT_PER_SECOND);
            if (BerserkConfig.FREE_STAMINA) fillStamina(p.getUuid());
        }
        if (s.active % 50 == 0) sound(world, titan, SoundEvents.BLOCK_FIRE_AMBIENT, 6.0f, 0.6f);

        visuals(world, titan, age);

        if (age % 10 == 0) {
            if (BerserkConfig.AURA_ENABLED) aura(world, titan, p);
            sync(p, s);
        }

        if (s.active <= 0) end(p, s, true);
    }

    private static void end(ServerPlayerEntity p, State s, boolean natural) {
        LivingEntity titan = s.titan;
        s.active = 0;
        s.cooldown = BerserkConfig.COOLDOWN_TICKS;
        s.titan = null;

        if (titan != null && !titan.isRemoved() && titan.isAlive()) {
            for (RegistryEntry<StatusEffect> e : TITAN_BUFFS) titan.removeStatusEffect(e);
            if (titan.getWorld() instanceof ServerWorld world) {
                sound(world, titan, SoundEvents.BLOCK_FIRE_EXTINGUISH, 8.0f, 0.5f);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, titan.getX(), titan.getY() + titan.getHeight() * 0.6,
                        titan.getZ(), 30, titan.getWidth() * 0.5, titan.getHeight() * 0.4, titan.getWidth() * 0.5, 0.05);
            }
            if (natural) {
                titan.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, BerserkConfig.PENALTY_TICKS, 0, false, false, true));
                titan.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, BerserkConfig.PENALTY_TICKS, 0, false, false, true));
            }
        }
        if (p != null) {
            if (natural) p.sendMessage(Text.literal("The rage fades... you're exhausted.").formatted(Formatting.GRAY), true);
            sync(p, s);
        }
    }

    // ------------------------------------------------------------------ effects

    private static void applyBuffs(LivingEntity titan) {
        int d = 60; // refreshed every 20 ticks, so they vanish within 3s if interrupted
        titan.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, d, BerserkConfig.STRENGTH_LEVEL, false, false, false));
        titan.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d, BerserkConfig.SPEED_LEVEL, false, false, false));
        titan.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d, BerserkConfig.RESISTANCE_LEVEL, false, false, false));
        titan.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, d, 0, false, false, false));
    }

    private static void sound(ServerWorld w, Entity at, SoundEvent s, float vol, float pitch) {
        w.playSound(null, at.getX(), at.getY(), at.getZ(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    /** Sends particles to every player within view distance (vanilla's default 32-block cap is too small for a titan). */
    private static void particles(ServerWorld w, ParticleEffect type, double x, double y, double z,
                                  int count, double dx, double dy, double dz, double speed) {
        double max = BerserkConfig.MAX_PARTICLE_VIEW_DISTANCE;
        double max2 = max * max;
        for (ServerPlayerEntity viewer : w.getPlayers()) {
            if (viewer.squaredDistanceTo(x, y, z) <= max2) {
                w.spawnParticles(viewer, type, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    private static void visuals(ServerWorld w, LivingEntity t, int age) {
        double x = t.getX(), y = t.getY(), z = t.getZ();
        double width = Math.max(1.0, t.getWidth());
        double h = Math.max(2.0, t.getHeight());
        int dens = (int) Math.min(40, 4 + h);

        // flaming body, red glow, smoke
        particles(w, ParticleTypes.FLAME, x, y + h * 0.5, z, dens, width * 0.5, h * 0.5, width * 0.5, 0.04);
        particles(w, RED_DUST, x, y + h * 0.5, z, dens, width * 0.55, h * 0.5, width * 0.55, 0.0);
        particles(w, ParticleTypes.LARGE_SMOKE, x, y + h * 0.7, z, Math.max(2, dens / 2), width * 0.5, h * 0.4, width * 0.5, 0.03);

        // rising spiral of fire around the body
        double ang = age * 0.35, r = width * 0.7;
        for (int i = 0; i < 4; i++) {
            double a = ang + i * (Math.PI / 2);
            double yy = y + ((age * 0.05 + i * 0.25) % 1.0) * h;
            particles(w, ParticleTypes.FLAME, x + Math.cos(a) * r, yy, z + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.02);
        }

        // steam venting from the shoulders
        if (age % 2 == 0) {
            particles(w, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + h * 0.85, z, 2, width * 0.45, 0.2, width * 0.45, 0.02);
        }

        // glowing eyes (head is roughly 90% up, facing the yaw direction)
        double yaw = Math.toRadians(t.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double ex = x + fx * width * 0.35, ez = z + fz * width * 0.35;
        double sx = -fz * width * 0.1, sz = fx * width * 0.1;
        particles(w, EYE_DUST, ex + sx, y + h * 0.9, ez + sz, 1, 0, 0, 0, 0);
        particles(w, EYE_DUST, ex - sx, y + h * 0.9, ez - sz, 1, 0, 0, 0, 0);

        if (age % 3 == 0) particles(w, ParticleTypes.LAVA, x, y + 0.2, z, 2, width * 0.5, 0.2, width * 0.5, 0);
        if (age % 6 == 0) particles(w, ParticleTypes.ELECTRIC_SPARK, x, y + h * 0.5, z, 6, width * 0.6, h * 0.5, width * 0.6, 0.4);
    }

    private static void shockwave(ServerWorld w, LivingEntity t) {
        double x = t.getX(), y = t.getY() + 0.2, z = t.getZ();
        double width = Math.max(1.0, t.getWidth());
        double speed = 0.6 + width * 0.08;
        int n = 64;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double dx = Math.cos(a), dz = Math.sin(a);
            double px = x + dx * width * 0.5, pz = z + dz * width * 0.5;
            particles(w, ParticleTypes.FLAME, px, y, pz, 0, dx, 0.04, dz, speed);
            particles(w, ParticleTypes.LARGE_SMOKE, px, y, pz, 0, dx, 0.02, dz, speed * 0.6);
        }
        particles(w, ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 1, 0, 0, 0, 0);
    }

    private static void aura(ServerWorld w, LivingEntity titan, ServerPlayerEntity rider) {
        double radius = Math.max(BerserkConfig.AURA_MIN_RADIUS, titan.getWidth() * 1.5);
        Box box = titan.getBoundingBox().expand(radius);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, le -> le != titan && le != rider && le.isAlive())) {
            // Never touch Danny's AOT entities (titans, nape/eye hitboxes...): a nape hit could one-shot a titan.
            if (e.getClass().getName().startsWith("daot.")) continue;
            if (e instanceof PassiveEntity || e instanceof TameableEntity) continue;
            if (e instanceof PlayerEntity && !BerserkConfig.AURA_HITS_PLAYERS) continue;
            e.setOnFireFor(BerserkConfig.AURA_BURN_SECONDS);
            e.damage(titan.getDamageSources().mobAttack(titan), BerserkConfig.AURA_DAMAGE);
        }
    }

    // ------------------------------------------------------------------ Danny's AOT stamina (via reflection, fails soft)

    private static Method fillStaminaMethod;
    private static boolean fillStaminaLookedUp;

    private static void fillStamina(UUID player) {
        if (!BerserkConfig.FREE_STAMINA) return;
        if (!fillStaminaLookedUp) {
            fillStaminaLookedUp = true;
            try {
                fillStaminaMethod = Class.forName("daot.network.ModNetworking").getMethod("fillStamina", UUID.class);
            } catch (Throwable t) {
                LOG.warn("Couldn't find ModNetworking.fillStamina; stamina refill disabled ({})", t.toString());
            }
        }
        if (fillStaminaMethod == null) return;
        try {
            fillStaminaMethod.invoke(null, player);
        } catch (Throwable t) {
            LOG.warn("fillStamina failed; disabling ({})", t.toString());
            fillStaminaMethod = null;
        }
    }

    // ------------------------------------------------------------------ sync

    private static void sync(ServerPlayerEntity p, State s) {
        boolean active = s.active > 0;
        ServerPlayNetworking.send(p, new BerserkStatePayload(active, s.active, active ? 0 : s.cooldown));
    }
}
