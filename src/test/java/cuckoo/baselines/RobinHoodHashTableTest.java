package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class RobinHoodHashTableTest {

    @Test
    public void testPutGet() {
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(100);
        t.put("a", 1);
        t.put("b", 2);
        assertEquals(Integer.valueOf(1), t.get("a"));
        assertEquals(Integer.valueOf(2), t.get("b"));
    }

    @Test
    public void testUpdate() {
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(100);
        t.put("a", 1);
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testRemove() {
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(100);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.remove("a"));
        assertNull(t.get("a"));
        assertEquals(0, t.size());
    }

    @Test
    public void testBulkInsertions() {
        RobinHoodHashTable<Integer, String> t = new RobinHoodHashTable<>(16);
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
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(100);
        assertNull(t.get("missing"));
        assertNull(t.remove("missing"));
    }

    @Test
    public void testLoadFactor() {
        RobinHoodHashTable<Integer, Integer> t = new RobinHoodHashTable<>(100);
        assertEquals(0.0, t.loadFactor(), 0.0001);
        t.put(1, 1);
        assertTrue(t.loadFactor() > 0.0);
    }

    @Test
    public void testImplementsInterface() {
        CuckooHashTable<String, Integer> t = new RobinHoodHashTable<>(16);
        t.put("x", 10);
        assertEquals(Integer.valueOf(10), t.get("x"));
        assertEquals(1, t.size());
    }

    @Test
    public void testRemoveThenReinsert() {
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(16);
        t.put("a", 1);
        t.remove("a");
        assertNull(t.get("a"));
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testStatsTracking() {
        RobinHoodHashTable<String, Integer> t = new RobinHoodHashTable<>(100);
        t.put("a", 1);
        t.get("a");
        t.remove("a");
        assertEquals(1, t.getStats().getInsertCount());
        assertEquals(1, t.getStats().getLookupCount());
        assertEquals(1, t.getStats().getRemoveCount());
    }
}
