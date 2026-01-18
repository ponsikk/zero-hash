package io.zerohash;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Blake3 hashing functionality.
 * 
 * <p>
 * These tests verify correctness against known BLAKE3 test vectors.
 */
@EnabledIf("isNativeLibraryAvailable")
class Blake3Test {

    // Known BLAKE3 hash of "hello"
    private static final String HELLO_HASH = "ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f";

    // Known BLAKE3 hash of empty string
    private static final String EMPTY_HASH = "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262";

    static boolean isNativeLibraryAvailable() {
        try {
            Class.forName("io.zerohash.internal.NativeLib");
            var field = io.zerohash.internal.NativeLib.class.getDeclaredMethod("isLoaded");
            return (boolean) field.invoke(null);
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void checkNativeLibrary() {
        if (!io.zerohash.internal.NativeLib.isLoaded()) {
            System.err.println("WARNING: Native library not loaded, tests will be skipped");
            System.err.println("Build the Rust library first: cd rust-ffi && cargo build --release");
        }
    }

    // ========================================================================
    // hash() tests
    // ========================================================================

    @Test
    @DisplayName("hash() - 'hello' produces known hash")
    void hash_hello() {
        byte[] result = Blake3.hash("hello".getBytes(StandardCharsets.UTF_8));

        assertThat(result).hasSize(32);
        assertThat(Blake3.hashHex("hello")).isEqualTo(HELLO_HASH);
    }

    @Test
    @DisplayName("hash() - empty input produces known hash")
    void hash_empty() {
        byte[] result = Blake3.hash(new byte[0]);

        assertThat(result).hasSize(32);
        assertThat(bytesToHex(result)).isEqualTo(EMPTY_HASH);
    }

    @Test
    @DisplayName("hashHex() - returns 64-char hex string")
    void hashHex_format() {
        String hex = Blake3.hashHex("test");

        assertThat(hex)
                .hasSize(64)
                .matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("hash(String) - UTF-8 encoding")
    void hash_string() {
        String input = "Привет мир! 🌍";

        byte[] fromString = Blake3.hash(input);
        byte[] fromBytes = Blake3.hash(input.getBytes(StandardCharsets.UTF_8));

        assertThat(fromString).isEqualTo(fromBytes);
    }

    @Test
    @DisplayName("hash() - null input throws NullPointerException")
    void hash_null() {
        assertThatNullPointerException()
                .isThrownBy(() -> Blake3.hash((byte[]) null));
    }

    // ========================================================================
    // hashZeroCopy() tests
    // ========================================================================

    @Test
    @DisplayName("hashZeroCopy() - DirectByteBuffer produces same hash")
    void hashZeroCopy_direct() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        ByteBuffer direct = ByteBuffer.allocateDirect(data.length);
        direct.put(data);
        direct.flip();

        byte[] result = Blake3.hashZeroCopy(direct);

        assertThat(bytesToHex(result)).isEqualTo(HELLO_HASH);
    }

    @Test
    @DisplayName("hashZeroCopy() - non-direct buffer throws")
    void hashZeroCopy_nonDirect() {
        ByteBuffer heap = ByteBuffer.allocate(10);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Blake3.hashZeroCopy(heap))
                .withMessage("Buffer must be direct");
    }

    // ========================================================================
    // keyedHash() tests
    // ========================================================================

    @Test
    @DisplayName("keyedHash() - different key produces different hash")
    void keyedHash_differentKey() {
        byte[] key1 = new byte[32];
        byte[] key2 = new byte[32];
        key2[0] = 1;

        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        byte[] mac1 = Blake3.keyedHash(key1, data);
        byte[] mac2 = Blake3.keyedHash(key2, data);

        assertThat(mac1).isNotEqualTo(mac2);
    }

    @Test
    @DisplayName("keyedHash() - same key produces same MAC")
    void keyedHash_deterministic() {
        byte[] key = new byte[32];
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        byte[] mac1 = Blake3.keyedHash(key, data);
        byte[] mac2 = Blake3.keyedHash(key, data);

        assertThat(mac1).isEqualTo(mac2);
    }

    @Test
    @DisplayName("keyedHash() - invalid key size throws")
    void keyedHash_invalidKeySize() {
        byte[] key = new byte[16]; // Should be 32
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> Blake3.keyedHash(key, data))
                .withMessage("Key must be exactly 32 bytes");
    }

