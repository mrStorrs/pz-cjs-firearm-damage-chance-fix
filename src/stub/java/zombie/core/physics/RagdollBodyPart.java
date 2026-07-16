package zombie.core.physics;

/**
 * Compile-only subset of the B42.19 API. The game supplies the real enum at runtime.
 */
public enum RagdollBodyPart {
    BODYPART_PELVIS,
    BODYPART_SPINE,
    BODYPART_HEAD;

    public static boolean isHead(int bodyPart) {
        return bodyPart == BODYPART_HEAD.ordinal();
    }
}
