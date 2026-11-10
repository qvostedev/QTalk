package com.qvoste.qtalk.support

val supportEnglish = mapOf(
    "Техническая поддержка" to "Technical support",
    "Сообщить о проблеме и предложить улучшения" to "Report a problem or suggest an improvement",
    "Сообщить о проблеме" to "Report a problem",
    "Предложить улучшение" to "Suggest an improvement",
    "Назад к настройкам" to "Back to settings",
    "Заголовок * (3–160 символов)" to "Title * (3–160 characters)",
    "Описание * (10–12000 символов)" to "Description * (10–12000 characters)",
    "К обращению автоматически приложат ограниченный архив очищенных журналов QTalk." to "A bounded archive of cleaned QTalk logs is attached automatically.",
    "Предложение отправляется без диагностических данных. Проверьте текст и скриншот перед отправкой." to "The suggestion is sent without diagnostics. Review your text and screenshot before sending.",
    "Добавить скриншот (PNG / JPEG)" to "Attach screenshot (PNG / JPEG)",
    "Удалить скриншот" to "Remove screenshot",
    "Отправить в поддержку" to "Send to support",
    "Обращение принято:" to "Ticket received:",
    "Новое обращение" to "New ticket",
    "Собираем диагностику…" to "Collecting diagnostics…",
    "Отправляем отчёт…" to "Uploading report…",
    "Ожидаем подтверждение…" to "Waiting for confirmation…",
    "Проверьте заголовок и описание." to "Check the title and description.",
    "Сервис поддержки ещё не настроен." to "Support is not configured yet.",
    "Поддержка недоступна на этой платформе." to "Support is unavailable on this platform.",
    "Выберите PNG или JPEG до 4 МиБ и 16 миллионов пикселей." to "Choose a PNG or JPEG up to 4 MiB and 16 million pixels.",
    "Не удалось собрать диагностику." to "Could not collect diagnostics.",
    "Дождитесь завершения текущего действия." to "Wait for the current action to finish.",
    "Слишком много запросов. Повторите позже." to "Too many requests. Try again later.",
    "Сервис вернул некорректное подтверждение. Доставка не подтверждена." to "The service returned an invalid receipt. Delivery is unconfirmed.",
    "Доставка не подтверждена. Повтор сохранит номер отчёта; не создавайте копию обращения." to "Delivery is unconfirmed. Retrying keeps the report ID; avoid creating a duplicate report.",
    "Сервис отклонил отчёт. Повторите позже." to "The service rejected the report. Try again later."
)
val supportUiStrings get() = supportEnglish.keys
