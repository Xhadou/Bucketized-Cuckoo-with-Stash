package cuckoo.benchmarks;

import cuckoo.core.*;
import cuckoo.baselines.*;
import org.openjdk.jmh.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Perfect (FKS) hashing is a static dictionary: all keys must be known at
 * construction time, and put/remove are unsupported. This benchmark compares
 * its lookup throughput against the dynamic variants on a fixed key set,
 * isolating the "zero-collision, O(1) worst-case lookup" guarantee against
 * schemes that tolerate collisions.
 *
 * Perfect hashing is INCLUDED here only; it is deliberately excluded from
 * InsertBenchmark, DeleteBenchmark, MixedWorkloadBenchmark, etc. because
 * those workloads mutate the key set.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
@State(Scope.Benchmark)
public class PerfectHashBenchmark {

    @Param({"PERFECT", "STANDARD", "BUCKETIZED_4", "LINEAR_PROBING",
            "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD"})
    public String tableType;

    @Param({"30000", "60000"})
    public int numElements;

    private CuckooHashTable<Integer, Integer> table;
    private int[] keys;
    private int[] missingKeys;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42);
        keys = new int[numElements];
        Map<Integer, Integer> entries = new HashMap<>();
        for (int i = 0; i < numElements; i++) {
            keys[i] = rng.nextInt();
            entries.put(keys[i], keys[i]);
        }
        missingKeys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            missingKeys[i] = rng.nextInt() | 0x40000000;
        }

        // STANDARD cuckoo has a ~50% load ceiling; use only 35% of keys to
        // avoid triggering rehash failures during setup.
        int safeN = tableType.equals("STANDARD") ? (int)(numElements * 0.35) : numElements;

        if (tableType.equals("PERFECT")) {
            Map<Integer, Integer> trimmed = new HashMap<>();
            for (int i = 0; i < safeN; i++) trimmed.put(keys[i], keys[i]);
            table = new PerfectHashTable<>(trimmed);
        } else {
            table = switch (tableType) {
                case "STANDARD"          -> new StandardCuckooHashTable<>(numElements);
                case "BUCKETIZED_4"      -> new BucketizedCuckooHashTable<>(numElements, 4);
                case "LINEAR_PROBING"    -> new LinearProbingHashTable<>(numElements);
                case "QUADRATIC_PROBING" -> new QuadraticProbingHashTable<>(numElements);
                case "HOPSCOTCH"         -> new HopscotchHashTable<>(numElements);
                case "ROBIN_HOOD"        -> new RobinHoodHashTable<>(numElements);
                default -> throw new IllegalArgumentException("Unknown: " + tableType);
            };
            for (int i = 0; i < safeN; i++) table.put(keys[i], keys[i]);
        }
    }

    @Benchmark
    public int positiveLookup() {
        int sum = 0;
        for (int key : keys) {
            Integer v = table.get(key);
            if (v != null) sum += v;
        }
        return sum;
    }

    @Benchmark
    public int negativeLookup() {
        int sum = 0;
        for (int key : missingKeys) {
            Integer v = table.get(key);
            if (v != null) sum += v;
        }
        return sum;
    }
}
