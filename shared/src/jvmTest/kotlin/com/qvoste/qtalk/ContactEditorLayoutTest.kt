package com.qvoste.qtalk

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.renderComposeScene
import com.qvoste.qtalk.ui.calls.components.DialerPanel
import com.qvoste.qtalk.ui.calls.theme.CallsTheme
import com.qvoste.qtalk.ui.calls.theme.ThemeMode
import com.qvoste.qtalk.voip.CallStatus
import com.qvoste.qtalk.voip.RegistrationState
import org.jetbrains.skia.EncodedImageFormat
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test

@OptIn(ExperimentalComposeUiApi::class)
class ContactEditorLayoutTest {
    @Test fun renderNewContactWithUnclippedActions() {
        val image = renderComposeScene(340, 960) {
            CallsTheme(ThemeMode.DARK) {
                DialerPanel(number = "", contact = null, contactBeingEdited = null,
                    creatingContact = true, accountNumber = "101", registration = RegistrationState.DISCONNECTED,
                    call = CallStatus(), error = null, compact = false, contactHistory = emptyList(),
                    contactNotes = emptyList(), onNumberChange = {}, onEditContact = {}, onToggleFavorite = {},
                    onCancelContactEdit = {}, onSaveContact = {}, onDeleteContact = null, onAddNote = {},
                    onUpdateNote = { _, _ -> }, onDeleteNote = {}, onTextInputFocusChange = {},
                    onCall = {}, onHangUp = {}, onTransferEmployee = {})
            }
        }
        val output = Path.of("build", "contact-previews", "create.png")
        Files.createDirectories(output.parent)
        Files.write(output, checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
        image.close()
    }
}
