package io.zerohash;

import io.zerohash.internal.NativeLib;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * High-performance BLAKE3 cryptographic hashing.
 * 
 * <p>
 * BLAKE3 is a cryptographic hash function that is fast and secure.
 * This implementation uses a native Rust library via Panama FFI.
 * 
 * <h2>Usage Examples</h2>
 * 
 * <pre>{@code
 * // Simple hashing
 * byte[] hash = Blake3.hash(data);
 * String hex = Blake3.hashHex(data);
 * 
 * // Keyed hashing (MAC)
 * byte[] mac = Blake3.keyedHash(key, data);
 * 
 * // Key derivation (KDF)
 * byte[] derived = Blake3.deriveKey("context", material, 32);
 * 
 * // Streaming API
 * try (var hasher = Blake3.hasher()) {
 *     hasher.update(chunk1);
 *     hasher.update(chunk2);
 *     byte[] hash = hasher.digest();
 * }
 * 
 * // Zero-copy with DirectByteBuffer
 * byte[] hash = Blake3.hashZeroCopy(directBuffer);
 * }</pre>
 */
public final class Blake3 {

    /** BLAKE3 hash output size in bytes (256 bits). */
    public static final int HASH_SIZE = 32;

    /** BLAKE3 key size for keyed hashing (256 bits). */
    public static final int KEY_SIZE = 32;

    private static final HexFormat HEX = HexFormat.of();

    static {
        NativeLib.ensureLoaded();
    }

    private Blake3() {
        // Utility class - no instantiation
    }

    // ========================================================================
    // One-shot hashing
    // ========================================================================

