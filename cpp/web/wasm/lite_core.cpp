#include <cctype>
#include <cstddef>
#include <cstring>

#ifdef __EMSCRIPTEN__
#include <emscripten/emscripten.h>
#define G134_EXPORT EMSCRIPTEN_KEEPALIVE
#else
#define G134_EXPORT
#endif

namespace {

bool isSpace(unsigned char ch) {
    return std::isspace(ch) != 0;
}

std::size_t safeLength(const char* text) {
    return text ? std::strlen(text) : 0;
}

} // namespace

extern "C" {

G134_EXPORT int g134_word_count(const char* text) {
    if (!text || !*text) return 0;

    int words = 0;
    bool insideWord = false;
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p; ++p) {
        if (isSpace(*p)) {
            insideWord = false;
        } else if (!insideWord) {
            insideWord = true;
            ++words;
        }
    }
    return words;
}

G134_EXPORT int g134_char_count(const char* text) {
    if (!text) return 0;

    // Count UTF-8 code points instead of raw bytes.
    int count = 0;
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p; ++p) {
        if ((*p & 0xC0u) != 0x80u) ++count;
    }
    return count;
}

G134_EXPORT int g134_line_count(const char* text) {
    if (!text || !*text) return 1;

    int lines = 1;
    for (const char* p = text; *p; ++p) {
        if (*p == '\n') ++lines;
    }
    return lines;
}

G134_EXPORT int g134_reading_seconds(const char* text) {
    const int words = g134_word_count(text);
    if (words <= 0) return 0;

    // 200 words/minute, rounded up to avoid showing zero for short text.
    return (words * 60 + 199) / 200;
}

G134_EXPORT int g134_nonspace_char_count(const char* text) {
    if (!text) return 0;

    int count = 0;
    for (const unsigned char* p = reinterpret_cast<const unsigned char*>(text); *p; ++p) {
        if (isSpace(*p)) continue;
        if ((*p & 0xC0u) != 0x80u) ++count;
    }
    return count;
}

G134_EXPORT int g134_is_empty(const char* text) {
    return safeLength(text) == 0 ? 1 : 0;
}

} // extern "C"
