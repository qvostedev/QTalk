package com.qvoste.qtalk.support

/** Public receiver address only. Cloudflare and Telegram credentials never belong in the client. */
object SupportConfiguration {
    private const val DEFAULT_ENDPOINT = "https://qtalk-support.malyshevon-tg-2026-a7f3.workers.dev/v1/reports"

    // An explicitly empty override disables sending; the isolated preview bypasses this default.
    fun endpoint(override: String? = System.getenv("QTALK_SUPPORT_ENDPOINT")): String = override ?: DEFAULT_ENDPOINT
}
