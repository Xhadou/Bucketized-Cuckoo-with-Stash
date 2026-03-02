# Cuckoo hashing: a complete implementation guide for CSD 482

**Cuckoo hashing delivers worst-case O(1) lookups by giving every key exactly two possible homes — and evicting incumbents like its namesake bird.** Originally proposed by Pagh and Rodler in 2001, the algorithm's elegant simplicity masks surprising depth: its load factor ceiling of ~50% can be shattered to 95%+ through bucketization, its rehash probability of Θ(1/n) can be crushed to O(1/n⁴) with a tiny stash, and per-bucket Bloom filters can halve cache misses on negative lookups. This report covers every dimension needed to implement standard cuckoo hashing and its extensions end-to-end, including algorithmic mechanics, theoretical guarantees, benchmarking methodology, language selection, and practical pitfalls.

---

## 1. The original algorithm: two tables, two hash functions, constant-time everything

Cuckoo hashing (Pagh & Rodler, *Journal of Algorithms* 51(2):122–144, 2004; ESA Test-of-Time Award 2020) uses **two hash tables** T1 and T2, each of size r, with independent hash functions h1 and h2. Every key x resides at exactly one of two locations: T1[h1(x)] or T2[h2(x)].

**Lookup** checks exactly two memory locations and returns — **worst-case O(1)** with precisely 2 table accesses, which can be issued in parallel. **Deletion** is equally trivial: check both locations, remove if found. No tombstones needed (unlike linear probing).

**Insertion** is the algorithm's heart. To insert key x: try T1[h1(x)]. If empty, place x there. If occupied by key y, *evict* y (swap it with x), then try to place y at T2[h2(y)]. If that slot is also occupied, evict again, alternating between tables. This displacement chain continues until an empty slot is found or a maximum iteration count (MaxLoop) is reached:

```
procedure insert(x):
    if lookup(x) then return
    loop MaxLoop times:
        if T1[h1(x)] is empty: T1[h1(x)] ← x; return
        swap x ↔ T1[h1(x)]
        if T2[h2(x)] is empty: T2[h2(x)] ← x; return
        swap x ↔ T2[h2(x)]
    rehash(); insert(x)
```

The recommended cap is **MaxLoop = ⌈6 · log₁₊δ/₂(|T|)⌉**, yielding a rehash probability of O(1/n²) per insertion. Since rehashing costs O(n), the amortized rehash contribution per insertion is O(1/n) — negligible.

**The cuckoo graph** provides the theoretical backbone. Vertices represent table slots; edges represent keys (each key x creates edge {h1(x), h2(x)}). Insertion succeeds if and only if the cuckoo graph is a pseudoforest — at most one cycle per connected component. The critical threshold occurs at **load factor 1/2**: below this, the graph is sparse and cycles are rare; above it, a giant multi-cyclic component forms with high probability. This is why standard cuckoo hashing is limited to roughly **50% load factor** (each table has r ≥ (1+ε)n slots for n keys, giving ~3 words per key overhead at load 1/3).

### Theoretical guarantees at a glance

| Operation | Complexity |
|-----------|-----------|
| Lookup | O(1) worst-case (exactly 2 accesses) |
| Delete | O(1) worst-case |
| Insert | O(1) amortized expected |
| Rehash probability per insert | O(1/n²) |
| Space | 2(1+ε)n slots total |

---

## 2. Bucketized cuckoo hashing shatters the 50% barrier

The single most impactful extension replaces each single-key slot with a **bucket of B keys**. Instead of each hash position holding one item, it holds B. A key can occupy any empty slot within either candidate bucket — eviction only triggers when both B-slot buckets are completely full.

### Load factor thresholds by configuration

These thresholds represent the theoretical maximum occupancy (from Walzer, ICALP 2018; Dietzfelbinger & Weidling, 2007):

| Bucket size B | k=2 hash functions | k=3 hash functions |
|---|---|---|
| B=1 (standard) | **50.0%** | 91.8% |
| B=2 | **89.7%** | 98.8% |
| B=3 | **95.9%** | 99.7% |
| B=4 | **98.0%** | 99.9% |

With overlapping windows (unaligned blocks), B=2 with k=2 reaches **96.5%**. In practice, the MemC3 and libcuckoo implementations achieve **95% occupancy** with B=4 and 2 hash functions, operating conservatively below the 98% theoretical ceiling.

### Why B=4 is the sweet spot

