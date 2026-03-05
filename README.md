# Bucketized Cuckoo Hashing with Stash

An academic implementation and empirical evaluation of cuckoo hashing variants in Java. This project implements standard cuckoo hashing, bucketized cuckoo hashing, and stash-augmented bucketized cuckoo hashing, then benchmarks them against classical baseline hash tables (separate chaining and linear probing) using JMH microbenchmarks.

## Directory Structure

```
.
├── pom.xml
├── .gitignore
├── scripts/
│   ├── run_benchmarks.sh
│   ├── generate_charts.py
│   └── requirements.txt
├── src/
│   ├── main/java/cuckoo/
│   │   ├── core/
│   │   │   ├── CuckooHashTable.java       (common interface)
│   │   │   ├── StandardCuckooHashTable.java
│   │   │   ├── BucketizedCuckooHashTable.java
│   │   │   └── StashedCuckooHashTable.java
│   │   ├── baselines/
│   │   │   ├── ChainingHashTable.java
│   │   │   └── LinearProbingHashTable.java
│   │   ├── hash/
│   │   │   ├── MurmurHash3.java
│   │   │   └── HashFunction.java
│   │   ├── stats/
│   │   │   └── BenchmarkStats.java
│   │   ├── util/
│   │   │   └── WorkloadGenerator.java
│   │   ├── analysis/
│   │   │   └── DirectAnalysis.java        (stress tests, no JMH overhead)
│   │   └── benchmarks/
│   │       ├── InsertBenchmark.java
│   │       ├── LookupBenchmark.java
│   │       ├── MixedWorkloadBenchmark.java
│   │       └── LoadFactorBenchmark.java
│   └── test/java/cuckoo/
│       ├── MurmurHash3Test.java
│       ├── StandardCuckooTest.java
│       ├── BucketizedCuckooTest.java
│       ├── StashedCuckooTest.java
│       ├── BaselinesTest.java
│       ├── StatsAndWorkloadTest.java
│       └── CorrectnessTest.java
├── docs/
│   ├── GETTING_STARTED.md
│   ├── RESEARCH.md
│   └── report/
│       └── final-report.md
├── results/
│   ├── csv/           (DirectAnalysis / JMH output)
│   └── charts/        (generated PNGs)
└── README.md
```

## Prerequisites

- **Java 17+** (tested with OpenJDK 17)
- **Maven 3.6+**
- **Python 3.8+** (only needed for chart generation)

> For a full walkthrough including setup, testing, and result analysis, see [`docs/GETTING_STARTED.md`](docs/GETTING_STARTED.md).

## Build Instructions

```bash
# Compile
mvn clean compile

# Run tests (61 tests across all 5 implementations)
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
# Run all benchmarks (takes ~30 minutes)
bash scripts/run_benchmarks.sh

# Or run a specific benchmark
java -jar target/benchmarks.jar InsertBenchmark

# List available benchmarks
java -jar target/benchmarks.jar -l
```

## Generating Charts

```bash
pip install -r scripts/requirements.txt
python scripts/generate_charts.py
# Charts saved to results/charts/
```

The chart generation script produces seven publication-quality visualizations:

1. Insert throughput comparison
2. Lookup throughput (positive and negative)
3. Load factor vs. bucket size
4. Rehash probability vs. stash size
5. Displacement chain length distribution
6. Mixed workload performance
7. Performance heatmap

## Hash Table Variants

| Variant | Description |
|---------|-------------|
| **StandardCuckooHashTable** | Classic two-table cuckoo hashing (Pagh & Rodler 2004). O(1) worst-case lookup. Maximum load factor ~50%. |
| **BucketizedCuckooHashTable** | Each table position holds B slots instead of one. With B=4, achieves ~95% load factor while preserving O(1) lookup. |
| **StashedCuckooHashTable** | Bucketized cuckoo hashing augmented with a small constant-size stash for displaced elements. Reduces rehash probability to near zero. |
| **ChainingHashTable** | Baseline. Separate chaining with linked lists. Simple but cache-unfriendly at high load. |
| **LinearProbingHashTable** | Baseline. Open addressing with linear probing. Good cache behavior but degrades beyond ~70% load. |

## Key Results

- **Bucketized cuckoo hashing** with bucket size B=4 achieves load factors above 95%, far exceeding standard cuckoo hashing (~50%) and linear probing (~70%).
- **Adding a stash** of size 3--4 virtually eliminates insertion failures, making rehashing unnecessary in practice.
- **Lookup throughput** for all cuckoo variants remains O(1) worst-case, competitive with or better than chaining and linear probing at high load factors.
- **Mixed workloads** (50% lookup, 30% insert, 20% delete) show that stashed bucketized cuckoo hashing provides the best overall throughput when the table is kept at high occupancy.

See the generated charts in `results/charts/` for detailed visualizations.

## References

1. **Pagh, R. & Rodler, F.F.** (2004). Cuckoo Hashing. *Journal of Algorithms*, 51(2), 122--144.
2. **Kirsch, A., Mitzenmacher, M. & Wieder, U.** (2010). More Robust Hashing: Cuckoo Hashing with a Stash. *SIAM Journal on Computing*, 39(4), 1543--1561.
3. **Fan, B., Andersen, D.G., Kaminsky, M. & Mitzenmacher, M.** (2014). Cuckoo Filter: Practically Better Than Bloom. *Proc. 10th ACM International on Conference on Emerging Networking Experiments and Technologies (CoNEXT)*, 75--88.

## License

This project is an academic exercise and is provided as-is for educational purposes.
