package cuckoo.analysis;

import cuckoo.core.*;
import cuckoo.baselines.*;
import cuckoo.hash.MurmurHash3;

import java.io.*;
import java.util.*;

/**
 * Direct analysis program that measures algorithmic properties:
 * - Max load factor vs bucket size
 * - Rehash count vs stash size
 * - Displacement chain length distributions
 * - Insert/lookup throughput (simple timing)
 *
 * Outputs CSV files for chart generation.
 */
public class DirectAnalysis {

    private static final int TABLE_SIZE = 100_000;
    private static final int TRIALS = 5;

    public static void main(String[] args) throws Exception {
        String resultsDir = "results/csv";
        new File(resultsDir).mkdirs();

        System.out.println("=== Direct Analysis ===\n");

        analyzeLoadFactorVsBucketSize(resultsDir);
        analyzeRehashVsStashSize(resultsDir);
        analyzeDisplacementChains(resultsDir);
        analyzeThroughput(resultsDir);

        System.out.println("\nAll analysis complete. CSVs written to " + resultsDir + "/");
    }

    /**
     * Chart 3: Load factor ceiling vs bucket size (B=1,2,4,8)
     */
    static void analyzeLoadFactorVsBucketSize(String dir) throws Exception {
        System.out.println("--- Load Factor vs Bucket Size ---");
        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/load_factor_vs_bucket.csv"))) {
            pw.println("bucket_size,trial,max_load_factor");
            for (int B : new int[]{1, 2, 4, 8}) {
                for (int t = 0; t < TRIALS; t++) {
                    BucketizedCuckooHashTable<Integer, Integer> table =
                        new BucketizedCuckooHashTable<>(TABLE_SIZE, B);
                    double maxLoad = 0;
                    for (int i = 0; i < TABLE_SIZE; i++) {
                        try {
                            table.put(i, i);
                            maxLoad = Math.max(maxLoad, table.loadFactor());
                        } catch (Exception e) {
                            break;
                        }
                    }
                    pw.printf("%d,%d,%.6f%n", B, t, maxLoad);
                    System.out.printf("  B=%d trial=%d: max_load=%.4f (%d elements)%n",
                        B, t, maxLoad, table.size());
                }
            }
        }
        System.out.println();
    }

    /**
     * Chart 4: Rehash count vs stash size (s=0,1,2,3,4)
     */
    static void analyzeRehashVsStashSize(String dir) throws Exception {
        System.out.println("--- Rehash Count vs Stash Size ---");
        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/rehash_vs_stash.csv"))) {
            pw.println("stash_size,trial,rehash_count,elements_inserted");

            // First: no stash (BucketizedCuckoo with B=4)
            for (int t = 0; t < TRIALS; t++) {
                BucketizedCuckooHashTable<Integer, Integer> table =
                    new BucketizedCuckooHashTable<>(TABLE_SIZE, 4);
                int inserted = 0;
                for (int i = 0; i < TABLE_SIZE; i++) {
                    try {
                        table.put(i, i);
                        inserted++;
                    } catch (Exception e) {
                        break;
                    }
                }
                int rehashCount = table.getStats().getRehashCount();
                pw.printf("%d,%d,%d,%d%n", 0, t, rehashCount, inserted);
                System.out.printf("  s=0 trial=%d: rehashes=%d, inserted=%d%n",
                    t, rehashCount, inserted);
            }

            // Stash sizes 1-4
            for (int s = 1; s <= 4; s++) {
                for (int t = 0; t < TRIALS; t++) {
                    StashedCuckooHashTable<Integer, Integer> table =
                        new StashedCuckooHashTable<>(TABLE_SIZE, 4, s);
                    int inserted = 0;
                    for (int i = 0; i < TABLE_SIZE; i++) {
                        try {
                            table.put(i, i);
                            inserted++;
                        } catch (Exception e) {
                            break;
                        }
                    }
                    int rehashCount = table.getStats().getRehashCount();
                    pw.printf("%d,%d,%d,%d%n", s, t, rehashCount, inserted);
                    System.out.printf("  s=%d trial=%d: rehashes=%d, inserted=%d%n",
                        s, t, rehashCount, inserted);
                }
            }
        }
        System.out.println();
    }

    /**
     * Chart 5: Displacement chain length distribution
     */
    static void analyzeDisplacementChains(String dir) throws Exception {
        System.out.println("--- Displacement Chain Lengths ---");
        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/displacement_chains.csv"))) {
            pw.println("table_type,chain_length,count");

            // Standard cuckoo
            StandardCuckooHashTable<Integer, Integer> standard =
                new StandardCuckooHashTable<>(TABLE_SIZE);
            for (int i = 0; i < (int)(TABLE_SIZE * 0.4); i++) {
                standard.put(i, i);
            }
            Map<Integer, Integer> stdHist = buildHistogram(standard.getStats().getDisplacementChainLengths());
            for (var e : stdHist.entrySet()) {
                pw.printf("Standard,%d,%d%n", e.getKey(), e.getValue());
            }
            System.out.printf("  Standard: %d chains recorded, avg=%.2f%n",
                standard.getStats().getDisplacementChainLengths().size(),
                standard.getStats().getAverageDisplacementChainLength());

            // Bucketized B=4
            BucketizedCuckooHashTable<Integer, Integer> bucketized =
                new BucketizedCuckooHashTable<>(TABLE_SIZE, 4);
            for (int i = 0; i < (int)(TABLE_SIZE * 0.9); i++) {
                bucketized.put(i, i);
            }
            Map<Integer, Integer> buckHist = buildHistogram(bucketized.getStats().getDisplacementChainLengths());
            for (var e : buckHist.entrySet()) {
                pw.printf("Bucketized_B4,%d,%d%n", e.getKey(), e.getValue());
            }
            System.out.printf("  Bucketized_B4: %d chains recorded, avg=%.2f%n",
                bucketized.getStats().getDisplacementChainLengths().size(),
                bucketized.getStats().getAverageDisplacementChainLength());
        }
        System.out.println();
    }

    /**
     * Chart 1, 2, 6: Throughput measurements (simple timing)
     */
    static void analyzeThroughput(String dir) throws Exception {
        System.out.println("--- Throughput Analysis ---");
        int N = 500_000;
        int warmupN = 100_000;
        Random rng = new Random(42);
        int[] keys = new int[N];
        for (int i = 0; i < N; i++) keys[i] = rng.nextInt();

        String[] types = {"STANDARD", "BUCKETIZED_4", "STASHED_3", "CHAINING", "LINEAR_PROBING"};

        try (PrintWriter pw = new PrintWriter(new FileWriter(dir + "/throughput.csv"))) {
            pw.println("table_type,operation,ops_per_sec");

            for (String type : types) {
                // --- Insert throughput ---
                // Warmup
                CuckooHashTable<Integer, Integer> warmup = createTable(type, N);
                for (int i = 0; i < warmupN; i++) warmup.put(keys[i], keys[i]);

                // Measure
                double insertOps = 0;
                for (int trial = 0; trial < 3; trial++) {
                    CuckooHashTable<Integer, Integer> table = createTable(type, N);
                    long start = System.nanoTime();
                    for (int i = 0; i < N; i++) table.put(keys[i], keys[i]);
                    long elapsed = System.nanoTime() - start;
                    insertOps += (double) N / (elapsed / 1e9);
                }
                insertOps /= 3;
                pw.printf("%s,insert,%.0f%n", type, insertOps);
                System.out.printf("  %s insert: %.0f ops/sec%n", type, insertOps);

                // --- Positive lookup throughput ---
                CuckooHashTable<Integer, Integer> loaded = createTable(type, N);
                int loadCount = type.equals("STANDARD") ? (int)(N * 0.4) : (int)(N * 0.6);
                for (int i = 0; i < loadCount; i++) loaded.put(keys[i], keys[i]);

                double posLookupOps = 0;
                for (int trial = 0; trial < 3; trial++) {
                    long start = System.nanoTime();
                    int sum = 0;
                    for (int i = 0; i < loadCount; i++) {
                        Integer v = loaded.get(keys[i]);
                        if (v != null) sum += v;
                    }
                    long elapsed = System.nanoTime() - start;
                    posLookupOps += (double) loadCount / (elapsed / 1e9);
                    if (sum == Integer.MIN_VALUE) System.out.print(""); // prevent dead code elim
                }
                posLookupOps /= 3;
                pw.printf("%s,positive_lookup,%.0f%n", type, posLookupOps);
                System.out.printf("  %s positive lookup: %.0f ops/sec%n", type, posLookupOps);

                // --- Negative lookup throughput ---
                int[] missingKeys = new int[loadCount];
                Random rng2 = new Random(999);
                for (int i = 0; i < loadCount; i++) missingKeys[i] = rng2.nextInt() | 0x70000000;

                double negLookupOps = 0;
                for (int trial = 0; trial < 3; trial++) {
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
                negLookupOps /= 3;
                pw.printf("%s,negative_lookup,%.0f%n", type, negLookupOps);
                System.out.printf("  %s negative lookup: %.0f ops/sec%n", type, negLookupOps);

                // --- Mixed workload ---
                CuckooHashTable<Integer, Integer> mixTable = createTable(type, N);
                int preload = (int)(N * 0.3);
                for (int i = 0; i < preload; i++) mixTable.put(i, i);

                double mixOps = 0;
                int mixOpsCount = 200_000;
                for (int trial = 0; trial < 3; trial++) {
                    Random mixRng = new Random(trial);
                    long start = System.nanoTime();
                    int sum = 0;
                    int nextKey = preload + trial * mixOpsCount;
                    for (int i = 0; i < mixOpsCount; i++) {
                        if (mixRng.nextDouble() < 0.8) {
                            // Read
                            Integer v = mixTable.get(mixRng.nextInt(preload));
                            if (v != null) sum += v;
                        } else {
                            // Write
                            mixTable.put(nextKey++, nextKey);
                        }
                    }
                    long elapsed = System.nanoTime() - start;
                    mixOps += (double) mixOpsCount / (elapsed / 1e9);
                    if (sum == Integer.MIN_VALUE) System.out.print("");
                }
                mixOps /= 3;
                pw.printf("%s,mixed,%.0f%n", type, mixOps);
                System.out.printf("  %s mixed: %.0f ops/sec%n", type, mixOps);
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
            default: throw new IllegalArgumentException(type);
        }
    }

    private static Map<Integer, Integer> buildHistogram(List<Integer> values) {
        TreeMap<Integer, Integer> hist = new TreeMap<>();
        for (int v : values) {
            hist.merge(v, 1, Integer::sum);
        }
        return hist;
    }
}
