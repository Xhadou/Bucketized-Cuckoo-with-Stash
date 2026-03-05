package cuckoo.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BenchmarkStats {
    private int rehashCount;
    private final List<Integer> displacementChainLengths;
    private int stashInsertions;
    private double maxLoadFactor;
    private long insertCount;
    private long lookupCount;
    private long removeCount;

    public BenchmarkStats() {
        this.displacementChainLengths = new ArrayList<>();
    }

    public void recordRehash() { rehashCount++; }
    public void recordDisplacementChain(int length) { displacementChainLengths.add(length); }
    public void recordStashInsertion() { stashInsertions++; }
    public void updateMaxLoadFactor(double lf) { maxLoadFactor = Math.max(maxLoadFactor, lf); }
    public void recordInsert() { insertCount++; }
    public void recordLookup() { lookupCount++; }
    public void recordRemove() { removeCount++; }

    public int getRehashCount() { return rehashCount; }
    public List<Integer> getDisplacementChainLengths() { return Collections.unmodifiableList(displacementChainLengths); }
    public double getAverageDisplacementChainLength() {
        if (displacementChainLengths.isEmpty()) return 0.0;
        return displacementChainLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }
    public int getStashInsertions() { return stashInsertions; }
    public double getMaxLoadFactor() { return maxLoadFactor; }
    public long getInsertCount() { return insertCount; }
    public long getLookupCount() { return lookupCount; }
    public long getRemoveCount() { return removeCount; }

    public void reset() {
        rehashCount = 0;
        displacementChainLengths.clear();
        stashInsertions = 0;
        maxLoadFactor = 0.0;
        insertCount = 0;
        lookupCount = 0;
        removeCount = 0;
    }
}
