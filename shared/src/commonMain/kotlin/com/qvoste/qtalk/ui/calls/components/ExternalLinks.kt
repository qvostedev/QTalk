package com.qvoste.qtalk.ui.calls.components

/** Public project page opened from About; no credentials or analytics are involved. */
internal const val QTALK_REPOSITORY_URL = "https://github.com/qvostedev/QTalk"

/** Opens a public HTTPS address in the user's browser. False means it could not be started. */
internal expect fun openExternalUrl(url: String): Boolean
