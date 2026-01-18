# Zero-Hash

High-performance BLAKE3 cryptographic hashing for JVM via Rust + Panama FFI.

[![Java](https://img.shields.io/badge/Java-25+-blue.svg)](https://openjdk.org/)
[![Rust](https://img.shields.io/badge/Rust-stable-orange.svg)](https://www.rust-lang.org/)
[![License](https://img.shields.io/badge/License-MIT%2FApache--2.0-green.svg)](LICENSE)

## Features

- **Fast**: BLAKE3 with SIMD acceleration via native Rust
- **Zero-copy**: Direct access to `ByteBuffer` without copying
- **Safe**: Panic-safe FFI boundary with `catch_unwind`
- **Complete**: One-shot, keyed (MAC), KDF, and streaming modes
- **Modern**: Uses Panama FFI (Java 21+, no JNI)

## Quick Start

```java
// Simple hashing
byte[] hash = Blake3.hash(data);
String hex = Blake3.hashHex("hello");

// Keyed hashing (MAC)
byte[] mac = Blake3.keyedHash(key, data);

// Key derivation (KDF)
byte[] derived = Blake3.deriveKey("my-app context", material);

// Streaming (large files)
try (var hasher = Blake3.hasher()) {
    hasher.update(chunk1);
    hasher.update(chunk2);
    byte[] hash = hasher.finalize();
}

// Zero-copy (DirectByteBuffer)
byte[] hash = Blake3.hashZeroCopy(directBuffer);
```

## Build

### Prerequisites

- Java 25+ with `--enable-preview`
- Rust stable
- Maven 3.8+

### Build Commands

```bash
# Build Rust library
cd rust-ffi && cargo build --release

# Build Java
mvn compile

# Run tests
mvn test

# Or use Makefile
make all      # Build everything
make test     # Run all tests
```

## API Reference

### One-shot Hashing

| Method | Description |
|--------|-------------|
| `Blake3.hash(byte[])` | Hash bytes, returns 32-byte array |
| `Blake3.hash(String)` | Hash UTF-8 string |
| `Blake3.hashHex(...)` | Hash and return 64-char hex string |
| `Blake3.hashZeroCopy(ByteBuffer)` | Hash direct buffer without copy |

### Keyed Hashing (MAC)

```java
byte[] key = new byte[32]; // Must be exactly 32 bytes
byte[] mac = Blake3.keyedHash(key, data);
```

### Key Derivation (KDF)

```java
byte[] derived = Blake3.deriveKey("context", material);
byte[] extended = Blake3.deriveKey("context", material, 64); // Custom length
```

### Streaming API

```java
try (var hasher = Blake3.hasher()) {
    hasher.update("chunk1")
          .update("chunk2")
          .update(bytes);
    byte[] hash = hasher.finalize();
    // Or: hasher.finalize(64) for XOF mode
}
```

## Performance

BLAKE3 is significantly faster than SHA-256:

| Algorithm | Speed (GB/s) |
|-----------|-------------|
| BLAKE3 (this) | ~5-10 |
| SHA-256 | ~0.3-0.5 |

*Benchmarks on modern x86_64 with AVX2/AVX-512*

## License

MIT OR Apache-2.0
