#!/usr/bin/env python3
"""Scrape level-based success markers -> src/main/resources/success_params.json.

Two wiki sources, merged into the recipes at build time (§4 of the design doc):

  A) {{Skilling success chart}} template params (low/high/req) -> LINEAR_INTERP
     (semi-precious gem cuts etc.). Verified: opal low=128 -> 129/256 = 0.5039 @ L1.
  B) The Cooking/Burn level tables                            -> BURN
     Base stop level = the "Range" column; gauntlet stop level = the "Gauntlets
     Default" column. Hosidius/cape are runtime ADD_CHANCE/FORCE_SUCCESS
     modifiers (see success_modifiers.json), not captured here. reqLevel is
     filled from the recipe at merge time.

Output is a JSON object keyed by item id (string) -> success model. BURN entries
overwrite LINEAR_INTERP on the same id (a cooked fish is never a gem cut).

Reuses api(), wikitext_batch(), parse_recipe(), _clean, _int, _indices, and
load_name_index() from extract_recipes.py. Standard library only.
"""
import argparse
import json
import os
import re
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import extract_recipes as ex  # noqa: E402

DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "success_params.json",
)

_CHART = re.compile(r"\{\{\s*Skilling success chart\s*(?=[|}])", re.I)
_PLINK = re.compile(r"\{\{\s*plink[t]?\s*\|\s*([^}|]+)", re.I)


def iter_templates(text, start_re):
    """Yield the inner body of each brace-matched template matching start_re."""
    last = 0
    for m in start_re.finditer(text):
        if m.start() < last:
            continue
        s, k, depth, n = m.start(), m.start(), 0, len(text)
        while k < n:
            if text.startswith("{{", k):
                depth += 1
                k += 2
            elif text.startswith("}}", k):
                depth -= 1
                k += 2
                if depth == 0:
                    break
            else:
                k += 1
        last = k
        yield text[s + 2:k - 2]


def pages_transcluding(template):
    """Every mainspace page transcluding a template (paginated title list)."""
    titles, cont = [], {}
    while True:
        d = ex.api(action="query", list="embeddedin", eititle=template,
                   einamespace=0, eilimit="max", **cont)
        titles += [p["title"] for p in d["query"]["embeddedin"]]
        cont = d.get("continue")
        if not cont:
            return titles
        time.sleep(0.5)


def scrape_interp(idx):
    """{{Skilling success chart}} -> {itemId: {type, low, high, req}}."""
    out = {}
    titles = pages_transcluding("Template:Skilling success chart")
    for j in range(0, len(titles), 50):
        for _, text in ex.wikitext_batch(titles[j:j + 50]).items():
            for body in iter_templates(text, _CHART):
                kv = ex.parse_recipe(body)
                for i in ex._indices(kv, "label"):
                    name = ex._clean(kv.get(f"label{i}"))
                    if not name:
                        img = kv.get(f"image{i}", "") or ""
                        name = ex._clean(img.replace(".png", ""))
                    item = idx.get(name, {}).get("id") if name else None
                    low, high = ex._int(kv.get(f"low{i}")), ex._int(kv.get(f"high{i}"))
                    if item and low is not None and high is not None:
                        out[str(item)] = {"type": "LINEAR_INTERP", "low": low, "high": high,
                                          "req": ex._int(kv.get(f"req{i}"), 1)}
        time.sleep(0.4)
    return out


# --- Cooking/Burn level table parsing -------------------------------------

def _split_attr(body):
    """Split a wikitable cell 'attrs|content' on the first top-level '|'."""
    depth_t = depth_l = 0
    for i, ch in enumerate(body):
        two = body[i:i + 2]
        if two == "{{":
            depth_t += 1
        elif two == "}}":
            depth_t -= 1
        elif two == "[[":
            depth_l += 1
        elif two == "]]":
            depth_l -= 1
        elif ch == "|" and depth_t <= 0 and depth_l <= 0:
            return body[:i], body[i + 1:]
    return "", body


def _hlabel(content):
    """Clean a header cell to a plain label (strip refs, tags, links, templates)."""
    content = re.sub(r"<ref[^>]*>.*?</ref>", "", content, flags=re.S)
    content = re.sub(r"<ref[^>]*/>", "", content)
    content = re.sub(r"<[^>]+>", "", content)
    return (ex._clean(content) or "").strip()


def _span(attrs, key):
    m = re.search(key + r"\s*=\s*\"?(\d+)", attrs)
    return int(m.group(1)) if m else 1


def _split_tables(text, start, end):
    """Yield each '{| ... |}' wikitable body found between two section markers."""
    i = text.find(start)
    j = text.find(end) if end else len(text)
    region = text[i:j] if i >= 0 else text
    k = 0
    while True:
        a = region.find("{|", k)
        if a < 0:
            return
        depth, p = 0, a
        while p < len(region):
            if region.startswith("{|", p):
                depth += 1
                p += 2
            elif region.startswith("|}", p):
                depth -= 1
                p += 2
                if depth == 0:
                    break
            else:
                p += 1
        yield region[a:p]
        k = p


