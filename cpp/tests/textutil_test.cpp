#include "textutil.hpp"

#include <cstdio>
#include <initializer_list>
#include <string>
#include <vector>

namespace {

int g_failed = 0;

void check(bool condition, const char* text, int line)
{
    if (condition) return;
    std::fprintf(stderr, "FAIL %s:%d %s\n", "textutil_test.cpp", line, text);
    ++g_failed;
}

#define CHECK(condition) check(static_cast<bool>(condition), #condition, __LINE__)

std::vector<std::uint8_t> bytes(std::initializer_list<unsigned> values)
{
    std::vector<std::uint8_t> out;
    for (unsigned value : values) out.push_back(static_cast<std::uint8_t>(value));
    return out;
}

}

int main()
{
    {
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(nullptr, 0, text, error));
        CHECK(text.text.empty());
        CHECK(text.encoding == TextEncoding::Utf8);
        CHECK(error.empty());
    }
    {
        auto data = bytes({0xD0, 0x90});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.text == L"А");
        CHECK(text.encoding == TextEncoding::Utf8);
        CHECK(!text.binary);
    }
    {
        auto data = bytes({0xEF, 0xBB, 0xBF, 0xD0, 0x90});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.text == L"А");
        CHECK(text.encoding == TextEncoding::Utf8Bom);
    }
    {
        auto data = bytes({0xFF, 0xFE, 0x10, 0x04});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.text == L"А");
        CHECK(text.encoding == TextEncoding::Utf16Le);
    }
    {
        auto data = bytes({0xFE, 0xFF, 0x04, 0x10});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.text == L"А");
        CHECK(text.encoding == TextEncoding::Utf16Be);
    }
    {
        auto data = bytes({0xFF, 0xFE, 0x00});
        DecodedText text;
        std::wstring error;
        CHECK(!decodeText(data.data(), data.size(), text, error));
        CHECK(!error.empty());
    }
    {
        auto data = bytes({0xC0});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.encoding == TextEncoding::Windows1251);
        CHECK(text.text == L"А");
    }
    {
        auto data = bytes({0xEF, 0xBB, 0xBF, 0xFF});
        DecodedText text;
        std::wstring error;
        CHECK(!decodeText(data.data(), data.size(), text, error));
    }
    {
        auto data = bytes({0x61, 0x00, 0x62});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.binary);
        CHECK(text.text.size() == 3);
        CHECK(text.text[1] == L'\0');
    }
    {
        auto data = bytes({'a', '\r', '\n', 'b'});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.text == L"a\nb");
        CHECK(text.ending == LineEnding::CrLf);
    }
    {
        auto data = bytes({'a', '\n', 'b'});
        DecodedText text;
        std::wstring error;
        CHECK(decodeText(data.data(), data.size(), text, error));
        CHECK(text.ending == LineEnding::Lf);
    }
    {
        std::wstring emoji(1, static_cast<wchar_t>(0xD83D));
        emoji.push_back(static_cast<wchar_t>(0xDE00));
        std::string encoded;
        std::wstring error;
        CHECK(encodeText(emoji, TextEncoding::Utf8, LineEnding::Lf, encoded, error));
        DecodedText text;
        CHECK(decodeText(reinterpret_cast<const std::uint8_t*>(encoded.data()), encoded.size(), text, error));
        CHECK(text.text == emoji);
        CHECK(text.encoding == TextEncoding::Utf8);
        CHECK(!encodeText(emoji, TextEncoding::Windows1251, LineEnding::Lf, encoded, error));
    }
    {
        std::string encoded;
        std::wstring error;
        CHECK(encodeText(L"А\nБ", TextEncoding::Utf8Bom, LineEnding::CrLf, encoded, error));
        CHECK(encoded.size() >= 3);
        CHECK(static_cast<unsigned char>(encoded[0]) == 0xEF);
        DecodedText text;
        CHECK(decodeText(reinterpret_cast<const std::uint8_t*>(encoded.data()), encoded.size(), text, error));
        CHECK(text.text == L"А\nБ");
        CHECK(text.encoding == TextEncoding::Utf8Bom);
        CHECK(text.ending == LineEnding::CrLf);
    }
    {
        std::string encoded;
        std::wstring error;
        CHECK(encodeText(L"А", TextEncoding::Windows1251, LineEnding::Lf, encoded, error));
        CHECK(encoded.size() == 1);
        CHECK(static_cast<unsigned char>(encoded[0]) == 0xC0);
    }
    {
        auto stats = statsOf(L"раз два\nтри");
        CHECK(stats.lines == 2);
        CHECK(stats.words == 3);
        CHECK(stats.chars == 10);
        auto empty = statsOf(L"");
        CHECK(empty.lines == 1);
        CHECK(empty.words == 0);
        CHECK(empty.chars == 0);
    }
    {
        auto place = caretAt(L"аб\nв", 3);
        CHECK(place.line == 2);
        CHECK(place.column == 1);
        place = caretAt(L"аб\nв", 1);
        CHECK(place.line == 1);
        CHECK(place.column == 2);
    }
    {
        CHECK(findText(L"абаб", L"аб", 1, true) == 2);
        CHECK(findText(L"Привет мир", L"привет", 0, false) == 0);
        CHECK(findText(L"Привет", L"нет", 0, false) == std::wstring::npos);
        CHECK(findTextBefore(L"абаб", L"аб", 4, true) == 2);
        CHECK(findTextBefore(L"абаб", L"аб", 2, true) == 0);
        CHECK(findTextBefore(L"Привет привет", L"ПРИВЕТ", 13, false) == 7);
        CHECK(findTextBefore(L"Привет привет", L"ПРИВЕТ", 7, false) == 0);
    }
    {
        int count = 0;
        auto replaced = replaceAll(L"АаА", L"а", L"б", false, count);
        CHECK(count == 3);
        CHECK(replaced == L"ббб");
        count = 99;
        replaced = replaceAll(L"аа", L"", L"б", true, count);
        CHECK(count == 0);
        CHECK(replaced == L"аа");
    }
    {
        std::vector<std::wstring> recent;
        recent = rememberRecent(recent, L"C:/Документы/Файл.txt", 2);
        recent = rememberRecent(recent, L"D:\\другой.txt", 2);
        recent = rememberRecent(recent, L"c:\\документы\\файл.txt", 2);
        CHECK(recent.size() == 2);
        CHECK(recent[0] == L"c:\\документы\\файл.txt");
        CHECK(samePath(recent[1], L"D:/Другой.txt"));
    }

    if (g_failed != 0) {
        std::fprintf(stderr, "%d checks failed\n", g_failed);
        return 1;
    }
    std::puts("ok");
    return 0;
}
