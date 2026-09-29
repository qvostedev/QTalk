package com.qvoste.qtalk

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window
import javax.swing.Timer

private const val DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1 = 19
private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_TEXT_COLOR = 36
private const val WM_GETICON = 0x007F
private const val WM_SETICON = 0x0080
private const val GA_ROOT = 2
private const val GWL_EXSTYLE = -20
private const val GCLP_HICON = -14
private const val GCLP_HICONSM = -34
private const val ICON_SMALL = 0L
private const val ICON_BIG = 1L
private const val ICON_SMALL2 = 2L
private const val CAPTION_ICON_SIZE = 16
private const val WS_EX_DLGMODALFRAME = 0x00000001L
private const val SWP_NOSIZE = 0x0001
private const val SWP_NOMOVE = 0x0002
private const val SWP_NOZORDER = 0x0004
private const val SWP_NOACTIVATE = 0x0010
private const val SWP_FRAMECHANGED = 0x0020
private const val SW_HIDE = 0
private const val SW_SHOW = 5

// DWM uses COLORREF (0x00BBGGRR), not the usual RGB byte order.
private const val QTALK_CAPTION_COLOR = 0x00100D0B
private const val LIGHT_TEXT_COLOR = 0x00FFFFFF

private interface DwmApi : StdCallLibrary {
    fun DwmSetWindowAttribute(
        windowHandle: Pointer,
        attribute: Int,
        value: Pointer,
        valueSize: Int
    ): Int
}

private interface User32Api : StdCallLibrary {
    fun CreateIcon(
        instance: Pointer?,
        width: Int,
        height: Int,
        planes: Byte,
        bitsPerPixel: Byte,
        andMask: ByteArray,
        xorMask: ByteArray
    ): Pointer?

    fun DestroyIcon(icon: Pointer): Boolean

    fun GetAncestor(windowHandle: Pointer, flags: Int): Pointer?

    fun GetWindowLongPtrW(windowHandle: Pointer, index: Int): Pointer?

    fun SetWindowLongPtrW(windowHandle: Pointer, index: Int, newValue: Pointer): Pointer?

    fun GetClassLongPtrW(windowHandle: Pointer, index: Int): Pointer?

    fun SetClassLongPtrW(windowHandle: Pointer, index: Int, newValue: Pointer?): Pointer?

    fun SendMessageW(
        windowHandle: Pointer,
        message: Int,
        parameter: Pointer?,
        value: Pointer?
    ): Pointer?

    fun SetWindowPos(
        windowHandle: Pointer,
        insertAfter: Pointer?,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        flags: Int
    ): Boolean

    fun ShowWindow(windowHandle: Pointer, command: Int): Boolean

}

private val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

/**
 * Applies QTalk colors to the native Windows caption without replacing the system frame.
 *
 * The OS check happens before DwmApi is loaded, so this file remains safe on Linux.
 */
