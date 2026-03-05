package cuckoo;

import cuckoo.core.*;
import cuckoo.baselines.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.*;

import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class CorrectnessTest {

    private final String name;
    private final CuckooHashTable<Integer, String> table;

    public CorrectnessTest(String name, CuckooHashTable<Integer, String> table) {
        this.name = name;
        this.table = table;
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> tables() {
        return Arrays.asList(new Object[][]{
            {"Standard", new StandardCuckooHashTable<Integer, String>(100000)},
            {"Bucketized_B4", new BucketizedCuckooHashTable<Integer, String>(100000, 4)},
            {"Stashed_B4_S3", new StashedCuckooHashTable<Integer, String>(100000, 4, 3)},
            {"Chaining", new ChainingHashTable<Integer, String>(100000)},
            {"LinearProbing", new LinearProbingHashTable<Integer, String>(100000)}
        });
    }

    @Test
    public void stressTestInsertAndLookup() {
        Random rng = new Random(42);
        Set<Integer> inserted = new HashSet<>();
        // Insert 100K random pairs
        for (int i = 0; i < 100000; i++) {
            int key = rng.nextInt(200000);
            table.put(key, "val" + key);
            inserted.add(key);
        }
        // All inserted keys must be found
        for (int key : inserted) {
            assertEquals(name + ": key " + key + " not found", "val" + key, table.get(key));
        }
        // Non-inserted keys return null
        for (int i = 200000; i < 200100; i++) {
            assertNull(name + ": key " + i + " should be null", table.get(i));
        }
    }

    @Test
    public void testDeleteCorrectness() {
        // Insert 10K
        for (int i = 0; i < 10000; i++) {
            table.put(i, "val" + i);
        }
        // Delete first 5K
        for (int i = 0; i < 5000; i++) {
            assertEquals(name + ": remove " + i, "val" + i, table.remove(i));
        }
        // Remaining 5K still found
        for (int i = 5000; i < 10000; i++) {
            assertEquals(name + ": key " + i, "val" + i, table.get(i));
        }
        // Deleted keys return null
        for (int i = 0; i < 5000; i++) {
            assertNull(name + ": deleted key " + i, table.get(i));
        }
        assertEquals(name + ": size after delete", 5000, table.size());
    }

    @Test
    public void testDuplicateKeyUpdate() {
        table.put(42, "first");
        table.put(42, "second");
        assertEquals(name + ": update", "second", table.get(42));
        assertEquals(name + ": size", 1, table.size());
    }

    @Test
    public void testRehashPreservesData() {
        // Fill to high load to trigger rehash (especially for Standard)
        int count = 50000;
        for (int i = 0; i < count; i++) {
            table.put(i, "val" + i);
        }
        // All keys must survive rehash
        for (int i = 0; i < count; i++) {
            assertEquals(name + ": key " + i + " after potential rehash", "val" + i, table.get(i));
        }
    }
}
