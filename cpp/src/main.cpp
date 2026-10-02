#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#ifndef NOMINMAX
#define NOMINMAX
#endif
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <commctrl.h>
#include <commdlg.h>
#include <shellapi.h>
#include <shlobj.h>
#include <shobjidl.h>

#include "resource.h"
#include "textutil.hpp"

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

namespace {

constexpr int kEditId = 100;
constexpr int ID_NEW = 4001;
constexpr int ID_OPEN = 4002;
constexpr int ID_SAVE = 4003;
constexpr int ID_SAVEAS = 4004;
constexpr int ID_EXIT = 4005;
constexpr int ID_UNDO = 4010;
constexpr int ID_CUT = 4011;
constexpr int ID_COPY = 4012;
constexpr int ID_PASTE = 4013;
constexpr int ID_SELALL = 4014;
constexpr int ID_FIND = 4015;
constexpr int ID_FIND_NEXT = 4016;
constexpr int ID_FIND_PREV = 4017;
constexpr int ID_REPLACE = 4018;
constexpr int ID_GOTO = 4019;
constexpr int ID_WRAP = 4020;
constexpr int ID_ZOOM_IN = 4021;
constexpr int ID_ZOOM_OUT = 4022;
constexpr int ID_ZOOM_RESET = 4023;
constexpr int ID_FONT = 4024;
constexpr int ID_DARK = 4025;
constexpr int ID_ABOUT = 4030;
constexpr int ID_ENC_UTF8 = 4100;
constexpr int ID_ENC_UTF8BOM = 4101;
constexpr int ID_ENC_UTF16LE = 4102;
constexpr int ID_ENC_UTF16BE = 4103;
constexpr int ID_ENC_1251 = 4104;
constexpr int ID_RECENT_BASE = 4200;
constexpr int kRecentLimit = 8;
constexpr std::int64_t kMaxFileBytes = 8 * 1024 * 1024;

HINSTANCE g_instance = nullptr;
HWND g_window = nullptr;
HWND g_edit = nullptr;
HWND g_status = nullptr;
HMENU g_encodingMenu = nullptr;
HMENU g_recentMenu = nullptr;
HFONT g_font = nullptr;
HFONT g_uiFont = nullptr;
HBRUSH g_darkBrush = nullptr;
HBRUSH g_darkStatusBrush = nullptr;

std::wstring g_path;
std::wstring g_face = L"Consolas";
std::wstring g_needle;
std::wstring g_replacement;
std::vector<std::wstring> g_recent;
TextEncoding g_encoding = TextEncoding::Utf8;
LineEnding g_eol = LineEnding::CrLf;
TextStats g_stats;
bool g_dirty = false;
bool g_wrap = true;
bool g_dark = false;
bool g_matchCase = false;
bool g_suppress = false;
bool g_comReady = false;
int g_points = 16;
int g_dpi = 96;
int g_frameX = CW_USEDEFAULT;
int g_frameY = CW_USEDEFAULT;
int g_frameW = 960;
int g_frameH = 680;
bool g_hasFrame = false;

enum class OpenResult { Opened, Declined, Failed };

std::wstring fileName()
{
    if (g_path.empty()) return L"без имени";
    auto slash = g_path.find_last_of(L"\\/");
    if (slash == std::wstring::npos) return g_path;
    return g_path.substr(slash + 1);
}

void setTitle()
{
    std::wstring title = L"G134Office Lite — " + fileName();
    if (g_dirty) title += L" *";
    if (g_window) SetWindowTextW(g_window, title.c_str());
}

void setStatus(const std::wstring& text)
{
    if (g_status) SetWindowTextW(g_status, text.c_str());
}

bool highContrast()
{
    HIGHCONTRASTW contrast{sizeof(contrast)};
    if (!SystemParametersInfoW(SPI_GETHIGHCONTRAST, sizeof(contrast), &contrast, 0)) return false;
    return (contrast.dwFlags & HCF_HIGHCONTRASTON) != 0;
}

void updateStatus()
{
    DWORD anchor = 0;
    DWORD caret = 0;
    if (g_edit) SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&anchor), reinterpret_cast<LPARAM>(&caret));
    int line = 0;
    int column = 1;
    if (g_edit) {
        line = static_cast<int>(SendMessageW(g_edit, EM_LINEFROMCHAR, caret, 0));
        int start = static_cast<int>(SendMessageW(g_edit, EM_LINEINDEX, line, 0));
        column = start >= 0 ? static_cast<int>(caret) - start + 1 : 1;
        if (column < 1) column = 1;
    }
    std::wstring encoding = encodingName(g_encoding);
    std::wstring ending = endingName(g_eol);
    wchar_t buffer[420];
    swprintf_s(
        buffer,
        L" Строка %d, столбец %d    %d слов, %d знаков    %s    %s    %d pt    %s%s",
        line + 1,
        column,
        g_stats.words,
        g_stats.chars,
        encoding.c_str(),
        ending.c_str(),
        g_points,
        g_wrap ? L"перенос" : L"без переноса",
        g_dirty ? L"    изменён" : L"");
    setStatus(buffer);
}

void refreshStats()
{
    int length = g_edit ? GetWindowTextLengthW(g_edit) : 0;
    std::wstring text(static_cast<std::size_t>(std::max(length, 0)), L'\0');
    if (length > 0) GetWindowTextW(g_edit, text.data(), length + 1);
    g_stats = statsOf(text);
}

std::wstring readEdit()
{
    int length = g_edit ? GetWindowTextLengthW(g_edit) : 0;
    std::wstring text(static_cast<std::size_t>(std::max(length, 0)), L'\0');
    if (length > 0) GetWindowTextW(g_edit, text.data(), length + 1);
    return text;
}

void markDirty()
{
    if (g_suppress) return;
    g_dirty = true;
    setTitle();
    refreshStats();
    updateStatus();
}

