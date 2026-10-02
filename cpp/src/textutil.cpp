#include "textutil.hpp"

#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>

#include <algorithm>

namespace {

constexpr std::size_t npos = std::wstring::npos;

bool isSpace(wchar_t c)
{
    return c == L' ' || c == L'\t' || c == L'\n' || c == L'\r' || c == L'\f' || c == L'\v' || c == 0x00A0;
}

wchar_t lowerChar(wchar_t c)
{
    wchar_t out = c;
    int n = LCMapStringW(LOCALE_USER_DEFAULT, LCMAP_LOWERCASE, &c, 1, &out, 1);
    return n == 1 ? out : c;
}

std::wstring lowerCopy(std::wstring_view text)
{
    if (text.empty() || text.size() > static_cast<std::size_t>(INT_MAX)) return std::wstring(text);
    std::wstring out(text.size(), L'\0');
    int n = LCMapStringW(
        LOCALE_USER_DEFAULT,
        LCMAP_LOWERCASE,
        text.data(),
        static_cast<int>(text.size()),
        out.data(),
        static_cast<int>(out.size()));
    if (n != static_cast<int>(text.size())) return std::wstring(text);
    return out;
}

bool equalAt(std::wstring_view haystack, std::size_t index, std::wstring_view needle, bool matchCase)
{
    if (index + needle.size() > haystack.size()) return false;
    if (matchCase) return haystack.compare(index, needle.size(), needle) == 0;
    for (std::size_t i = 0; i < needle.size(); ++i) {
        if (lowerChar(haystack[index + i]) != lowerChar(needle[i])) return false;
    }
    return true;
}

std::wstring toLf(std::wstring_view text)
{
    std::wstring out;
    out.reserve(text.size());
    for (std::size_t i = 0; i < text.size(); ++i) {
        if (text[i] == L'\r') {
            out.push_back(L'\n');
            if (i + 1 < text.size() && text[i + 1] == L'\n') ++i;
        } else {
            out.push_back(text[i]);
        }
    }
    return out;
}

LineEnding detectEnding(std::wstring_view text)
{
    int crlf = 0;
    int lf = 0;
    int cr = 0;
    for (std::size_t i = 0; i < text.size(); ++i) {
        if (text[i] == L'\r' && i + 1 < text.size() && text[i + 1] == L'\n') {
            ++crlf;
            ++i;
        } else if (text[i] == L'\n') {
            ++lf;
        } else if (text[i] == L'\r') {
            ++cr;
        }
    }
    if (lf > crlf && lf >= cr) return LineEnding::Lf;
    if (cr > crlf && cr > lf) return LineEnding::Cr;
    return LineEnding::CrLf;
}

std::wstring applyEnding(std::wstring_view lfText, LineEnding ending)
{
    const wchar_t* eol = ending == LineEnding::Lf ? L"\n" : ending == LineEnding::Cr ? L"\r" : L"\r\n";
    std::wstring out;
    out.reserve(lfText.size() + 16);
    for (wchar_t c : lfText) {
        if (c == L'\n') out.append(eol);
        else out.push_back(c);
    }
    return out;
}

bool hasZeroByte(const std::uint8_t* data, std::size_t size)
{
    return std::find(data, data + size, static_cast<std::uint8_t>(0)) != data + size;
}

bool decodeUtf8(const std::uint8_t* data, std::size_t size, std::wstring& text)
{
    if (size == 0) {
        text.clear();
        return true;
    }
    if (size > static_cast<std::size_t>(INT_MAX)) return false;
    int chars = MultiByteToWideChar(
        CP_UTF8,
        MB_ERR_INVALID_CHARS,
        reinterpret_cast<const char*>(data),
        static_cast<int>(size),
        nullptr,
        0);
    if (chars <= 0) return false;
    text.assign(static_cast<std::size_t>(chars), L'\0');
    int written = MultiByteToWideChar(
        CP_UTF8,
        MB_ERR_INVALID_CHARS,
        reinterpret_cast<const char*>(data),
        static_cast<int>(size),
        text.data(),
        chars);
    return written == chars;
}

bool decodeCodePage(unsigned codePage, const std::uint8_t* data, std::size_t size, std::wstring& text, bool strict)
{
    if (size == 0) {
        text.clear();
        return true;
    }
    if (size > static_cast<std::size_t>(INT_MAX)) return false;
    DWORD flags = strict ? MB_ERR_INVALID_CHARS : 0;
    int chars = MultiByteToWideChar(codePage, flags, reinterpret_cast<const char*>(data), static_cast<int>(size), nullptr, 0);
    if (chars <= 0) return false;
    text.assign(static_cast<std::size_t>(chars), L'\0');
    int written = MultiByteToWideChar(
        codePage,
        flags,
        reinterpret_cast<const char*>(data),
        static_cast<int>(size),
        text.data(),
        chars);
    return written == chars;
}

bool decodeUtf16(const std::uint8_t* data, std::size_t size, bool bigEndian, std::wstring& text, std::wstring& error)
{
    if (size % 2 != 0) {
        error = L"Файл UTF-16 обрезан: нечётное число байт.";
        return false;
    }
    text.resize(size / 2);
    for (std::size_t i = 0; i < text.size(); ++i) {
        unsigned lo = bigEndian ? data[i * 2 + 1] : data[i * 2];
        unsigned hi = bigEndian ? data[i * 2] : data[i * 2 + 1];
        text[i] = static_cast<wchar_t>(static_cast<std::uint16_t>((hi << 8) | lo));
    }
    return true;
}

void finishDecoded(std::wstring raw, TextEncoding encoding, bool binary, DecodedText& out)
{
    out.ending = detectEnding(raw);
    out.text = toLf(raw);
    out.encoding = encoding;
    out.binary = binary;
}

bool encodeWide(const std::wstring& text, unsigned codePage, bool strict, std::string& out, std::wstring& error)
{
    if (text.empty()) {
        out.clear();
        return true;
    }
    if (text.size() > static_cast<std::size_t>(INT_MAX)) {
        error = L"Текст слишком большой для выбранной кодировки.";
        return false;
    }
    BOOL usedDefault = FALSE;
    DWORD flags = 0;
    if (codePage == CP_UTF8) flags = WC_ERR_INVALID_CHARS;
    else if (strict) flags = WC_NO_BEST_FIT_CHARS;
    int bytes = WideCharToMultiByte(
        codePage,
        flags,
        text.data(),
        static_cast<int>(text.size()),
        nullptr,
        0,
        strict && codePage != CP_UTF8 ? "\x3F" : nullptr,
        strict && codePage != CP_UTF8 ? &usedDefault : nullptr);
    if (bytes <= 0 || usedDefault) {
        error = codePage == 1251
            ? L"В тексте есть символы, которых нет в Windows-1251."
            : L"Не удалось перекодировать текст.";
        return false;
    }
    out.assign(static_cast<std::size_t>(bytes), '\0');
    usedDefault = FALSE;
    int written = WideCharToMultiByte(
        codePage,
        flags,
        text.data(),
        static_cast<int>(text.size()),
        out.data(),
        bytes,
        strict && codePage != CP_UTF8 ? "\x3F" : nullptr,
        strict && codePage != CP_UTF8 ? &usedDefault : nullptr);
    if (written != bytes || usedDefault) {
        error = codePage == 1251
            ? L"В тексте есть символы, которых нет в Windows-1251."
            : L"Не удалось перекодировать текст.";
        return false;
    }
    return true;
}

}

