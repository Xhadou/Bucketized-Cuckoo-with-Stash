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
public class MixedWorkloadBenchmark {

    @Param({"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING"})
    public String tableType;

    private CuckooHashTable<Integer, Integer> table;
    private int[] operations; // encoded: positive = lookup existing, negative = insert new
    private static final int TABLE_SIZE = 100000;
    private static final int OPS = 100000;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42);
        int preload = (int)(TABLE_SIZE * 0.5); // 50% pre-loaded

        switch (tableType) {
            case "STANDARD": table = new StandardCuckooHashTable<>(TABLE_SIZE); break;
            case "BUCKETIZED_4": table = new BucketizedCuckooHashTable<>(TABLE_SIZE, 4); break;
            case "STASHED_3": table = new StashedCuckooHashTable<>(TABLE_SIZE, 4, 3); break;
            case "CHAINING": table = new ChainingHashTable<>(TABLE_SIZE); break;
            case "LINEAR_PROBING": table = new LinearProbingHashTable<>(TABLE_SIZE); break;
            default: throw new IllegalArgumentException();
        }

        // Pre-load 50%
        int[] preloadKeys = new int[preload];
        for (int i = 0; i < preload; i++) {
            preloadKeys[i] = i;
            table.put(i, i);
        }

        // Generate mixed operations: 80% reads, 20% writes
        operations = new int[OPS];
        int nextInsert = preload;
        for (int i = 0; i < OPS; i++) {
            if (rng.nextDouble() < 0.8) {
                // Read: 50% hit, 50% miss
                if (rng.nextBoolean()) {
                    operations[i] = rng.nextInt(preload); // existing key
                } else {
                    operations[i] = -(TABLE_SIZE + rng.nextInt(TABLE_SIZE)); // missing key (negative)
                }
            } else {
                // Write: insert new key
                operations[i] = nextInsert++;
            }
        }
    }

    @Benchmark
    public int mixedWorkload() {
        int sum = 0;
        for (int op : operations) {
            if (op >= 0 && op < TABLE_SIZE) {
                Integer val = table.get(op);
                if (val != null) sum += val;
            } else if (op < 0) {
                Integer val = table.get(-op);
                if (val != null) sum += val;
            } else {
                table.put(op, op);
            }
        }
        return sum;
    }
}
