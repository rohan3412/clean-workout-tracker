@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSCachesDirectory
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_t
import platform.posix.memcpy

/** The bytes of an `NSData`. */
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}

/** A copy of the bytes as an `NSData`. */
fun ByteArray.toNSData(): NSData = if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }

/** Runs [block] on the main thread (UIKit's), at once when already there. */
fun onMain(block: () -> Unit) {
    if (platform.Foundation.NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
}

/** The text of an `NSError`, or [fallback]. */
fun NSError?.sentence(fallback: String): String = this?.localizedDescription ?: fallback

/** Files in the app's storage, over `NSFileManager`; paths are plain strings, as the shared code keeps them. Failures give false or null, never throw. */
object Fs {
    private val fm get() = NSFileManager.defaultManager

    /** The app's storage: the Documents folder (the database, the pictures and the animations are under it). */
    val root: String by lazy { NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).first() as String }

    /** The cache, which the system may empty. */
    val cache: String by lazy { NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String }

    /** The temporary folder, with no trailing slash. */
    val tmp: String get() = NSTemporaryDirectory().trimEnd('/')

    fun join(vararg parts: String): String = parts.joinToString("/") { it.trimEnd('/') }

    fun newTmp(name: String): String = join(tmp, "picked", NSUUID().UUIDString, name).also { mkdirs(it.substringBeforeLast('/')) }

    fun exists(path: String): Boolean = fm.fileExistsAtPath(path)

    fun isDirectory(path: String): Boolean = memScoped {
        val dir = alloc<BooleanVar>()
        fm.fileExistsAtPath(path, dir.ptr) && dir.value
    }

    fun isFile(path: String): Boolean = exists(path) && !isDirectory(path)

    fun mkdirs(path: String) { fm.createDirectoryAtPath(path, true, null, null) }

    /** The names in a folder; empty when it is not there. */
    fun list(path: String): List<String> = (fm.contentsOfDirectoryAtPath(path, null) as? List<*>).orEmpty().filterIsInstance<String>()

    /** Every file below [path], as paths relative to it. */
    fun files(path: String): List<String> =
        (fm.subpathsAtPath(path) as? List<*>).orEmpty().filterIsInstance<String>().filter { isFile(join(path, it)) }

    fun delete(path: String) { if (exists(path)) fm.removeItemAtPath(path, null) }

    /** Moves [from] to [to], replacing what is there (as `File.renameTo` does). */
    fun move(from: String, to: String): Boolean {
        if (!exists(from)) return false
        mkdirs(to.substringBeforeLast('/'))
        delete(to)
        return fm.moveItemAtPath(from, to, null)
    }

    fun copy(from: String, to: String): Boolean {
        mkdirs(to.substringBeforeLast('/'))
        delete(to)
        return fm.copyItemAtPath(from, to, null)
    }

    fun read(path: String): ByteArray? = NSData.dataWithContentsOfFile(path)?.toByteArray()

    fun write(path: String, bytes: ByteArray): Boolean {
        mkdirs(path.substringBeforeLast('/'))
        return bytes.toNSData().writeToFile(path, true)
    }

    fun size(path: String): Long =
        ((fm.attributesOfItemAtPath(path, null)?.get(NSFileSize)) as? NSNumber)?.longLongValue ?: 0L

    fun folderBytes(path: String): Long = files(path).sumOf { size(join(path, it)) }

    /** A file bundled with the app (`exercise_info.json`), as text. */
    fun bundledText(name: String): String? {
        val path = NSBundle.mainBundle.pathForResource(name.substringBeforeLast('.'), name.substringAfterLast('.', "")) ?: return null
        return read(path)?.decodeToString()
    }
}

/** The view controller on top, to present a picker or a sheet from: the key window's root, and whatever it presents. */
fun topViewController(): UIViewController? {
    val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
    val scene = scenes.firstOrNull { it.activationState == platform.UIKit.UISceneActivationStateForegroundActive } ?: scenes.firstOrNull()
    val window: UIWindow? = scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() } ?: scene?.windows?.firstOrNull() as? UIWindow
    var top = window?.rootViewController ?: return null
    while (true) top = top.presentedViewController ?: return top
}

/** Runs [block] with room for an `NSError` and gives back what [block] returned and the error, if any. */
inline fun <T> withError(block: (kotlinx.cinterop.CPointer<ObjCObjectVar<NSError?>>) -> T): Pair<T, NSError?> = memScoped {
    val error = alloc<ObjCObjectVar<NSError?>>()
    val result = block(error.ptr)
    result to error.value
}

/** A file's address as an `NSURL`. */
fun fileUrl(path: String): NSURL = NSURL.fileURLWithPath(path)
