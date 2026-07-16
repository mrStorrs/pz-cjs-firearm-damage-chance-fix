package com.cjstorrs.firearmdamagechancefix;

import java.lang.reflect.Method;

/**
 * Loads the compiled patch against the real game classes under PZ's Java runtime.
 */
public final class GameApiLinkageTest {
    private GameApiLinkageTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        ClassLoader loader = GameApiLinkageTest.class.getClassLoader();
        Class<?> combatManager = load(loader, "zombie.CombatManager");
        Class<?> ballisticsController = load(loader, "zombie.core.physics.BallisticsController");
        Class<?> statisticsManager = load(loader, "zombie.statistics.StatisticsManager");
        Class<?> isoGameCharacter = load(loader, "zombie.characters.IsoGameCharacter");
        Class<?> isoMovingObject = load(loader, "zombie.iso.IsoMovingObject");
        Class<?> baseVehicle = load(loader, "zombie.vehicles.BaseVehicle");
        Class<?> handWeapon = load(loader, "zombie.inventory.types.HandWeapon");
        Class<?> swipeState = load(loader, "zombie.ai.states.SwipeStatePlayer");
        Class<?> attackType = load(loader, "zombie.AttackType");
        Class<?> shotDirection = load(loader, "zombie.combat.ShotDirection");
        Class<?> hitReaction = load(loader, "zombie.combat.HitReaction");
        Class<?> statisticType = load(loader, "zombie.statistics.StatisticType");
        Class<?> statisticCategory = load(loader, "zombie.statistics.StatisticCategory");

        requireMethod(
            combatManager,
            "attackCollisionCheck",
            isoGameCharacter,
            handWeapon,
            swipeState,
            attackType
        );
        requireMethod(combatManager, "resolveHitReaction", isoGameCharacter, isoGameCharacter, shotDirection);
        check(
            requireMethod(combatManager, "processHit", handWeapon, isoGameCharacter, isoGameCharacter).getReturnType()
                == int.class,
            "CombatManager.processHit must return int"
        );
        check(
            requireMethod(ballisticsController, "getCachedTargetedBodyPart", int.class).getReturnType() == int.class,
            "BallisticsController targeted body part must return int"
        );
        requireMethod(statisticsManager, "incrementStatistic", statisticType, statisticCategory, String.class, float.class);
        requireMethod(isoGameCharacter, "Hit", handWeapon, isoGameCharacter, float.class, boolean.class, float.class);
        requireMethod(isoMovingObject, "Hit", handWeapon, isoGameCharacter, float.class, boolean.class, float.class);
        requireMethod(baseVehicle, "processHit", isoGameCharacter, handWeapon, float.class);
        requireMethod(isoGameCharacter, "setCriticalHit", boolean.class);

        Class<?> ragdollBodyPart = load(loader, "zombie.core.physics.RagdollBodyPart");
        requireMethod(ragdollBodyPart, "isHead", int.class);
        ragdollBodyPart.getField("BODYPART_SPINE");
        ragdollBodyPart.getField("BODYPART_HEAD");
        hitReaction.getField("SHOT_HEAD_FWD");
        hitReaction.getField("SHOT_HEAD_FWD02");
        hitReaction.getField("SHOT_HEAD_BWD");
        hitReaction.getField("SHOT_CHEST");

        Class<?> sandboxOptions = load(loader, "zombie.SandboxOptions");
        Class<?> sandboxOption = load(loader, "zombie.SandboxOptions$SandboxOption");
        Class<?> integerSandboxOption = load(loader, "zombie.SandboxOptions$IntegerSandboxOption");
        check(
            requireMethod(sandboxOptions, "getOptionByName", String.class).getReturnType() == sandboxOption,
            "SandboxOptions.getOptionByName return type changed"
        );
        check(
            integerSandboxOption.getMethod("getValue").getReturnType() == int.class,
            "IntegerSandboxOption.getValue must return int"
        );

        Class<?> patches = load(loader, "com.cjstorrs.firearmdamagechancefix.FirearmDamageChancePatches");
        requireMethod(patches, "applyPendingReducedDamage", isoGameCharacter, float.class);
        requireMethod(
            load(loader, patches.getName() + "$ResolvedHitReaction"),
            "exit",
            hitReaction
        );
        requireMethod(
            load(loader, patches.getName() + "$ProcessedBodyPart"),
            "exit",
            isoGameCharacter,
            int.class
        );
        requireMethod(
            load(loader, patches.getName() + "$CharacterHit"),
            "enter",
            isoGameCharacter,
            float.class
        );

        System.out.println("GameApiLinkageTest: PASS");
    }

    private static Class<?> load(ClassLoader loader, String className) throws ClassNotFoundException {
        return Class.forName(className, false, loader);
    }

    private static Method requireMethod(Class<?> owner, String name, Class<?>... parameters)
            throws NoSuchMethodException {
        return owner.getDeclaredMethod(name, parameters);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
