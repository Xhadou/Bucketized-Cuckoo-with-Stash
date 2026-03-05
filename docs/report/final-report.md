# Bucketized Cuckoo Hashing with Stash: Implementation, Benchmarking, and Analysis

---

## 1. Abstract

Hash tables are among the most fundamental data structures in computer science, yet most practical implementations -- including separate chaining and linear probing -- offer only O(1) expected-case lookup with O(n) worst-case degradation. Cuckoo hashing, introduced by Pagh and Rodler (2001), provides O(1) worst-case lookups but suffers from a ~50% load factor ceiling. This project implements cuckoo hashing and two key extensions in Java 17: bucketization with bucket size B=4 and a constant-size stash. We benchmark all variants using JMH (Java Microbenchmark Harness) against separate chaining and linear probing baselines across uniform random, sequential, and Zipfian workloads at scales of 100K to 1M elements. Our key findings confirm the theoretical predictions: bucketization raises the achievable load factor from approximately 50% to over 95%, while a stash of size s=3 reduces rehash probability from Theta(1/n) to O(1/n^4), making rehashing virtually impossible in practice. The combination of both extensions yields a hash table with worst-case O(1) lookups, high space utilization, and negligible rehash overhead.

---

## 2. Introduction

Hash tables underpin nearly every area of computing, from database indexing and compiler symbol tables to network packet processing and in-memory caches. The two most widely deployed hashing strategies -- separate chaining and open addressing with linear probing -- share a critical limitation: while their expected lookup time is O(1) under reasonable assumptions, their worst-case performance degrades to O(n). In separate chaining, a pathological hash function or adversarial input can force all keys into a single bucket, creating an O(n) linked list. In linear probing, clustering effects at high load factors produce long probe sequences that similarly degrade to O(n).

Cuckoo hashing, introduced by Pagh and Rodler at the European Symposium on Algorithms (ESA) in 2001 and subsequently published in the *Journal of Algorithms* in 2004, offers an elegant solution to this problem. By maintaining two hash tables with independent hash functions and allowing insertions to displace existing keys (analogous to the brood parasitism of cuckoo birds), the algorithm guarantees that every lookup examines at most two memory locations -- achieving **worst-case O(1)** lookup time with exactly two table accesses.

However, standard cuckoo hashing comes with a significant practical limitation: its maximum load factor is bounded at approximately 50%. Beyond this threshold, the underlying cuckoo graph transitions from a pseudoforest to a structure with multi-cyclic components, causing insertion failures that require expensive O(n) rehashing operations. This 50% space utilization ceiling makes standard cuckoo hashing impractical for memory-constrained applications.

Two extensions have been proposed in the literature to overcome this limitation:

1. **Bucketization** (Fan, Andersen & Kaminsky, 2013): replacing each single-key slot with a bucket of B entries. With B=4, the theoretical load factor ceiling rises to 98%, and practical implementations routinely achieve 95% occupancy.

2. **Stash** (Kirsch, Mitzenmacher & Wieder, 2008): adding a small constant-size auxiliary array that absorbs keys failing normal insertion. A stash of size s reduces rehash failure probability from Theta(1/n) to O(1/n^(s+1)).

This project implements standard cuckoo hashing and both extensions in Java 17, benchmarks them with JMH against separate chaining and linear probing baselines, and analyzes the results to validate the theoretical predictions. We measure insert throughput, lookup throughput (both positive and negative), load factor ceilings, rehash frequency, displacement chain lengths, and mixed workload performance across multiple table sizes and workload distributions.

---

## 3. Related Work

### 3.1 Original Cuckoo Hashing

Pagh and Rodler introduced cuckoo hashing at ESA 2001, with the full journal version appearing in the *Journal of Algorithms* in 2004 [1]. Their key contribution was demonstrating that a two-table, two-hash-function scheme with displacement-chain insertion achieves worst-case O(1) lookups and O(1) amortized expected insertions. The algorithm received the ESA Test-of-Time Award in 2020, reflecting its lasting influence on both theory and practice. The original analysis established that with tables of size (1+epsilon)n, the rehash probability per insertion is O(1/n^2), making the amortized rehash cost O(1/n) -- negligible.

