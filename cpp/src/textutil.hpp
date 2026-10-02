#pragma once

#include <cstddef>
#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

enum class TextEncoding {
    Utf8,
    Utf8Bom,
    Utf16Le,
    Utf16Be,
    Windows1251
};

enum class LineEnding {
    CrLf,
    Lf,
    Cr
};

struct DecodedText {
    std::wstring text;
    TextEncoding encoding = TextEncoding::Utf8;
    LineEnding ending = LineEnding::CrLf;
    bool binary = false;
};

struct TextStats {
    int lines = 1;
    int words = 0;
    int chars = 0;
};

struct CaretPlace {
    int line = 1;
    int column = 1;
};

bool decodeText(const std::uint8_t* data, std::size_t size, DecodedText& out, std::wstring& error);
bool encodeText(std::wstring_view lfText, TextEncoding encoding, LineEnding ending, std::string& out, std::wstring& error);

std::wstring encodingName(TextEncoding encoding);
std::wstring endingName(LineEnding ending);

TextStats statsOf(std::wstring_view text);
CaretPlace caretAt(std::wstring_view text, std::size_t index);

std::size_t findText(std::wstring_view haystack, std::wstring_view needle, std::size_t from, bool matchCase);
std::size_t findTextBefore(std::wstring_view haystack, std::wstring_view needle, std::size_t before, bool matchCase);
std::wstring replaceAll(std::wstring text, std::wstring_view needle, std::wstring_view replacement, bool matchCase, int& count);

std::vector<std::wstring> rememberRecent(std::vector<std::wstring> recent, const std::wstring& path, std::size_t limit);
bool samePath(std::wstring_view left, std::wstring_view right);
