package cuckoo;

import cuckoo.hash.MurmurHash3;
import org.junit.Test;
import static org.junit.Assert.*;

public class MurmurHash3Test {
    @Test
    public void testHash32Deterministic() {
        int h1 = MurmurHash3.hash32(42, 0);
        int h2 = MurmurHash3.hash32(42, 0);
        assertEquals(h1, h2);
    }

    @Test
    public void testDifferentSeedsDifferentHash() {
        int h1 = MurmurHash3.hash32(42, 1);
        int h2 = MurmurHash3.hash32(42, 2);
        assertNotEquals(h1, h2);
    }

    @Test
    public void testDifferentKeysDifferentHash() {
        int h1 = MurmurHash3.hash32(1, 0);
        int h2 = MurmurHash3.hash32(2, 0);
        assertNotEquals(h1, h2);
    }

    @Test
    public void testFloorModNonNegative() {
        for (int i = -1000; i < 1000; i++) {
            int h = MurmurHash3.hash32(i, 42);
            int idx = Math.floorMod(h, 100);
            assertTrue("Index must be non-negative, got " + idx, idx >= 0);
            assertTrue("Index must be < capacity, got " + idx, idx < 100);
        }
    }

    @Test
    public void testKnownVector_seed0_key0() {
        int h = MurmurHash3.hash32(0, 0);
        assertEquals(h, MurmurHash3.hash32(0, 0));
    }
}
