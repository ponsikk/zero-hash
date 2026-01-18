# Zero-Hash Makefile
# Build automation for Rust + Java project

.PHONY: all clean rust java test test-rust test-java

# Default target
all: rust java

# Build Rust library
rust:
	cd rust-ffi && cargo build --release

# Build Java project
java:
	mvn compile -q

# Run all tests
test: test-rust test-java

# Run Rust tests
test-rust:
	cd rust-ffi && cargo test --release

# Run Java tests (requires native library to be built first)
test-java: rust
	mvn test

# Clean build artifacts
clean:
	cd rust-ffi && cargo clean
	mvn clean -q

# Install: copy native library to target
install: rust
	@mkdir -p native
	@if [ -f rust-ffi/target/release/zero_hash_ffi.dll ]; then \
		cp rust-ffi/target/release/zero_hash_ffi.dll native/; \
	elif [ -f rust-ffi/target/release/libzero_hash_ffi.dylib ]; then \
		cp rust-ffi/target/release/libzero_hash_ffi.dylib native/; \
	else \
		cp rust-ffi/target/release/libzero_hash_ffi.so native/; \
	fi
	@echo "Native library installed to native/"

# Development: build and run tests
dev: rust java test

# Release build with package
release: rust java
	mvn package -DskipTests

# Show library info
info:
	@echo "=== Rust Library ==="
	@ls -lh rust-ffi/target/release/*zero_hash_ffi* 2>/dev/null || echo "Not built yet"
	@echo ""
	@echo "=== Java Classes ==="
	@find target/classes -name "*.class" 2>/dev/null | head -10 || echo "Not compiled yet"
