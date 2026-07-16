package com.cjstorrs.firearmdamagechancefix;

import java.util.concurrent.atomic.AtomicBoolean;
import zombie.combat.HitReaction;
import zombie.core.physics.RagdollBodyPart;

/**
 * Carries the engine's failed Damage Chance decision from the statistic call to
 * the immediately following target damage call on the same combat thread.
 */
public final class DamageChanceContext {
    static final String DAMAGE_IGNORED_STATISTIC = "Bullets Damage Ignored";

    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static final AtomicBoolean FIRST_CORRECTION_LOGGED = new AtomicBoolean(false);

    private DamageChanceContext() {
    }

    public static void beginAttack() {
        State state = STATE.get();
        state.attackDepth++;
        state.ignoreDamagePending = false;
    }

    public static void recordStatistic(String statisticName) {
        State state = STATE.get();
        if (state.attackDepth > 0 && DAMAGE_IGNORED_STATISTIC.equals(statisticName)) {
            state.ignoreDamagePending = true;
        }
    }

    public static boolean hasPendingDamageReduction() {
        State state = STATE.get();
        return state.attackDepth > 0 && state.ignoreDamagePending;
    }

    public static int reroutePendingHeadBodyPart(int originalBodyPart) {
        if (hasPendingDamageReduction() && RagdollBodyPart.isHead(originalBodyPart)) {
            return RagdollBodyPart.BODYPART_SPINE.ordinal();
        }
        return originalBodyPart;
    }

    public static HitReaction reroutePendingHeadReaction(HitReaction originalReaction) {
        if (!hasPendingDamageReduction()) {
            return originalReaction;
        }

        return switch (originalReaction) {
            case SHOT_HEAD_FWD, SHOT_HEAD_FWD02, SHOT_HEAD_BWD -> HitReaction.SHOT_CHEST;
            default -> originalReaction;
        };
    }

    public static boolean consumePendingDamageReduction() {
        State state = STATE.get();
        if (state.attackDepth <= 0 || !state.ignoreDamagePending) {
            return false;
        }

        state.ignoreDamagePending = false;
        if (FIRST_CORRECTION_LOGGED.compareAndSet(false, true)) {
            System.out.println("[cjsFirearmDamageChanceFix] Corrected first failed Damage Chance roll; applied configured non-critical damage and rerouted head targeting.");
        }
        return true;
    }

    public static float reduceFailedRollDamage(float originalDamage) {
        return originalDamage * DamageChanceSettings.getFailedRollDamageMultiplier();
    }

    public static void endAttack() {
        State state = STATE.get();
        if (state.attackDepth <= 1) {
            STATE.remove();
            return;
        }

        state.attackDepth--;
        state.ignoreDamagePending = false;
    }

    static void resetForTest() {
        STATE.remove();
        FIRST_CORRECTION_LOGGED.set(false);
    }

    private static final class State {
        private int attackDepth;
        private boolean ignoreDamagePending;
    }
}
