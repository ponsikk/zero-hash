package io.zerohash.benchmark;

import io.zerohash.Blake3;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * JMH Benchmarks for Zero-Hash BLAKE3 implementation.
 * 
 * <p>
 * Run with:
 * 
 * <pre>
 * mvn clean test-compile exec:java -Dexec.mainClass=io.zerohash.benchmark.Blake3Benchmark
 * </pre>
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1, jvmArgs = { "--enable-preview", "--enable-native-access=ALL-UNNAMED" })
@State(Scope.Benchmark)
public class Blake3Benchmark {

    @Param({ "1024", "65536", "1048576", "10485760", "104857600" }) // 1KB, 64KB, 1MB, 10MB, 100MB
    private int dataSize;

    private byte[] data;
    private ByteBuffer directBuffer;
    private MessageDigest sha256;
    private byte[] expectedHash;

    // File-based benchmark state
    private Path tempFile;
    private Path tempDir;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        data = new byte[dataSize];
        new Random(42).nextBytes(data); // Fixed seed for reproducibility

        directBuffer = ByteBuffer.allocateDirect(dataSize);
        directBuffer.put(data);
        directBuffer.flip();

        sha256 = MessageDigest.getInstance("SHA-256");
        expectedHash = Blake3.hash(data);

        // Create temporary file for file benchmarks in target directory
        Path targetDir = Path.of("target");
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }

        tempFile = targetDir.resolve("blake3-bench-" + dataSize + ".bin");
        Files.write(tempFile, data);

        // Create temporary directory with multiple files for directory benchmarks
        tempDir = targetDir.resolve("blake3-bench-dir-" + dataSize);
        if (Files.exists(tempDir)) {
            // Clean up existing directory
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
        Files.createDirectories(tempDir);

        // Create 10 files in the directory
        for (int i = 0; i < 10; i++) {
            Path file = tempDir.resolve("file-" + i + ".bin");
            byte[] fileData = new byte[Math.max(1, dataSize / 10)];
            new Random(42 + i).nextBytes(fileData);
            Files.write(file, fileData);
        }
    }

    @TearDown(Level.Trial)
    public void teardown() throws IOException {
        // Clean up temporary files
        if (tempFile != null && Files.exists(tempFile)) {
            Files.delete(tempFile);
        }

        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                });
        }
    }

    // ========================================================================
    // BLAKE3 benchmarks
    // ========================================================================

    @Benchmark
    public byte[] blake3_singleThreaded() {
        return Blake3.hash(data);
    }

    @Benchmark
    public byte[] blake3_parallel() {
        return Blake3.hashParallel(data);
    }

    @Benchmark
    public byte[] blake3_zeroCopy() {
        directBuffer.position(0);
        return Blake3.hashZeroCopy(directBuffer);
    }

    @Benchmark
    public byte[] blake3_streaming() {
        try (var hasher = Blake3.hasher()) {
            // Process in 64KB chunks
            int chunkSize = 64 * 1024;
            for (int offset = 0; offset < data.length; offset += chunkSize) {
                int len = Math.min(chunkSize, data.length - offset);
                byte[] chunk = new byte[len];
                System.arraycopy(data, offset, chunk, 0, len);
                hasher.update(chunk);
            }
            return hasher.digest();
        }
    }

    // ========================================================================
    // SHA-256 baseline
    // ========================================================================

    @Benchmark
    public byte[] sha256_baseline() {
        sha256.reset();
        return sha256.digest(data);
    }

    // ========================================================================
    // Verification benchmark
    // ========================================================================

    @Benchmark
    public boolean blake3_verify() {
        return Blake3.verify(data, expectedHash);
    }

    // ========================================================================
    // File hashing benchmarks
    // ========================================================================

    @Benchmark
    public byte[] blake3_hashFile() throws IOException {
        return Blake3.hashFile(tempFile);
    }

    @Benchmark
    public byte[] blake3_hashFileParallel() throws IOException {
        return Blake3.hashFileParallel(tempFile);
    }

    @Benchmark
    public byte[] blake3_hashFileMmap() throws IOException {
        return Blake3.hashFileMmap(tempFile);
    }

    @Benchmark
    public byte[] blake3_hashFileMmapParallel() throws IOException {
        return Blake3.hashFileMmapParallel(tempFile);
    }

    // ========================================================================
    // Directory hashing benchmark
    // ========================================================================

    @Benchmark
    public byte[] blake3_hashDirectory() throws IOException {
        return Blake3.hashDirectory(tempDir);
    }

    // ========================================================================
    // Main runner
    // ========================================================================

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(Blake3Benchmark.class.getSimpleName())
                .forks(1)
                .build();

        new Runner(opt).run();
    }
}
