package com.qvoste.qtalk.ui.calls.model

/**
 * Google Sheets owns directory contacts. User-created contacts and their
 * favorites remain local across directory refreshes.
 */
internal fun mergeDownloadedContacts(
    localContacts: List<Contact>,
    downloadedContacts: List<Contact>
): List<Contact> {
    val localByIdentity = localContacts.groupBy(Contact::syncIdentity)
    val localByTelegram = localContacts
        .filter { it.normalizedTelegramForSync().isNotBlank() }
        .groupBy { it.normalizedTelegramForSync() }
    val localByNumber = localContacts
        .filter { it.normalizedNumberForSync().isNotBlank() }
        .groupBy { it.normalizedNumberForSync() }
    val downloadedNumberCounts = downloadedContacts
        .map { it.normalizedNumberForSync() }
        .filter(String::isNotBlank)
        .groupingBy(String::toString)
        .eachCount()

    val downloadedIdentities = downloadedContacts.map(Contact::syncIdentity).toSet()
    val refreshedDirectory = downloadedContacts.map { downloaded ->
        val exactMatches = localByIdentity[downloaded.syncIdentity()].orEmpty()
        val telegramMatches = downloaded.normalizedTelegramForSync()
            .takeIf(String::isNotBlank)
            ?.let(localByTelegram::get)
            .orEmpty()
        val normalizedNumber = downloaded.normalizedNumberForSync()
        val uniqueNumberMatch = normalizedNumber
            .takeIf { number ->
                number.isNotBlank() &&
                    localByNumber[number]?.size == 1 &&
                    downloadedNumberCounts[number] == 1
            }
            ?.let(localByNumber::get)
            .orEmpty()

        downloaded.copy(
            avatarUrl = (exactMatches + uniqueNumberMatch).firstOrNull { it.avatarUrl.startsWith("file:") }?.avatarUrl ?: downloaded.avatarUrl,
            source = if (exactMatches.any { it.source == ContactSource.LOCAL }) {
                ContactSource.LOCAL
            } else {
                ContactSource.DIRECTORY
            },
            favorite = downloaded.favorite ||
                exactMatches.any(Contact::favorite) ||
                telegramMatches.any(Contact::favorite) ||
                uniqueNumberMatch.any(Contact::favorite)
        )
    }

    val retainedLocal = localContacts.filter { local ->
        local.source != ContactSource.DIRECTORY && local.syncIdentity() !in downloadedIdentities
    }.map { it.copy(source = ContactSource.LOCAL) }

    return refreshedDirectory + retainedLocal
}

private fun Contact.syncIdentity(): String =
    "${name.normalizedTextForSync()}\u0000${normalizedNumberForSync()}"

private fun Contact.normalizedNumberForSync(): String = number.filter(Char::isDigit)

private fun Contact.normalizedTelegramForSync(): String = telegram
    .trim()
    .removePrefix("@")
    .lowercase()

private fun String.normalizedTextForSync(): String = trim()
    .lowercase()
    .replace(Regex("\\s+"), " ")