void applyFont()
{
    int dpi = g_dpi > 0 ? g_dpi : 96;
    HFONT font = CreateFontW(
        -MulDiv(g_points, dpi, 72),
        0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        FIXED_PITCH | FF_MODERN, g_face.c_str());
    if (font) {
        if (g_edit) SendMessageW(g_edit, WM_SETFONT, reinterpret_cast<WPARAM>(font), TRUE);
        if (g_font) DeleteObject(g_font);
        g_font = font;
    }
    HFONT ui = CreateFontW(
        -MulDiv(9, dpi, 72),
        0, 0, 0, FW_NORMAL, FALSE, FALSE, FALSE, DEFAULT_CHARSET,
        OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        VARIABLE_PITCH | FF_SWISS, L"Segoe UI");
    if (ui) {
        if (g_status) SendMessageW(g_status, WM_SETFONT, reinterpret_cast<WPARAM>(ui), TRUE);
        if (g_uiFont) DeleteObject(g_uiFont);
        g_uiFont = ui;
    }
}

void layout()
{
    if (!g_window || !g_edit || !g_status) return;
    RECT client{};
    GetClientRect(g_window, &client);
    int statusHeight = std::max(18, MulDiv(22, g_dpi > 0 ? g_dpi : 96, 96));
    int editHeight = std::max(0, static_cast<int>(client.bottom - statusHeight));
    MoveWindow(g_edit, 0, 0, client.right, editHeight, TRUE);
    MoveWindow(g_status, 0, editHeight, client.right, statusHeight, TRUE);
}

std::wstring appDirectory()
{
    PWSTR local = nullptr;
    if (FAILED(SHGetKnownFolderPath(FOLDERID_LocalAppData, 0, nullptr, &local)) || local == nullptr) return {};
    std::wstring dir = local;
    CoTaskMemFree(local);
    dir += L"\\G134Office";
    CreateDirectoryW(dir.c_str(), nullptr);
    return dir;
}

std::wstring lastErrorText()
{
    DWORD code = GetLastError();
    wchar_t* message = nullptr;
    DWORD flags = FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
    if (FormatMessageW(flags, nullptr, code, 0, reinterpret_cast<wchar_t*>(&message), 0, nullptr) == 0 || message == nullptr) {
        return L"неизвестная ошибка";
    }
    std::wstring text = message;
    LocalFree(message);
    while (!text.empty() && (text.back() == L'\r' || text.back() == L'\n' || text.back() == L' ')) text.pop_back();
    return text;
}

void errorBox(HWND owner, const std::wstring& text)
{
    MessageBoxW(owner, text.c_str(), L"G134Office Lite", MB_ICONERROR | MB_OK);
}

bool readBytes(const std::wstring& path, std::vector<std::uint8_t>& out, std::wstring& error)
{
    DWORD attributes = GetFileAttributesW(path.c_str());
    if (attributes == INVALID_FILE_ATTRIBUTES) {
        error = L"Не удалось открыть файл. " + lastErrorText();
        return false;
    }
    if ((attributes & FILE_ATTRIBUTE_DIRECTORY) != 0) {
        error = L"Это папка, а не файл.";
        return false;
    }
    HANDLE file = CreateFileW(path.c_str(), GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE, nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = L"Не удалось открыть файл. " + lastErrorText();
        return false;
    }
    LARGE_INTEGER size{};
    if (!GetFileSizeEx(file, &size)) {
        CloseHandle(file);
        error = L"Не удалось прочитать размер файла.";
        return false;
    }
    if (size.QuadPart > kMaxFileBytes) {
        CloseHandle(file);
        error = L"Файл больше 8 МБ. Lite открывает обычный текст до этого размера.";
        return false;
    }
    out.resize(static_cast<std::size_t>(std::max<LONGLONG>(size.QuadPart, 0)));
    std::size_t got = 0;
    while (got < out.size()) {
        DWORD chunk = 0;
        DWORD ask = static_cast<DWORD>(std::min<std::size_t>(out.size() - got, 1u << 20));
        if (!ReadFile(file, out.data() + got, ask, &chunk, nullptr)) {
            CloseHandle(file);
            error = L"Не удалось прочитать файл. " + lastErrorText();
            return false;
        }
        if (chunk == 0) break;
        got += chunk;
    }
    CloseHandle(file);
    out.resize(got);
    return true;
}