Modern CPUs use **64-byte cache lines**. With B=4 slots per bucket and 16-byte entries (8-byte key + 8-byte value), one bucket = 4 × 16 = 64 bytes = exactly one cache line. Scanning all 4 entries in a bucket after a single cache fetch is essentially free compared to the cost of the memory access itself. Approximately **80% of keys reside in their primary bucket**, so most positive lookups require just one cache line read.

For larger slot counts, the EuroSys 2014 paper found **B=8** optimal overall — 8 keys fit in one cache line if only keys (8 bytes each) are stored inline, with values accessed separately. B=8 provides even shorter displacement chains and >95% load in practice, at the cost of scanning more slots per lookup.

**Tag-based optimization** (from MemC3): store a 1-byte fingerprint per slot in bucket metadata. Compare all B tags via SIMD before fetching full keys. This eliminates most unnecessary key comparisons and is standard in production implementations.

---

## 3. A tiny stash eliminates rehashing almost entirely

Kirsch, Mitzenmacher, and Wieder (ESA 2008; *SIAM J. Computing* 39(4):1543–1561, 2010) introduced an elegant extension: a **small constant-sized auxiliary array** (the "stash") that absorbs keys failing normal insertion. Instead of triggering an expensive O(n) rehash, the problematic key simply goes into the stash.

**Lookup with stash**: check T1[h1(x)], check T2[h2(x)], then linearly scan the stash. Since the stash holds only 3–4 entries (fits in a single cache line), this adds negligible overhead.

### Rehash probability drops polynomially with stash size

| Stash size s | Failure probability | For n = 10⁶ |
|---|---|---|
| 0 (no stash) | Θ(1/n) | ~10⁻⁶ |
| 1 | O(1/n²) | ~10⁻¹² |
| 2 | O(1/n³) | ~10⁻¹⁸ |
| 3 | O(1/n⁴) | ~10⁻²⁴ |
| 4 | O(1/n⁵) | ~10⁻³⁰ |

**A stash of 3–4 entries makes rehashing virtually impossible** for any practical table size. Kirsch et al. describe this as a "tremendous improvement, enhancing cuckoo hashing's practical viability in both hardware and software."

When combining buckets of size d with a stash of size s, the corrected tight bound (Bossuat et al., 2022) is **Θ(n⁻ᵈ⁻ˢ)**. The combination of B=4 buckets and s=3 stash gives both high load factor (~95%) and negligible rehash probability.

A remarkable theoretical bonus: with a stash, even **2-independent hash functions** suffice (Aumüller, Dietzfelbinger, Woelfel, 2014), dramatically weakening the hash function requirements from the O(log n)-independence needed without one.

---

## 4. Per-bucket Bloom filters cut negative lookup cost in half

Cuckoo++ (Le Scouarnec, ANCS 2018) addresses the fundamental asymmetry in cuckoo hash lookups: positive lookups usually hit the primary bucket (1 cache miss), but **negative lookups always check both buckets** (2 cache misses). For networking workloads with many misses (firewall rule checking, connection tracking), this doubles the cost.

The solution embeds a compact **Bloom filter in each bucket's metadata** that records which keys use this bucket as their secondary home. The lookup becomes:

1. Hash the key, fetch the primary bucket (1 cache line read)
2. Scan primary bucket for the key
3. If found → done (1 memory access)
4. If not found, query the bucket's Bloom filter: "could this key be in the secondary bucket?"
5. If Bloom filter says "definitely not" → return negative (still just 1 memory access)
6. If Bloom filter says "possibly" → fetch secondary bucket (2 memory accesses)

Since only ~20% of entries reside in their secondary bucket, the Bloom filter has low occupancy and **very low false positive rate**, meaning >80% of negative lookups complete with a single memory access.

**Performance results** on Intel Xeon: **37M positive lookups/sec** and **60M negative lookups/sec** on a single core — a 45–70% improvement over DPDK's highly optimized cuckoo hash. On 18 cores, Cuckoo++ achieved **496M lookups/sec** for predominantly-positive workloads. The Bloom filter is stored within the existing bucket cache line, so it adds no extra memory accesses for positive lookups.

---

## 5. BFS dominates DFS for displacement chains, random walk excels for d≥3

Three strategies exist for resolving displacement chains during insertion, with dramatically different characteristics:

**DFS (standard Pagh & Rodler)** follows a single displacement path depth-first. Simple to implement (just a loop with swaps) with O(1) extra space, but can produce very long chains — up to **250 displacements** with B=4 and M=2000 maximum slots examined. This makes concurrent access difficult since the entire chain must be locked.

