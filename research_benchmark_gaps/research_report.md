# Benchmark Gap Analysis: Bucketized Cuckoo with Stash

## What the Project Does Well
- JMH config is sound: 3 forks, 5 warmup, 10 measurement iterations
- DCE/constant-folding properly mitigated (returns values, uses @State/@Param)
- Both positive and negative lookups tested
- Mixed workload benchmark exists
- Parameterized correctness tests across all 5 implementations
- Real measured data with visualization pipeline

## Critical Gaps (Must Fix)

### 1. Missing: Insertion Failure Probability Analysis
Academic papers (Kirsch-Mitzenmacher-Wieder) validate the stash by plotting failure probability vs table size on a log-log scale. The project should run 10K+ random trials and measure what fraction need rehash, then verify the slope matches O(1/n^s).

### 2. Missing: Memory Usage Measurement
No bytes-per-entry tracking. Every systems paper reports this.

### 3. Missing: Cache Miss Analysis
The core argument for bucketization is cache locality, but no cache data supports it. Need JMH `-prof perfnorm` on Linux.

### 4. Missing: Small Table Sizes
Smallest benchmark (100K) exceeds L2. Need 1K-10K to show the cache-friendly regime.

### 5. Missing: Throughput vs Load Factor Curves
The single most common graph in modern papers. Currently only measure throughput at fixed load factors.

### 6. Missing: Stash Utilization Metrics
No measurement of stash occupancy, stash access frequency during lookups, or stash overflow rate.

## Important Gaps (Should Fix)

### 7. No Delete Workload
### 8. No Non-Uniform Key Distributions (Zipf, sequential)
### 9. No Growing-Table / Resize Benchmark (tables are pre-sized)
### 10. No Per-Operation Latency Percentiles (P50/P95/P99)
### 11. Missing: Phase Transition Analysis (failure rate at closely-spaced load factors near threshold)
### 12. No Confidence Intervals in reported results
### 13. Mixed workload doesn't parameterize read/write ratio

## Sources
See findings_academic_papers.md, findings_benchmarking_methodology.md, findings_cuckoo_metrics.md
