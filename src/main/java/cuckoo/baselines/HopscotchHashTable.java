package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import cuckoo.stats.BenchmarkStats;

public class HopscotchHashTable<K, V> implements CuckooHashTable<K, V> {

    private static final int SEED = 42;
    private static final int H = 32; // neighborhood size — fits in an int bitmap
    private static final double LOAD_FACTOR_THRESHOLD = 0.9;

    private static class Entry<K, V> {
        final K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Entry<K, V>[] table;
    private int[] hopInfo; // bitmap per bucket: bit j set means slot (bucket+j)%cap belongs to this bucket
    private int capacity;
    private int size;
    private final HashFamily hashFamily;
    private final BenchmarkStats stats = new BenchmarkStats();

    public HopscotchHashTable(int initialCapacity) {
        this(initialCapacity, HashFunctions.defaultFamily());
    }

    @SuppressWarnings("unchecked")
    public HopscotchHashTable(int initialCapacity, HashFamily hashFamily) {
        this.capacity = Math.max(initialCapacity, 16);
        this.table = new Entry[this.capacity];
        this.hopInfo = new int[this.capacity];
        this.size = 0;
        this.hashFamily = hashFamily;
    }

    private int hash(K key) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), SEED), capacity);
    }

    @Override
    public V get(K key) {
        stats.recordLookup();
        int bucket = hash(key);
        int bitmap = hopInfo[bucket];
        while (bitmap != 0) {
            int bit = Integer.numberOfTrailingZeros(bitmap);
            int pos = (bucket + bit) % capacity;
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                return e.value;
            }
            bitmap &= bitmap - 1; // clear lowest set bit
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        stats.recordInsert();

        // Check for existing key first
        int bucket = hash(key);
        int bitmap = hopInfo[bucket];
        int tmp = bitmap;
        while (tmp != 0) {
            int bit = Integer.numberOfTrailingZeros(tmp);
            int pos = (bucket + bit) % capacity;
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                e.value = value;
                return;
            }
            tmp &= tmp - 1;
        }

        // Resize if load factor threshold exceeded
        if ((double) (size + 1) / capacity > LOAD_FACTOR_THRESHOLD) {
            resize(capacity * 2);
            put_internal(key, value);
            return;
        }

        if (!put_internal(key, value)) {
            resize(capacity * 2);
            put_internal(key, value);
        }
    }

    /**
     * Internal insert assuming key is not already present.
     * Returns true on success, false if we need to resize.
     */
    private boolean put_internal(K key, V value) {
        int bucket = hash(key);

        // Linear probe for an empty slot
        int emptySlot = -1;
        for (int i = 0; i < capacity; i++) {
            int pos = (bucket + i) % capacity;
            if (table[pos] == null) {
                emptySlot = pos;
                break;
            }
        }
        if (emptySlot == -1) {
            return false; // table completely full, need resize
        }

        // Try to bring the empty slot within H of the home bucket
        while (displacement(bucket, emptySlot) >= H) {
            // Find an entry in the H-1 slots before emptySlot that can be moved
            boolean moved = false;
            for (int d = H - 1; d >= 1; d--) {
                int candidate = (emptySlot - d + capacity) % capacity;
                // Check if candidate bucket owns any entry that could be moved to emptySlot
                // candidate's neighborhood bitmap tells us which slots belong to it.
                // We need to find a bit j in hopInfo[candidate] such that
                // (candidate + j) % capacity != emptySlot and j < d
                // (because moving to emptySlot means bit d replaces bit j)
                int candBitmap = hopInfo[candidate];
                // We only care about bits 0..d-1 (slots closer to candidate than emptySlot)
                // because bit d would mean the entry is already at emptySlot
                int mask = (d < 32) ? ((1 << d) - 1) : -1; // bits 0..d-1
                int movableBits = candBitmap & mask;
                if (movableBits != 0) {
                    int bit = Integer.numberOfTrailingZeros(movableBits);
                    int moveFrom = (candidate + bit) % capacity;
                    // Move entry from moveFrom to emptySlot
                    table[emptySlot] = table[moveFrom];
                    table[moveFrom] = null;
                    // Update candidate's bitmap: clear bit, set bit for d
                    hopInfo[candidate] &= ~(1 << bit);
                    hopInfo[candidate] |= (1 << d);
                    emptySlot = moveFrom;
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return false; // can't bring empty slot within neighborhood, need resize
            }
        }

        // Place the new entry
        table[emptySlot] = new Entry<>(key, value);
        int bit = displacement(bucket, emptySlot);
        hopInfo[bucket] |= (1 << bit);
        size++;
        return true;
    }

    /**
     * Circular distance from 'from' to 'to'.
     */
    private int displacement(int from, int to) {
        return (to - from + capacity) % capacity;
    }

    @Override
    public V remove(K key) {
        stats.recordRemove();
        int bucket = hash(key);
        int bitmap = hopInfo[bucket];
        while (bitmap != 0) {
            int bit = Integer.numberOfTrailingZeros(bitmap);
            int pos = (bucket + bit) % capacity;
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                V val = e.value;
                table[pos] = null;
                hopInfo[bucket] &= ~(1 << bit);
                size--;
                return val;
            }
            bitmap &= bitmap - 1;
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
        stats.recordRehash();
        Entry<K, V>[] oldTable = table;
        int oldCapacity = capacity;
        this.capacity = newCapacity;
        this.table = new Entry[newCapacity];
        this.hopInfo = new int[newCapacity];
        this.size = 0;
        for (int i = 0; i < oldCapacity; i++) {
            Entry<K, V> e = oldTable[i];
            if (e != null) {
                put_internal(e.key, e.value);
            }
        }
    }
}