bool decodeText(const std::uint8_t* data, std::size_t size, DecodedText& out, std::wstring& error)
{
    error.clear();
    out = {};
    if (data == nullptr && size != 0) {
        error = L"Пустой буфер файла.";
        return false;
    }
    if (size == 0) return true;

    std::wstring raw;
    if (size >= 2 && data[0] == 0xFF && data[1] == 0xFE) {
        if (!decodeUtf16(data + 2, size - 2, false, raw, error)) return false;
        finishDecoded(std::move(raw), TextEncoding::Utf16Le, false, out);
        return true;
    }
    if (size >= 2 && data[0] == 0xFE && data[1] == 0xFF) {
        if (!decodeUtf16(data + 2, size - 2, true, raw, error)) return false;
        finishDecoded(std::move(raw), TextEncoding::Utf16Be, false, out);
        return true;
    }
    if (size >= 3 && data[0] == 0xEF && data[1] == 0xBB && data[2] == 0xBF) {
        if (!decodeUtf8(data + 3, size - 3, raw)) {
            error = L"Файл помечен как UTF-8, но содержит недопустимые байты.";
            return false;
        }
        finishDecoded(std::move(raw), TextEncoding::Utf8Bom, hasZeroByte(data + 3, size - 3), out);
        return true;
    }
    if (decodeUtf8(data, size, raw)) {
        finishDecoded(std::move(raw), TextEncoding::Utf8, hasZeroByte(data, size), out);
        return true;
    }
    if (!decodeCodePage(1251, data, size, raw, false)) {
        error = L"Не удалось прочитать файл ни как UTF-8, ни как Windows-1251.";
        return false;
    }
    finishDecoded(std::move(raw), TextEncoding::Windows1251, hasZeroByte(data, size), out);
    return true;
}

