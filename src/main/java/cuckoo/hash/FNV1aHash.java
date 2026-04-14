package cuckoo.hash;

/**
 * FNV-1a 32-bit hash function (Fowler-Noll-Vo).
 * Simple and fast, but weaker avalanche properties than MurmurHash3 or xxHash.
 * Included for hash function sensitivity benchmarking.
 */
public final class FNV1aHash {
    private static final int FNV_OFFSET_BASIS = 0x811c9dc5;
    private static final int FNV_PRIME = 0x01000193;

    private FNV1aHash() {}

    public static int hash32(int key, int seed) {
        int h = FNV_OFFSET_BASIS ^ seed;

        // Process each byte of the 4-byte key
        h ^= (key & 0xFF);
        h *= FNV_PRIME;
        h ^= ((key >>> 8) & 0xFF);
        h *= FNV_PRIME;
        h ^= ((key >>> 16) & 0xFF);
        h *= FNV_PRIME;
        h ^= ((key >>> 24) & 0xFF);
        h *= FNV_PRIME;

        return h;
    }
}
