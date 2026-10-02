// Быстрый локальный счётчик. В браузере ту же формулу вызывает engines.js.
// Сборка WASM: emcc text_engine.cpp -O2 -s MODULARIZE=1 -s EXPORT_NAME=G134Text -o text_engine.js
#include <stdint.h>
extern "C" {
  int g134_chars(const char* s) {
    int n = 0;
    if (!s) return 0;
    for (const unsigned char* p = (const unsigned char*)s; *p; ++p) {
      if ((*p & 0xC0) != 0x80) ++n;
    }
    return n;
  }
  int g134_words(const char* s) {
    int n = 0, in = 0;
    if (!s) return 0;
    for (const unsigned char* p = (const unsigned char*)s; *p; ++p) {
      int space = *p == ' ' || *p == '\n' || *p == '\t' || *p == '\r';
      if (space) in = 0;
      else if (!in) { in = 1; ++n; }
    }
    return n;
  }
}
