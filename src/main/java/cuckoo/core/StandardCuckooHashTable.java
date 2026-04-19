package cuckoo.core;

import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import cuckoo.stats.BenchmarkStats;

import java.util.Random;

public class StandardCuckooHashTable<K, V> implements CuckooHashTable<K, V> {

    private static class Entry<K, V> {
        K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private Entry<K, V>[] table1;
    private Entry<K, V>[] table2;
    private int capacity;
    private final int expectedSize;
    private int size;
    private int maxLoop;
    private int seed1;
    private int seed2;
    private final Random random;
    private final HashFamily hashFamily;
    private final BenchmarkStats stats = new BenchmarkStats();
    private static final int MAX_GROWTHS = 10;

    public StandardCuckooHashTable(int expectedSize) {
        this(expectedSize, HashFunctions.defaultFamily());
    }

    @SuppressWarnings("unchecked")
    public StandardCuckooHashTable(int expectedSize, HashFamily hashFamily) {
        this.expectedSize = expectedSize;
        this.hashFamily = hashFamily;
        this.capacity = (int) (expectedSize * 2.2);
        this.maxLoop = (int) (6 * Math.log(capacity) / Math.log(2));
        this.random = new Random();
        this.seed1 = random.nextInt();
        this.seed2 = random.nextInt();
        this.table1 = new Entry[capacity];
        this.table2 = new Entry[capacity];
        this.size = 0;
    }

    private int hash1(K key) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), seed1), capacity);
    }

    private int hash2(K key) {
        return Math.floorMod(hashFamily.hash(key.hashCode(), seed2), capacity);
    }

    @Override
    public V get(K key) {
        int h1 = hash1(key);
        if (table1[h1] != null && table1[h1].key.equals(key)) {
            return table1[h1].value;
        }
        int h2 = hash2(key);
        if (table2[h2] != null && table2[h2].key.equals(key)) {
            return table2[h2].value;
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        // Check for existing key first (update in-place if found in either table)
        int h1 = hash1(key);
        if (table1[h1] != null && table1[h1].key.equals(key)) {
            table1[h1].value = value;
            return;
        }
        int h2 = hash2(key);
        if (table2[h2] != null && table2[h2].key.equals(key)) {
            table2[h2].value = value;
            return;
        }

        // Create new entry and begin displacement chain
        Entry<K, V> entry = new Entry<>(key, value);
        insertEntry(entry);
    }

    private void insertEntry(Entry<K, V> entry) {
        for (int rehashAttempt = 0; rehashAttempt < 20; rehashAttempt++) {
            for (int i = 0; i < maxLoop; i++) {
                int h1 = hash1(entry.key);
                if (table1[h1] == null) {
                    table1[h1] = entry;
                    size++;
                    stats.recordDisplacementChain(i);
                    return;
                }
                // Swap with existing entry in table1
                Entry<K, V> displaced = table1[h1];
                table1[h1] = entry;
                entry = displaced;

                int h2 = hash2(entry.key);
                if (table2[h2] == null) {
                    table2[h2] = entry;
                    size++;
                    stats.recordDisplacementChain(i);
                    return;
                }
                // Swap with existing entry in table2
                displaced = table2[h2];
                table2[h2] = entry;
                entry = displaced;
            }

            // maxLoop exceeded: rehash and retry
            stats.recordDisplacementChain(maxLoop);
            rehash();
        }
        throw new IllegalStateException("Insertion failed after 20 rehash attempts");
    }

    @Override
    public V remove(K key) {
        int h1 = hash1(key);
        if (table1[h1] != null && table1[h1].key.equals(key)) {
            V value = table1[h1].value;
            table1[h1] = null;
            size--;
            return value;
        }
        int h2 = hash2(key);
        if (table2[h2] != null && table2[h2].key.equals(key)) {
            V value = table2[h2].value;
            table2[h2] = null;
            size--;
            return value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void rehash() {
        stats.recordRehash();
        Entry<K, V>[] oldTable1 = table1;
        Entry<K, V>[] oldTable2 = table2;
        int oldSize = size;

        for (int growth = 0; growth <= MAX_GROWTHS; growth++) {
            for (int attempt = 0; attempt < 10; attempt++) {
                seed1 = random.nextInt();
                seed2 = random.nextInt();
                table1 = new Entry[capacity];
                table2 = new Entry[capacity];
                maxLoop = (int) (6 * Math.log(capacity) / Math.log(2));
                size = 0;

                boolean success = true;
                try {
                    for (Entry<K, V> e : oldTable1) {
                        if (e != null) {
                            insertEntryNoRehash(e);
                        }
                    }
                    for (Entry<K, V> e : oldTable2) {
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

    private void insertEntryNoRehash(Entry<K, V> entry) {
        for (int i = 0; i < maxLoop; i++) {
            int h1 = hash1(entry.key);
            if (table1[h1] == null) {
                table1[h1] = entry;
                size++;
                return;
            }
            Entry<K, V> displaced = table1[h1];
            table1[h1] = entry;
            entry = displaced;

            int h2 = hash2(entry.key);
            if (table2[h2] == null) {
                table2[h2] = entry;
                size++;
                return;
            }
            displaced = table2[h2];
            table2[h2] = entry;
            entry = displaced;
        }

        throw new IllegalStateException("Insertion failed during rehash");
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public double loadFactor() {
        return (double) size / (2 * capacity);
    }

    @Override
    public BenchmarkStats getStats() {
        return stats;
    }
}
