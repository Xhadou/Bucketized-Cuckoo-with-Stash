# Academic Experiments in Cuckoo Hashing Papers: Findings

This document surveys the standard experiments, metrics, graphs, and evaluation methodologies used in foundational and modern cuckoo hashing papers. The goal is to identify what a rigorous experimental evaluation of a cuckoo hashing variant should include.

---

## 1. Pagh & Rodler -- "Cuckoo Hashing" (ESA 2001 / J.Algorithms 2004)

**Paper:** The original cuckoo hashing paper introducing 2-table, 1-slot-per-bucket hashing with worst-case O(1) lookup.

### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Metric** | Average clock cycles per operation (lookup, insert, delete) |
| **Measurement** | Used x86 `rdtsc` instruction; subtracted 32-cycle call overhead |
| **Parameter varied** | Table size / number of elements (set sizes from 2^12 to 2^24) |
| **Load factor** | Fixed at 1/3 (each table sized to hold n elements, total space = 3n) |
| **Baselines** | Linear probing, two-way chaining, double hashing |
| **Runs** | 10 independent runs per data point; variance reported |

### Key Graphs/Tables

1. **Average time per successful lookup** vs. set size -- compared cuckoo hashing against linear probing, two-way chaining, double hashing. Linear probing won on average; cuckoo and two-way chaining followed ~40 clock cycles behind.
2. **Average time per insertion** vs. set size -- insertion cost dominated by random memory accesses for large sets; for small (in-cache) sets, cuckoo was slower due to higher computation overhead.
3. **Time for forced rehashes** included in insertion measurements.
4. **Fraction of keys placed in T2** -- at load factor 1/3, only ~10% of new keys end up in T2 when both candidate positions are inspected.

### Claims Validated Experimentally

- Worst-case O(1) lookup is practical (only 2 memory accesses).
- Insertion amortized cost is competitive with other open-addressing schemes.
- Rehash cost is manageable and amortized away.
- Performance is competitive with linear probing for lookups despite the 2-access guarantee.

### What They Did NOT Measure

- They did not vary load factor systematically (fixed at 1/3).
- No throughput (ops/sec) measurements -- only per-operation cycle counts.
- No concurrency experiments.
- No memory efficiency / space overhead analysis beyond the 3n cells.

---

## 2. Kirsch, Mitzenmacher & Wieder -- "More Robust Hashing: Cuckoo Hashing with a Stash" (ESA 2008 / SICOMP 2010)

**Paper:** Introduces a constant-size stash to dramatically reduce insertion failure probability.

### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Primary metric** | Failure probability (probability that insertion fails, requiring full rehash) |
| **Parameter varied** | Stash size s (0, 1, 2, 3, 4); table size n |
| **Methodology** | Primarily analytical with simulation validation |
| **Hash functions** | 2 hash functions (standard cuckoo) with added stash |

### Key Graphs/Tables

1. **Failure probability vs. table size n** for various stash sizes s -- the central result. Shows exponential decrease in failure probability as stash size increases.
2. **Theoretical bound**: Failure probability = O(n^{-(s+1)}) for stash of size s with 2 hash functions. (Note: later work by Aumiller et al. 2022 found a bug; the correct bound involves O(n^{-d-s-1}) for d-ary cuckoo hashing.)
3. **Simulation results** confirming the theoretical predictions -- ran millions of random insertions and measured how often the build process fails.

### Claims Validated Experimentally

- A stash of size 3-4 makes failure probability negligible for practical table sizes.
- The stash does not meaningfully impact lookup performance (stash checked only on miss in main tables).
- Small constant-size stash suffices -- no need to grow stash with n.

### What They Did NOT Measure

- No wall-clock time or throughput benchmarks.
- No comparison against alternative failure-mitigation strategies (e.g., more hash functions, larger buckets).
- No evaluation of stash impact on lookup latency in practice.
- Primarily a theory paper -- experimental section is simulation of failure rates only.

---