def _leaf_headers(hrows):
    """Expand two-row headers (colspan/rowspan) into a flat leaf-column list."""
    if not hrows:
        return []
    row1 = hrows[0]
    row2 = list(hrows[1]) if len(hrows) > 1 else []
    leaves = []
    for label, cs, rs in row1:
        if rs >= 2 or not row2:
            leaves.extend([label] * max(cs, 1))
        else:
            for _ in range(max(cs, 1)):
                sub = row2.pop(0)[0] if row2 else None
                leaves.append(f"{label} {sub}".strip() if sub and sub != label else label)
    collapsed = []
    for lf in leaves:  # collapse the colspan-2 "Food" header down to one data column
        if collapsed and collapsed[-1] == lf:
            continue
        collapsed.append(lf)
    return collapsed


def _parse_table(tbl):
    """Return (leaf_headers, data_rows) for one burn wikitable."""
    rows = re.split(r"\n\|-", tbl)
    header_rows, data_rows = [], []
    for row in rows:
        hcells, dcells, is_header = [], [], False
        for line in row.split("\n"):
            line = line.rstrip()
            if line.startswith("!"):
                is_header = True
                for part in line[1:].split("!!"):
                    attrs, content = _split_attr(part)
                    hcells.append((_hlabel(content), _span(attrs, "colspan"), _span(attrs, "rowspan")))
            elif line.startswith("|") and not line.startswith(("|-", "|}", "|+", "|=")):
                _, content = _split_attr(line[1:])
                dcells.append(content.strip())
        if is_header and hcells:
            header_rows.append(hcells)
        elif dcells:
            data_rows.append(dcells)
    return _leaf_headers(header_rows), data_rows


def scrape_burn(idx):
    """Cooking/Burn level tables -> {itemId: {type:'BURN', stopLevel, gauntletStopLevel}}."""
    out = {}
    text = ex.wikitext_batch(["Cooking/Burn level"]).get("Cooking/Burn level", "")
    for tbl in _split_tables(text, "==Food types==", "==Obtaining burnt food"):
        leaves, data = _parse_table(tbl)
        if not leaves:
            continue
        lower = [lf.lower() for lf in leaves]

        def find(pred):
            return next((i for i, lf in enumerate(lower) if pred(lf)), None)

        base_i = find(lambda s: "range" in s)
        if base_i is None:
            base_i = find(lambda s: "fire" in s)
        gaunt_i = find(lambda s: "default" in s)  # "Cooking gauntlets Default" column
        if base_i is None:
            continue
        for cells in data:
            if not cells:
                continue
            m = _PLINK.search(cells[0])
            name = ex._clean(m.group(1)) if m else ex._clean(cells[0])
            item = idx.get(name, {}).get("id") if name else None
            if not item or base_i >= len(cells):
                continue
            gaunt = ex._int(cells[gaunt_i]) if gaunt_i is not None and gaunt_i < len(cells) else None
            stop = ex._int(cells[base_i])
            if stop is None:
                # A dash in the base column means the food can still burn at 99 on a
                # plain range (never safe). Model that as a stop level above 99 so
                # the food always carries a real burn chance, provided this is a
                # genuine food row (some other cooking column is numeric).
                if any(ex._int(c) is not None for c in cells[1:]):
                    stop = 100
                else:
                    continue
            out[str(item)] = {"type": "BURN", "stopLevel": stop, "gauntletStopLevel": gaunt}
    return out


def main(argv=None):
    ap = argparse.ArgumentParser(description="Scrape success params to success_params.json")
    ap.add_argument("-o", "--out", default=DEFAULT_OUT, help="output path (default: bundled resources)")
    args = ap.parse_args(argv)

    print("loading item name->id index from /mapping ...", file=sys.stderr)
    idx = ex.load_name_index()

    print("scraping {{Skilling success chart}} (LINEAR_INTERP) ...", file=sys.stderr)
    params = scrape_interp(idx)
    print(f"  {len(params)} LINEAR_INTERP models", file=sys.stderr)

    print("scraping Cooking/Burn level (BURN) ...", file=sys.stderr)
    burn = scrape_burn(idx)
    print(f"  {len(burn)} BURN models", file=sys.stderr)
    params.update(burn)  # BURN wins over interp on the same id

    with_gaunt = sum(1 for v in burn.values() if v.get("gauntletStopLevel") is not None)
    ordered = {k: params[k] for k in sorted(params, key=int)}
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"wrote {len(ordered)} success models to {args.out} "
          f"({with_gaunt} BURN entries carry a gauntlet stop level)", file=sys.stderr)


if __name__ == "__main__":
    main()
