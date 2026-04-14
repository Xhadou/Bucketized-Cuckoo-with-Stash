package cuckoo.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class DAryHashTableTest {

    // ------------------------------------------------------------------
    //  Basic operations (d=2, comparable to standard cuckoo)
    // ------------------------------------------------------------------

    @Test
    public void testPutAndGet_d2() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 2);
        table.put("hello", 42);
        assertEquals(Integer.valueOf(42), table.get("hello"));
    }

    @Test
    public void testGetMissReturnsNull_d2() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 2);
        assertNull(table.get("missing"));
    }

    @Test
    public void testUpdateExistingKey_d2() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 2);
        table.put("key", 1);
        table.put("key", 2);
        assertEquals(Integer.valueOf(2), table.get("key"));
        assertEquals(1, table.size());
    }

    @Test
    public void testRemove_d2() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 2);
        table.put("key", 1);
        assertEquals(Integer.valueOf(1), table.remove("key"));
        assertNull(table.get("key"));
        assertEquals(0, table.size());
    }

    @Test
    public void testRemoveMissReturnsNull_d2() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 2);
        assertNull(table.remove("missing"));
    }

    // ------------------------------------------------------------------
    //  Bulk insert / lookup for d=2
    // ------------------------------------------------------------------

    @Test
    public void testBulkInsert_d2() {
        DAryHashTable<Integer, String> table = new DAryHashTable<>(5000, 2);
        for (int i = 0; i < 2000; i++) {
            table.put(i, "val" + i);
        }
        for (int i = 0; i < 2000; i++) {
            assertEquals("val" + i, table.get(i));
        }
        assertEquals(2000, table.size());
    }

    // ------------------------------------------------------------------
    //  d=3 — should achieve ~91% load factor
    // ------------------------------------------------------------------

    @Test
    public void testPutAndGet_d3() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 3);
        table.put("alpha", 1);
        table.put("beta", 2);
        assertEquals(Integer.valueOf(1), table.get("alpha"));
        assertEquals(Integer.valueOf(2), table.get("beta"));
    }

    @Test
    public void testBulkInsert_d3() {
        int n = 1000;
        DAryHashTable<Integer, String> table = new DAryHashTable<>(n, 3);
        for (int i = 0; i < n; i++) {
            table.put(i, "v" + i);
        }
        for (int i = 0; i < n; i++) {
            assertEquals("v" + i, table.get(i));
        }
        assertEquals(n, table.size());
    }

    @Test
    public void testHighLoadFactor_d3() {
        int n = 1000;
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(n, 3);
        for (int i = 0; i < n; i++) {
            table.put(i, i);
        }
        double lf = table.loadFactor();
        // d=3 with 1.15x overhead should reach at least 85% (theoretical ~91%)
        assertTrue("Expected load factor >= 0.80 but was " + lf, lf >= 0.80);
        assertTrue("Load factor must be <= 1.0 but was " + lf, lf <= 1.0);
    }

    // ------------------------------------------------------------------
    //  d=4 — should achieve ~97% load factor
    // ------------------------------------------------------------------

    @Test
    public void testPutAndGet_d4() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 4);
        table.put("x", 10);
        assertEquals(Integer.valueOf(10), table.get("x"));
    }

    @Test
    public void testBulkInsert_d4() {
        int n = 2000;
        DAryHashTable<Integer, String> table = new DAryHashTable<>(n, 4);
        for (int i = 0; i < n; i++) {
            table.put(i, "v" + i);
        }
        for (int i = 0; i < n; i++) {
            assertEquals("v" + i, table.get(i));
        }
        assertEquals(n, table.size());
    }

    @Test
    public void testHighLoadFactor_d4() {
        int n = 2000;
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(n, 4);
        for (int i = 0; i < n; i++) {
            table.put(i, i);
        }
        double lf = table.loadFactor();
        // d=4 with 1.15x overhead should reach at least 85% (theoretical ~97%)
        assertTrue("Expected load factor >= 0.80 but was " + lf, lf >= 0.80);
        assertTrue("Load factor must be <= 1.0 but was " + lf, lf <= 1.0);
    }

    // ------------------------------------------------------------------
    //  Negative lookups
    // ------------------------------------------------------------------

    @Test
    public void testNegativeLookups() {
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(500, 3);
        for (int i = 0; i < 200; i++) {
            table.put(i, i);
        }
        for (int i = 200; i < 400; i++) {
            assertNull("Key " + i + " should not be present", table.get(i));
        }
    }

    // ------------------------------------------------------------------
    //  Load factor calculation
    // ------------------------------------------------------------------

    @Test
    public void testLoadFactorCalculation() {
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(1000, 3);
        assertEquals(0.0, table.loadFactor(), 0.0001);

        for (int i = 0; i < 100; i++) {
            table.put(i, i);
        }
        double lf = table.loadFactor();
        assertTrue("Load factor should be positive after inserts", lf > 0);
        assertTrue("Load factor should be < 1", lf < 1);
    }

    // ------------------------------------------------------------------
    //  Remove then re-insert
    // ------------------------------------------------------------------

    @Test
    public void testRemoveAndReinsert() {
        DAryHashTable<String, Integer> table = new DAryHashTable<>(100, 3);
        table.put("a", 1);
        table.put("b", 2);
        table.remove("a");
        assertNull(table.get("a"));
        assertEquals(1, table.size());

        table.put("a", 99);
        assertEquals(Integer.valueOf(99), table.get("a"));
        assertEquals(2, table.size());
    }

    // ------------------------------------------------------------------
    //  Edge: d minimum value
    // ------------------------------------------------------------------

    @Test(expected = IllegalArgumentException.class)
    public void testDLessThan2Throws() {
        new DAryHashTable<>(100, 1);
    }

    // ------------------------------------------------------------------
    //  Large bulk test (stress)
    // ------------------------------------------------------------------

    @Test
    public void testLargeInsert_d3() {
        int n = 5000;
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(n, 3);
        for (int i = 0; i < n; i++) {
            table.put(i, i * 10);
        }
        assertEquals(n, table.size());
        for (int i = 0; i < n; i++) {
            assertEquals(Integer.valueOf(i * 10), table.get(i));
        }
    }

    @Test
    public void testLargeInsert_d4() {
        int n = 5000;
        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(n, 4);
        for (int i = 0; i < n; i++) {
            table.put(i, i * 10);
        }
        assertEquals(n, table.size());
        for (int i = 0; i < n; i++) {
            assertEquals(Integer.valueOf(i * 10), table.get(i));
        }
    }
}
