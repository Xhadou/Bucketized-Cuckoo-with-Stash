package cuckoo.hash;

/**
 * MurmurHash3 (x86_32 variant) adapted for cuckoo hashing.
 * <p>
 * Provides deterministic, high-quality 32-bit hashes suitable for use as
 * independent hash functions when instantiated with different seeds.
 */
public final class MurmurHash3 {

    private MurmurHash3() { /* utility class */ }

    /**
     * MurmurHash3 finalizer / avalanche mix for 32-bit values.
     */
    public static int fmix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    /**
     * Full MurmurHash3_x86_32 for a single 4-byte (int) key.
     *
     * @param key  the integer key
     * @param seed the hash seed
     * @return 32-bit hash value
     */
    public static int hash32(int key, int seed) {
        int h = seed;
        int k = key;

        k *= 0xcc9e2d51;
        k = Integer.rotateLeft(k, 15);
        k *= 0x1b873593;

        h ^= k;
        h = Integer.rotateLeft(h, 13);
        h = h * 5 + 0xe6546b64;

        // finalization — length = 4
        h ^= 4;
        return fmix32(h);
    }

    /**
     * Hashes an arbitrary object by feeding its {@code hashCode()} through
     * the int variant.
     *
     * @param key  the object key (must not be {@code null})
     * @param seed the hash seed
     * @return 32-bit hash value
     */
    public static int hash32(Object key, int seed) {
        return hash32(key.hashCode(), seed);
    }
}
