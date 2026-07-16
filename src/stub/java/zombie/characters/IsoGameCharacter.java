package zombie.characters;

/**
 * Compile-only subset of the B42.19 API. The game supplies the real class at runtime.
 */
public class IsoGameCharacter {
    private boolean criticalHit;

    public boolean isCriticalHit() {
        return criticalHit;
    }

    public void setCriticalHit(boolean criticalHit) {
        this.criticalHit = criticalHit;
    }
}