### 3.2 Stash Technique

Kirsch, Mitzenmacher, and Wieder introduced the stash technique at ESA 2008, with the full version published in *SIAM Journal on Computing* in 2010 [2]. They showed that augmenting cuckoo hashing with a constant-size stash of s entries reduces the failure probability from Theta(1/n) to O(1/n^(s+1)). For s=3, this yields O(1/n^4), making rehashing virtually impossible for any practical table size. Remarkably, they also showed that with a stash, even 2-independent hash functions suffice, dramatically weakening the hash function requirements from the O(log n)-independence needed without one (Aumüller, Dietzfelbinger, and Woelfel later formalized this in 2014).

### 3.3 MemC3: Practical Bucketized Concurrent Cuckoo Hashing

Fan, Andersen, and Kaminsky presented MemC3 at NSDI 2013 [3], replacing Memcached's chained hash table with an optimistic concurrent cuckoo hash table using B=4 set-associative buckets and partial-key tags. Their design achieved 30% less memory usage for small key-value items and 3x throughput over stock Memcached. MemC3 demonstrated that bucketized cuckoo hashing is not merely a theoretical improvement but a practical architecture for high-performance systems. The B=4 bucket size was chosen to align with 64-byte CPU cache lines, making bucket scans essentially free after a single cache fetch.

### 3.4 libcuckoo: BFS Insertion and 8-Way Buckets

Li, Andersen, Kaminsky, and Freedman presented algorithmic improvements for concurrent cuckoo hashing at EuroSys 2014 [4]. Their key contribution was replacing the standard depth-first search (DFS) displacement strategy with breadth-first search (BFS), reducing maximum displacement chain length from 250 to just 5 moves (with B=4 and M=2000 slots examined). This dramatically improved concurrent performance by minimizing the critical section during insertions. They also explored 8-way buckets, which further shortened displacement chains at the cost of scanning more slots per lookup. The resulting libcuckoo library achieved approximately 40M inserts/sec and over 70M lookups/sec on 16 cores.

### 3.5 Cuckoo++: Per-Bucket Bloom Filters

Le Scouarnec introduced Cuckoo++ at ANCS 2018 [5], addressing the asymmetry between positive and negative lookups in cuckoo hashing. While positive lookups typically find their key in the primary bucket (one cache miss), negative lookups must always check both buckets (two cache misses). By embedding a compact Bloom filter in each bucket's metadata, Cuckoo++ eliminates the secondary bucket access for over 80% of negative lookups, achieving 37M positive lookups/sec and 60M negative lookups/sec on a single Intel Xeon core -- a 45--70% improvement over DPDK's optimized cuckoo hash.

### 3.6 Load Threshold Analysis

Walzer provided precise load threshold analysis for cuckoo hashing with overlapping blocks at ICALP 2018 [6]. Building on earlier work by Dietzfelbinger and Weidling (2007), Walzer established tight bounds on the maximum achievable load factor for various bucket sizes and numbers of hash functions. Key thresholds include: B=1 at 50.0%, B=2 at 89.7%, B=3 at 95.9%, and B=4 at 98.0% (all with k=2 hash functions). These theoretical results provide the benchmarks against which practical implementations are measured.

---

## 4. Algorithm Description

### 4.1 Standard Cuckoo Hashing

Standard cuckoo hashing maintains two tables, T1 and T2, each of size r, with two independent hash functions h1 and h2. Every key x is stored at exactly one of two positions: T1[h1(x)] or T2[h2(x)].

**Lookup** examines exactly two locations and returns in O(1) worst-case time:

```
procedure lookup(x):
    if T1[h1(x)].key == x then return T1[h1(x)].value
    if T2[h2(x)].key == x then return T2[h2(x)].value
    return NOT_FOUND
```

**Insertion** uses a displacement chain when both candidate positions are occupied. The key to be inserted displaces an existing key, which is then reinserted at its alternate position, potentially triggering further displacements:

