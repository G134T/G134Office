// C++ контракт веб-лайта. В браузере работает как WebAssembly.
// JS отвечает за экран и сохранение, C++ — за счёт символов, строк и слов.
extern "C" {
  int count_chars(const unsigned char*, int len) { return len; }
  int count_lines(const unsigned char* s, int len) {
    if (len <= 0) return 0;
    int lines = 1;
    for (int i = 0; i < len; ++i) if (s[i] == '\n') ++lines;
    return lines;
  }
  int count_words(const unsigned char* s, int len) {
    int words = 0, in = 0;
    for (int i = 0; i < len; ++i) {
      int word = s[i] > 32;
      if (word && !in) ++words;
      in = word;
    }
    return words;
  }
}
