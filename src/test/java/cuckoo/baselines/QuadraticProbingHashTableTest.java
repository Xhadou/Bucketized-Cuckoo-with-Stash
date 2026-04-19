package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuadraticProbingHashTableTest {

    @Test
    public void testPutGet() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(100);
        t.put("a", 1);
        t.put("b", 2);
        assertEquals(Integer.valueOf(1), t.get("a"));
        assertEquals(Integer.valueOf(2), t.get("b"));
    }

    @Test
    public void testUpdate() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(100);
        t.put("a", 1);
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testRemove() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(100);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.remove("a"));
        assertNull(t.get("a"));
        assertEquals(0, t.size());
    }

    @Test
    public void testRemoveThenReinsert() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(16);
        t.put("a", 1);
        t.remove("a");
        assertNull(t.get("a"));
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testBulkInsertions() {
        QuadraticProbingHashTable<Integer, String> t = new QuadraticProbingHashTable<>(16);
        for (int i = 0; i < 2000; i++) {
            t.put(i, "v" + i);
        }
        for (int i = 0; i < 2000; i++) {
            assertEquals("v" + i, t.get(i));
        }
        assertEquals(2000, t.size());
    }

    @Test
    public void testNegativeLookup() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(100);
        assertNull(t.get("missing"));
        assertNull(t.remove("missing"));
    }

    @Test
    public void testLoadFactor() {
        QuadraticProbingHashTable<Integer, Integer> t = new QuadraticProbingHashTable<>(100);
        assertEquals(0.0, t.loadFactor(), 0.0001);
        t.put(1, 1);
        assertTrue(t.loadFactor() > 0.0);
    }

    @Test
    public void testTombstoneCleanupOnResize() {
        // Use a small capacity so resizes happen quickly
        QuadraticProbingHashTable<Integer, Integer> t = new QuadraticProbingHashTable<>(16);
        // Insert enough to approach threshold, then delete and reinsert to create tombstones
        for (int i = 0; i < 6; i++) {
            t.put(i, i);
        }
        // Remove most of them — creates tombstones
        for (int i = 0; i < 5; i++) {
            t.remove(i);
        }
        assertEquals(1, t.size());
        // Now insert many more — tombstones + new entries will force a resize
        // After resize, tombstones should be cleaned up
        for (int i = 100; i < 200; i++) {
            t.put(i, i);
        }
        // All keys should be retrievable
        assertEquals(Integer.valueOf(5), t.get(5));
        for (int i = 100; i < 200; i++) {
            assertEquals(Integer.valueOf(i), t.get(i));
        }
        assertEquals(101, t.size());
    }

    @Test
    public void testImplementsInterface() {
        CuckooHashTable<String, Integer> t = new QuadraticProbingHashTable<>(16);
        t.put("x", 10);
        assertEquals(Integer.valueOf(10), t.get("x"));
        assertEquals(1, t.size());
    }

    @Test
    public void testStatsTracking() {
        QuadraticProbingHashTable<String, Integer> t = new QuadraticProbingHashTable<>(100);
        t.put("a", 1);
        t.get("a");
        t.remove("a");
        assertEquals(1, t.getStats().getInsertCount());
        assertEquals(1, t.getStats().getLookupCount());
        assertEquals(1, t.getStats().getRemoveCount());
    }
}
