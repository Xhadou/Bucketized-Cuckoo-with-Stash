# Benchmarking Methodology: Best Practices and Common Pitfalls

Research findings for improving the Bucketized Cuckoo with Stash benchmark suite.

---

## 1. JMH Best Practices for Hash Table / Data Structure Benchmarking

### Dead Code Elimination (DCE)

The JVM aggressively eliminates code whose results are never used. In a benchmark,
if you compute a value but discard it, the JIT compiler may remove the entire
computation, making the benchmark measure nothing.

**Mitigation strategies:**
- **Return the result** from the `@Benchmark` method. JMH automatically consumes the
  return value to prevent DCE. This is the simplest and preferred approach.
- **Use `Blackhole.consume()`** when the benchmark produces multiple values that
  cannot be combined into a single return. JMH injects a `Blackhole` parameter that
  accepts values and prevents the compiler from proving they are unused.
- Never rely on writing to a field as a DCE defense -- the JIT may still eliminate
  the write if it proves the field is never read.

**Current project status:** The existing benchmarks (InsertBenchmark, LookupBenchmark,
MixedWorkloadBenchmark) correctly return values from `@Benchmark` methods. The
`insertAll()` method returns the table object, and lookup methods return accumulated
`int sum` values. This is sound practice.

### Constant Folding

The JIT compiler can evaluate expressions at compile time if all inputs are known
constants. A benchmark with hardcoded input values may measure zero work.

**Mitigation strategies:**
- Inputs must come from `@State` objects, not literals or `static final` fields.
- Use `@Param` annotations for variable inputs (the JIT cannot fold these).
- Be wary of loop bounds -- a known-constant trip count enables more aggressive
  optimization.

**Current project status:** The benchmarks correctly source keys from `@State` fields
and use `@Param` for configuration. No constant folding risk detected.

### Loop Optimizations

When benchmarking in a loop (as the current benchmarks do), the JVM may apply
loop unrolling, vectorization, or hoisting of invariants in ways that do not reflect
real-world usage.

**Mitigation strategies:**
- Prefer single-operation `@Benchmark` methods that JMH invokes repeatedly, rather
  than manually writing a for-loop over all elements.
- If a loop is necessary (e.g., to measure amortized throughput of bulk inserts),
  ensure the loop body has data-dependent side effects that prevent reordering.

**Potential gap in current project:** The `positiveLookup()` and `negativeLookup()`
methods iterate over the entire key array inside one `@Benchmark` invocation. This
measures "throughput of processing an entire batch" rather than "per-operation
throughput." Consider adding single-key lookup benchmarks with an index counter in
a `@State` object, advanced via `@Setup(Level.Invocation)` or an incrementing index.

### Warmup and Steady-State

JMH's warmup phase allows the JIT to reach steady state. Too few warmup iterations
can capture compilation artifacts; too many waste time.

**Best practice:** 5 warmup iterations of 2+ seconds each is generally sufficient
for typical data structure benchmarks. The current project uses 5 warmup iterations
at 2 seconds each -- this is appropriate.

### Fork Count

Each fork runs in a fresh JVM, eliminating profile pollution from previous benchmarks.

**Best practice:** A minimum of 3 forks is recommended for reliable results. The
current project uses `@Fork(3)` -- this is correct.