## 3. Erlingsson, Manasse & McSherry -- "A Cool and Practical Alternative to Traditional Hash Tables" (WDDS 2006)

**Paper:** Introduces bucketized cuckoo hashing (multiple slots per bucket) and evaluates interaction with multiple hash functions.

### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Primary metric** | Maximum achievable load factor before insertion failure |
| **Parameters varied** | Number of hash functions d (2, 3, 4, ...); bucket size b (1, 2, 4, 8) |
| **Methodology** | Empirical measurement of load factor thresholds |

### Key Graphs/Tables

1. **Load factor threshold vs. number of hash functions** -- with b=1 slot/bucket:
   - d=2: ~50% load factor
   - d=3: ~91% load factor
   - d=4: ~97% load factor
2. **Load factor threshold vs. bucket size** -- with d=2 hash functions:
   - b=1: ~50%
   - b=2: ~86%
   - b=4: ~95%
   - b=8: ~98%
3. **Combined effect of d and b** -- the interaction is superlinear; even small increases in both yield dramatic improvement.

### Claims Validated Experimentally

- Bucketization is a practical and effective way to increase load factors.
- The combination of multiple hash functions + larger buckets pushes load factor above 95%.
- Bucketized cuckoo hashing is a viable, practical alternative to traditional hash tables.

### What They Did NOT Measure

- No throughput or latency benchmarks.
- No comparison against non-cuckoo hash tables.
- No analysis of insertion cost (number of displacements/evictions).
- No memory efficiency comparison accounting for bucket metadata overhead.
- No concurrency experiments.

---

## 4. Modern Systems Papers

### 4a. MemC3 -- Fan, Andersen & Kaminsky (NSDI 2013)

**Paper:** Applies optimistic cuckoo hashing to build a high-performance key-value cache (improved Memcached).

#### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Primary metrics** | Throughput (ops/sec), memory efficiency (bytes/item) |
| **Workload generator** | Yahoo Cloud Serving Benchmark (YCSB) |
| **Parameters varied** | Read/write ratio, number of threads, key-value sizes |
| **Baselines** | Stock Memcached, optimized Memcached variants |
| **Bucket configuration** | 4-way set-associative buckets with 2 hash functions |

#### Key Graphs/Tables

1. **Throughput vs. number of threads** -- shows near-linear scaling; 35M lookups/sec aggregate across threads.
2. **Throughput vs. read/write ratio** -- read-heavy workloads show biggest gains.
3. **Memory overhead comparison** -- 30% less memory than Memcached for small KV pairs.
4. **Throughput comparison** -- 3x throughput over baseline Memcached.
5. **Per-thread throughput** -- 5M lookups/sec per thread.
6. **Latency distribution** (CDF) for various operations.

#### Claims Validated

- Concurrent cuckoo hashing with tag-based lookup achieves high throughput.
- Space efficiency superior to chaining-based alternatives.
- CLOCK eviction is nearly as effective as LRU with much lower overhead.

---

### 4b. libcuckoo / "Algorithmic Improvements for Fast Concurrent Cuckoo Hashing" (EuroSys 2014)

**Paper:** Li, Andersen, Kaminsky, Freedman. Extends MemC3 with fine-grained locking for full read-write concurrency.

#### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Primary metrics** | Throughput (Mops/sec), speedup vs. single-thread |
| **Parameters varied** | Number of threads, read/write mix, table size, load factor |
| **Baselines** | Intel TBB concurrent_hash_map, other concurrent hash tables |
| **Concurrency model** | Lock striping with 2-bucket granularity |

#### Key Graphs/Tables

1. **Throughput vs. number of threads** for various read/write mixes (100/0, 95/5, 50/50, 0/100).
2. **Throughput vs. load factor** -- showing performance remains stable up to ~95% load.
3. **Comparison table** against Intel TBB, click hash, other concurrent hash maps.
4. **Scalability analysis** -- near-linear scaling for read-heavy workloads.
5. **Latency percentiles** (p50, p99, p99.9) for operations.

#### Claims Validated

