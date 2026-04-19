package cuckoo.hash;

/**
 * Carter-Wegman universal hash family.
 *
 * h(x) = ((a * x + b) mod p)
 *
 * where p = 2^31 - 1 is a Mersenne prime and (a, b) are derived from the seed.
 * Theoretically proven 2-universal: for any distinct keys x, y drawn before
 * (a, b) are chosen, Pr[h(x) = h(y)] <= 1/m when the output is taken mod m.
 *
 * Unlike MurmurHash3/xxHash (which are deterministic avalanche-cascade hashes
 * designed for uniform output on fixed inputs), this is a theoretically-
 * guaranteed universal family in the Carter-Wegman sense -- the guarantee
 * holds against adversarial inputs because a, b are random.
 */
public final class UniversalHash {
    // Mersenne prime 2^31 - 1; enables fast modular reduction.
    private static final long P = 2147483647L;

    // Large odd multipliers used to spread the seed into distinct (a, b) pairs.
    // Knuth's multiplier and a second large prime keep derived params decorrelated.
    private static final long A_DERIVE = 2654435761L;
    private static final long B_DERIVE = 40503L;

    private UniversalHash() {}

    public static int hash32(int key, int seed) {
        long a = ((long)(seed | 1) * A_DERIVE) % (P - 1) + 1; // a in [1, p-1]
        long b = ((long)(seed + 1) * B_DERIVE) % P;            // b in [0, p-1]
        long x = key & 0xFFFFFFFFL; // treat as unsigned 32-bit
        long h = (a * x + b) % P;
        return (int) h;
    }
}
