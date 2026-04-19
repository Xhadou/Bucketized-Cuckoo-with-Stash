package cuckoo.analysis;

import cuckoo.core.*;
import cuckoo.baselines.*;
import cuckoo.hash.HashFamily;
import cuckoo.hash.HashFunctions;

import java.io.*;
import java.util.*;

/**
 * Direct analysis program that measures algorithmic properties
 * by stress-testing hash tables beyond their designed comfort zone.
 * Outputs CSV files for chart generation.
 */
public class DirectAnalysis {

    private static final int TRIALS = 5;

    public static void main(String[] args) throws Exception {
        String resultsDir = "results/csv";
        new File(resultsDir).mkdirs();

        System.out.println("=== Direct Analysis ===\n");

        analyzeLoadFactorVsBucketSize(resultsDir);
        analyzeRehashVsStashSize(resultsDir);
        analyzeDisplacementChains(resultsDir);
        analyzeThroughput(resultsDir);
        analyzeDAryLoadFactors(resultsDir);
        analyzeHashFunctionSensitivity(resultsDir);
        analyzeDeleteThroughput(resultsDir);
        analyzePerfectHashLookup(resultsDir);

        System.out.println("\nAll analysis complete. CSVs written to " + resultsDir + "/");
    }

    /**
     * Chart 3: Load factor ceiling vs bucket size (B=1,2,4,8).
     * Uses small expectedSize but inserts far more elements to push past
     * the designed load factor and find the true ceiling.
     */
    static void analyzeLoadFactorVsBucketSize(String dir) throws Exception {
        System.out.println("--- Load Factor vs Bucket Size ---");
        // Use a small expected size so the table is tight, then insert
        // way more than expected to find the ceiling before rehash/failure.
        int expectedSize = 5000;
        int maxInserts = 50000; // 10x expected — will trigger rehashes

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/load_factor_vs_bucket.csv"))) {
            pw.println("bucket_size,trial,max_load_factor,elements_at_max,rehashes");
            for (int B : new int[]{1, 2, 4, 8}) {
                for (int t = 0; t < TRIALS; t++) {
                    BucketizedCuckooHashTable<Integer, Integer> table =
                        new BucketizedCuckooHashTable<>(expectedSize, B);
                    double maxLoad = 0;
                    int elementsAtMax = 0;
                    // Use different keys per trial
                    Random rng = new Random(t * 1000 + B);
                    for (int i = 0; i < maxInserts; i++) {
                        try {
                            table.put(rng.nextInt(), i);
                            double lf = table.loadFactor();
                            if (lf > maxLoad) {
                                maxLoad = lf;
                                elementsAtMax = table.size();
                            }
                        } catch (Exception e) {
                            break;
                        }
                    }
                    int rehashes = table.getStats().getRehashCount();
                    pw.printf("%d,%d,%.6f,%d,%d%n", B, t, maxLoad, elementsAtMax, rehashes);
                    System.out.printf("  B=%d trial=%d: max_load=%.4f (%d elements, %d rehashes)%n",
                        B, t, maxLoad, elementsAtMax, rehashes);
                }
            }
        }
        System.out.println();
    }

    /**
     * Chart 4: Rehash count vs stash size (s=0,1,2,3,4).
     * Uses tight table sizing and inserts many elements to force rehashes.
     * Runs multiple rounds of 10K insertions each and accumulates rehash counts.
     */
    static void analyzeRehashVsStashSize(String dir) throws Exception {
        System.out.println("--- Rehash Count vs Stash Size ---");
        // Tight sizing: expectedSize=5000 but insert 20000 to stress
        int expectedSize = 5000;
        int insertCount = 20000;

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/rehash_vs_stash.csv"))) {
            pw.println("stash_size,trial,rehash_count,elements_inserted");

            // s=0: BucketizedCuckoo B=4 (no stash)
            for (int t = 0; t < TRIALS; t++) {
                BucketizedCuckooHashTable<Integer, Integer> table =
                    new BucketizedCuckooHashTable<>(expectedSize, 4);
                Random rng = new Random(t * 7 + 13);
                int inserted = 0;
                for (int i = 0; i < insertCount; i++) {
                    try {
                        table.put(rng.nextInt(), i);
                        inserted++;
                    } catch (Exception e) {
                        break;
                    }
                }
                int rehashCount = table.getStats().getRehashCount();
                pw.printf("%d,%d,%d,%d%n", 0, t, rehashCount, inserted);
                System.out.printf("  s=0 trial=%d: rehashes=%d, inserted=%d, load=%.4f%n",
                    t, rehashCount, inserted, table.loadFactor());
            }

            // s=1..4: StashedCuckoo B=4
            for (int s = 1; s <= 4; s++) {
                for (int t = 0; t < TRIALS; t++) {
                    StashedCuckooHashTable<Integer, Integer> table =
                        new StashedCuckooHashTable<>(expectedSize, 4, s);
                    Random rng = new Random(t * 7 + 13);
                    int inserted = 0;
                    for (int i = 0; i < insertCount; i++) {
                        try {
                            table.put(rng.nextInt(), i);
                            inserted++;
                        } catch (Exception e) {
                            break;
                        }
                    }
                    int rehashCount = table.getStats().getRehashCount();
                    int stashIns = table.getStats().getStashInsertions();
                    pw.printf("%d,%d,%d,%d%n", s, t, rehashCount, inserted);
                    System.out.printf("  s=%d trial=%d: rehashes=%d, stash_insertions=%d, inserted=%d, load=%.4f%n",
                        s, t, rehashCount, stashIns, inserted, table.loadFactor());
                }
            }
        }
        System.out.println();
    }

    /**
     * Chart 5: Displacement chain length distribution.
     * Uses tight sizing to force more displacement chains.
     */
    static void analyzeDisplacementChains(String dir) throws Exception {
        System.out.println("--- Displacement Chain Lengths ---");
        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/displacement_chains.csv"))) {
            pw.println("table_type,chain_length,count");

            // Standard cuckoo — tight sizing (expected=5000, insert ~4500 to get ~40% load)
            StandardCuckooHashTable<Integer, Integer> standard =
                new StandardCuckooHashTable<>(5000);
            for (int i = 0; i < 4500; i++) {
                standard.put(i, i);
            }
            List<Integer> stdChains = standard.getStats().getDisplacementChainLengths();
            // Filter to only non-zero chains for interesting histogram
            Map<Integer, Integer> stdHist = buildHistogram(stdChains);
            for (var e : stdHist.entrySet()) {
                pw.printf("Standard,%d,%d%n", e.getKey(), e.getValue());
            }
            long stdNonZero = stdChains.stream().filter(x -> x > 0).count();
            System.out.printf("  Standard: %d total chains, %d non-zero, avg=%.2f, max=%d%n",
                stdChains.size(), stdNonZero,
                standard.getStats().getAverageDisplacementChainLength(),
                stdChains.stream().mapToInt(x -> x).max().orElse(0));

            // Bucketized B=4 — tight sizing (expected=5000, insert ~9000 to get ~90% load)
            BucketizedCuckooHashTable<Integer, Integer> bucketized =
                new BucketizedCuckooHashTable<>(5000, 4);
            for (int i = 0; i < 4500; i++) {
                bucketized.put(i, i);
            }
            List<Integer> buckChains = bucketized.getStats().getDisplacementChainLengths();
            Map<Integer, Integer> buckHist = buildHistogram(buckChains);
            for (var e : buckHist.entrySet()) {
                pw.printf("Bucketized_B4,%d,%d%n", e.getKey(), e.getValue());
            }
            long buckNonZero = buckChains.stream().filter(x -> x > 0).count();
            System.out.printf("  Bucketized_B4: %d total chains, %d non-zero, avg=%.2f, max=%d%n",
                buckChains.size(), buckNonZero,
                bucketized.getStats().getAverageDisplacementChainLength(),
                buckChains.stream().mapToInt(x -> x).max().orElse(0));
        }
        System.out.println();
    }

    /**
     * Charts 1, 2, 6: Throughput measurements with warmup.
     */
    static void analyzeThroughput(String dir) throws Exception {
        System.out.println("--- Throughput Analysis ---");
        int N = 500_000;
        Random rng = new Random(42);
        int[] keys = new int[N];
        for (int i = 0; i < N; i++) keys[i] = rng.nextInt();

        String[] types = {"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING",
                          "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"};
        int warmupTrials = 2;
        int measureTrials = 5;

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/throughput.csv"))) {
            pw.println("table_type,operation,ops_per_sec");

            for (String type : types) {
                // --- Insert throughput ---
                // Warmup
                for (int w = 0; w < warmupTrials; w++) {
                    CuckooHashTable<Integer, Integer> t = createTable(type, N);
                    for (int i = 0; i < N; i++) t.put(keys[i], keys[i]);
                }
                // Measure
                double insertOps = 0;
                for (int trial = 0; trial < measureTrials; trial++) {
                    CuckooHashTable<Integer, Integer> table = createTable(type, N);
                    long start = System.nanoTime();
                    for (int i = 0; i < N; i++) table.put(keys[i], keys[i]);
                    long elapsed = System.nanoTime() - start;
                    insertOps += (double) N / (elapsed / 1e9);
                }
                insertOps /= measureTrials;
                pw.printf("%s,insert,%.0f%n", type, insertOps);
                System.out.printf("  %s insert: %,.0f ops/sec%n", type, insertOps);

                // --- Positive lookup ---
                CuckooHashTable<Integer, Integer> loaded = createTable(type, N);
                int loadCount = type.equals("STANDARD") ? (int)(N * 0.35) : (int)(N * 0.6);
                for (int i = 0; i < loadCount; i++) loaded.put(keys[i], keys[i]);

                // Warmup lookups
                for (int w = 0; w < warmupTrials; w++) {
                    int sum = 0;
                    for (int i = 0; i < loadCount; i++) {
                        Integer v = loaded.get(keys[i]);
                        if (v != null) sum += v;
                    }
                    if (sum == Integer.MIN_VALUE) System.out.print("");
                }

                double posLookupOps = 0;
                for (int trial = 0; trial < measureTrials; trial++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (int i = 0; i < loadCount; i++) {
                        Integer v = loaded.get(keys[i]);
                        if (v != null) sum += v;
                    }
                    long elapsed = System.nanoTime() - start;
                    posLookupOps += (double) loadCount / (elapsed / 1e9);
                    if (sum == Integer.MIN_VALUE) System.out.print("");
                }
                posLookupOps /= measureTrials;
                pw.printf("%s,positive_lookup,%.0f%n", type, posLookupOps);
                System.out.printf("  %s positive lookup: %,.0f ops/sec%n", type, posLookupOps);

                // --- Negative lookup ---
                int[] missingKeys = new int[loadCount];
                Random rng2 = new Random(999);
                for (int i = 0; i < loadCount; i++) missingKeys[i] = rng2.nextInt() | 0x70000000;

                double negLookupOps = 0;
                for (int trial = 0; trial < measureTrials; trial++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (int i = 0; i < loadCount; i++) {
                        Integer v = loaded.get(missingKeys[i]);
                        if (v != null) sum += v;
                    }
                    long elapsed = System.nanoTime() - start;
                    negLookupOps += (double) loadCount / (elapsed / 1e9);
                    if (sum == Integer.MIN_VALUE) System.out.print("");
                }
                negLookupOps /= measureTrials;
                pw.printf("%s,negative_lookup,%.0f%n", type, negLookupOps);
                System.out.printf("  %s negative lookup: %,.0f ops/sec%n", type, negLookupOps);

                // --- Mixed workload ---
                CuckooHashTable<Integer, Integer> mixTable = createTable(type, N);
                int preload = (int)(N * 0.3);
                for (int i = 0; i < preload; i++) mixTable.put(i, i);

                int mixOpsCount = 200_000;
                double mixOps = 0;
                for (int trial = 0; trial < measureTrials; trial++) {
                    Random mixRng = new Random(trial);
                    long start = System.nanoTime();
                    int sum = 0;
                    int nextKey = preload + trial * mixOpsCount;
                    for (int i = 0; i < mixOpsCount; i++) {
                        if (mixRng.nextDouble() < 0.8) {
                            Integer v = mixTable.get(mixRng.nextInt(preload));
                            if (v != null) sum += v;
                        } else {
                            mixTable.put(nextKey, nextKey); nextKey++;
                        }
                    }
                    long elapsed = System.nanoTime() - start;
                    mixOps += (double) mixOpsCount / (elapsed / 1e9);
                    if (sum == Integer.MIN_VALUE) System.out.print("");
                }
                mixOps /= measureTrials;
                pw.printf("%s,mixed,%.0f%n", type, mixOps);
                System.out.printf("  %s mixed: %,.0f ops/sec%n", type, mixOps);
                System.out.println();
            }
        }
    }

    private static CuckooHashTable<Integer, Integer> createTable(String type, int size) {
        switch (type) {
            case "STANDARD": return new StandardCuckooHashTable<>(size);
            case "BUCKETIZED_4": return new BucketizedCuckooHashTable<>(size, 4);
            case "STASHED_3": return new StashedCuckooHashTable<>(size, 4, 3);
            case "CHAINING": return new ChainingHashTable<>(size);
            case "LINEAR_PROBING": return new LinearProbingHashTable<>(size);
            case "QUADRATIC_PROBING": return new QuadraticProbingHashTable<>(size);
            case "HOPSCOTCH": return new HopscotchHashTable<>(size);
            case "ROBIN_HOOD": return new RobinHoodHashTable<>(size);
            case "D_ARY_3": return new DAryHashTable<>(size, 3);
            default: throw new IllegalArgumentException(type);
        }
    }

    /**
     * d-ary load factor ceiling: how high can d=2,3,4 pack elements
     * before insertion fails, across several table sizes?
     */
    static void analyzeDAryLoadFactors(String dir) throws Exception {
        System.out.println("--- d-ary Load Factor Ceiling ---");
        int[] dValues = {2, 3, 4};
        int[] sizes = {10000, 50000, 100000};

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/dary_load_factors.csv"))) {
            pw.println("d,table_size,trial,max_load_factor,elements_inserted");
            for (int d : dValues) {
                for (int tableSize : sizes) {
                    for (int t = 0; t < TRIALS; t++) {
                        DAryHashTable<Integer, Integer> table = new DAryHashTable<>(tableSize, d);
                        Random rng = new Random(t * 31 + d * 7 + tableSize);
                        double maxLoad = 0;
                        int inserted = 0;
                        for (int i = 0; i < tableSize * 2; i++) {
                            try {
                                table.put(rng.nextInt(), i);
                                inserted++;
                                double lf = table.loadFactor();
                                if (lf > maxLoad) maxLoad = lf;
                            } catch (Exception e) {
                                break;
                            }
                        }
                        pw.printf("%d,%d,%d,%.6f,%d%n", d, tableSize, t, maxLoad, inserted);
                        System.out.printf("  d=%d size=%d trial=%d: max_load=%.4f, inserted=%d%n",
                            d, tableSize, t, maxLoad, inserted);
                    }
                }
            }
        }
        System.out.println();
    }

    /**
     * Hash function sensitivity: compare MurmurHash3, xxHash, FNV1a
     * on BucketizedCuckoo(B=4) with 50k insertions.
     */
    static void analyzeHashFunctionSensitivity(String dir) throws Exception {
        System.out.println("--- Hash Function Sensitivity ---");
        int insertCount = 50000;
        int expectedSize = 50000;

        String[] hashNames = {"MurmurHash3", "xxHash32", "FNV1a", "Universal"};
        HashFamily[] families = {HashFunctions.murmur3(), HashFunctions.xxhash32(),
                                 HashFunctions.fnv1a(), HashFunctions.universal()};

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/hash_sensitivity.csv"))) {
            pw.println("hash_function,trial,avg_displacement_chain,rehash_count,max_load_factor");
            for (int h = 0; h < hashNames.length; h++) {
                for (int t = 0; t < TRIALS; t++) {
                    BucketizedCuckooHashTable<Integer, Integer> table =
                        new BucketizedCuckooHashTable<>(expectedSize, 4, families[h]);
                    Random rng = new Random(t * 17 + h);
                    double maxLoad = 0;
                    for (int i = 0; i < insertCount; i++) {
                        try {
                            table.put(rng.nextInt(), i);
                            double lf = table.loadFactor();
                            if (lf > maxLoad) maxLoad = lf;
                        } catch (Exception e) {
                            break;
                        }
                    }
                    double avgChain = table.getStats().getAverageDisplacementChainLength();
                    int rehashes = table.getStats().getRehashCount();
                    pw.printf("%s,%d,%.4f,%d,%.6f%n", hashNames[h], t, avgChain, rehashes, maxLoad);
                    System.out.printf("  %s trial=%d: avg_chain=%.4f, rehashes=%d, max_load=%.4f%n",
                        hashNames[h], t, avgChain, rehashes, maxLoad);
                }
            }
        }
        System.out.println();
    }

    /**
     * Delete throughput: insert 50k keys then time deleting all of them,
     * for every variant.
     */
    static void analyzeDeleteThroughput(String dir) throws Exception {
        System.out.println("--- Delete Throughput ---");
        int N = 50000;
        String[] types = {"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING",
                          "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD", "D_ARY_3"};
        int warmupTrials = 2;
        int measureTrials = 5;

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/delete_throughput.csv"))) {
            pw.println("table_type,delete_ops_per_sec");
            for (String type : types) {
                Random rng = new Random(42);
                int[] keys = new int[N];
                for (int i = 0; i < N; i++) keys[i] = rng.nextInt();

                // Determine safe insert count — standard cuckoo can't handle high load
                int insertCount = type.equals("STANDARD") ? (int)(N * 0.35) : N;

                // Warmup
                for (int w = 0; w < warmupTrials; w++) {
                    CuckooHashTable<Integer, Integer> t = createTable(type, N);
                    for (int i = 0; i < insertCount; i++) t.put(keys[i], keys[i]);
                    for (int i = 0; i < insertCount; i++) t.remove(keys[i]);
                }

                // Measure
                double deleteOps = 0;
                for (int trial = 0; trial < measureTrials; trial++) {
                    CuckooHashTable<Integer, Integer> table = createTable(type, N);
                    for (int i = 0; i < insertCount; i++) table.put(keys[i], keys[i]);

                    long start = System.nanoTime();
                    for (int i = 0; i < insertCount; i++) table.remove(keys[i]);
                    long elapsed = System.nanoTime() - start;
                    deleteOps += (double) insertCount / (elapsed / 1e9);
                }
                deleteOps /= measureTrials;
                pw.printf("%s,%.0f%n", type, deleteOps);
                System.out.printf("  %s delete: %,.0f ops/sec%n", type, deleteOps);
            }
        }
        System.out.println();
    }

    /**
     * Perfect hashing comparison: measure lookup throughput for FKS perfect
     * hashing vs top dynamic variants on a fixed key set. Perfect hashing
     * is static (no put/remove), so only positive lookup is measured.
     */
    static void analyzePerfectHashLookup(String dir) throws Exception {
        System.out.println("--- Perfect Hashing vs Dynamic (Lookup) ---");
        int N = 50_000;
        int warmupTrials = 2;
        int measureTrials = 5;

        Random rng = new Random(42);
        int[] keys = new int[N];
        for (int i = 0; i < N; i++) keys[i] = rng.nextInt();

        Map<Integer, Integer> entryMap = new HashMap<>();
        for (int k : keys) entryMap.put(k, k);

        // Variant builders. Perfect is built from the entry map; the rest
        // are filled by insertion so they reach their natural load factors.
        String[] types = {"PERFECT", "STANDARD", "BUCKETIZED_4", "LINEAR_PROBING",
                          "QUADRATIC_PROBING", "HOPSCOTCH", "ROBIN_HOOD"};

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/perfect_hash_lookup.csv"))) {
            pw.println("table_type,lookup_ops_per_sec,construction_ms");
            for (String type : types) {
                int safeN = type.equals("STANDARD") ? (int)(N * 0.35) : N;

                // Construction (measured once)
                long cStart = System.nanoTime();
                CuckooHashTable<Integer, Integer> table;
                if (type.equals("PERFECT")) {
                    table = new PerfectHashTable<>(entryMap);
                } else {
                    table = createTable(type, N);
                    for (int i = 0; i < safeN; i++) table.put(keys[i], keys[i]);
                }
                long cMs = (System.nanoTime() - cStart) / 1_000_000;

                // Warmup
                for (int w = 0; w < warmupTrials; w++) {
                    int sum = 0;
                    for (int i = 0; i < safeN; i++) {
                        Integer v = table.get(keys[i]);
                        if (v != null) sum += v;
                    }
                    if (sum == 0xDEADBEEF) System.out.print("");
                }

                // Measure
                double totalOps = 0;
                for (int t = 0; t < measureTrials; t++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (int i = 0; i < safeN; i++) {
                        Integer v = table.get(keys[i]);
                        if (v != null) sum += v;
                    }
                    long elapsed = System.nanoTime() - start;
                    totalOps += safeN / (elapsed / 1e9);
                    if (sum == 0xDEADBEEF) System.out.print("");
                }
                double opsPerSec = totalOps / measureTrials;
                pw.printf("%s,%.0f,%d%n", type, opsPerSec, cMs);
                System.out.printf("  %s lookup: %,.0f ops/sec  (build: %d ms)%n",
                                  type, opsPerSec, cMs);
            }
        }
        System.out.println();
    }

    private static Map<Integer, Integer> buildHistogram(List<Integer> values) {
        TreeMap<Integer, Integer> hist = new TreeMap<>();
        for (int v : values) {
            hist.merge(v, 1, Integer::sum);
        }
        return hist;
    }
}
