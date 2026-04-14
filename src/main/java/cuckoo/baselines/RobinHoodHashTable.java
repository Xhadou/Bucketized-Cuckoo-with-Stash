package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import cuckoo.stats.BenchmarkStats;

public class RobinHoodHashTable<K, V> implements CuckooHashTable<K, V> {

    private static final int SEED = 42;
    private static final double LOAD_FACTOR_THRESHOLD = 0.9;

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
    private final HashFamily hashFamily;
    private final BenchmarkStats stats = new BenchmarkStats();

    public RobinHoodHashTable(int initialCapacity) {
        this(initialCapacity, HashFunctions.defaultFamily());
    }

    @SuppressWarnings("unchecked")
    public RobinHoodHashTable(int initialCapacity, HashFamily hashFamily) {
        this.capacity = Math.max(initialCapacity, 16);
        this.table = new Entry[this.capacity];
        this.size = 0;
        this.hashFamily = hashFamily;
    }

    private int hash(K key) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), SEED), capacity);
    }

    /**
     * Probe distance: how far this position is from the key's home bucket.
     */
    private int probeDistance(K key, int pos) {
        return (pos - hash(key) + capacity) % capacity;
    }

    @Override
    public V get(K key) {
        stats.recordLookup();
        int home = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = (home + i) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null; // empty slot — key absent
            }
            if (e.key.equals(key)) {
                return e.value;
            }
            // If the existing entry's probe distance is less than ours (i),
            // then our key would have been placed here or earlier — key absent.
            if (probeDistance(e.key, pos) < i) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        stats.recordInsert();

        // Resize before inserting if threshold exceeded
        if ((double) (size + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize(capacity * 2);
        }

        putInternal(key, value, true);
    }

    /**
     * Internal put. If countSize is true, increments size on new insertion.
     */
    private void putInternal(K key, V value, boolean countSize) {
        int home = hash(key);
        K curKey = key;
        V curValue = value;
        int curHome = home;

        for (int i = 0; i < capacity; i++) {
            int pos = (curHome + i) % capacity;
            Entry<K, V> e = table[pos];

            if (e == null) {
                table[pos] = new Entry<>(curKey, curValue);
                if (countSize) {
                    size++;
                }
                return;
            }

            // If we find the same key, update in place
            if (e.key.equals(curKey)) {
                e.value = curValue;
                // If we were carrying a displaced entry, that means curKey == original key
                // Only on the first iteration (curKey == key) should we NOT increment size
                // On subsequent iterations this shouldn't happen (no duplicate keys in table)
                return;
            }

            // Robin Hood: compare probe distances
            int existingDist = probeDistance(e.key, pos);
            int ourDist = (pos - curHome + capacity) % capacity;

            if (ourDist > existingDist) {
                // Steal this slot from the richer (shorter probe) entry
                table[pos] = new Entry<>(curKey, curValue);
                curKey = e.key;
                curValue = e.value;
                curHome = hash(curKey);
                // Continue inserting the displaced entry
                // Reset i so that i tracks displacement from curHome
                // The displaced entry was at distance existingDist from its home.
                // It needs to probe starting from the NEXT position.
                i = (pos - curHome + capacity) % capacity;
                // The for-loop will increment i, so the next pos will be curHome + i + 1
            }
        }

        // Should not reach here if resize works correctly
        resize(capacity * 2);
        putInternal(curKey, curValue, countSize);
    }

    @Override
    public V remove(K key) {
        stats.recordRemove();
        int home = hash(key);
        for (int i = 0; i < capacity; i++) {
            int pos = (home + i) % capacity;
            Entry<K, V> e = table[pos];
            if (e == null) {
                return null;
            }
            if (e.key.equals(key)) {
                V val = e.value;
                table[pos] = null;
                size--;
                // Backward-shift deletion
                shiftBack(pos);
                return val;
            }
            if (probeDistance(e.key, pos) < i) {
                return null; // key absent
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
     */
    private boolean shouldShift(int naturalPos, int gapPos, int currentPos) {
        if (gapPos < currentPos) {
            return naturalPos <= gapPos || naturalPos > currentPos;
        } else {
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
        stats.recordRehash();
        Entry<K, V>[] oldTable = table;
        int oldCapacity = capacity;
        this.capacity = newCapacity;
        this.table = new Entry[newCapacity];
        this.size = 0;
        for (int i = 0; i < oldCapacity; i++) {
            Entry<K, V> e = oldTable[i];
            if (e != null) {
                putInternal(e.key, e.value, true);
            }
        }
    }
}
