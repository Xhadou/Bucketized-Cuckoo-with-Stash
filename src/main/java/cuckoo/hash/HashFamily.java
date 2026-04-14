package cuckoo.hash;

/**
 * A family of hash functions parameterized by seed.
 * Given the same (keyHash, seed) pair, always returns the same output.
 * Different seeds produce independent hash functions from the same family.
 */
@FunctionalInterface
public interface HashFamily {
    int hash(int keyHash, int seed);
}
