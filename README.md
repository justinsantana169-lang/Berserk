# DAOT Berserk Mode v2 (Fabric 1.21.1)

Makes the **Berserk** ability on Danny's AOT's Attack Titan ability bar (slot 9, default key `9`) actually do something.

## How it works (what was found in Danny's AOT)
- The ability bar already shows a Berserk icon in slot 9 for the Attack Titan.
- The mod's networking already routes ability 9 to `AttackTitanEntity.triggerAbility(9)` (and requires the Armor Potion tag `has_hardening`), but that method ignores 9 - it's a stub.
- This addon uses a Mixin on `daot.AttackTitanEntity` (no compile dependency on Danny's mod) to:
  start Berserk on ability 9, multiply `getAttackDamageMultiplier()` and `getAttackReachScale()`, and skip `drainAttackStamina()`.

## Berserk gives
Titan: damage x2, reach +25%, Strength III, Speed II, Resistance III, fire resistance, 1%/s heal, free stamina,
roar + fire shockwave, flames/smoke/steam/glowing eyes, fire aura that scorches vanilla hostile mobs.
Rider: red pulsing screen edges + timer bar. After 20s: 10s of Slowness + Weakness on the titan, 60s cooldown.
All numbers: `BerserkConfig.java`.

## Build
Generate the official template for 1.21.1 at https://fabricmc.net/develop/template, copy this project's `src/`
and `gradle.properties` over it, then `./gradlew build` (Java 21). Jar is in `build/libs/`.
Install next to Danny's AOT (+ GeckoLib, Player Animation Library, Fabric API, AAA Particles).

## Testing
- Be the Attack Titan, then press `9`. If you see "requires consumption of the Armor Potion": `/tag @s add has_hardening`.
- Op commands: `/berserk start | stop | reset | info`.

## Easiest way to get the .jar (no installs)
1. Make a free GitHub account, create a new repository, and upload everything in this folder
   (including the hidden `.github` folder) to it.
2. Open the repo's **Actions** tab, wait for "Build jar" to finish (~3-5 min).
3. Click the run, download the **daot-berserk-addon** artifact, unzip it.
4. Use `daot-berserk-addon-2.0.0.jar` (NOT the `-sources` one) in your `mods` folder.
If the build fails, copy the red error text and send it to Claude.
