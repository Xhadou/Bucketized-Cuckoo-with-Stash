package cuckoo.hash;

/**
 * A hash function that maps a key of type {@code K} to a 32-bit integer.
 *
 * @param <K> the type of keys to hash
 */
@FunctionalInterface
public interface HashFunction<K> {

    /**
     * Computes a 32-bit hash value for the given key.
     *
     * @param key the key to hash
     * @return a 32-bit hash value
     */
    int hash(K key);
}
