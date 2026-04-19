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
public class InsertBenchmark {

    @Param({"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING", "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"})
    public String tableType;

    @Param({"100000", "500000", "1000000"})
    public int numElements;

    private int[] keys;

    @Setup(Level.Trial)
    public void generateKeys() {
        Random rng = new Random(42);
        keys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            keys[i] = rng.nextInt();
        }
    }

    private CuckooHashTable<Integer, Integer> createTable() {
        switch (tableType) {
            case "STANDARD": return new StandardCuckooHashTable<>(numElements);
            case "BUCKETIZED_4": return new BucketizedCuckooHashTable<>(numElements, 4);
            case "STASHED_3": return new StashedCuckooHashTable<>(numElements, 4, 3);
            case "CHAINING": return new ChainingHashTable<>(numElements);
            case "LINEAR_PROBING": return new LinearProbingHashTable<>(numElements);
            case "QUADRATIC_PROBING": return new QuadraticProbingHashTable<>(numElements);
            case "HOPSCOTCH": return new HopscotchHashTable<>(numElements);
            case "ROBIN_HOOD": return new RobinHoodHashTable<>(numElements);
            case "D_ARY_3": return new DAryHashTable<>(numElements, 3);
            default: throw new IllegalArgumentException("Unknown type: " + tableType);
        }
    }

    @Benchmark
    public CuckooHashTable<Integer, Integer> insertAll() {
        CuckooHashTable<Integer, Integer> table = createTable();
        for (int key : keys) {
            table.put(key, key);
        }
        return table;
    }
}
