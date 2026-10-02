#define UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commdlg.h>
#include <commctrl.h>

#include <string>

namespace {

constexpr wchar_t kClass[] = L"G134OfficeLite";
constexpr int kEditId = 1001;
constexpr int kStatusId = 1002;
constexpr int ID_NEW = 1, ID_OPEN = 2, ID_SAVE = 3, ID_SAVEAS = 4, ID_EXIT = 5;
constexpr int ID_UNDO = 10, ID_CUT = 11, ID_COPY = 12, ID_PASTE = 13, ID_SELALL = 14, ID_FIND = 15;
constexpr int ID_WRAP = 20, ID_ZOOM_IN = 21, ID_ZOOM_OUT = 22, ID_ABOUT = 30;

HWND g_edit = nullptr;
HWND g_status = nullptr;
HFONT g_font = nullptr;
std::wstring g_path;
bool g_dirty = false;
bool g_wrap = true;
int g_pt = 16;
std::wstring g_find;

std::wstring fileName()
{
    if (g_path.empty()) return L"без имени";
    return g_path.substr(g_path.find_last_of(L"\\/") + 1);
}

std::wstring title() { return L"G134Office Lite — " + fileName() + (g_dirty ? L" *" : L""); }

void setDirty(HWND window, bool dirty)
{
    g_dirty = dirty;
    SetWindowTextW(window, title().c_str());
}

void applyFont()
{
    if (g_font) DeleteObject(g_font);
    g_font = CreateFontW(-g_pt, 0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY, FIXED_PITCH | FF_MODERN, L"Consolas");
    SendMessageW(g_edit, WM_SETFONT, reinterpret_cast<WPARAM>(g_font), TRUE);
}

std::wstring readEdit()
{
    int n = GetWindowTextLengthW(g_edit);
    std::wstring text(static_cast<size_t>(n), L'\0');
    if (n > 0) GetWindowTextW(g_edit, text.data(), n + 1);
    return text;
}

void updateStatus()
{
    DWORD start = 0, end = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&start), reinterpret_cast<LPARAM>(&end));
    int line = static_cast<int>(SendMessageW(g_edit, EM_LINEFROMCHAR, start, 0));
    int col = static_cast<int>(start - SendMessageW(g_edit, EM_LINEINDEX, line, 0));
    wchar_t buf[160];
    wsprintfW(buf, L" Строка %d, столбец %d    UTF-8    %d pt    %s",
        line + 1, col + 1, g_pt, g_wrap ? L"перенос" : L"без переноса");
    SendMessageW(g_status, SB_SETTEXTW, 0, reinterpret_cast<LPARAM>(buf));
}

bool writeFile(const std::wstring& path, const std::wstring& text)
{
    HANDLE file = CreateFileW(path.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return false;
    int bytes = WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), nullptr, 0, nullptr, nullptr);
    std::string utf8(static_cast<size_t>(bytes), '\0');
    if (bytes > 0) WideCharToMultiByte(CP_UTF8, 0, text.c_str(), static_cast<int>(text.size()), utf8.data(), bytes, nullptr, nullptr);
    const char bom[] = "\xEF\xBB\xBF";
    DWORD written = 0;
    WriteFile(file, bom, 3, &written, nullptr);
    BOOL ok = WriteFile(file, utf8.data(), static_cast<DWORD>(utf8.size()), &written, nullptr);
    CloseHandle(file);
    return ok;
}

std::wstring readFile(const std::wstring& path)
{
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) return {};
    DWORD size = GetFileSize(file, nullptr);
    std::string bytes(size, '\0');
    DWORD got = 0;
    if (size > 0) ReadFile(file, bytes.data(), size, &got, nullptr);
    CloseHandle(file);
    bytes.resize(got);
    size_t off = 0;
    if (bytes.size() >= 3 && static_cast<unsigned char>(bytes[0]) == 0xEF && static_cast<unsigned char>(bytes[1]) == 0xBB && static_cast<unsigned char>(bytes[2]) == 0xBF) off = 3;
    int chars = MultiByteToWideChar(CP_UTF8, 0, bytes.data() + off, static_cast<int>(bytes.size() - off), nullptr, 0);
    std::wstring text(static_cast<size_t>(chars), L'\0');
    if (chars > 0) MultiByteToWideChar(CP_UTF8, 0, bytes.data() + off, static_cast<int>(bytes.size() - off), text.data(), chars);
    return text;
}

