package cuckoo.hash;

/**
 * 32-bit xxHash implementation (based on Yann Collet's xxHash algorithm).
 * Optimized for speed; passes all SMHasher quality tests.
 */
public final class XXHash32 {
    private static final int PRIME2 = 0x85EBCA77;
    private static final int PRIME3 = 0xC2B2AE3D;
    private static final int PRIME4 = 0x27D4EB2F;
    private static final int PRIME5 = 0x165667B1;

    private XXHash32() {}

    public static int hash32(int key, int seed) {
        int h = seed + PRIME5 + 4; // seed + PRIME5 + input length (4 bytes)

        h += key * PRIME3;
        h = Integer.rotateLeft(h, 17) * PRIME4;

        // Finalization (avalanche)
        h ^= h >>> 15;
        h *= PRIME2;
        h ^= h >>> 13;
        h *= PRIME3;
        h ^= h >>> 16;
        return h;
    }
}
