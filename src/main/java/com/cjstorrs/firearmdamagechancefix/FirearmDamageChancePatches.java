package com.cjstorrs.firearmdamagechancefix;

import me.zed_0xff.zombie_buddy.Patch;
import zombie.characters.IsoGameCharacter;
import zombie.combat.HitReaction;

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

    @Patch(className = "zombie.core.physics.BallisticsController", methodName = "getCachedTargetedBodyPart")
    public static final class TargetedBodyPart {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) int bodyPart) {
            bodyPart = DamageChanceContext.reroutePendingHeadBodyPart(bodyPart);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "resolveHitReaction")
    public static final class ResolvedHitReaction {
        @Patch.OnExit
        public static void exit(@Patch.Return(readOnly = false) HitReaction hitReaction) {
            hitReaction = DamageChanceContext.reroutePendingHeadReaction(hitReaction);
        }
    }

    @Patch(className = "zombie.CombatManager", methodName = "processHit")
    public static final class ProcessedBodyPart {
        @Patch.OnExit
        public static void exit(
                @Patch.Argument(1) IsoGameCharacter wielder,
                @Patch.Return(readOnly = false) int bodyPart) {
            bodyPart = DamageChanceContext.reroutePendingHeadBodyPart(bodyPart);
            if (DamageChanceContext.hasPendingDamageReduction()) {
                wielder.setCriticalHit(false);
            }
        }
    }

    @Patch(className = "zombie.characters.IsoGameCharacter", methodName = "Hit")
    public static final class CharacterHit {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(1) IsoGameCharacter wielder,
                @Patch.Argument(value = 2, readOnly = false) float damage) {
            damage = applyPendingReducedDamage(wielder, damage);
        }
    }

    @Patch(className = "zombie.iso.IsoMovingObject", methodName = "Hit")
    public static final class MovingObjectHit {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(1) IsoGameCharacter wielder,
                @Patch.Argument(value = 2, readOnly = false) float damage) {
            damage = applyPendingReducedDamage(wielder, damage);
        }
    }

    @Patch(className = "zombie.vehicles.BaseVehicle", methodName = "processHit")
    public static final class VehicleHit {
        @Patch.OnEnter
        public static void enter(
                @Patch.Argument(0) IsoGameCharacter wielder,
                @Patch.Argument(value = 2, readOnly = false) float damage) {
            damage = applyPendingReducedDamage(wielder, damage);
        }
    }

    public static float applyPendingReducedDamage(IsoGameCharacter wielder, float originalDamage) {
        if (!DamageChanceContext.consumePendingDamageReduction()) {
            return originalDamage;
        }

        wielder.setCriticalHit(false);
        return DamageChanceContext.reduceFailedRollDamage(originalDamage);
    }
}
