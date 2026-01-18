//! BLAKE3 Hasher Implementation
//!
//! Provides the core hashing logic wrapped for FFI-safe operations.

use blake3::{Hash, Hasher as B3Hasher};
use std::slice;

/// BLAKE3 hash output size in bytes (256 bits)
pub const HASH_SIZE: usize = 32;

/// BLAKE3 key size in bytes (256 bits)
pub const KEY_SIZE: usize = 32;

/// Error codes for FFI functions
pub mod error {
    pub const SUCCESS: i32 = 0;
    pub const NULL_POINTER: i32 = -1;
    pub const INVALID_LENGTH: i32 = -2;
    pub const INVALID_HASHER: i32 = -3;
}

/// Compute BLAKE3 hash of input data
///
/// # Safety
/// - `data` must be valid for `len` bytes
/// - `out` must be valid for at least 32 bytes
pub unsafe fn hash(data: *const u8, len: usize, out: *mut u8) -> i32 {
    if data.is_null() && len > 0 {
        return error::NULL_POINTER;
    }
    if out.is_null() {
        return error::NULL_POINTER;
    }

    let input = if len == 0 {
        &[]
    } else {
        slice::from_raw_parts(data, len)
    };

    let hash: Hash = blake3::hash(input);
    let hash_bytes = hash.as_bytes();

    std::ptr::copy_nonoverlapping(hash_bytes.as_ptr(), out, HASH_SIZE);

    error::SUCCESS
}

/// Compute keyed BLAKE3 hash (MAC)
///
/// # Safety
/// - `key` must be valid for 32 bytes
/// - `data` must be valid for `len` bytes
/// - `out` must be valid for at least 32 bytes
pub unsafe fn hash_keyed(key: *const u8, data: *const u8, len: usize, out: *mut u8) -> i32 {
    if key.is_null() {
        return error::NULL_POINTER;
    }
    if data.is_null() && len > 0 {
        return error::NULL_POINTER;
    }
    if out.is_null() {
        return error::NULL_POINTER;
    }

    let key_slice = slice::from_raw_parts(key, KEY_SIZE);
    let key_array: [u8; KEY_SIZE] = key_slice.try_into().unwrap();

    let input = if len == 0 {
        &[]
    } else {
        slice::from_raw_parts(data, len)
    };

    let hash = blake3::keyed_hash(&key_array, input);
    let hash_bytes = hash.as_bytes();

    std::ptr::copy_nonoverlapping(hash_bytes.as_ptr(), out, HASH_SIZE);

    error::SUCCESS
}

/// Derive key using BLAKE3 KDF
///
/// # Safety
/// - `context` must be valid for `context_len` bytes
/// - `material` must be valid for `material_len` bytes
/// - `out` must be valid for `out_len` bytes
pub unsafe fn derive_key(
    context: *const u8,
    context_len: usize,
    material: *const u8,
    material_len: usize,
    out: *mut u8,
    out_len: usize,
) -> i32 {
    if context.is_null() && context_len > 0 {
        return error::NULL_POINTER;
    }
    if material.is_null() && material_len > 0 {
        return error::NULL_POINTER;
    }
    if out.is_null() {
        return error::NULL_POINTER;
    }
    if out_len == 0 {
        return error::INVALID_LENGTH;
    }

    let context_str = if context_len == 0 {
        ""
    } else {
        let context_slice = slice::from_raw_parts(context, context_len);
        match std::str::from_utf8(context_slice) {
            Ok(s) => s,
            Err(_) => return error::INVALID_LENGTH,
        }
    };

    let material_slice = if material_len == 0 {
        &[]
    } else {
        slice::from_raw_parts(material, material_len)
    };

    // Use BLAKE3's derive_key with OutputReader for arbitrary length
    let mut hasher = B3Hasher::new_derive_key(context_str);
    hasher.update(material_slice);
    let mut output = hasher.finalize_xof();
    
    let out_slice = slice::from_raw_parts_mut(out, out_len);
    output.fill(out_slice);

    error::SUCCESS
}

/// Streaming hasher wrapper for FFI
pub struct StreamingHasher {
    inner: B3Hasher,
}

impl StreamingHasher {
    /// Create a new streaming hasher
    pub fn new() -> Self {
        StreamingHasher {
            inner: B3Hasher::new(),
        }
    }

    /// Update hasher with more data
    pub fn update(&mut self, data: &[u8]) {
        self.inner.update(data);
    }

    /// Finalize and get hash output
    pub fn finalize(&self, out: &mut [u8]) {
        let hash = self.inner.finalize();
        let len = out.len().min(HASH_SIZE);
        out[..len].copy_from_slice(&hash.as_bytes()[..len]);
    }

    /// Finalize with extended output (XOF mode)
    pub fn finalize_xof(&self, out: &mut [u8]) {
        let mut output = self.inner.finalize_xof();
        output.fill(out);
    }
}

impl Default for StreamingHasher {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_hello() {
        let input = b"hello";
        let mut output = [0u8; HASH_SIZE];

        unsafe {
            let result = hash(input.as_ptr(), input.len(), output.as_mut_ptr());
            assert_eq!(result, error::SUCCESS);
        }

        // Known BLAKE3 hash of "hello"
        let expected = hex::decode("ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f").unwrap();
        assert_eq!(output.as_slice(), expected.as_slice());
    }

    #[test]
    fn test_hash_empty() {
        let mut output = [0u8; HASH_SIZE];

        unsafe {
            let result = hash(std::ptr::null(), 0, output.as_mut_ptr());
            assert_eq!(result, error::SUCCESS);
        }

        // Known BLAKE3 hash of empty string
        let expected = hex::decode("af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262").unwrap();
        assert_eq!(output.as_slice(), expected.as_slice());
    }

    #[test]
    fn test_keyed_hash() {
        let key = [0u8; KEY_SIZE];
        let input = b"hello";
        let mut output = [0u8; HASH_SIZE];

        unsafe {
            let result = hash_keyed(key.as_ptr(), input.as_ptr(), input.len(), output.as_mut_ptr());
            assert_eq!(result, error::SUCCESS);
        }

        // Keyed hash should be different from regular hash
        let regular = hex::decode("ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f").unwrap();
        assert_ne!(output.as_slice(), regular.as_slice());
    }

    #[test]
    fn test_derive_key() {
        let context = b"zero-hash example";
        let material = b"secret key material";
        let mut output = [0u8; 64];

        unsafe {
            let result = derive_key(
                context.as_ptr(),
                context.len(),
                material.as_ptr(),
                material.len(),
                output.as_mut_ptr(),
                output.len(),
            );
            assert_eq!(result, error::SUCCESS);
        }

        // First 32 bytes should match if we derive again
        let mut output2 = [0u8; 64];
        unsafe {
            derive_key(
                context.as_ptr(),
                context.len(),
                material.as_ptr(),
                material.len(),
                output2.as_mut_ptr(),
                output2.len(),
            );
        }
        assert_eq!(output, output2);
    }

    #[test]
    fn test_streaming_hasher() {
        let mut hasher = StreamingHasher::new();
        hasher.update(b"hel");
        hasher.update(b"lo");

        let mut output = [0u8; HASH_SIZE];
        hasher.finalize(&mut output);

        // Should match one-shot hash of "hello"
        let expected = hex::decode("ea8f163db38682925e4491c5e58d4bb3506ef8c14eb78a86e908c5624a67200f").unwrap();
        assert_eq!(output.as_slice(), expected.as_slice());
    }
}
