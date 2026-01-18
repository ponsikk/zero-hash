Zero-Hash: BLAKE3 High-Performance Hasher for JVM
Цель
Создать компактный open-source модуль для высокопроизводительного хеширования данных через BLAKE3 на Rust с Java API через Panama FFI.

Компоненты из zero-copy-validator для переиспользования
Компонент	Путь	Что взять
ffi-safety-macro	rust-ffi/ffi-safety-macro/	Полностью — макросы #[ffi_safe] для catch_unwind
NativeLib.java	src/.../internal/NativeLib.java	Шаблон Panama FFI bindings, loadNativeLibrary()
Project structure	Корень	pom.xml, Makefile, Dockerfile, build scripts
Cargo.toml	rust-ffi/Cargo.toml	Профили релиза (LTO, opt-level 3)
Архитектура проекта
zero-hash/
├── pom.xml                           # Maven, Java 21+
├── Makefile                          # Build automation
├── rust-ffi/
│   ├── Cargo.toml                    # blake3
│   ├── ffi-safety-macro/             # [COPY] from zero-copy-validator
│   │   ├── Cargo.toml
│   │   └── src/lib.rs
│   └── src/
│       ├── lib.rs                    # FFI exports
│       └── hasher.rs                 # BLAKE3 wrapper
└── src/main/java/io/zerohash/
    ├── Blake3.java                   # Public API
    ├── HashResult.java               # Result wrapper
    └── internal/
        └── NativeLib.java            # Panama FFI bindings
API Design (Java)
// Simple hashing
byte[] hash = Blake3.hash(data);                    // 32 bytes
String hex = Blake3.hashHex(data);                  // 64 chars hex
// Keyed hashing (MAC)
byte[] mac = Blake3.keyedHash(key, data);
// Key derivation (KDF)
byte[] derived = Blake3.deriveKey("context", material);
// Streaming (large files)
try (Blake3.Hasher hasher = Blake3.hasher()) {
    hasher.update(chunk1);
    hasher.update(chunk2);
    byte[] hash = hasher.finalize();
}
// Zero-copy (DirectByteBuffer)
byte[] hash = Blake3.hashZeroCopy(directBuffer);
FFI Functions (Rust → Java)
Rust Function	Signature	Description
blake3_hash	(ptr, len, out_ptr) → i32	One-shot hash
blake3_hash_keyed	(key_ptr, data_ptr, len, out_ptr) → i32	Keyed MAC
blake3_derive_key	(ctx_ptr, ctx_len, mat_ptr, mat_len, out_ptr, out_len) → i32	KDF
blake3_hasher_new	() → *mut Hasher	Create streaming hasher
blake3_hasher_update	(hasher, ptr, len) → i32	Update hasher
blake3_hasher_finalize	(hasher, out_ptr, out_len) → i32	Finalize
blake3_hasher_free	(hasher)	Free hasher
Этапы реализации
Phase 1: Project Setup (30 min)
 Создать директорию e:\zero-hash
 Скопировать ffi-safety-macro из zero-copy-validator
 Создать базовый Cargo.toml с blake3 dependency
 Создать pom.xml (Java 21, без Spring)
Phase 2: Rust Implementation (2 hours)
 Реализовать blake3_hash() — one-shot hashing
 Реализовать blake3_hash_keyed() — MAC
 Реализовать blake3_derive_key() — KDF
 Реализовать streaming API (new/update/finalize/free)
 Добавить #[ffi_safe] macros
Phase 3: Java Bindings (2 hours)
 Создать NativeLib.java с Panama bindings
 Создать Blake3.java public API
 Добавить zero-copy методы (DirectByteBuffer)
 Реализовать streaming hasher wrapper
Phase 4: Testing & Benchmarks (1 hour)
 Unit tests для всех методов
 Бенчмарк vs MessageDigest (SHA-256)
 Тест на больших файлах (1GB+)
Phase 5: Documentation (30 min)
 README.md с примерами
 Javadoc
 GitHub badges
Rust Cargo.toml
[package]
name = "zero-hash-ffi"
version = "0.1.0"
edition = "2021"
[lib]
crate-type = ["cdylib", "rlib"]
[dependencies]
blake3 = "1.5"                        # BLAKE3 with SIMD
ffi-safety-macro = { path = "ffi-safety-macro" }
[profile.release]
opt-level = 3
lto = true
codegen-units = 1
panic = "unwind"                      # Required for catch_unwind!
strip = true
Verification Plan
Automated Tests
# Rust tests
cd rust-ffi && cargo test --release
# Java tests
mvn test
Manual Verification
Сравнить результат Blake3.hashHex() с официальной утилитой b3sum
Benchmark: хешировать файл 100MB, сравнить время с MessageDigest SHA-256
Промт для начала разработки
Создай новый проект zero-hash в директории e:\zero-hash.
1. Скопируй ffi-safety-macro из zero-copy-validator
2. Создай Cargo.toml с зависимостью blake3 = "1.5"
3. Реализуй базовую функцию blake3_hash(ptr, len, out_ptr) -> i32
4. Создай простой Java binding через Panama FFI
5. Напиши тест: хешируем строку "hello" и проверяем результат
Используй архитектуру и паттерны из zero-copy-validator используй напрямую либо GIT https://github.com/ponsikk/zero-copy-validator 
Либо Zero-Hash: BLAKE3 High-Performance Hasher for JVM
Цель
Создать компактный open-source модуль для высокопроизводительного хеширования данных через BLAKE3 на Rust с Java API через Panama FFI.

