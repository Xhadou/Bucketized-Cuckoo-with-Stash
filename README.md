# Bucketized Cuckoo Hashing with Stash

An academic implementation and empirical evaluation of cuckoo hashing variants in Java. This project implements nine hash table variants — standard, bucketized, stashed, and d-ary cuckoo hashing, plus five classical baselines — and benchmarks them against each other using JMH microbenchmarks across synthetic and real-world workloads.

## Directory Structure

```
.
├── pom.xml
├── .gitignore
├── HYPOTHESES.md                        # 5 falsifiable hypotheses + experiment matrix
├── GETTING_STARTED.md                   # Setup and usage guide
├── benchmark_results.txt                # Raw JMH output (full suite)
├── lookup_results.txt                   # Raw JMH output (lookup benchmark)
├── scripts/
│   ├── run_benchmarks.sh
│   ├── generate_charts.py
│   └── requirements.txt
├── src/
│   ├── main/java/cuckoo/
│   │   ├── core/
│   │   │   ├── CuckooHashTable.java              (common interface)
│   │   │   ├── StandardCuckooHashTable.java
│   │   │   ├── BucketizedCuckooHashTable.java
│   │   │   ├── StashedCuckooHashTable.java
│   │   │   └── DAryHashTable.java
│   │   ├── baselines/
│   │   │   ├── ChainingHashTable.java
│   │   │   ├── LinearProbingHashTable.java
│   │   │   ├── QuadraticProbingHashTable.java
│   │   │   ├── HopscotchHashTable.java
│   │   │   └── RobinHoodHashTable.java
│   │   ├── hash/
│   │   │   ├── HashFamily.java                   (functional interface for injectable hash families)
│   │   │   ├── HashFunction.java
│   │   │   ├── HashFunctions.java
│   │   │   ├── MurmurHash3.java
│   │   │   ├── XXHash32.java
│   │   │   └── FNV1aHash.java
│   │   ├── stats/
│   │   │   └── BenchmarkStats.java
│   │   ├── analysis/
│   │   │   └── DirectAnalysis.java               (stress tests, no JMH overhead)
│   │   ├── util/
│   │   │   ├── WikipediaDataLoader.java
│   │   │   └── WorkloadGenerator.java
│   │   └── benchmarks/
│   │       ├── InsertBenchmark.java
│   │       ├── LookupBenchmark.java
│   │       ├── MixedWorkloadBenchmark.java
│   │       ├── LoadFactorBenchmark.java
│   │       ├── DeleteBenchmark.java
│   │       ├── DatasetBenchmark.java
│   │       ├── HashFunctionBenchmark.java
│   │       └── RealDataBenchmark.java
│   └── test/java/cuckoo/
│       ├── MurmurHash3Test.java
│       ├── StandardCuckooTest.java
│       ├── BucketizedCuckooTest.java
│       ├── StashedCuckooTest.java
│       ├── BaselinesTest.java
│       ├── StatsAndWorkloadTest.java
│       ├── CorrectnessTest.java
│       ├── baselines/
│       │   ├── HopscotchHashTableTest.java
│       │   ├── QuadraticProbingHashTableTest.java
│       │   └── RobinHoodHashTableTest.java
│       ├── core/
│       │   └── DAryHashTableTest.java
│       └── hash/
│           └── HashFunctionTest.java
├── data/
│   └── README.md                        # Instructions for downloading Wikipedia dataset
├── docs/
│   └── RESEARCH.md
│   └── report/
│       ├── main.tex                     # Full IEEE conference report (LaTeX source)
│       ├── references.bib
│       └── main.pdf                     # Compiled report
└── results/
    ├── csv/                             # CSVs from DirectAnalysis and JMH runs
    │   ├── throughput.csv
    │   ├── load_factor_vs_bucket.csv
    │   ├── rehash_vs_stash.csv
    │   ├── displacement_chains.csv
    │   ├── dary_load_factors.csv
    │   ├── delete_throughput.csv
    │   └── hash_sensitivity.csv
    └── charts/                          # Generated PNG charts
        ├── 01_insert_throughput.png
        ├── 02_lookup_throughput.png
        ├── 03_load_factor_vs_bucket_size.png
        ├── 04_rehash_vs_stash.png
        ├── 05_displacement_chains.png
        ├── 06_mixed_workload.png
        ├── 07_delete_throughput.png
        ├── 08_hash_sensitivity.png
        ├── 09_dary_load_factors.png
        └── 10_performance_heatmap.png
```

## Prerequisites

