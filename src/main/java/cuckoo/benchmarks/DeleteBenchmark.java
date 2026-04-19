package cuckoo.benchmarks;

import cuckoo.core.*;
import cuckoo.baselines.*;
import org.openjdk.jmh.annotations.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
@State(Scope.Benchmark)
public class DeleteBenchmark {

    @Param({"CHAINING", "LINEAR_PROBING", "QUADRATIC_PROBING", "STANDARD_CUCKOO", "BUCKETIZED_4", "STASHED_3", "HOPSCOTCH", "ROBIN_HOOD"})
    public String tableType;

    @Param({"100000", "500000"})
    public int numElements;

    private int[] keys;
    private int[] extraKeys; // additional keys for reinsert after delete

    @Setup(Level.Trial)
    public void generateKeys() {
        Random rng = new Random(42);
        keys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            keys[i] = rng.nextInt();
        }
        // Extra keys that are distinct from the original keys (used for reinsertion)
        extraKeys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            extraKeys[i] = rng.nextInt();
        }
    }

    private CuckooHashTable<Integer, Integer> createTable() {
        switch (tableType) {
            case "STANDARD_CUCKOO": return new StandardCuckooHashTable<>(numElements);
            case "BUCKETIZED_4":    return new BucketizedCuckooHashTable<>(numElements, 4);
            case "STASHED_3":       return new StashedCuckooHashTable<>(numElements, 4, 3);
            case "CHAINING":        return new ChainingHashTable<>(numElements);
            case "LINEAR_PROBING":  return new LinearProbingHashTable<>(numElements);
            case "QUADRATIC_PROBING": return new QuadraticProbingHashTable<>(numElements);
            case "HOPSCOTCH":       return new HopscotchHashTable<>(numElements);
            case "ROBIN_HOOD":      return new RobinHoodHashTable<>(numElements);
            default: throw new IllegalArgumentException("Unknown type: " + tableType);
        }
    }

    /**
     * Insert N keys, then time deleting all N keys.
     */
    @Benchmark
    public CuckooHashTable<Integer, Integer> deleteAll() {
        CuckooHashTable<Integer, Integer> table = createTable();
        for (int key : keys) {
            table.put(key, key);
        }
        for (int key : keys) {
            table.remove(key);
        }
        return table;
    }

    /**
     * Pre-fill the table, then time a cycle of: delete key, insert new key.
     * Simulates cache eviction patterns.
     */
    @Benchmark
    public CuckooHashTable<Integer, Integer> deleteAndReinsert() {
        CuckooHashTable<Integer, Integer> table = createTable();
        // Pre-fill
        for (int key : keys) {
            table.put(key, key);
        }
        // Delete-then-reinsert cycle
        for (int i = 0; i < numElements; i++) {
            table.remove(keys[i]);
            table.put(extraKeys[i], extraKeys[i]);
        }
        return table;
    }
}
