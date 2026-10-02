# G134Office Lite

Нативный редактор txt на Win32. Один exe, без JVM, JavaFX и сторонних DLL.
Это не замена полному редактору: нет docx, pdf, ленты и проверки орфографии.

Сборка из «Developer PowerShell for VS»:

```powershell
cd cpp
cmake -S . -B build
cmake --build build --config Release
.\build\Release\G134OfficeLite.exe
```

Файл можно передать аргументом. Ctrl+O, Ctrl+S, Ctrl+N.
