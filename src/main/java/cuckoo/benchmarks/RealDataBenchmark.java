package cuckoo.benchmarks;

import cuckoo.core.*;
import cuckoo.baselines.*;
import cuckoo.util.WikipediaDataLoader;
import org.openjdk.jmh.annotations.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 10, time = 2)
@Fork(3)
@State(Scope.Benchmark)
public class RealDataBenchmark {

    @Param({"CHAINING", "LINEAR_PROBING", "QUADRATIC_PROBING", "STANDARD_CUCKOO", "BUCKETIZED_4",
             "STASHED_3", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"})
    public String tableType;

    private String[] keys;
    private String[] missingKeys;
    private CuckooHashTable<String, Integer> filledTable;

    @Setup(Level.Trial)
    public void setup() {
        try {
            String[] allTitles = WikipediaDataLoader.loadPageTitles("data/pageviews-sample.gz", 200000);
            keys = Arrays.copyOf(allTitles, Math.min(100000, allTitles.length));
            missingKeys = Arrays.copyOfRange(allTitles, keys.length,
                    Math.min(keys.length + 50000, allTitles.length));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Wikipedia data from data/pageviews-sample.gz. "
                    + "Download it first — see README.", e);
        }

        // Pre-fill a table for lookup benchmarks
        filledTable = createTable(keys.length);
        for (int i = 0; i < keys.length; i++) {
            filledTable.put(keys[i], i);
        }
    }

    private CuckooHashTable<String, Integer> createTable(int size) {
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
    public CuckooHashTable<String, Integer> insertAll() {
        CuckooHashTable<String, Integer> table = createTable(keys.length);
        for (int i = 0; i < keys.length; i++) {
            table.put(keys[i], i);
        }
        return table;
    }

    @Benchmark
    public int positiveLookup() {
        int sum = 0;
        for (String key : keys) {
            Integer val = filledTable.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }

    @Benchmark
    public int negativeLookup() {
        int sum = 0;
        for (String key : missingKeys) {
            Integer val = filledTable.get(key);
            if (val != null) sum += val;
        }
        return sum;
    }
}