Компоненты из zero-copy-validator для переиспользования
Компонент	Путь	Что взять
ffi-safety-macro	rust-ffi/ffi-safety-macro/	Полностью — макросы #[ffi_safe] для catch_unwind
NativeLib.java	src/.../internal/NativeLib.java	Шаблон Panama FFI bindings, loadNativeLibrary()
Project structure	Корень	pom.xml, Makefile, Dockerfile, build scripts
Cargo.toml	rust-ffi/Cargo.toml	Профили релиза (LTO, opt-level 3)
Архитектура проекта
zero-hash/
├── pom.xml                           # Maven, Java 21+
├── Makefile                          # Build automation
├── rust-ffi/
│   ├── Cargo.toml                    # blake3
│   ├── ffi-safety-macro/             # [COPY] from zero-copy-validator
│   │   ├── Cargo.toml
│   │   └── src/lib.rs
│   └── src/
│       ├── lib.rs                    # FFI exports
│       └── hasher.rs                 # BLAKE3 wrapper
└── src/main/java/io/zerohash/
    ├── Blake3.java                   # Public API
    ├── HashResult.java               # Result wrapper
    └── internal/
        └── NativeLib.java            # Panama FFI bindings
API Design (Java)
// Simple hashing
byte[] hash = Blake3.hash(data);                    // 32 bytes
String hex = Blake3.hashHex(data);                  // 64 chars hex
// Keyed hashing (MAC)
byte[] mac = Blake3.keyedHash(key, data);
// Key derivation (KDF)
byte[] derived = Blake3.deriveKey("context", material);
// Streaming (large files)
try (Blake3.Hasher hasher = Blake3.hasher()) {
    hasher.update(chunk1);
    hasher.update(chunk2);
    byte[] hash = hasher.finalize();
}
// Zero-copy (DirectByteBuffer)
byte[] hash = Blake3.hashZeroCopy(directBuffer);
FFI Functions (Rust → Java)
Rust Function	Signature	Description
blake3_hash	(ptr, len, out_ptr) → i32	One-shot hash
blake3_hash_keyed	(key_ptr, data_ptr, len, out_ptr) → i32	Keyed MAC
blake3_derive_key	(ctx_ptr, ctx_len, mat_ptr, mat_len, out_ptr, out_len) → i32	KDF
blake3_hasher_new	() → *mut Hasher	Create streaming hasher
blake3_hasher_update	(hasher, ptr, len) → i32	Update hasher
blake3_hasher_finalize	(hasher, out_ptr, out_len) → i32	Finalize
blake3_hasher_free	(hasher)	Free hasher
Этапы реализации
Phase 1: Project Setup (30 min)
 Создать директорию e:\zero-hash
 Скопировать ffi-safety-macro из zero-copy-validator
 Создать базовый Cargo.toml с blake3 dependency
 Создать pom.xml (Java 21, без Spring)
Phase 2: Rust Implementation (2 hours)
 Реализовать blake3_hash() — one-shot hashing
 Реализовать blake3_hash_keyed() — MAC
 Реализовать blake3_derive_key() — KDF
 Реализовать streaming API (new/update/finalize/free)
 Добавить #[ffi_safe] macros
Phase 3: Java Bindings (2 hours)
 Создать NativeLib.java с Panama bindings
 Создать Blake3.java public API
 Добавить zero-copy методы (DirectByteBuffer)
 Реализовать streaming hasher wrapper
Phase 4: Testing & Benchmarks (1 hour)
 Unit tests для всех методов
 Бенчмарк vs MessageDigest (SHA-256)
 Тест на больших файлах (1GB+)
Phase 5: Documentation (30 min)
 README.md с примерами
 Javadoc
 GitHub badges
Rust Cargo.toml
[package]
name = "zero-hash-ffi"
version = "0.1.0"
edition = "2021"
[lib]
crate-type = ["cdylib", "rlib"]
[dependencies]
blake3 = "1.5"                        # BLAKE3 with SIMD
ffi-safety-macro = { path = "ffi-safety-macro" }
[profile.release]
opt-level = 3
lto = true
codegen-units = 1
panic = "unwind"                      # Required for catch_unwind!
strip = true
Verification Plan
Automated Tests
# Rust tests
cd rust-ffi && cargo test --release
# Java tests
mvn test
Manual Verification
Сравнить результат Blake3.hashHex() с официальной утилитой b3sum
Benchmark: хешировать файл 100MB, сравнить время с MessageDigest SHA-256
Промт для начала разработки
Создай новый проект zero-hash в директории e:\zero-hash.
1. Скопируй ffi-safety-macro из zero-copy-validator
2. Создай Cargo.toml с зависимостью blake3 = "1.5"
3. Реализуй базовую функцию blake3_hash(ptr, len, out_ptr) -> i32
4. Создай простой Java binding через Panama FFI
5. Напиши тест: хешируем строку "hello" и проверяем результат
Используй архитектуру и паттерны из zero-copy-validator. 