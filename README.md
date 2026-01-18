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

JMH microbenchmarks on GitHub Actions (4 cores): 

### vs SHA-256: кто быстрее?

| Data Size | BLAKE3 Zero-Copy | BLAKE3 Parallel | SHA-256 | Выигрыш |
|-----------|------------------|-----------------|---------|---------|
| 64 KB | 102K ops/s | 38K ops/s | 20K ops/s | **5x** |
| 1 MB | 6.7K ops/s | 4.8K ops/s | 1.1K ops/s | **6x** |
| 10 MB | 632 ops/s | 385 ops/s | 119 ops/s | **5x** |
| 100 MB | 56 ops/s | 26 ops/s | 12 ops/s | **4.7x** |

### Какой метод выбрать?

- **`hashZeroCopy()`** — самый быстрый. Если данные уже в `DirectByteBuffer`, используй его.
- **`hashParallel()`** — хорош для файлов >1MB. Использует все ядра через Rayon.
- **`hash()`** — базовый вариант. Для мелких данных (<64KB) работает отлично.
- **Streaming** — для огромных файлов, которые не влезают в память.

<details>
<summary>Полный вывод JMH бенчмарков</summary>

```
Benchmark                              (dataSize)   Mode  Cnt        Score        Error  Units
Blake3Benchmark.blake3_parallel              1024  thrpt    3  1023163.146 ±  25696.591  ops/s
Blake3Benchmark.blake3_parallel             65536  thrpt    3    38019.450 ±   9965.844  ops/s
Blake3Benchmark.blake3_parallel           1048576  thrpt    3     4768.934 ±    138.943  ops/s
Blake3Benchmark.blake3_parallel          10485760  thrpt    3      385.021 ±     21.870  ops/s
Blake3Benchmark.blake3_parallel         104857600  thrpt    3       25.709 ±      3.151  ops/s
Blake3Benchmark.blake3_singleThreaded        1024  thrpt    3  1065710.801 ±  17087.353  ops/s
Blake3Benchmark.blake3_singleThreaded       65536  thrpt    3    79430.127 ±   1414.476  ops/s
Blake3Benchmark.blake3_singleThreaded     1048576  thrpt    3     4297.553 ±     35.835  ops/s
Blake3Benchmark.blake3_singleThreaded    10485760  thrpt    3      291.466 ±     15.076  ops/s
Blake3Benchmark.blake3_singleThreaded   104857600  thrpt    3       20.409 ±      2.519  ops/s
Blake3Benchmark.blake3_streaming             1024  thrpt    3   877258.269 ± 195766.278  ops/s
Blake3Benchmark.blake3_streaming            65536  thrpt    3    51465.645 ±   9406.399  ops/s
Blake3Benchmark.blake3_streaming          1048576  thrpt    3     2853.753 ±     87.409  ops/s
Blake3Benchmark.blake3_streaming         10485760  thrpt    3      257.822 ±      4.442  ops/s
Blake3Benchmark.blake3_streaming        104857600  thrpt    3       27.139 ±      7.142  ops/s
Blake3Benchmark.blake3_verify                1024  thrpt    3  1064962.317 ±  13139.846  ops/s
Blake3Benchmark.blake3_verify               65536  thrpt    3    80055.871 ±   1396.889  ops/s
Blake3Benchmark.blake3_verify             1048576  thrpt    3     4242.384 ±     38.179  ops/s
Blake3Benchmark.blake3_verify            10485760  thrpt    3      291.438 ±     19.388  ops/s
Blake3Benchmark.blake3_verify           104857600  thrpt    3       20.335 ±      5.414  ops/s
Blake3Benchmark.blake3_zeroCopy              1024  thrpt    3  1094461.599 ±  31262.232  ops/s
Blake3Benchmark.blake3_zeroCopy             65536  thrpt    3   102922.934 ±   7465.248  ops/s
Blake3Benchmark.blake3_zeroCopy           1048576  thrpt    3     6745.847 ±     44.663  ops/s
Blake3Benchmark.blake3_zeroCopy          10485760  thrpt    3      632.321 ±      4.592  ops/s
Blake3Benchmark.blake3_zeroCopy         104857600  thrpt    3       55.940 ±     18.569  ops/s
Blake3Benchmark.sha256_baseline              1024  thrpt    3  1162794.195 ±  95197.388  ops/s
Blake3Benchmark.sha256_baseline             65536  thrpt    3    20184.649 ±    613.383  ops/s
Blake3Benchmark.sha256_baseline           1048576  thrpt    3     1145.848 ±    528.001  ops/s
Blake3Benchmark.sha256_baseline          10485760  thrpt    3      119.116 ±      6.330  ops/s
Blake3Benchmark.sha256_baseline         104857600  thrpt    3       11.824 ±      0.033  ops/s
```

</details>

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
// Basic file hashing (streaming, low memory)
byte[] hash = Blake3.hashFile(Path.of("large-file.bin"));

// Memory-mapped (fastest for files >10MB)
byte[] hash = Blake3.hashFileMmap(path);

// Memory-mapped + parallel Rayon
byte[] hash = Blake3.hashFileMmapParallel(path);

// Load into memory + parallel (for files that fit in RAM)
byte[] hash = Blake3.hashFileParallel(path);
```

### Directory Hashing

```java
// Hash entire directory (sorted, deterministic)
byte[] hash = Blake3.hashDirectory(Path.of("./src"));

// Get hash for each file
Map<String, String> hashes = Blake3.hashDirectoryFilesHex(directory);
// {"src/main/App.java": "abc123...", "src/main/Utils.java": "def456..."}
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