bool writeBytesAtomic(const std::wstring& path, const std::string& bytes, std::wstring& error)
{
    std::wstring temporary = path + L".g134tmp";
    HANDLE file = CreateFileW(temporary.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (file == INVALID_HANDLE_VALUE) {
        error = L"Не удалось записать файл. " + lastErrorText();
        return false;
    }
    std::size_t put = 0;
    while (put < bytes.size()) {
        DWORD chunk = 0;
        DWORD ask = static_cast<DWORD>(std::min<std::size_t>(bytes.size() - put, 1u << 20));
        if (!WriteFile(file, bytes.data() + put, ask, &chunk, nullptr) || chunk == 0) {
            CloseHandle(file);
            DeleteFileW(temporary.c_str());
            error = L"Не удалось записать файл. " + lastErrorText();
            return false;
        }
        put += chunk;
    }
    FlushFileBuffers(file);
    CloseHandle(file);
    if (!MoveFileExW(temporary.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
        error = L"Не удалось заменить файл. " + lastErrorText();
        DeleteFileW(temporary.c_str());
        return false;
    }
    return true;
}

std::wstring lfToCrlf(std::wstring_view text)
{
    std::wstring out;
    out.reserve(text.size() + 16);
    for (wchar_t c : text) {
        if (c == L'\n') out.append(L"\r\n");
        else out.push_back(c);
    }
    return out;
}

void loadLists()
{
    std::wstring dir = appDirectory();
    if (dir.empty()) return;
    std::vector<std::uint8_t> bytes;
    std::wstring error;
    if (readBytes(dir + L"\\lite-recent.txt", bytes, error)) {
        DecodedText decoded;
        if (decodeText(bytes.data(), bytes.size(), decoded, error)) {
            std::wstring line;
            auto push = [&](const std::wstring& item) {
                if (item.empty() || g_recent.size() >= static_cast<std::size_t>(kRecentLimit)) return;
                g_recent.push_back(item);
            };
            for (wchar_t c : decoded.text) {
                if (c == L'\n') {
                    push(line);
                    line.clear();
                } else {
                    line.push_back(c);
                }
            }
            push(line);
        }
    }
    bytes.clear();
    if (!readBytes(dir + L"\\lite.ini", bytes, error)) return;
    DecodedText decoded;
    if (!decodeText(bytes.data(), bytes.size(), decoded, error)) return;
    bool sawX = false, sawY = false, sawW = false, sawH = false;
    std::wstring line;
    auto apply = [&](std::wstring item) {
        auto eq = item.find(L'=');
        if (eq == std::wstring::npos) return;
        std::wstring key = item.substr(0, eq);
        std::wstring value = item.substr(eq + 1);
        if (key == L"wrap") g_wrap = value != L"0";
        else if (key == L"dark") g_dark = value == L"1";
        else if (key == L"points") g_points = std::clamp(_wtoi(value.c_str()), 8, 72);
        else if (key == L"face" && !value.empty() && value.size() < LF_FACESIZE) g_face = value;
        else if (key == L"x") { g_frameX = _wtoi(value.c_str()); sawX = true; }
        else if (key == L"y") { g_frameY = _wtoi(value.c_str()); sawY = true; }
        else if (key == L"w") { g_frameW = _wtoi(value.c_str()); sawW = true; }
        else if (key == L"h") { g_frameH = _wtoi(value.c_str()); sawH = true; }
    };
    for (wchar_t c : decoded.text) {
        if (c == L'\n') {
            apply(line);
            line.clear();
        } else {
            line.push_back(c);
        }
    }
    apply(line);
    g_hasFrame = sawX && sawY && sawW && sawH && g_frameW >= 320 && g_frameH >= 200;
    if (!g_hasFrame) return;
    RECT frame{g_frameX, g_frameY, g_frameX + g_frameW, g_frameY + g_frameH};
    RECT desktop{
        GetSystemMetrics(SM_XVIRTUALSCREEN),
        GetSystemMetrics(SM_YVIRTUALSCREEN),
        GetSystemMetrics(SM_XVIRTUALSCREEN) + GetSystemMetrics(SM_CXVIRTUALSCREEN),
        GetSystemMetrics(SM_YVIRTUALSCREEN) + GetSystemMetrics(SM_CYVIRTUALSCREEN)};
    RECT intersection{};
    if (!IntersectRect(&intersection, &frame, &desktop) || intersection.right - intersection.left < 120 || intersection.bottom - intersection.top < 80) {
        g_hasFrame = false;
    }
}

void saveLists()
{
    std::wstring dir = appDirectory();
    if (dir.empty()) return;
    std::wstring recentText;
    for (const auto& path : g_recent) {
        recentText += path;
        recentText.push_back(L'\n');
    }
    std::string encoded;
    std::wstring error;
    if (encodeText(recentText, TextEncoding::Utf8, LineEnding::Lf, encoded, error)) {
        writeBytesAtomic(dir + L"\\lite-recent.txt", encoded, error);
    }
    WINDOWPLACEMENT placement{sizeof(placement)};
    if (g_window && GetWindowPlacement(g_window, &placement)) {
        g_frameX = placement.rcNormalPosition.left;
        g_frameY = placement.rcNormalPosition.top;
        g_frameW = placement.rcNormalPosition.right - placement.rcNormalPosition.left;
        g_frameH = placement.rcNormalPosition.bottom - placement.rcNormalPosition.top;
    }
    wchar_t ini[512];
    swprintf_s(
        ini,
        L"wrap=%d\npoints=%d\ndark=%d\nface=%s\nx=%d\ny=%d\nw=%d\nh=%d\n",
        g_wrap ? 1 : 0,
        g_points,
        g_dark ? 1 : 0,
        g_face.c_str(),
        g_frameX,
        g_frameY,
        g_frameW,
        g_frameH);
    if (encodeText(ini, TextEncoding::Utf8, LineEnding::Lf, encoded, error)) {
        writeBytesAtomic(dir + L"\\lite.ini", encoded, error);
    }
}

std::wstring menuLabel(const std::wstring& path)
{
    std::wstring label;
    for (wchar_t c : path) {
        if (c == L'&') label.append(L"&&");
        else label.push_back(c);
    }
    return label;
}

void rebuildRecent()
{
    if (!g_recentMenu) return;
    while (GetMenuItemCount(g_recentMenu) > 0) DeleteMenu(g_recentMenu, 0, MF_BYPOSITION);
    if (g_recent.empty()) {
        AppendMenuW(g_recentMenu, MF_STRING | MF_GRAYED, ID_RECENT_BASE, L"нет недавних файлов");
        return;
    }
    for (std::size_t i = 0; i < g_recent.size() && i < static_cast<std::size_t>(kRecentLimit); ++i) {
        AppendMenuW(g_recentMenu, MF_STRING, ID_RECENT_BASE + static_cast<int>(i), menuLabel(g_recent[i]).c_str());
    }
}

void remember(const std::wstring& path)
{
    g_recent = rememberRecent(std::move(g_recent), path, kRecentLimit);
    rebuildRecent();
    saveLists();
}

void syncEncodingMenu()
{
    if (!g_encodingMenu) return;
    int id = ID_ENC_UTF8;
    switch (g_encoding) {
    case TextEncoding::Utf8: id = ID_ENC_UTF8; break;
    case TextEncoding::Utf8Bom: id = ID_ENC_UTF8BOM; break;
    case TextEncoding::Utf16Le: id = ID_ENC_UTF16LE; break;
    case TextEncoding::Utf16Be: id = ID_ENC_UTF16BE; break;
    case TextEncoding::Windows1251: id = ID_ENC_1251; break;
    }
    CheckMenuRadioItem(g_encodingMenu, ID_ENC_UTF8, ID_ENC_1251, id, MF_BYCOMMAND);
}

void syncViewMenu()
{
    HMENU menu = g_window ? GetMenu(g_window) : nullptr;
    if (!menu) return;
    CheckMenuItem(menu, ID_WRAP, MF_BYCOMMAND | (g_wrap ? MF_CHECKED : MF_UNCHECKED));
    CheckMenuItem(menu, ID_DARK, MF_BYCOMMAND | (g_dark ? MF_CHECKED : MF_UNCHECKED));
}

LRESULT CALLBACK editSub(HWND edit, UINT msg, WPARAM wparam, LPARAM lparam, UINT_PTR, DWORD_PTR)
{
    if (msg == WM_MOUSEWHEEL && (GET_KEYSTATE_WPARAM(wparam) & MK_CONTROL)) {
        SendMessageW(g_window, WM_COMMAND, GET_WHEEL_DELTA_WPARAM(wparam) > 0 ? ID_ZOOM_IN : ID_ZOOM_OUT, 0);
        return 0;
    }
    if (msg == WM_GETDLGCODE) return DefSubclassProc(edit, msg, wparam, lparam) | DLGC_WANTTAB | DLGC_WANTALLKEYS;
    LRESULT result = DefSubclassProc(edit, msg, wparam, lparam);
    if (msg == WM_KEYUP || msg == WM_LBUTTONUP || msg == WM_SETFOCUS || (msg == WM_MOUSEMOVE && (wparam & MK_LBUTTON))) updateStatus();
    return result;
}

HWND createEdit(HWND window)
{
    DWORD style = WS_CHILD | WS_VISIBLE | WS_VSCROLL | ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | ES_WANTRETURN | ES_NOHIDESEL;
    if (!g_wrap) style |= WS_HSCROLL | ES_AUTOHSCROLL;
    HWND edit = CreateWindowExW(
        WS_EX_CLIENTEDGE, L"EDIT", L"", style, 0, 0, 0, 0, window,
        reinterpret_cast<HMENU>(static_cast<INT_PTR>(kEditId)), g_instance, nullptr);
    SendMessageW(edit, EM_SETLIMITTEXT, 0, 0);
    SetWindowSubclass(edit, editSub, 1, 0);
    return edit;
}

void setEditorText(const std::wstring& text)
{
    g_suppress = true;
    SetWindowTextW(g_edit, text.c_str());
    g_suppress = false;
}

OpenResult openPath(HWND window, const std::wstring& path);
bool saveTo(HWND window, const std::wstring& path);

std::wstring pickFile(HWND owner, bool save)
{
    IFileDialog* dialog = nullptr;
    HRESULT created = CoCreateInstance(
        save ? CLSID_FileSaveDialog : CLSID_FileOpenDialog,
        nullptr,
        CLSCTX_INPROC_SERVER,
        IID_PPV_ARGS(&dialog));
    if (g_comReady && SUCCEEDED(created) && dialog) {
        COMDLG_FILTERSPEC filters[] = {{L"Текст", L"*.txt"}, {L"Все файлы", L"*.*"}};
        dialog->SetFileTypes(2, filters);
        dialog->SetDefaultExtension(L"txt");
        DWORD options = 0;
        dialog->GetOptions(&options);
        options |= FOS_FORCEFILESYSTEM | FOS_NOCHANGEDIR;
        options |= save ? FOS_OVERWRITEPROMPT : FOS_FILEMUSTEXIST;
        dialog->SetOptions(options);
        if (save && !g_path.empty()) dialog->SetFileName(fileName().c_str());
        std::wstring chosen;
        if (SUCCEEDED(dialog->Show(owner))) {
            IShellItem* item = nullptr;
            if (SUCCEEDED(dialog->GetResult(&item)) && item) {
                PWSTR display = nullptr;
                if (SUCCEEDED(item->GetDisplayName(SIGDN_FILESYSPATH, &display)) && display) {
                    chosen = display;
                    CoTaskMemFree(display);
                }
                item->Release();
            }
        }
        dialog->Release();
        return chosen;
    }

    std::wstring buffer(32768, L'\0');
    OPENFILENAMEW ofn{};
    ofn.lStructSize = sizeof(ofn);
    ofn.hwndOwner = owner;
    ofn.lpstrFilter = L"Текст\0*.txt\0Все файлы\0*.*\0";
    ofn.lpstrFile = buffer.data();
    ofn.nMaxFile = static_cast<DWORD>(buffer.size());
    ofn.Flags = OFN_EXPLORER | OFN_HIDEREADONLY | OFN_NOCHANGEDIR | (save ? OFN_OVERWRITEPROMPT : OFN_FILEMUSTEXIST);
    ofn.lpstrDefExt = L"txt";
    BOOL ok = save ? GetSaveFileNameW(&ofn) : GetOpenFileNameW(&ofn);
    return ok ? buffer.c_str() : L"";
}

bool saveTo(HWND window, const std::wstring& path)
{
    if (path.empty()) return false;
    std::string encoded;
    std::wstring error;
    if (!encodeText(readEdit(), g_encoding, g_eol, encoded, error) || !writeBytesAtomic(path, encoded, error)) {
        errorBox(window, error.empty() ? L"Не удалось сохранить файл." : error);
        return false;
    }
    g_path = path;
    g_dirty = false;
    setTitle();
    refreshStats();
    updateStatus();
    remember(path);
    return true;
}

bool confirmLose(HWND window)
{
    if (!g_dirty) return true;
    int answer = MessageBoxW(window, L"Сохранить изменения?", L"G134Office Lite", MB_YESNOCANCEL | MB_ICONQUESTION);
    if (answer == IDCANCEL) return false;
    if (answer == IDNO) return true;
    std::wstring path = g_path.empty() ? pickFile(window, true) : g_path;
    if (path.empty()) return false;
    return saveTo(window, path);
}

OpenResult openPath(HWND window, const std::wstring& path)
{
    std::vector<std::uint8_t> bytes;
    std::wstring error;
    if (!readBytes(path, bytes, error)) {
        errorBox(window, error);
        return OpenResult::Failed;
    }
    DecodedText decoded;
    if (!decodeText(bytes.data(), bytes.size(), decoded, error)) {
        errorBox(window, error.empty() ? L"Не удалось прочитать текст." : error);
        return OpenResult::Failed;
    }
    if (decoded.binary) {
        int answer = MessageBoxW(
            window,
            L"Файл содержит нулевые байты и похож на двоичный. Открыть его как текст?",
            L"G134Office Lite",
            MB_YESNO | MB_ICONWARNING);
        if (answer != IDYES) return OpenResult::Declined;
    }
    setEditorText(lfToCrlf(decoded.text));
    g_path = path;
    g_encoding = decoded.encoding;
    g_eol = decoded.ending;
    g_dirty = false;
    syncEncodingMenu();
    setTitle();
    refreshStats();
    updateStatus();
    remember(path);
    return OpenResult::Opened;
}

std::wstring selectedText()
{
    DWORD anchor = 0;
    DWORD caret = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&anchor), reinterpret_cast<LPARAM>(&caret));
    DWORD from = std::min(anchor, caret);
    DWORD to = std::max(anchor, caret);
    std::wstring text = readEdit();
    if (from > text.size()) return {};
    if (to > text.size()) to = static_cast<DWORD>(text.size());
    return text.substr(from, to - from);
}

