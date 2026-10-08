package dev.berserk.daotberserk;

/** Tweak everything here. */
public final class BerserkConfig {
    private BerserkConfig() {}

    // ---- timing ----
    public static final int DURATION_TICKS = 20 * 20;  // 20s of berserk
    public static final int COOLDOWN_TICKS = 20 * 60;  // 60s recharge (per player)
    public static final int PENALTY_TICKS  = 20 * 10;  // exhaustion after it ends

    // ---- buffs applied to the TITAN ----
    public static final float DAMAGE_MULTIPLIER = 2.0f; // titan melee/kick damage x2
    public static final double REACH_MULTIPLIER = 1.25; // attack reach +25%
    public static final boolean FREE_STAMINA    = true; // attacks cost no stamina, stamina kept full
    public static final int STRENGTH_LEVEL   = 2;       // status effects on the titan (0 = I)
    public static final int SPEED_LEVEL      = 1;
    public static final int RESISTANCE_LEVEL = 2;
    public static final float HEAL_PERCENT_PER_SECOND = 0.01f; // 1% max HP / second

    // ---- fire aura (vanilla mobs only; never touches Danny's AOT entities) ----
    public static final boolean AURA_ENABLED = true;
    public static final double  AURA_MIN_RADIUS = 6.0;
    public static final float   AURA_DAMAGE = 3.0f;     // every 10 ticks
    public static final int     AURA_BURN_SECONDS = 5;
    public static final boolean AURA_HITS_PLAYERS = false;

    // ---- visuals ----
    public static final int MAX_PARTICLE_VIEW_DISTANCE = 128; // blocks
}
