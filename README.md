# CJS Firearm Damage Chance Fix

ZombieBuddy patch for a Project Zomboid B42.19 combat ordering bug.

When a firearm accuracy roll fails in Damage Chance mode, `CombatManager` correctly records `Bullets Damage Ignored` and sets its per-target ignore flag. It then calls the target's `Hit(...)` method with the older cumulative flag before merging the current flag. The failed roll therefore applies full damage to the first target of a shot.

This mod leaves the game JAR untouched. Failed rolls become configurable grazes while successful rolls keep the complete vanilla damage path. Its ZombieBuddy advice patches:

1. Open and close a thread-local scope around `CombatManager.attackCollisionCheck(...)`.
2. Mark the exact `Bullets Damage Ignored` statistic branch after it completes.
3. Reroute failed targeted head hits and random head reactions to the torso.
4. Clear critical-hit amplification and scale the immediately following character, moving-object, or vehicle hit to the configured percentage.

The sandbox option **Failed Roll Damage** ranges from 0% to 100% and defaults to **25%**. Failed rolls are always non-critical and non-head regardless of that percentage. Sounds, statistics, and successful-roll behavior are otherwise unchanged.

## Build

The build requires Java 17 or newer, a local ZombieBuddy JAR, and the B42.19 Project Zomboid JAR. Narrow compile-only B42.19 API stubs bridge the game's Java 25 bytecode to the Java 17 patch target; those stubs are never packaged. The default dependency paths match this workspace.

```bash
./build.sh
```

Override the dependency path when needed:

```bash
ZOMBIE_BUDDY_JAR=/path/to/ZombieBuddy.jar \
PROJECT_ZOMBOID_JAR=/path/to/projectzomboid.jar \
./build.sh
```

The tracked runtime JAR is written to `42/media/java/CJSFirearmDamageChanceFix.jar`. The build runs context and patch-metadata tests, then verifies the compiled patch against the real game classes under Project Zomboid's Java runtime. Override that runtime with `PROJECT_ZOMBOID_JAVA` if the JAR is stored outside its normal game directory.

## In-game verification

Enable `ZombieBuddy` followed by `cjsFirearmDamageChanceFix`, configure **Sandbox > CJS Firearm Damage Chance Fix > Failed Roll Damage**, choose `Firearms Use Damage Chance = Zombies only`, and fire poorly aimed shots at a distant zombie's head. A failed roll should produce a torso reaction, clear critical damage, and deal the configured fraction of normal damage. The console logs the first corrected roll once per process.