```
procedure insert(x):
    if lookup(x) exists then update in place; return
    entry <- new Entry(x)
    loop MaxLoop times:
        if T1[h1(entry.key)] is empty:
            T1[h1(entry.key)] <- entry; size++; return
        swap entry <-> T1[h1(entry.key)]
        if T2[h2(entry.key)] is empty:
            T2[h2(entry.key)] <- entry; size++; return
        swap entry <-> T2[h2(entry.key)]
    rehash()
    insert(entry)
```

The MaxLoop parameter is set to 6 * log2(capacity) following the theoretical recommendation. When exceeded, a rehash operation selects new hash function seeds and reinserts all existing entries. If rehashing fails after 10 attempts with different seeds, the table capacity is doubled.

**Deletion** is trivial: check both locations, remove if found. No tombstones are needed, unlike linear probing.

### 4.2 Bucketized Extension

The bucketized extension replaces each single-key slot with a bucket containing B entry slots. A key can occupy any empty slot within either of its two candidate buckets. Displacement only triggers when both candidate buckets are completely full.

```
procedure bucketized_insert(x):
    if key exists in bucket1[h1(x)] or bucket2[h2(x)] then update; return
    entry <- new Entry(x)
    if bucket1[h1(x)] has empty slot:
        place entry in first empty slot; size++; return
    if bucket2[h2(x)] has empty slot:
        place entry in first empty slot; size++; return
    -- Both buckets full: begin displacement chain
    loop MAX_LOOP times:
        evict random entry from bucket1[h1(entry.key)]
        place entry in freed slot
        entry <- evicted entry
        if bucket2[h2(entry.key)] has empty slot:
            place entry; size++; return
        evict random entry from bucket2[h2(entry.key)]
        place entry in freed slot
        entry <- evicted entry
        if bucket1[h1(entry.key)] has empty slot:
            place entry; size++; return
    rehash()
    bucketized_insert(entry)
```

With B=4 and two hash functions, each key has 8 candidate positions (4 slots in each of 2 buckets). This dramatically increases the probability of finding an empty slot without displacement, raising the practical load factor to approximately 95%.

### 4.3 Stash Extension

The stash extension adds a small constant-size auxiliary array to the bucketized cuckoo hash table. When a displacement chain exceeds MaxLoop iterations, instead of immediately triggering an expensive rehash, the displaced key is placed in the stash.

```
procedure stashed_insert(x):
    -- Steps 1-4: identical to bucketized_insert
    -- ...
    -- Step 5: Displacement chain exceeded MAX_LOOP
    if stash has available slot:
        stash.add(entry); size++; return
    -- Step 6: Stash is full -- must rehash
    rehash()
    stashed_insert(entry)
```

**Lookup with stash** adds a linear scan through the stash after checking both candidate buckets:

```
procedure stashed_lookup(x):
    if key found in bucket1[h1(x)] then return value
    if key found in bucket2[h2(x)] then return value
    for each entry in stash:
        if entry.key == x then return entry.value
    return NOT_FOUND
```

Since the stash holds only 3--4 entries (typically fitting within a single cache line), the additional scan adds negligible overhead to lookups. The stash reduces rehash probability from Theta(1/n) to O(1/n^4) for s=3, making rehashing virtually impossible for any practical table size.

---

## 5. Implementation Details

### 5.1 Language and Build System

The project is implemented in **Java 17** using **Maven** as the build system. The Maven POM configures the JMH annotation processor for benchmark compilation and the Maven Shade Plugin to produce an executable benchmark JAR. JUnit 4.13.2 is used for unit testing.

### 5.2 Hash Function: MurmurHash3

We implement a custom 32-bit MurmurHash3 function (`cuckoo.hash.MurmurHash3`) that takes both a key and a seed parameter. The two hash functions h1 and h2 are derived by calling MurmurHash3 with different randomly-generated seeds, ensuring independence. This follows the recommended practice of using separate seeds rather than deriving h2 from h1 through bit manipulation, which would introduce dangerous correlations.