bool selectionMatches(const std::wstring& selection)
{
    if (g_matchCase) return selection == g_needle;
    return findText(selection, g_needle, 0, false) == 0 && selection.size() == g_needle.size();
}

void selectRange(std::size_t from, std::size_t to)
{
    SendMessageW(g_edit, EM_SETSEL, static_cast<WPARAM>(from), static_cast<LPARAM>(to));
    SendMessageW(g_edit, EM_SCROLLCARET, 0, 0);
    updateStatus();
}

void findDirection(HWND window, bool forward)
{
    if (g_needle.empty()) {
        setStatus(L" Введите строку поиска");
        return;
    }
    std::wstring text = readEdit();
    DWORD anchor = 0;
    DWORD caret = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&anchor), reinterpret_cast<LPARAM>(&caret));
    std::size_t current = std::min(anchor, caret);
    std::size_t at = std::wstring::npos;
    bool wrapped = false;
    if (forward) {
        std::size_t from = current;
        if (selectionMatches(selectedText())) from = std::max(anchor, caret);
        at = findText(text, g_needle, from, g_matchCase);
        if (at == std::wstring::npos && from > 0) {
            at = findText(text, g_needle, 0, g_matchCase);
            wrapped = true;
        }
    } else {
        std::size_t before = current;
        at = findTextBefore(text, g_needle, before, g_matchCase);
        if (at == std::wstring::npos) {
            at = findTextBefore(text, g_needle, text.size(), g_matchCase);
            wrapped = true;
        }
    }
    if (at == std::wstring::npos || (wrapped && at == current)) {
        setStatus(L" Не найдено");
        return;
    }
    selectRange(at, at + g_needle.size());
    if (wrapped) setStatus(forward ? L" Поиск продолжен с начала" : L" Поиск продолжен с конца");
    (void)window;
}

