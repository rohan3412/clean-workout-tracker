@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package clean.app.ios

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSItemProvider
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithoutUI
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.systemBackgroundColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIDatePicker
import platform.UIKit.UIDatePickerMode
import platform.UIKit.UIDatePickerStyle
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIViewController
import platform.UIKit.sheetPresentationController
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UINavigationController
import platform.UIKit.UIBarButtonSystemItem
import platform.UIKit.navigationItem

/**
 * What a picked file or folder is on the iPhone, as the opaque `String` the shared code keeps: a path in the app's own storage (a file the
 * picker copied in), or `bm:` and a security-scoped bookmark (a folder, or the file a save went to), which stays good across restarts.
 */
object IosHandles {
    private const val BOOKMARK = "bm:"

    fun bookmarkOf(url: NSURL): String? {
        val started = url.startAccessingSecurityScopedResource()
        try {
            val (data, _) = withError { url.bookmarkDataWithOptions(0u, null, null, it) }
            return data?.base64EncodedStringWithOptions(0u)?.let { BOOKMARK + it }
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    /** The address a `bm:` handle names (null when it is gone), already usable after [access]. */
    fun resolve(handle: String): NSURL? {
        if (!handle.startsWith(BOOKMARK)) return fileUrl(handle)
        val data = NSData.create(base64EncodedString = handle.removePrefix(BOOKMARK), options = 0u) ?: return null
        val (url, _) = withError { NSURL.URLByResolvingBookmarkData(data, NSURLBookmarkResolutionWithoutUI, null, null, it) }
        return url
    }

    /** Runs [block] with access to what [handle] names, ending the access after. Null when the handle cannot be resolved. */
    fun <R> access(handle: String, block: (NSURL) -> R): R? {
        val url = resolve(handle) ?: return null
        val started = url.startAccessingSecurityScopedResource()
        try {
            return block(url)
        } finally {
            if (started) url.stopAccessingSecurityScopedResource()
        }
    }

    fun read(handle: String): ByteArray? =
        if (!handle.startsWith(BOOKMARK)) Fs.read(handle) else access(handle) { NSData.dataWithContentsOfURL(it)?.toByteArray() }

    /** Writes [bytes] to the file [handle] names, in place (the file is there already: the save made it). */
    fun write(handle: String, bytes: ByteArray): Boolean =
        if (!handle.startsWith(BOOKMARK)) Fs.write(handle, bytes) else access(handle) { bytes.toNSData().writeToURL(it, false) } ?: false

    fun folderName(handle: String): String? = access(handle) { it.lastPathComponent }

    /** A path to read the file from, copied into the cache when it has to be (a `bm:` handle); the copy is the caller's to delete. */
    fun stage(handle: String, name: String): String? {
        if (!handle.startsWith(BOOKMARK)) return handle
        val to = Fs.join(Fs.tmp, name)
        return access(handle) { url -> url.path?.let { Fs.copy(it, to) } }?.let { if (it) to else null }
    }
}

/** The system's pickers and sheets. Each delegate is kept in a field, because UIKit holds delegates weakly. */
class IosPickers {
    private var delegate: Any? = null

    private fun present(vc: UIViewController) = onMain { topViewController()?.presentViewController(vc, true, null) }

    // ---- Files

    private class DocumentDelegate(val onPick: (List<NSURL>) -> Unit) : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            onPick(didPickDocumentsAtURLs.filterIsInstance<NSURL>())
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { onPick(emptyList()) }
    }

    /** Asks where to save a file called [name]; the file is made empty there and its handle given (null when cancelled), for the caller to write into. */
    fun saveFile(name: String, onPicked: (String?) -> Unit) = onMain {
        val temp = Fs.newTmp(name)
        Fs.write(temp, ByteArray(0))
        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(fileUrl(temp)), asCopy = true)
        val d = DocumentDelegate { urls -> delegate = null; Fs.delete(temp); onPicked(urls.firstOrNull()?.let { IosHandles.bookmarkOf(it) }) }
        delegate = d
        picker.delegate = d
        present(picker)
    }