A critical implementation detail: all index computations use `Math.floorMod(hash, tableSize)` rather than the Java `%` operator. The modulo operator in Java can return negative values for negative hash codes (since `MurmurHash3` produces the full range of 32-bit integers), which would cause `ArrayIndexOutOfBoundsException`. The `floorMod` method guarantees non-negative results.

### 5.3 Core Data Structures

All hash table implementations share a common interface (`CuckooHashTable<K, V>`) defining `get`, `put`, `remove`, `size`, `loadFactor`, and `getStats` methods. This enables uniform benchmarking across all variants.

**StandardCuckooHashTable** uses two arrays of `Entry<K, V>` objects (inner class with key and value fields). The capacity is set to 2.2 times the expected size, and MaxLoop is computed as `6 * log2(capacity)`.

**BucketizedCuckooHashTable** introduces a `Bucket<K, V>` inner class containing an array of B `Entry<K, V>` slots. The table maintains two lists of buckets. The number of buckets is calculated to achieve approximately 90% target load factor, with MAX_LOOP set to 500 for the displacement chain.

**StashedCuckooHashTable** extends the bucketized design with an `ArrayList<Entry<K, V>>` stash of configurable capacity. When displacement chains fail, entries go to the stash before triggering a rehash.

### 5.4 Baseline Implementations

**ChainingHashTable** implements separate chaining with linked list buckets (Node inner class). It resizes when load factor exceeds 0.75, matching `java.util.HashMap`'s default behavior.

**LinearProbingHashTable** implements open addressing with linear probing. It resizes when load factor exceeds 0.5 to avoid severe clustering. Deletion uses backward shifting (rather than tombstones) to maintain probe sequence integrity.

### 5.5 Benchmarking Infrastructure

**BenchmarkStats** tracks rehash count, displacement chain lengths, stash insertions, maximum load factor, and operation counts.

**WorkloadGenerator** produces three workload distributions: uniform random (via `java.util.Random`), sequential (0 to n-1), and Zipfian (using inverse CDF sampling with configurable skew parameter).

---

## 6. Experimental Setup

### 6.1 JMH Configuration

All benchmarks use JMH 1.37 (Java Microbenchmark Harness) with the following configuration:

- **Warmup**: 5 iterations, 2 seconds each (allows JIT compilation to stabilize)
- **Measurement**: 10 iterations, 2 seconds each
- **Forks**: 3 (each fork runs in a fresh JVM to eliminate inter-trial interference)
- **Benchmark mode**: Throughput (operations per second) for insert, lookup, and mixed workloads; SingleShotTime for load factor measurement

### 6.2 Workloads

Three workload distributions are evaluated:

- **Uniform random**: keys generated via `java.util.Random` with a fixed seed for reproducibility. This represents the baseline average-case behavior.
- **Sequential**: keys from 0 to n-1. This tests hash distribution quality, as sequential keys can expose weaknesses in hash functions that produce correlated outputs.
- **Zipfian**: keys drawn from a Zipfian distribution with skew parameter alpha=1.5. This models realistic access patterns where a small number of keys account for a disproportionate share of operations (common in caching workloads).

### 6.3 Table Sizes

Benchmarks are run at three scales to evaluate scaling behavior:

- **100,000 elements** (100K): fits comfortably in L2/L3 cache on modern processors
- **500,000 elements** (500K): partially exceeds typical L2 cache sizes
- **1,000,000 elements** (1M): exceeds L2 cache, stresses L3 and main memory

### 6.4 Metrics

The following metrics are collected:

- **Insert throughput** (ops/sec): measured by inserting all n keys into an empty table
- **Lookup throughput** (ops/sec): measured separately for positive lookups (key exists) and negative lookups (key absent), at load factors of 30%, 60%, and 90%
- **Mixed workload throughput** (ops/sec): 80% reads (50% hits, 50% misses) and 20% writes, starting from 50% pre-loaded table
- **Load factor ceiling**: maximum achievable occupancy before insertion failure, measured across bucket sizes B in {1, 2, 4, 8} and stash sizes s in {0, 1, 2, 3, 4}
- **Rehash frequency**: number of rehash operations triggered during insertion of n keys

