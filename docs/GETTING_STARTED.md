# Getting Started

This guide walks you through setting up, running, and analysing results for the Bucketized Cuckoo Hashing with Stash project.

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java JDK | 17+ | Must be on PATH (`java -version`) |
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

Runs 61 unit and integration tests across all 5 hash table implementations:

- `MurmurHash3Test` — hash function determinism and distribution
- `StandardCuckooTest` — basic standard cuckoo hashing
- `BucketizedCuckooTest` — bucket-based cuckoo with B=1,2,4,8
- `StashedCuckooTest` — stash extension absorbing insertion failures
- `BaselinesTest` — chaining and linear probing baselines
- `StatsAndWorkloadTest` — stats collector and workload generator
- `CorrectnessTest` — parameterised stress tests across all 5 types (100K keys each)

Expected: `Tests run: 61, Failures: 0, Errors: 0`

## 4. Project Structure

```
src/main/java/cuckoo/
  core/          # Hash table implementations
    StandardCuckooHashTable.java      # Classic 2-table cuckoo hashing
    BucketizedCuckooHashTable.java    # B-slot buckets per position
    StashedCuckooHashTable.java       # Bucketized + overflow stash
  baselines/
    ChainingHashTable.java            # Separate chaining baseline
    LinearProbingHashTable.java       # Open addressing baseline
  hash/
    MurmurHash3.java                  # 32-bit hash with seed support
  stats/
    BenchmarkStats.java               # Metrics collector
  benchmarks/                         # JMH benchmark classes
  analysis/
    DirectAnalysis.java               # Stress test runner (no JMH overhead)

src/test/java/cuckoo/                 # All test files

scripts/
  generate_charts.py                  # Reads CSVs, produces 7 charts
  run_benchmarks.sh                   # Full JMH run script (Linux/macOS)
  requirements.txt                    # Python dependencies

docs/
  RESEARCH.md                         # Background reading on cuckoo hashing
  report/final-report.md              # Full academic report with results
  GETTING_STARTED.md                  # This file

results/
  csv/                                # Output CSVs from DirectAnalysis
  charts/                             # Output PNG charts
```

## 5. Run Direct Analysis (Recommended First Step)

`DirectAnalysis` stress-tests all implementations and writes CSV data used by the charts. It runs in seconds without JMH overhead and is the best starting point for understanding the algorithmic properties.

```bash
mvn compile -q
java -cp target/classes cuckoo.analysis.DirectAnalysis
```

This writes four CSVs to `results/csv/`:

| File | What it measures |
|------|-----------------|
| `throughput.csv` | Insert, lookup, and mixed ops/sec for all 5 types |
| `load_factor_vs_bucket.csv` | Max achievable load factor for B=1,2,4,8 |
| `rehash_vs_stash.csv` | Rehash count vs stash size s=0..4 |
| `displacement_chains.csv` | Chain length distribution (Standard vs Bucketized) |

## 6. Generate Charts

Install Python dependencies once:

```bash
pip install -r scripts/requirements.txt
```

Then generate all 7 charts:

```bash
python scripts/generate_charts.py
```

Charts are written to `results/charts/`:

| Chart | Key insight |
|-------|-------------|
| `insert_throughput.png` | Throughput comparison at N=500K |
| `lookup_throughput.png` | Positive vs negative lookup speed |
| `load_factor_vs_bucket_size.png` | B=4 reaches ~98% load vs ~56% for B=1 |
| `rehash_vs_stash_size.png` | Stash of s=3 nearly eliminates rehashes |
| `displacement_chains.png` | Chain length distribution histogram |
| `mixed_workload.png` | 80% reads / 20% writes throughput |
| `summary_heatmap.png` | Full comparative summary |

## 7. Run Full JMH Benchmarks (Optional, Takes ~30 Minutes)

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

To run a specific benchmark (e.g., only insert):

```bash
java -jar target/benchmarks.jar "InsertBenchmark" -rf csv -rff results/csv/insert.csv
```

## 8. Understanding the Key Results

### Load Factor vs Bucket Size

Standard cuckoo hashing (B=1) fails around 50% load. Increasing bucket size dramatically raises the ceiling:

| Bucket size (B) | Max load factor |
|-----------------|----------------|
| 1 (standard) | ~56% |
| 2 | ~90% |
| 4 | ~98% |
| 8 | ~99.8% |

### Stash Effect on Rehashing

A small stash absorbs insertion failures that would otherwise trigger a full rehash:

| Stash size (s) | Rehash count (20K inserts) |
|----------------|---------------------------|
| 0 (no stash) | multiple |
| 1 | significantly fewer |
| 3 | near zero |

This matches the theoretical result: stash reduces rehash probability from Theta(1/n) to O(1/n^(s+1)).

## 9. Key Implementation Rules

These are enforced throughout the codebase — important if you are extending the code:

- **Always use** `Math.floorMod(MurmurHash3.hash32(key, seed), capacity)` — never `%` (breaks on negative hashes)
- **Never derive h2 from h1** — use completely independent seeds for the two hash functions
- **Always check for an existing key** before starting a displacement chain (prevents size inflation on updates)
- **All rehash paths are bounded** — insertion retries up to 20 rehash cycles, and each rehash tries 10 seed pairs per capacity level with at most `MAX_GROWTHS=10` capacity doublings before failing with an exception. There is no unbounded recursion.

## 10. Further Reading

- `docs/RESEARCH.md` — background on cuckoo hashing papers (Pagh & Rodler, Kirsch & Mitzenmacher)
- `docs/report/final-report.md` — full academic report with analysis of all results
