#!/usr/bin/env python3
"""Extract ALL OSRS processing recipes by parsing the {{Recipe}} template from
page wikitext -- captures every declared output (not just output1), so new
content is picked up on re-run with no per-recipe curation.

Writes src/main/resources/recipes.json (an array of Recipe objects in the
normalized schema documented in processing-profit-plugin-features.md section 4).

Data source: the OSRS Wiki (CC BY-NC-SA 3.0 -- non-commercial, share-alike,
attribution required) and the OSRS Wiki Real-time Prices API. A descriptive
User-Agent is required by both. Recipes change rarely, so this runs at build /
release time and the result is bundled in the jar -- never fetched at runtime.

Usage:
    python3 tools/extract_recipes.py                 # full extraction
    python3 tools/extract_recipes.py --limit 200     # first 200 pages (quick test)
    python3 tools/extract_recipes.py -o out.json     # custom output path

No third-party dependencies -- uses only the Python standard library so CI does
not need a pip install step.
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

WIKI = "https://oldschool.runescape.wiki/api.php"
PRICES = "https://prices.runescape.wiki/api/v1/osrs/mapping"
UA = "processing-profit-plugin/0.1 (recipe extractor; https://github.com/Oveduumnakal/Processing-Profit-Plugin)"

DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "recipes.json",
)

WIKILINK = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]+)?\]\]")
_START = re.compile(r"\{\{\s*Recipe\s*(?=[|}])", re.I)  # boundary rejects {{Recipes}}, {{Recipe materials}}


def _get(url, params=None):
    """GET a URL (with optional query params) and return the decoded JSON body."""
    if params is not None:
        url = url + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    last_err = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception as e:  # noqa: BLE001 - retry any transient network/HTTP error
            last_err = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"request failed after retries: {url}") from last_err


def api(**params):
    """Call the MediaWiki API with format=json."""
    params.setdefault("format", "json")
    return _get(WIKI, params)


def recipe_pages(page_cap=None):
    """Every mainspace page transcluding Template:Recipe (paginated)."""
    titles, cont = [], {}
    while True:
        d = api(action="query", list="embeddedin", eititle="Template:Recipe",
                einamespace=0, eilimit="max", **cont)
        titles += [p["title"] for p in d["query"]["embeddedin"]]
        if page_cap is not None and len(titles) >= page_cap:
            return titles[:page_cap]
        cont = d.get("continue")
        if not cont:
            return titles
        time.sleep(0.5)


def wikitext_batch(titles):
    """Fetch current wikitext for up to 50 titles in one call: {title: text}."""
    d = api(action="query", prop="revisions", rvprop="content", rvslots="main",
            titles="|".join(titles))
    out = {}
    for p in d["query"]["pages"].values():
        rev = (p.get("revisions") or [{}])[0]
        text = (rev.get("slots", {}).get("main", {}) or {}).get("*") or rev.get("*", "")
        out[p["title"]] = text
    return out


def find_templates(text):
    """Yield the inner body of each brace-matched {{Recipe ...}} block."""
    last = 0
    for m in _START.finditer(text):
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


def split_params(body):
    """Split on top-level '|', ignoring nested {{}} and [[]] (e.g. {{GEP|..}} costs)."""
    parts, buf, td, ld, i = [], [], 0, 0, 0
    while i < len(body):
        two = body[i:i + 2]
        if two == "{{":
            td += 1
            buf.append(two)
            i += 2
        elif two == "}}":
            td -= 1
            buf.append(two)
            i += 2
        elif two == "[[":
            ld += 1
            buf.append(two)
            i += 2
        elif two == "]]":
            ld -= 1
            buf.append(two)
            i += 2
        elif body[i] == "|" and td == 0 and ld == 0:
            parts.append("".join(buf))
            buf = []
            i += 1
        else:
            buf.append(body[i])
            i += 1
    parts.append("".join(buf))
    return parts


def parse_recipe(body):
    """Turn a template body into a lowercased {param: value} dict."""
    kv = {}
    for part in split_params(body):
        if "=" in part:
            k, v = part.split("=", 1)
            kv[k.strip().lower()] = v.strip()
    return kv


def _clean(s):
    if not s:
        return None
    s = WIKILINK.sub(r"\1", s)
    s = re.sub(r"\{\{[^{}]*\}\}", "", s)  # strip [[links]] and {{GEP|..}} cost templates
    return s.strip() or None


def _int(v, d=None):
    try:
        return int(float(str(v).replace(",", "")))
    except (TypeError, ValueError):
        return d


def _float(v, d=None):
    try:
        return float(str(v).replace(",", ""))
    except (TypeError, ValueError):
        return d  # e.g. "Varies"


def _yes(v):
    s = (v or "").strip().lower()
    if s in ("yes", "true"):
        return True
    if s in ("no", "false"):
        return False
    return None


def _indices(kv, prefix):
    found = set()
    for k in kv:
        m = re.fullmatch(prefix + r"(\d+)", k)
        if m:
            found.add(int(m.group(1)))
    return sorted(found)


def slugify(skill, name):
    slug = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return f"{(skill or 'misc').lower()}:{slug}"


def normalize(title, kv, idx, now):
    """Map a parsed {{Recipe}} template to the normalized schema, or None to skip."""
    outs = []
    for i in _indices(kv, "output"):  # ALL declared outputs, not just output1
        name = _clean(kv.get(f"output{i}"))
        if name:
            outs.append({
                "itemId": idx.get(name, {}).get("id"),
                "name": name,
                "qty": _int(kv.get(f"output{i}quantity"), 1),
                "variant": _clean(kv.get(f"output{i}subtxt")),
                "successNote": kv.get(f"output{i}quantitynote"),
            })
    if not outs:
        return None  # gathering / minigame / bonus row -> skip
    inputs = []
    for i in _indices(kv, "mat"):
        name = _clean(kv.get(f"mat{i}"))
        if name:
            inputs.append({
                "itemId": idx.get(name, {}).get("id"),
                "name": name,
                "qty": _int(kv.get(f"mat{i}quantity"), 1),
                "consumed": True,
            })
    skills = []
    for i in _indices(kv, "skill"):
        name = _clean(kv.get(f"skill{i}"))
        if name:
            skills.append({
                "skill": name,
                "level": _int(kv.get(f"skill{i}lvl"), 1),
                "xp": _float(kv.get(f"skill{i}exp")),
                "boostable": _yes(kv.get(f"skill{i}boostable")),
            })
    tools = [{"itemId": idx.get(t, {}).get("id"), "name": t, "qty": 1, "consumed": False}
             for t in WIKILINK.findall(kv.get("tool", ""))]
    quest = _clean(kv.get("quest"))
    return {
        "recipeId": slugify(skills[0]["skill"] if skills else "misc", outs[0]["name"]),
        "outputs": outs,
        "inputs": inputs,
        "tools": tools,
        "skills": skills,
        "ticks": _int(kv.get("ticks")),
        "ticksNote": _clean(kv.get("ticksnote")),
        "members": bool(_yes(kv.get("members"))),
        "facility": _clean(kv.get("facility")),
        "requirements": {"quests": [quest] if quest else [], "misc": []},
        "success": {"type": "ALWAYS"},  # curated success params override this at merge time
        "onFailure": None,
        "source": {"page": title, "template": "Recipe", "revision": None, "extractedAt": now},
    }


def load_name_index():
    """name -> {id, limit, members} from the prices /mapping endpoint."""
    req = urllib.request.Request(PRICES, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        mapping = json.loads(r.read().decode("utf-8"))
    return {i["name"]: {"id": i["id"], "limit": i.get("limit"), "members": i.get("members")}
            for i in mapping}


def main(argv=None):
    ap = argparse.ArgumentParser(description="Extract OSRS {{Recipe}} data to recipes.json")
    ap.add_argument("-o", "--out", default=DEFAULT_OUT, help="output path (default: bundled resources)")
    ap.add_argument("--limit", type=int, default=None, help="cap number of pages (for quick testing)")
    args = ap.parse_args(argv)

    now = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    print("loading item name->id index from /mapping ...", file=sys.stderr)
    idx = load_name_index()
    print(f"  {len(idx)} items", file=sys.stderr)

    print("enumerating pages transcluding Template:Recipe ...", file=sys.stderr)
    pages = recipe_pages(args.limit)
    print(f"  {len(pages)} pages", file=sys.stderr)

    recipes, seen, unresolved = [], set(), 0
    for j in range(0, len(pages), 50):
        batch = pages[j:j + 50]
        for title, text in wikitext_batch(batch).items():
            for body in find_templates(text):  # a page may hold several {{Recipe}} blocks
                rec = normalize(title, parse_recipe(body), idx, now)
                if not rec:
                    continue
                if not rec["outputs"][0]["itemId"]:
                    unresolved += 1
                    continue
                key = (rec["recipeId"], tuple(sorted((m["name"], m["qty"]) for m in rec["inputs"])))
                if key not in seen:
                    seen.add(key)
                    recipes.append(rec)
        print(f"  parsed {min(j + 50, len(pages))}/{len(pages)} pages, "
              f"{len(recipes)} recipes so far", file=sys.stderr)
        time.sleep(0.5)  # be polite to the wiki

    recipes.sort(key=lambda r: r["recipeId"])
    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(recipes, f, indent=2, ensure_ascii=False)
        f.write("\n")

    multi = sum(1 for r in recipes if len(r["outputs"]) > 1)
    print(f"wrote {len(recipes)} recipes ({multi} multi-output) to {args.out}", file=sys.stderr)
    print(f"skipped {unresolved} recipes whose primary output id did not resolve", file=sys.stderr)


if __name__ == "__main__":
    main()
