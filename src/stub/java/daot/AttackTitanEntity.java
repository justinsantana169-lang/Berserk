package daot;

/**
 * COMPILE-TIME STAND-IN ONLY. Lets the Mixin annotation processor see the target class.
 * This is NOT included in the built jar; at runtime the real class from Danny's AOT is used.
 */
public class AttackTitanEntity {
    public boolean isDefeated() { return false; }
    public boolean isRoaring() { return false; }
    public boolean isKnocked() { return false; }
    public boolean isTransforming() { return false; }
    public boolean isDismounting() { return false; }
    public void triggerRoar() {}
    public void triggerAbility(int ability) {}
    protected float getAttackDamageMultiplier() { return 1.0f; }
    protected double getAttackReachScale() { return 1.0; }
    protected void drainAttackStamina() {}
}
