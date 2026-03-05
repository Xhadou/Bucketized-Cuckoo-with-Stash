package cuckoo.util;

import java.util.Random;

public final class WorkloadGenerator {
    private WorkloadGenerator() {}

    public static int[] uniformRandom(int n, int seed) {
        Random rng = new Random(seed);
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = rng.nextInt();
        }
        return data;
    }

    public static int[] sequential(int n) {
        int[] data = new int[n];
        for (int i = 0; i < n; i++) {
            data[i] = i;
        }
        return data;
    }

    public static int[] zipfian(int n, double skew, int seed) {
        Random rng = new Random(seed);
        int[] data = new int[n];
        int maxVal = n;
        // Precompute cumulative probabilities
        double[] cdf = new double[maxVal + 1];
        double sum = 0;
        for (int i = 1; i <= maxVal; i++) {
            sum += 1.0 / Math.pow(i, skew);
        }
        double cumulative = 0;
        for (int i = 1; i <= maxVal; i++) {
            cumulative += 1.0 / Math.pow(i, skew) / sum;
            cdf[i] = cumulative;
        }
        for (int i = 0; i < n; i++) {
            double r = rng.nextDouble();
            // Binary search for the value
            int lo = 1, hi = maxVal;
            while (lo < hi) {
                int mid = (lo + hi) / 2;
                if (cdf[mid] < r) lo = mid + 1;
                else hi = mid;
            }
            data[i] = lo;
        }
        return data;
    }
}
