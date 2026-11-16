#!/usr/bin/env python3
"""Builds .temp/books/shelf_books.json.gz: chapters of public-domain books for the
chiseled bookshelves in Level 1882's libraries, laid out as Minecraft book pages.

Texts are downloaded once from Project Gutenberg into .temp/gutenberg/. Each work gets a few
excerpts, each starting at a chapter heading and running for up to PAGES pages. The licence
header and footer are stripped, so the pages carry only the public-domain text.

    python3 scripts/books/gutenberg.py && python3 scripts/scans/publish.py

The file isn't shipped in the jar: publish.py adds it to meshes/ and the manifest, and servers
download it like the level meshes.
"""

import gzip
import json
import re
import sys
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / ".temp" / "gutenberg"
OUT = ROOT / ".temp" / "books" / "shelf_books.json.gz"

PAGES = 20          # pages per excerpt
EXCERPTS = 3        # excerpts per subject work
ANY_EXCERPTS = 2    # excerpts per general-literature work
WIDTH = 113         # book line width in pixels (the client's is 114)
LINES = 14          # lines per page

# (Gutenberg id, title as shown (<= 32 chars), author, gallery themes; ANY = general shelves)
WORKS = [
    (38015, "De Re Metallica", "Georg Agricola", ["MINERALS"]),
    (4701, "The Ethics of the Dust", "John Ruskin", ["MINERALS"]),
    (28058, "A Text-Book of Precious Stones", "Frank B. Wade", ["MINERALS"]),
    (2166, "King Solomon's Mines", "H. Rider Haggard", ["MINERALS", "ARCHAEOLOGY"]),
    (139, "The Lost World", "Arthur Conan Doyle", ["FOSSILS"]),
    (63923, "The Old Red Sandstone", "Hugh Miller", ["FOSSILS", "GEOLOGY"]),
    (28248, "The Testimony of the Rocks", "Hugh Miller", ["FOSSILS", "GEOLOGY"]),
    (35316, "Dragons of the Air", "H. G. Seeley", ["FOSSILS"]),
    (7116, "Vestiges of Creation", "Robert Chambers", ["FOSSILS", "ZOOLOGY"]),
    (1228, "On the Origin of Species", "Charles Darwin", ["FOSSILS", "ZOOLOGY", "BOTANY"]),
    (84, "Frankenstein", "Mary Shelley", ["MOBS"]),
    (345, "Dracula", "Bram Stoker", ["MOBS"]),
    (159, "The Island of Doctor Moreau", "H. G. Wells", ["MOBS"]),
    (43, "Dr. Jekyll and Mr. Hyde", "Robert Louis Stevenson", ["MOBS"]),
    (2300, "The Descent of Man", "Charles Darwin", ["MOBS", "ZOOLOGY"]),
    (5200, "Metamorphosis", "Franz Kafka", ["MOBS", "ZOOLOGY"]),
    (49513, "The Complete Herbal", "Nicholas Culpeper", ["BOTANY"]),
    (5765, "Insectivorous Plants", "Charles Darwin", ["BOTANY"]),
    (205, "Walden", "Henry David Thoreau", ["BOTANY"]),
    (2355, "Vegetable Mould and Worms", "Charles Darwin", ["BOTANY", "GEOLOGY"]),
    (1322, "Leaves of Grass", "Walt Whitman", ["BOTANY"]),
    (2701, "Moby-Dick", "Herman Melville", ["MARINE"]),
    (164, "Twenty Thousand Leagues", "Jules Verne", ["MARINE"]),
    (695, "Glaucus", "Charles Kingsley", ["MARINE"]),
    (2690, "Coral Reefs", "Charles Darwin", ["MARINE", "GEOLOGY"]),
    (683, "The Compleat Angler", "Izaak Walton", ["MARINE"]),
    (1268, "The Mysterious Island", "Jules Verne", ["MARINE"]),
    (2707, "The History of Herodotus", "Herodotus", ["ARCHAEOLOGY"]),
    (1727, "The Odyssey", "Homer", ["ARCHAEOLOGY"]),
    (6130, "The Iliad", "Homer", ["ARCHAEOLOGY"]),
    (586, "Hydriotaphia, Urn Burial", "Sir Thomas Browne", ["ARCHAEOLOGY", "CURIOSITIES"]),
    (51960, "Ancient Stone Implements", "John Evans", ["ARCHAEOLOGY"]),
    (59783, "Tutankhamen", "G. Elliot Smith", ["ARCHAEOLOGY"]),
    (1565, "The Last Days of Pompeii", "Edward Bulwer-Lytton", ["ARCHAEOLOGY"]),
    (9628, "Buried Cities", "Jennie Hall", ["ARCHAEOLOGY"]),
    (20239, "The Ten Books on Architecture", "Vitruvius", ["ARCHAEOLOGY", "ART"]),
    (1001, "Inferno", "Dante Alighieri", ["NETHER"]),
    (26, "Paradise Lost", "John Milton", ["NETHER"]),
    (779, "Doctor Faustus", "Christopher Marlowe", ["NETHER"]),
    (123, "At the Earth's Core", "Edgar Rice Burroughs", ["NETHER"]),
    (18857, "Journey to the Centre", "Jules Verne", ["NETHER", "GEOLOGY"]),
    (35, "The Time Machine", "H. G. Wells", ["END"]),
    (18247, "The Last Man", "Mary Shelley", ["END"]),
    (10662, "The Night Land", "William Hope Hodgson", ["END"]),
    (11229, "The Purple Cloud", "M. P. Shiel", ["END"]),
    (201, "Flatland", "Edwin A. Abbott", ["END", "CURIOSITIES"]),
    (36, "The War of the Worlds", "H. G. Wells", ["END"]),
    (1013, "The First Men in the Moon", "H. G. Wells", ["END"]),
    (27378, "The Story of the Heavens", "Robert S. Ball", ["END"]),
    (11, "Alice in Wonderland", "Lewis Carroll", ["CURIOSITIES"]),
    (12, "Through the Looking-Glass", "Lewis Carroll", ["CURIOSITIES"]),
    (829, "Gulliver's Travels", "Jonathan Swift", ["CURIOSITIES"]),
    (5230, "The Invisible Man", "H. G. Wells", ["CURIOSITIES"]),
    (15491, "Micrographia", "Robert Hooke", ["CURIOSITIES"]),
    (33504, "Opticks", "Isaac Newton", ["CURIOSITIES"]),
    (14474, "The Chemical History of a Candle", "Michael Faraday", ["CURIOSITIES"]),
    (30155, "Relativity", "Albert Einstein", ["CURIOSITIES"]),
    (932, "The Fall of the House of Usher", "Edgar Allan Poe", ["CURIOSITIES"]),
    (57493, "The Natural History of Pliny", "Pliny the Elder", ["CURIOSITIES", "ZOOLOGY"]),
    (174, "The Picture of Dorian Gray", "Oscar Wilde", ["ART"]),
    (5000, "Notebooks", "Leonardo da Vinci", ["ART"]),
    (2398, "The Renaissance", "Walter Pater", ["ART"]),
    (2176, "Seven Discourses on Art", "Sir Joshua Reynolds", ["ART"]),
    (35898, "The Seven Lamps of Architecture", "John Ruskin", ["ART"]),
    (30754, "The Stones of Venice", "John Ruskin", ["ART"]),
    (944, "The Voyage of the Beagle", "Charles Darwin", ["ZOOLOGY"]),
    (2530, "The Malay Archipelago", "Alfred Russel Wallace", ["ZOOLOGY"]),
    (1408, "The Natural History of Selborne", "Gilbert White", ["ZOOLOGY"]),
    (2440, "A Naturalist on the Amazons", "Henry Walter Bates", ["ZOOLOGY"]),
    (1887, "The Life of the Spider", "Jean-Henri Fabre", ["ZOOLOGY"]),
    (4511, "The Life of the Bee", "Maurice Maeterlinck", ["ZOOLOGY"]),
    (2781, "Just So Stories", "Rudyard Kipling", ["ZOOLOGY"]),
    (33224, "Principles of Geology", "Charles Lyell", ["GEOLOGY"]),
    (14565, "Cosmos", "Alexander von Humboldt", ["GEOLOGY"]),
    (31627, "Volcanoes: Past and Present", "Edward Hull", ["GEOLOGY"]),
    (59080, "The Earth's Beginning", "Robert S. Ball", ["GEOLOGY"]),
    (1342, "Pride and Prejudice", "Jane Austen", ["ANY"]),
    (1400, "Great Expectations", "Charles Dickens", ["ANY"]),
    (1260, "Jane Eyre", "Charlotte Bronte", ["ANY"]),
    (1524, "Hamlet", "William Shakespeare", ["ANY"]),
    (996, "Don Quixote", "Miguel de Cervantes", ["ANY"]),
    (120, "Treasure Island", "Robert Louis Stevenson", ["ANY"]),
    (103, "Around the World in 80 Days", "Jules Verne", ["ANY"]),
    (1661, "Adventures of Sherlock Holmes", "Arthur Conan Doyle", ["ANY"]),
    (145, "Middlemarch", "George Eliot", ["ANY"]),
    (2814, "Dubliners", "James Joyce", ["ANY"]),
    (1497, "The Republic", "Plato", ["ANY"]),
    (2680, "Meditations", "Marcus Aurelius", ["ANY"]),
    (1232, "The Prince", "Niccolo Machiavelli", ["ANY"]),
    (76, "Huckleberry Finn", "Mark Twain", ["ANY"]),
    (1184, "The Count of Monte Cristo", "Alexandre Dumas", ["ANY"]),
    (1952, "The Yellow Wallpaper", "Charlotte Perkins Gilman", ["ANY"]),
    (3296, "Confessions", "Saint Augustine", ["ANY"]),
    (7849, "The Trial", "Franz Kafka", ["ANY"]),
]