    /** Asks for a file of any kind; it is copied into the app's temporary folder and its path given. */
    fun openFile(onPicked: (String?) -> Unit) = onMain {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem!!), asCopy = true)
        val d = DocumentDelegate { urls -> delegate = null; onPicked(urls.firstOrNull()?.path?.let { copyIn(it) }) }
        delegate = d
        picker.delegate = d
        present(picker)
    }

    /** The picker's copy moved to a folder of ours that is cleared at launch, under its own name. */
    private fun copyIn(path: String): String? {
        val to = Fs.newTmp(path.substringAfterLast('/'))
        return if (Fs.move(path, to)) to else path
    }

    /** Asks for a folder; its handle is a bookmark that stays good across restarts. */
    fun pickFolder(onPicked: (String?) -> Unit) = onMain {
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder!!), asCopy = false)
        val d = DocumentDelegate { urls -> delegate = null; onPicked(urls.firstOrNull()?.let { IosHandles.bookmarkOf(it) }) }
        delegate = d
        picker.delegate = d
        present(picker)
    }

    // ---- Pictures

    private class PhotoDelegate(val onPick: (List<String>) -> Unit) : NSObject(), PHPickerViewControllerDelegateProtocol {
        override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
            picker.dismissViewControllerAnimated(true, null)
            val providers = didFinishPicking.filterIsInstance<PHPickerResult>().map { it.itemProvider }
            val paths = ArrayList<String>()
            fun next(i: Int) {
                if (i >= providers.size) { onMain { onPick(paths) }; return }
                providers[i].loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
                    if (data != null) {
                        val path = Fs.newTmp("picked.img")
                        if (data.writeToURL(fileUrl(path), true)) paths += path
                    }
                    next(i + 1)
                }
            }
            next(0)
        }
    }

    /** The system photo picker (no permission needed): one picture or several; their paths, copied into the temporary folder. */
    fun pickPhotos(multiple: Boolean, onPicked: (List<String>) -> Unit) = onMain {
        val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary())
        config.selectionLimit = if (multiple) 0 else 1
        config.filter = PHPickerFilter.imagesFilter
        val picker = PHPickerViewController(config)
        val d = PhotoDelegate { paths -> delegate = null; onPicked(paths) }
        delegate = d
        picker.delegate = d
        present(picker)
    }

    private class CameraDelegate(val onShot: (UIImage?) -> Unit) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
            picker.dismissViewControllerAnimated(true, null)
            onShot(didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage)
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            picker.dismissViewControllerAnimated(true, null)
            onShot(null)
        }
    }

    /** The camera; false when the device has none. The picture is written as a JPEG in the temporary folder and its path given. */
    fun camera(onShot: (String?) -> Unit): Boolean {
        if (!UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) return false
        onMain {
            val picker = UIImagePickerController()
            picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            val d = CameraDelegate { image ->
                delegate = null
                val path = Fs.newTmp("capture.jpg")
                val data = image?.let { UIImageJPEGRepresentation(it, 0.95) }
                onShot(if (data != null && data.writeToURL(fileUrl(path), true)) path else null)
            }
            delegate = d
            picker.delegate = d
            present(picker)
        }
        return true
    }

    // ---- The share sheet

    fun shareItems(items: List<Any>) = onMain {
        val top = topViewController() ?: return@onMain
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        // On an iPad the sheet is a popover and needs something to point at.
        sheet.popoverPresentationController?.sourceView = top.view
        top.view.bounds.useContents {
            sheet.popoverPresentationController?.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
        top.presentViewController(sheet, true, null)
    }

    // ---- Date and time

    private class Tap(val action: () -> Unit) : NSObject() {
        @ObjCAction fun tapped() { action() }
    }

    private val taps = ArrayList<Tap>()

    /** A sheet with the wheels of [mode] and Cancel and Done above them; [onDone] gets the date chosen. */
    private fun wheels(mode: UIDatePickerMode, initial: NSDate, maximum: NSDate?, onDone: (NSDate) -> Unit) = onMain {
        val content = UIViewController(nibName = null, bundle = null)
        content.view.backgroundColor = UIColor.systemBackgroundColor
        val picker = UIDatePicker()
        picker.datePickerMode = mode
        picker.preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleWheels
        picker.date = initial
        if (maximum != null) picker.maximumDate = maximum
        val nav = UINavigationController(rootViewController = content)
        val done = Tap { nav.dismissViewControllerAnimated(true, null); taps.clear(); onDone(picker.date) }
        val cancel = Tap { nav.dismissViewControllerAnimated(true, null); taps.clear() }
        taps += done; taps += cancel
        content.navigationItem.rightBarButtonItem = UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemDone, target = done, action = platform.objc.sel_registerName("tapped"))
        content.navigationItem.leftBarButtonItem = UIBarButtonItem(barButtonSystemItem = UIBarButtonSystemItem.UIBarButtonSystemItemCancel, target = cancel, action = platform.objc.sel_registerName("tapped"))
        content.view.addSubview(picker)
        picker.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activateConstraints(
            listOf(
                picker.centerXAnchor.constraintEqualToAnchor(content.view.centerXAnchor),
                picker.topAnchor.constraintEqualToAnchor(content.view.safeAreaLayoutGuide.topAnchor),
            ),
        )
        nav.modalPresentationStyle = UIModalPresentationPageSheet
        nav.sheetPresentationController?.detents = listOf(UISheetPresentationControllerDetent.mediumDetent())
        present(nav)
    }

    fun pickDateTime(initial: NSDate, maximum: NSDate, onPick: (NSDate) -> Unit) = wheels(UIDatePickerMode.UIDatePickerModeDateAndTime, initial, maximum, onPick)

    fun pickTime(initial: NSDate, onPick: (NSDate) -> Unit) = wheels(UIDatePickerMode.UIDatePickerModeTime, initial, null, onPick)
}