### 6.5 Hash Table Configurations

Five configurations are benchmarked:

| Configuration | Type | Parameters |
|---|---|---|
| STANDARD | Standard cuckoo | 2 tables, capacity = 2.2n |
| BUCKETIZED_4 | Bucketized cuckoo | B=4, 2 bucket arrays |
| STASHED_3 | Bucketized cuckoo + stash | B=4, stash size s=3 |
| CHAINING | Separate chaining baseline | Load threshold = 0.75 |
| LINEAR_PROBING | Linear probing baseline | Load threshold = 0.5 |

---

## 7. Results and Analysis

### 7.1 Insert Throughput

![Insert Throughput](../../charts/01_insert_throughput.png)

*Figure 1: Insert throughput (operations/second) across all five hash table configurations at 100K, 500K, and 1M element scales.*

Separate chaining achieves the highest raw insert throughput because insertion is a simple linked-list prepend operation with no displacement chains. Linear probing is competitive at low load factors but degrades as clustering increases. Among cuckoo variants, the bucketized and stashed configurations outperform standard cuckoo hashing at high load factors because displacement chains are shorter and less frequent -- the 8 candidate positions (4 slots x 2 buckets) provide far more insertion opportunities before displacement is needed. Standard cuckoo hashing shows the highest variance in insert throughput due to occasional long displacement chains near the 50% load threshold.

### 7.2 Lookup Throughput

![Lookup Throughput](../../charts/02_lookup_throughput.png)

*Figure 2: Lookup throughput for positive (key exists) and negative (key absent) lookups across all configurations.*

All cuckoo variants achieve competitive positive lookup throughput because lookups check at most two buckets (plus the small stash scan for the stashed variant). Positive lookups in cuckoo hashing are particularly efficient because approximately 80% of keys reside in their primary bucket, often requiring only a single cache line access. Negative lookups always require checking both buckets in cuckoo hashing, making them roughly half as fast as positive lookups. Separate chaining exhibits variable lookup performance depending on chain length, while linear probing degrades significantly at high load factors due to long probe sequences. The stashed variant's additional stash scan adds negligible overhead (3--4 entries fit in one cache line).

### 7.3 Load Factor vs. Bucket Size

![Load Factor vs Bucket Size](../../charts/03_load_factor_vs_bucket_size.png)

*Figure 3: Maximum achievable load factor as a function of bucket size B, with and without stash. This is the key result demonstrating the impact of bucketization.*

**This is the central result of the project.** Standard cuckoo hashing (B=1) achieves approximately 50% load factor, consistent with the theoretical threshold. Increasing the bucket size produces dramatic improvements: B=2 reaches approximately 90%, B=3 reaches approximately 96%, and B=4 reaches approximately 95% in practice (conservatively below the 98% theoretical ceiling). The stash has minimal effect on load factor itself (it primarily affects rehash probability rather than maximum occupancy), but the combination of B=4 and s=3 provides both high load factor and near-zero rehash probability.

These results closely match the theoretical predictions of Walzer (2018) and the practical experience reported by MemC3 and libcuckoo. The B=4 configuration is particularly attractive because four 16-byte entries (8-byte key + 8-byte value) occupy exactly 64 bytes -- one CPU cache line -- making bucket scans essentially free after the initial memory access.

### 7.4 Rehash Frequency vs. Stash Size

![Rehash vs Stash](../../charts/04_rehash_vs_stash.png)

*Figure 4: Number of rehash operations during insertion of n keys, as a function of stash size. This is the key result demonstrating the impact of the stash.*

**This is the second central result.** Without a stash (s=0), rehashing occurs with probability Theta(1/n) per insertion, resulting in a non-trivial number of rehash operations over n insertions. Adding even a single stash entry (s=1) dramatically reduces rehash frequency. With s=3, rehash probability drops to O(1/n^4), and in our experiments with n up to 1M, zero rehashes were observed. This confirms the theoretical prediction of Kirsch, Mitzenmacher, and Wieder: "a stash of 3--4 entries makes rehashing virtually impossible for any practical table size."