void replaceOne()
{
    if (g_needle.empty()) return;
    if (selectionMatches(selectedText())) {
        SendMessageW(g_edit, EM_REPLACESEL, TRUE, reinterpret_cast<LPARAM>(g_replacement.c_str()));
        refreshStats();
    }
    findDirection(g_window, true);
}

void replaceEvery(HWND window)
{
    if (g_needle.empty()) {
        setStatus(L" Введите строку поиска");
        return;
    }
    int count = 0;
    std::wstring replaced = replaceAll(readEdit(), g_needle, g_replacement, g_matchCase, count);
    if (count == 0) {
        setStatus(L" Не найдено");
        return;
    }
    DWORD anchor = 0;
    DWORD caret = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&anchor), reinterpret_cast<LPARAM>(&caret));
    SendMessageW(g_edit, EM_SETSEL, 0, -1);
    SendMessageW(g_edit, EM_REPLACESEL, TRUE, reinterpret_cast<LPARAM>(replaced.c_str()));
    selectRange(std::min(anchor, caret), std::min<std::size_t>(std::min(anchor, caret), replaced.size()));
    refreshStats();
    wchar_t buffer[80];
    swprintf_s(buffer, L" Заменено: %d", count);
    setStatus(buffer);
    (void)window;
}

std::wstring dialogText(HWND dialog, int id)
{
    HWND control = GetDlgItem(dialog, id);
    int length = GetWindowTextLengthW(control);
    std::wstring text(static_cast<std::size_t>(std::max(length, 0)), L'\0');
    if (length > 0) GetWindowTextW(control, text.data(), length + 1);
    return text;
}

void readFindDialog(HWND dialog)
{
    g_needle = dialogText(dialog, IDC_NEEDLE);
    g_replacement = dialogText(dialog, IDC_REPLACEMENT);
    g_matchCase = IsDlgButtonChecked(dialog, IDC_MATCHCASE) == BST_CHECKED;
}

INT_PTR CALLBACK findProc(HWND dialog, UINT msg, WPARAM wparam, LPARAM lparam)
{
    switch (msg) {
    case WM_INITDIALOG: {
        auto* seed = reinterpret_cast<std::wstring*>(lparam);
        if (seed && !seed->empty()) SetDlgItemTextW(dialog, IDC_NEEDLE, seed->c_str());
        else if (!g_needle.empty()) SetDlgItemTextW(dialog, IDC_NEEDLE, g_needle.c_str());
        if (!g_replacement.empty()) SetDlgItemTextW(dialog, IDC_REPLACEMENT, g_replacement.c_str());
        CheckDlgButton(dialog, IDC_MATCHCASE, g_matchCase ? BST_CHECKED : BST_UNCHECKED);
        return TRUE;
    }
    case WM_COMMAND:
        switch (LOWORD(wparam)) {
        case IDOK:
            readFindDialog(dialog);
            findDirection(GetParent(dialog), true);
            return TRUE;
        case IDC_REPLACE_ONE:
            readFindDialog(dialog);
            replaceOne();
            return TRUE;
        case IDC_REPLACE_ALL:
            readFindDialog(dialog);
            replaceEvery(GetParent(dialog));
            return TRUE;
        case IDCANCEL:
            readFindDialog(dialog);
            EndDialog(dialog, IDCANCEL);
            return TRUE;
        }
        break;
    }
    return FALSE;
}

