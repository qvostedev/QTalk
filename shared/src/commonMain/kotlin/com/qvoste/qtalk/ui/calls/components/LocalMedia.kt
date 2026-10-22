package com.qvoste.qtalk.ui.calls.components

/** Imports a validated copy into QTalk's local storage; cancellation returns null. */
internal expect suspend fun importLocalMedia(avatar: Boolean): String?
internal expect fun customRingtones(): List<com.qvoste.qtalk.voip.RingtoneInfo>
