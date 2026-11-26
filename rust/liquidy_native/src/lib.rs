use std::ffi::c_void;

type JNIEnv = *const *const *const c_void;
type JClass = *mut c_void;
type JIntArray = *mut c_void;

const IDX_GET_INT_ARRAY_ELEMENTS: usize = 187;
const IDX_RELEASE_INT_ARRAY_ELEMENTS: usize = 195;

#[inline]
unsafe fn get_int_array_elements(env: JNIEnv, array: JIntArray) -> *mut i32 {
    let vtable = *env;
    let f: extern "C" fn(JNIEnv, JIntArray, *mut u8) -> *mut i32 =
        std::mem::transmute(*vtable.add(IDX_GET_INT_ARRAY_ELEMENTS));
    f(env, array, std::ptr::null_mut())
}

#[inline]
unsafe fn release_int_array_elements(env: JNIEnv, array: JIntArray, elems: *mut i32, mode: i32) {
    let vtable = *env;
    let f: extern "C" fn(JNIEnv, JIntArray, *mut i32, i32) =
        std::mem::transmute(*vtable.add(IDX_RELEASE_INT_ARRAY_ELEMENTS));
    f(env, array, elems, mode);
}

#[inline]
fn box_blur_h(src: &[u32], dst: &mut [u32], w: usize, h: usize, r: usize, preserve_alpha: bool) {
    let div = (2 * r + 1) as u32;
    let wm = w - 1;
    for y in 0..h {
        let row = y * w;
        let mut sa = 0u32;
        let mut sr = 0u32;
        let mut sg = 0u32;
        let mut sb = 0u32;

        let first = src[row];
        let fa = (first >> 24) & 0xFF;
        let fw = if preserve_alpha { fa } else { 1 };
        sa += fw * (r as u32 + 1);
        sr += ((first >> 16) & 0xFF) * fw * (r as u32 + 1);
        sg += ((first >> 8) & 0xFF) * fw * (r as u32 + 1);
        sb += (first & 0xFF) * fw * (r as u32 + 1);

        for i in 1..=r {
            let p = src[row + i.min(wm)];
            let pa = (p >> 24) & 0xFF;
            let pw = if preserve_alpha { pa } else { 1 };
            sa += pw;
            sr += ((p >> 16) & 0xFF) * pw;
            sg += ((p >> 8) & 0xFF) * pw;
            sb += (p & 0xFF) * pw;
        }

        for x in 0..w {
            // Must match the Kotlin reference (WallpaperSettings.blurPixels): opaque output unless alpha-weighted,
            // then the averaged alpha with colour divided by the summed weight; fully transparent stays 0.
            let out_a = if preserve_alpha { sa / div } else { 255 };
            dst[row + x] = if out_a == 0 {
                0
            } else {
                let denom = if preserve_alpha { sa } else { div };
                (out_a << 24) | ((sr / denom) << 16) | ((sg / denom) << 8) | (sb / denom)
            };

            let p_add = src[row + (x + r + 1).min(wm)];
            let p_sub = src[row + x.saturating_sub(r)];
            let wa = if preserve_alpha { (p_add >> 24) & 0xFF } else { 1 };
            let ws = if preserve_alpha { (p_sub >> 24) & 0xFF } else { 1 };
            sa = sa + wa - ws;
            sr = sr + ((p_add >> 16) & 0xFF) * wa - ((p_sub >> 16) & 0xFF) * ws;
            sg = sg + ((p_add >> 8) & 0xFF) * wa - ((p_sub >> 8) & 0xFF) * ws;
            sb = sb + (p_add & 0xFF) * wa - (p_sub & 0xFF) * ws;
        }
    }
}