internal fun applyQTalkWindowsTitleBar(window: Window): () -> Unit {
    if (!isWindows) return {}

    return runCatching {
        val dwmApi = Native.load("dwmapi", DwmApi::class.java)
        val user32Api = Native.load("user32", User32Api::class.java)
        val componentHandle = Native.getComponentPointer(window)
        val windowHandle = user32Api.GetAncestor(componentHandle, GA_ROOT) ?: componentHandle

        val qTalkLargeIcon = user32Api.SendMessageW(
            windowHandle,
            WM_GETICON,
            Pointer(ICON_BIG),
            null
        )
        val transparentCaptionIcon = checkNotNull(
            user32Api.CreateIcon(
                null,
                CAPTION_ICON_SIZE,
                CAPTION_ICON_SIZE,
                1,
                32,
                ByteArray(CAPTION_ICON_SIZE * CAPTION_ICON_SIZE / 8) { 0xFF.toByte() },
                ByteArray(CAPTION_ICON_SIZE * CAPTION_ICON_SIZE * 4)
            )
        ) {
            "Windows failed to create a transparent caption icon"
        }

        // Windows falls back from the small caption slot to the class' large icon.
        // A real transparent HICON prevents that fallback while the large QTalk icon
        // remains available to the taskbar.
        val previousCaptionIcon = user32Api.SendMessageW(
            windowHandle,
            WM_SETICON,
            Pointer(ICON_SMALL),
            transparentCaptionIcon
        )
        val previousSecondaryCaptionIcon = user32Api.SendMessageW(
            windowHandle,
            WM_SETICON,
            Pointer(ICON_SMALL2),
            transparentCaptionIcon
        )
        val previousClassSmallIcon = user32Api.SetClassLongPtrW(
            windowHandle,
            GCLP_HICONSM,
            transparentCaptionIcon
        )
        val previousClassLargeIcon = qTalkLargeIcon
            ?.takeIf { Pointer.nativeValue(it) != 0L }
            ?.let { user32Api.SetClassLongPtrW(windowHandle, GCLP_HICON, it) }
        val previousExtendedStyle = user32Api.GetWindowLongPtrW(windowHandle, GWL_EXSTYLE)
        val previousExtendedStyleValue = previousExtendedStyle?.let(Pointer::nativeValue) ?: 0L
        user32Api.SetWindowLongPtrW(
            windowHandle,
            GWL_EXSTYLE,
            Pointer(previousExtendedStyleValue or WS_EX_DLGMODALFRAME)
        )
        user32Api.refreshNonClientFrame(windowHandle)

        // Windows caches the taskbar button icon when the window first appears.
        // Recreate only that button once, after class-big has been set to QTalk.
        if (qTalkLargeIcon.isNonNullPointer()) {
            user32Api.ShowWindow(windowHandle, SW_HIDE)
            user32Api.ShowWindow(windowHandle, SW_SHOW)
        }
        user32Api.installTransparentCaptionIcon(windowHandle, transparentCaptionIcon)

        // Compose can reapply the AWT icon after later recompositions. The taskbar
        // has already cached the QTalk icon supplied when the window was created;
        // guard only the two small caption fallbacks from being restored.
        val captionIconGuard = Timer(250) {
            val windowSmallIcon = user32Api.SendMessageW(
                windowHandle,
                WM_GETICON,
                Pointer(ICON_SMALL),
                null
            )
            val secondaryWindowSmallIcon = user32Api.SendMessageW(
                windowHandle,
                WM_GETICON,
                Pointer(ICON_SMALL2),
                null
            )
            val classSmallIcon = user32Api.GetClassLongPtrW(windowHandle, GCLP_HICONSM)
            val currentExtendedStyle = user32Api.GetWindowLongPtrW(windowHandle, GWL_EXSTYLE)
                ?.let(Pointer::nativeValue)
                ?: 0L
            val captionlessStyleMissing =
                currentExtendedStyle and WS_EX_DLGMODALFRAME == 0L
            if (
                windowSmallIcon != transparentCaptionIcon ||
                secondaryWindowSmallIcon != transparentCaptionIcon ||
                classSmallIcon != transparentCaptionIcon ||
                captionlessStyleMissing
            ) {
                user32Api.installTransparentCaptionIcon(windowHandle, transparentCaptionIcon)
                if (captionlessStyleMissing) {
                    user32Api.SetWindowLongPtrW(
                        windowHandle,
                        GWL_EXSTYLE,
                        Pointer(currentExtendedStyle or WS_EX_DLGMODALFRAME)
                    )
                }
                user32Api.refreshNonClientFrame(windowHandle)
            }
        }
        captionIconGuard.start()

        val captionColorResult = dwmApi.setIntAttribute(
            windowHandle,
            DWMWA_CAPTION_COLOR,
            QTALK_CAPTION_COLOR
        )
        val textColorResult = dwmApi.setIntAttribute(
            windowHandle,
            DWMWA_TEXT_COLOR,
            LIGHT_TEXT_COLOR
        )

        // Windows versions without explicit caption/text color attributes can still
        // render a readable dark native caption using the immersive-dark-mode flag.
        if (captionColorResult != 0 || textColorResult != 0) {
            val modernResult = dwmApi.setIntAttribute(
                windowHandle,
                DWMWA_USE_IMMERSIVE_DARK_MODE,
                1
            )
            if (modernResult != 0) {
                dwmApi.setIntAttribute(
                    windowHandle,
                    DWMWA_USE_IMMERSIVE_DARK_MODE_BEFORE_20H1,
                    1
                )
            }
        }

        {
            captionIconGuard.stop()
            user32Api.SendMessageW(
                windowHandle,
                WM_SETICON,
                Pointer(ICON_SMALL),
                previousCaptionIcon
            )
            user32Api.SendMessageW(
                windowHandle,
                WM_SETICON,
                Pointer(ICON_SMALL2),
                previousSecondaryCaptionIcon
            )
            user32Api.SetClassLongPtrW(windowHandle, GCLP_HICONSM, previousClassSmallIcon)
            if (qTalkLargeIcon.isNonNullPointer()) {
                user32Api.SetClassLongPtrW(windowHandle, GCLP_HICON, previousClassLargeIcon)
            }
            user32Api.SetWindowLongPtrW(
                windowHandle,
                GWL_EXSTYLE,
                Pointer(previousExtendedStyleValue)
            )
            user32Api.refreshNonClientFrame(windowHandle)
            user32Api.DestroyIcon(transparentCaptionIcon)
            Unit
        }
    }.getOrElse { error ->
        System.err.println("QTalk: failed to configure the native Windows title bar")
        error.printStackTrace()
        ({})
    }
}

internal fun qTalkNativeWindowTitle(): String = if (isWindows) "" else "QTalk"

private fun DwmApi.setIntAttribute(
    windowHandle: Pointer,
    attribute: Int,
    value: Int
): Int {
    val nativeValue = Memory(Int.SIZE_BYTES.toLong())
    nativeValue.setInt(0, value)
    return DwmSetWindowAttribute(windowHandle, attribute, nativeValue, Int.SIZE_BYTES)
}

private fun User32Api.refreshNonClientFrame(windowHandle: Pointer) {
    SetWindowPos(
        windowHandle,
        null,
        0,
        0,
        0,
        0,
        SWP_NOSIZE or SWP_NOMOVE or SWP_NOZORDER or SWP_NOACTIVATE or SWP_FRAMECHANGED
    )
}

private fun User32Api.installTransparentCaptionIcon(
    windowHandle: Pointer,
    transparentIcon: Pointer
) {
    SendMessageW(windowHandle, WM_SETICON, Pointer(ICON_SMALL), transparentIcon)
    SendMessageW(windowHandle, WM_SETICON, Pointer(ICON_SMALL2), transparentIcon)
    SetClassLongPtrW(windowHandle, GCLP_HICONSM, transparentIcon)
}

private fun Pointer?.isNonNullPointer(): Boolean = this != null && Pointer.nativeValue(this) != 0L
