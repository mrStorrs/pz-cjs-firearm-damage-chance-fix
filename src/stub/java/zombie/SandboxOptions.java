package zombie;

/**
 * Compile-only subset of the B42.19 API. The game supplies the real class at runtime.
 */
public final class SandboxOptions {
    public static final SandboxOptions instance = new SandboxOptions();

    private SandboxOption testOption;

    private SandboxOptions() {
    }

    public SandboxOption getOptionByName(String name) {
        return testOption;
    }

    public void setOptionForTest(SandboxOption option) {
        testOption = option;
    }

    public interface SandboxOption {
    }

    public static final class IntegerSandboxOption implements SandboxOption {
        private final int value;

        public IntegerSandboxOption(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