# Advance widths of Minecraft's default font (glyph width + 1 pixel of spacing).
NARROW = {" ": 4, "!": 2, '"': 4, "'": 2, "(": 4, ")": 4, "*": 4, ",": 2, ".": 2, ":": 2, ";": 2,
          "<": 5, ">": 5, "@": 7, "I": 4, "[": 4, "]": 4, "`": 3, "f": 5, "i": 2, "k": 5, "l": 3,
          "t": 4, "{": 4, "}": 4, "|": 2, "~": 7}

ASCII = {"‘": "'", "’": "'", "“": '"', "”": '"', "—": "--", "–": "-",
         "…": "...", " ": " ", "æ": "ae", "Æ": "Ae", "œ": "oe", "Œ": "Oe"}

HEADING = re.compile(r"^(CHAPTER|Chapter|BOOK|Book|CANTO|Canto|PART|Part|LETTER|Letter|ACT|LECTURE|Lecture|"
                     r"SECTION|STAVE|ADVENTURE|[IVXLC]+\.|[IVXLC]+$|\d+\.)\b")


# tables, transcriber's notes, index entries and footnote references, which read badly in a book
JUNK = re.compile(r"\+--|\|.*\||\S {4,}\S|^Transcriber|amended to|^\{?\d+\}? |, \d+, \d+, \d+", re.M)
# back matter and apparatus: never start an excerpt there
BACK = re.compile(r"APPENDIX|INDEX|NOTES|PLATES|CONTENTS|ILLUSTRATIONS|GLOSSARY|FOOTNOTE", re.I)


