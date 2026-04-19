package cuckoo.benchmarks;

import cuckoo.core.*;
import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;
import org.openjdk.jmh.annotations.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
@State(Scope.Benchmark)
public class HashFunctionBenchmark {

    @Param({"MURMUR3", "XXHASH", "FNV1A", "UNIVERSAL"})
    public String hashFunction;

    @Param({"100000", "500000"})
    public int numElements;

    private int[] keys;
    private int[] missingKeys;
    private CuckooHashTable<Integer, Integer> prefilledTable;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42);
        keys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            keys[i] = rng.nextInt();
        }
        // Keys guaranteed to differ from inserted keys (used for negative lookups)
        missingKeys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            missingKeys[i] = rng.nextInt();
        }

        // Pre-fill a table for lookup benchmarks
        prefilledTable = createTable();
        for (int key : keys) {
            prefilledTable.put(key, key);
        }
    }

    private HashFamily resolveHashFamily() {
        switch (hashFunction) {
            case "MURMUR3": return HashFunctions.murmur3();
            case "XXHASH":  return HashFunctions.xxhash32();
            case "FNV1A":   return HashFunctions.fnv1a();
            case "UNIVERSAL": return HashFunctions.universal();
            default: throw new IllegalArgumentException("Unknown hash function: " + hashFunction);
        }
    }

    private CuckooHashTable<Integer, Integer> createTable() {
        return new BucketizedCuckooHashTable<>(numElements, 4, resolveHashFamily());
    }

    /**
     * Time inserting all keys into a fresh table.
     */
    @Benchmark
    public CuckooHashTable<Integer, Integer> insertAll() {
        CuckooHashTable<Integer, Integer> table = createTable();
        for (int key : keys) {
            table.put(key, key);
        }
        return table;
    }

    /**
     * Time looking up all existing keys (positive lookups — all should hit).
     */
    @Benchmark
    public int positiveLookup() {
        int sum = 0;
        for (int key : keys) {
            Integer val = prefilledTable.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }

    /**
     * Time looking up keys that don't exist in the table (negative lookups — all should miss).
     */
    @Benchmark
    public int negativeLookup() {
        int sum = 0;
        for (int key : missingKeys) {
            Integer val = prefilledTable.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }
}