    // ========================================================================
    // deriveKey() tests
    // ========================================================================

    @Test
    @DisplayName("deriveKey() - produces deterministic output")
    void deriveKey_deterministic() {
        byte[] material = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] key1 = Blake3.deriveKey("context", material);
        byte[] key2 = Blake3.deriveKey("context", material);

        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("deriveKey() - different context produces different key")
    void deriveKey_contextSeparation() {
        byte[] material = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] key1 = Blake3.deriveKey("context1", material);
        byte[] key2 = Blake3.deriveKey("context2", material);

        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("deriveKey() - custom output length")
    void deriveKey_customLength() {
        byte[] material = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] key64 = Blake3.deriveKey("context", material, 64);
        byte[] key32 = Blake3.deriveKey("context", material, 32);

        assertThat(key64).hasSize(64);
        assertThat(key32).hasSize(32);
        // First 32 bytes should match
        assertThat(key64).startsWith(key32);
    }

    // ========================================================================
    // Streaming API tests
    // ========================================================================

    @Test
    @DisplayName("Hasher - streaming produces same hash as one-shot")
    void hasher_matchesOneShot() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);

        byte[] oneShotHash = Blake3.hash(data);

        byte[] streamingHash;
        try (var hasher = Blake3.hasher()) {
            hasher.update(data);
            streamingHash = hasher.finalize();
        }

        assertThat(streamingHash).isEqualTo(oneShotHash);
    }

    @Test
    @DisplayName("Hasher - multiple updates")
    void hasher_multipleUpdates() {
        try (var hasher = Blake3.hasher()) {
            hasher.update("hel");
            hasher.update("lo");
            String hex = hasher.finalizeHex();

            assertThat(hex).isEqualTo(HELLO_HASH);
        }
    }

    @Test
    @DisplayName("Hasher - chaining")
    void hasher_chaining() {
        try (var hasher = Blake3.hasher()) {
            String hex = hasher
                    .update("hel")
                    .update("lo")
                    .finalizeHex();

            assertThat(hex).isEqualTo(HELLO_HASH);
        }
    }

    @Test
    @DisplayName("Hasher - XOF mode with custom length")
    void hasher_xof() {
        try (var hasher = Blake3.hasher()) {
            hasher.update("hello");
            byte[] output64 = hasher.finalize(64);

            assertThat(output64).hasSize(64);
        }
    }

    @Test
    @DisplayName("Hasher - finalize prevents further updates")
    void hasher_finalizeRejectsUpdates() {
        try (var hasher = Blake3.hasher()) {
            hasher.update("hello");
            hasher.finalize();

            assertThatIllegalStateException()
                    .isThrownBy(() -> hasher.update("world"))
                    .withMessage("Hasher is already finalized");
        }
    }

    @Test
    @DisplayName("Hasher - close prevents use")
    void hasher_closeRejectsUse() {
        Blake3.Hasher hasher = Blake3.hasher();
        hasher.close();

        assertThatIllegalStateException()
                .isThrownBy(() -> hasher.update("hello"))
                .withMessage("Hasher is closed");
    }

    // ========================================================================
    // Performance sanity tests
    // ========================================================================

    @Test
    @DisplayName("Performance - 1MB hashing completes quickly")
    void performance_1mb() {
        byte[] data = new byte[1024 * 1024]; // 1MB

        long start = System.nanoTime();
        Blake3.hash(data);
        long elapsed = System.nanoTime() - start;

        // Should complete in less than 100ms
        assertThat(elapsed).isLessThan(100_000_000L);
    }

    // ========================================================================
    // Utility methods
    // ========================================================================

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
