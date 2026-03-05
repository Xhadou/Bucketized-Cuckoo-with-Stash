package cuckoo;

import cuckoo.baselines.ChainingHashTable;
import cuckoo.baselines.LinearProbingHashTable;
import cuckoo.core.CuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class BaselinesTest {
    // --- ChainingHashTable tests ---
    @Test
    public void testChainingPutGet() {
        ChainingHashTable<String, Integer> t = new ChainingHashTable<>(100);
        t.put("a", 1);
        t.put("b", 2);
        assertEquals(Integer.valueOf(1), t.get("a"));
        assertEquals(Integer.valueOf(2), t.get("b"));
    }

    @Test
    public void testChainingUpdate() {
        ChainingHashTable<String, Integer> t = new ChainingHashTable<>(100);
        t.put("a", 1);
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testChainingRemove() {
        ChainingHashTable<String, Integer> t = new ChainingHashTable<>(100);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.remove("a"));
        assertNull(t.get("a"));
        assertEquals(0, t.size());
    }

    @Test
    public void testChainingBulk() {
        ChainingHashTable<Integer, String> t = new ChainingHashTable<>(16);
        for (int i = 0; i < 100000; i++) {
            t.put(i, "v" + i);
        }
        for (int i = 0; i < 100000; i++) {
            assertEquals("v" + i, t.get(i));
        }
        assertEquals(100000, t.size());
    }

    @Test
    public void testChainingMissReturnsNull() {
        ChainingHashTable<String, Integer> t = new ChainingHashTable<>(100);
        assertNull(t.get("missing"));
        assertNull(t.remove("missing"));
    }

    // --- LinearProbingHashTable tests ---
    @Test
    public void testLinearPutGet() {
        LinearProbingHashTable<String, Integer> t = new LinearProbingHashTable<>(100);
        t.put("a", 1);
        t.put("b", 2);
        assertEquals(Integer.valueOf(1), t.get("a"));
        assertEquals(Integer.valueOf(2), t.get("b"));
    }

    @Test
    public void testLinearUpdate() {
        LinearProbingHashTable<String, Integer> t = new LinearProbingHashTable<>(100);
        t.put("a", 1);
        t.put("a", 2);
        assertEquals(Integer.valueOf(2), t.get("a"));
        assertEquals(1, t.size());
    }

    @Test
    public void testLinearRemove() {
        LinearProbingHashTable<String, Integer> t = new LinearProbingHashTable<>(100);
        t.put("a", 1);
        assertEquals(Integer.valueOf(1), t.remove("a"));
        assertNull(t.get("a"));
        assertEquals(0, t.size());
    }

    @Test
    public void testLinearBulk() {
        LinearProbingHashTable<Integer, String> t = new LinearProbingHashTable<>(16);
        for (int i = 0; i < 100000; i++) {
            t.put(i, "v" + i);
        }
        for (int i = 0; i < 100000; i++) {
            assertEquals("v" + i, t.get(i));
        }
        assertEquals(100000, t.size());
    }

    @Test
    public void testLinearMissReturnsNull() {
        LinearProbingHashTable<String, Integer> t = new LinearProbingHashTable<>(100);
        assertNull(t.get("missing"));
        assertNull(t.remove("missing"));
    }

    // --- Both implement interface ---
    @Test
    public void testImplementsInterface() {
        CuckooHashTable<String, Integer> c = new ChainingHashTable<>(10);
        CuckooHashTable<String, Integer> l = new LinearProbingHashTable<>(10);
        c.put("x", 1);
        l.put("x", 1);
        assertEquals(Integer.valueOf(1), c.get("x"));
        assertEquals(Integer.valueOf(1), l.get("x"));
    }
}