**BFS** (Li et al., EuroSys 2014) explores all possible placements level-by-level, finding the **shortest possible displacement chain**. With B=4 and M=2000, the maximum BFS path length is just **⌈log₄(751)⌉ = 5 displacements** versus 250 for DFS. The critical insight: the search phase (exploring M slots) runs *outside* any critical section, and only the final 5-move chain executes under lock. This reduced path invalidation probability from 4.28% (DFS) to **1.75×10⁻⁵** (BFS) in concurrent settings, enabling a ~20× throughput improvement with 8 threads.

**Random walk** (Fotakis et al., 2005) randomly selects which key to evict at each step. For d=2 hash functions, random walk is deterministic (each key has exactly 2 locations). For d≥3, it becomes the fastest practical approach: no BFS queue overhead, O(1) extra space, and empirically very fast. Recent theoretical breakthroughs proved O(1) expected insertion time for d≥3 (Walzer, ESA 2022) and d≥4 (Bell & Frieze, FOCS 2024).

| Property | DFS | BFS | Random Walk |
|---|---|---|---|
| Max chain length (B=4, M=2000) | 250 | **5** | Varies |
| Extra space | O(1) | O(M) | O(1) |
| Implementation complexity | Very simple | Complex | Simple |
| Best for | Simple implementations | **Concurrent tables** | d-ary cuckoo (d≥3) |

**Recommendation for the course project**: implement DFS first as your baseline (simplest), then add BFS as an improvement for the write-up. If using d≥3 hash functions, random walk is the natural choice.

---

## 6. Hash function requirements and practical choices

Cuckoo hashing is sensitive to hash function quality. Theoretically, **O(log n)-independence** suffices; at least 6-independence is necessary. In practice, the following work well:

**MurmurHash3 with different seeds** is the recommended default. Use `h1(x) = MurmurHash3(x, seed=42)` and `h2(x) = MurmurHash3(x, seed=137)`. Different seeds produce effectively independent functions. Alternatively, compute a single 128-bit hash and split it into two 64-bit halves.

**Tabulation hashing** is the theoretically strongest practical option — proven sufficient for cuckoo hashing by Pătrașcu & Thorup (2012) despite being only 3-independent. It works by splitting keys into bytes, looking up random values in precomputed tables, and XORing: `h(x) = T₁[byte₀] ⊕ T₂[byte₁] ⊕ T₃[byte₂] ⊕ T₄[byte₃]`. Very fast (table lookups + XOR) with rigorous guarantees.

**Warning**: never derive h2 from h1 via simple bit manipulation (e.g., `h2(x) = h1(x) XOR (h1(x) >> 16)`). This creates correlated hash values and breaks the independence assumption, leading to frequent failures.

**Language-specific libraries**: In Java, use Google Guava's `Hashing.murmur3_32_fixed(seed)` or `Hashing.murmur3_128(seed)`. In Python, use the `mmh3` package (`pip install mmh3`), which provides `mmh3.hash64(key, seed)` returning two 64-bit values — instant h1/h2 from a single call.

**Critical Java gotcha**: always use `Math.floorMod(hash, tableSize)` rather than `hash % tableSize`. The modulo operator in Java can return negative values for negative hash codes, causing array index errors.

---

## 7. Benchmarking methodology for meaningful results

### Essential metrics to collect

- **Throughput** (ops/sec) for insert, lookup (positive and negative), and delete at various load factors
- **Load factor at failure**: the occupancy when insertion first fails or triggers rehash
- **Rehash count**: total rehashes over N insertions (should be near zero with stash)
- **Displacement chain length distribution**: histogram per insertion — reveals algorithm behavior
- **Memory efficiency**: bytes per stored key-value pair (include empty slots and overhead)
- **Cache miss rate**: use `perf stat -e cache-misses,L1-dcache-load-misses` on Linux

### Benchmarking protocol

Run a proper warm-up phase (10–20% of operations) before measurement — critical for Java's JIT compiler. Execute **at least 5–10 complete trials** and report mean, median, standard deviation, and p95/p99. Pin the process to a specific CPU core (`taskset -c 2`) and disable frequency scaling (`cpupower frequency-set -g performance`) to reduce variance.

### Comparison baselines and workloads

Compare against three baselines: your own chained hash table (for controlled comparison), your own linear-probing table, and the language's built-in map (`java.util.HashMap` at 0.75 load factor, or Python's `dict`). This gives 3–4 curves per chart — ideal for a course presentation.

