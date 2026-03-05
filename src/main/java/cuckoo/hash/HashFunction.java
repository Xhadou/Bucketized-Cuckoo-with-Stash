package cuckoo.hash;

@FunctionalInterface
public interface HashFunction<K> {
    int apply(K key);
}
