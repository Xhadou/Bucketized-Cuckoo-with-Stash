package cuckoo.benchmarks;

import cuckoo.core.*;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@Fork(3)
@State(Scope.Benchmark)
public class LoadFactorBenchmark {

    @Param({"1", "2", "4", "8"})
    public int bucketSize;

    @Param({"0", "1", "2", "3", "4"})
    public int stashSize;

    private static final int TABLE_SIZE = 100000;

    @Benchmark
    public double measureMaxLoadFactor() {
        CuckooHashTable<Integer, Integer> table;
        if (stashSize == 0) {
            table = new BucketizedCuckooHashTable<>(TABLE_SIZE, bucketSize);
        } else {
            table = new StashedCuckooHashTable<>(TABLE_SIZE, bucketSize, stashSize);
        }

        double maxLoad = 0;
        for (int i = 0; i < TABLE_SIZE; i++) {
            try {
                table.put(i, i);
                double lf = table.loadFactor();
                if (lf > maxLoad) maxLoad = lf;
            } catch (Exception e) {
                break;
            }
        }
        return maxLoad;
    }
}
