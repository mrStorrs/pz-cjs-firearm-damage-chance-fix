package com.cjstorrs.firearmdamagechancefix;

import me.zed_0xff.zombie_buddy.Patch;

public final class FirearmDamageChancePatches {
    private FirearmDamageChancePatches() {
    }

    @Patch(className = "zombie.CombatManager", methodName = "attackCollisionCheck")
    public static final class AttackScope {
        @Patch.OnEnter
        public static void enter() {
            DamageChanceContext.beginAttack();
        }

        @Patch.OnExit(onThrowable = Throwable.class)
        public static void exit() {
            DamageChanceContext.endAttack();
        }
    }

    @Patch(className = "zombie.statistics.StatisticsManager", methodName = "incrementStatistic")
    public static final class FailedDamageStatistic {
        @Patch.OnExit
        public static void exit(@Patch.Argument(2) String statisticName) {
            DamageChanceContext.recordStatistic(statisticName);
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "Hit")
    public static final class CharacterHit {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 3, readOnly = false) boolean ignoreDamage) {
            ignoreDamage = DamageChanceContext.applyPendingIgnoreDamage(ignoreDamage);
        }
    }

    @Patch(className = "zombie.iso.IsoMovingObject", methodName = "Hit")
    public static final class MovingObjectHit {
        @Patch.OnEnter
        public static void enter(@Patch.Argument(value = 3, readOnly = false) boolean ignoreDamage) {
            ignoreDamage = DamageChanceContext.applyPendingIgnoreDamage(ignoreDamage);
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "processHit")
    public static final class VehicleHit {
        @Patch.OnEnter(skipOn = true)
        public static boolean enter() {
            return DamageChanceContext.consumePendingVehicleSkip();
        }
    }
}
