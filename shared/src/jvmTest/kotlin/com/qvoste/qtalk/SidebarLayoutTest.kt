package com.qvoste.qtalk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.renderComposeScene
import com.qvoste.qtalk.ui.calls.components.Sidebar
import com.qvoste.qtalk.ui.calls.model.AppSection
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.theme.*
import com.qvoste.qtalk.voip.RegistrationState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class SidebarLayoutTest {
    @Test fun renderEverySelectedSectionWithConsistentIconOpacity() {
        for (mode in ThemeMode.entries) for (section in AppSection.entries) {
            val image = renderComposeScene(220, 800) {
                CallsTheme(mode) {
                    Box(Modifier.fillMaxSize().background(AppBackground)) {
                        Sidebar(section, RegistrationState.REGISTERED, false,
                            Contact("Иван Петров", "101", false), onSelect = {})
                    }
                }
            }
            val output = Path.of("build", "sidebar-previews", "$mode-$section.png")
            Files.createDirectories(output.parent)
            Files.write(output, checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            image.close()
        }
    }
}
