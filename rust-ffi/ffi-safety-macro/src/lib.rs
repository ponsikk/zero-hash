use proc_macro::TokenStream;
use quote::quote;
use syn::{parse_macro_input, ItemFn};

/// Proc macro that wraps FFI functions with panic safety.
///
/// Automatically adds `catch_unwind` around the function body to prevent
/// panics from unwinding across FFI boundary (which causes UB and crashes JVM).
///
/// # Usage
///
/// ```rust
/// #[ffi_safe]
/// #[no_mangle]
/// pub unsafe extern "C" fn blake3_hash(ptr: *const u8, len: usize, out: *mut u8) -> i32 {
///     // Function body that might panic
///     hash_impl(ptr, len, out)
/// }
/// ```
#[proc_macro_attribute]
pub fn ffi_safe(_attr: TokenStream, item: TokenStream) -> TokenStream {
    let input = parse_macro_input!(item as ItemFn);

    let ItemFn {
        attrs,
        vis,
        sig,
        block,
    } = input;

    let fn_name = &sig.ident;
    let fn_name_str = fn_name.to_string();

    // Generate panic-safe wrapper
    let expanded = quote! {
        #(#attrs)*
        #vis #sig {
            match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                #block
            })) {
                Ok(result) => result,
                Err(panic_info) => {
                    eprintln!(
                        "🔥 PANIC caught in FFI function '{}': {:?}",
                        #fn_name_str,
                        panic_info
                    );
                    Default::default()
                }
            }
        }
    };

    TokenStream::from(expanded)
}

/// Alternative macro for FFI functions that return error codes.
///
/// Instead of returning Default::default(), returns a specific error code.
///
/// # Usage
///
/// ```rust
/// #[ffi_safe_with_error(-1)]
/// #[no_mangle]
/// pub unsafe extern "C" fn blake3_hash(...) -> i32 {
///     // Function body
/// }
/// ```
#[proc_macro_attribute]
pub fn ffi_safe_with_error(attr: TokenStream, item: TokenStream) -> TokenStream {
    let error_code = parse_macro_input!(attr as syn::LitInt);
    let input = parse_macro_input!(item as ItemFn);

    let ItemFn {
        attrs,
        vis,
        sig,
        block,
    } = input;

    let fn_name = &sig.ident;
    let fn_name_str = fn_name.to_string();

    let expanded = quote! {
        #(#attrs)*
        #vis #sig {
            match std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
                #block
            })) {
                Ok(result) => result,
                Err(panic_info) => {
                    eprintln!(
                        "🔥 PANIC caught in FFI function '{}': {:?}",
                        #fn_name_str,
                        panic_info
                    );
                    #error_code
                }
            }
        }
    };

    TokenStream::from(expanded)
}
