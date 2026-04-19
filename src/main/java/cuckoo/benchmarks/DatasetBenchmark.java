package cuckoo.benchmarks;

import cuckoo.core.*;
import cuckoo.baselines.*;
import cuckoo.util.WorkloadGenerator;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
@State(Scope.Benchmark)
public class DatasetBenchmark {

    @Param({"CHAINING", "LINEAR_PROBING", "QUADRATIC_PROBING", "STANDARD_CUCKOO", "BUCKETIZED_4",
             "STASHED_3", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"})
    public String tableType;

    @Param({"UNIFORM", "SEQUENTIAL", "ZIPFIAN", "HOTSPOT"})
    public String distribution;

    private int[] keys;
    private CuckooHashTable<Integer, Integer> filledTable;

    @Setup(Level.Trial)
    public void generateKeys() {
        switch (distribution) {
            case "UNIFORM":
                keys = WorkloadGenerator.uniformRandom(100000, 42);
                break;
            case "SEQUENTIAL":
                keys = WorkloadGenerator.sequential(100000);
                break;
            case "ZIPFIAN":
                keys = WorkloadGenerator.zipfian(100000, 0.99, 42);
                break;
            case "HOTSPOT":
                keys = WorkloadGenerator.hotspot(100000, 0.2, 0.8, 42);
                break;
            default:
                throw new IllegalArgumentException("Unknown distribution: " + distribution);
        }

        // Pre-fill a table for lookup benchmarks
        filledTable = createTable(keys.length);
        for (int key : keys) {
            filledTable.put(key, key);
        }
    }

    private CuckooHashTable<Integer, Integer> createTable(int size) {
        switch (tableType) {
            case "CHAINING": return new ChainingHashTable<>(size);
            case "LINEAR_PROBING": return new LinearProbingHashTable<>(size);
            case "QUADRATIC_PROBING": return new QuadraticProbingHashTable<>(size);
            case "STANDARD_CUCKOO": return new StandardCuckooHashTable<>(size);
            case "BUCKETIZED_4": return new BucketizedCuckooHashTable<>(size, 4);
            case "STASHED_3": return new StashedCuckooHashTable<>(size, 4, 3);
            case "HOPSCOTCH": return new HopscotchHashTable<>(size);
            case "ROBIN_HOOD": return new RobinHoodHashTable<>(size);
            case "D_ARY_3": return new DAryHashTable<>(size, 3);
            default: throw new IllegalArgumentException("Unknown: " + tableType);
        }
    }

    @Benchmark
    public CuckooHashTable<Integer, Integer> insertAll() {
        CuckooHashTable<Integer, Integer> table = createTable(keys.length);
        for (int key : keys) {
            table.put(key, key);
        }
        return table;
    }

    @Benchmark
    public int lookupAll() {
        int sum = 0;
        for (int key : keys) {
            Integer val = filledTable.get(key);
            if (val != null) {
                sum += val.hashCode();
            }
        }
        return sum;
    }
}
