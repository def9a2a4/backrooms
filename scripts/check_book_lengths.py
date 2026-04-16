#!/usr/bin/env python3
"""Check that all book page strings in YAML configs fit within Minecraft's ~256 char page limit."""

import sys
import glob
import yaml

MAX_PAGE_LENGTH = 256
BOOK_YMLS = glob.glob("src/main/resources/levels/*_books.yml")

if not BOOK_YMLS:
    print("No *_books.yml files found")
    sys.exit(1)

failed = False

for path in sorted(BOOK_YMLS):
    data = yaml.safe_load(open(path))
    if data is None:
        continue

    file_label = path.split("/")[-1]
    violations = []

    # Single pages
    for i, page in enumerate(data.get("pages", [])):
        if len(page) > MAX_PAGE_LENGTH:
            violations.append(f"  pages[{i}]: {len(page)} chars")

    # Multi-page books
    for book in data.get("multi_page_books", []):
        title = book.get("title", "?")
        for i, page in enumerate(book.get("pages", [])):
            if len(page) > MAX_PAGE_LENGTH:
                violations.append(f"  \"{title}\" page {i+1}: {len(page)} chars")

    # Cursed snippets (embedded in gibberish, but still worth flagging if very long)
    for i, snippet in enumerate(data.get("cursed_snippets", [])):
        if len(snippet) > MAX_PAGE_LENGTH:
            violations.append(f"  cursed_snippets[{i}]: {len(snippet)} chars")

    # Stats
    all_pages = list(data.get("pages", []))
    for book in data.get("multi_page_books", []):
        all_pages.extend(book.get("pages", []))
    snippets = data.get("cursed_snippets", [])

    n_titles = len(data.get("titles", []))
    n_authors = len(data.get("authors", []))
    n_books = len(data.get("multi_page_books", []))

    print(f"{file_label}:")
    if all_pages:
        lengths = [len(p) for p in all_pages]
        print(f"  {len(all_pages)} pages (min={min(lengths)}, max={max(lengths)}, avg={sum(lengths)//len(lengths)})")
    if n_titles:
        print(f"  {n_titles} titles, {n_authors} authors, {n_books} multi-page books")
    if snippets:
        print(f"  {len(snippets)} cursed snippets")

    if violations:
        failed = True
        print(f"  FAIL - {len(violations)} page(s) over {MAX_PAGE_LENGTH} chars:")
        for v in violations:
            print(v)
    else:
        print(f"  OK - all pages <= {MAX_PAGE_LENGTH} chars")
    print()

sys.exit(1 if failed else 0)
