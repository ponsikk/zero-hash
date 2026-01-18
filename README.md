# Zero-Hash

[![Build](https://github.com/ponsikk/zero-hash/actions/workflows/build.yml/badge.svg)](https://github.com/ponsikk/zero-hash/actions)
[![Java](https://img.shields.io/badge/Java-25+-blue.svg)](https://openjdk.org/)
[![Rust](https://img.shields.io/badge/Rust-stable-orange.svg)](https://www.rust-lang.org/)
[![License](https://img.shields.io/badge/License-MIT%2FApache--2.0-green.svg)](LICENSE)

**High-performance BLAKE3 cryptographic hashing for JVM** via Rust + Panama FFI.

## Features

- 🚀 **Fast**: BLAKE3 with SIMD acceleration (AVX2/AVX-512)
- ⚡ **Parallel**: Rayon multithreaded hashing for large data
- 🔒 **Zero-copy**: Direct `ByteBuffer` access without heap allocation
- 🛡️ **Safe**: Panic-safe FFI boundary with `catch_unwind`
- 🔑 **Complete**: One-shot, keyed (MAC), KDF, and streaming modes
- 🆕 **Modern**: Panama FFI (Java 21+), no JNI

## Benchmarks



| Data Size | BLAKE3 | BLAKE3-Parallel | SHA-256 | vs SHA-256 |
|-----------|--------|-----------------|---------|------------|
| 64 KB | 196 MB/s | **405 MB/s** | 23 MB/s | **17x faster** |
| 10 MB | 2.8 GB/s | **4.9 GB/s** | 1.3 GB/s | **3.7x faster** |
| 100 MB | 2.1 GB/s | **3.2 GB/s** | 1.3 GB/s | **2.4x faster** |
| 512 MB | 2.1 GB/s | **3.2 GB/s** | 1.3 GB/s | **2.4x faster** |
| 1 GB | 2.1 GB/s | **3.2 GB/s** | 1.3 GB/s | **2.4x faster** |

**Parallel speedup**: 1.5-2.1x on 4 cores (scales with more cores)

## Quick Start

```java
import io.zerohash.Blake3;

// Simple hashing
byte[] hash = Blake3.hash(data);
String hex = Blake3.hashHex("hello");
// ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f

// Parallel hashing (for large data >128KB)
byte[] hash = Blake3.hashParallel(largeData);

// Keyed hashing (MAC)
byte[] key = new byte[32];
byte[] mac = Blake3.keyedHash(key, data);

// Key derivation (KDF)
byte[] derived = Blake3.deriveKey("my-app context", material);

// Streaming (large files)
try (var hasher = Blake3.hasher()) {
    hasher.update(chunk1);
    hasher.update(chunk2);
    byte[] hash = hasher.digest();
}

// Zero-copy (DirectByteBuffer)
ByteBuffer direct = ByteBuffer.allocateDirect(1024);
byte[] hash = Blake3.hashZeroCopy(direct);
```

## API Reference

### One-shot Hashing

| Method | Description |
|--------|-------------|
| `Blake3.hash(byte[])` | Hash bytes → 32-byte array |
| `Blake3.hash(String)` | Hash UTF-8 string |
| `Blake3.hashHex(...)` | Hash → 64-char hex string |
| `Blake3.hashParallel(byte[])` | Parallel hash (Rayon) |
| `Blake3.hashZeroCopy(ByteBuffer)` | Hash direct buffer |

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
    byte[] hash = hasher.digest();
    // Or: hasher.digest(64) for XOF mode
}
```

### File Hashing

```java
byte[] hash = Blake3.hashFile(Path.of("large-file.bin"));
String hex = Blake3.hashFileHex(path);

// Parallel (loads file into memory, faster for files that fit)
byte[] hash = Blake3.hashFileParallel(path);
```

### Verification

```java
// Verify data
boolean valid = Blake3.verify(data, expectedHash);
boolean valid = Blake3.verify(data, "ea8f163db38682925e...");

// Verify file
boolean valid = Blake3.verifyFile(path, expectedHash);
boolean valid = Blake3.verifyFile(path, "ea8f163db38682925e...");
```

### Hex Utilities

```java
byte[] bytes = Blake3.fromHex("ea8f163db38682925e...");
String hex = Blake3.toHex(bytes);
```

## JMH Benchmarks

Run professional microbenchmarks:

```bash
# Compile and run JMH benchmarks
mvn clean test-compile exec:java \
  -Dexec.mainClass=io.zerohash.benchmark.Blake3Benchmark
```

Benchmarks compare:
- `blake3_singleThreaded` — standard hash
- `blake3_parallel` — Rayon parallel hash
- `blake3_zeroCopy` — DirectByteBuffer hash
- `blake3_streaming` — streaming API
- `sha256_baseline` — SHA-256 for comparison
- `blake3_verify` — hash verification

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

# Run benchmarks
java --enable-preview --enable-native-access=ALL-UNNAMED \
  -Djava.library.path=rust-ffi/target/release \
  -cp target/classes \
  io.zerohash.benchmark.SimpleBenchmark
```

Or use `Makefile`:

```bash
make all      # Build everything
make test     # Run all tests
```

## Project Structure

```
zero-hash/
├── rust-ffi/                 # Rust native library
│   ├── Cargo.toml           # blake3 + rayon
│   ├── ffi-safety-macro/    # Panic-safe FFI macros
│   └── src/
│       ├── lib.rs           # FFI exports
│       └── hasher.rs        # BLAKE3 wrapper
└── src/main/java/io/zerohash/
    ├── Blake3.java          # Public API
    └── internal/
        └── NativeLib.java   # Panama FFI bindings
```

## Why BLAKE3?

| Feature | BLAKE3 | SHA-256 |
|---------|--------|---------|
| Speed | ~3 GB/s | ~0.5 GB/s |
| Parallelizable | ✅ Yes | ❌ No |
| SIMD optimized | ✅ AVX2/AVX-512 | ❌ Limited |
| Streaming | ✅ Yes | ✅ Yes |
| XOF mode | ✅ Yes | ❌ No |
| Security | 256-bit | 256-bit |

## License

MIT OR Apache-2.0