def number(heading):
    """The chapter number in a heading (arabic or roman), or None."""
    m = re.search(r"\b(\d+|[IVXLC]+)\b", heading)
    if not m:
        return None
    t = m.group(1)
    if t.isdigit():
        return int(t)
    vals, n = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100}, 0
    for a, b in zip(t, t[1:] + " "):
        n += -vals[a] if b != " " and vals[b] > vals[a] else vals[a]
    return n


def width(s):
    return sum(NARROW.get(ch, 6) for ch in s)


def fetch(gid):
    path = CACHE / f"{gid}.txt"
    if not path.exists() or path.stat().st_size == 0:
        CACHE.mkdir(parents=True, exist_ok=True)
        url = f"https://www.gutenberg.org/cache/epub/{gid}/pg{gid}.txt"
        print(f"  fetching {url}", file=sys.stderr)
        with urllib.request.urlopen(url, timeout=120) as r:
            path.write_bytes(r.read())
    return path.read_text(encoding="utf-8", errors="replace")


def clean(s):
    for k, v in ASCII.items():
        s = s.replace(k, v)
    s = re.sub(r"\[(Illustration|Footnote|Sidenote)[^\]]*\]", "", s, flags=re.S)
    s = re.sub(r"\[\d+\]", "", s)
    s = s.replace("_", "").replace("=", "")
    # keep Latin letters with accents (the default font has them), drop anything else exotic
    return "".join(ch for ch in s if ch in "\n" or (32 <= ord(ch) < 127)
                   or unicodedata.category(ch).startswith("L") and ord(ch) < 0x250)


def body(text):
    start = re.search(r"\*\*\* ?START OF.*?\*\*\*", text)
    end = re.search(r"\*\*\* ?END OF", text)
    text = text[start.end() if start else 0:end.start() if end else len(text)]
    return clean(text.replace("\r", ""))


def paragraphs(text):
    """Blocks of text between blank lines: (lines, is_verse)."""
    out = []
    for block in re.split(r"\n\s*\n", text):
        lines = [l.strip() for l in block.split("\n") if l.strip()]
        if not lines or JUNK.search(block):
            continue
        verse = len(lines) >= 2 and sum(len(l) < 55 for l in lines) >= len(lines) * 0.8
        out.append((lines, verse))
    return out


def is_heading(par, nxt):
    lines, _ = par
    return (len(lines) <= 3 and len(lines[0]) < 60 and HEADING.match(lines[0]) is not None
            and any(len(" ".join(n[0])) > 250 for n in nxt))


