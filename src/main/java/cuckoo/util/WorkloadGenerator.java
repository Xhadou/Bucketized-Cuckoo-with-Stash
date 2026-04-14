package cuckoo.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
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

    /**
     * Load string keys from a newline-delimited file.
     * Returns unique keys (deduplication via LinkedHashSet to preserve order).
     */
    public static String[] fromFile(String path) throws IOException {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    keys.add(trimmed);
                }
            }
        }
        return keys.toArray(new String[0]);
    }

    /**
     * Generate a hotspot distribution: fraction {@code hotFraction} of operations target
     * {@code hotsetFraction} of keys. Models cache-like workloads.
     *
     * @param n             number of keys to generate
     * @param hotsetFraction fraction of the key space that is "hot" (0.0 to 1.0)
     * @param hotFraction    fraction of operations that target hot keys (0.0 to 1.0)
     * @param seed          random seed for reproducibility
     * @return array of key indices in [0, n)
     */
    public static int[] hotspot(int n, double hotsetFraction, double hotFraction, int seed) {
        Random rng = new Random(seed);
        int[] data = new int[n];
        int hotsetSize = Math.max(1, (int) (n * hotsetFraction));
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < hotFraction) {
                // Pick from the hot set: keys [0, hotsetSize)
                data[i] = rng.nextInt(hotsetSize);
            } else {
                // Pick from the cold set: keys [hotsetSize, n)
                data[i] = hotsetSize + rng.nextInt(n - hotsetSize);
            }
        }
        return data;
    }
}
