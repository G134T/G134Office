# SignPath — G134Office

Статус: исходники Java/Kotlin/JavaFX и Windows-workflow уже в `main`.
Одобрение SignPath Foundation ещё не получено. Подписи нет.

Репозиторий: https://github.com/G134T/G134Office
Лицензия: MIT.
Сборка: `.github/workflows/build.yml` на `windows-latest`.

## Что уже есть

- `build.gradle.kts`, `src/main`, `g134.ico`, `G134Office.iss`.
- C++-заготовка лежит в `cpp/`, релиз её не собирает.
- CI ставит Temurin 26, Inno Setup, гоняет `gradlew build`, `build-release.cmd`, `build-installer.cmd`.
- Неподписанный Setup кладётся в artifact `G134Office-Setup-unsigned`.
- Шаг SignPath не валит сборку, если секретов нет или сервис отказал.

## Что не подписывать

`glass.dll`, `prism_*.dll` и прочие DLL JavaFX / JVM — чужие.
Сертификат проекта на них вешать нельзя. Именно неподписанная `glass.dll` уже блокировалась Smart App Control (CodeIntegrity 3033/3077, CreateProcess 4551).

Конфиг в `docs/signpath-artifact.xml` подписывает только `G134Office-Setup-*.exe`.

## Секреты репозитория, после одобрения

- `SIGNPATH_API_TOKEN`
- `SIGNPATH_ORGANIZATION_ID`
- `SIGNPATH_PROJECT_SLUG`
- `SIGNPATH_POLICY_SLUG`
- `SIGNPATH_ARTIFACT_CONFIG_SLUG` — slug конфига из `docs/signpath-artifact.xml`

Заявка: https://signpath.io/product/open-source
Условия: https://signpath.org/terms.html
