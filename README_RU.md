# QTalk

<p align="center">
  <img src="logo_2.svg" alt="Логотип QTalk" width="300">
</p>

<p align="center">
  SIP-звонки с открытым кодом и удобным настольным интерфейсом.
</p>

<p align="center">
  <a href="README.md">English</a> · <strong>Русский</strong>
</p>

QTalk объединяет звонки, контакты, избранное и историю вызовов в одном приложении. Настольный интерфейс создан на Kotlin Multiplatform и Compose Multiplatform; за SIP и передачу звука отвечает liblinphone.

<p align="center">
  <a href="https://github.com/qvostedev/QTalk/releases/latest">Скачать последнюю версию</a> · <a href="#сборка-из-исходников">Собрать из исходников</a> · <a href="LICENSE.txt">Лицензия</a>
</p>

## Интерфейс

На скриншотах показаны тёмная и светлая темы с демонстрационными данными.

<table width="100%">
  <tr><th width="50%">Тёмная тема</th><th width="50%">Светлая тема</th></tr>
  <tr>
    <td width="50%"><img src="docs/screenshots/dark-preview.png" alt="QTalk в тёмной теме" width="100%"></td>
    <td width="50%"><img src="docs/screenshots/light-preview.png" alt="QTalk в светлой теме" width="100%"></td>
  </tr>
</table>

## Возможности

- Входящие и исходящие аудиозвонки по SIP, управление звонком и перевод на внутренний номер.
- Контакты, избранное, заметки и поиск по истории вызовов.
- Необязательная синхронизация контактов с Google Таблицами; добавленные вручную контакты хранятся отдельно.
- Тёмная и светлая темы, компактный виджет звонка.
- Выбор аудиоустройств, чувствительности микрофона и громкости рингтона.
- Проверка новых версий в релизах GitHub из приложения.

## Поддержка платформ

| Платформа | Статус |
| --- | --- |
| Windows (x64) | ✅ Готово; установщик в [релизах](https://github.com/qvostedev/QTalk/releases/latest) |
| Linux (x86_64) | ✅ Готово; сборка из исходников |
| Android | В разработке |
| iOS | В разработке |

Настольными версиями уже можно пользоваться, но проект продолжает развиваться. Для звонков нужны собственные SIP-аккаунт и сервер: QTalk не предоставляет услуги телефонии. Синхронизация контактов необязательна и настраивается локально.

## Установка

На Windows скачайте установщик `QTalk-Setup-*.exe` из [последнего релиза](https://github.com/qvostedev/QTalk/releases/latest) и запустите его. Для Linux инструкция по сборке настольной версии приведена ниже.

## Сборка из исходников

Нужен **JDK 21**. Gradle Wrapper уже есть в репозитории, отдельно устанавливать Gradle не требуется.

### Windows

```powershell
.\gradlew.bat :desktopApp:run
```

Чтобы собрать установщик Windows, установите Inno Setup 6 и выполните:

```powershell
.\gradlew.bat :desktopApp:packageQTalkInstaller
```

Установщик появится в `desktopApp/build/compose/binaries/main/exe/`. Подробнее — в [инструкции по установщику](installer/windows/README.md).

### Linux

На Linux x86_64 можно воспользоваться готовым окружением Nix или самостоятельно установить JDK 21 и необходимые системные библиотеки:

```bash
nix develop
./gradlew :desktopApp:run
```

## Устройство проекта

```text
Compose UI → ViewModel / сценарии → VoipEngine → Linphone SDK
```

Общий модуль Kotlin Multiplatform содержит интерфейс и логику приложения. Настольный адаптер Linphone реализует `VoipEngine`, поэтому детали SIP отделены от остального кода.

## Лицензия

QTalk распространяется по лицензии **AGPL-3.0-only**. Полный текст — в файле [LICENSE](LICENSE.txt). Программа предоставляется без гарантий.

Сторонние компоненты сохраняют собственные лицензии. Linphone SDK 5.5.23 имеет двойное лицензирование; QTalk использует открытый вариант под AGPLv3. Подробнее — в [README SDK](https://github.com/BelledonneCommunications/linphone-sdk/blob/5.5.23/README.md) и его [лицензии](https://github.com/BelledonneCommunications/linphone-sdk/blob/5.5.23/LICENSE.txt).
