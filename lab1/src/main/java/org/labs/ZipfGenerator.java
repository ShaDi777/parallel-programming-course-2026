package org.labs;

import java.util.Arrays;
import java.util.Random;

public class ZipfGenerator {
    public static final int ARRAY_SIZE = 1 << 20;
    public static final int K_MAX = 1023;
    public static final double POW = 1.15;
    public static final long SEED = 777;
    public static final Random RANDOM = new Random(SEED);

    public static long[] generateRandomArray() {
        double[] probArray = new double[K_MAX];

        double total = 0;
        for (int i = 0; i < probArray.length; i++) {
            total += 1.0 / Math.pow((i + 1), POW);
            probArray[i] = total;
        }
        for (int i = 0; i < probArray.length; i++) {
            probArray[i] /= total;
        }

        long[] array = new long[ARRAY_SIZE];
        for (int i = 0; i < ARRAY_SIZE; i++) {
            var sample = RANDOM.nextDouble();
            for (int bucket = 0; bucket < probArray.length; bucket++) {
                if (sample < probArray[bucket]) {
                    array[i] = bucket + 1;
                    break;
                }
            }
        }

        return array;
    }

    // Debug test
    static void main() {
        long t0 = System.nanoTime();
        long[] values = ZipfGenerator.generateRandomArray();
        long t1 = System.nanoTime();

        System.out.printf("Generated %,d values in %.1f ms%n", values.length, (t1 - t0) / 1e6);

        // Check
        long zeros = 0, over1024 = 0;
        for (long v : values) {
            if (v == 0) zeros++;
            if (v >= 1024) over1024++;
        }
        System.out.printf("Bucket 0: %d (%.2f%%)%n", zeros, 100.0 * zeros / ZipfGenerator.ARRAY_SIZE);
        System.out.printf(">=1024:   %d (%.2f%%)%n", over1024, 100.0 * over1024 / ZipfGenerator.ARRAY_SIZE);

        // Check stability
        long[] firstTen = Arrays.copyOfRange(values, 0, Math.min(10, values.length));
        System.out.println(Arrays.toString(firstTen));

        // Check distribution
        long[] distribution = new long[ZipfGenerator.K_MAX];
        for (long value : values) {
            distribution[(int) value - 1]++;
        }
        System.out.println(Arrays.toString(distribution));
    }
}
