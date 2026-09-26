# Подготовка G134Office к SignPath Foundation

Статус: подготовка заявки. Одобрение SignPath и доверенная подпись пока не получены.

## Что нужно сверить с опубликованным репозиторием

- Адрес GitHub: https://github.com/G134T/G134Office
- В опубликованном репозитории уже есть MIT-лицензия.
- Важно: опубликованный `main` сейчас содержит C++/CMake-заготовку с одним
  `main.cpp`, а локальное приложение из этой рабочей папки — Java/Kotlin/JavaFX.
  SignPath должен получить именно репозиторий, из которого собирается EXE;
  иначе происхождение подписанного артефакта не совпадёт с исходниками.
- В опубликованном репозитории пока нет GitHub Actions, Windows-релиза и
  документации по готовому EXE.
- Опубликованный релиз Windows с описанием возможностей и инструкцией запуска.
- Учётные записи автора, проверяющего изменения и утверждающего выпуск;
  включённая двухфакторная аутентификация.
- Сборка из исходников на GitHub-hosted runner. Сейчас доступна локальная
  сборка `build-release.cmd`, но workflow в этой копии отсутствует.

## Границы подписи

Приложение использует JavaFX, JVM и другие сторонние нативные библиотеки.
Неподписанная JavaFX glass.dll уже блокировалась Smart App Control.
Подпись установщика не заменяет подписи загружаемых DLL.

По условиям Foundation нельзя подписывать сторонние DLL сертификатом проекта.
Нужны подписанные сборки от поставщиков. Отдельно следует согласовать с SignPath
подпись EXE, созданного jpackage, и установщика Inno Setup: загрузчики генерируются
сторонними инструментами, поэтому право на подпись нельзя считать подтверждённым.

## Черновик обращения — не отправлен

Subject: G134Office — eligibility for SignPath Foundation

Hello,

I am an individual developer residing in Russia and the maintainer of G134Office,
a Windows desktop document and PDF editor written in Java/Kotlin with JavaFX.
I would like to check eligibility for the SignPath Foundation program before
configuring the integration.

Could you confirm whether you can accept a project maintained by an individual
resident of Russia, and whether a jpackage-generated application launcher and an
Inno Setup installer qualify for signing under your own-binaries policy?

The distribution includes third-party JavaFX and JVM DLLs. I understand that
these cannot simply be signed using the project's Foundation certificate and
that signed upstream builds need to be obtained separately.

The public repository is https://github.com/G134T/G134Office and currently has
an MIT license. Note that the repository's current main branch is a small
C++/CMake scaffold; the Java/Kotlin/JavaFX application is currently in a
separate local working tree. I need to confirm whether SignPath can accept the
repository after the application source and reproducible Windows workflow are
published there.

## Дальнейшая интеграция после одобрения

1. Согласовать лицензию и зависимости, опубликовать сведения о конфиденциальности.
   Описать веб-режим Ficbook и импорт его cookie из локальных браузеров по действию
   пользователя; не заявлять, что приложение вообще не работает с сетью или сессиями.
2. Добавить Code signing policy с реальными участниками. Указывать поддержку
   SignPath как полученную можно только после одобрения.
3. Подключить репозиторий в SignPath и настроить проверяемую сборку GitHub Actions.
4. После сборки загрузить артефакт в GitHub Actions, запросить подпись согласованных
   файлов через SignPath, получить и проверить результат. Секрет API хранить
   в GitHub Actions Secrets, не в исходниках.
5. Проверить конечный дистрибутив с включённым Smart App Control, включая DLL.

## Официальные источники

- Условия: https://signpath.org/terms.html
- Заявка: https://signpath.org/apply.html
- Интеграция GitHub: https://docs.signpath.io/trusted-build-systems/github
