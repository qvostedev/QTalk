package com.qvoste.qtalk.support

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

// Attachments are normalized PNG bytes, never a path to an arbitrary file.
class SupportAttachment(val filename: String, bytes: ByteArray) {
    private val data = bytes.copyOf()
    fun pngBytes(): ByteArray = data.copyOf()
}

/** A problem report carries diagnostics; an improvement suggestion never does. */
enum class SupportKind(val title: String) {
    PROBLEM("Сообщить о проблеме"), IMPROVEMENT("Предложить улучшение")
}

data class SupportDraft(val title: String = "",
    val description: String = "", val attachment: SupportAttachment? = null,
    val kind: SupportKind = SupportKind.PROBLEM) {
    val validForm get() = title.trim().length in 3..160 && description.trim().length in 10..12000
    val canSend get() = validForm
}

enum class SupportStage(val text: String) {
    IDLE("Готово"), COLLECTING("Собираем диагностику…"), UPLOADING("Отправляем отчёт…"), WAITING("Ожидаем подтверждение…")
}
enum class SupportError(val text: String) {
    INVALID_DRAFT("Проверьте заголовок и описание."),
    NOT_CONFIGURED("Сервис поддержки ещё не настроен."),
    UNSUPPORTED("Поддержка недоступна на этой платформе."),
    INVALID_IMAGE("Выберите PNG или JPEG до 4 МиБ и 16 миллионов пикселей."),
    COLLECTION("Не удалось собрать диагностику."),
    BUSY("Дождитесь завершения текущего действия."),
    RATE_LIMITED("Слишком много запросов. Повторите позже."),
    INVALID_RESPONSE("Сервис вернул некорректное подтверждение. Доставка не подтверждена."),
    DELIVERY_UNCERTAIN("Доставка не подтверждена. Повтор сохранит номер отчёта; не создавайте копию обращения."),
    REJECTED("Сервис отклонил отчёт. Повторите позже.")
}
class SupportFailure(val error: SupportError) : Exception(error.name)
data class SupportReceipt(val reportId: String, val ticketId: String)
interface SupportService {
    suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt
    suspend fun chooseScreenshot(): SupportAttachment?
}
object UnsupportedSupportService : SupportService {
    val error = SupportError.UNSUPPORTED
    override suspend fun send(draft: SupportDraft, stage: (SupportStage) -> Unit): SupportReceipt = throw SupportFailure(error)
    override suspend fun chooseScreenshot(): SupportAttachment? = throw SupportFailure(error)
}
data class SupportState(val draft: SupportDraft = SupportDraft(), val busy: Boolean = false,
    val stage: SupportStage = SupportStage.IDLE, val error: SupportError? = null,
    val receipt: SupportReceipt? = null)

/** Owned by the application, so leaving Settings retains draft, attachment and receipt. */
class SupportSession(private val service: SupportService) {
    private val mutable = MutableStateFlow(SupportState())
    val state = mutable.asStateFlow()
    private val lock = Mutex()
    fun edit(transform: (SupportDraft) -> SupportDraft) {
        if (!mutable.value.busy) mutable.value = mutable.value.copy(draft = transform(mutable.value.draft),
            error = null, receipt = null)
    }
    private suspend fun action(block: suspend () -> Unit) {
        if (!lock.tryLock()) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        try { block() }
        catch (cancel: CancellationException) {
            if (mutable.value.stage == SupportStage.UPLOADING || mutable.value.stage == SupportStage.WAITING)
                mutable.value = mutable.value.copy(error = SupportError.DELIVERY_UNCERTAIN)
            throw cancel
        }
        catch (failure: SupportFailure) { mutable.value = mutable.value.copy(error = failure.error) }
        catch (_: Exception) { mutable.value = mutable.value.copy(error = SupportError.COLLECTION) }
        finally { mutable.value = mutable.value.copy(busy = false, stage = SupportStage.IDLE); lock.unlock() }
    }
    suspend fun send() {
        if (mutable.value.receipt != null) return
        action {
            val draft = mutable.value.draft
            if (!draft.canSend) throw SupportFailure(SupportError.INVALID_DRAFT)
            val receipt = service.send(draft) { mutable.value = mutable.value.copy(stage = it) }
            mutable.value = mutable.value.copy(receipt = receipt)
        }
    }
    suspend fun chooseScreenshot() = action {
        val attachment = service.chooseScreenshot()
        if (attachment != null) mutable.value = mutable.value.copy(draft = mutable.value.draft.copy(attachment = attachment))
    }
}
val LocalSupportSession = staticCompositionLocalOf { SupportSession(UnsupportedSupportService) }
