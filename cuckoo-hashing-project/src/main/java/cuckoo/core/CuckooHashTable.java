package cuckoo.core;

import cuckoo.stats.BenchmarkStats;

public interface CuckooHashTable<K, V> {
    V get(K key);
    void put(K key, V value);
    V remove(K key);
    int size();
    double loadFactor();
    BenchmarkStats getStats();
}
