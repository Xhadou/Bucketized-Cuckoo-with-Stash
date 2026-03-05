package cuckoo;

import cuckoo.core.BucketizedCuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class BucketizedCuckooTest {
    @Test
    public void testBasicPutGet() {
        BucketizedCuckooHashTable<String, Integer> t = new BucketizedCuckooHashTable<>(100, 4);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.get("a"));
    }

    @Test
    public void testHighLoadFactor() {
        int capacity = 10000;
        BucketizedCuckooHashTable<Integer, Integer> t = new BucketizedCuckooHashTable<>(capacity, 4);
        int inserted = 0;
        for (int i = 0; i < (int)(capacity * 0.90); i++) {
            t.put(i, i);
            inserted++;
        }
        // Verify all inserted keys found
        for (int i = 0; i < inserted; i++) {
            assertEquals(Integer.valueOf(i), t.get(i));
        }
        // Load factor should be near 90%
        assertTrue("Load factor should exceed 80%, got " + t.loadFactor(), t.loadFactor() > 0.80);
    }

    @Test
    public void testBucketSize1MatchesStandard() {
        // B=1 should behave like standard cuckoo (low load factor)
        BucketizedCuckooHashTable<Integer, Integer> t = new BucketizedCuckooHashTable<>(1000, 1);
        for (int i = 0; i < 400; i++) {
            t.put(i, i);
        }
        for (int i = 0; i < 400; i++) {
            assertEquals(Integer.valueOf(i), t.get(i));
        }
    }

    @Test
    public void testRemove() {
        BucketizedCuckooHashTable<String, Integer> t = new BucketizedCuckooHashTable<>(100, 4);
        t.put("x", 10);
        assertEquals(Integer.valueOf(10), t.remove("x"));
        assertNull(t.get("x"));
    }

    @Test
    public void testUpdateExistingKey() {
        BucketizedCuckooHashTable<String, Integer> t = new BucketizedCuckooHashTable<>(100, 4);
        t.put("k", 1);
        t.put("k", 2);
        assertEquals(Integer.valueOf(2), t.get("k"));
        assertEquals(1, t.size());
    }
}
