package clean.app

import androidx.compose.ui.graphics.ImageBitmap

/** Pictures already cut, by key, the least recently used going first once [limitBytes] is passed (two bytes a dot, as the thumbnails are kept). Used on the main thread only. */
class ImageCache(private val limitBytes: Long) {
    private val map = LinkedHashMap<String, ImageBitmap>()
    private var size = 0L

    private fun sizeOf(v: ImageBitmap) = v.width.toLong() * v.height * 2

    fun get(key: String): ImageBitmap? {
        val v = map.remove(key) ?: return null
        map[key] = v
        return v
    }

    fun put(key: String, value: ImageBitmap) {
        map.remove(key)?.let { size -= sizeOf(it) }
        map[key] = value
        size += sizeOf(value)
        while (size > limitBytes && map.size > 1) {
            val eldest = map.entries.first()
            size -= sizeOf(eldest.value)
            map.remove(eldest.key)
        }
    }
}