- Fine-grained locking enables concurrent cuckoo hashing to scale to many cores.
- Breadth-first search (BFS) for eviction paths outperforms random walk and DFS.
- Load factors of 95%+ are achievable with 4-way buckets and 2 hash functions.

---

### 4c. HORTON Tables -- Breslow, Zhang, et al. (USENIX ATC 2016)

**Paper:** Redesigned BCHT that reduces average bucket accesses per lookup.

#### Experiments Conducted

| Aspect | Details |
|--------|---------|
| **Primary metrics** | Throughput (Mops/sec), average bucket accesses per lookup, load factor |
| **Parameters varied** | Load factor, successful vs. unsuccessful query ratio, bucket size |
| **Baselines** | Standard BCHT, cuckoo hashing variants |

#### Key Graphs/Tables

1. **Average bucket accesses per lookup** -- positive lookups: <1.18 buckets (vs. 1.5 for standard BCHT); negative lookups: <1.06 buckets (vs. 2.0 for BCHT).
2. **Throughput improvement** -- 17-35% over baseline BCHT for successful queries; 73-89% for unsuccessful queries.
3. **Load factor achieved** -- 95%+ maintained.
4. **Throughput vs. load factor** curves.
5. **Breakdown of 1-bucket vs. 2-bucket lookups** as function of load factor.

#### Claims Validated

- Remap entries (stored in otherwise-empty slots) can redirect lookups to avoid second bucket access.
- Negative lookups (key not present) benefit most dramatically.
- Horton tables maintain high load factors while reducing lookup cost.

---

### 4d. Cuckoo++ -- Pontarelli et al. (ANCS 2018)

**Paper:** Hash tables optimized for networking applications (connection tracking, firewalls, NAT).

#### Key Metrics

- Throughput for batched lookups.
- Memory access count per operation.
- Load factor sustainability.
- Comparison against both "optimistic" (MemC3-style) and "pessimistic" (lock-based) cuckoo variants.
- Claims consistent performance improvement even at high load factors (unlike Horton tables).

---

### 4e. SetSep -- Arbitman, Naor & Segev

**Paper:** Backyard cuckoo hashing / de-amortized cuckoo hashing.

#### Key Metrics

- Worst-case operation time (not just amortized).
- Stash overflow frequency.
- Comparison of worst-case vs. amortized performance.

---

## 5. Summary: Standard Experimental Methodology for Cuckoo Hashing Papers

### Metrics Every Paper Should Track

| Metric | Category | Who Uses It |
|--------|----------|-------------|
| **Throughput** (ops/sec) | Performance | MemC3, libcuckoo, Horton, Cuckoo++ |
| **Latency per operation** (cycles or ns) | Performance | Pagh-Rodler, libcuckoo |
| **Latency distribution** (p50/p99/p99.9) | Performance | libcuckoo, MemC3 |
| **Load factor** (max achievable) | Space efficiency | All papers |
| **Insertion failure probability** | Robustness | Kirsch-Mitzenmacher, Erlingsson |
| **Average bucket/memory accesses per lookup** | I/O efficiency | Horton, Pagh-Rodler |
| **Number of displacements per insertion** | Insertion cost | Pagh-Rodler (implicitly), libcuckoo |
| **Memory overhead** (bytes/entry) | Space efficiency | MemC3 |
| **Scalability** (throughput vs. threads) | Concurrency | MemC3, libcuckoo |

### Parameters Every Paper Should Vary

| Parameter | Typical Range |
|-----------|---------------|
| **Table size / number of elements (n)** | 2^10 to 2^28 |
| **Load factor** | 50% to 99% |
| **Bucket size (b)** | 1, 2, 4, 8 |
| **Number of hash functions (d)** | 2, 3, 4 |
| **Stash size (s)** | 0, 1, 2, 3, 4 |
| **Read/write mix** | 100/0, 95/5, 50/50, 0/100 |
| **Number of threads** | 1 to 16+ |
| **Key/value size** | 4B to 256B+ |

### Standard Baselines for Comparison

