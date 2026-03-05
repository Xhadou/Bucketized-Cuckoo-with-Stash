package cuckoo.hash;

public final class MurmurHash3 {
    private MurmurHash3() {}

    public static int hash32(int key, int seed) {
        int h = seed;
        int k = key;
        k *= 0xcc9e2d51;
        k = Integer.rotateLeft(k, 15);
        k *= 0x1b873593;
        h ^= k;
        h = Integer.rotateLeft(h, 13);
        h = h * 5 + 0xe6546b64;
        // finalization
        h ^= 4; // length = 4 bytes
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    public static int hash32(Object key, int seed) {
        return hash32(key.hashCode(), seed);
    }
}