- **Java 25** (tested with OpenJDK 25.0.2)
- **Maven 3.6+**
- **Python 3.9+** (only needed for chart generation)

> For a full walkthrough including setup, testing, and result analysis, see [`GETTING_STARTED.md`](GETTING_STARTED.md).

## Build Instructions

```bash
# Compile
mvn clean compile

# Run tests (115 tests across all 9 implementations)
mvn test

# Build benchmarks jar
mvn package -DskipTests
```

## Quick Analysis (Recommended First Step)

Run `DirectAnalysis` to stress-test all implementations and generate CSV data in seconds, without JMH overhead:

```bash
mvn compile -q
java -cp target/classes cuckoo.analysis.DirectAnalysis
```

## Running Full JMH Benchmarks

```bash
# Run all benchmarks (takes ~45 minutes)
bash scripts/run_benchmarks.sh

# Or run a specific benchmark
java -jar target/benchmarks.jar InsertBenchmark

# List available benchmarks
java -jar target/benchmarks.jar -l
```

## Hash Table Variants

| Variant | Family | Description |
|---------|--------|-------------|
| **StandardCuckooHashTable** | Cuckoo | Classic two-table cuckoo hashing (Pagh & Rodler 2004). O(1) worst-case lookup. Max load factor ~57%. |
| **BucketizedCuckooHashTable** | Cuckoo | Each table position holds B slots. With B=4, achieves ~98% load factor while preserving O(1) lookup. |
| **StashedCuckooHashTable** | Cuckoo | Bucketized cuckoo augmented with a small constant-size stash. Reduces rehash probability to near zero. |
| **DAryHashTable** | Cuckoo | Uses d≥3 hash functions for richer placement options. d=3 reaches ~89%, d=4 reaches ~96% load. |
| **ChainingHashTable** | Baseline | Separate chaining with linked lists. Simple but cache-unfriendly at high load. |
| **LinearProbingHashTable** | Baseline | Open addressing with linear probing. Excellent cache behavior up to ~70% load. |
| **QuadraticProbingHashTable** | Baseline | Quadratic probing with triangular sequence. Avoids primary clustering; uses tombstones for deletion. |
| **HopscotchHashTable** | Baseline | Neighborhood-bitmap open addressing. Bounds probe distance to H=32; good cache behavior. |
| **RobinHoodHashTable** | Baseline | Displaces elements with shorter probe distances to reduce variance. |

## Key Results (Summary)

- **H1 Confirmed:** B=4 achieves 98% load factor vs ~57% for standard cuckoo (B=1).
- **H2 Confirmed:** Stash of s=3 reduces rehash count to near zero.
- **H3 Confirmed:** B=4+s=3 achieves >95% load factor with near-zero rehash probability simultaneously.
- **H4 Falsified:** Bucketized cuckoo is ~60% slower than linear probing on lookups; Standard Cuckoo is 1.85× faster. Bucketization is a memory-efficiency optimization, not a speed optimization.
- **H5 Confirmed:** Cuckoo variants show ≤15% throughput variance across distributions; Robin Hood shows 10× variation; Bucketized shows 34% slowdown on real Wikipedia string keys.

See [`docs/report/main.pdf`](docs/report/main.pdf) for the full analysis.

## References

1. **Pagh, R. & Rodler, F.F.** (2004). Cuckoo Hashing. *Journal of Algorithms*, 51(2), 122--144.
2. **Kirsch, A., Mitzenmacher, M. & Wieder, U.** (2009). More Robust Hashing: Cuckoo Hashing with a Stash. *SIAM Journal on Computing*, 39(4), 1543--1561.
3. **Fan, B., Andersen, D.G. & Kaminsky, M.** (2013). MemC3: Compact and Concurrent MemCache with Dumber Caching and Smarter Hashing. *NSDI*.
4. **Erlingsson, Ú., Manasse, M. & McSherry, F.** (2006). A Cool and Practical Alternative to Traditional Hash Tables. *WDDS*.
5. **Herlihy, M., Shavit, N. & Tzafrir, M.** (2008). Hopscotch Hashing. *DISC*.
6. **Celis, P., Larson, P-Å. & Munro, J.I.** (1985). Robin Hood Hashing. *FOCS*.
7. **Fotakis, D., Pagh, R., Sanders, P. & Spirakis, P.** (2005). Space Efficient Hash Tables with Worst Case Constant Access Time. *Theory of Computing Systems*, 38(2).

## License

This project is an academic exercise and is provided as-is for educational purposes.
