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
