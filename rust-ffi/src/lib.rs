//! Zero-Hash FFI Library
//!
//! High-performance BLAKE3 hashing exposed via FFI for JVM consumption.
//!
//! # FFI Functions
//!
//! - `blake3_hash` - One-shot hashing
//! - `blake3_hash_keyed` - Keyed hashing (MAC)
//! - `blake3_derive_key` - Key derivation (KDF)
//! - `blake3_hasher_new` - Create streaming hasher
//! - `blake3_hasher_update` - Update streaming hasher
//! - `blake3_hasher_finalize` - Finalize streaming hasher
//! - `blake3_hasher_free` - Free streaming hasher

mod hasher;

use ffi_safety_macro::{ffi_safe, ffi_safe_with_error};
use hasher::{StreamingHasher, error, HASH_SIZE};
use std::slice;

// ============================================================================
// One-shot hashing
// ============================================================================

/// Compute BLAKE3 hash of input data.
///
/// # Parameters
/// - `data`: Pointer to input data (can be null if len is 0)
/// - `len`: Length of input data in bytes
/// - `out`: Pointer to output buffer (must be at least 32 bytes)
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_hash(data: *const u8, len: usize, out: *mut u8) -> i32 {
    hasher::hash(data, len, out)
}

/// Compute BLAKE3 hash using parallel Rayon threads.
/// 
/// This is optimized for large data (>128KB). For smaller data,
/// use `blake3_hash()` instead as the threading overhead may hurt performance.
///
/// # Parameters
/// - `data`: Pointer to input data (can be null if len is 0)
/// - `len`: Length of input data in bytes
/// - `out`: Pointer to output buffer (must be at least 32 bytes)
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_hash_parallel(data: *const u8, len: usize, out: *mut u8) -> i32 {
    hasher::hash_parallel(data, len, out)
}

/// Compute keyed BLAKE3 hash (MAC).
///
/// # Parameters
/// - `key`: Pointer to 32-byte key
/// - `data`: Pointer to input data
/// - `len`: Length of input data in bytes
/// - `out`: Pointer to output buffer (must be at least 32 bytes)
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_hash_keyed(
    key: *const u8,
    data: *const u8,
    len: usize,
    out: *mut u8,
) -> i32 {
    hasher::hash_keyed(key, data, len, out)
}

/// Derive key using BLAKE3 KDF.
///
/// # Parameters
/// - `context`: Pointer to context string (UTF-8)
/// - `context_len`: Length of context in bytes
/// - `material`: Pointer to key material
/// - `material_len`: Length of key material in bytes
/// - `out`: Pointer to output buffer
/// - `out_len`: Desired output length in bytes
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
/// - `-2` on invalid length
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_derive_key(
    context: *const u8,
    context_len: usize,
    material: *const u8,
    material_len: usize,
    out: *mut u8,
    out_len: usize,
) -> i32 {
    hasher::derive_key(context, context_len, material, material_len, out, out_len)
}

// ============================================================================
// Streaming API
// ============================================================================

/// Opaque handle to streaming hasher
pub type HasherHandle = *mut StreamingHasher;

/// Create a new streaming hasher.
///
/// # Returns
/// - Non-null pointer to hasher on success
/// - Null pointer on allocation failure
#[ffi_safe]
#[no_mangle]
pub unsafe extern "C" fn blake3_hasher_new() -> HasherHandle {
    Box::into_raw(Box::new(StreamingHasher::new()))
}

/// Update streaming hasher with more data.
///
/// # Parameters
/// - `hasher`: Handle to streaming hasher
/// - `data`: Pointer to input data
/// - `len`: Length of input data in bytes
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
/// - `-3` on invalid hasher
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_hasher_update(
    hasher: HasherHandle,
    data: *const u8,
    len: usize,
) -> i32 {
    if hasher.is_null() {
        return error::INVALID_HASHER;
    }
    if data.is_null() && len > 0 {
        return error::NULL_POINTER;
    }

    let hasher = &mut *hasher;
    let input = if len == 0 {
        &[]
    } else {
        slice::from_raw_parts(data, len)
    };

    hasher.update(input);
    error::SUCCESS
}

/// Finalize streaming hasher and get hash output.
///
/// # Parameters
/// - `hasher`: Handle to streaming hasher
/// - `out`: Pointer to output buffer
/// - `out_len`: Desired output length in bytes (32 for standard hash)
///
/// # Returns
/// - `0` on success
/// - `-1` on null pointer error
/// - `-2` on invalid length
/// - `-3` on invalid hasher
#[ffi_safe_with_error(-1)]
#[no_mangle]
pub unsafe extern "C" fn blake3_hasher_finalize(
    hasher: HasherHandle,
    out: *mut u8,
    out_len: usize,
) -> i32 {
    if hasher.is_null() {
        return error::INVALID_HASHER;
    }
    if out.is_null() {
        return error::NULL_POINTER;
    }
    if out_len == 0 {
        return error::INVALID_LENGTH;
    }

    let hasher = &*hasher;
    let out_slice = slice::from_raw_parts_mut(out, out_len);

    if out_len <= HASH_SIZE {
        hasher.finalize(out_slice);
    } else {
        hasher.finalize_xof(out_slice);
    }

    error::SUCCESS
}

/// Free streaming hasher.
///
/// # Parameters
/// - `hasher`: Handle to streaming hasher (can be null)
#[no_mangle]
pub unsafe extern "C" fn blake3_hasher_free(hasher: HasherHandle) {
    if !hasher.is_null() {
        drop(Box::from_raw(hasher));
    }
}

// ============================================================================
// Utility functions
// ============================================================================

/// Get the default hash size (32 bytes).
#[no_mangle]
pub extern "C" fn blake3_hash_size() -> usize {
    HASH_SIZE
}

/// Get the key size for keyed hashing (32 bytes).
#[no_mangle]
pub extern "C" fn blake3_key_size() -> usize {
    hasher::KEY_SIZE
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_ffi_hash() {
        let input = b"hello";
        let mut output = [0u8; 32];

        unsafe {
            let result = blake3_hash(input.as_ptr(), input.len(), output.as_mut_ptr());
            assert_eq!(result, 0);
        }

        let expected = hex::decode("ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f").unwrap();
        assert_eq!(output.as_slice(), expected.as_slice());
    }

    #[test]
    fn test_ffi_streaming() {
        unsafe {
            let hasher = blake3_hasher_new();
            assert!(!hasher.is_null());

            let result = blake3_hasher_update(hasher, b"hel".as_ptr(), 3);
            assert_eq!(result, 0);

            let result = blake3_hasher_update(hasher, b"lo".as_ptr(), 2);
            assert_eq!(result, 0);

            let mut output = [0u8; 32];
            let result = blake3_hasher_finalize(hasher, output.as_mut_ptr(), 32);
            assert_eq!(result, 0);

            blake3_hasher_free(hasher);

            let expected = hex::decode("ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f").unwrap();
            assert_eq!(output.as_slice(), expected.as_slice());
        }
    }
}
