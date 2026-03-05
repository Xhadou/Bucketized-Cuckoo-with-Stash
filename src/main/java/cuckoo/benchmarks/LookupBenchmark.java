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
public class LookupBenchmark {

    @Param({"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING"})
    public String tableType;

    @Param({"0.3", "0.6", "0.9"})
    public double targetLoadFactor;

    private CuckooHashTable<Integer, Integer> table;
    private int[] existingKeys;
    private int[] missingKeys;
    private static final int TABLE_SIZE = 100000;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42);
        int numToInsert;

        switch (tableType) {
            case "STANDARD":
                // Standard cuckoo can't handle high load, cap at ~40%
                numToInsert = (int)(TABLE_SIZE * Math.min(targetLoadFactor, 0.40));
                table = new StandardCuckooHashTable<>(TABLE_SIZE);
                break;
            case "BUCKETIZED_4":
                numToInsert = (int)(TABLE_SIZE * targetLoadFactor);
                table = new BucketizedCuckooHashTable<>(TABLE_SIZE, 4);
                break;
            case "STASHED_3":
                numToInsert = (int)(TABLE_SIZE * targetLoadFactor);
                table = new StashedCuckooHashTable<>(TABLE_SIZE, 4, 3);
                break;
            case "CHAINING":
                numToInsert = (int)(TABLE_SIZE * targetLoadFactor);
                table = new ChainingHashTable<>(TABLE_SIZE);
                break;
            case "LINEAR_PROBING":
                numToInsert = (int)(TABLE_SIZE * targetLoadFactor);
                table = new LinearProbingHashTable<>(TABLE_SIZE);
                break;
            default:
                throw new IllegalArgumentException("Unknown: " + tableType);
        }

        existingKeys = new int[numToInsert];
        for (int i = 0; i < numToInsert; i++) {
            existingKeys[i] = rng.nextInt();
            table.put(existingKeys[i], existingKeys[i]);
        }

        missingKeys = new int[numToInsert];
        for (int i = 0; i < numToInsert; i++) {
            missingKeys[i] = rng.nextInt() | 0x40000000; // Bias to different range
        }
    }

    @Benchmark
    public int positiveLookup() {
        int sum = 0;
        for (int key : existingKeys) {
            Integer val = table.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }

    @Benchmark
    public int negativeLookup() {
        int sum = 0;
        for (int key : missingKeys) {
            Integer val = table.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }
}
