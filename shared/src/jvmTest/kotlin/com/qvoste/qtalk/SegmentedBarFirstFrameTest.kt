package com.qvoste.qtalk

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import com.qvoste.qtalk.ui.calls.SegmentedBar
import com.qvoste.qtalk.ui.calls.i18n.*
import com.qvoste.qtalk.ui.calls.theme.*
import org.jetbrains.skia.Bitmap
import kotlin.test.*

@OptIn(ExperimentalComposeUiApi::class)
class SegmentedBarFirstFrameTest {
    @Test fun selectedPillIsPaintedInTheFirstFrameForEverySelectionScaleAndLanguage() {
        for (language in AppLanguage.entries) for (scale in listOf(1f, 1.5f)) for (index in 0..2) {
            val image = renderComposeScene(750, 100, Density(scale)) {
                CallsTheme(ThemeMode.DARK) {
                    CompositionLocalProvider(LocalAppLanguage provides LanguageSettings(language)) {
                        SegmentedBar(listOf("Все  17", "Клиенты  8", "Сотрудники  9"), index, {})
                    }
                }
            }
            val bitmap = Bitmap.makeFromImage(image)
            val bluePixels = (0 until image.height).sumOf { y ->
                (0 until image.width).count { x -> bitmap.getColor(x, y) == 0xFF0088FF.toInt() }
            }
            bitmap.close(); image.close()
            assertTrue(bluePixels > 200, "First frame has no selected pill: $language / $scale / $index ($bluePixels)")
        }
    }
}
