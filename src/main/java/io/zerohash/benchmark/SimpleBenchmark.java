package io.zerohash.benchmark;

import io.zerohash.Blake3;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;

/**
 * Simple benchmark comparing BLAKE3 vs SHA-256.
 * 
 * Run: java --enable-preview --enable-native-access=ALL-UNNAMED -cp
 * target/classes io.zerohash.benchmark.SimpleBenchmark
 */
public class SimpleBenchmark {

    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 5;
    private static final int[] DATA_SIZES = {
            1024, // 1 KB
            64 * 1024, // 64 KB
            1024 * 1024, // 1 MB
            10 * 1024 * 1024, // 10 MB
            100 * 1024 * 1024, // 100 MB
            512 * 1024 * 1024, // 512 MB
            1024 * 1024 * 1024// 1 GB
    };

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           Zero-Hash Benchmark: BLAKE3 vs SHA-256             ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();

        for (int size : DATA_SIZES) {
            byte[] data = generateData(size);
            System.out.printf("📊 Data size: %s%n", formatSize(size));
            System.out.println("─".repeat(50));

            // Warmup
            System.out.print("   Warming up...");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                Blake3.hash(data);
                sha256(data);
            }
            System.out.println(" done");

            // Benchmark BLAKE3
            long blake3Time = benchmarkBlake3(data);
            double blake3Speed = (double) size / blake3Time * 1000; // bytes per second

            // Benchmark SHA-256
            long sha256Time = benchmarkSha256(data);
            double sha256Speed = (double) size / sha256Time * 1000;

            // Results
            System.out.printf("   BLAKE3:  %8.2f ms  (%s/s)%n",
                    blake3Time / 1_000_000.0, formatSize((long) blake3Speed));
            System.out.printf("   SHA-256: %8.2f ms  (%s/s)%n",
                    sha256Time / 1_000_000.0, formatSize((long) sha256Speed));
            System.out.printf("   Speedup: %.1fx faster%n", (double) sha256Time / blake3Time);
            System.out.println();
        }

        System.out.println("✅ Benchmark complete!");
    }

    private static long benchmarkBlake3(byte[] data) {
        long totalTime = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            Blake3.hash(data);
            totalTime += System.nanoTime() - start;
        }
        return totalTime / BENCHMARK_ITERATIONS;
    }

    private static long benchmarkSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        long totalTime = 0;
        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            md.reset();
            md.digest(data);
            totalTime += System.nanoTime() - start;
        }
        return totalTime / BENCHMARK_ITERATIONS;
    }

    private static byte[] sha256(byte[] data) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }

    private static byte[] generateData(int size) {
        byte[] data = new byte[size];
        new Random(42).nextBytes(data); // Fixed seed for reproducibility
        return data;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024)
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