def wrap(line, limit=WIDTH):
    """Word-wraps one line of text to the book's width."""
    out, cur = [], ""
    for word in line.split(" "):
        while width(word) > limit:  # a word too long for a line: break it
            cut = len(word)
            while width(word[:cut]) > limit:
                cut -= 1
            if cur:
                out.append(cur)
                cur = ""
            out.append(word[:cut])
            word = word[cut:]
        trial = word if not cur else cur + " " + word
        if width(trial) <= limit:
            cur = trial
        else:
            out.append(cur)
            cur = word
    out.append(cur)
    return out


def paginate(pars, heading):
    """Lays paragraphs out as pages of LINES lines, stopping at PAGES pages."""
    pages, page = [], []

    def push(line):
        nonlocal page
        if len(page) == LINES:
            pages.append("\n".join(page))
            page = []
        if not page and line == "":
            return  # no blank line at the top of a page
        page.append(line)

    for l in heading[0]:
        for w in wrap(l):
            push(w)
    push("")
    for lines, verse in pars:
        rows = []
        if verse:
            for l in lines:
                first, *rest = wrap(l)  # a verse line that runs over is indented
                rows += [first] + ["  " + r for r in wrap(" ".join(rest), WIDTH - 8)] if rest else [first]
        else:
            rows = wrap(" ".join(lines))
        if len(pages) + (len(page) + len(rows)) / LINES > PAGES:
            # last paragraph: keep whole sentences that fit
            room = (PAGES - len(pages)) * LINES - len(page)
            if room < 3:
                break
            if verse:  # verse: as many whole lines as fit
                for w in rows[:room]:
                    push(w)
                break
            text = " ".join(lines)
            sentences = re.findall(r"[^.!?]+[.!?]+[\"')]*\s*", text)
            kept = ""
            for s in sentences:
                if len(wrap((kept + s).strip())) > room:
                    break
                kept += s
            for w in wrap(kept.strip()) if kept.strip() else []:
                push(w)
            break
        for w in rows:
            push(w)
        push("")
    while page and page[-1] == "":
        page.pop()
    if page:
        pages.append("\n".join(page))
    return pages[:PAGES]


def excerpts(gid, n):
    pars = paragraphs(body(fetch(gid)))
    heads = [i for i, p in enumerate(pars) if is_heading(p, pars[i + 1:i + 4])]
    # skip a table of contents: headings with no real text between them
    heads = [h for k, h in enumerate(heads)
             if sum(len(" ".join(p[0])) for p in pars[h + 1:(heads[k + 1] if k + 1 < len(heads) else len(pars))]) > 2000]
    # the real chapter, not its line in a table of contents, and nothing from the back matter
    last = {}
    for h in heads:
        if h < len(pars) * 0.93 and not any(BACK.search(" ".join(p[0])) for p in pars[h:h + 3]):
            last[re.sub(r"\W+", " ", pars[h][0][0]).strip().upper()] = h
    heads = sorted(last.values())
    # a contents entry left over is numbered out of order: CHAPTER 135 just before CHAPTER I
    nums = [number(pars[h][0][0]) for h in heads]
    heads = [h for k, h in enumerate(heads)
             if k + 1 >= len(heads) or nums[k] is None or nums[k + 1] is None or nums[k] <= nums[k + 1]]
    if len(heads) < n:
        # no usable chapter headings: start at paragraphs spread through the text
        heads = [int(len(pars) * f) for f in (0.08, 0.45, 0.8)]
        heads = [next((j for j in range(h, len(pars)) if len(" ".join(pars[j][0])) > 250), h) for h in heads]
        return [paginate(pars[h:], ([], False)) for h in heads[:n]]
    picks = sorted({heads[round(k * (len(heads) - 1) / max(1, n - 1))] for k in range(n)})
    out = []
    for h in picks:
        j = h + 1  # a heading may run on into a subtitle line or two
        head = list(pars[h][0])
        while j < len(pars) and len(" ".join(pars[j][0])) < 80 and len(head) < 4:
            head += pars[j][0]
            j += 1
        out.append(paginate(pars[j:], (head, False)))
    return out


def main():
    works = []
    for gid, title, author, themes in WORKS:
        assert len(title) <= 32, title
        ex = [e for e in excerpts(gid, ANY_EXCERPTS if themes == ["ANY"] else EXCERPTS) if len(e) >= 4]
        print(f"{gid:>6} {title:<34} {len(ex)} excerpts, {sum(map(len, ex))} pages", file=sys.stderr)
        if ex:
            works.append({"title": title, "author": author, "themes": themes, "excerpts": ex})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    data = json.dumps({"works": works}, ensure_ascii=False, separators=(",", ":")).encode()
    OUT.write_bytes(gzip.compress(data, 9, mtime=0))
    print(f"{len(works)} works, {len(data) // 1024} KB, {OUT.stat().st_size // 1024} KB gzipped -> {OUT.relative_to(ROOT)}",
          file=sys.stderr)


if __name__ == "__main__":
    main()
