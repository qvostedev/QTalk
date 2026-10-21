package com.qvoste.qtalk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import com.qvoste.qtalk.ui.calls.components.ConferenceCallOverlay
import com.qvoste.qtalk.ui.calls.model.Contact
import com.qvoste.qtalk.ui.calls.theme.CallsTheme
import com.qvoste.qtalk.ui.calls.theme.ThemeMode
import com.qvoste.qtalk.voip.ConferencePhase
import com.qvoste.qtalk.voip.ConferenceStatus
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalComposeUiApi::class)
class ConferenceLayoutTest {
    @Test fun renderActiveAndInvitingConferenceAtDesktopAndCompactSizes() {
        val contacts = listOf(Contact("Алексей Смирнов", "101", false),
            Contact("Мария Волкова", "102", false), Contact("Иван Соколов", "103", false))
        for (phase in listOf(ConferencePhase.ACTIVE, ConferencePhase.CONSULTING)) {
            for (width in listOf(960, 640)) {
                val state = ConferenceStatus(phase,
                    participants = if (phase == ConferencePhase.ACTIVE) listOf("102", "103") else listOf("102"),
                    invitedNumber = "103", mutedNumbers = setOf("103"),
                    speakingNumbers = if (phase == ConferencePhase.ACTIVE) setOf("102") else emptySet())
                val image = renderComposeScene(width, 700) {
                    CallsTheme(ThemeMode.DARK) {
                        ConferenceCallOverlay(state, "101", { number -> contacts.firstOrNull { it.number == number } },
                            125, null, {}, {}, {}, {}, {}, {}, {})
                    }
                }
                assertEquals(width, image.width)
                val output = Path.of("build", "conference-previews", "$phase-$width.png")
                Files.createDirectories(output.parent)
                Files.write(output, checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
                image.close()
            }
        }
    }
}