INT_PTR CALLBACK gotoProc(HWND dialog, UINT msg, WPARAM wparam, LPARAM)
{
    switch (msg) {
    case WM_INITDIALOG: {
        DWORD caret = 0;
        SendMessageW(g_edit, EM_GETSEL, 0, reinterpret_cast<LPARAM>(&caret));
        int line = static_cast<int>(SendMessageW(g_edit, EM_LINEFROMCHAR, caret, 0)) + 1;
        SetDlgItemInt(dialog, IDC_LINE, static_cast<UINT>(line), FALSE);
        return TRUE;
    }
    case WM_COMMAND:
        if (LOWORD(wparam) == IDCANCEL) {
            EndDialog(dialog, IDCANCEL);
            return TRUE;
        }
        if (LOWORD(wparam) == IDOK) {
            BOOL ok = FALSE;
            UINT line = GetDlgItemInt(dialog, IDC_LINE, &ok, FALSE);
            if (!ok || line < 1) {
                MessageBoxW(dialog, L"Нужен номер строки.", L"G134Office Lite", MB_ICONINFORMATION);
                return TRUE;
            }
            LRESULT index = SendMessageW(g_edit, EM_LINEINDEX, line - 1, 0);
            if (index < 0) {
                MessageBoxW(dialog, L"В тексте нет такой строки.", L"G134Office Lite", MB_ICONINFORMATION);
                return TRUE;
            }
            selectRange(static_cast<std::size_t>(index), static_cast<std::size_t>(index));
            EndDialog(dialog, IDOK);
            return TRUE;
        }
        break;
    }
    return FALSE;
}

void openFind(HWND window, bool replace)
{
    std::wstring seed = selectedText();
    if (seed.find(L'\r') != std::wstring::npos || seed.find(L'\n') != std::wstring::npos || seed.size() > 200) seed.clear();
    if (!replace && seed.empty()) seed = g_needle;
    DialogBoxParamW(g_instance, MAKEINTRESOURCEW(IDD_FIND), window, findProc, reinterpret_cast<LPARAM>(seed.empty() ? nullptr : &seed));
    SetFocus(g_edit);
}

void chooseFont(HWND window)
{
    LOGFONTW logFont{};
    logFont.lfHeight = -MulDiv(g_points, g_dpi > 0 ? g_dpi : 96, 72);
    logFont.lfWeight = FW_NORMAL;
    logFont.lfCharSet = DEFAULT_CHARSET;
    logFont.lfQuality = CLEARTYPE_QUALITY;
    logFont.lfPitchAndFamily = FIXED_PITCH | FF_MODERN;
    wcsncpy_s(logFont.lfFaceName, g_face.c_str(), _TRUNCATE);
    CHOOSEFONTW choose{};
    choose.lStructSize = sizeof(choose);
    choose.hwndOwner = window;
    choose.lpLogFont = &logFont;
    choose.iPointSize = g_points * 10;
    choose.Flags = CF_SCREENFONTS | CF_INITTOLOGFONTSTRUCT | CF_FORCEFONTEXIST | CF_NOSCRIPTSEL;
    if (!ChooseFontW(&choose)) return;
    g_face = logFont.lfFaceName;
    if (choose.iPointSize >= 80 && choose.iPointSize <= 720) g_points = choose.iPointSize / 10;
    applyFont();
    updateStatus();
}

void setEncoding(TextEncoding encoding)
{
    if (g_encoding == encoding) return;
    g_encoding = encoding;
    g_dirty = true;
    syncEncodingMenu();
    setTitle();
    updateStatus();
}

void recreateEdit(HWND window)
{
    std::wstring text = readEdit();
    DWORD anchor = 0;
    DWORD caret = 0;
    SendMessageW(g_edit, EM_GETSEL, reinterpret_cast<WPARAM>(&anchor), reinterpret_cast<LPARAM>(&caret));
    bool dirty = g_dirty;
    g_suppress = true;
    DestroyWindow(g_edit);
    g_edit = createEdit(window);
    applyFont();
    SetWindowTextW(g_edit, text.c_str());
    SendMessageW(g_edit, EM_SETSEL, anchor, caret);
    g_suppress = false;
    g_dirty = dirty;
    layout();
    SetFocus(g_edit);
    updateStatus();
}

void buildMenu(HWND window)
{
    HMENU menu = CreateMenu();
    HMENU file = CreatePopupMenu();
    AppendMenuW(file, MF_STRING, ID_NEW, L"Создать\tCtrl+N");
    AppendMenuW(file, MF_STRING, ID_OPEN, L"Открыть...\tCtrl+O");
    g_recentMenu = CreatePopupMenu();
    AppendMenuW(file, MF_POPUP, reinterpret_cast<UINT_PTR>(g_recentMenu), L"Недавние");
    AppendMenuW(file, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(file, MF_STRING, ID_SAVE, L"Сохранить\tCtrl+S");
    AppendMenuW(file, MF_STRING, ID_SAVEAS, L"Сохранить как...\tCtrl+Shift+S");
    g_encodingMenu = CreatePopupMenu();
    AppendMenuW(g_encodingMenu, MF_STRING, ID_ENC_UTF8, L"UTF-8");
    AppendMenuW(g_encodingMenu, MF_STRING, ID_ENC_UTF8BOM, L"UTF-8 с BOM");
    AppendMenuW(g_encodingMenu, MF_STRING, ID_ENC_UTF16LE, L"UTF-16 LE");
    AppendMenuW(g_encodingMenu, MF_STRING, ID_ENC_UTF16BE, L"UTF-16 BE");
    AppendMenuW(g_encodingMenu, MF_STRING, ID_ENC_1251, L"Windows-1251");
    AppendMenuW(file, MF_POPUP, reinterpret_cast<UINT_PTR>(g_encodingMenu), L"Кодировка");
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
    AppendMenuW(edit, MF_STRING, ID_FIND_NEXT, L"Найти далее\tF3");
    AppendMenuW(edit, MF_STRING, ID_FIND_PREV, L"Найти назад\tShift+F3");
    AppendMenuW(edit, MF_STRING, ID_REPLACE, L"Заменить...\tCtrl+H");
    AppendMenuW(edit, MF_STRING, ID_GOTO, L"Перейти к строке...\tCtrl+G");

    HMENU view = CreatePopupMenu();
    AppendMenuW(view, MF_STRING, ID_WRAP, L"Перенос строк");
    AppendMenuW(view, MF_STRING, ID_FONT, L"Шрифт...");
    AppendMenuW(view, MF_STRING, ID_ZOOM_IN, L"Крупнее\tCtrl++");
    AppendMenuW(view, MF_STRING, ID_ZOOM_OUT, L"Мельче\tCtrl+-");
    AppendMenuW(view, MF_STRING, ID_ZOOM_RESET, L"Обычный размер\tCtrl+0");
    AppendMenuW(view, MF_SEPARATOR, 0, nullptr);
    AppendMenuW(view, MF_STRING, ID_DARK, L"Тёмная тема");

    HMENU help = CreatePopupMenu();
    AppendMenuW(help, MF_STRING, ID_ABOUT, L"О программе");

    AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(file), L"Файл");
    AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(edit), L"Правка");
    AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(view), L"Вид");
    AppendMenuW(menu, MF_POPUP, reinterpret_cast<UINT_PTR>(help), L"Справка");
    SetMenu(window, menu);
    rebuildRecent();
    syncEncodingMenu();
    syncViewMenu();
}

