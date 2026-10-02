# G134Office: преемственность веб-лайт → Android APK

Это задел для Codex/Grok и Android Studio. Не смешивать с настольным JavaFX.

## Что уже есть

- Веб: https://g134t.github.io/G134Office/ из `cpp/web/`.
- Работает без входа. GitHub-кнопка необязательна.
- Сохранение на устройство: DOCX, ODT, RTF, TXT, PDF через печать.
- Черновик в `localStorage`, ключ `g134office-lite-draft-v4`.
- Логика редактора: `cpp/web/editor.js`. Вёрстка: `cpp/web/index.html`.

## Что не ломать

- Не требовать вход для набора текста.
- Не отправлять текст документа на сервер.
- Не подменять веб JavaFX-классами: браузер их не исполняет.

## Android-задел

Каталог `android/` открывается в Android Studio как отдельный проект.

- applicationId: `org.g134.office`
- Имя: G134Office
- Первый экран: WebView на веб-лайт, чтобы APK сразу умел писать.
- Следующий шаг настоящего редактора: вынести сохранение из WebView через Android `ACTION_CREATE_DOCUMENT`.

## Сборка APK

1. Android Studio → Open → папка `android/`.
2. Дождаться Gradle sync.
3. Build → Build APK(s). Файл будет `android/app/build/outputs/apk/debug/app-debug.apk`.
4. Переименовать в `G134Office.apk` и поставить на телефон.

Подпись для магазина в этот задел не входит.
