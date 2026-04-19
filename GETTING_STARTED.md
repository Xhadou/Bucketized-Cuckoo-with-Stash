# Getting Started

This guide walks you through setting up, running, and analysing results for the Bucketized Cuckoo Hashing with Stash project.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java JDK | 25+ | Must be on PATH (`java -version`) |
| Maven | 3.6+ | Must be on PATH (`mvn -version`) |
| Python | 3.9+ | For chart generation only |
| Git | any | For cloning |

## 1. Clone the Repository

```bash
git clone https://github.com/Xhadou/Bucketized-Cuckoo-with-Stash.git
cd Bucketized-Cuckoo-with-Stash
```

## 2. Build the Project

```bash
mvn clean package -DskipTests
```

This compiles all source files and produces `target/benchmarks.jar` (the self-contained JMH runner). Expected output ends with `BUILD SUCCESS`.

## 3. Run the Test Suite

```bash
mvn test
```

Runs 115 unit and integration tests across all 9 hash table implementations:

- `MurmurHash3Test` — hash function determinism and distribution
- `StandardCuckooTest` — basic standard cuckoo hashing
- `BucketizedCuckooTest` — bucket-based cuckoo with B=1,2,4,8
- `StashedCuckooTest` — stash extension absorbing insertion failures
- `BaselinesTest` — chaining and linear probing baselines
- `baselines/RobinHoodHashTableTest` — Robin Hood probing correctness
- `baselines/QuadraticProbingHashTableTest` — quadratic probing + tombstone deletion
- `StatsAndWorkloadTest` — stats collector and workload generator
- `CorrectnessTest` — parameterised stress tests across representative types (100K keys each)

Expected: `Tests run: 115, Failures: 0, Errors: 0`

## 4. Project Structure

```
src/main/java/cuckoo/
  core/                                    # Cuckoo family implementations
    CuckooHashTable.java                   # Common interface
    StandardCuckooHashTable.java           # Classic 2-table cuckoo hashing
    BucketizedCuckooHashTable.java         # B-slot buckets per position
    StashedCuckooHashTable.java            # Bucketized + overflow stash
    DAryHashTable.java                     # d hash functions (d=2,3,4)
  baselines/
    ChainingHashTable.java                 # Separate chaining
    LinearProbingHashTable.java            # Linear open addressing
    QuadraticProbingHashTable.java         # Quadratic open addressing + tombstones
    HopscotchHashTable.java                # Neighborhood-bitmap open addressing
    RobinHoodHashTable.java                # Probe-distance displacement
  hash/
    HashFamily.java                        # Functional interface for injectable hash families
    HashFunction.java
    HashFunctions.java
    MurmurHash3.java                       # 32-bit hash with seed support
    XXHash32.java                          # xxHash32 implementation
    FNV1aHash.java                         # FNV-1a implementation
  stats/
    BenchmarkStats.java                    # Metrics collector
  util/
    WikipediaDataLoader.java               # Loads real pageview titles from .gz dump
    WorkloadGenerator.java
  benchmarks/                              # JMH benchmark classes (8 total)
    InsertBenchmark.java
    LookupBenchmark.java
    MixedWorkloadBenchmark.java
    LoadFactorBenchmark.java
    DeleteBenchmark.java
    DatasetBenchmark.java
    HashFunctionBenchmark.java
    RealDataBenchmark.java
  analysis/
    DirectAnalysis.java                    # Stress test runner (no JMH overhead)

src/test/java/cuckoo/
  MurmurHash3Test.java
  StandardCuckooTest.java
  BucketizedCuckooTest.java
  StashedCuckooTest.java
  BaselinesTest.java
  StatsAndWorkloadTest.java
  CorrectnessTest.java
  baselines/
    HopscotchHashTableTest.java
    QuadraticProbingHashTableTest.java
    RobinHoodHashTableTest.java
  core/
    DAryHashTableTest.java
  hash/
    HashFunctionTest.java

data/
  README.md                                # Instructions for downloading Wikipedia dataset
  pageviews-sample.gz                      # Wikipedia pageview dump (gitignored — see data/README.md)

scripts/
  generate_charts.py                       # Reads CSVs, produces charts
  run_benchmarks.sh                        # Full JMH run script (Linux/macOS)
  requirements.txt                         # Python dependencies

docs/
  RESEARCH.md                              # Background reading on cuckoo hashing papers
  report/
    main.tex                               # Full IEEE conference report (LaTeX source)
    references.bib                         # BibTeX references
    main.pdf                               # Compiled report

results/
  csv/                                     # Output CSVs from DirectAnalysis and JMH
  charts/                                  # Generated PNG charts (01–07)
```

