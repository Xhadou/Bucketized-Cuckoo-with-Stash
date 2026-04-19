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

    @Param({"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING", "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"})
    public String tableType;

    // Number of elements inserted into every variant. Using element count
    // instead of "target load factor" because variants have different load
    // factor ceilings (standard cuckoo caps at ~50%, others go to 95%+) — a
    // fair comparison requires equal data size, not equal fill ratio.
    @Param({"30000", "60000", "90000"})
    public int numElements;

    private CuckooHashTable<Integer, Integer> table;
    private int[] existingKeys;
    private int[] missingKeys;

    @Setup(Level.Trial)
    public void setup() {
        Random rng = new Random(42);

        // Size each table so it can actually hold numElements given its load
        // factor ceiling. This gives every variant a fair chance to store the
        // full data set, then we measure lookup performance at the actual
        // load factor each variant naturally reaches.
        table = switch (tableType) {
            case "STANDARD"          -> new StandardCuckooHashTable<>(numElements);
            case "BUCKETIZED_4"      -> new BucketizedCuckooHashTable<>(numElements, 4);
            case "STASHED_3"         -> new StashedCuckooHashTable<>(numElements, 4, 3);
            case "CHAINING"          -> new ChainingHashTable<>(numElements);
            case "LINEAR_PROBING"    -> new LinearProbingHashTable<>(numElements);
            case "QUADRATIC_PROBING" -> new QuadraticProbingHashTable<>(numElements);
            case "HOPSCOTCH"         -> new HopscotchHashTable<>(numElements);
            case "ROBIN_HOOD"        -> new RobinHoodHashTable<>(numElements);
            case "D_ARY_3"           -> new DAryHashTable<>(numElements, 3);
            default -> throw new IllegalArgumentException("Unknown: " + tableType);
        };

        existingKeys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            existingKeys[i] = rng.nextInt();
            table.put(existingKeys[i], existingKeys[i]);
        }

        missingKeys = new int[numElements];
        for (int i = 0; i < numElements; i++) {
            missingKeys[i] = rng.nextInt() | 0x40000000;
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