Generate workloads covering: **uniform random** keys (baseline behavior), **sequential** keys (tests hash distribution quality), **Zipfian/skewed** distribution (realistic access patterns: `np.random.zipf(a=1.5, size=N)`), and **mixed read/write** (80% lookups with 50% hits / 50% misses + 20% inserts).

### Tools by language

For **Java**, use JMH (Java Microbenchmark Harness) — the gold standard. It handles JVM warmup, dead-code elimination, forking, and provides error bars with confidence intervals automatically. Generate the project skeleton via Maven archetype, annotate benchmarks with `@Benchmark`, and use modes like `Throughput` and `SampleTime` (latency distribution). Integrate with perf via JMH's `-prof perf` flag.

For **Python**, use `time.perf_counter_ns()` for high-resolution timing (never `time.time()`). Implement warmup manually. For cache analysis, run the entire script under `perf stat`.

---

## 8. Java vs Python: use Java for benchmarks, Python for charts

| Factor | Java | Python |
|---|---|---|
| Raw speed | **10–50× faster** for tight hash table loops | Interpreter overhead dominates |
| Memory layout | Primitive `int[]` arrays, predictable | 28+ bytes per Python int object |
| Benchmarking tools | **JMH** (gold standard) | `timeit`, manual statistics |
| Cache observability | JMH + perf integration | External only |
| Visualization | Weak (needs export) | **matplotlib/seaborn** (excellent) |
| Development speed | More boilerplate | Faster prototyping |