std::wstring pick(HWND owner, bool save)
{
    wchar_t buffer[MAX_PATH] = L"";
    OPENFILENAMEW ofn{};
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = owner;
    ofn.lpstrFilter = L"Текст\0*.txt\0Все файлы\0*.*\0";
    ofn.lpstrFile = buffer;
    ofn.nMaxFile = MAX_PATH;
    ofn.Flags = OFN_EXPLORER | OFN_HIDEREADONLY | (save ? OFN_OVERWRITEPROMPT : OFN_FILEMUSTEXIST);
    ofn.lpstrDefExt = L"txt";
    if (save) { if (!GetSaveFileNameW(&ofn)) return {}; }
    else if (!GetOpenFileNameW(&ofn)) return {};
    return buffer;
}

bool confirmLose(HWND window);

bool saveTo(HWND window, const std::wstring& path)
{
    if (path.empty() || !writeFile(path, readEdit())) {
        MessageBoxW(window, L"Не удалось сохранить файл.", L"G134Office Lite", MB_ICONERROR);
        return false;
    }
    g_path = path;
    setDirty(window, false);
    updateStatus();
    return true;
}

bool confirmLose(HWND window)
{
    if (!g_dirty) return true;
    int answer = MessageBoxW(window, L"Сохранить изменения?", L"G134Office Lite", MB_YESNOCANCEL | MB_ICONQUESTION);
    if (answer == IDCANCEL) return false;
    if (answer == IDNO) return true;
    std::wstring path = g_path.empty() ? pick(window, true) : g_path;
    return saveTo(window, path);
}

void openPath(HWND window, const std::wstring& path)
{
    SetWindowTextW(g_edit, readFile(path).c_str());
    g_path = path;
    setDirty(window, false);
    updateStatus();
}

INT_PTR CALLBACK findDlg(HWND dlg, UINT msg, WPARAM wparam, LPARAM)
{
    if (msg == WM_COMMAND && (LOWORD(wparam) == IDOK || LOWORD(wparam) == IDCANCEL)) {
        if (LOWORD(wparam) == IDOK) {
            wchar_t buf[256] = L"";
            GetDlgItemTextW(dlg, 200, buf, 256);
            g_find = buf;
        }
        EndDialog(dlg, LOWORD(wparam));
        return TRUE;
    }
    if (msg == WM_INITDIALOG) {
        SetDlgItemTextW(dlg, 200, g_find.c_str());
        return TRUE;
    }
    return FALSE;
}

bool askFind(HWND owner)
{
    alignas(4) unsigned char mem[512]{};
    DLGTEMPLATE* dlg = reinterpret_cast<DLGTEMPLATE*>(mem);
    dlg->style = DS_SETFONT | DS_MODALFRAME | WS_POPUP | WS_CAPTION | WS_SYSMENU;
    dlg->cdit = 4;
    dlg->cx = 220; dlg->cy = 55;
    WORD* p = reinterpret_cast<WORD*>(dlg + 1);
    *p++ = 0; *p++ = 0;
    const wchar_t* cap = L"Найти";
    while (*cap) *p++ = *cap++;
    *p++ = 0;
    *p++ = 9;
    const wchar_t* font = L"Segoe UI";
    while (*font) *p++ = *font++;
    *p++ = 0;
    auto item = [&](DWORD style, short x, short y, short cx, short cy, WORD id, WORD klass, const wchar_t* text) {
        p = reinterpret_cast<WORD*>((reinterpret_cast<ULONG_PTR>(p) + 3) & ~3);
        auto* it = reinterpret_cast<DLGITEMTEMPLATE*>(p);
        it->style = style; it->x = x; it->y = y; it->cx = cx; it->cy = cy; it->id = id;
        p = reinterpret_cast<WORD*>(it + 1);
        *p++ = 0xFFFF; *p++ = klass;
        while (*text) *p++ = *text++;
        *p++ = 0; *p++ = 0;
    };
    item(WS_CHILD | WS_VISIBLE, 8, 8, 40, 10, static_cast<WORD>(-1), 0x0082, L"Строка");
    item(WS_CHILD | WS_VISIBLE | WS_BORDER | ES_AUTOHSCROLL | WS_TABSTOP, 50, 6, 160, 12, 200, 0x0081, L"");
    item(WS_CHILD | WS_VISIBLE | BS_DEFPUSHBUTTON | WS_TABSTOP, 110, 32, 45, 14, IDOK, 0x0080, L"Найти");
    item(WS_CHILD | WS_VISIBLE | WS_TABSTOP, 162, 32, 48, 14, IDCANCEL, 0x0080, L"Отмена");
    return DialogBoxIndirectParamW(GetModuleHandleW(nullptr), dlg, owner, findDlg, 0) == IDOK && !g_find.empty();
}

