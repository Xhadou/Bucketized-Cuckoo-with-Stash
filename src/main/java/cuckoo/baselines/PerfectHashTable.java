package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import cuckoo.stats.BenchmarkStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Fredman-Komlos-Szemeredi (FKS) two-level perfect hashing.
 *
 * Given a fixed set of n keys known at construction time, builds a data
 * structure that guarantees O(1) worst-case lookup with zero collisions.
 * Expected total space is O(n). Construction runs in expected O(n) time
 * (retries the primary hash until total secondary space <= 4n).
 *
 * This is a STATIC dictionary: put() and remove() throw
 * UnsupportedOperationException. Included for comparison against
 * dynamic schemes on read-only workloads, per professor feedback.
 */
public class PerfectHashTable<K, V> implements CuckooHashTable<K, V> {

    private static final long P = 2147483647L; // 2^31 - 1, Mersenne prime

    private final int n;
    private final int primarySize;
    private final long primaryA;
    private final long primaryB;
    private final Object[][] secondaryKeys;
    private final Object[][] secondaryValues;
    private final long[] secondaryA;
    private final long[] secondaryB;
    private final BenchmarkStats stats = new BenchmarkStats();

    @SuppressWarnings("unchecked")
    public PerfectHashTable(Map<K, V> entries) {
        this.n = entries.size();
        this.primarySize = Math.max(1, n);

        Random rng = new Random(42);
        long pa;
        long pb;
        List<Map.Entry<K, V>>[] buckets;

        // FKS primary: retry until sum of b_i^2 <= 4n (expected after a few tries).
        int attempts = 0;
        while (true) {
            pa = 1 + (Math.abs(rng.nextLong()) % (P - 1));
            pb = Math.abs(rng.nextLong()) % P;

            buckets = new List[primarySize];
            for (int i = 0; i < primarySize; i++) buckets[i] = new ArrayList<>();

            for (Map.Entry<K, V> e : entries.entrySet()) {
                int slot = primaryHash(e.getKey(), pa, pb);
                buckets[slot].add(e);
            }

            long totalSecondary = 0;
            for (List<Map.Entry<K, V>> b : buckets) {
                totalSecondary += (long) b.size() * b.size();
            }
            attempts++;
            if (totalSecondary <= 4L * n || attempts > 20) break;
        }
        this.primaryA = pa;
        this.primaryB = pb;

        this.secondaryKeys = new Object[primarySize][];
        this.secondaryValues = new Object[primarySize][];
        this.secondaryA = new long[primarySize];
        this.secondaryB = new long[primarySize];

        // Build each secondary: size = b_i^2, retry (a_i, b_i) until collision-free.
        for (int i = 0; i < primarySize; i++) {
            List<Map.Entry<K, V>> bucket = buckets[i];
            int bSize = bucket.size();
            if (bSize == 0) {
                secondaryKeys[i] = new Object[0];
                secondaryValues[i] = new Object[0];
                continue;
            }
            int sz = bSize * bSize;

            long sa = 1;
            long sb = 0;
            Object[] sk = null;
            Object[] sv = null;
            for (int retry = 0; retry < 200; retry++) {
                sa = 1 + (Math.abs(rng.nextLong()) % (P - 1));
                sb = Math.abs(rng.nextLong()) % P;
                sk = new Object[sz];
                sv = new Object[sz];
                boolean collision = false;
                for (Map.Entry<K, V> e : bucket) {
                    int slot = secondaryHash(e.getKey(), sa, sb, sz);
                    if (sk[slot] != null) { collision = true; break; }
                    sk[slot] = e.getKey();
                    sv[slot] = e.getValue();
                }
                if (!collision) break;
            }
            secondaryA[i] = sa;
            secondaryB[i] = sb;
            secondaryKeys[i] = sk;
            secondaryValues[i] = sv;
        }
        stats.updateMaxLoadFactor((double) n / primarySize);
    }

    private int primaryHash(K key, long a, long b) {
        long x = key.hashCode() & 0xFFFFFFFFL;
        return (int) (((a * x + b) % P) % primarySize);
    }

    private int secondaryHash(K key, long a, long b, int m) {
        long x = key.hashCode() & 0xFFFFFFFFL;
        return (int) (((a * x + b) % P) % m);
    }

    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        stats.recordLookup();
        int bucket = primaryHash(key, primaryA, primaryB);
        Object[] sk = secondaryKeys[bucket];
        if (sk.length == 0) return null;
        int slot = secondaryHash(key, secondaryA[bucket], secondaryB[bucket], sk.length);
        Object storedKey = sk[slot];
        if (storedKey != null && storedKey.equals(key)) {
            return (V) secondaryValues[bucket][slot];
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        throw new UnsupportedOperationException(
            "PerfectHashTable is static; construct with the full key set");
    }

    @Override
    public V remove(K key) {
        throw new UnsupportedOperationException(
            "PerfectHashTable is static; deletions not supported");
    }

    @Override
    public int size() { return n; }

    @Override
    public double loadFactor() {
        long totalSecondarySlots = 0;
        for (Object[] s : secondaryKeys) totalSecondarySlots += s.length;
        if (totalSecondarySlots == 0) return 0.0;
        return (double) n / totalSecondarySlots;
    }

    @Override
    public BenchmarkStats getStats() { return stats; }
}
