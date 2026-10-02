# G134Office Lite

Нативный редактор txt на Win32. Один exe, без JVM и JavaFX.

Умеет: создать, открыть, сохранить UTF-8, перетаскивание файла, поиск, перенос строк, крупнее/мельче, строка и столбец.
Не умеет: docx, pdf, орфография, страницы.

```powershell
cd cpp
cmake -S . -B build
cmake --build build --config Release
.\build\Release\G134OfficeLite.exe
```