void doFind(HWND window)
{
    if (!askFind(window)) return;
    std::wstring text = readEdit();
    DWORD start = 0, end = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&start), reinterpret_cast<LPARAM>(&end));
    size_t at = text.find(g_find, end);
    if (at == std::wstring::npos) at = text.find(g_find);
    if (at == std::wstring::npos) {
        MessageBoxW(window, L"Не найдено.", L"G134Office Lite", MB_OK);
        return;
    }
    SendMessageW(g_edit, EM_SETSEL, at, at + g_find.size());
    SendMessageW(g_edit, EM_SCROLLCARET, 0, 0);
    updateStatus();
}

void recreateEdit(HWND window)
{
    std::wstring text = readEdit();
    DWORD sel = 0, end = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&sel), reinterpret_cast<LPARAM>(&end));
    DestroyWindow(g_edit);
    DWORD style = WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_WANTRETURN;
    if (!g_wrap) style |= WS_HSCROLL | ES_AUTOHSCROLL;
    g_edit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", text.c_str(), style, 0, 0, 0, 0, window,
        reinterpret_cast<HMENU>(static_cast<INT_PTR>(kEditId)), GetModuleHandleW(nullptr), nullptr);
    applyFont();
    SendMessageW(g_edit, EM_SETLIMITTEXT, 0, 0);
    SendMessageW(g_edit, EM_SETSEL, sel, end);
    RECT rc{}; GetClientRect(window, &rc);
    SendMessageW(window, WM_SIZE, 0, MAKELPARAM(rc.right, rc.bottom));
    SetFocus(g_edit);
}

