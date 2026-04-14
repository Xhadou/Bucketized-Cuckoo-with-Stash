package cuckoo.hash;

/**
 * Factory for hash family instances.
 * Each method returns a HashFamily that can be injected into hash table constructors.
 */
public final class HashFunctions {
    private HashFunctions() {}

    public static HashFamily murmur3() {
        return MurmurHash3::hash32;
    }

    public static HashFamily xxhash32() {
        return XXHash32::hash32;
    }

    public static HashFamily fnv1a() {
        return FNV1aHash::hash32;
    }

    /** Default hash family used when none is specified. */
    public static HashFamily defaultFamily() {
        return MurmurHash3::hash32;
    }
}
