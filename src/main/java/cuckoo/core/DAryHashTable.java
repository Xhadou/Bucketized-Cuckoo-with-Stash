package cuckoo.core;

import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import cuckoo.stats.BenchmarkStats;

import java.util.Random;

/**
 * d-ary cuckoo hash table: generalizes standard cuckoo hashing from 2 to d
 * hash functions over a single flat table.
 *
 * Each key has d candidate positions h_0(key)..h_{d-1}(key).
 * On collision the RANDOM displacement strategy is used: pick a random
 * candidate position, evict its occupant, and let the displaced entry
 * try its own d alternatives.  This gives O(1) amortized insertion for d >= 4
 * and excellent space utilisation (~97 % for d=4).
 */
public class DAryHashTable<K, V> implements CuckooHashTable<K, V> {

    /* ------------------------------------------------------------------ */
    /*  Inner entry type                                                   */
    /* ------------------------------------------------------------------ */
    private static class Entry<K, V> {
        K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Constants                                                          */
    /* ------------------------------------------------------------------ */
    private static final int MAX_LOOP = 500;
    private static final int MAX_GROWTHS = 10;

    /* ------------------------------------------------------------------ */
    /*  Fields                                                             */
    /* ------------------------------------------------------------------ */
    private final int d;
    private final HashFamily hashFamily;
    private final Random random;
    private final BenchmarkStats stats = new BenchmarkStats();

    private Entry<K, V>[] table;
    private int capacity;
    private int size;
    private int[] seeds;

    /* ------------------------------------------------------------------ */
    /*  Constructors                                                       */
    /* ------------------------------------------------------------------ */

    public DAryHashTable(int expectedSize, int d) {
        this(expectedSize, d, HashFunctions.defaultFamily());
    }

    @SuppressWarnings("unchecked")
    public DAryHashTable(int expectedSize, int d, HashFamily hashFamily) {
        if (d < 2) {
            throw new IllegalArgumentException("d must be >= 2, got " + d);
        }
        this.d = d;
        this.hashFamily = hashFamily;
        this.random = new Random();
        this.capacity = (int) (expectedSize * (d <= 2 ? 2.2 : 1.15));
        if (this.capacity < 1) {
            this.capacity = 1;
        }
        this.seeds = newSeeds();
        this.table = new Entry[capacity];
        this.size = 0;
    }

    /* ------------------------------------------------------------------ */
    /*  Hash helpers                                                        */
    /* ------------------------------------------------------------------ */

    private int[] newSeeds() {
        int[] s = new int[d];
        for (int i = 0; i < d; i++) {
            s[i] = random.nextInt();
        }
        return s;
    }

    private int hashFor(K key, int i) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), seeds[i]), capacity);
    }

    /* ------------------------------------------------------------------ */
    /*  Public API                                                         */
    /* ------------------------------------------------------------------ */

    @Override
    public V get(K key) {
        for (int i = 0; i < d; i++) {
            int pos = hashFor(key, i);
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                return e.value;
            }
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        // Update in place if already present
        for (int i = 0; i < d; i++) {
            int pos = hashFor(key, i);
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                e.value = value;
                return;
            }
        }

        Entry<K, V> entry = new Entry<>(key, value);
        insertEntry(entry);
    }

    @Override
    public V remove(K key) {
        for (int i = 0; i < d; i++) {
            int pos = hashFor(key, i);
            Entry<K, V> e = table[pos];
            if (e != null && e.key.equals(key)) {
                V value = e.value;
                table[pos] = null;
                size--;
                return value;
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

    /* ------------------------------------------------------------------ */
    /*  Insertion with random-walk displacement                            */
    /* ------------------------------------------------------------------ */

    private void insertEntry(Entry<K, V> entry) {
        for (int rehashAttempt = 0; rehashAttempt < 20; rehashAttempt++) {
            for (int loop = 0; loop < MAX_LOOP; loop++) {
                // Try each of the d candidate slots
                for (int i = 0; i < d; i++) {
                    int pos = hashFor(entry.key, i);
                    if (table[pos] == null) {
                        table[pos] = entry;
                        size++;
                        stats.recordDisplacementChain(loop);
                        return;
                    }
                }

                // All d positions occupied -- evict a random one
                int victim = random.nextInt(d);
                int pos = hashFor(entry.key, victim);
                Entry<K, V> displaced = table[pos];
                table[pos] = entry;
                entry = displaced;
            }

            // MAX_LOOP exceeded -- rehash and retry
            stats.recordDisplacementChain(MAX_LOOP);
            rehash();
        }
        throw new IllegalStateException("Insertion failed after 20 rehash attempts");
    }

    /**
     * Insert without triggering a recursive rehash.  Used during rehash to
     * re-insert every existing entry under the new seeds / capacity.
     */
    private void insertEntryNoRehash(Entry<K, V> entry) {
        for (int loop = 0; loop < MAX_LOOP; loop++) {
            for (int i = 0; i < d; i++) {
                int pos = hashFor(entry.key, i);
                if (table[pos] == null) {
                    table[pos] = entry;
                    size++;
                    return;
                }
            }

            int victim = random.nextInt(d);
            int pos = hashFor(entry.key, victim);
            Entry<K, V> displaced = table[pos];
            table[pos] = entry;
            entry = displaced;
        }
        throw new IllegalStateException("Insertion failed during rehash");
    }

    /* ------------------------------------------------------------------ */
    /*  Rehash: try new seeds, then grow capacity                          */
    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private void rehash() {
        stats.recordRehash();
        Entry<K, V>[] oldTable = table;
        int oldSize = size;

        for (int growth = 0; growth <= MAX_GROWTHS; growth++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                seeds = newSeeds();
                table = new Entry[capacity];
                size = 0;

                boolean success = true;
                try {
                    for (Entry<K, V> e : oldTable) {
                        if (e != null) {
                            insertEntryNoRehash(e);
                        }
                    }
                } catch (IllegalStateException ex) {
                    success = false;
                }

                if (success && size == oldSize) {
                    return;
                }
            }

            capacity *= 2;
        }
        throw new IllegalStateException("Rehash failed after " + MAX_GROWTHS + " capacity doublings");
    }
}
