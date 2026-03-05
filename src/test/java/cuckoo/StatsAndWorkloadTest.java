package cuckoo;

import cuckoo.stats.BenchmarkStats;
import cuckoo.util.WorkloadGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

public class StatsAndWorkloadTest {
    @Test
    public void testBenchmarkStatsRecording() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.recordRehash();
        stats.recordRehash();
        assertEquals(2, stats.getRehashCount());
    }

    @Test
    public void testDisplacementChainTracking() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.recordDisplacementChain(5);
        stats.recordDisplacementChain(3);
        stats.recordDisplacementChain(7);
        assertEquals(3, stats.getDisplacementChainLengths().size());
        assertEquals(5.0, stats.getAverageDisplacementChainLength(), 0.01);
    }

    @Test
    public void testStashInsertionTracking() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.recordStashInsertion();
        stats.recordStashInsertion();
        assertEquals(2, stats.getStashInsertions());
    }

    @Test
    public void testMaxLoadFactor() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.updateMaxLoadFactor(0.5);
        stats.updateMaxLoadFactor(0.8);
        stats.updateMaxLoadFactor(0.6);
        assertEquals(0.8, stats.getMaxLoadFactor(), 0.001);
    }

    @Test
    public void testOperationCounts() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.recordInsert();
        stats.recordInsert();
        stats.recordLookup();
        stats.recordRemove();
        assertEquals(2, stats.getInsertCount());
        assertEquals(1, stats.getLookupCount());
        assertEquals(1, stats.getRemoveCount());
    }

    @Test
    public void testReset() {
        BenchmarkStats stats = new BenchmarkStats();
        stats.recordRehash();
        stats.recordInsert();
        stats.recordDisplacementChain(5);
        stats.reset();
        assertEquals(0, stats.getRehashCount());
        assertEquals(0, stats.getInsertCount());
        assertTrue(stats.getDisplacementChainLengths().isEmpty());
    }

    @Test
    public void testUniformRandom() {
        int[] data = WorkloadGenerator.uniformRandom(1000, 42);
        assertEquals(1000, data.length);
        // Same seed produces same data
        int[] data2 = WorkloadGenerator.uniformRandom(1000, 42);
        assertArrayEquals(data, data2);
    }

    @Test
    public void testSequential() {
        int[] data = WorkloadGenerator.sequential(100);
        assertEquals(100, data.length);
        for (int i = 0; i < 100; i++) {
            assertEquals(i, data[i]);
        }
    }

    @Test
    public void testZipfian() {
        int[] data = WorkloadGenerator.zipfian(10000, 1.5, 42);
        assertEquals(10000, data.length);
        // Zipfian should have many small values, few large values
        int smallCount = 0;
        for (int v : data) {
            if (v <= 10) smallCount++;
        }
        assertTrue("Zipfian should have many small values", smallCount > data.length / 4);
    }
}
