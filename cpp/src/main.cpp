#define UNICODE
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commdlg.h>

#include <string>
#include <vector>

namespace {

constexpr wchar_t kClass[] = L"G134OfficeLite";
constexpr int kEditId = 1001;

HWND g_edit = nullptr;
std::wstring g_path;
bool g_dirty = false;

std::wstring title()
{
    std::wstring name = g_path.empty() ? L"без имени" : g_path.substr(g_path.find_last_of(L"\\/") + 1);
    return L"G134Office Lite — " + name + (g_dirty ? L" *" : L"");
}

void setDirty(HWND window, bool dirty)
{
    g_dirty = dirty;
    SetWindowTextW(window, title().c_str());
}

std::wstring readEdit()
{
    int n = GetWindowTextLengthW(g_edit);
    std::wstring text(static_cast<size_t>(n), L'\0');
    if (n > 0) GetWindowTextW(g_edit, text.data(), n + 1);
    return text;
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
    if (save) {
        if (!GetSaveFileNameW(&ofn)) return {};
    } else if (!GetOpenFileNameW(&ofn)) {
        return {};
    }
    return buffer;
}

bool confirmLose(HWND window)
{
    if (!g_dirty) return true;
    int answer = MessageBoxW(window, L"Сохранить изменения?", L"G134Office Lite", MB_YESNOCANCEL | MB_ICONQUESTION);
    if (answer == IDCANCEL) return false;
    if (answer == IDNO) return true;
    std::wstring path = g_path.empty() ? pick(window, true) : g_path;
    if (path.empty()) return false;
    if (!writeFile(path, readEdit())) {
        MessageBoxW(window, L"Не удалось сохранить файл.", L"G134Office Lite", MB_ICONERROR);
        return false;
    }
    g_path = path;
    setDirty(window, false);
    return true;
}

void openPath(HWND window, const std::wstring& path)
{
    SetWindowTextW(g_edit, readFile(path).c_str());
    g_path = path;
    setDirty(window, false);
}

LRESULT CALLBACK proc(HWND window, UINT msg, WPARAM wparam, LPARAM lparam)
{
    switch (msg) {
    case WM_CREATE: {
        g_edit = CreateWindowExW(WS_EX_CLIENTEDGE, L"EDIT", L"", WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_WANTRETURN,
            0, 0, 0, 0, window, reinterpret_cast<HMENU>(static_cast<INT_PTR>(kEditId)), GetModuleHandleW(nullptr), nullptr);
        SendMessageW(g_edit, WM_SETFONT, reinterpret_cast<WPARAM>(GetStockObject(DEFAULT_GUI_FONT)), TRUE);
        SendMessageW(g_edit, EM_SETLIMITTEXT, 0, 0);
        HMENU menu = CreateMenu();
        HMENU file = CreatePopupMenu();
        AppendMenuW(file, MF_STRING, 1, L"Создать\tCtrl+N");
        AppendMenuW(file, MF_STRING, 2, L"Открыть...\tCtrl+O");
        AppendMenuW(file, MF_STRING, 3, L"Сохранить\tCtrl+S");
        AppendMenuW(file, MF_STRING, 4, L"Сохранить как...");
        AppendMenuW(file, MF_SEPARATOR, 0, nullptr);
        AppendMenuW(file, MF_STRING, 5, L"Выход");
        AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(file), L"Файл");
        SetMenu(window, menu);
        setDirty(window, false);
        return 0;
    }
    case WM_SIZE:
        MoveWindow(g_edit, 0, 0, LOWORD(lparam), HIWORD(lparam), TRUE);
        return 0;
    case WM_COMMAND:
        if (LOWORD(wparam) == kEditId && HIWORD(wparam) == EN_CHANGE) {
            setDirty(window, true);
            return 0;
        }
        switch (LOWORD(wparam)) {
        case 1:
            if (confirmLose(window)) {
                SetWindowTextW(g_edit, L"");
                g_path.clear();
                setDirty(window, false);
            }
            return 0;
        case 2: {
            if (!confirmLose(window)) return 0;
            std::wstring path = pick(window, false);
            if (!path.empty()) openPath(window, path);
            return 0;
        }
        case 3:
        case 4: {
            std::wstring path = (LOWORD(wparam) == 3 && !g_path.empty()) ? g_path : pick(window, true);
            if (path.empty()) return 0;
            if (!writeFile(path, readEdit())) {
                MessageBoxW(window, L"Не удалось сохранить файл.", L"G134Office Lite", MB_ICONERROR);
                return 0;
            }
            g_path = path;
            setDirty(window, false);
            return 0;
        }
        case 5:
            SendMessageW(window, WM_CLOSE, 0, 0);
            return 0;
        }
        break;
    case WM_CLOSE:
        if (confirmLose(window)) DestroyWindow(window);
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(window, msg, wparam, lparam);
}

} // namespace

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR cmd, int show)
{
    WNDCLASSW wc{};
    wc.lpfnWndProc = proc;
    wc.hInstance = instance;
    wc.hCursor = LoadCursorW(nullptr, IDC_IBEAM);
    wc.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    wc.lpszClassName = kClass;
    RegisterClassW(&wc);

    HWND window = CreateWindowExW(0, kClass, L"G134Office Lite", WS_OVERLAPPEDWINDOW,
        CW_USEDEFAULT, CW_USEDEFAULT, 900, 640, nullptr, nullptr, instance, nullptr);
    ShowWindow(window, show);
    if (cmd != nullptr && cmd[0] != L'\0') openPath(window, cmd);

    ACCEL accel[] = {
        {FCONTROL | FVIRTKEY, 'N', 1},
        {FCONTROL | FVIRTKEY, 'O', 2},
        {FCONTROL | FVIRTKEY, 'S', 3}
    };
    HACCEL table = CreateAcceleratorTableW(accel, 3);
    MSG message;
    while (GetMessageW(&message, nullptr, 0, 0)) {
        if (!TranslateAcceleratorW(window, table, &message)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    return static_cast<int>(message.wParam);
}