bool encodeText(std::wstring_view lfText, TextEncoding encoding, LineEnding ending, std::string& out, std::wstring& error)
{
    error.clear();
    std::wstring prepared = applyEnding(toLf(lfText), ending);
    std::string body;
    switch (encoding) {
    case TextEncoding::Utf8:
    case TextEncoding::Utf8Bom:
        if (!encodeWide(prepared, CP_UTF8, true, body, error)) return false;
        if (encoding == TextEncoding::Utf8Bom) out.assign("\xEF\xBB\xBF", 3);
        else out.clear();
        out.append(body);
        return true;
    case TextEncoding::Windows1251:
        return encodeWide(prepared, 1251, true, out, error);
    case TextEncoding::Utf16Le:
    case TextEncoding::Utf16Be: {
        bool big = encoding == TextEncoding::Utf16Be;
        out.resize(2 + prepared.size() * 2);
        out[0] = static_cast<char>(big ? 0xFE : 0xFF);
        out[1] = static_cast<char>(big ? 0xFF : 0xFE);
        for (std::size_t i = 0; i < prepared.size(); ++i) {
            auto unit = static_cast<unsigned>(static_cast<std::uint16_t>(prepared[i]));
            unsigned lo = big ? (unit >> 8) & 0xFF : unit & 0xFF;
            unsigned hi = big ? unit & 0xFF : (unit >> 8) & 0xFF;
            out[2 + i * 2] = static_cast<char>(lo);
            out[3 + i * 2] = static_cast<char>(hi);
        }
        return true;
    }
    default:
        error = L"Неизвестная кодировка.";
        return false;
    }
}

std::wstring encodingName(TextEncoding encoding)
{
    switch (encoding) {
    case TextEncoding::Utf8: return L"UTF-8";
    case TextEncoding::Utf8Bom: return L"UTF-8 BOM";
    case TextEncoding::Utf16Le: return L"UTF-16 LE";
    case TextEncoding::Utf16Be: return L"UTF-16 BE";
    case TextEncoding::Windows1251: return L"Windows-1251";
    default: return L"UTF-8";
    }
}

std::wstring endingName(LineEnding ending)
{
    switch (ending) {
    case LineEnding::CrLf: return L"CRLF";
    case LineEnding::Lf: return L"LF";
    case LineEnding::Cr: return L"CR";
    default: return L"CRLF";
    }
}