**Sources:**
- [Oracle: Avoiding Benchmarking Pitfalls on the JVM](https://www.oracle.com/technical-resources/articles/java/architect-benchmarking.html)
- [JMH Tutorial - Jenkov](https://jenkov.com/tutorials/java-performance/jmh.html)
- [Baeldung: Microbenchmarking with Java](https://www.baeldung.com/java-microbenchmark-harness)
- [Shipilev: Compiler Blackholes](https://shipilev.net/jvm/anatomy-quarks/27-compiler-blackholes/)

---

## 2. Rigorous Hash Table Benchmarking in Systems Research

Systems papers on hash tables (e.g., from USENIX ATC, MICRO, EuroSys) typically
report far more than just throughput. A rigorous evaluation includes:

### Memory Usage Tracking

- **Bytes per key-value pair** at various load factors, including overhead from
  metadata, pointers, padding, and allocator waste.
- **Peak vs. steady-state memory**, especially around resize events.
- **Memory fragmentation** -- some designs (e.g., chaining with per-node allocation)
  cause severe fragmentation that `Runtime.totalMemory()` does not capture.

**Gap in current project:** No memory usage measurement exists. The benchmarks only
report throughput. Adding `Runtime.getRuntime().totalMemory() - freeMemory()` snapshots
or using a JMH `@AuxCounters` state would provide basic memory tracking. For more
precision, use `-XX:NativeMemoryTracking=summary` or a custom allocator wrapper.

### Cache Miss Analysis

- Systems papers measure L1/L2/L3 cache miss rates using hardware performance
  counters (via `perf stat`, Intel VTune, or LIKWID).
- The number of cache misses per operation is often more predictive of real-world
  performance than raw throughput, because cache miss latency (~100-300 cycles)
  dominates hash table lookup cost.
- A single cache miss on a modern Intel processor costs approximately 200+ cycles
  for an LLC miss going to DRAM.

**Gap in current project:** No cache-level analysis is performed. See Section 4 for
recommendations.

### Varying Table Sizes Relative to Cache Hierarchy

- Benchmark at table sizes that fit entirely in L1, L2, L3, and that exceed LLC
  capacity. Performance characteristics change dramatically at each boundary.
- Report the ratio of hash table data to cache capacity.

**Gap in current project:** The benchmarks use 100K, 500K, and 1M element counts,
but do not document how these map to cache boundaries. With `Integer` boxing,
each entry may consume ~48-64 bytes (key object + value object + table slot), so:
  - 100K entries ~ 5-6 MB (likely exceeds L2, fits in L3)
  - 500K entries ~ 25-30 MB (likely exceeds L3)
  - 1M entries ~ 50-60 MB (far exceeds L3)
  
  Smaller sizes (1K-10K entries, fitting in L1/L2) should be added to show
  the cache-friendly regime where cuckoo hashing's bounded probing shines.

### NUMA Awareness

- Multi-socket benchmarks should pin threads to specific NUMA nodes.
- Cross-NUMA memory access adds ~100ns latency penalty.
- JMH's single-threaded mode avoids this, but any future concurrent benchmarks
  must account for it.

**Sources:**
- [Breslow et al.: Fast Hash Tables for In-Memory Data-Intensive Computing (USENIX ATC 2016)](https://www.usenix.org/system/files/conference/atc16/atc16_paper-breslow.pdf)
- [Zhang & Sanchez: Leveraging Caches to Accelerate Hash Tables (MICRO 2019)](https://people.csail.mit.edu/sanchez/papers/2019.hta.micro.pdf)
- [van Dijk: Analysing and Improving Hash Table Performance (BSc thesis)](https://www.tvandijk.nl/pdf/bscthesis.pdf)
- [Leventov: Hash Table Tradeoffs: CPU, Memory, and Variability](https://leventov.medium.com/hash-table-tradeoffs-cpu-memory-and-variability-22dc944e6b9a)

---

## 3. Statistical Rigor in Benchmarking

### Number of Trials (Forks) and Iterations

- **Forks (trials):** 3-5 forks is standard in JMH. Each fork runs in a fresh JVM
  to eliminate JIT profile pollution and GC state carryover.
- **Measurement iterations:** 10-20 measurement iterations per fork is typical.
  More iterations narrow the confidence interval.
- **Rule of thumb:** If the 99.9% confidence interval reported by JMH is wider than
  5-10% of the mean, add more iterations or forks.

**Current project status:** 3 forks x 10 measurement iterations = 30 data points
per benchmark configuration. This is acceptable but on the lower end. Consider
increasing to 5 forks for publication-quality results.

### Confidence Intervals and Variance Reporting

- JMH reports 99.9% confidence intervals by default. Always report these alongside
  the mean.
- Coefficient of variation (CV = stddev/mean) should be below 5% for stable
  benchmarks. If CV exceeds 10%, investigate sources of noise (GC, OS scheduling,
  thermal throttling, background processes).
- Use `@OutputTimeUnit` consistently to make comparisons straightforward.

**Gap in current project:** The benchmark runner script should be verified to preserve
and report the full JMH output including error bars. Results should be presented
with confidence intervals, not just point estimates.

### Eliminating Noise

- **GC interference:** Use `-XX:+UseEpsilonGC` (no-op GC) for micro-benchmarks that
  fit in heap, or `-Xms` = `-Xmx` to eliminate heap resizing.
- **OS noise:** Disable turbo boost, pin CPU frequency, disable hyperthreading for
  reproducible results. On Linux, use `cpupower frequency-set -g performance`.
- **Thermal throttling:** Monitor CPU temperature; sustained benchmarks may throttle.
- **Background processes:** Run on a quiet machine. JMH forks help, but OS-level
  noise still affects wall-clock measurements.

**Gap in current project:** The benchmark scripts do not document or enforce any
system-level noise reduction. Consider adding a pre-flight check script that warns
if turbo boost is enabled or CPU governor is not set to "performance."

### Statistical Tests for Comparing Implementations

- When comparing two hash table implementations, a simple mean comparison is
  insufficient. Use paired t-tests or Wilcoxon signed-rank tests on per-fork results.
- JMH's `-prof perfasm` and `-prof gc` can provide deeper analysis.
- Report effect sizes (e.g., "Bucketized cuckoo is 1.4x faster than standard
  cuckoo") alongside p-values.

**Sources:**
- [Elinext: JMH Advanced Analysis](https://www.elinext.com/blog/java-microbenchmarking-with-jmh-advanced-performance-analysis/)
- [ResearchGate: Rigorous Benchmarking in Reasonable Time](https://www.researchgate.net/publication/257193308_Rigorous_Benchmarking_in_Reasonable_Time)
- [JMH Benchmark With Examples](https://javadevcentral.com/jmh-benchmark-with-examples/)

---

## 4. Cache-Aware Benchmarking for Hash Tables

### How to Measure Cache Behavior

**On Linux (primary approach):**
```bash
# Basic cache statistics for a Java process
perf stat -e L1-dcache-loads,L1-dcache-load-misses,\
L1-dcache-stores,L1-dcache-store-misses,\
LLC-loads,LLC-load-misses,\
cycles,instructions \
java -jar benchmarks.jar

# Detailed per-function cache analysis
perf record -e cache-misses -g java -jar benchmarks.jar
perf report
```

**On macOS (project's likely platform):**
- macOS does not expose hardware performance counters via `perf`.
- Use Instruments.app with the "Counters" template, or `dtrace` for limited
  profiling.
- For rigorous cache analysis, run benchmarks on a Linux machine or VM with `perf`
  access.

**Using LIKWID (Linux):**
```bash
likwid-perfctr -g L2 -m -C 0 java -jar benchmarks.jar
likwid-perfctr -g L3 -m -C 0 java -jar benchmarks.jar
likwid-perfctr -g MEM -m -C 0 java -jar benchmarks.jar
```

**JMH built-in profilers:**
```java
// Run with Linux perf integration
// Command line: -prof perf       (basic perf stats)
//               -prof perfnorm   (normalized per-operation perf stats)
//               -prof perfasm    (annotated assembly with perf data)
```
The `-prof perfnorm` output is particularly valuable -- it reports cache misses
*per benchmark operation*, making comparisons across implementations fair.

### Key Cache Metrics to Report

| Metric | What It Reveals |
|--------|----------------|
| L1-dcache-load-misses / operation | Data locality of hash probing |
| LLC-load-misses / operation | Whether the table fits in cache |
| Instructions per operation | Computational overhead |
| Cycles per operation | End-to-end cost including stalls |
| Branch misses / operation | Predictability of control flow |

### Cache-Size-Aware Test Design

Design benchmark table sizes around known cache boundaries:

| Regime | Typical Size | Expected Behavior |
|--------|-------------|-------------------|
| Fits in L1 (32-48 KB) | ~500 entries | All approaches fast; cuckoo's bounded probing shows minimal advantage |
| Fits in L2 (256 KB - 1 MB) | ~5K-15K entries | Cache locality starts mattering; bucketized cuckoo should show gains |
| Fits in L3 (6-30 MB) | ~100K-500K entries | Main memory latency for misses; memory layout dominates |
| Exceeds L3 | 1M+ entries | Every probe is a potential DRAM access; bounded worst-case lookup count is critical |

**Gap in current project:** The smallest benchmark size (100K elements) already
exceeds L2 on most processors. The cache-friendly regime where bucketized cuckoo's
co-located bucket layout provides the most dramatic advantage is untested.

**Sources:**
- [Intel Community: Performance Counters for L1, L2, L3](https://community.intel.com/t5/Software-Tuning-Performance/Performance-counters-for-measuring-L2-and-L3-Hit-ratios/td-p/1132698)
- [Johnny's Software Lab: Measuring Memory Subsystem Performance](https://johnnysswlab.com/measuring-memory-subsystem-performance/)
- [Pesterev et al.: Locating Cache Performance Bottlenecks Using Data Profiling](https://people.csail.mit.edu/nickolai/papers/pesterev-dprof.pdf)

---

## 5. Common Mistakes in Hash Table Benchmarks That Invalidate Results

### Mistake 1: Testing Only Uniform Random Keys

Most benchmarks generate keys with `Random.nextInt()`, which produces a nearly
uniform distribution. Real workloads often have skewed distributions (Zipf),
sequential patterns, or clustered keys that stress different aspects of the hash
table.

**Impact:** A hash table that performs well on uniform random keys may degrade
severely on sequential or clustered keys due to hash collision patterns.

**Current project status:** All benchmarks use `Random(42)` with `nextInt()` --
purely uniform random. Consider adding sequential key insertion and Zipf-distributed
key workloads.

### Mistake 2: Ignoring the Hash Function's Contribution

The choice of hash function dramatically affects results. Power-of-two table sizes
combined with a weak hash function (e.g., identity or modulo) cause pathological
clustering. A benchmark comparing table designs should either:
- Use the same high-quality hash function across all implementations, OR
- Separately benchmark with multiple hash functions to show sensitivity.

**Current project status:** The hash function is embedded in the implementations.
It should be documented which hash function each variant uses and whether they are
equivalent.

### Mistake 3: Benchmarking Only Successful Operations

Many benchmarks only measure successful lookups (keys that exist). Negative lookups
(keys that are absent) often have very different performance characteristics:
- Cuckoo hashing: negative lookups check exactly 2 locations (plus stash), same as
  positive.
- Chaining: negative lookups must traverse an entire chain.
- Linear probing: negative lookups scan until an empty slot.

**Current project status:** The `LookupBenchmark` correctly includes both positive
and negative lookup benchmarks. This is a strength.

### Mistake 4: Not Testing Delete-Heavy Workloads

Deletion patterns profoundly affect hash table performance. Tombstones in open
addressing degrade lookup performance over time. Benchmarks that only insert and
lookup miss this degradation.

**Gap in current project:** No delete benchmark exists. The mixed workload benchmark
only has inserts and lookups, not deletions.

### Mistake 5: Fixed-Size Tables Hiding Resize Costs

If the benchmark pre-sizes the table to exactly fit the data, it hides the cost
of dynamic resizing, which is a major real-world concern.

**Gap in current project:** Tables are pre-sized with `numElements` in constructors.
Consider adding a "growing table" benchmark that starts with a small initial capacity
and measures amortized insert cost including resizes.

### Mistake 6: Measuring Bulk Operations Instead of Steady-State

Inserting all elements then doing all lookups does not reflect real workloads where
inserts and lookups are interleaved. The table's state during interleaved operations
(partially full, recently resized, stash occupancy varying) may differ from the
bulk case.

**Current project status:** The `MixedWorkloadBenchmark` partially addresses this
with 80/20 read/write mix. However, it does not vary the mix ratio as a `@Param`,
which limits analysis.

### Mistake 7: Not Reporting Load Factor at Measurement Time

Hash table performance is heavily dependent on load factor. Results must report the
actual load factor during measurement, not just the number of elements.

**Gap in current project:** The `LoadFactorBenchmark` exists but should be
cross-referenced with throughput results. Every benchmark result should annotate
the effective load factor.

### Mistake 8: Compiler and Configuration Fairness

When comparing implementations, differences in compilation flags, JVM arguments,
or memory allocation strategies can dwarf algorithmic differences. All
implementations should be compiled and run under identical conditions.

**Current project status:** Since all implementations run in the same JMH harness
with the same JVM, this is handled correctly.

**Sources:**
- [MDPI: Key Concepts, Weakness and Benchmark on Hash Table Data Structures](https://www.mdpi.com/1999-4893/15/3/100)
- [Mytherin: Fair Benchmarking Considered Difficult (DBTest 2018)](https://mytherin.github.io/papers/2018-dbtest.pdf)
- [Attractive Chaos: Revisiting Hash Table Performance](https://attractivechaos.wordpress.com/2018/01/13/revisiting-hash-table-performance/)
- [Preshing: Hash Table Performance Tests](https://preshing.com/20110603/hash-table-performance-tests/)
- [Jackson Allan: Extensive Benchmark of C/C++ Hash Tables](https://jacksonallan.github.io/c_cpp_hash_tables_benchmark/)

---

## Summary of Gaps in Current Project Benchmarks

| Gap | Priority | Effort |
|-----|----------|--------|
| No memory usage measurement | High | Low -- add `@AuxCounters` or post-benchmark snapshot |
| No cache miss analysis (perf counters) | High | Medium -- requires Linux + JMH `-prof perfnorm` |
| Missing small table sizes (L1/L2-resident) | High | Low -- add `@Param` values 1000, 5000, 10000 |
| No delete workload benchmark | Medium | Low -- add delete-heavy mixed benchmark |
| No non-uniform key distributions (Zipf, sequential) | Medium | Medium -- add key generation strategies |
| No growing-table / resize benchmark | Medium | Low -- start from small initial capacity |
| Per-operation benchmarks (single lookup) missing | Medium | Low -- add single-op benchmark with index state |
| Statistical reporting may lack CI display | Low | Low -- verify scripts preserve JMH error output |
| No system-level noise reduction in scripts | Low | Low -- add pre-flight checks |
| Mixed workload does not parameterize read/write ratio | Low | Low -- add `@Param` for mix ratio |
