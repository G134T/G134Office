def stats(text):
    words = [w for w in text.split() if w]
    return {
        "chars": len(text),
        "words": len(words),
        "lines": text.count("\n") + (1 if text else 0),
    }

def plain(html):
    out = []
    skip = False
    for ch in html:
        if ch == "<":
            skip = True
        elif ch == ">":
            skip = False
            out.append(" ")
        elif not skip:
            out.append(ch)
    return " ".join("".join(out).split())