LRESULT CALLBACK proc(HWND window, UINT msg, WPARAM wparam, LPARAM lparam)
{
    switch (msg) {
    case WM_CREATE:
        g_window = window;
        g_dpi = static_cast<int>(GetDpiForWindow(window));
        if (g_dpi <= 0) g_dpi = 96;
        g_edit = createEdit(window);
        g_status = CreateWindowExW(0, L"STATIC", L"", WS_CHILD | WS_VISIBLE | SS_LEFT | SS_CENTERIMAGE | SS_ENDELLIPSIS, 0, 0, 0, 0, window, nullptr, g_instance, nullptr);
        applyFont();
        buildMenu(window);
        DragAcceptFiles(window, TRUE);
        setTitle();
        refreshStats();
        updateStatus();
        return 0;
    case WM_DPICHANGED: {
        g_dpi = HIWORD(wparam);
        auto* suggested = reinterpret_cast<RECT*>(lparam);
        SetWindowPos(window, nullptr, suggested->left, suggested->top, suggested->right - suggested->left, suggested->bottom - suggested->top, SWP_NOZORDER | SWP_NOACTIVATE);
        applyFont();
        layout();
        return 0;
    }
    case WM_SIZE:
        layout();
        return 0;
    case WM_CTLCOLOREDIT:
    case WM_CTLCOLORSTATIC:
        if (!g_dark || highContrast()) break;
        SetTextColor(reinterpret_cast<HDC>(wparam), RGB(232, 232, 232));
        SetBkColor(reinterpret_cast<HDC>(wparam), msg == WM_CTLCOLORSTATIC ? RGB(17, 17, 17) : RGB(27, 27, 27));
        return reinterpret_cast<LRESULT>(msg == WM_CTLCOLORSTATIC ? g_darkStatusBrush : g_darkBrush);
    case WM_DROPFILES: {
        auto drop = reinterpret_cast<HDROP>(wparam);
        std::wstring path(32768, L'\0');
        UINT copied = DragQueryFileW(drop, 0, path.data(), static_cast<UINT>(path.size()));
        DragFinish(drop);
        if (copied > 0 && confirmLose(window)) {
            path.resize(copied);
            openPath(window, path);
        }
        return 0;
    }
    case WM_COMMAND:
        if (LOWORD(wparam) == kEditId && HIWORD(wparam) == EN_CHANGE) {
            markDirty();
            return 0;
        }
        if (LOWORD(wparam) >= ID_RECENT_BASE && LOWORD(wparam) < ID_RECENT_BASE + kRecentLimit) {
            std::size_t index = static_cast<std::size_t>(LOWORD(wparam) - ID_RECENT_BASE);
            if (index < g_recent.size()) {
                std::wstring path = g_recent[index];
                if (confirmLose(window) && openPath(window, path) == OpenResult::Failed) {
                    std::erase_if(g_recent, [&](const std::wstring& item) { return samePath(item, path); });
                    rebuildRecent();
                    saveLists();
                }
            }
            return 0;
        }
        switch (LOWORD(wparam)) {
        case ID_NEW:
            if (confirmLose(window)) {
                setEditorText(L"");
                g_path.clear();
                g_encoding = TextEncoding::Utf8;
                g_eol = LineEnding::CrLf;
                g_dirty = false;
                syncEncodingMenu();
                setTitle();
                refreshStats();
                updateStatus();
            }
            return 0;
        case ID_OPEN:
            if (confirmLose(window)) {
                std::wstring path = pickFile(window, false);
                if (!path.empty()) openPath(window, path);
            }
            return 0;
        case ID_SAVE:
            saveTo(window, g_path.empty() ? pickFile(window, true) : g_path);
            return 0;
        case ID_SAVEAS: {
            std::wstring path = pickFile(window, true);
            if (!path.empty()) saveTo(window, path);
            return 0;
        }
        case ID_EXIT:
            SendMessageW(window, WM_CLOSE, 0, 0);
            return 0;
        case ID_UNDO:
            SendMessageW(g_edit, WM_UNDO, 0, 0);
            return 0;
        case ID_CUT:
            SendMessageW(g_edit, WM_CUT, 0, 0);
            return 0;
        case ID_COPY:
            SendMessageW(g_edit, WM_COPY, 0, 0);
            return 0;
        case ID_PASTE:
            SendMessageW(g_edit, WM_PASTE, 0, 0);
            return 0;
        case ID_SELALL:
            SendMessageW(g_edit, EM_SETSEL, 0, -1);
            updateStatus();
            return 0;
        case ID_FIND:
            openFind(window, false);
            return 0;
        case ID_REPLACE:
            openFind(window, true);
            return 0;
        case ID_FIND_NEXT:
            findDirection(window, true);
            return 0;
        case ID_FIND_PREV:
            findDirection(window, false);
            return 0;
        case ID_GOTO:
            DialogBoxParamW(g_instance, MAKEINTRESOURCEW(IDD_GOTO), window, gotoProc, 0);
            SetFocus(g_edit);
            return 0;
        case ID_WRAP:
            g_wrap = !g_wrap;
            recreateEdit(window);
            syncViewMenu();
            return 0;
        case ID_ZOOM_IN:
            if (g_points < 72) {
                g_points = std::min(72, g_points + 1);
                applyFont();
                updateStatus();
            }
            return 0;
        case ID_ZOOM_OUT:
            if (g_points > 8) {
                g_points = std::max(8, g_points - 1);
                applyFont();
                updateStatus();
            }
            return 0;
        case ID_ZOOM_RESET:
            g_points = 16;
            applyFont();
            updateStatus();
            return 0;
        case ID_FONT:
            chooseFont(window);
            return 0;
        case ID_DARK:
            g_dark = !g_dark;
            syncViewMenu();
            if (g_edit) InvalidateRect(g_edit, nullptr, TRUE);
            if (g_status) InvalidateRect(g_status, nullptr, TRUE);
            return 0;
        case ID_ENC_UTF8: setEncoding(TextEncoding::Utf8); return 0;
        case ID_ENC_UTF8BOM: setEncoding(TextEncoding::Utf8Bom); return 0;
        case ID_ENC_UTF16LE: setEncoding(TextEncoding::Utf16Le); return 0;
        case ID_ENC_UTF16BE: setEncoding(TextEncoding::Utf16Be); return 0;
        case ID_ENC_1251: setEncoding(TextEncoding::Windows1251); return 0;
        case ID_ABOUT:
            MessageBoxW(
                window,
                L"G134Office Lite 0.2\n\nТекстовый редактор Windows без JVM и JavaFX.\nUTF-8, UTF-8 с BOM, UTF-16 и Windows-1251, поиск и замена, перенос строк.\n\nДокументы docx и pdf открывает отдельное приложение G134Office.\nhttps://github.com/G134T/G134Office",
                L"О программе",
                MB_OK | MB_ICONINFORMATION);
            return 0;
        }
        break;
    case WM_CLOSE:
        if (confirmLose(window)) DestroyWindow(window);
        return 0;
    case WM_DESTROY:
        saveLists();
        if (g_font) DeleteObject(g_font);
        if (g_uiFont) DeleteObject(g_uiFont);
        g_font = nullptr;
        g_uiFont = nullptr;
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(window, msg, wparam, lparam);
}

}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE, PWSTR, int show)
{
    g_instance = instance;
    g_comReady = SUCCEEDED(CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED | COINIT_DISABLE_OLE1DDE));
    INITCOMMONCONTROLSEX controls{sizeof(controls), ICC_STANDARD_CLASSES | ICC_WIN95_CLASSES};
    InitCommonControlsEx(&controls);
    loadLists();
    g_darkBrush = CreateSolidBrush(RGB(27, 27, 27));
    g_darkStatusBrush = CreateSolidBrush(RGB(17, 17, 17));

    WNDCLASSEXW windowClass{};
    windowClass.cbSize = sizeof(windowClass);
    windowClass.lpfnWndProc = proc;
    windowClass.hInstance = instance;
    windowClass.hCursor = LoadCursorW(nullptr, IDC_ARROW);
    windowClass.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
    windowClass.lpszClassName = L"G134OfficeLite";
    windowClass.hIcon = LoadIconW(instance, MAKEINTRESOURCEW(IDI_APP));
    windowClass.hIconSm = static_cast<HICON>(LoadImageW(instance, MAKEINTRESOURCEW(IDI_APP), IMAGE_ICON, GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), 0));
    RegisterClassExW(&windowClass);

    HWND window = CreateWindowExW(
        0,
        windowClass.lpszClassName,
        L"G134Office Lite",
        WS_OVERLAPPEDWINDOW,
        g_hasFrame ? g_frameX : CW_USEDEFAULT,
        g_hasFrame ? g_frameY : CW_USEDEFAULT,
        g_hasFrame ? g_frameW : 960,
        g_hasFrame ? g_frameH : 680,
        nullptr,
        nullptr,
        instance,
        nullptr);
    if (!window) {
        if (g_darkBrush) DeleteObject(g_darkBrush);
        if (g_darkStatusBrush) DeleteObject(g_darkStatusBrush);
        if (g_comReady) CoUninitialize();
        return 1;
    }
    ShowWindow(window, show);
    UpdateWindow(window);

    int argc = 0;
    LPWSTR* argv = CommandLineToArgvW(GetCommandLineW(), &argc);
    if (argv) {
        if (argc >= 2 && argv[1] && argv[1][0]) openPath(window, argv[1]);
        LocalFree(argv);
    }

    ACCEL accelerators[] = {
        {FCONTROL | FVIRTKEY, 'N', ID_NEW},
        {FCONTROL | FVIRTKEY, 'O', ID_OPEN},
        {FCONTROL | FVIRTKEY, 'S', ID_SAVE},
        {FCONTROL | FSHIFT | FVIRTKEY, 'S', ID_SAVEAS},
        {FCONTROL | FVIRTKEY, 'F', ID_FIND},
        {FCONTROL | FVIRTKEY, 'H', ID_REPLACE},
        {FCONTROL | FVIRTKEY, 'G', ID_GOTO},
        {FCONTROL | FVIRTKEY, 'A', ID_SELALL},
        {FVIRTKEY, VK_F3, ID_FIND_NEXT},
        {FSHIFT | FVIRTKEY, VK_F3, ID_FIND_PREV},
        {FCONTROL | FVIRTKEY, VK_OEM_PLUS, ID_ZOOM_IN},
        {FCONTROL | FSHIFT | FVIRTKEY, VK_OEM_PLUS, ID_ZOOM_IN},
        {FCONTROL | FVIRTKEY, VK_ADD, ID_ZOOM_IN},
        {FCONTROL | FVIRTKEY, VK_OEM_MINUS, ID_ZOOM_OUT},
        {FCONTROL | FVIRTKEY, VK_SUBTRACT, ID_ZOOM_OUT},
        {FCONTROL | FVIRTKEY, '0', ID_ZOOM_RESET},
    };
    HACCEL table = CreateAcceleratorTableW(accelerators, static_cast<int>(sizeof(accelerators) / sizeof(accelerators[0])));
    MSG message{};
    while (GetMessageW(&message, nullptr, 0, 0)) {
        if (!TranslateAcceleratorW(window, table, &message)) {
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    DestroyAcceleratorTable(table);
    if (g_darkBrush) DeleteObject(g_darkBrush);
    if (g_darkStatusBrush) DeleteObject(g_darkStatusBrush);
    if (g_comReady) CoUninitialize();
    return static_cast<int>(message.wParam);
}
