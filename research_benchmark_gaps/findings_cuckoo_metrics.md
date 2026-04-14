# Key Metrics and Analyses for Evaluating Cuckoo Hashing Variants

Research findings on the specific metrics important for benchmarking bucketized cuckoo
hashing and cuckoo hashing with a stash.

---

## 1. Insertion Failure Probability Analysis

### Basic Cuckoo Hashing (2 tables, 1 item per bucket)

- **Failure probability per insertion**: O(1/n^2) for load factor < 1/2, where n is table
  size. This follows from the random bipartite graph model: failure requires a connected
  component with more edges than vertices (a "complex component"), which occurs with
  probability O(1/n^2) below the 50% threshold.
- **Rehash probability over n insertions**: Since each insertion fails independently with
  O(1/n^2), a union bound gives that all n insertions succeed with probability at least
  1 - O(1/n). Equivalently, the probability of needing at least one rehash during a full
  build is O(1/n).
- **At the threshold (load = 0.5)**: The probability of failure transitions sharply. Below
  50%, the cuckoo graph is a pseudoforest with high probability. Above 50%, a giant
  component with multiple cycles appears, making insertion failure near-certain.

### With a Stash of Size s (Kirsch, Mitzenmacher, Wieder 2008)

- **Failure probability**: O(1/n^s) for constant stash size s. A stash of size s = 3 or 4
  yields a failure probability of O(1/n^3) or O(1/n^4) respectively, which is effectively
  negligible for tables of practical size.
- **Generalized (d items per bucket, stash size s)**: The failure probability becomes
  O(n^{(s+1)(1-d)}). For example, with d = 4 items per bucket and s = 3, this is
  O(n^{-12}).