#[inline]
fn box_blur_v(src: &[u32], dst: &mut [u32], w: usize, h: usize, r: usize, preserve_alpha: bool) {
    let div = (2 * r + 1) as u32;
    let hm = h - 1;
    for x in 0..w {
        let mut sa = 0u32;
        let mut sr = 0u32;
        let mut sg = 0u32;
        let mut sb = 0u32;

        let first = src[x];
        let fw = if preserve_alpha { (first >> 24) & 0xFF } else { 1 };
        sa += fw * (r as u32 + 1);
        sr += ((first >> 16) & 0xFF) * fw * (r as u32 + 1);
        sg += ((first >> 8) & 0xFF) * fw * (r as u32 + 1);
        sb += (first & 0xFF) * fw * (r as u32 + 1);

        for i in 1..=r {
            let p = src[i.min(hm) * w + x];
            let pw = if preserve_alpha { (p >> 24) & 0xFF } else { 1 };
            sa += pw;
            sr += ((p >> 16) & 0xFF) * pw;
            sg += ((p >> 8) & 0xFF) * pw;
            sb += (p & 0xFF) * pw;
        }

        for y in 0..h {
            let idx = y * w + x;
            // Must match the Kotlin reference (WallpaperSettings.blurPixels): opaque output unless alpha-weighted,
            // then the averaged alpha with colour divided by the summed weight; fully transparent stays 0.
            let out_a = if preserve_alpha { sa / div } else { 255 };
            dst[idx] = if out_a == 0 {
                0
            } else {
                let denom = if preserve_alpha { sa } else { div };
                (out_a << 24) | ((sr / denom) << 16) | ((sg / denom) << 8) | (sb / denom)
            };

            let p_add = src[(y + r + 1).min(hm) * w + x];
            let p_sub = src[y.saturating_sub(r) * w + x];
            let wa = if preserve_alpha { (p_add >> 24) & 0xFF } else { 1 };
            let ws = if preserve_alpha { (p_sub >> 24) & 0xFF } else { 1 };
            sa = sa + wa - ws;
            sr = sr + ((p_add >> 16) & 0xFF) * wa - ((p_sub >> 16) & 0xFF) * ws;
            sg = sg + ((p_add >> 8) & 0xFF) * wa - ((p_sub >> 8) & 0xFF) * ws;
            sb = sb + (p_add & 0xFF) * wa - (p_sub & 0xFF) * ws;
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn Java_com_gamadev_prism_rust_NativeBridge_blurPixelsNative(
    env: JNIEnv,
    _class: JClass,
    pixels: JIntArray,
    width: i32,
    height: i32,
    radius: i32,
    preserve_alpha: u8,
    passes: i32,
) {
    if pixels.is_null() || width <= 0 || height <= 0 {
        return;
    }
    let w = width as usize;
    let h = height as usize;
    let r = radius.max(1) as usize;
    let len = w * h;
    let ptr = get_int_array_elements(env, pixels);
    if ptr.is_null() {
        return;
    }
    let slice = std::slice::from_raw_parts_mut(ptr as *mut u32, len);
    let mut tmp = vec![0u32; len];
    let pa = preserve_alpha != 0;
    for _ in 0..passes.max(1) {
        box_blur_h(slice, &mut tmp, w, h, r, pa);
        box_blur_v(&tmp, slice, w, h, r, pa);
    }
    release_int_array_elements(env, pixels, ptr, 0);
}

#[no_mangle]
pub extern "C" fn Java_com_gamadev_prism_rust_NativeBridge_springDisplacementNative(
    _env: JNIEnv,
    _class: JClass,
    x0: f32,
    t: f32,
    response: f32,
    damping: f32,
) -> f32 {
    if t <= 0.0 {
        return x0;
    }
    let w = (2.0 * std::f64::consts::PI) / (response as f64);
    let z = (damping as f64).min(0.999);
    let wd = w * (1.0 - z * z).sqrt();
    let td = t as f64;
    let val = (x0 as f64) * (-z * w * td).exp() * ((wd * td).cos() + (z * w / wd) * (wd * td).sin());
    val as f32
}
