package io.zerohash.internal;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/**
 * Panama FFI bindings for zero-hash-ffi native library.
 * 
 * <p>This class handles loading the native library and provides
 * low-level method handles to the FFI functions.
 */
public final class NativeLib {

    private static final boolean LOADED;
    private static final Throwable LOAD_ERROR;
    
    // Linker for FFI calls
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;
    
    // Method handles for FFI functions
    public static final MethodHandle BLAKE3_HASH;
    public static final MethodHandle BLAKE3_HASH_KEYED;
    public static final MethodHandle BLAKE3_DERIVE_KEY;
    public static final MethodHandle BLAKE3_HASHER_NEW;
    public static final MethodHandle BLAKE3_HASHER_UPDATE;
    public static final MethodHandle BLAKE3_HASHER_FINALIZE;
    public static final MethodHandle BLAKE3_HASHER_FREE;
    public static final MethodHandle BLAKE3_HASH_SIZE;
    public static final MethodHandle BLAKE3_KEY_SIZE;
    
    static {
        boolean loaded = false;
        Throwable error = null;
        SymbolLookup lookup = null;
        
        try {
            lookup = loadNativeLibrary();
            loaded = true;
        } catch (Throwable t) {
            error = t;
        }
        
        LOADED = loaded;
        LOAD_ERROR = error;
        LOOKUP = lookup;
        
        if (LOADED) {
            // blake3_hash(data: *const u8, len: usize, out: *mut u8) -> i32
            BLAKE3_HASH = downcall("blake3_hash",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,    // data
                    ValueLayout.JAVA_LONG,  // len (usize)
                    ValueLayout.ADDRESS     // out
                ));
            
            // blake3_hash_keyed(key: *const u8, data: *const u8, len: usize, out: *mut u8) -> i32
            BLAKE3_HASH_KEYED = downcall("blake3_hash_keyed",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,    // key
                    ValueLayout.ADDRESS,    // data
                    ValueLayout.JAVA_LONG,  // len
                    ValueLayout.ADDRESS     // out
                ));
            
            // blake3_derive_key(ctx, ctx_len, mat, mat_len, out, out_len) -> i32
            BLAKE3_DERIVE_KEY = downcall("blake3_derive_key",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,    // context
                    ValueLayout.JAVA_LONG,  // context_len
                    ValueLayout.ADDRESS,    // material
                    ValueLayout.JAVA_LONG,  // material_len
                    ValueLayout.ADDRESS,    // out
                    ValueLayout.JAVA_LONG   // out_len
                ));
            
            // blake3_hasher_new() -> *mut Hasher
            BLAKE3_HASHER_NEW = downcall("blake3_hasher_new",
                FunctionDescriptor.of(ValueLayout.ADDRESS));
            
            // blake3_hasher_update(hasher, data, len) -> i32
            BLAKE3_HASHER_UPDATE = downcall("blake3_hasher_update",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,    // hasher
                    ValueLayout.ADDRESS,    // data
                    ValueLayout.JAVA_LONG   // len
                ));
            
            // blake3_hasher_finalize(hasher, out, out_len) -> i32
            BLAKE3_HASHER_FINALIZE = downcall("blake3_hasher_finalize",
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,    // hasher
                    ValueLayout.ADDRESS,    // out
                    ValueLayout.JAVA_LONG   // out_len
                ));
            
            // blake3_hasher_free(hasher)
            BLAKE3_HASHER_FREE = downcall("blake3_hasher_free",
                FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
            
            // blake3_hash_size() -> usize
            BLAKE3_HASH_SIZE = downcall("blake3_hash_size",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG));
            
            // blake3_key_size() -> usize
            BLAKE3_KEY_SIZE = downcall("blake3_key_size",
                FunctionDescriptor.of(ValueLayout.JAVA_LONG));
        } else {
            BLAKE3_HASH = null;
            BLAKE3_HASH_KEYED = null;
            BLAKE3_DERIVE_KEY = null;
            BLAKE3_HASHER_NEW = null;
            BLAKE3_HASHER_UPDATE = null;
            BLAKE3_HASHER_FINALIZE = null;
            BLAKE3_HASHER_FREE = null;
            BLAKE3_HASH_SIZE = null;
            BLAKE3_KEY_SIZE = null;
        }
    }
    
    private NativeLib() {
        // Utility class - no instantiation
    }
    
    /**
     * Check if the native library was loaded successfully.
     */
    public static boolean isLoaded() {
        return LOADED;
    }
    
    /**
     * Get the error that occurred during library loading, if any.
     */
    public static Throwable getLoadError() {
        return LOAD_ERROR;
    }
    
    /**
     * Ensure the native library is loaded.
     * @throws IllegalStateException if the library failed to load
     */
    public static void ensureLoaded() {
        if (!LOADED) {
            throw new IllegalStateException(
                "Failed to load zero-hash native library", LOAD_ERROR);
        }
    }
    
    /**
     * Load the native library from various locations.
     */
    private static SymbolLookup loadNativeLibrary() {
        String libName = getLibraryName();
        
        // 1. Try java.library.path (standard way)
        try {
            System.loadLibrary("zero_hash_ffi");
            return SymbolLookup.loaderLookup();
        } catch (UnsatisfiedLinkError ignored) {
        }
        
        // 2. Try loading from target/release directory (development)
        Path devPath = Path.of("rust-ffi/target/release", libName);
        if (devPath.toFile().exists()) {
            System.load(devPath.toAbsolutePath().toString());
            return SymbolLookup.loaderLookup();
        }
        
        // 3. Try loading from current directory
        Path curPath = Path.of(libName);
        if (curPath.toFile().exists()) {
            System.load(curPath.toAbsolutePath().toString());
            return SymbolLookup.loaderLookup();
        }
        
        // 4. Try loading from native/ directory
        Path nativePath = Path.of("native", libName);
        if (nativePath.toFile().exists()) {
            System.load(nativePath.toAbsolutePath().toString());
            return SymbolLookup.loaderLookup();
        }
        
        throw new UnsatisfiedLinkError(
            "Cannot find native library: " + libName + 
            " (searched: java.library.path, rust-ffi/target/release/, ./, native/)");
    }
    
    /**
     * Get platform-specific library name.
     */
    private static String getLibraryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "zero_hash_ffi.dll";
        } else if (os.contains("mac")) {
            return "libzero_hash_ffi.dylib";
        } else {
            return "libzero_hash_ffi.so";
        }
    }
    
    /**
     * Create a downcall method handle for a native function.
     */
    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return LOOKUP.find(name)
            .map(symbol -> LINKER.downcallHandle(symbol, descriptor))
            .orElseThrow(() -> new UnsatisfiedLinkError("Symbol not found: " + name));
    }
}
