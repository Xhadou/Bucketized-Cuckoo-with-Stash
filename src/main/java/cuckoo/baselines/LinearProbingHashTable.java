package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.hash.MurmurHash3;
import cuckoo.stats.BenchmarkStats;

public class LinearProbingHashTable<K, V> implements CuckooHashTable<K, V> {

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

    @SuppressWarnings("unchecked")
    private Entry<K, V>[] table;
    private int capacity;
    private int size;
    private final BenchmarkStats stats = new BenchmarkStats();

    @SuppressWarnings("unchecked")
    public LinearProbingHashTable(int initialCapacity) {
        this.capacity = Math.max(initialCapacity, 16);
        this.table = new Entry[this.capacity];
        this.size = 0;
    }

    private int hash(K key) {
        return Math.floorMod(MurmurHash3.hash32(key.hashCode(), SEED), capacity);
    }

    @Override
    public V get(K key) {
        int idx = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = (idx + i) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null;
            }
            if (e.key.equals(key)) {
                return e.value;
            }
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        // Resize before inserting if threshold exceeded
        if ((double) (size + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize(capacity * 2);
        }
        int idx = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = (idx + i) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                table[pos] = new Entry<>(key, value);
                size++;
                return;
            }
            if (e.key.equals(key)) {
                e.value = value;
                return;
            }
        }
        // Should not reach here if resize works correctly
        resize(capacity * 2);
        put(key, value);
    }

    @Override
    public V remove(K key) {
        int idx = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = (idx + i) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null;
            }
            if (e.key.equals(key)) {
                V val = e.value;
                table[pos] = null;
                size--;
                // Shift back subsequent entries that may have been displaced
                shiftBack(pos);
                return val;
            }
        }
        return null;
    }

    /**
     * After removing an entry at the given position, shift back any subsequent
     * entries whose natural hash position is at or before the gap. This avoids
     * the need for tombstones.
     */
    private void shiftBack(int removedPos) {
        int pos = removedPos;
        while (true) {
            pos = (pos + 1) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                break;
            }
            int naturalPos = hash(e.key);
            // Check if the entry at 'pos' needs to be moved back.
            // It needs moving if removedPos lies in the range [naturalPos, pos)
            // (circularly).
            if (shouldShift(naturalPos, removedPos, pos)) {
                table[removedPos] = e;
                table[pos] = null;
                removedPos = pos;
            }
        }
    }

    /**
     * Returns true if an entry with natural position 'naturalPos' currently
     * stored at 'currentPos' should be shifted to 'gapPos'.
     *
     * The condition: gapPos is between naturalPos and currentPos (circularly),
     * meaning the entry "passes over" the gap on its probe path.
     */
    private boolean shouldShift(int naturalPos, int gapPos, int currentPos) {
        if (gapPos < currentPos) {
            // gap ... current (no wrap-around between them)
            return naturalPos <= gapPos || naturalPos > currentPos;
        } else {
            // current ... gap (wrap-around between them)
            return naturalPos <= gapPos && naturalPos > currentPos;
        }
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
        for (int i = 0; i < oldCapacity; i++) {
            Entry<K, V> e = oldTable[i];
            if (e != null) {
                put(e.key, e.value);
            }
        }
    }
}
