package cuckoo.baselines;

import cuckoo.core.CuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class HopscotchHashTableTest {

    @Test
    public void testPutGet() {
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(100);
        t.put("a", 1);
        t.put("b", 2);
        assertEquals(Integer.valueOf(1), t.get("a"));
        assertEquals(Integer.valueOf(2), t.get("b"));
    }

    @Test
    public void testUpdate() {
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(100);
        t.put("a", 1);
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testRemove() {
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(100);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.remove("a"));
        assertNull(t.get("a"));
        assertEquals(0, t.size());
    }

    @Test
    public void testBulkInsertions() {
        HopscotchHashTable<Integer, String> t = new HopscotchHashTable<>(16);
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
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(100);
        assertNull(t.get("missing"));
        assertNull(t.remove("missing"));
    }

    @Test
    public void testLoadFactor() {
        HopscotchHashTable<Integer, Integer> t = new HopscotchHashTable<>(100);
        assertEquals(0.0, t.loadFactor(), 0.0001);
        t.put(1, 1);
        assertTrue(t.loadFactor() > 0.0);
    }

    @Test
    public void testImplementsInterface() {
        CuckooHashTable<String, Integer> t = new HopscotchHashTable<>(16);
        t.put("x", 10);
        assertEquals(Integer.valueOf(10), t.get("x"));
        assertEquals(1, t.size());
    }

    @Test
    public void testRemoveThenReinsert() {
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(16);
        t.put("a", 1);
        t.remove("a");
        assertNull(t.get("a"));
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testStatsTracking() {
        HopscotchHashTable<String, Integer> t = new HopscotchHashTable<>(100);
        t.put("a", 1);
        t.get("a");
        t.remove("a");
        assertEquals(1, t.getStats().getInsertCount());
        assertEquals(1, t.getStats().getLookupCount());
        assertEquals(1, t.getStats().getRemoveCount());
    }
}