LRESULT CALLBACK proc(HWND window, UINT msg, WPARAM wparam, LPARAM lparam)
{
    switch (msg) {
    case WM_CREATE: {
        g_edit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_WANTRETURN,
            0, 0, 0, 0, window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kEditId)), GetModuleHandleW(nullptr), nullptr);
        g_status = CreateWindowExW(0, STATUSCLASSNAMEW, L"", WS_CHILD | WS_VISIBLE, 0, 0, 0, 0, window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kStatusId)), GetModuleHandleW(nullptr), nullptr);
        applyFont();
        SendMessageW(g_edit, EM_SETLIMITTEXT, 0, 0);
        HMENU menu = CreateMenu();
        HMENU file = CreatePopupMenu();
        AppendMenuW(file, MF_STRING, ID_NEW, L"Создать\tCtrl+N");
        AppendMenuW(file, MF_STRING, ID_OPEN, L"Открыть...\tCtrl+O");
        AppendMenuW(file, MF_STRING, ID_SAVE, L"Сохранить\tCtrl+S");
        AppendMenuW(file, MF_STRING, ID_SAVEAS, L"Сохранить как...");
        AppendMenuW(file, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(file, MF_STRING, ID_EXIT, L"Выход");
        HMENU edit = CreatePopupMenu();
        AppendMenuW(edit, MF_STRING, ID_UNDO, L"Отменить\tCtrl+Z");
        AppendMenuW(edit, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(edit, MF_STRING, ID_CUT, L"Вырезать\tCtrl+X");
        AppendMenuW(edit, MF_STRING, ID_COPY, L"Копировать\tCtrl+C");
        AppendMenuW(edit, MF_STRING, ID_PASTE, L"Вставить\tCtrl+V");
        AppendMenuW(edit, MF_STRING, ID_SELALL, L"Выделить всё\tCtrl+A");
        AppendMenuW(edit, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(edit, MF_STRING, ID_FIND, L"Найти...\tCtrl+F");
        HMENU view = CreatePopupMenu();
        AppendMenuW(view, MF_STRING | MF_CHECKED, ID_WRAP, L"Перенос строк");
        AppendMenuW(view, MF_STRING, ID_ZOOM_IN, L"Крупнее\tCtrl++");
        AppendMenuW(view, MF_STRING, ID_ZOOM_OUT, L"Мельче\tCtrl+-");
        HMENU help = CreatePopupMenu();
        AppendMenuW(help, MF_STRING, ID_ABOUT, L"О программе");
        AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(file), L"Файл");
        AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(edit), L"Правка");
        AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(view), L"Вид");
        AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(help), L"Справка");
        SetMenu(window, menu);
        DragAcceptFiles(window, TRUE);
        setDirty(window, false);
        updateStatus();
        return 0;
    }
    case WM_SIZE: {
        SendMessageW(g_status, WM_SIZE, 0, 0);
        RECT sr{}; GetWindowRect(g_status, &sr);
        int sh = sr.bottom - sr.top;
        MoveWindow(g_edit, 0, 0, LOWORD(lparam), HIWORD(lparam) - sh, TRUE);
        return 0;
    }
    case WM_DROPFILES: {
        HDROP drop = reinterpret_cast<HDROP>(wparam);
        wchar_t path[MAX_PATH];
        if (DragQueryFileW(drop, 0, path, MAX_PATH) && confirmLose(window)) openPath(window, path);
        DragFinish(drop);
        return 0;
    }
    case WM_COMMAND:
        if (LOWORD(wparam) == kEditId && HIWORD(wparam) == EN_CHANGE) { setDirty(window, true); updateStatus(); return 0; }
        switch (LOWORD(wparam)) {
        case ID_NEW:
            if (confirmLose(window)) { SetWindowTextW(g_edit, L""); g_path.clear(); setDirty(window, false); updateStatus(); }
            return 0;
        case ID_OPEN: {
            if (!confirmLose(window)) return 0;
            std::wstring path = pick(window, false);
            if (!path.empty()) openPath(window, path);
            return 0;
        }
        case ID_SAVE: saveTo(window, g_path.empty() ? pick(window, true) : g_path); return 0;
        case ID_SAVEAS: saveTo(window, pick(window, true)); return 0;
        case ID_EXIT: SendMessageW(window, WM_CLOSE, 0, 0); return 0;
        case ID_UNDO: SendMessageW(g_edit, WM_UNDO, 0, 0); return 0;
        case ID_CUT: SendMessageW(g_edit, WM_CUT, 0, 0); return 0;
        case ID_COPY: SendMessageW(g_edit, WM_COPY, 0, 0); return 0;
        case ID_PASTE: SendMessageW(g_edit, WM_PASTE, 0, 0); return 0;
        case ID_SELALL: SendMessageW(g_edit, EM_SETSEL, 0, -1); return 0;
        case ID_FIND: doFind(window); return 0;
        case ID_WRAP:
            g_wrap = !g_wrap;
            CheckMenuItem(GetMenu(window), ID_WRAP, MF_BYCOMMAND | (g_wrap ? MF_CHECKED : MF_UNCHECKED));
            recreateEdit(window);
            updateStatus();
            return 0;
        case ID_ZOOM_IN: if (g_pt < 48) { g_pt += 2; applyFont(); updateStatus(); } return 0;
        case ID_ZOOM_OUT: if (g_pt > 10) { g_pt -= 2; applyFont(); updateStatus(); } return 0;
        case ID_ABOUT:
            MessageBoxW(window, L"G134Office Lite 0.1\nТекстовый редактор Win32, без JVM.\nПолный редактор — отдельное JavaFX-приложение.", L"О программе", MB_OK);
            return 0;
        }
        break;
    case WM_SETFOCUS: SetFocus(g_edit); return 0;
    case WM_CLOSE: if (confirmLose(window)) DestroyWindow(window); return 0;
    case WM_DESTROY:
        if (g_font) DeleteObject(g_font);
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(window, msg, wparam, lparam);
}

}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR cmd, int show)
{
    InitCommonControls();
    WNDCLASSW wc{};
    wc.lpfnWndProc = proc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(nullptr, IDC_IBEAM);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    wc.lpszClassName = kClass;
    RegisterClassW(&wc);
    HWND window = CreateWindowExW(0, kClass, L"G134Office Lite", WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT, 960, 680, nullptr, nullptr, instance, nullptr);
    ShowWindow(window, show);
    if (cmd && cmd[0]) openPath(window, cmd);
    ACCEL accel[] = {
        {FCONTROL | FVIRTKEY, 'N', ID_NEW}, {FCONTROL | FVIRTKEY, 'O', ID_OPEN}, {FCONTROL | FVIRTKEY, 'S', ID_SAVE},
        {FCONTROL | FVIRTKEY, 'F', ID_FIND}, {FCONTROL | FVIRTKEY, 'A', ID_SELALL},
        {FCONTROL | FVIRTKEY, VK_OEM_PLUS, ID_ZOOM_IN}, {FCONTROL | FVIRTKEY, VK_ADD, ID_ZOOM_IN},
        {FCONTROL | FVIRTKEY, VK_OEM_MINUS, ID_ZOOM_OUT}, {FCONTROL | FVIRTKEY, VK_SUBTRACT, ID_ZOOM_OUT}
    };
    HACCEL table = CreateAcceleratorTableW(accel, 9);
    MSG message;
    while (GetMessageW(&message, nullptr, 0, 0)) {
        if (!TranslateAcceleratorW(window, table, &message)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    return static_cast<int>(message.wParam);
}