- **Within cuckoo family:** Standard (unbucketized) cuckoo, BCHT, MemC3-style optimistic cuckoo
- **Outside cuckoo family:** Linear probing, chained hashing, Robin Hood hashing, Intel TBB concurrent_hash_map, std::unordered_map
- **Systems-level:** Memcached, Redis (for KV store papers)

### Standard Graph Types

1. **Throughput vs. load factor** -- the most common graph in modern papers
2. **Throughput vs. number of threads** -- for concurrent variants
3. **Failure probability vs. table size** -- for stash/robustness papers
4. **Load factor threshold vs. bucket size / hash functions** -- for bucketized variants
5. **CDF of operation latency** -- for systems papers
6. **Average displacements per insertion vs. load factor** -- measures insertion cost
7. **Memory usage vs. number of entries** -- space efficiency

---

## 6. Gap Analysis: What a Bucketized Cuckoo Hashing with Stash Paper Needs

Given that the project combines bucketization (from Erlingsson et al.) with a stash (from Kirsch et al.), a rigorous evaluation should include:

### Must-Have Experiments

1. **Failure probability vs. table size** for various stash sizes (s=0..4) and bucket sizes (b=1,2,4,8) -- the core novelty claim. Does bucketization + stash yield multiplicative improvement in failure reduction?
2. **Maximum load factor vs. stash size** for various bucket sizes -- how much does the stash push the load factor ceiling?
3. **Average displacements per insertion vs. load factor** -- does the stash reduce displacement chains?
4. **Throughput vs. load factor** -- practical performance under varying fullness.
5. **Lookup latency breakdown** -- cost of checking stash on miss. How often is the stash consulted? What is the overhead?

### Should-Have Experiments

6. **Throughput comparison** against standard BCHT (no stash), standard cuckoo + stash (no buckets), MemC3, libcuckoo, std::unordered_map.
7. **Memory overhead analysis** -- bytes per entry including stash overhead vs. baselines.
8. **Insertion time vs. load factor** -- showing that the stash reduces or eliminates expensive rehashes.
9. **Rehash frequency** with and without stash -- how many rehashes are avoided?

### Nice-to-Have Experiments

10. **Scalability** (if concurrent) -- throughput vs. threads.
11. **Varying key/value sizes** -- impact on cache behavior.
12. **YCSB or realistic workload** benchmarks.
13. **Construction time** for static vs. dynamic use cases.

---

## Sources

- [Pagh & Rodler, "Cuckoo Hashing" (BRICS report)](https://www.brics.dk/RS/01/32/BRICS-RS-01-32.pdf)
- [Pagh & Rodler, "Cuckoo Hashing" (Journal version)](https://www.rasmuspagh.net/papers/cuckoo.pdf)
- [Kirsch, Mitzenmacher & Wieder, "More Robust Hashing: Cuckoo Hashing with a Stash"](https://www.eecs.harvard.edu/~michaelm/postscripts/esa2008full.pdf)
- [Kirsch et al. at Springer](https://link.springer.com/chapter/10.1007/978-3-540-87744-8_51)
- [MemC3: Compact and Concurrent MemCache (NSDI 2013)](https://www.cs.cmu.edu/~dga/papers/memc3-nsdi2013.pdf)
- [Algorithmic Improvements for Fast Concurrent Cuckoo Hashing (EuroSys 2014)](https://www.cs.princeton.edu/~mfreed/docs/cuckoo-eurosys14.pdf)
- [HORTON Tables (USENIX ATC 2016)](https://cseweb.ucsd.edu//~tullsen/horton.pdf)
- [Cuckoo++ Hash Tables (ANCS 2018)](https://arxiv.org/pdf/1712.09624)
- [libcuckoo on GitHub](https://github.com/efficient/libcuckoo)
- [Erlingsson et al. referenced via Wikipedia](https://en.wikipedia.org/wiki/Cuckoo_hashing)
- [Generalized Cuckoo Hashing with a Stash, Revisited](https://arxiv.org/abs/2010.01890)
