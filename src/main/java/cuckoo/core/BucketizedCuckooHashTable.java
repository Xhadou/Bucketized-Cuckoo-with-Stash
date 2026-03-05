package cuckoo.core;

import cuckoo.hash.MurmurHash3;
import cuckoo.stats.BenchmarkStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BucketizedCuckooHashTable<K, V> implements CuckooHashTable<K, V> {

    private static class Entry<K, V> {
        K key;
        V value;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class Bucket<K, V> {
        @SuppressWarnings("unchecked")
        private final Entry<K, V>[] slots;
        private final int bucketSize;

        @SuppressWarnings("unchecked")
        Bucket(int bucketSize) {
            this.bucketSize = bucketSize;
            this.slots = new Entry[bucketSize];
        }

        V find(K key) {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] != null && slots[i].key.equals(key)) {
                    return slots[i].value;
                }
            }
            return null;
        }

        boolean tryInsert(Entry<K, V> entry) {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] == null) {
                    slots[i] = entry;
                    return true;
                }
            }
            return false;
        }

        boolean isFull() {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] == null) {
                    return false;
                }
            }
            return true;
        }

        Entry<K, V> evict(int index) {
            Entry<K, V> evicted = slots[index];
            slots[index] = null;
            return evicted;
        }

        boolean updateIfExists(K key, V value) {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] != null && slots[i].key.equals(key)) {
                    slots[i].value = value;
                    return true;
                }
            }
            return false;
        }

        V removeKeyAndReturn(K key) {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] != null && slots[i].key.equals(key)) {
                    V val = slots[i].value;
                    slots[i] = null;
                    return val;
                }
            }
            return null;
        }

        boolean containsKey(K key) {
            for (int i = 0; i < bucketSize; i++) {
                if (slots[i] != null && slots[i].key.equals(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    private List<Bucket<K, V>> buckets1;
    private List<Bucket<K, V>> buckets2;
    private int numBuckets;
    private final int bucketSize;
    private int size;
    private int seed1;
    private int seed2;
    private final Random random;
    private final BenchmarkStats stats = new BenchmarkStats();
    private static final int MAX_LOOP = 500;

    public BucketizedCuckooHashTable(int expectedSize, int bucketSize) {
        this.bucketSize = bucketSize;
        this.random = new Random();
        this.seed1 = random.nextInt();
        this.seed2 = random.nextInt();
        this.numBuckets = Math.max(1, (int) Math.ceil((double) expectedSize / (bucketSize * 0.9 * 2)));
        this.buckets1 = createBucketList(numBuckets);
        this.buckets2 = createBucketList(numBuckets);
        this.size = 0;
    }

    private List<Bucket<K, V>> createBucketList(int length) {
        List<Bucket<K, V>> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(new Bucket<>(bucketSize));
        }
        return list;
    }

    private int hash1(K key) {
        return Math.floorMod(MurmurHash3.hash32(key.hashCode(), seed1), numBuckets);
    }

    private int hash2(K key) {
        return Math.floorMod(MurmurHash3.hash32(key.hashCode(), seed2), numBuckets);
    }

    @Override
    public V get(K key) {
        int h1 = hash1(key);
        Bucket<K, V> b1 = buckets1.get(h1);
        if (b1.containsKey(key)) {
            return b1.find(key);
        }
        int h2 = hash2(key);
        return buckets2.get(h2).find(key);
    }

    @Override
    public void put(K key, V value) {
        // Step 1: Check if key exists in either bucket (update if so)
        int h1 = hash1(key);
        if (buckets1.get(h1).updateIfExists(key, value)) {
            return;
        }
        int h2 = hash2(key);
        if (buckets2.get(h2).updateIfExists(key, value)) {
            return;
        }

        // Step 2: Insert new entry with bounded rehash attempts
        Entry<K, V> entry = new Entry<>(key, value);
        insertEntry(entry);
    }

    private void insertEntry(Entry<K, V> entry) {
        for (int rehashAttempt = 0; rehashAttempt < 20; rehashAttempt++) {
            int h1 = hash1(entry.key);
            if (buckets1.get(h1).tryInsert(entry)) {
                size++;
                stats.recordDisplacementChain(0);
                return;
            }
            int h2 = hash2(entry.key);
            if (buckets2.get(h2).tryInsert(entry)) {
                size++;
                stats.recordDisplacementChain(0);
                return;
            }

            // Both full — displacement chain
            for (int i = 0; i < MAX_LOOP; i++) {
                // Evict a random entry from buckets1[h1]
                int evictIndex = random.nextInt(bucketSize);
                Entry<K, V> evicted = buckets1.get(h1).evict(evictIndex);
                buckets1.get(h1).tryInsert(entry);
                entry = evicted;

                // Try to place evicted entry in its alternate bucket (table 2)
                int altH2 = hash2(entry.key);
                if (buckets2.get(altH2).tryInsert(entry)) {
                    size++;
                    stats.recordDisplacementChain(i + 1);
                    return;
                }

                // Evict from buckets2[altH2]
                evictIndex = random.nextInt(bucketSize);
                evicted = buckets2.get(altH2).evict(evictIndex);
                buckets2.get(altH2).tryInsert(entry);
                entry = evicted;

                // Try to place in table 1
                h1 = hash1(entry.key);
                if (buckets1.get(h1).tryInsert(entry)) {
                    size++;
                    stats.recordDisplacementChain(i + 1);
                    return;
                }
            }

            // maxLoop exceeded — rehash and retry
            stats.recordDisplacementChain(MAX_LOOP);
            rehash();
        }
        throw new IllegalStateException("Insertion failed after 20 rehash attempts");
    }

    @Override
    public V remove(K key) {
        int h1 = hash1(key);
        Bucket<K, V> b1 = buckets1.get(h1);
        if (b1.containsKey(key)) {
            size--;
            return b1.removeKeyAndReturn(key);
        }
        int h2 = hash2(key);
        Bucket<K, V> b2 = buckets2.get(h2);
        if (b2.containsKey(key)) {
            size--;
            return b2.removeKeyAndReturn(key);
        }
        return null;
    }

    private void rehash() {
        stats.recordRehash();
        List<Bucket<K, V>> oldBuckets1 = buckets1;
        List<Bucket<K, V>> oldBuckets2 = buckets2;
        int oldSize = size;

        for (int attempt = 0; attempt < 10; attempt++) {
            seed1 = random.nextInt();
            seed2 = random.nextInt();
            buckets1 = createBucketList(numBuckets);
            buckets2 = createBucketList(numBuckets);
            size = 0;

            boolean success = true;
            try {
                reinsertAll(oldBuckets1);
                reinsertAll(oldBuckets2);
            } catch (IllegalStateException ex) {
                success = false;
            }

            if (success && size == oldSize) {
                return;
            }
        }

        // All 10 attempts failed: double numBuckets and retry
        numBuckets *= 2;
        rehashFromBuckets(oldBuckets1, oldBuckets2, oldSize);
    }

    private void rehashFromBuckets(List<Bucket<K, V>> oldB1, List<Bucket<K, V>> oldB2, int oldSize) {
        for (int attempt = 0; attempt < 10; attempt++) {
            seed1 = random.nextInt();
            seed2 = random.nextInt();
            buckets1 = createBucketList(numBuckets);
            buckets2 = createBucketList(numBuckets);
            size = 0;

            boolean success = true;
            try {
                reinsertAll(oldB1);
                reinsertAll(oldB2);
            } catch (IllegalStateException ex) {
                success = false;
            }

            if (success && size == oldSize) {
                return;
            }
        }

        // Still failing: double again
        numBuckets *= 2;
        rehashFromBuckets(oldB1, oldB2, oldSize);
    }

    private void reinsertAll(List<Bucket<K, V>> buckets) {
        for (Bucket<K, V> bucket : buckets) {
            for (int i = 0; i < bucketSize; i++) {
                if (bucket.slots[i] != null) {
                    insertEntryNoRehash(bucket.slots[i]);
                }
            }
        }
    }

    private void insertEntryNoRehash(Entry<K, V> entry) {
        int h1 = hash1(entry.key);
        if (buckets1.get(h1).tryInsert(entry)) {
            size++;
            return;
        }
        int h2 = hash2(entry.key);
        if (buckets2.get(h2).tryInsert(entry)) {
            size++;
            return;
        }

        // Displacement chain
        for (int i = 0; i < MAX_LOOP; i++) {
            int evictIndex = random.nextInt(bucketSize);
            Entry<K, V> evicted = buckets1.get(h1).evict(evictIndex);
            buckets1.get(h1).tryInsert(entry);
            entry = evicted;

            int altH2 = hash2(entry.key);
            if (buckets2.get(altH2).tryInsert(entry)) {
                size++;
                return;
            }

            evictIndex = random.nextInt(bucketSize);
            evicted = buckets2.get(altH2).evict(evictIndex);
            buckets2.get(altH2).tryInsert(entry);
            entry = evicted;

            h1 = hash1(entry.key);
            if (buckets1.get(h1).tryInsert(entry)) {
                size++;
                return;
            }
        }

        throw new IllegalStateException("Insertion failed during rehash");
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public double loadFactor() {
        return (double) size / (2 * numBuckets * bucketSize);
    }

    @Override
    public BenchmarkStats getStats() {
        return stats;
    }
}