**The recommended strategy**: implement the hash tables and run benchmarks in **Java** using JMH, then export results as CSV and generate publication-quality charts in **Python** with matplotlib/seaborn/pandas. This combination produces the most meaningful benchmark results (Java's JIT gives realistic performance characteristics) while enabling excellent academic presentation.

If forced to choose one language: **Java** if the course emphasizes performance analysis; **Python** if concepts matter more than raw speed. If using Python exclusively, use `mmh3` (C extension) for hashing, `numpy` arrays for table storage, and compare against the built-in `dict` — explain that your cuckoo implementation will be slower in absolute terms because `dict` is highly optimized C code, but emphasize the **worst-case O(1) lookup guarantee** that `dict` lacks.

---

## 9. Real-world systems built on cuckoo hashing

Cuckoo hashing has seen remarkable adoption in high-performance systems, largely driven by the CMU "Efficient" group (Bin Fan, David G. Andersen, Michael Kaminsky, and collaborators):

**MemC3** (NSDI 2013) replaced Memcached's chained hash table with optimistic concurrent cuckoo hashing using B=4 set-associative buckets and partial-key tags. Results: **30% less memory** for small key-value pairs and **3× throughput** over stock Memcached. The follow-up **libcuckoo** (EuroSys 2014) extended this to multiple concurrent writers, achieving ~40M inserts/sec and >70M lookups/sec on 16 cores.

**SILT** (SOSP 2011) uses partial-key cuckoo hashing as an in-memory index for flash-based key-value storage, storing only 15-bit fingerprints instead of full 160-bit keys. It achieves **0.7 bytes of DRAM per entry** with 93% occupancy and an average of 1.01 flash reads per lookup.

**DPDK's librte_hash** is the standard cuckoo hash implementation for high-performance packet processing (firewalls, NATs, load balancers), using B=4 buckets with BFS-based insertion. **CuckooSwitch** (CoNEXT 2013) built a software Ethernet switch on this foundation, forwarding **92M minimum-sized packets/sec** with a table of one billion entries.

**Cuckoo filters** (CoNEXT 2014) adapt cuckoo hashing into a probabilistic data structure that replaces Bloom filters — supporting deletion, using less space when false positive rate ε < 3%, and requiring only 2 cache misses per lookup versus k for Bloom filters. They are now used in database query optimization, network traffic measurement, and distributed caching.

Other applications include **cryptographic protocols** (Private Set Intersection, Oblivious RAM), TikTok's recommendation system (solving embedding table collisions), and FPGA-based network processing.

---

## 10. Critical pitfalls and edge cases every implementer hits

**Infinite insertion loops** are the most dangerous bug. Always enforce a MaxLoop cap (6·log₂(n) for basic cuckoo, or 500 for bucketized variants). When exceeded, trigger a rehash — and handle rehash failure gracefully by retrying with new hash function seeds up to ~10 times before growing the table.

**Rehash-of-rehash failure**: rehashing itself can fail if new hash functions also produce cycles. Implement a retry loop with fresh random seeds, and fall back to table growth if repeated rehashes fail:

```java
private void rehash() {
    for (int attempt = 0; attempt < 10; attempt++) {
        seed1 = random.nextInt();
        seed2 = random.nextInt();
        if (rebuildWithNewSeeds()) return;
    }
    growTable();  // double capacity, then rehash
}
```

**Negative hash codes in Java**: `hash % tableSize` can return negative values. Always use `Math.floorMod(hash, tableSize)` or mask with `hash & (tableSize - 1)` for power-of-2 tables.

**Table sizing**: power-of-2 sizes enable fast modulo via bitmask (`index = hash & (size - 1)`) but amplify poor hash distribution. With a good hash function (MurmurHash3), power-of-2 is recommended. Growth factor of 2× with geometric doubling gives O(1) amortized resize cost.

**Duplicate key handling**: always check if a key already exists before starting displacement. Otherwise you can insert duplicates, corrupting the table invariant.

**Load factor management**: for basic 2-table cuckoo hashing, keep load ≤ 40–45% (not 50%) to avoid the sharp threshold. For B=4 bucketized, target **90–92%** (not the theoretical 98% maximum) to maintain fast insertions.

---

## 11. Reference implementations and essential reading

### Production-quality code to study

- **libcuckoo** (C++, Apache 2.0): the reference concurrent implementation with B=4 buckets, BFS insertion, partial-key tags, and fine-grained locking — https://github.com/efficient/libcuckoo
- **DPDK librte_hash** (C, BSD): production hash library for networking — https://github.com/DPDK/dpdk (lib/librte_hash/)
- **Cuckoo++** (C, Clear BSD): the Bloom-filter-optimized variant — https://github.com/technicolor-research/cuckoopp
- **CuckooFilter4J** (Java): thread-safe cuckoo filter with Guava-compatible interface — https://github.com/MGunlogson/CuckooFilter4J

### Educational implementations for reference

- **prateek5795/Cuckoo-Hashing** (Java): includes comparison benchmarks against HashMap/HashSet
- **felixzhuologist/cuckoohashing** (Python): clean single-table implementation following Pagh & Rodler
- **efficient/cuckoofilter** (C++): reference cuckoo filter implementation by the original paper authors

### Essential papers (in reading order for the project)

1. Pagh & Rodler, "Cuckoo Hashing," *J. Algorithms* 2004 — the foundation
2. Kirsch, Mitzenmacher & Wieder, "More Robust Hashing: Cuckoo Hashing with a Stash," *SIAM J. Comput.* 2010 — stash technique
3. Fan, Andersen & Kaminsky, "MemC3," *NSDI* 2013 — practical bucketized concurrent cuckoo
4. Li, Andersen, Kaminsky & Freedman, "Algorithmic Improvements for Fast Concurrent Cuckoo Hashing," *EuroSys* 2014 — BFS insertion, 8-way buckets
5. Fan, Andersen, Kaminsky & Mitzenmacher, "Cuckoo Filter: Practically Better Than Bloom," *CoNEXT* 2014 — cuckoo filters
6. Le Scouarnec, "Cuckoo++ Hash Tables," *ANCS* 2018 — Bloom filter optimization
7. Walzer, "Load Thresholds for Cuckoo Hashing with Overlapping Blocks," *ICALP* 2018 — precise threshold analysis

---

## Conclusion: a practical architecture for the project

The strongest implementation strategy combines all three extensions into a single coherent system. Start with **basic 2-table cuckoo hashing** as the baseline (trivial to implement, demonstrates the core displacement mechanism). Then add **B=4 bucketization** to push load from 50% to 95% while aligning with cache lines. Layer on a **stash of size 3–4** to reduce rehash probability to ~O(1/n⁴). Finally, implement **per-bucket Bloom filters** (Cuckoo++ style) to eliminate secondary bucket lookups on misses.

For insertion strategy, implement DFS first for simplicity, then BFS for the improvement narrative. Use MurmurHash3 with different seeds for h1 and h2. Benchmark with JMH in Java across uniform, sequential, and Zipfian workloads at load factors from 10% to 95%, measuring throughput, chain length distribution, and rehash frequency. Compare against `java.util.HashMap` and a simple linear-probing table. Generate charts in Python showing the dramatic impact of each extension on load factor, throughput, and rehash count — these visual before/after comparisons make the strongest academic presentation.