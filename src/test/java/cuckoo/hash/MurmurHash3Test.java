package cuckoo.hash;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MurmurHash3Test {

    // --- fmix32 ---

    @Test
    void fmix32_zero_returns_zero() {
        assertEquals(0, MurmurHash3.fmix32(0));
    }

    @Test
    void fmix32_is_deterministic() {
        int a = MurmurHash3.fmix32(42);
        int b = MurmurHash3.fmix32(42);
        assertEquals(a, b);
    }

    @Test
    void fmix32_avalanche_different_inputs_produce_different_outputs() {
        assertNotEquals(MurmurHash3.fmix32(1), MurmurHash3.fmix32(2));
    }

    // --- hash32(int, int) ---

    @Test
    void hash32_int_deterministic() {
        int h1 = MurmurHash3.hash32(123, 42);
        int h2 = MurmurHash3.hash32(123, 42);
        assertEquals(h1, h2);
    }

    @Test
    void hash32_int_different_seeds_produce_different_hashes() {
        int h1 = MurmurHash3.hash32(123, 42);
        int h2 = MurmurHash3.hash32(123, 137);
        assertNotEquals(h1, h2, "Different seeds should produce different hashes");
    }

    @Test
    void hash32_int_different_keys_produce_different_hashes() {
        int h1 = MurmurHash3.hash32(100, 0);
        int h2 = MurmurHash3.hash32(200, 0);
        assertNotEquals(h1, h2, "Different keys should produce different hashes");
    }

    // --- hash32(Object, int) ---

    @Test
    void hash32_object_delegates_to_int_variant() {
        Integer key = 999;
        int fromObject = MurmurHash3.hash32((Object) key, 7);
        int fromInt    = MurmurHash3.hash32(key.hashCode(), 7);
        assertEquals(fromInt, fromObject);
    }

    @Test
    void hash32_object_string_deterministic() {
        String key = "hello";
        int h1 = MurmurHash3.hash32(key, 0);
        int h2 = MurmurHash3.hash32(key, 0);
        assertEquals(h1, h2);
    }

    // --- distribution sanity check ---

    @Test
    void hash32_distribution_covers_positive_and_negative_range() {
        boolean seenPositive = false;
        boolean seenNegative = false;
        for (int i = 0; i < 1000; i++) {
            int h = MurmurHash3.hash32(i, 42);
            if (h > 0) seenPositive = true;
            if (h < 0) seenNegative = true;
        }
        assertTrue(seenPositive, "Expected some positive hash values");
        assertTrue(seenNegative, "Expected some negative hash values");
    }
}