    /**
     * Compute BLAKE3 hash of input data.
     * 
     * @param data input data to hash
     * @return 32-byte hash
     * @throws NullPointerException if data is null
     * @throws RuntimeException     if hashing fails
     */
    public static byte[] hash(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input;
            if (data.length > 0) {
                input = arena.allocate(data.length);
                input.copyFrom(MemorySegment.ofArray(data));
            } else {
                input = MemorySegment.NULL;
            }

            MemorySegment output = arena.allocate(HASH_SIZE);

            int result = (int) NativeLib.BLAKE3_HASH.invokeExact(
                    input,
                    (long) data.length,
                    output);

            if (result != 0) {
                throw new RuntimeException("blake3_hash failed with error code: " + result);
            }

            return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException("blake3_hash invocation failed", t);
        }
    }

    /**
     * Compute BLAKE3 hash and return as hex string.
     * 
     * @param data input data to hash
     * @return 64-character hex string
     */
    public static String hashHex(byte[] data) {
        return HEX.formatHex(hash(data));
    }

    /**
     * Compute BLAKE3 hash of a string (UTF-8 encoded).
     * 
     * @param data string to hash
     * @return 32-byte hash
     */
    public static byte[] hash(String data) {
        return hash(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Compute BLAKE3 hash of a string and return as hex.
     * 
     * @param data string to hash
     * @return 64-character hex string
     */
    public static String hashHex(String data) {
        return HEX.formatHex(hash(data));
    }

    // ========================================================================
    // Parallel hashing (Rayon multithreaded)
    // ========================================================================

    /**
     * Compute BLAKE3 hash using parallel Rayon threads.
     * 
     * <p>
     * This is optimized for large data (>128KB). For smaller data,
     * use {@link #hash(byte[])} instead as the threading overhead may hurt
     * performance.
     * 
     * @param data input data to hash
     * @return 32-byte hash
     * @throws NullPointerException if data is null
     */
    public static byte[] hashParallel(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input;
            if (data.length > 0) {
                input = arena.allocate(data.length);
                input.copyFrom(MemorySegment.ofArray(data));
            } else {
                input = MemorySegment.NULL;
            }

            MemorySegment output = arena.allocate(HASH_SIZE);

            int result = (int) NativeLib.BLAKE3_HASH_PARALLEL.invokeExact(
                    input,
                    (long) data.length,
                    output);

            if (result != 0) {
                throw new RuntimeException("blake3_hash_parallel failed with error code: " + result);
            }

            return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException("blake3_hash_parallel invocation failed", t);
        }
    }

    /**
     * Compute parallel BLAKE3 hash and return as hex string.
     * 
     * @param data input data to hash
     * @return 64-character hex string
     */
    public static String hashParallelHex(byte[] data) {
        return HEX.formatHex(hashParallel(data));
    }

    // ========================================================================
    // File hashing
    // ========================================================================

    /**
     * Compute BLAKE3 hash of a file.
     * 
     * <p>
     * Uses streaming API to handle large files without loading into memory.
     * 
     * @param path path to file
     * @return 32-byte hash
     * @throws java.io.IOException if file cannot be read
     */
    public static byte[] hashFile(java.nio.file.Path path) throws java.io.IOException {
        try (var hasher = hasher();
                var channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {

            ByteBuffer buffer = ByteBuffer.allocateDirect(64 * 1024); // 64KB chunks
            while (channel.read(buffer) != -1) {
                buffer.flip();
                hasher.updateDirect(buffer);
                buffer.clear();
            }
            return hasher.digest();
        }
    }

    /**
     * Compute BLAKE3 hash of a file and return as hex string.
     * 
     * @param path path to file
     * @return 64-character hex string
     * @throws java.io.IOException if file cannot be read
     */
    public static String hashFileHex(java.nio.file.Path path) throws java.io.IOException {
        return HEX.formatHex(hashFile(path));
    }

    /**
     * Compute parallel BLAKE3 hash of a file (loads entire file into memory).
     * 
     * <p>
     * For files that fit in memory, this is faster than streaming.
     * Use {@link #hashFile(java.nio.file.Path)} for very large files.
     * 
     * @param path path to file
     * @return 32-byte hash
     * @throws java.io.IOException if file cannot be read
     */
    public static byte[] hashFileParallel(java.nio.file.Path path) throws java.io.IOException {
        byte[] data = java.nio.file.Files.readAllBytes(path);
        return hashParallel(data);
    }

    /**
     * Compute BLAKE3 hash using memory-mapped file I/O.
     * 
     * <p>
     * This is the fastest method for large files. The file is mapped
     * directly into memory without copying, then hashed using parallel Rayon.
     * 
     * <p>
     * Best for files >10MB. For smaller files, use
     * {@link #hashFile(java.nio.file.Path)}.
     * 
     * @param path path to file
     * @return 32-byte hash
     * @throws java.io.IOException if file cannot be read
     */
    public static byte[] hashFileMmap(java.nio.file.Path path) throws java.io.IOException {
        try (var channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) {
                return hash(new byte[0]);
            }
            if (size > Integer.MAX_VALUE) {
                // File too large for single mapping, fall back to streaming
                return hashFile(path);
            }

            java.nio.MappedByteBuffer mapped = channel.map(
                    java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size);

            // Use zero-copy hashing since MappedByteBuffer is direct
            return hashZeroCopy(mapped);
        }
    }

    /**
     * Compute BLAKE3 hash using memory-mapped file with parallel Rayon.
     * 
     * @param path path to file
     * @return 32-byte hash
     * @throws java.io.IOException if file cannot be read
     */
    public static byte[] hashFileMmapParallel(java.nio.file.Path path) throws java.io.IOException {
        try (var channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ)) {
            long size = channel.size();
            if (size == 0) {
                return hash(new byte[0]);
            }
            if (size > Integer.MAX_VALUE) {
                return hashFile(path);
            }

            java.nio.MappedByteBuffer mapped = channel.map(
                    java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size);

            // Convert to byte array for parallel hashing
            byte[] data = new byte[(int) size];
            mapped.get(data);
            return hashParallel(data);
        }
    }

    /**
     * Compute BLAKE3 hash of a file and return as hex string (memory-mapped).
     * 
     * @param path path to file
     * @return 64-character hex string
     * @throws java.io.IOException if file cannot be read
     */
    public static String hashFileMmapHex(java.nio.file.Path path) throws java.io.IOException {
        return HEX.formatHex(hashFileMmap(path));
    }

    // ========================================================================
    // Directory hashing
    // ========================================================================

    /**
     * Compute combined BLAKE3 hash of all files in a directory.
     * 
     * <p>
     * Files are sorted by path to ensure deterministic results.
     * The hash includes relative path and content of each file.
     * 
     * @param directory path to directory
     * @return 32-byte hash
     * @throws java.io.IOException      if directory cannot be read
     * @throws IllegalArgumentException if path is not a directory
     */
    public static byte[] hashDirectory(java.nio.file.Path directory) throws java.io.IOException {
        if (!java.nio.file.Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }

        try (var hasher = hasher()) {
            java.nio.file.Files.walk(directory)
                    .filter(java.nio.file.Files::isRegularFile)
                    .sorted()
                    .forEach(file -> {
                        try {
                            // Include relative path in hash for uniqueness
                            String relativePath = directory.relativize(file).toString();
                            hasher.update(relativePath);

                            // Include file content
                            byte[] content = java.nio.file.Files.readAllBytes(file);
                            hasher.update(content);
                        } catch (java.io.IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    });

            return hasher.digest();
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /**
     * Compute combined BLAKE3 hash of directory and return as hex string.
     * 
     * @param directory path to directory
     * @return 64-character hex string
     * @throws java.io.IOException if directory cannot be read
     */
    public static String hashDirectoryHex(java.nio.file.Path directory) throws java.io.IOException {
        return HEX.formatHex(hashDirectory(directory));
    }

    /**
     * Compute hash of each file in directory and return as map.
     * 
     * @param directory path to directory
     * @return map of relative path to 32-byte hash
     * @throws java.io.IOException if directory cannot be read
     */
    public static java.util.Map<String, byte[]> hashDirectoryFiles(java.nio.file.Path directory)
            throws java.io.IOException {
        if (!java.nio.file.Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }

        java.util.Map<String, byte[]> result = new java.util.TreeMap<>();

        java.nio.file.Files.walk(directory)
                .filter(java.nio.file.Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = directory.relativize(file).toString();
                        byte[] hash = hashFile(file);
                        result.put(relativePath, hash);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });

        return result;
    }

    /**
     * Compute hash of each file in directory and return as map of hex strings.
     * 
     * @param directory path to directory
     * @return map of relative path to 64-char hex hash
     * @throws java.io.IOException if directory cannot be read
     */
    public static java.util.Map<String, String> hashDirectoryFilesHex(java.nio.file.Path directory)
            throws java.io.IOException {
        java.util.Map<String, byte[]> hashes = hashDirectoryFiles(directory);
        java.util.Map<String, String> result = new java.util.TreeMap<>();
        hashes.forEach((path, hash) -> result.put(path, HEX.formatHex(hash)));
        return result;
    }

    // ========================================================================
    // Verification utilities
    // ========================================================================

    /**
     * Verify that data matches expected hash.
     * 
     * @param data         data to verify
     * @param expectedHash expected 32-byte hash
     * @return true if hash matches
     */
    public static boolean verify(byte[] data, byte[] expectedHash) {
        if (expectedHash == null || expectedHash.length != HASH_SIZE) {
            return false;
        }
        byte[] actualHash = hash(data);
        return java.util.Arrays.equals(actualHash, expectedHash);
    }

    /**
     * Verify that data matches expected hex hash.
     * 
     * @param data        data to verify
     * @param expectedHex expected 64-character hex hash
     * @return true if hash matches
     */
    public static boolean verify(byte[] data, String expectedHex) {
        if (expectedHex == null || expectedHex.length() != 64) {
            return false;
        }
        try {
            byte[] expectedHash = fromHex(expectedHex);
            return verify(data, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Verify file hash.
     * 
     * @param path         path to file
     * @param expectedHash expected 32-byte hash
     * @return true if hash matches
     * @throws java.io.IOException if file cannot be read
     */
    public static boolean verifyFile(java.nio.file.Path path, byte[] expectedHash) throws java.io.IOException {
        if (expectedHash == null || expectedHash.length != HASH_SIZE) {
            return false;
        }
        byte[] actualHash = hashFile(path);
        return java.util.Arrays.equals(actualHash, expectedHash);
    }

    /**
     * Verify file hash against hex string.
     * 
     * @param path        path to file
     * @param expectedHex expected 64-character hex hash
     * @return true if hash matches
     * @throws java.io.IOException if file cannot be read
     */
    public static boolean verifyFile(java.nio.file.Path path, String expectedHex) throws java.io.IOException {
        if (expectedHex == null || expectedHex.length() != 64) {
            return false;
        }
        try {
            byte[] expectedHash = fromHex(expectedHex);
            return verifyFile(path, expectedHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ========================================================================
    // Hex utilities
    // ========================================================================

    /**
     * Parse hex string to bytes.
     * 
     * @param hex hex string (case insensitive)
     * @return byte array
     * @throws IllegalArgumentException if hex is invalid
     */
    public static byte[] fromHex(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("hex cannot be null");
        }
        return HEX.parseHex(hex);
    }

    /**
     * Convert bytes to hex string.
     * 
     * @param bytes byte array
     * @return lowercase hex string
     */
    public static String toHex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        return HEX.formatHex(bytes);
    }

    // ========================================================================
    // Zero-copy hashing
    // ========================================================================

    /**
     * Compute BLAKE3 hash using zero-copy access to DirectByteBuffer.
     * 
     * <p>
     * This method avoids copying data from native memory to Java heap,
     * providing better performance for large buffers.
     * 
     * @param buffer direct byte buffer containing data to hash
     * @return 32-byte hash
     * @throws IllegalArgumentException if buffer is not direct
     */
    public static byte[] hashZeroCopy(ByteBuffer buffer) {
        if (!buffer.isDirect()) {
            throw new IllegalArgumentException("Buffer must be direct");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment input = MemorySegment.ofBuffer(buffer);
            MemorySegment output = arena.allocate(HASH_SIZE);

            int result = (int) NativeLib.BLAKE3_HASH.invokeExact(
                    input,
                    (long) buffer.remaining(),
                    output);

            if (result != 0) {
                throw new RuntimeException("blake3_hash failed with error code: " + result);
            }

            return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException("blake3_hash invocation failed", t);
        }
    }

    // ========================================================================
    // Keyed hashing (MAC)
    // ========================================================================

    /**
     * Compute keyed BLAKE3 hash (Message Authentication Code).
     * 
     * @param key  32-byte key
     * @param data input data
     * @return 32-byte MAC
     * @throws IllegalArgumentException if key is not 32 bytes
     */
    public static byte[] keyedHash(byte[] key, byte[] data) {
        if (key == null || key.length != KEY_SIZE) {
            throw new IllegalArgumentException("Key must be exactly 32 bytes");
        }
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment keySegment = arena.allocate(KEY_SIZE);
            keySegment.copyFrom(MemorySegment.ofArray(key));

            MemorySegment input;
            if (data.length > 0) {
                input = arena.allocate(data.length);
                input.copyFrom(MemorySegment.ofArray(data));
            } else {
                input = MemorySegment.NULL;
            }

            MemorySegment output = arena.allocate(HASH_SIZE);

            int result = (int) NativeLib.BLAKE3_HASH_KEYED.invokeExact(
                    keySegment,
                    input,
                    (long) data.length,
                    output);

            if (result != 0) {
                throw new RuntimeException("blake3_hash_keyed failed with error code: " + result);
            }

            return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException("blake3_hash_keyed invocation failed", t);
        }
    }

    // ========================================================================
    // Key derivation (KDF)
    // ========================================================================

    /**
     * Derive a key using BLAKE3 KDF.
     * 
     * @param context      domain separation context string
     * @param material     input key material
     * @param outputLength desired output length in bytes
     * @return derived key bytes
     */
    public static byte[] deriveKey(String context, byte[] material, int outputLength) {
        if (context == null) {
            throw new NullPointerException("context cannot be null");
        }
        if (material == null) {
            throw new NullPointerException("material cannot be null");
        }
        if (outputLength <= 0) {
            throw new IllegalArgumentException("outputLength must be positive");
        }

        byte[] contextBytes = context.getBytes(StandardCharsets.UTF_8);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ctxSegment;
            if (contextBytes.length > 0) {
                ctxSegment = arena.allocate(contextBytes.length);
                ctxSegment.copyFrom(MemorySegment.ofArray(contextBytes));
            } else {
                ctxSegment = MemorySegment.NULL;
            }

            MemorySegment matSegment;
            if (material.length > 0) {
                matSegment = arena.allocate(material.length);
                matSegment.copyFrom(MemorySegment.ofArray(material));
            } else {
                matSegment = MemorySegment.NULL;
            }

            MemorySegment output = arena.allocate(outputLength);

            int result = (int) NativeLib.BLAKE3_DERIVE_KEY.invokeExact(
                    ctxSegment,
                    (long) contextBytes.length,
                    matSegment,
                    (long) material.length,
                    output,
                    (long) outputLength);

            if (result != 0) {
                throw new RuntimeException("blake3_derive_key failed with error code: " + result);
            }

            return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
        } catch (Throwable t) {
            throw new RuntimeException("blake3_derive_key invocation failed", t);
        }
    }

    /**
     * Derive a 32-byte key using BLAKE3 KDF.
     * 
     * @param context  domain separation context string
     * @param material input key material
     * @return 32-byte derived key
     */
    public static byte[] deriveKey(String context, byte[] material) {
        return deriveKey(context, material, HASH_SIZE);
    }

    // ========================================================================
    // Streaming API
    // ========================================================================

    /**
     * Create a new streaming hasher.
     * 
     * @return new hasher instance
     */
    public static Hasher hasher() {
        return new Hasher();
    }

    /**
     * Streaming BLAKE3 hasher for incremental hashing.
     * 
     * <p>
     * Use this for hashing large files or data streams.
     * The hasher must be closed after use to free native resources.
     */
    public static final class Hasher implements AutoCloseable {

        private MemorySegment handle;
        private boolean done = false;

        private Hasher() {
            try {
                this.handle = (MemorySegment) NativeLib.BLAKE3_HASHER_NEW.invokeExact();
                if (handle.equals(MemorySegment.NULL)) {
                    throw new RuntimeException("Failed to create hasher");
                }
            } catch (Throwable t) {
                throw new RuntimeException("blake3_hasher_new invocation failed", t);
            }
        }

        /**
         * Update the hasher with more data.
         * 
         * @param data data to add
         * @return this hasher for chaining
         * @throws IllegalStateException if hasher is finalized or closed
         */
        public Hasher update(byte[] data) {
            if (handle == null) {
                throw new IllegalStateException("Hasher is closed");
            }
            if (done) {
                throw new IllegalStateException("Hasher is already finalized");
            }
            if (data == null || data.length == 0) {
                return this;
            }

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment input = arena.allocate(data.length);
                input.copyFrom(MemorySegment.ofArray(data));

                int result = (int) NativeLib.BLAKE3_HASHER_UPDATE.invokeExact(
                        handle,
                        input,
                        (long) data.length);

                if (result != 0) {
                    throw new RuntimeException("blake3_hasher_update failed: " + result);
                }
            } catch (Throwable t) {
                throw new RuntimeException("blake3_hasher_update invocation failed", t);
            }

            return this;
        }

        /**
         * Update the hasher with string data (UTF-8).
         * 
         * @param data string to add
         * @return this hasher for chaining
         */
        public Hasher update(String data) {
            return update(data.getBytes(StandardCharsets.UTF_8));
        }

        /**
         * Update the hasher with data from DirectByteBuffer (zero-copy).
         * 
         * @param buffer direct byte buffer
         * @return this hasher for chaining
         * @throws IllegalArgumentException if buffer is not direct
         */
        public Hasher updateDirect(ByteBuffer buffer) {
            if (handle == null) {
                throw new IllegalStateException("Hasher is closed");
            }
            if (done) {
                throw new IllegalStateException("Hasher is already finalized");
            }
            if (!buffer.isDirect()) {
                throw new IllegalArgumentException("Buffer must be direct");
            }
            if (!buffer.hasRemaining()) {
                return this;
            }

            try {
                MemorySegment input = MemorySegment.ofBuffer(buffer);
                int result = (int) NativeLib.BLAKE3_HASHER_UPDATE.invokeExact(
                        handle,
                        input,
                        (long) buffer.remaining());

                if (result != 0) {
                    throw new RuntimeException("blake3_hasher_update failed: " + result);
                }
            } catch (Throwable t) {
                throw new RuntimeException("blake3_hasher_update invocation failed", t);
            }

            return this;
        }

        /**
         * Finalize the hasher and get the 32-byte hash.
         * 
         * @return 32-byte hash
         * @throws IllegalStateException if hasher is closed
         */
        public byte[] digest() {
            return digest(HASH_SIZE);
        }

        /**
         * Finalize the hasher with custom output length (XOF mode).
         * 
         * @param outputLength desired output length
         * @return hash bytes
         */
        public byte[] digest(int outputLength) {
            if (handle == null) {
                throw new IllegalStateException("Hasher is closed");
            }
            if (outputLength <= 0) {
                throw new IllegalArgumentException("outputLength must be positive");
            }

            done = true;

            try (Arena arena = Arena.ofConfined()) {
                MemorySegment output = arena.allocate(outputLength);

                int result = (int) NativeLib.BLAKE3_HASHER_FINALIZE.invokeExact(
                        handle,
                        output,
                        (long) outputLength);

                if (result != 0) {
                    throw new RuntimeException("blake3_hasher_finalize failed: " + result);
                }

                return output.toArray(java.lang.foreign.ValueLayout.JAVA_BYTE);
            } catch (Throwable t) {
                throw new RuntimeException("blake3_hasher_finalize invocation failed", t);
            }
        }

        /**
         * Finalize and return hex string.
         * 
         * @return 64-character hex string
         */
        public String digestHex() {
            return HEX.formatHex(digest());
        }

        @Override
        public void close() {
            if (handle != null) {
                try {
                    NativeLib.BLAKE3_HASHER_FREE.invokeExact(handle);
                } catch (Throwable t) {
                    // Ignore errors during cleanup
                }
                handle = null;
            }
        }
    }
}
