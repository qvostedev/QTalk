package com.qvoste.qtalk

import com.qvoste.qtalk.ui.calls.i18n.AppLanguage
import com.qvoste.qtalk.ui.calls.i18n.translateUiText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class HandoffMessagesTest {
    @Test fun handoffErrorsHaveEnglishTranslations() {
        val messages = listOf(
            "Передача отменена. Повторите выход",
            "Нет активного звонка",
            "Новые аудиосоединения не готовы",
            "Не удалось подготовить новый микшер",
            "Не удалось подготовить прямое LAN-соединение. Исходный звонок сохранён",
            "Организатор отключился. Автоматическое восстановление звонка недоступно",
            "Восстановление исходного звонка ещё не завершено",
            "Не удалось восстановить исходный звонок",
            "Обновите QTalk у всех участников и проверьте прямое LAN-соединение. Звонок сохранён",
            "Восстановление исходного звонка ещё не завершено. Оставьте QTalk открытым",
            "Обновите QTalk у всех участников. Исходный звонок сохранён"
        )
        messages.forEach { source ->
            val english = translateUiText(source, AppLanguage.ENGLISH)
            assertNotEquals(source, english, source)
            assertFalse(english.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }, english)
        }
    }
}
