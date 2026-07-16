# CJS Firearm Damage Chance Fix

ZombieBuddy patch for a Project Zomboid B42.19 combat ordering bug.

When a firearm accuracy roll fails in Damage Chance mode, `CombatManager` correctly records `Bullets Damage Ignored` and sets its per-target ignore flag. It then calls the target's `Hit(...)` method with the older cumulative flag before merging the current flag. The failed roll therefore applies full damage to the first target of a shot.

This mod leaves the game JAR untouched. Its ZombieBuddy advice patches:

1. Open and close a thread-local scope around `CombatManager.attackCollisionCheck(...)`.
2. Mark the exact `Bullets Damage Ignored` statistic branch after it completes.
3. Consume that marker at the immediately following character or moving-object `Hit(...)` call and force `ignoreDamage=true`.
4. Skip `BaseVehicle.processHit(...)` when the same failed roll occurs with "All types of target" enabled.

Hit reactions, targeted body parts, sounds, statistics, and the normal successful-roll damage pipeline are otherwise unchanged.

## Build

The build requires Java 17 or newer and a local ZombieBuddy JAR. The default path matches this workspace.

```bash
./build.sh
```

Override the dependency path when needed:

```bash
ZOMBIE_BUDDY_JAR=/path/to/ZombieBuddy.jar ./build.sh
```

The tracked runtime JAR is written to `42/media/java/CJSFirearmDamageChanceFix.jar`. The build also runs the context and patch-metadata tests.

## In-game verification

Enable `ZombieBuddy` followed by `cjsFirearmDamageChanceFix`, choose `Firearms Use Damage Chance = Zombies only`, and fire poorly aimed shots at a distant zombie. Failed rolls may still produce the intended visual hit reaction, but they must not reduce zombie health or kill it. The console logs the first corrected roll once per process.
