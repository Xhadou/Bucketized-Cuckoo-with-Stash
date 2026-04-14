# Research Plan: Cuckoo Hashing Benchmark Best Practices

## Main Question
What experiments, metrics, and methodology should a rigorous bucketized cuckoo hashing with stash project include, and what gaps exist in the current implementation?

## Subtopics

### 1. Academic Paper Experiments
Key experiments from foundational papers (Pagh & Rodler, Erlingsson et al., Kirsch-Mitzenmacher-Wieder). What metrics they measure, what graphs they present, what claims they validate experimentally.

### 2. Hash Table Benchmarking Best Practices
JMH pitfalls, cache-aware benchmarking, proper statistical methodology, memory profiling, and what the systems community considers rigorous for hash table evaluation.

### 3. Cuckoo Hashing Specific Metrics
Insertion failure probability, expected displacement path length, stash utilization rates, theoretical vs empirical load factor thresholds, and cache-line analysis specific to bucketized variants.

## Synthesis
Compare findings against the project's current test/benchmark suite to identify concrete gaps and improvements.
