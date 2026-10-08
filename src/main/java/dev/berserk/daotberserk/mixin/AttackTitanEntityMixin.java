package dev.berserk.daotberserk.mixin;

import dev.berserk.daotberserk.BerserkConfig;
import dev.berserk.daotberserk.BerserkManager;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks Danny's AOT's AttackTitanEntity by name (string target), so this addon does not need
 * Danny's AOT on its compile classpath.
 *
 * What we found in the mod (2.4.x / 2.5.x):
 *  - The Attack Titan ability bar already shows a Berserk icon in slot 9 (key "9").
 *  - ModNetworking.handleTitanAbility already routes ability 9 here (and checks the Armor Potion tag),
 *    but AttackTitanEntity.triggerAbility(9) does nothing -> Berserk is a stub.
 *  - getAttackDamageMultiplier() / getAttackReachScale() / drainAttackStamina() are hook points.
 *
 * All method names below are NOT obfuscated in Danny's jar. If a future update renames them,
 * the game will crash on startup with a clear "injection failed" message (defaultRequire = 1).
 */
@Mixin(targets = "daot.AttackTitanEntity", remap = false)
public abstract class AttackTitanEntityMixin {

    @Shadow public abstract boolean isDefeated();
    @Shadow public abstract boolean isRoaring();
    @Shadow public abstract boolean isKnocked();
    @Shadow public abstract boolean isTransforming();
    @Shadow public abstract boolean isDismounting();
    @Shadow public abstract void triggerRoar();

    /** Ability 9 = Berserk slot. Everything else is left to the original method. */
    @Inject(method = "triggerAbility(I)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void daotBerserk$onAbility(int ability, CallbackInfo ci) {
        if (ability != BerserkManager.BERSERK_ABILITY) return;
        ci.cancel(); // never fall through to the (empty) vanilla handling

        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient) return;
        if (isDefeated() || isRoaring() || isKnocked() || isTransforming() || isDismounting()) return;

        if (BerserkManager.tryStart(self)) {
            triggerRoar(); // the mod's own roar animation + sound (no-op if conditions aren't met)
        }
    }

    @Inject(method = "getAttackDamageMultiplier()F", at = @At("RETURN"), cancellable = true, remap = false)
    private void daotBerserk$damage(CallbackInfoReturnable<Float> cir) {
        if (BerserkManager.isBerserk(this)) {
            cir.setReturnValue(cir.getReturnValue() * BerserkConfig.DAMAGE_MULTIPLIER);
        }
    }

    @Inject(method = "getAttackReachScale()D", at = @At("RETURN"), cancellable = true, remap = false)
    private void daotBerserk$reach(CallbackInfoReturnable<Double> cir) {
        if (BerserkManager.isBerserk(this)) {
            cir.setReturnValue(cir.getReturnValue() * BerserkConfig.REACH_MULTIPLIER);
        }
    }

    @Inject(method = "drainAttackStamina()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void daotBerserk$noDrain(CallbackInfo ci) {
        if (BerserkConfig.FREE_STAMINA && BerserkManager.isBerserk(this)) ci.cancel();
    }
}
