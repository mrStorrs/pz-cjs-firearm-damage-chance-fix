package com.cjstorrs.firearmdamagechancefix;

import java.lang.reflect.Method;
import java.util.List;
import me.zed_0xff.zombie_buddy.Patch;
import me.zed_0xff.zombie_buddy.PatchEngine;

public final class DamageChancePatchTest {
    private DamageChancePatchTest() {
    }

    public static void main(String[] args) throws ReflectiveOperationException, InterruptedException {
        testMarkerLifecycle();
        testAttackCleanup();
        testThreadIsolation();
        testPatchMetadata();
        testZombieBuddyDiscovery();
        System.out.println("DamageChancePatchTest: PASS");
    }

    private static void testMarkerLifecycle() {
        DamageChanceContext.resetForTest();
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "marker must be inactive outside an attack");

        DamageChanceContext.beginAttack();
        DamageChanceContext.recordStatistic("Bullets Chance Missed");
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "unrelated statistic must not mark damage ignored");

        DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        check(DamageChanceContext.applyPendingIgnoreDamage(false), "failed Damage Chance roll must force ignored damage");
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "marker must be consumed exactly once");

        DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        check(DamageChanceContext.applyPendingIgnoreDamage(true), "existing ignore flag must remain true and consume the marker");
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "consumed marker must not leak");
        DamageChanceContext.endAttack();
    }

    private static void testAttackCleanup() {
        DamageChanceContext.resetForTest();
        FirearmDamageChancePatches.AttackScope.enter();
        FirearmDamageChancePatches.FailedDamageStatistic.exit(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        FirearmDamageChancePatches.AttackScope.exit();
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "attack exit must discard an unconsumed marker");

        FirearmDamageChancePatches.AttackScope.enter();
        FirearmDamageChancePatches.FailedDamageStatistic.exit(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
        check(FirearmDamageChancePatches.VehicleHit.enter(), "vehicle patch must skip a failed all-target Damage Chance roll");
        check(!FirearmDamageChancePatches.VehicleHit.enter(), "vehicle skip marker must be single-use");
        FirearmDamageChancePatches.AttackScope.exit();
    }

    private static void testThreadIsolation() throws InterruptedException {
        DamageChanceContext.resetForTest();
        DamageChanceContext.beginAttack();

        boolean[] workerResult = {false};
        Thread worker = new Thread(() -> {
            DamageChanceContext.beginAttack();
            DamageChanceContext.recordStatistic(DamageChanceContext.DAMAGE_IGNORED_STATISTIC);
            workerResult[0] = DamageChanceContext.applyPendingIgnoreDamage(false);
            DamageChanceContext.endAttack();
        });
        worker.start();
        worker.join();

        check(workerResult[0], "worker thread must consume its own marker");
        check(!DamageChanceContext.applyPendingIgnoreDamage(false), "worker marker must not leak to the caller thread");
        DamageChanceContext.endAttack();
    }

    private static void testPatchMetadata() throws ReflectiveOperationException {
        List<Class<?>> patchClasses = List.of(
            FirearmDamageChancePatches.AttackScope.class,
            FirearmDamageChancePatches.FailedDamageStatistic.class,
            FirearmDamageChancePatches.CharacterHit.class,
            FirearmDamageChancePatches.MovingObjectHit.class,
            FirearmDamageChancePatches.VehicleHit.class
        );

        for (Class<?> patchClass : patchClasses) {
            check(patchClass.isAnnotationPresent(Patch.class), patchClass.getName() + " must carry @Patch");
        }

        Patch characterPatch = FirearmDamageChancePatches.CharacterHit.class.getAnnotation(Patch.class);
        check("zombie.characters.IsoGameCharacter".equals(characterPatch.className()), "character patch target changed");
        check("Hit".equals(characterPatch.methodName()), "character patch method changed");

        Patch vehiclePatch = FirearmDamageChancePatches.VehicleHit.class.getAnnotation(Patch.class);
        check("zombie.vehicles.BaseVehicle".equals(vehiclePatch.className()), "vehicle patch target changed");
        check("processHit".equals(vehiclePatch.methodName()), "vehicle patch method changed");

        Method attackExit = FirearmDamageChancePatches.AttackScope.class.getDeclaredMethod("exit");
        Patch.OnExit attackExitAdvice = attackExit.getAnnotation(Patch.OnExit.class);
        check(attackExitAdvice != null, "attack scope exit must carry @Patch.OnExit");
        check(Throwable.class.equals(attackExitAdvice.onThrowable()), "attack scope must clean up after exceptions");

        Method statisticExit = FirearmDamageChancePatches.FailedDamageStatistic.class.getDeclaredMethod("exit", String.class);
        Patch.Argument statisticArgument = statisticExit.getParameters()[0].getAnnotation(Patch.Argument.class);
        check(statisticArgument != null, "statistic key must carry @Patch.Argument");
        check(statisticArgument.value() == 2, "statistic key must remain argument 2");

        assertMutableIgnoreDamageArgument(FirearmDamageChancePatches.CharacterHit.class);
        assertMutableIgnoreDamageArgument(FirearmDamageChancePatches.MovingObjectHit.class);

        Method vehicleEnter = FirearmDamageChancePatches.VehicleHit.class.getDeclaredMethod("enter");
        Patch.OnEnter vehicleEnterAdvice = vehicleEnter.getAnnotation(Patch.OnEnter.class);
        check(vehicleEnterAdvice != null, "vehicle entry must carry @Patch.OnEnter");
        check(vehicleEnterAdvice.skipOn(), "vehicle entry must skip failed Damage Chance hits");
        check(vehicleEnter.getReturnType().equals(boolean.class), "vehicle skip advice must return primitive boolean");
    }

    private static void assertMutableIgnoreDamageArgument(Class<?> patchClass) throws ReflectiveOperationException {
        Method enter = patchClass.getDeclaredMethod("enter", boolean.class);
        Patch.Argument argument = enter.getParameters()[0].getAnnotation(Patch.Argument.class);
        check(argument != null, patchClass.getName() + " entry argument must carry @Patch.Argument");
        check(argument.value() == 3, patchClass.getName() + " must patch ignoreDamage argument 3");
        check(!argument.readOnly(), patchClass.getName() + " ignoreDamage argument must remain mutable");
    }

    private static void testZombieBuddyDiscovery() {
        List<Class<?>> discovered = PatchEngine.collectPatches(
            "com.cjstorrs.firearmdamagechancefix",
            DamageChancePatchTest.class.getClassLoader()
        );
        check(discovered.size() == 5, "ZombieBuddy must discover exactly five patch classes");
        check(discovered.contains(FirearmDamageChancePatches.AttackScope.class), "ZombieBuddy missed attack scope patch");
        check(discovered.contains(FirearmDamageChancePatches.FailedDamageStatistic.class), "ZombieBuddy missed statistic patch");
        check(discovered.contains(FirearmDamageChancePatches.CharacterHit.class), "ZombieBuddy missed character hit patch");
        check(discovered.contains(FirearmDamageChancePatches.MovingObjectHit.class), "ZombieBuddy missed moving-object hit patch");
        check(discovered.contains(FirearmDamageChancePatches.VehicleHit.class), "ZombieBuddy missed vehicle hit patch");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