## 5. Run Direct Analysis (Recommended First Step)

`DirectAnalysis` stress-tests all implementations and writes CSV data. It runs in seconds without JMH overhead and is the best starting point for understanding the algorithmic properties.

```bash
mvn compile -q
java -cp target/classes cuckoo.analysis.DirectAnalysis
```

This writes CSVs to `results/csv/`:

| File | What it measures |
|------|-----------------|
| `throughput.csv` | Insert, lookup, and mixed ops/sec for all 9 variants |
| `load_factor_vs_bucket.csv` | Max achievable load factor for B=1,2,4,8 |
| `rehash_vs_stash.csv` | Rehash count vs stash size s=0..4 |
| `displacement_chains.csv` | Chain length distribution (Standard vs Bucketized) |
| `dary_load_factors.csv` | Max load factor for d=2,3,4 |

## 6. Generate Charts

Install Python dependencies once:

```bash
pip install -r scripts/requirements.txt
```

Then generate charts:

```bash
python scripts/generate_charts.py
```

## 7. Run Full JMH Benchmarks (Optional, Takes ~45 Minutes)

For statistically rigorous results with warmup and forking:

```bash
# Linux/macOS
bash scripts/run_benchmarks.sh

# Windows (manually)
mvn clean package -DskipTests
java -jar target/benchmarks.jar -rf csv -rff results/csv/all_benchmarks.csv
```

To list available benchmarks without running them:

```bash
java -jar target/benchmarks.jar -l
```

To run a specific benchmark:

```bash
java -jar target/benchmarks.jar "InsertBenchmark" -rf csv -rff results/csv/insert.csv
```

## 8. Real-World Dataset (Wikipedia)

`RealDataBenchmark` runs all 9 variants against real Wikipedia page title strings. Download the dataset first:

```bash
# See data/README.md for the full download URL
# Place the file at: data/pageviews-sample.gz
```

Then run:

```bash
java -jar target/benchmarks.jar "RealDataBenchmark"
```

## 9. Understanding the Key Results

### Load Factor vs Bucket Size

| Bucket size (B) | Max load factor |
|-----------------|----------------|
| 1 (standard) | ~57% |
| 2 | ~90% |
| 4 | ~98% |
| 8 | ~99.8% |

### d-ary Cuckoo Load Factors

| Hash functions (d) | Max load factor |
|--------------------|----------------|
| 2 (standard) | ~57% |
| 3 | ~89% |
| 4 | ~96% |

### Stash Effect on Rehashing

A small stash absorbs insertion failures that would otherwise trigger a full rehash:

| Stash size (s) | Rehash count (n=100K inserts) |
|----------------|-------------------------------|
| 0 (no stash) | multiple |
| 1 | significantly fewer |
| 3 | near zero |

This matches the theoretical result: stash reduces rehash probability from Theta(1/n) to O(1/n^(s+1)).

## 10. Key Implementation Rules

These are enforced throughout the codebase — important if you are extending the code:

- **Always use** `Math.floorMod(MurmurHash3.hash32(key, seed), capacity)` — never `%` (breaks on negative hashes)
- **Never derive h2 from h1** — use completely independent seeds for the two hash functions
- **Always check for an existing key** before starting a displacement chain (prevents size inflation on updates)
- **All rehash paths are bounded** — insertion retries up to 20 rehash cycles, and each rehash tries 10 seed pairs per capacity level with at most `MAX_GROWTHS=10` capacity doublings before failing with an exception

## 11. Further Reading

- `docs/RESEARCH.md` — background on cuckoo hashing papers
- `HYPOTHESES.md` — the 5 falsifiable hypotheses driving the experiment design
- `docs/report/main.pdf` — full IEEE-format academic report with all results and analysis
