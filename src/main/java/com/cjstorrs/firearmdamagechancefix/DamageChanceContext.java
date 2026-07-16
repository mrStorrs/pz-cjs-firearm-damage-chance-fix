package com.cjstorrs.firearmdamagechancefix;

import java.util.concurrent.atomic.AtomicBoolean;

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

    public static boolean applyPendingIgnoreDamage(boolean originalIgnoreDamage) {
        State state = STATE.get();
        if (state.attackDepth <= 0 || !state.ignoreDamagePending) {
            return originalIgnoreDamage;
        }

        state.ignoreDamagePending = false;
        if (FIRST_CORRECTION_LOGGED.compareAndSet(false, true)) {
            System.out.println("[cjsFirearmDamageChanceFix] Corrected first failed Damage Chance roll; target health damage was suppressed.");
        }
        return true;
    }

    public static boolean consumePendingVehicleSkip() {
        return applyPendingIgnoreDamage(false);
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
