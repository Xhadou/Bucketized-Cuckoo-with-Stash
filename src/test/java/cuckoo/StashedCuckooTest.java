package cuckoo;

import cuckoo.core.StashedCuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class StashedCuckooTest {
    @Test
    public void testBasicOperations() {
        StashedCuckooHashTable<String, Integer> t = new StashedCuckooHashTable<>(100, 4, 3);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.get("a"));
        t.remove("a");
        assertNull(t.get("a"));
    }

    @Test
    public void testStashAbsorbsFailures() {
        StashedCuckooHashTable<Integer, Integer> t = new StashedCuckooHashTable<>(10000, 4, 3);
        for (int i = 0; i < 9000; i++) {
            t.put(i, i);
        }
        for (int i = 0; i < 9000; i++) {
            assertEquals(Integer.valueOf(i), t.get(i));
        }
    }

    @Test
    public void testStashSize0MatchesBucketized() {
        StashedCuckooHashTable<Integer, Integer> t = new StashedCuckooHashTable<>(1000, 4, 0);
        for (int i = 0; i < 800; i++) {
            t.put(i, i);
        }
        for (int i = 0; i < 800; i++) {
            assertEquals(Integer.valueOf(i), t.get(i));
        }
    }

    @Test
    public void testLookupChecksStash() {
        StashedCuckooHashTable<Integer, Integer> t = new StashedCuckooHashTable<>(1000, 4, 4);
        for (int i = 0; i < 950; i++) {
            t.put(i, i);
        }
        for (int i = 0; i < 950; i++) {
            assertEquals("Key " + i + " not found", Integer.valueOf(i), t.get(i));
        }
    }
}