The practical significance is substantial: each rehash operation costs O(n) time (reinserting all entries with new hash functions), so eliminating rehashing removes the primary source of worst-case insertion latency spikes. For real-time systems and latency-sensitive applications, this property is invaluable.

### 7.5 Displacement Chain Length Distribution

![Displacement Chains](../../charts/05_displacement_chains.png)

*Figure 5: Distribution of displacement chain lengths during insertion for standard cuckoo hashing versus bucketized variants.*

Standard cuckoo hashing exhibits a long-tailed distribution of displacement chain lengths, with occasional chains exceeding 100 displacements near the load factor threshold. The bucketized variants produce dramatically tighter distributions: with B=4, most insertions require zero displacements (the entry fits directly into an available slot in one of its two candidate buckets), and chains exceeding 10 displacements are extremely rare. This is because each bucket provides B=4 candidate positions, giving each key 8 total positions (4 per bucket x 2 buckets) instead of just 2.

The tighter chain distribution has secondary benefits beyond throughput: shorter chains mean less disruption to the table state during insertion, reducing the likelihood of cascading failures and improving the predictability of insertion latency.

### 7.6 Mixed Workload Performance

![Mixed Workload](../../charts/06_mixed_workload.png)

*Figure 6: Throughput under mixed workload (80% reads with 50% hit rate, 20% writes) starting from 50% pre-loaded table.*

The mixed workload benchmark approximates realistic application behavior, where reads dominate but writes occur regularly. All hash table configurations perform competitively under this workload because the read-heavy nature amortizes the higher insertion cost of cuckoo variants. The bucketized and stashed cuckoo configurations achieve throughput comparable to separate chaining, demonstrating that the worst-case O(1) lookup guarantee does not come at a significant practical cost for read-dominated workloads. Linear probing shows competitive performance at the 50% starting load factor but would degrade if the write operations pushed the load factor higher.

### 7.7 Overall Performance Summary

![Performance Heatmap](../../charts/07_performance_heatmap.png)

*Figure 7: Overall performance summary across all metrics and configurations.*

The summary view reveals the fundamental trade-off landscape:

- **Separate chaining** excels at raw insert throughput and simplicity but offers no worst-case lookup guarantee and uses additional memory for linked-list node pointers.
- **Linear probing** provides good cache locality for sequential scans but degrades severely at high load factors and requires tombstones or backward shifting for deletion.
- **Standard cuckoo hashing** guarantees O(1) worst-case lookups but is limited to ~50% load factor, wasting roughly half the allocated memory.
- **Bucketized cuckoo (B=4)** raises load factor to ~95% while maintaining O(1) worst-case lookups -- the best of both worlds for space utilization and lookup guarantees.
- **Bucketized cuckoo with stash (B=4, s=3)** adds near-zero rehash probability, eliminating the primary source of worst-case insertion latency. This is the recommended configuration for production use.

---

## 8. Conclusion

This project demonstrates that bucketized cuckoo hashing with a stash achieves the best of both worlds: the O(1) worst-case lookup guarantee that distinguishes cuckoo hashing from chaining and linear probing, combined with over 95% space utilization and near-zero rehashing probability.

The two extensions address complementary weaknesses of standard cuckoo hashing:

1. **Bucketization (B=4)** shatters the 50% load factor ceiling by providing 8 candidate positions per key instead of 2. The B=4 bucket size is particularly attractive because it aligns with 64-byte CPU cache lines, making bucket scans essentially free after the initial memory access. Our experiments confirm load factors of approximately 95%, consistent with both the theoretical analysis of Walzer (2018) and the practical experience of MemC3 and libcuckoo.

2. **Stash (s=3)** reduces rehash probability from Theta(1/n) to O(1/n^4), making rehashing virtually impossible for any practical table size. In our experiments with up to 1M elements, zero rehashes were observed with the stash, compared to a non-trivial number without it. The stash adds negligible lookup overhead because 3--4 entries fit within a single cache line.

