package com.cjstorrs.firearmdamagechancefix;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.PatchEngine;
import zombie.SandboxOptions;
import zombie.characters.IsoGameCharacter;
import zombie.combat.HitReaction;
import zombie.core.physics.RagdollBodyPart;

public final class DamageChancePatchTest {
    private DamageChancePatchTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException, InterruptedException {
        testMarkerLifecycleAndHeadRerouting();
        testDamagePercentage();
        testReducedDamageAndCriticalCleanup();
        testAttackCleanup();
        testThreadIsolation();
        testPatchMetadata();
        testZombieBuddyDiscovery();
        System.out.println("DamageChancePatchTest: PASS");
    }

    private static void testMarkerLifecycleAndHeadRerouting() {
        DamageChanceContext.resetForTest();
        check(!DamageChanceContext.consumePendingDamageReduction(), "marker must be inactive outside an attack");

        DamageChanceContext.beginAttack();
        DamageChanceContext.recordStatistic("Bullets Chance Missed");
        check(!DamageChanceContext.hasPendingDamageReduction(), "unrelated statistic must not mark reduced damage");

        DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        check(DamageChanceContext.hasPendingDamageReduction(), "failed Damage Chance roll must mark reduced damage");
        check(
            DamageChanceContext.reroutePendingHeadBodyPart(RagdollBodyPart.BODYPART_HEAD.ordinal())
                == RagdollBodyPart.BODYPART_SPINE.ordinal(),
            "failed targeted head hit must move to the torso"
        );
        check(
            DamageChanceContext.reroutePendingHeadBodyPart(RagdollBodyPart.BODYPART_PELVIS.ordinal())
                == RagdollBodyPart.BODYPART_PELVIS.ordinal(),
            "failed non-head body part must remain unchanged"
        );
        check(
            DamageChanceContext.reroutePendingHeadReaction(HitReaction.SHOT_HEAD_FWD) == HitReaction.SHOT_CHEST,
            "failed head reaction must become a chest reaction"
        );
        check(
            DamageChanceContext.reroutePendingHeadReaction(HitReaction.SHOT_LEG_L) == HitReaction.SHOT_LEG_L,
            "failed non-head reaction must remain unchanged"
        );
        check(DamageChanceContext.consumePendingDamageReduction(), "failed roll marker must be consumable");
        check(!DamageChanceContext.hasPendingDamageReduction(), "marker must be consumed exactly once");
        check(!DamageChanceContext.consumePendingDamageReduction(), "consumed marker must not leak");
        DamageChanceContext.endAttack();
    }

    private static void testDamagePercentage() {
        check(DamageChanceSettings.DEFAULT_DAMAGE_PERCENT == 25, "default failed-roll damage must remain 25 percent");
        checkClose(0.25F, DamageChanceSettings.multiplierFromPercent(25), "25 percent must become a 0.25 multiplier");
        checkClose(0.0F, DamageChanceSettings.multiplierFromPercent(0), "zero percent must disable failed-roll damage");
        checkClose(1.0F, DamageChanceSettings.multiplierFromPercent(100), "100 percent must preserve base damage");
        checkClose(0.0F, DamageChanceSettings.multiplierFromPercent(-5), "percentage must clamp at zero");
        checkClose(1.0F, DamageChanceSettings.multiplierFromPercent(105), "percentage must clamp at 100");

        SandboxOptions.instance.setOptionForTest(new SandboxOptions.IntegerSandboxOption(60));
        checkClose(
            0.60F,
            DamageChanceSettings.getFailedRollDamageMultiplier(),
            "configured sandbox percentage must control failed-roll damage"
        );
        SandboxOptions.instance.setOptionForTest(null);
        checkClose(
            0.25F,
            DamageChanceSettings.getFailedRollDamageMultiplier(),
            "missing sandbox option must fall back to 25 percent"
        );
    }

