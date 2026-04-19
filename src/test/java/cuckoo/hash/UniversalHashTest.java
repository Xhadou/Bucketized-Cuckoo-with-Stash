package cuckoo.hash;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class UniversalHashTest {

    @Test
    public void determinismSameSeedSameInput() {
        int h1 = UniversalHash.hash32(42, 7);
        int h2 = UniversalHash.hash32(42, 7);
        assertEquals(h1, h2);
    }

    @Test
    public void differentSeedsProduceDifferentHashes() {
        int h0 = UniversalHash.hash32(12345, 0);
        int h1 = UniversalHash.hash32(12345, 1);
        int h2 = UniversalHash.hash32(12345, 2);
        assertNotEquals(h0, h1);
        assertNotEquals(h1, h2);
        assertNotEquals(h0, h2);
    }

    @Test
    public void outputDistributionReasonablyUniform() {
        // Insert 10K distinct keys into 1000 buckets, chi-square-ish sanity check.
        int m = 1000;
        int n = 10_000;
        int[] counts = new int[m];
        for (int x = 0; x < n; x++) {
            int h = UniversalHash.hash32(x, 42);
            counts[Math.floorMod(h, m)]++;
        }
        // Expected ~10 per bucket. No bucket should be grossly under/overloaded.
        int min = Integer.MAX_VALUE, max = 0;
        for (int c : counts) { min = Math.min(min, c); max = Math.max(max, c); }
        assertTrue("min bucket " + min + " too low", min > 0);
        assertTrue("max bucket " + max + " too high", max < 40);
    }

    @Test
    public void handlesNegativeKeys() {
        // Should not crash or produce degenerate output on negative inputs.
        Set<Integer> hashes = new HashSet<>();
        for (int x = -1000; x < 1000; x++) {
            hashes.add(UniversalHash.hash32(x, 99));
        }
        // Should produce many distinct hashes.
        assertTrue(hashes.size() > 1900);
    }
}