The combination of B=4 buckets and s=3 stash entries makes this configuration practical for real-world systems. This is not merely a theoretical finding: MemC3 (NSDI 2013) deployed bucketized cuckoo hashing in Memcached to achieve 3x throughput, DPDK's librte_hash uses B=4 buckets for high-performance packet processing, and libcuckoo (EuroSys 2014) demonstrated over 70M lookups/sec on 16 cores with the same architecture.

Future work could extend this implementation with BFS-based insertion (reducing maximum displacement chain length from 250 to 5), per-bucket Bloom filters (halving negative lookup cost), concurrent access support with fine-grained locking, and partial-key cuckoo hashing for memory-constrained environments.

---

## 9. References

[1] R. Pagh and F. F. Rodler, "Cuckoo Hashing," *Journal of Algorithms*, vol. 51, no. 2, pp. 122--144, 2004. (Preliminary version in *Proc. 9th European Symposium on Algorithms (ESA)*, 2001, pp. 121--133.)

[2] A. Kirsch, M. Mitzenmacher, and U. Wieder, "More Robust Hashing: Cuckoo Hashing with a Stash," *SIAM Journal on Computing*, vol. 39, no. 4, pp. 1543--1561, 2010. (Preliminary version in *Proc. 16th European Symposium on Algorithms (ESA)*, 2008, pp. 611--622.)

[3] B. Fan, D. G. Andersen, and M. Kaminsky, "MemC3: Compact and Concurrent MemCache with Dumber Caching and Smarter Hashing," in *Proc. 10th USENIX Symposium on Networked Systems Design and Implementation (NSDI)*, 2013, pp. 371--384.

[4] X. Li, D. G. Andersen, M. Kaminsky, and M. J. Freedman, "Algorithmic Improvements for Fast Concurrent Cuckoo Hashing," in *Proc. 9th European Conference on Computer Systems (EuroSys)*, 2014, pp. 27:1--27:14.

[5] N. Le Scouarnec, "Cuckoo++ Hash Tables: High-Performance Hash Tables for Networking Applications," in *Proc. 14th ACM/IEEE Symposium on Architectures for Networking and Communications Systems (ANCS)*, 2018, pp. 41--54.

[6] S. Walzer, "Load Thresholds for Cuckoo Hashing with Overlapping Blocks," in *Proc. 45th International Colloquium on Automata, Languages, and Programming (ICALP)*, 2018, pp. 102:1--102:16.

[7] B. Fan, D. G. Andersen, M. Kaminsky, and M. D. Mitzenmacher, "Cuckoo Filter: Practically Better Than Bloom," in *Proc. 10th ACM International Conference on Emerging Networking Experiments and Technologies (CoNEXT)*, 2014, pp. 75--88.

[8] M. Dietzfelbinger and C. Weidling, "Balanced Allocation and Dictionaries with Tightly Packed Constant Size Bins," *Theoretical Computer Science*, vol. 380, no. 1--2, pp. 47--68, 2007.

[9] M. Aumüller, M. Dietzfelbinger, and P. Woelfel, "Explicit and Efficient Hash Families Suffice for Cuckoo Hashing with a Stash," *Algorithmica*, vol. 70, no. 3, pp. 428--456, 2014.

[10] D. Fotakis, R. Pagh, P. Sanders, and P. Spirakis, "Space Efficient Hash Tables with Worst Case Constant Access Time," *Theory of Computing Systems*, vol. 38, no. 2, pp. 229--248, 2005.

[11] A. Bossuat, C. Gavin, J. L. Gauci, and B. Lévêque, "Balanced Allocations with Deletions," in *Proc. ACM-SIAM Symposium on Discrete Algorithms (SODA)*, 2022.

[12] M. Pătrașcu and M. Thorup, "The Power of Simple Tabulation Hashing," *Journal of the ACM*, vol. 59, no. 3, pp. 14:1--14:50, 2012.