    private static void testReducedDamageAndCriticalCleanup() {
        DamageChanceContext.resetForTest();
        SandboxOptions.instance.setOptionForTest(new SandboxOptions.IntegerSandboxOption(25));
        IsoGameCharacter wielder = new IsoGameCharacter();
        wielder.setCriticalHit(true);

        DamageChanceContext.beginAttack();
        DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        float damage = FirearmDamageChancePatches.applyPendingReducedDamage(wielder, 8.0F);

        checkClose(2.0F, damage, "failed roll must deal the configured fraction of normal damage");
        check(!wielder.isCriticalHit(), "failed roll must clear the critical-hit flag");
        check(!DamageChanceContext.hasPendingDamageReduction(), "damage application must consume the marker");
        DamageChanceContext.endAttack();
    }

    private static void testAttackCleanup() {
        DamageChanceContext.resetForTest();
        FirearmDamageChancePatches.AttackScope.enter();
        FirearmDamageChancePatches.FailedDamageStatistic.exit(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        FirearmDamageChancePatches.AttackScope.exit();
        check(!DamageChanceContext.hasPendingDamageReduction(), "attack exit must discard an unconsumed marker");
        check(!DamageChanceContext.consumePendingDamageReduction(), "cleaned marker must not affect another attack");
    }

    private static void testThreadIsolation() throws InterruptedException {
        DamageChanceContext.resetForTest();
        DamageChanceContext.beginAttack();

        boolean[] workerResult = {false};
        Thread worker = new Thread(() -> {
            DamageChanceContext.beginAttack();
            DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
            workerResult[0] = DamageChanceContext.consumePendingDamageReduction();
            DamageChanceContext.endAttack();
        });
        worker.start();
        worker.join();

        check(workerResult[0], "worker thread must consume its own marker");
        check(!DamageChanceContext.hasPendingDamageReduction(), "worker marker must not leak to the caller thread");
        DamageChanceContext.endAttack();
    }

    private static void testPatchMetadata() throws ReflectiveOperationException {
        List<Class<?>> patchClasses = patchClasses();
        for (Class<?> patchClass : patchClasses) {
            check(patchClass.isAnnotationPresent(Patch.class), patchClass.getName() + " must carry @Patch");
        }

        assertPatchTarget(
            FirearmDamageChancePatches.TargetedBodyPart.class,
            "zombie.core.physics.BallisticsController",
            "getCachedTargetedBodyPart"
        );
        assertPatchTarget(FirearmDamageChancePatches.ResolvedHitReaction.class, "zombie.CombatManager", "resolveHitReaction");
        assertPatchTarget(FirearmDamageChancePatches.ProcessedBodyPart.class, "zombie.CombatManager", "processHit");
        assertPatchTarget(FirearmDamageChancePatches.CharacterHit.class, "zombie.characters.IsoGameCharacter", "Hit");
        assertPatchTarget(FirearmDamageChancePatches.MovingObjectHit.class, "zombie.iso.IsoMovingObject", "Hit");
        assertPatchTarget(FirearmDamageChancePatches.VehicleHit.class, "zombie.vehicles.BaseVehicle", "processHit");

        Method attackExit = FirearmDamageChancePatches.AttackScope.class.getDeclaredMethod("exit");
        Patch.OnExit attackExitAdvice = attackExit.getAnnotation(Patch.OnExit.class);
        check(attackExitAdvice != null, "attack scope exit must carry @Patch.OnExit");
        check(Throwable.class.equals(attackExitAdvice.onThrowable()), "attack scope must clean up after exceptions");

        Method statisticExit = FirearmDamageChancePatches.FailedDamageStatistic.class.getDeclaredMethod("exit", String.class);
        assertArgument(statisticExit.getParameters()[0], 2, true, "statistic key");

        Method targetedExit = FirearmDamageChancePatches.TargetedBodyPart.class.getDeclaredMethod("exit", int.class);
        assertMutableReturn(targetedExit.getParameters()[0], "cached targeted body part");

        Method reactionExit = FirearmDamageChancePatches.ResolvedHitReaction.class.getDeclaredMethod("exit", HitReaction.class);
        assertMutableReturn(reactionExit.getParameters()[0], "resolved hit reaction");

        Method processedExit = FirearmDamageChancePatches.ProcessedBodyPart.class.getDeclaredMethod(
            "exit",
            IsoGameCharacter.class,
            int.class
        );
        assertArgument(processedExit.getParameters()[0], 1, true, "processHit wielder");
        assertMutableReturn(processedExit.getParameters()[1], "processed body part");

        assertMutableDamageAdvice(FirearmDamageChancePatches.CharacterHit.class, 1);
        assertMutableDamageAdvice(FirearmDamageChancePatches.MovingObjectHit.class, 1);
        assertMutableDamageAdvice(FirearmDamageChancePatches.VehicleHit.class, 0);
    }

    private static void assertPatchTarget(Class<?> patchClass, String className, String methodName) {
        Patch patch = patchClass.getAnnotation(Patch.class);
        check(className.equals(patch.className()), patchClass.getName() + " target class changed");
        check(methodName.equals(patch.methodName()), patchClass.getName() + " target method changed");
    }

    private static void assertMutableDamageAdvice(Class<?> patchClass, int wielderArgumentIndex) throws ReflectiveOperationException {
        Method enter = patchClass.getDeclaredMethod("enter", IsoGameCharacter.class, float.class);
        Patch.OnEnter advice = enter.getAnnotation(Patch.OnEnter.class);
        check(advice != null, patchClass.getName() + " entry must carry @Patch.OnEnter");
        check(!advice.skipOn(), patchClass.getName() + " must reduce damage without skipping the target method");
        assertArgument(enter.getParameters()[0], wielderArgumentIndex, true, patchClass.getName() + " wielder");
        assertArgument(enter.getParameters()[1], 2, false, patchClass.getName() + " damage");
    }

    private static void assertArgument(Parameter parameter, int index, boolean readOnly, String label) {
        Patch.Argument argument = parameter.getAnnotation(Patch.Argument.class);
        check(argument != null, label + " must carry @Patch.Argument");
        check(argument.value() == index, label + " argument index changed");
        check(argument.readOnly() == readOnly, label + " mutability changed");
    }

    private static void assertMutableReturn(Parameter parameter, String label) {
        Patch.Return returnValue = parameter.getAnnotation(Patch.Return.class);
        check(returnValue != null, label + " must carry @Patch.Return");
        check(!returnValue.readOnly(), label + " must remain mutable");
    }

    private static void testZombieBuddyDiscovery() {
        List<Class<?>> discovered = PatchEngine.collectPatches(
            "com.cjstorrs.firearmdamagechancefix",
            DamageChancePatchTest.class.getClassLoader()
        );
        List<Class<?>> expected = patchClasses();
        check(discovered.size() == expected.size(), "ZombieBuddy must discover exactly eight patch classes");
        for (Class<?> patchClass : expected) {
            check(discovered.contains(patchClass), "ZombieBuddy missed " + patchClass.getName());
        }
    }

    private static List<Class<?>> patchClasses() {
        return List.of(
            FirearmDamageChancePatches.AttackScope.class,
            FirearmDamageChancePatches.FailedDamageStatistic.class,
            FirearmDamageChancePatches.TargetedBodyPart.class,
            FirearmDamageChancePatches.ResolvedHitReaction.class,
            FirearmDamageChancePatches.ProcessedBodyPart.class,
            FirearmDamageChancePatches.CharacterHit.class,
            FirearmDamageChancePatches.MovingObjectHit.class,
            FirearmDamageChancePatches.VehicleHit.class
        );
    }

    private static void checkClose(float expected, float actual, String message) {
        if (Math.abs(expected - actual) > 0.0001F) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
