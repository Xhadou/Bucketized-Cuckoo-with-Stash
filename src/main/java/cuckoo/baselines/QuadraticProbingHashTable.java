package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import cuckoo.stats.BenchmarkStats;

public class QuadraticProbingHashTable<K, V> implements CuckooHashTable<K, V> {

    private static final int SEED = 42;
    private static final double LOAD_FACTOR_THRESHOLD = 0.5;

    private static class Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    /** Sentinel tombstone entry — key and value are null. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final Entry TOMBSTONE = new Entry(null, null);

    @SuppressWarnings("unchecked")
    private Entry<K, V>[] table;
    private int capacity;
    private int size;
    private int occupied; // size + tombstone count (for resize decisions)
    private final HashFamily hashFamily;
    private final BenchmarkStats stats = new BenchmarkStats();

    public QuadraticProbingHashTable(int initialCapacity) {
        this(initialCapacity, HashFunctions.defaultFamily());
    }

    @SuppressWarnings("unchecked")
    public QuadraticProbingHashTable(int initialCapacity, HashFamily hashFamily) {
        this.capacity = Math.max(Integer.highestOneBit(Math.max(initialCapacity, 16) - 1) << 1, 16);
        this.hashFamily = hashFamily;
        this.table = new Entry[this.capacity];
        this.size = 0;
        this.occupied = 0;
    }

    private int hash(K key) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), SEED), capacity);
    }

    /**
     * Triangular-number probe: position = (hash + i*(i+1)/2) % capacity.
     * Guaranteed to visit every slot when capacity is a power of 2.
     */
    private int probe(int hash, int i) {
        return Math.floorMod(hash + (i * i + i) / 2, capacity);
    }

    private boolean isTombstone(Entry<K, V> e) {
        return e == TOMBSTONE;
    }

    @Override
    public V get(K key) {
        stats.recordLookup();
        int h = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = probe(h, i);
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null;
            }
            if (!isTombstone(e) && e.key.equals(key)) {
                return e.value;
            }
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        stats.recordInsert();
        // Resize if occupied (live + tombstones) exceeds threshold
        if ((double) (occupied + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize(capacity * 2);
        }

        int h = hash(key);
        int firstTombstone = -1;
        for (int i = 0; i < capacity; i++) {
            int pos = probe(h, i);
            Entry<K, V> e = table[pos];
            if (e == null) {
                // Insert at first tombstone if we passed one, otherwise here
                if (firstTombstone != -1) {
                    table[firstTombstone] = new Entry<>(key, value);
                    // Tombstone reused — occupied stays the same
                } else {
                    table[pos] = new Entry<>(key, value);
                    occupied++;
                }
                size++;
                return;
            }
            if (isTombstone(e)) {
                if (firstTombstone == -1) {
                    firstTombstone = pos;
                }
                continue;
            }
            if (e.key.equals(key)) {
                e.value = value;
                return;
            }
        }
        // Table is full — should not happen if resize works correctly
        resize(capacity * 2);
        put(key, value);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V remove(K key) {
        // unchecked: TOMBSTONE cast
        stats.recordRemove();
        int h = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = probe(h, i);
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null;
            }
            if (!isTombstone(e) && e.key.equals(key)) {
                V val = e.value;
                table[pos] = (Entry<K, V>) TOMBSTONE;
                size--;
                // occupied stays the same — tombstone still counts
                return val;
            }
        }
        return null;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public double loadFactor() {
        return (double) size / capacity;
    }

    @Override
    public BenchmarkStats getStats() {
        return stats;
    }

    @SuppressWarnings("unchecked")
    private void resize(int newCapacity) {
        Entry<K, V>[] oldTable = table;
        int oldCapacity = capacity;
        this.capacity = newCapacity;
        this.table = new Entry[newCapacity];
        this.size = 0;
        this.occupied = 0;
        for (int i = 0; i < oldCapacity; i++) {
            Entry<K, V> e = oldTable[i];
            if (e != null && !isTombstone(e)) {
                put(e.key, e.value);
            }
        }
    }
}
