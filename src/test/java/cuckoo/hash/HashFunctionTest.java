package cuckoo.hash;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.Random;

public class HashFunctionTest {

    // ---- XXHash32 tests ----

    @Test
    public void testXXHash32Consistency() {
        int h1 = XXHash32.hash32(12345, 0);
        int h2 = XXHash32.hash32(12345, 0);
        assertEquals("Same input and seed must produce same hash", h1, h2);

        int h3 = XXHash32.hash32(Integer.MIN_VALUE, 99);
        int h4 = XXHash32.hash32(Integer.MIN_VALUE, 99);
        assertEquals("Consistency must hold for any input/seed pair", h3, h4);
    }

    @Test
    public void testXXHash32SeedIndependence() {
        int h1 = XXHash32.hash32(42, 1);
        int h2 = XXHash32.hash32(42, 2);
        assertNotEquals("Different seeds should produce different hashes", h1, h2);
    }

    @Test
    public void testXXHash32Avalanche() {
        Random rng = new Random(42);
        long totalBitsChanged = 0;
        int numKeys = 1000;

        for (int i = 0; i < numKeys; i++) {
            int key = rng.nextInt();
            int hash1 = XXHash32.hash32(key, 0);

            // Flip one random bit in the input
            int bitToFlip = rng.nextInt(32);
            int flippedKey = key ^ (1 << bitToFlip);
            int hash2 = XXHash32.hash32(flippedKey, 0);

            totalBitsChanged += Integer.bitCount(hash1 ^ hash2);
        }

        double avgBitsChanged = (double) totalBitsChanged / numKeys;
        assertTrue("Flipping one input bit should change >= 10 output bits on average, but got "
                + avgBitsChanged, avgBitsChanged >= 10.0);
    }

    // ---- FNV1a tests ----

    @Test
    public void testFNV1aConsistency() {
        int h1 = FNV1aHash.hash32(12345, 0);
        int h2 = FNV1aHash.hash32(12345, 0);
        assertEquals("Same input and seed must produce same hash", h1, h2);

        int h3 = FNV1aHash.hash32(Integer.MAX_VALUE, 77);
        int h4 = FNV1aHash.hash32(Integer.MAX_VALUE, 77);
        assertEquals("Consistency must hold for any input/seed pair", h3, h4);
    }

    @Test
    public void testFNV1aSeedIndependence() {
        int h1 = FNV1aHash.hash32(42, 1);
        int h2 = FNV1aHash.hash32(42, 2);
        assertNotEquals("Different seeds should produce different hashes", h1, h2);
    }

    @Test
    public void testFNV1aDistribution() {
        int numKeys = 10000;
        int numBuckets = 1000;
        int[] bucketCounts = new int[numBuckets];

        for (int i = 0; i < numKeys; i++) {
            int h = FNV1aHash.hash32(i, 0);
            int bucket = Math.floorMod(h, numBuckets);
            bucketCounts[bucket]++;
        }

        double average = (double) numKeys / numBuckets; // 10.0
        int maxAllowed = (int) (average * 3);

        for (int i = 0; i < numBuckets; i++) {
            assertTrue("Bucket " + i + " has " + bucketCounts[i]
                    + " entries, exceeding 3x the average (" + maxAllowed + ")",
                    bucketCounts[i] <= maxAllowed);
        }
    }

    // ---- HashFunctions factory tests ----

    @Test
    public void testHashFunctionsFactory() {
        HashFamily murmur = HashFunctions.murmur3();
        HashFamily xxhash = HashFunctions.xxhash32();
        HashFamily fnv1a = HashFunctions.fnv1a();

        assertNotNull("murmur3() must return non-null", murmur);
        assertNotNull("xxhash32() must return non-null", xxhash);
        assertNotNull("fnv1a() must return non-null", fnv1a);

        int key = 999;
        int seed = 0;
        int hMurmur = murmur.hash(key, seed);
        int hXXHash = xxhash.hash(key, seed);
        int hFNV1a = fnv1a.hash(key, seed);

        // At least two of the three should differ (all three different is expected)
        boolean allSame = (hMurmur == hXXHash) && (hXXHash == hFNV1a);
        assertFalse("Different hash families should produce different results for the same input",
                allSame);
    }

    // ---- HashFamily interface tests ----

    @Test
    public void testHashFamilyDeterminism() {
        HashFamily[] families = {
            HashFunctions.murmur3(),
            HashFunctions.xxhash32(),
            HashFunctions.fnv1a()
        };

        for (HashFamily family : families) {
            int h1 = family.hash(42, 7);
            int h2 = family.hash(42, 7);
            assertEquals("HashFamily must be deterministic: same input -> same output", h1, h2);

            int h3 = family.hash(0, 0);
            int h4 = family.hash(0, 0);
            assertEquals("HashFamily must be deterministic for zero input", h3, h4);

            int h5 = family.hash(Integer.MIN_VALUE, Integer.MAX_VALUE);
            int h6 = family.hash(Integer.MIN_VALUE, Integer.MAX_VALUE);
            assertEquals("HashFamily must be deterministic for extreme inputs", h5, h6);
        }
    }
}
