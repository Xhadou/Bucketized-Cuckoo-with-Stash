package cuckoo;

import cuckoo.core.StandardCuckooHashTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class StandardCuckooTest {
    @Test
    public void testPutAndGet() {
        StandardCuckooHashTable<String, Integer> table = new StandardCuckooHashTable<>(100);
        table.put("hello", 42);
        assertEquals(Integer.valueOf(42), table.get("hello"));
    }

    @Test
    public void testGetMissReturnsNull() {
        StandardCuckooHashTable<String, Integer> table = new StandardCuckooHashTable<>(100);
        assertNull(table.get("missing"));
    }

    @Test
    public void testUpdateExistingKey() {
        StandardCuckooHashTable<String, Integer> table = new StandardCuckooHashTable<>(100);
        table.put("key", 1);
        table.put("key", 2);
        assertEquals(Integer.valueOf(2), table.get("key"));
        assertEquals(1, table.size());
    }

    @Test
    public void testRemove() {
        StandardCuckooHashTable<String, Integer> table = new StandardCuckooHashTable<>(100);
        table.put("key", 1);
        assertEquals(Integer.valueOf(1), table.remove("key"));
        assertNull(table.get("key"));
        assertEquals(0, table.size());
    }

    @Test
    public void testRemoveMissReturnsNull() {
        StandardCuckooHashTable<String, Integer> table = new StandardCuckooHashTable<>(100);
        assertNull(table.remove("missing"));
    }

    @Test
    public void testBulkInsertAndLookup() {
        StandardCuckooHashTable<Integer, String> table = new StandardCuckooHashTable<>(10000);
        for (int i = 0; i < 4000; i++) {
            table.put(i, "val" + i);
        }
        for (int i = 0; i < 4000; i++) {
            assertEquals("val" + i, table.get(i));
        }
        assertEquals(4000, table.size());
    }

    @Test
    public void testLoadFactor() {
        StandardCuckooHashTable<Integer, Integer> table = new StandardCuckooHashTable<>(1000);
        for (int i = 0; i < 100; i++) {
            table.put(i, i);
        }
        assertTrue(table.loadFactor() > 0);
        assertTrue(table.loadFactor() < 1);
    }
}
