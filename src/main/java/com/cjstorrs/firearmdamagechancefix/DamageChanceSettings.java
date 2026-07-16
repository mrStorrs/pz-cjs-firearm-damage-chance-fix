package com.cjstorrs.firearmdamagechancefix;

import java.util.concurrent.atomic.AtomicBoolean;
import zombie.SandboxOptions;

public final class DamageChanceSettings {
    static final String DAMAGE_PERCENT_OPTION = "CJSFirearmDamageChanceFix.FailedRollDamagePercent";
    static final int DEFAULT_DAMAGE_PERCENT = 25;

    private static final AtomicBoolean MISSING_OPTION_LOGGED = new AtomicBoolean(false);

    private DamageChanceSettings() {
    }

    public static float getFailedRollDamageMultiplier() {
        SandboxOptions.SandboxOption option = SandboxOptions.instance.getOptionByName(DAMAGE_PERCENT_OPTION);
        if (option instanceof SandboxOptions.IntegerSandboxOption integerOption) {
            return multiplierFromPercent(integerOption.getValue());
        }

        if (MISSING_OPTION_LOGGED.compareAndSet(false, true)) {
            System.out.println(
                "[cjsFirearmDamageChanceFix] Sandbox option "
                    + DAMAGE_PERCENT_OPTION
                    + " is unavailable; using the 25% default."
            );
        }
        return multiplierFromPercent(DEFAULT_DAMAGE_PERCENT);
    }

    static float multiplierFromPercent(int percent) {
        int boundedPercent = Math.max(0, Math.min(100, percent));
        return boundedPercent / 100.0F;
    }
}