- **Tighter recent bound**: The probability that the stash size exceeds s is O(n^{-d-s})
  where d is the bucket capacity (from "Generalized Cuckoo Hashing with a Stash,
  Revisited", 2022).
- **Key insight for benchmarking**: Measure the fraction of builds (out of many random
  trials) that require rehash. Compare against O(1/n^s) scaling. Plot log(failure_rate) vs
  log(n) and verify the slope matches -s (or -(d+s) for the tighter bound).

### How Papers Report This

Papers typically run 10,000-1,000,000 independent random trials at each load factor and
report:
- Fraction of trials requiring rehash (insertion failure rate)
- This is plotted on a log scale vs. load factor
- The sharp phase transition at the theoretical threshold is visually prominent

---

## 2. Expected Displacement / Eviction Chain Length

### Theoretical Bounds

- **Expected insertion time**: O(1) amortized for load factor below the threshold. This
  follows from the expected size of connected components in the cuckoo graph being O(1).
- **MaxLoop parameter**: Practical implementations use a maximum displacement limit
  (commonly called MaxLoop or max_kicks). The original Pagh-Rodler analysis suggests
  MaxLoop = O(log n) suffices. Common practical choices:
  - MaxLoop = 500 (used in many implementations as a safe default)
  - MaxLoop = 6 * log2(n) (theoretical recommendation)
  - The libcuckoo implementation (Li et al., EuroSys 2014) uses 500 as the default
- **Near the threshold**: Expected chain length diverges. At load factor c approaching c*_d,
  the expected insertion time grows but remains O(1) for c < c*_d (proven for d >= 3 by
  Bell and Frieze, FOCS 2024).
- **Random walk vs. BFS**: Random walk cuckoo hashing (where the evicted key is placed in
  its alternate location chosen uniformly) has polylogarithmic expected insertion time with
  high probability for d >= 8 (Frieze, Melsted, Mitzenmacher 2009). The BFS/search-based
  approach produces kick-out chains an order of magnitude shorter at high density (~97.5%).

### How to Measure Empirically

- **Track per-insertion displacement count**: For each insertion, count the number of
  evictions before the chain terminates. Report:
  - Mean displacement chain length
  - Median displacement chain length
  - 95th and 99th percentile chain lengths
  - Maximum chain length observed
- **Plot chain length distribution**: Histogram of chain lengths at various load factors.
  The distribution should be geometric/exponential below the threshold.
- **Track cumulative displacements**: Total evictions across all insertions divided by
  number of insertions gives the amortized displacement cost.

---

## 3. Stash Utilization and Effectiveness Metrics

### Theoretical Stash Occupancy

- For standard cuckoo hashing (2 tables, 1 item per bucket) with load factor alpha < 0.5:
  the expected number of items that cannot be placed in the main table (and thus need the
  stash) is O(1/n^{s-1}) for a stash of size s. In practice, the stash is almost always
  empty at moderate load factors.
- At higher load factors (approaching the threshold), stash utilization increases. The stash
  absorbs items from "complex components" in the cuckoo graph -- connected components with
  more edges than vertices.
- **With bucketization (d items per bucket)**: The stash is needed even less frequently
  because bucketization itself dramatically reduces the failure probability. The combination
  is synergistic: bucketization handles most collisions, and the stash catches the rare
  remaining failures.

### Metrics to Track

1. **Stash occupancy at end of build**: How many items are in the stash after all insertions
   complete. Report the distribution across many trials.
2. **Stash high-water mark**: Maximum stash occupancy observed during the insertion sequence
   (items may enter and leave the stash during insertions if the stash is checked as part of
   the eviction process).
3. **Stash overflow rate**: Fraction of trials where the stash itself overflows (stash
   occupancy > s). This is the true "insertion failure" metric for stash-augmented cuckoo
   hashing.
4. **Stash access frequency during lookups**: What fraction of lookups must consult the
   stash. This is the performance cost of the stash -- ideally it should be near zero. Each
   stash probe is an additional O(s) comparison.
5. **Stash occupancy vs. load factor curve**: Plot average stash occupancy as load factor
   increases. Should remain near zero until approaching the threshold, then rise sharply.

### Effectiveness Assessment

- The stash is "working as intended" if:
  - Stash overflow rate matches the theoretical O(1/n^s) bound
  - Stash occupancy is typically 0 or 1 at moderate load factors
  - The stash enables achieving higher load factors without rehash compared to no-stash
  - Lookup performance degradation from stash probing is minimal (< 1-2% of lookups
    actually need the stash)

---

## 4. Cache-Line Utilization in Bucketized Cuckoo Hashing

### Why Bucket Size Matters

- A CPU cache line is typically 64 bytes. The core optimization in bucketized cuckoo hashing
  is to size each bucket to fit within one or two cache lines, so that checking all slots in
  a bucket requires only 1-2 memory accesses.
- **4-way bucketized**: With 8-byte entries (fingerprint + pointer or key), 4 entries fit in
  32 bytes (half a cache line) or 8 entries fit in 64 bytes (one cache line). This is the
  most common practical configuration.
- **8-way set-associative**: Used in some implementations (e.g., RocksDB cuckoo tables).
  Keys are packed first, then values, so that key comparison requires accessing only one
  cache line, and the value cache line is accessed only on a match.

### Load Factor Achievements by Bucket Size

| Bucket Size (slots) | Max Practical Load Factor | Notes |
|---------------------|--------------------------|-------|
| 1 (standard)        | ~50%                     | Basic cuckoo hashing |
| 2                   | ~80%                     | Significant improvement |
| 4                   | ~95%                     | Sweet spot for most uses |
| 8                   | ~98%+                    | Diminishing returns vs. 4 |

### Memory Access Pattern

- **Lookup**: Check the primary bucket (1 cache line access), then if not found, check the
  alternate bucket (1 more cache line access). Worst case = 2 cache misses. With a stash,
  add s comparisons (but stash fits in L1 cache if small).
- **At 90% load with Cuckoo++ design**: 85% of keys are found in the first bucket (1 cache
  miss), 15% require checking the second bucket (2 cache misses). Average ~1.15 cache
  misses per lookup.
- **Prefetching**: The alternate bucket address can be computed and prefetched while checking
  the primary bucket, hiding the latency of the second access.

### Metrics to Track

1. **Cache misses per lookup**: Measure via hardware performance counters (perf stat) or
   estimate from bucket access patterns.
2. **Primary bucket hit rate**: Fraction of lookups resolved in the first bucket checked.
   Higher is better; depends on load factor and bucket size.
3. **Bytes read per lookup**: Total bytes transferred from memory per lookup operation.
   Ideally 64 bytes (one cache line) for a positive lookup found in the first bucket.
4. **Throughput (ops/sec) vs. bucket size**: Empirical measurement showing the
   cache-friendliness tradeoff.

---

## 5. Theoretical Thresholds from Random Graph Theory

### Sharp Load Factor Thresholds (c*_d) for d-ary Cuckoo Hashing

The load factor threshold c*_d is the maximum fraction of the table that can be filled. Below
this threshold, insertion succeeds with high probability in O(1) expected time. Above it,
insertion fails with high probability.

| d (hash functions) | c*_d (load threshold) | Notes |
|--------------------|----------------------|-------|
| 2                  | 0.5 (exact)          | Classical Erdos-Renyi threshold |
| 3                  | ~0.918               | Significant jump from d=2 |
| 4                  | ~0.977               | |
| 5                  | ~0.992               | |
| 6                  | ~0.997               | |
| Large d            | 1 - (1+o(1))e^{-d}  | Approaches 1 exponentially fast |

Source: Bell and Frieze, "O(1) Insertion for Random Walk d-ary Cuckoo Hashing up to the Load
Threshold" (FOCS 2024).

### Peeling Threshold vs. Load Threshold

- The **peeling threshold** is a lower bound on the load threshold, defined as the point
  below which a simple greedy "peeling" algorithm can place all items. For d=3, the peeling
  threshold is c ~= 0.818, compared to the true load threshold c*_3 ~= 0.918.
- Below the peeling threshold, O(1) insertion time was already known (Walzer, ESA 2022).
- Between the peeling and load thresholds, insertion is harder but still O(1) expected
  (Bell and Frieze 2024).

### Combined: Bucketized d-ary Thresholds

When each bucket holds b items and there are d hash functions, the effective load threshold
increases further. The combination of bucketization (b > 1) and multiple hash functions
(d >= 3) allows load factors above 99% in practice.

### Phase Transition Behavior

- The transition from "insertion succeeds" to "insertion fails" is **sharp**: there is no
  gradual degradation. At load factor c < c*_d, the probability of failure is o(1). At
  c > c*_d, the probability of failure is 1 - o(1).
- This is analogous to the Erdos-Renyi phase transition for the appearance of a giant
  component in random graphs. The cuckoo graph (bipartite graph with items as edges and
  buckets as vertices) undergoes a structural phase transition at c*_d.
- **For benchmarking**: The sharpness of this transition means that measuring failure
  probability at closely-spaced load factors near the threshold (e.g., increments of 0.01)
  will reveal a clear "cliff" in success rate.

---

## Summary: Recommended Benchmark Metrics

| Metric Category | Key Measurements | Expected Behavior |
|----------------|-----------------|-------------------|
| Insertion failure rate | Fraction of builds needing rehash | O(1/n^s) with stash size s |
| Displacement chain length | Mean, P50, P95, P99, max | O(1) mean below threshold |
| Stash occupancy | Mean, max, overflow rate | Near-zero at moderate load |
| Cache performance | Cache misses/lookup, primary hit rate | 1-2 misses with bucketization |
| Load factor threshold | Max achievable load without rehash | See c*_d table above |
| Phase transition sharpness | Failure rate vs. load factor curve | Sharp cliff at threshold |

---

## Key References

- Pagh, Rodler. "Cuckoo Hashing" (2001). Original cuckoo hashing paper.
  https://www.brics.dk/RS/01/32/BRICS-RS-01-32.pdf
- Kirsch, Mitzenmacher, Wieder. "More Robust Hashing: Cuckoo Hashing with a Stash" (2008).
  Stash analysis with O(1/n^s) failure bounds.
  https://www.eecs.harvard.edu/~michaelm/postscripts/esa2008full.pdf
- Kirsch, Mitzenmacher. "Using a Queue to De-amortize Cuckoo Hashing in Hardware" (2007).
- Li, Andersen, Kaminsky, Freedman. "Algorithmic Improvements for Fast Concurrent Cuckoo
  Hashing" (EuroSys 2014). Practical bucketized implementation with 4-way buckets.
  https://www.cs.princeton.edu/~mfreed/docs/cuckoo-eurosys14.pdf
- Fan, Andersen, Kaminsky, Mitzenmacher. Cuckoo Filter paper (uses bucketized cuckoo hashing).
- Bell, Frieze. "O(1) Insertion for Random Walk d-ary Cuckoo Hashing up to the Load
  Threshold" (FOCS 2024). Proves O(1) insertion up to c*_d for d >= 3.
  https://arxiv.org/abs/2401.14394
- Frieze, Melsted, Mitzenmacher. "An Analysis of Random-Walk Cuckoo Hashing" (2009).
  Polylogarithmic insertion time for d >= 8.
  https://www.eecs.harvard.edu/~michaelm/postscripts/RANDOM-CUCKOO.pdf
- Zhou et al. "Generalized Cuckoo Hashing with a Stash, Revisited" (2022). Tighter bound
  O(n^{-d-s}) on stash overflow.
  https://arxiv.org/abs/2010.01890
- Sun et al. "Cuckoo++ Hash Tables: High-Performance Hash Tables for Networking Applications"
  (2017). Cache-optimized bucketized design.
  https://arxiv.org/pdf/1712.09624
