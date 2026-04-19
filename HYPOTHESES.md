# Hypotheses & Experimental Design

This document maps each hypothesis to specific, falsifiable predictions and the benchmarks that validate them.

---

## H1: Bucketization Threshold

**Claim:** Bucketized cuckoo hashing with B=4 achieves a maximum load factor of >= 95%, breaking the 50% wall of standard cuckoo hashing, consistent with theoretical predictions from Erlingsson et al.

| Prediction | Metric | Benchmark |
|-----------|--------|-----------|
| Standard cuckoo (B=1) hits ~50% load factor | Max load factor before rehash | `LoadFactorBenchmark` with `bucketSize=1` |
| B=2 achieves ~80% | Max load factor | `LoadFactorBenchmark` with `bucketSize=2` |
| B=4 achieves >= 95% | Max load factor | `LoadFactorBenchmark` with `bucketSize=4` |
| B=8 achieves >= 98% | Max load factor | `LoadFactorBenchmark` with `bucketSize=8` |

**Falsification:** If B=4 cannot sustain > 90% load factor across 10,000 random trials, H1 is falsified.

---

## H2: Stash Effectiveness

**Claim:** Adding a constant-sized stash of s=3 to bucketized cuckoo hashing (B=4) reduces rehash probability by at least two orders of magnitude compared to the unstashed variant, following the O(1/n^(s+1)) theoretical bound from Kirsch et al.

| Prediction | Metric | Benchmark |
|-----------|--------|-----------|
| B=4, s=0: rehash probability ~ 1/n | Fraction of builds needing rehash | `DirectAnalysis.analyzeRehashVsStashSize` |
| B=4, s=1: ~100x reduction | Rehash probability | Same |
| B=4, s=3: ~10,000x reduction | Rehash probability | Same |
| log(failure_rate) vs log(n) has slope = -(s+1) | Regression slope | Plot from DirectAnalysis CSV output |

**Falsification:** If s=3 does not reduce rehash probability by at least 100x relative to s=0 at n=100,000, H2 is falsified.

---

## H3: Bucketization + Stash Synergy

**Claim:** The combination of bucketization (B=4) and stash (s=3) achieves load factors above 95% with near-zero rehash probability -- a synergistic improvement beyond what either extension provides alone.

| Configuration | Expected Load Factor | Expected Rehash Rate |
|--------------|---------------------|---------------------|
| Standard (B=1, s=0) | ~50% | ~1/n |
| Stash only (B=1, s=3) | ~50% (stash doesn't raise LF ceiling) | ~1/n^4 |
| Bucketized only (B=4, s=0) | ~95% | moderate |
| **Combined (B=4, s=3)** | **>= 95%** | **near-zero** |

**Benchmark:** `LoadFactorBenchmark` with all 4 configurations. The combined variant must outperform both individual extensions on at least one metric.

**Falsification:** If B=4+s=3 does not achieve both >= 95% load factor AND lower rehash rate than B=4+s=0, H3 is falsified.

---

## H4: Practical Competitiveness

**Claim:** At load factors above 80%, bucketized cuckoo hashing with stash (B=4, s=3) achieves lookup throughput within 20% of the fastest baseline (typically linear probing), while maintaining O(1) worst-case lookup guarantee.

| Prediction | Metric | Benchmark |
|-----------|--------|-----------|
| Linear probing has highest raw lookup throughput | Throughput (ops/sec) | `LookupBenchmark` at targetLoadFactor=0.8 |
| Cuckoo B=4+s=3 is within 20% of linear probing | Throughput ratio | Same |
| Robin Hood hashing is between them | Throughput (ops/sec) | Same |
| Hopscotch is competitive with cuckoo | Throughput (ops/sec) | Same |
| d-ary (d=3) has lower throughput due to 3 random accesses | Throughput (ops/sec) | Same |

**Benchmark:** `LookupBenchmark` and `InsertBenchmark` with all hash table variants at comparable load factors.

**Falsification:** If cuckoo B=4+s=3 lookup throughput is more than 20% slower than the fastest baseline at LF=0.8, H4 is falsified.

---

## H5: Distribution Sensitivity

**Claim:** Cuckoo hashing performance remains stable across uniform, Zipfian, and real-world key distributions due to MurmurHash3's strong avalanche properties, unlike linear probing which may degrade on sequential or structured keys.

| Distribution | Expected Cuckoo Behavior | Expected Linear Probing Behavior |
|-------------|-------------------------|--------------------------------|
| Uniform random | Baseline | Baseline |
| Sequential | Stable (MurmurHash3 scrambles) | Potential clustering |
| Zipfian (theta=0.99) | Stable for inserts; hot keys benefit from cache | Hot keys cluster in probe chains |
| Wikipedia pageviews | Stable | Potential degradation on structured string keys |

**Benchmark:** `DatasetBenchmark` comparing all variants across synthetic and real distributions. Also `HashFunctionBenchmark` to isolate hash function quality effects.

**Falsification:** If cuckoo hashing throughput variance across distributions exceeds 15%, or if linear probing shows no degradation on any distribution, H5 is falsified.

---

## Experiment Matrix

| Benchmark | Validates | Key Parameters |
|-----------|----------|----------------|
| `LoadFactorBenchmark` | H1, H3 | bucketSize={1,2,4,8}, stashSize={0,1,2,3,4} |
| `DirectAnalysis.analyzeRehashVsStashSize` | H2, H3 | B=4, s={0,1,2,3,4}, n={10K,50K,100K,500K} |
| `LookupBenchmark` | H4 | All 9 variants, numElements={30K,60K,90K} |
| `InsertBenchmark` | H4 | All 9 variants, numElements={100K,500K,1M} |
| `DeleteBenchmark` | H4 | All 9 variants, numElements={100K,500K} |
| `HashFunctionBenchmark` | H5 | hashFunc={MURMUR3,XXHASH,FNV1A}, B=4 |
| `DatasetBenchmark` | H5 | All 8 variants, data={uniform,zipfian,wikipedia} |
| `DirectAnalysis.analyzeDisplacementChains` | H1, H2 | B={1,2,4,8}, d={2,3,4} |

### Hash Table Variants (9 total)

1. Separate chaining (baseline)
2. Linear probing (baseline)
3. Quadratic probing (baseline)
4. Hopscotch hashing (baseline)
5. Robin Hood hashing (baseline)
6. Standard cuckoo (d=2, B=1)
7. Bucketized cuckoo (d=2, B=4)
8. Stashed cuckoo (d=2, B=4, s=3)
9. d-ary cuckoo (d=3, B=1)