TextStats statsOf(std::wstring_view text)
{
    TextStats stats;
    bool inWord = false;
    for (wchar_t c : text) {
        if (c != L'\r' && c != L'\n') ++stats.chars;
        if (c == L'\n') ++stats.lines;
        if (isSpace(c)) inWord = false;
        else if (!inWord) {
            inWord = true;
            ++stats.words;
        }
    }
    return stats;
}

CaretPlace caretAt(std::wstring_view text, std::size_t index)
{
    if (index > text.size()) index = text.size();
    CaretPlace place;
    std::size_t lineStart = 0;
    for (std::size_t i = 0; i < index; ++i) {
        if (text[i] == L'\n') {
            ++place.line;
            lineStart = i + 1;
        }
    }
    for (std::size_t i = lineStart; i < index; ++i) {
        if (text[i] != L'\r') ++place.column;
    }
    return place;
}

std::size_t findText(std::wstring_view haystack, std::wstring_view needle, std::size_t from, bool matchCase)
{
    if (needle.empty() || from > haystack.size()) return npos;
    if (matchCase) return haystack.find(needle, from);
    std::wstring foldedHay = lowerCopy(haystack);
    std::wstring foldedNeedle = lowerCopy(needle);
    if (foldedHay.size() == haystack.size() && foldedNeedle.size() == needle.size()) {
        return foldedHay.find(foldedNeedle, from);
    }
    for (std::size_t i = from; i + needle.size() <= haystack.size(); ++i) {
        if (equalAt(haystack, i, needle, false)) return i;
    }
    return npos;
}

std::size_t findTextBefore(std::wstring_view haystack, std::wstring_view needle, std::size_t before, bool matchCase)
{
    if (needle.empty()) return npos;
    if (before > haystack.size()) before = haystack.size();
    if (before < needle.size()) return npos;
    std::wstring_view slice = haystack.substr(0, before);
    if (matchCase) return slice.rfind(needle);
    std::wstring folded = lowerCopy(slice);
    std::wstring foldedNeedle = lowerCopy(needle);
    if (folded.size() == slice.size() && foldedNeedle.size() == needle.size()) return folded.rfind(foldedNeedle);
    for (std::size_t offset = before - needle.size() + 1; offset > 0; --offset) {
        if (equalAt(haystack, offset - 1, needle, false)) return offset - 1;
    }
    return npos;
}

std::wstring replaceAll(std::wstring text, std::wstring_view needle, std::wstring_view replacement, bool matchCase, int& count)
{
    count = 0;
    if (needle.empty()) return text;
    std::size_t from = 0;
    while (from <= text.size()) {
        std::size_t at = findText(text, needle, from, matchCase);
        if (at == npos) break;
        text.replace(at, needle.size(), replacement);
        from = at + replacement.size();
        ++count;
        if (count >= 100000) break;
    }
    return text;
}

bool samePath(std::wstring_view left, std::wstring_view right)
{
    if (left.size() != right.size()) return false;
    for (std::size_t i = 0; i < left.size(); ++i) {
        auto fold = [](wchar_t c) {
            if (c == L'/') return L'\\';
            if (c >= L'A' && c <= L'Z') return static_cast<wchar_t>(c + 32);
            if (c >= 0x0410 && c <= 0x042F) return static_cast<wchar_t>(c + 0x20);
            if (c == 0x0401) return static_cast<wchar_t>(0x0451);
            return c;
        };
        if (fold(left[i]) != fold(right[i])) return false;
    }
    return true;
}

std::vector<std::wstring> rememberRecent(std::vector<std::wstring> recent, const std::wstring& path, std::size_t limit)
{
    if (path.empty() || limit == 0) return recent;
    std::vector<std::wstring> next;
    next.push_back(path);
    for (const auto& item : recent) {
        if (samePath(item, path)) continue;
        next.push_back(item);
        if (next.size() == limit) break;
    }
    return next;
}
