package cuckoo.baselines;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.*;

public class PerfectHashTableTest {

    @Test
    public void lookupAllKeysSucceeds() {
        Map<Integer, Integer> data = new HashMap<>();
        Random rng = new Random(1);
        for (int i = 0; i < 5000; i++) {
            data.put(rng.nextInt(), i);
        }
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(data);
        for (Map.Entry<Integer, Integer> e : data.entrySet()) {
            assertEquals(e.getValue(), table.get(e.getKey()));
        }
    }

    @Test
    public void missingKeysReturnNull() {
        Map<Integer, Integer> data = new HashMap<>();
        for (int i = 0; i < 100; i++) data.put(i, i * 2);
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(data);
        for (int miss = 1000; miss < 1050; miss++) {
            assertNull("unexpected hit for missing key " + miss, table.get(miss));
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void putIsRejected() {
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(Map.of(1, 1));
        table.put(2, 2);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeIsRejected() {
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(Map.of(1, 1));
        table.remove(1);
    }

    @Test
    public void emptyInputIsHandled() {
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(new HashMap<>());
        assertEquals(0, table.size());
        assertNull(table.get(42));
    }

    @Test
    public void sizeMatchesInput() {
        Map<Integer, Integer> data = new HashMap<>();
        for (int i = 0; i < 1234; i++) data.put(i, i);
        PerfectHashTable<Integer, Integer> table = new PerfectHashTable<>(data);
        assertEquals(1234, table.size());
    }

    @Test
    public void worksWithStringKeys() {
        Map<String, Integer> data = new HashMap<>();
        String[] words = {"apple", "banana", "cherry", "date", "elderberry"};
        for (int i = 0; i < words.length; i++) data.put(words[i], i);
        PerfectHashTable<String, Integer> table = new PerfectHashTable<>(data);
        for (int i = 0; i < words.length; i++) {
            assertEquals(Integer.valueOf(i), table.get(words[i]));
        }
        assertNull(table.get("fig"));
    }
}
