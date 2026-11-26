package com.gamadev.prism.rust

object NativeBridge {
    val available: Boolean = runCatching {
        System.loadLibrary("liquidy_native")
        true
    }.getOrDefault(false)

    @JvmStatic
    external fun blurPixelsNative(
        pixels: IntArray,
        width: Int,
        height: Int,
        radius: Int,
        preserveAlpha: Boolean,
        passes: Int,
    )

    @JvmStatic
    external fun springDisplacementNative(
        x0: Float,
        t: Float,
        response: Float,
        damping: Float,
    ): Float
}
