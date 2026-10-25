package com.qvoste.qtalk

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import com.qvoste.qtalk.ui.calls.components.CallKeypadKey
import com.qvoste.qtalk.ui.calls.theme.*
import org.jetbrains.skia.EncodedImageFormat
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class CallKeypadThemeTest {
    @Test fun lightCallKeypadUsesWhiteButtonsEvenInsideDarkOverlay() {
        for (light in listOf(false, true)) {
            val image = renderComposeScene(48, 48) {
                CallsTheme(ThemeMode.DARK) {
                    CompositionLocalProvider(LocalCallLightControls provides light) { CallKeypadKey('5') {} }
                }
            }
            val bytes = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
            val pixels = ImageIO.read(bytes.inputStream())
            assertEquals(if (light) 0xffffffff.toInt() else 0xff101215.toInt(), pixels.getRGB(24, 8))
            image.close()
        }
    }
}
