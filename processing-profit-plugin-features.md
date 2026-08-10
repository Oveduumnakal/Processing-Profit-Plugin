# Processing Profit Tracker — Feature Specification

A standalone RuneLite plugin for **Old School RuneScape** that answers one question:

> *"Given what I have, what I can buy, or some mix of both — which final products are worth processing right now, and how much do I make?"*

It ingests base/intermediary materials (bought or gathered), maps them to the products they can be turned into via a skill, and ranks those products by profitability. Price and item data come **automatically from the OSRS Wiki Real-time Prices API**; recipe/production relationships are resolved from Wiki data (see [§4](#4-data-sources--architecture)).

Profitability is evaluated across **four dimensions** (all in scope):

1. Raw GP margin (the baseline)
2. **GP/hour** throughput
3. **XP rate & profit-per-XP** (skilling tradeoff)
4. **Skill-level gating** (only surface what you can actually do)
5. **Buy limits & GE volume** (only surface what you can actually source and sell)

---

## Table of Contents

1. [Design goals & scope](#1-design-goals--scope)
2. [Prior art / competitive review](#2-prior-art--competitive-review)
3. [The three sourcing modes (the core interaction)](#3-the-three-sourcing-modes)
4. [Data sources & architecture](#4-data-sources--architecture)
5. [Core profit engine features](#5-core-profit-engine-features)
6. [The four profit dimensions in detail](#6-the-four-profit-dimensions)
7. [Quality-of-life features](#7-quality-of-life-features)
8. [UI / UX design](#8-ui--ux-design)
9. [Configuration options](#9-configuration-options)
10. [OSRS-specific edge cases & gotchas](#10-osrs-specific-edge-cases--gotchas)
11. [Plugin Hub compliance notes](#11-plugin-hub-compliance-notes)
12. [Suggested phasing (MVP → v2 → stretch)](#12-suggested-phasing)
13. [Decisions & remaining open items](#13-decisions--remaining-open-items)

---

## 1. Design goals & scope

**In scope**

- Any *processing* conversion: `N base/intermediary items (+ tools/consumables) → 1..M product items` via a skill action (Smithing, Crafting, Cooking, Fletching, Herblore, Fletching, Fletching…, and non-combat conversions like decanting, cleaning herbs, spinning, tanning-adjacent flows, gem cutting, etc.).
- Multi-step chains (ore → bar → item), i.e. recursive recipe trees with "make vs. buy the intermediary" decisions.
- Both "what can I make **now**" (from bank/inventory) and "what **should** I make" (buy mats to order).
- **Two valuation lenses**: **GE market** (default) and **Ironman / no-GE** — value outputs by high alch (net nature rune) or shop, inputs by shop price or gather (see [§3](#3-the-three-sourcing-modes)).

**Explicitly out of scope**

- Any form of automation, input injection, or click assistance. This is an **informational/overlay** tool only.
- Combat/PvM loot valuation (that's a drop-tracker problem, not a processing one).
- Flip-finding / merching (adjacent but different; can interoperate, shouldn't merge).
- **Ledger / realized-profit / cost-basis tracking** — delegated to **Stockpile**. This plugin is forward-looking (what *should* I make); Stockpile tracks what you *did* make. Valuation here is always **current market price** (opportunity cost), no persistence.

**Non-negotiable UX principle:** the plugin *informs a decision*, it never makes or executes one.

---

## 2. Prior art / competitive review

### OSRS / RuneLite ecosystem

| Tool | What it does | What to borrow | Gap it leaves |
|---|---|---|---|
| **banked-materials-value** (Plugin Hub–style) | Reads your bank, matches items against a JSON recipe dataset, shows post-processing profit as a bank tooltip | The "value your bank as products" concept; bank-scan → recipe-match → GE-price flow | Manual JSON recipe data (author flags this as the main bottleneck); no GP/hr, XP, or buy-with-mode |
| **Passive profit trackers** (better-profit-tracker, mrhappyasthma, nofatigue lineage) | Watch inventory/bank deltas and accumulate realized GP with gold-drop animations | Live inventory delta tracking; GE-vs-alch-vs-shop value sourcing; session reset UX | Backward-looking only — tells you what you *made*, not what you *should* make |
| **07Flip / GE-Tracker / Flipping Utilities** | Flip finders: live margins, ROI, GE tax math, buy limits, per-item analysis; 07Flip also has decant / set-combine / repair calculators | GE-tax modeling, buy-limit awareness, ROI framing, sortable opportunity tables, watchlists | Flip-centric (buy low/sell high), not conversion/skilling profit |
| **Wiki skill calculators** (herb-run profit, high-alch tables, etc.) | Per-skill profit + XP projections | Level-gated projections, XP-per-action, profit *and* XP shown together | Web-based, single-skill, not "from your current bank" |
| **"Processing finished" notifier plugins** | Ping when repetitive Herblore/Fletching/Cooking finishes | Cheap, well-loved QoL hook to bolt on | Nothing to do with profitability |

**Lesson:** OSRS already has (a) passive "what I made" trackers and (b) flip finders, but **no strong forward-looking, bank-aware, cross-skill processing planner**. The `banked-materials-value` concept is the closest, and its own author names the recipe-data problem as the thing to solve. That's the wedge.

### Cross-game gold standard — TradeSkillMaster (WoW)

TSM is the most mature "what should I craft" system in any game. Directly transferable ideas:

- **Crafted Item Value − Crafting Cost = Profit**, surfaced *in tooltips* on every relevant item.
- **Value sources**: a material can be valued at market price, vendor price, or its own crafting cost — user-selectable. This maps cleanly to OSRS ("value my on-hand mats at GE price vs. what I actually paid").
- **Operations / restock rules**: min/max stock targets and a **minimum profit threshold** below which an item isn't queued.
- **Craft queue + shopping list ("gathering")**: pick products → get an exact material shopping list.
- **Groups**: bundle items that should be treated with the same rules.

### Other references

- **EVE Online industry tools** — normalize everything to **ISK/hour**, model build-vs-buy for every intermediate, and account for job time. The "everything reduces to profit-per-hour and every sub-component is a make-or-buy question" mindset is exactly right for OSRS chains.
- **Path of Exile crafting cost tools** — surface *expected* cost under probabilistic outcomes; relevant to OSRS actions with **success/failure rates** (burning food, gem-cutting fails at low level).

---

## 3. The three sourcing modes

This is the heart of the plugin and the primary UI selector. Every profit number is computed under one of three sourcing assumptions:

### A. On-hand ("what can I make right now?")
- Scan **bank + inventory** (and optionally GE collection box / looting bag).
- For each makeable product, compute how many you can produce from current stock and the total profit.
- Rank by total achievable profit and by profit/ea.
- *Valuation:* consumed on-hand mats are valued at **current market price** (opportunity cost). Actual-paid cost basis / realized profit isn't tracked here — that's Stockpile's job.

### B. Buy-to-order ("if I bought the mats, what's the margin?")
- Assume all inputs are purchased at GE.
- Pure per-product margin and ROI, independent of current stock — a classic crafting calculator.
- Respect **buy limits** when projecting how many you could realistically source per 4-hour window.

### C. Hybrid ("use what I have, buy the rest") — *the underserved, highest-value mode*
- Consume on-hand mats first (valued at market price in v1), then top up the shortfall via GE at market price.
- Produces a **blended profit** and an exact **shopping list** for the missing mats only.
- Answers the real player question: *"I have 3k feathers and 10k headless arrows — what's the cheapest path to a full inventory of a profitable product?"*

> **Make-vs-buy for intermediaries** applies across all three modes: for a chain like `ore → bar → item`, decide per intermediate whether to craft it from on-hand/bought inputs or just buy it finished, and pick whichever is cheaper. Surface both paths in the detail view.

### Valuation lens — GE market vs Ironman (no-GE)

Orthogonal to the sourcing modes above: **how** items are priced. Implemented behind the same `PriceLookup` abstraction, so the calc engine is unchanged — just a different price source.

- **GE market** (default) — inputs at GE buy price, outputs at GE sell price, GE tax + buy limits + volume all apply. The three sourcing modes above assume this lens.
- **Ironman / no-GE** — for accounts that can't use the GE:
  - **Outputs** valued by **high alch** (net of nature-rune cost) or shop sell value — whichever the player would realistically use. `/mapping` already carries `highalch`/`lowalch`/store `value`, so no extra data.
  - **Inputs** valued at **shop price** where shop-buyable, otherwise treated as **gathered** (0 gp, or an optional gather-time cost).
  - **Disables** the GE-only machinery: no GE tax, no buy limits, no volume gating, and Buy-to-order / hybrid GE top-up collapse to On-hand + gather.
  - **Ranking** shifts to **GP/hr and XP/hr** — the question becomes "is processing this worth the alch/shop value and the time," not "what's the market margin." This is the lens most ironmen actually want for processing decisions.

---

## 4. Data sources & architecture

### Prices & item metadata — solved, via the OSRS Wiki Real-time Prices API

| Endpoint | Provides |
|---|---|
| `/mapping` | item id ↔ name, icon, **members** flag, **GE buy limit**, high/low **alch** values |
| `/latest` | current instant-buy (`high`) and instant-sell (`low`) prices + timestamps |
| `/volumes` | trading volume (liquidity signal) |
| `/5m` `/1h` `/6h` `/24h` | interval-averaged prices (smoother, less spiky than `/latest`) |
| `/timeseries?id=&timestep=` | historical series for graphs / trend arrows |

**Critical price semantics (the trap you already hit once):**
`high` = **instant-buy** price (what you pay to buy *now*); `low` = **instant-sell** price (what you get selling *now*). When *buying mats* you either pay `high` (instant) or place a buy offer near `low` (patient). When *selling products* you either take `low` (instant) or place a sell offer near `high` (patient). The buy/sell price policy must be a config choice (see [§9](#9-configuration-options)), and averaged endpoints should be preferred over `/latest` for thinly-traded items to avoid single-outlier spikes.

**Wiki API etiquette (required):** descriptive `User-Agent`, multi-level client-side caching, and no sustained multi-query-per-second hammering. Batch item lookups; refresh on an interval, not per-tick.

### Recipes / production graph — the real work

The price API gives **prices, not recipes.** There is **no complete off-the-shelf recipe graph** to drop in:

- **osrsbox-db / osrsreboxed-db** (osrsbox is deprecated; `0xNeffarion/osrsreboxed-db` is the maintained fork) give per-item *metadata* — id, name, examine, high/low alch, buy limit, quest flag — but **no input→output relationships**. Great for the item side of the model; useless for recipes.
- **banked-materials-value** ships a *partial, hand-authored* recipe JSON. Usable as a seed, not as coverage.

**Source of truth = the OSRS Wiki `Template:Recipe` / `Infobox recipe`.** Every processing conversion on the wiki is encoded in this template. Its fields:

| Template param | Meaning |
|---|---|
| `skill1`, `skill1lvl`, `skill1exp`, `skill1boostable` (…`skill2`, `skill3`) | skill(s), level req, XP, boostable |
| `ticks`, `ticks2`, `ticksnote` | game ticks per action (→ GP/hr & XP/hr) |
| `members` | P2P/F2P |
| `tool`, `facility` | non-consumed tool / station (furnace, obelisk, etc.) |
| `mat1..matN` (+ `mat#qty`, `mat#cost`, `mat#img`, `mat#name`) | inputs |
| `output1..` (+ `output#quantity`, `output#quantitynote`) | outputs |
| `quest`, `misc1..` | requirement gating |

**Query it in realtime — use Bucket, not SMW.** The wiki migrated off Semantic MediaWiki to a new engine called **Bucket**; the old `api.php?action=ask` you used for drop tables is **hard-deprecated and being removed** — don't build on it. Recipe data lives in the bucket literally named **`recipe`**:

```
https://oldschool.runescape.wiki/api.php?action=bucket&query=bucket('recipe').select(<fields>).limit(500).offset(0).run()
```

Bucket syntax is chainable (`.select().where().limit().offset().run()`), all names **lowercase with underscores**. The `bucket:recipe` columns are confirmed: **`production_json`** (NOINDEX — the full recipe: output, materials+qty, skill/level/xp, ticks), `source_template`, `uses_material`, `uses_skill`, `uses_facility`, `uses_tool`, `is_boostable`, `is_members_only`. Because `production_json` is NOINDEX you can't `.where()` on it — filter with the indexed columns (e.g. `.where('uses_skill','Smithing')`) and read structure from the JSON. Probe its inner shape with `bucket('recipe').select('production_json').limit(3).run()`.

**Confirmed `production_json` shape** (from that probe):

```jsonc
{
  "output": { "name": "Bronze bar", "quantity": "1", "cost": 63,
              "subtxt": "Normal furnace", "image": "Bronze bar.png" },   // or "" for no-product rows
  "materials": [ { "name": "Copper ore", "quantity": "1", "image": "Copper ore.png" }, … ],
  "skills":    [ { "name": "Smithing", "level": "1", "experience": "6.2", "boostable": "" } ],
  "ticks": "5", "members": false,
  "tools": "[[Pickaxe]]", "facilities": "Furnace"   // wikilink-wrapped strings, not lists
}
```

Three gotchas the extractor handles: **(1)** `output` is `""` on gathering / minigame `{{Recipe}}` uses (e.g. "Gem rocks", "Agility Pyramid bonus") — those aren't processing recipes, so **skip any row without an `output` object**; **(2)** every numeric is a **string**, and `experience` can be non-numeric (`"Varies"`) — parse defensively (XP becomes `null`/unknown); **(3)** `tools`/`facilities` are **wikilink strings**, and `output.subtxt` ("Normal furnace") is a useful **variant** label to disambiguate multiple recipes that yield the same item.

**Two gotchas that shape the schema:**

1. **Bucket only carries the *first* output.** The wiki explicitly notes multiple outputs are supported in the template but only `output1` is passed to Bucket. For multi-output / byproduct recipes, special-case them or fall back to parsing page **wikitext** (`action=parse&prop=wikitext`, then parse the `{{Recipe|…}}` blocks) — keep a wikitext-parse fallback path.
2. **Success rates are free text, not numbers.** They live in `output#quantitynote` (e.g. *"50% success chance for iron bars without a ring of forging"*). You'll need a small parser + curated overrides to turn the common ones into level-scaled success curves.

**License caveat (real, plan for it):** OSRS Wiki content is **CC BY-NC-SA 3.0 — non-commercial, share-alike, attribution required** (Weird Gloop terms), and a descriptive `User-Agent` is expected. Fine for a free Plugin Hub plugin, but include an attribution note and keep it non-commercial.

**Recommended pipeline** (don't query recipes at runtime — they barely change and you'd hammer the wiki):

1. **Build-time extractor** parses `{{Recipe}}` wikitext (pages enumerated via `embeddedin`), capturing **all declared outputs**, normalizes into your schema, resolves item names→ids, writes a **versioned `recipes.json`** bundled with the plugin. Refresh per release.
2. **Item metadata** (buy limits, alch, ids) merged from the prices `/mapping` endpoint or osrsreboxed-db.
3. **Live prices at runtime** from the `/5m` endpoint (5-minute averages: `avgHighPrice`→buy, `avgLowPrice`→sell), refreshed every **60 s**; `highPriceVolume`/`lowPriceVolume` in the same payload feed the liquidity dimension for free. `/5m` recomputes server-side every 5 min, so cache between refreshes and fall back to `/1h` (or flag stale) when a window's average is null.
4. **User override layer**: optionally load a user `recipes-overrides.json` so power users can add/fix recipes without a release.
5. **Distribution**: CI **bundles a snapshot** of `recipes.json` + `success_params.json` in the jar; the catalog updates with each plugin release. (No runtime re-pull — recipes change rarely, and a hosted-fetch path added a dependency and a failure mode for little gain.)

### Normalized recipe schema

Designed up front for multi-skill, multi-output, byproducts, non-consumed tools, per-action consumables, level-scaled success, and quest/diary gating (retrofitting these hurts):

```jsonc
// recipes.json — array of Recipe
{
  "recipeId": "smithing:cannonball",     // stable synthetic key (skill:slug)
  "outputs": [
    { "itemId": 2, "name": "Cannonball", "qty": 4, "variant": null, "successNote": null }
  ],
  "inputs": [
    { "itemId": 2353, "name": "Steel bar", "qty": 1, "consumed": true }
  ],
  "tools": [
    { "itemId": 4, "name": "Ammo mould", "consumed": false }
  ],
  "skills": [
    { "skill": "Smithing", "level": 35, "xp": 25.6, "boostable": false }
  ],
  "ticks": 10,                 // primary action time; null if unknown
  "ticksNote": null,
  "members": true,
  "facility": "Furnace",       // tool/station free text
  "requirements": { "quests": ["Dwarf Cannon"], "misc": [] },
  "success": { "type": "ALWAYS" },   // or LINEAR_INTERP {low,high} / BURN {stopLevel,baseChance} / FIXED {fixedRate}
  "onFailure": null,                 // e.g. {"itemId": 1633, "name": "Crushed gem", "qty": 1} for gem cuts
  "source": {                  // provenance for debugging + attribution
    "page": "Cannonball", "revision": 15290000, "extractedAt": "2026-08-09T00:00:00Z"
  }
}
```

### Scaffolding — build-time extractor (Python, `{{Recipe}}` wikitext parser)

Run in CI / pre-release. Enumerates every page transcluding `Template:Recipe` (via `embeddedin`), batch-fetches wikitext, and parses the `{{Recipe}}` template directly — so it captures **all declared outputs**, and new content is picked up automatically on re-run with no per-recipe curation. Resolves names→ids via `/mapping`, writes `recipes.json`. (Verified against real `{{Recipe}}` wikitext including nested `{{GEP|…}}` cost templates and multi-output blocks.)

```python
#!/usr/bin/env python3
"""Extract ALL OSRS processing recipes by parsing the {{Recipe}} template from
page wikitext — captures every declared output (not just output1), so new
content is picked up on re-run with no per-recipe curation. Writes recipes.json."""
import json, time, re, requests

WIKI     = "https://oldschool.runescape.wiki/api.php"
PRICES   = "https://prices.runescape.wiki/api/v1/osrs/mapping"
UA       = "processing-profit-plugin/0.1 (recipe extractor; contact: <your-contact>)"
S = requests.Session(); S.headers.update({"User-Agent": UA})
WIKILINK = re.compile(r"\[\[([^\]|]+)(?:\|[^\]]+)?\]\]")
_START   = re.compile(r"\{\{\s*Recipe\s*(?=[|}])", re.I)   # boundary: rejects {{Recipes}}, {{Recipe materials}}

def api(**params):
    params.setdefault("format", "json")
    r = S.get(WIKI, params=params, timeout=30); r.raise_for_status()
    return r.json()

def recipe_pages():
    """Every mainspace page transcluding Template:Recipe (paginated)."""
    titles, cont = [], {}
    while True:
        d = api(action="query", list="embeddedin", eititle="Template:Recipe",
                einamespace=0, eilimit="max", **cont)
        titles += [p["title"] for p in d["query"]["embeddedin"]]
        cont = d.get("continue")
        if not cont: return titles
        time.sleep(0.5)

def wikitext_batch(titles):                        # up to 50 titles per call
    d = api(action="query", prop="revisions", rvprop="content", rvslots="main",
            titles="|".join(titles))
    out = {}
    for p in d["query"]["pages"].values():
        rev = (p.get("revisions") or [{}])[0]
        out[p["title"]] = (rev.get("slots", {}).get("main", {}) or {}).get("*") or rev.get("*", "")
    return out

def find_templates(text):
    """Yield the inner body of each brace-matched {{Recipe ...}} block."""
    last = 0
    for m in _START.finditer(text):
        if m.start() < last: continue
        s, k, depth, n = m.start(), m.start(), 0, len(text)
        while k < n:
            if text.startswith("{{", k): depth += 1; k += 2
            elif text.startswith("}}", k):
                depth -= 1; k += 2
                if depth == 0: break
            else: k += 1
        last = k
        yield text[s+2:k-2]

def split_params(body):
    """Split on top-level '|', ignoring nested {{}} and [[]] (e.g. {{GEP|..}} costs)."""
    parts, buf, td, ld, i = [], [], 0, 0, 0
    while i < len(body):
        two = body[i:i+2]
        if two == "{{": td += 1; buf.append(two); i += 2
        elif two == "}}": td -= 1; buf.append(two); i += 2
        elif two == "[[": ld += 1; buf.append(two); i += 2
        elif two == "]]": ld -= 1; buf.append(two); i += 2
        elif body[i] == "|" and td == 0 and ld == 0:
            parts.append("".join(buf)); buf = []; i += 1
        else:
            buf.append(body[i]); i += 1
    parts.append("".join(buf))
    return parts

def parse_recipe(body):
    kv = {}
    for part in split_params(body):
        if "=" in part:
            k, v = part.split("=", 1); kv[k.strip().lower()] = v.strip()
    return kv

def _clean(s):
    if not s: return None
    s = WIKILINK.sub(r"\1", s); s = re.sub(r"\{\{[^{}]*\}\}", "", s)   # strip links + {{GEP|..}}
    return s.strip() or None
def _int(v, d=None):
    try: return int(float(str(v).replace(",", "")))
    except (TypeError, ValueError): return d
def _float(v, d=None):
    try: return float(str(v).replace(",", ""))
    except (TypeError, ValueError): return d       # e.g. "Varies"
def _yes(v):
    s = (v or "").strip().lower()
    return True if s in ("yes", "true") else False if s in ("no", "false") else None
def _indices(kv, prefix):
    return sorted({int(m.group(1)) for k in kv for m in [re.fullmatch(prefix + r"(\d+)", k)] if m})
def slugify(skill, name):
    return f"{(skill or 'misc').lower()}:{re.sub(r'[^a-z0-9]+', '-', name.lower()).strip('-')}"

def normalize(title, kv, idx):
    outs = []
    for i in _indices(kv, "output"):               # ALL declared outputs, not just output1
        name = _clean(kv.get(f"output{i}"))
        if name:
            outs.append({"itemId": idx.get(name, {}).get("id"), "name": name,
                         "qty": _int(kv.get(f"output{i}quantity"), 1),
                         "variant": _clean(kv.get(f"output{i}subtxt")),
                         "successNote": kv.get(f"output{i}quantitynote")})
    if not outs: return None                        # gathering/bonus row -> skip
    inputs = []
    for i in _indices(kv, "mat"):
        name = _clean(kv.get(f"mat{i}"))
        if name:
            inputs.append({"itemId": idx.get(name, {}).get("id"), "name": name,
                           "qty": _int(kv.get(f"mat{i}quantity"), 1), "consumed": True})
    skills = []
    for i in _indices(kv, "skill"):
        name = _clean(kv.get(f"skill{i}"))
        if name:
            skills.append({"skill": name, "level": _int(kv.get(f"skill{i}lvl"), 1),
                           "xp": _float(kv.get(f"skill{i}exp")),
                           "boostable": _yes(kv.get(f"skill{i}boostable"))})
    tools = [{"itemId": idx.get(t, {}).get("id"), "name": t, "qty": 1, "consumed": False}
             for t in WIKILINK.findall(kv.get("tool", ""))]
    return {
        "recipeId": slugify(skills[0]["skill"] if skills else "misc", outs[0]["name"]),
        "outputs": outs, "inputs": inputs, "tools": tools, "skills": skills,
        "ticks": _int(kv.get("ticks")),
        "members": bool(_yes(kv.get("members"))),
        "facility": _clean(kv.get("facility")),
        "requirements": {"quests": [q for q in [_clean(kv.get("quest"))] if q], "misc": []},
        "success": {"type": "ALWAYS"},              # curated success/failure table overrides these
        "onFailure": None,
        "source": {"page": title, "template": "Recipe", "revision": None,
                   "extractedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())},
    }

if __name__ == "__main__":
    idx = {i["name"]: {"id": i["id"], "limit": i.get("limit"), "members": i.get("members")}
           for i in S.get(PRICES, timeout=30).json()}
    pages = recipe_pages()
    recipes, seen = [], set()
    for j in range(0, len(pages), 50):
        for title, text in wikitext_batch(pages[j:j+50]).items():
            for body in find_templates(text):       # a page may hold several {{Recipe}} blocks
                rec = normalize(title, parse_recipe(body), idx)
                if not rec or not rec["outputs"][0]["itemId"]:
                    continue
                key = (rec["recipeId"], tuple(sorted((m["name"], m["qty"]) for m in rec["inputs"])))
                if key not in seen:
                    seen.add(key); recipes.append(rec)
        time.sleep(0.5)                              # be polite to the wiki
    json.dump(recipes, open("recipes.json", "w"), indent=2)
    print(f"wrote {len(recipes)} recipes from {len(pages)} pages")
```

> The Bucket `recipe` table (confirmed columns above) stays handy as a fast single-query source for ad-hoc filtered lookups and as a cross-check, but the wikitext parser is **authoritative for extraction** — Bucket only carries `output1`, so it can't see byproducts or failure outputs. Note failure items (burnt food, crushed gems) generally aren't *declared* recipe outputs; those come from the scraped success params plus a small failure-item list (§6), not from the parser.

### Scaffolding — success-rate scraper (Python)

Two wiki sources, both scrapeable, feeding `success_params.json` (merged into recipes at build time). Source A **reuses the `{{Recipe}}` parser helpers verbatim** — just point them at a different template.

```python
#!/usr/bin/env python3
"""Scrape level-based success markers -> success_params.json.
 A) {{Skilling success chart}} template params (low/high/req) -> LINEAR_INTERP  (gem-cut fails, etc.)
 B) Cooking/Burn level table (stop-burn levels)              -> BURN
Reuses api(), wikitext_batch(), split_params(), parse_recipe(), _int, _clean, _indices from the extractor."""
import json, re, time

_CHART = re.compile(r"\{\{\s*Skilling success chart\s*(?=[|}])", re.I)

def _templates(text, start_re):                    # same brace-matcher, parameterised template name
    last = 0
    for m in start_re.finditer(text):
        if m.start() < last: continue
        s, k, depth, n = m.start(), m.start(), 0, len(text)
        while k < n:
            if text.startswith("{{", k): depth += 1; k += 2
            elif text.startswith("}}", k):
                depth -= 1; k += 2
                if depth == 0: break
            else: k += 1
        last = k
        yield text[s+2:k-2]

def scrape_interp(idx):
    """Every page transcluding Template:Skilling success chart -> {itemId: {type,low,high,req}}."""
    out, cont = {}, {}
    while True:
        d = api(action="query", list="embeddedin", eititle="Template:Skilling success chart",
                einamespace=0, eilimit="max", **cont)
        titles = [p["title"] for p in d["query"]["embeddedin"]]
        for j in range(0, len(titles), 50):
            for _, text in wikitext_batch(titles[j:j+50]).items():
                for body in _templates(text, _CHART):
                    kv = parse_recipe(body)             # key=value parser, unchanged
                    for i in _indices(kv, "label"):
                        name = _clean(kv.get(f"label{i}")) or _clean(kv.get(f"image{i}", "").replace(".png", ""))
                        item = idx.get(name, {}).get("id")
                        if item and kv.get(f"low{i}") and kv.get(f"high{i}"):
                            out[item] = {"type": "LINEAR_INTERP",
                                         "low": _int(kv[f"low{i}"]), "high": _int(kv[f"high{i}"]),
                                         "req": _int(kv.get(f"req{i}"), 1)}
            time.sleep(0.5)
        cont = d.get("continue")
        if not cont:
            return out

def scrape_burn():
    """Cooking/Burn level -> {itemId: {type:'BURN', stopLevel, gauntletStopLevel}}.
    Standard wikitable: food name column + stop-level columns for base (fire/range) AND
    gauntlets (per fish, e.g. shark 99->94, lobster 74->64). Capture BOTH the base and the
    gauntlet stop level — gauntlets are a per-fish stop-level swap, NOT a flat level bonus.
    reqLevel is filled from the recipe at merge; Hosidius is applied at runtime as ADD_CHANCE."""
    html = api(action="parse", page="Cooking/Burn level", prop="text")["parse"]["text"]["*"]
    ...  # BeautifulSoup over <table class="wikitable">; emit {itemId:{"type":"BURN","stopLevel":N,"gauntletStopLevel":M}}
    return {}

if __name__ == "__main__":
    idx = load_name_index()                            # from the recipe extractor
    params = {}
    params.update(scrape_interp(idx))
    params.update(scrape_burn())                        # BURN entries; recipe.success set at merge
    json.dump(params, open("success_params.json", "w"), indent=2)
    print(f"wrote {len(params)} success models")
```

At the recipe-merge step, `success_params[outputItemId]` (or by input, for gems) sets each recipe's `success` model; anything unmatched stays `ALWAYS` (100%). Failure outputs (crushed gem, burnt food) are the small companion list keyed the same way. **Verified against live data**: opal `low=128` → `129/256 = 0.50390625` at level 1, matching the wiki's rendered curve exactly.

### Success modifiers (items / areas / diaries)

Base curves aren't the whole story — items, areas, and diary tiers shift success or yield. These are a **small, stable curated registry** (`success_modifiers.json`; the wiki has no uniform machine-readable source for them), each with a shape, a value, and an applicability gate. Toggled by config (or auto-detected from equipment/inventory/varbits — see below). Four shapes cover everything:

| Modifier | Shape | Value | Applies to |
|---|---|---|---|
| **Jeweller's chisel** (Wyrmscraig) | `ADD_CHANCE` | **+0.20** (percentage points) | cutting opal / jade / red topaz |
| **Jeweller's chisel** (double-gem) | `YIELD_MULT` | **×1.10** | cutting any gem |
| **Cooking gauntlets** | `STOP_LEVEL` | swaps in a **per-fish** gauntlet stop level (e.g. shark 99→94, lobster 74→64) | lobster, swordfish, monkfish, shark, anglerfish |
| **Hosidius range** (easy Kourend) | `ADD_CHANCE` | **+0.05** | any cooking (burn) |
| **Hosidius range** (elite Kourend) | `ADD_CHANCE` | **+0.10** | any cooking (burn) |
| **Cooking cape** | `FORCE_SUCCESS` | — | any cooking |

```jsonc
// success_modifiers.json
[
  { "id": "jewellers_chisel",   "effect": "ADD_CHANCE",      "value": 0.20,
    "skill": "Crafting", "items": [1625, 1627, 1629] },     // uncut opal / jade / red topaz
  { "id": "jewellers_double",   "effect": "YIELD_MULT",      "value": 1.10, "skill": "Crafting", "items": [] },
  { "id": "cooking_gauntlets",  "effect": "STOP_LEVEL",      "value": 0,
    "skill": "Cooking",  "items": [377, 371, 7944, 383, 13439] },  // raw lobster/sword/monk/shark/angler
  { "id": "hosidius_elite",     "effect": "ADD_CHANCE",      "value": 0.10, "skill": "Cooking",  "items": [] },
  { "id": "cooking_cape",       "effect": "FORCE_SUCCESS",   "value": 0,    "skill": "Cooking",  "items": [] }
]
```

`Success.chance()` composes them in the right order — for BURN recipes a `STOP_LEVEL` modifier (gauntlets) swaps in the recipe's scraped per-fish gauntlet stop level, then `ADD_CHANCE` (Hosidius/jeweller's) adds, then `FORCE_SUCCESS` (cape) overrides, clamped to [0, 1] — and `Success.yieldMult()` scales the good-output quantity. **Auto-detection** is a nice enhancement: RuneLite can read equipped gauntlets/cape, a jeweller's chisel in the inventory, and Kourend diary varbits, flipping the right toggles automatically; config checkboxes are the baseline fallback. Because this registry is tiny and rarely changes, it's the one hand-maintained piece — and new items like the jeweller's chisel (which post-dates much of the ecosystem) are a one-line add.


```java
// --- model ---
public record ItemStack(int itemId, String name, int qty, boolean consumed) {}
public record RecipeOutput(int itemId, String name, int qty, String variant, String successNote) {}
public record SkillReq(String skill, int level, Double xp, Boolean boostable) {}  // xp null == "Varies"/unknown
public record Requirements(List<String> quests, List<String> misc) {}

// Per-level success probability. Params are scraped from the wiki:
//   LINEAR_INTERP low/high            <- {{Skilling success chart}} (markers out of 255)
//   BURN stopLevel/gauntletStopLevel  <- Cooking/Burn level table (both columns; req from the recipe)
public enum SuccessType { ALWAYS, LINEAR_INTERP, BURN, FIXED }
public record SuccessModel(SuccessType type, Integer low, Integer high,
                           Integer stopLevel, Integer gauntletStopLevel, Integer reqLevel, Double fixedRate) {
    public double chance(int level) { return chance(level, null); }
    public double chance(int level, Integer effStop) {         // effStop lets a modifier swap the stop level
        return switch (type) {
            case ALWAYS -> 1.0;
            case FIXED  -> fixedRate;
            case LINEAR_INTERP -> {                            // p = (floor((low·(99−L)+high·(L−1))/98)+1)/256
                double c = (low * (99 - level) + high * (level - 1)) / 98.0;
                yield Math.min(1.0, Math.max(0.0, (Math.floor(c) + 1) / 256.0));
            }
            case BURN -> {                                     // burn = (stop−L)/(stop−req+1); success = 1−burn
                int stop = effStop != null ? effStop : stopLevel;
                if (level >= stop) yield 1.0;
                double burn = (double) (stop - level) / (stop - reqLevel + 1);
                yield Math.min(1.0, Math.max(0.0, 1.0 - burn));
            }
        };
    }
    public static SuccessModel always() {
        return new SuccessModel(SuccessType.ALWAYS, null, null, null, null, null, null);
    }
}
// What a failed attempt yields (materials are consumed either way). null item == nothing.
public record FailureOutcome(Integer itemId, String name, int qty) {}

// Items/areas/diaries that change success or yield. Small curated registry (see success_modifiers.json).
public enum ModifierEffect { ADD_CHANCE, STOP_LEVEL, FORCE_SUCCESS, YIELD_MULT }
public record SuccessModifier(
        String id,                     // "jewellers_chisel", "cooking_gauntlets", "hosidius_elite", "cooking_cape"
        ModifierEffect effect,
        double value,                  // +0.20 chance / ×1.10 yield  (unused for STOP_LEVEL & FORCE_SUCCESS)
        String skill,                  // gate: only applies to this skill (null = any)
        Set<Integer> items) {          // gate: only these output/input item ids (empty = all in skill)
    boolean applies(Recipe r) {
        if (skill != null && !skill.equalsIgnoreCase(r.primarySkill().skill())) return false;
        if (items == null || items.isEmpty()) return true;
        return r.outputs().stream().anyMatch(o -> items.contains(o.itemId()))
            || r.inputs().stream().anyMatch(i -> items.contains(i.itemId()));
    }
}

// Composes the base curve with the player's active modifiers (from config / auto-detect).
public final class Success {
    public static double chance(Recipe r, int level, List<SuccessModifier> active) {
        boolean force = false, gauntlets = false;
        double add = 0;
        for (SuccessModifier m : active) {
            if (!m.applies(r)) continue;
            switch (m.effect()) {
                case STOP_LEVEL    -> gauntlets = true;           // cooking gauntlets: swap in gauntlet stop level
                case ADD_CHANCE    -> add += m.value();           // Hosidius +0.05/+0.10, jeweller's +0.20
                case FORCE_SUCCESS -> force = true;               // cooking cape
                case YIELD_MULT    -> { /* handled in yieldMult */ }
            }
        }
        Integer effStop = gauntlets ? r.success().gauntletStopLevel() : null;   // null -> model's own stopLevel
        double p = force ? 1.0 : r.success().chance(level, effStop) + add;
        return Math.min(1.0, Math.max(0.0, p));
    }
    public static double yieldMult(Recipe r, List<SuccessModifier> active) {
        double y = 1.0;
        for (SuccessModifier m : active)
            if (m.effect() == ModifierEffect.YIELD_MULT && m.applies(r)) y *= m.value();
        return y;                                                  // jeweller's double-gem ≈ ×1.10
    }
}

public record Recipe(
        String recipeId, List<RecipeOutput> outputs, List<ItemStack> inputs,
        List<ItemStack> tools, List<SkillReq> skills, Integer ticks,
        boolean members, String facility, Requirements requirements,
        SuccessModel success, FailureOutcome onFailure) {

    public int primaryOutputId() { return outputs.get(0).itemId(); }
    public SkillReq primarySkill() { return skills.get(0); }
}
```

```java
// --- repository: load bundled recipes.json (+ optional user overrides) ---
public class RecipeRepository {
    private final Map<Integer, List<Recipe>> byOutput = new HashMap<>();
    private final Map<String,  List<Recipe>> bySkill  = new HashMap<>();
    private List<Recipe> all = List.of();
    private static final Gson GSON = new GsonBuilder().create();

    public void load() {
        try (Reader r = new InputStreamReader(
                getClass().getResourceAsStream("/recipes.json"), StandardCharsets.UTF_8)) {
            all = List.of(GSON.fromJson(r, Recipe[].class));
        } catch (Exception e) {
            log.error("failed to load recipes.json", e);
            all = List.of();
        }
        // TODO: merge a user override file from the RuneLite profile dir if present
        index();
    }

    private void index() {
        byOutput.clear(); bySkill.clear();
        for (Recipe rec : all) {
            byOutput.computeIfAbsent(rec.primaryOutputId(), k -> new ArrayList<>()).add(rec);
            bySkill.computeIfAbsent(rec.primarySkill().skill(), k -> new ArrayList<>()).add(rec);
        }
    }

    public List<Recipe> all() { return all; }
    public List<Recipe> forOutput(int itemId) { return byOutput.getOrDefault(itemId, List.of()); }
    public List<Recipe> forSkill(String skill) { return bySkill.getOrDefault(skill, List.of()); }
}
```

```java
// --- profit calc: PURE + testable (no Swing, no network).
//     This is where the inverted-spread bug class gets killed by unit tests. ---
public record ProfitResult(
        Recipe recipe, long inputCost, long expectedOutputPreTax, long expectedTax,
        long profitEach, double roi, long gpPerHour, double xpPerHour,
        double profitPerXp, double successChance, boolean stalePrices) {}

public interface PriceLookup {
    /** /5m avgHighPrice — instant-buy side; what you pay to buy   */ long buyPrice(int itemId);
    /** /5m avgLowPrice  — instant-sell side; what you get selling */ long sellPrice(int itemId);
    /** min(highPriceVolume, lowPriceVolume) from the same /5m row */ long volume(int itemId);
    boolean isStale(int itemId);
    // Two impls: GePriceLookup (/5m) and IronmanValuation (buyPrice = shop/gather, sellPrice = alch/shop).
}

public final class ProfitCalculator {
    // playerLevel = live level in the recipe's primary skill; active = modifiers on now (config/auto-detect)
    public ProfitResult evaluate(Recipe rec, PriceLookup prices, PriceConfig cfg,
                                 int playerLevel, List<SuccessModifier> active) {
        long inputCost = rec.inputs().stream()
                .filter(ItemStack::consumed)
                .mapToLong(i -> prices.buyPrice(i.itemId()) * (long) i.qty())
                .sum();                                   // + per-action consumable/charge costs

        double p     = Success.chance(rec, playerLevel, active);   // base curve + effective-level/+chance/force
        double yMult = Success.yieldMult(rec, active);             // e.g. jeweller's double-gem ×1.10

        // success branch: the intended product (post-tax), yield-adjusted
        RecipeOutput out = rec.outputs().get(0);
        long goodGross = Math.round(prices.sellPrice(out.itemId()) * (long) out.qty() * yMult);
        long goodTax   = cfg.applyGeTax(out.itemId(), goodGross);
        long goodNet   = goodGross - goodTax;

        // failure branch: crushed gem / burnt food / nothing — materials still consumed
        long failNet = 0;
        if (rec.onFailure() != null && rec.onFailure().itemId() != null) {
            long g = prices.sellPrice(rec.onFailure().itemId()) * (long) rec.onFailure().qty();
            failNet = g - cfg.applyGeTax(rec.onFailure().itemId(), g);
        }

        long expectedOutput = Math.round(p * goodGross + (1 - p) * 0);      // pre-tax, success side
        long expectedTax    = Math.round(p * goodTax);
        long expectedNet    = Math.round(p * goodNet + (1 - p) * failNet);
        long profitEach     = expectedNet - inputCost;

        double roi       = inputCost == 0 ? 0 : (double) profitEach / inputCost;
        double perHour   = cfg.actionsPerHour(rec.ticks());   // ticks × efficiency %; NaN if ticks null
        long gpPerHour   = Double.isNaN(perHour) ? 0 : Math.round(profitEach * perHour);
        Double xpBoxed   = rec.primarySkill().xp();
        double xp        = xpBoxed == null ? 0 : xpBoxed;
        double xpPerHour = Double.isNaN(perHour) ? 0 : xp * perHour;
        double profitPerXp = xp == 0 ? 0 : profitEach / xp;
        boolean stale = prices.isStale(out.itemId())
                || rec.inputs().stream().anyMatch(i -> prices.isStale(i.itemId()));
        return new ProfitResult(rec, inputCost, expectedOutput, expectedTax, profitEach,
                roi, gpPerHour, xpPerHour, profitPerXp, p, stale);
    }
}
```

### Client architecture (RuneLite)

- `PluginPanel` (Swing) for the main sidebar UI; you've built config-panel `JList`/drag-drop UIs before, so the interaction model is familiar.
- A `@Subscribe` layer on `ItemContainerChanged` (inventory/bank) to keep the on-hand snapshot live.
- A price client (your `WikiRealtimePriceClient` pattern) with caching + freshness timestamps.
- `RecipeRepository` loading the bundled `recipes.json` (+ optional user overrides).
- `ProfitCalculator` kept **pure and network-free** so the math is unit-tested in isolation (guards against the inverted-spread class of bug).

---

## 5. Core profit engine features

- **Full conversion catalog**: every known recipe, filterable/sortable, computed under the active sourcing mode.
- **Recursive recipe trees**: expand any product to see the full chain; auto-pick cheaper make-or-buy per intermediate, with the alternative shown.
- **Per-product cost breakdown**: line-item each input (qty × unit price), tools/consumables, GE tax on the sale, and net profit/ea.
- **GE tax modeling**: apply the 2% sell tax (with the per-item cap and the low-value exemption threshold) to product sale price; expose pre-tax vs post-tax.
- **Level-based success math (all recipes)**: every conversion carries a `SuccessModel` evaluated at the player's live level (standard interpolation, cooking burn, flat, or fixed), then adjusted by any active **modifiers** — items, areas, and diary tiers (jeweller's chisel, cooking gauntlets, Hosidius range, cooking cape) that shift the chance, the effective level, or the yield. Expected profit = `p·(good output × yield) + (1−p)·(failure output) − input cost`, since materials are consumed on both outcomes. The `low`/`high` markers and burn-stop levels are **scraped from the wiki**; the modifier registry and failure outputs are small curated lists; recipes with no model default to 100%.
- **Byproduct / multi-output crediting**: value all outputs of an action, not just the "main" one.
- **Break-even material price**: for each recipe, the max you can pay per key input and still profit — powerful for setting GE buy offers.
- **Batch/"make-all" projection**: profit for a full inventory of actions, a full bank's worth, or an arbitrary target quantity.
- **Confidence / staleness flags**: mark results using stale, null, or low-volume prices so a "profit" isn't taken at face value.

---

## 6. The four profit dimensions

Every recipe row can be sorted/filtered by each of these; the detail view shows all at once.

### 6.1 Raw GP margin (baseline)
`profit/ea = Σ(output value, post-tax) − Σ(input cost) − per-action consumable cost`. Also show **ROI %** (`profit / input cost`) so cheap high-turnover conversions aren't buried under high-GP-but-low-% ones.

### 6.2 GP/hour throughput
`GP/hr = profit/action × actions/hr`, where `actions/hr` derives from the recipe's action time (`ticks`) and a user-tunable **efficiency %** (e.g. 80–95%) for banking/travel overhead, since raw tick math overstates real rates. Label it *theoretical throughput* so it isn't mistaken for a measured rate. **When `ticks` is missing** (some recipes have none), show **n/a** rather than guessing — the row still ranks fine on profit/ea and XP. Curated per-method overrides can fill in the top earners. This is the number most players actually optimize.

### 6.3 XP rate & profit-per-XP
Show **XP/hr** and **profit-per-XP** (often negative — i.e., GP *cost* per XP for training methods). This reframes the tool as *"cheapest way to train skill S"* as well as *"best money maker,"* and lets players weigh a slightly-less-profitable method that gives far better XP. Include a **profit-vs-XP scatter** so the efficient frontier is visible at a glance.

### 6.4 Skill-level gating
Pull the player's live levels; by default **hide recipes above current level** (with a toggle to show locked ones, greyed, with the level delta). Respect **quest/diary requirements** too. Success rates are **always level-scaled** (see the success-math feature in §5), so early-game profit reflects real (lower) yields rather than assuming 100%.

### 6.5 Buy limits & GE volume
- **Buy limits**: from `/mapping`; cap projected sourcing per 4-hour window and warn when a "money maker" is limit-throttled (great margin, trivial volume you can buy).
- **Volume/liquidity**: from `/volumes`; flag or filter out products/inputs you can't realistically sell or buy at the quoted price. A 10k-profit conversion on an item trading 12/day is a trap, and the tool should say so.

---

## 7. Quality-of-life features

- **Watchlist / favorites** — pin recipes you care about to the top.
- **Shopping-list generator (TSM-style)** — pick product(s) + target qty → exact mat list with quantities, per-item and total cost, buy-limit-aware split across windows, and (hybrid mode) only the shortfall.
- **"Can I make this?" checker** — type/click a product, get max makeable now + what's missing.
- **Min-profit / min-GP-hr thresholds** — hide anything below your bar (TSM's min-profit operation).
- **Bank overlay** — hover the bank (or a toggle) to see top makeable products and total achievable profit, à la `banked-materials-value`.
- **GE-screen helper (informational only)** — when a mat is selected on the GE, show the break-even buy price and how many units you still need. *No auto-fill of prices/quantities* (compliance).
- **Processing-finished notification** — reuse the well-loved "repetitive action done" ping so users don't over/under-craft a batch.
- **Presets / profiles** — save filter+sort+mode combos ("F2P Smithing money", "cheap Herblore XP").
- **Import/export config** — share presets and custom recipe overrides via string/JSON (TSM import/export is beloved for this).
- **Price-source toggle per session** — instant vs. patient, `/latest` vs. `/1h` average.
- **Ironman / no-GE valuation** — a valuation lens (see §3) for accounts that can't use the GE: outputs valued by high alch (net nature rune) or shop, inputs by shop price or gather, ranked by GP/hr + XP/hr. Same `PriceLookup` abstraction, alternate price source.

---

## 8. UI / UX design

> **Visual reference:** an interactive mockup of the panel lives in **`ui-mockup.html`** (RuneLite dark theme, sample data). The wireframe below is the durable in-doc reference; the HTML shows the real styling, color coding, density toggle, and the expandable success breakdown.

```
┌──────────────────────────────────────────┐
│ ⚒  Processing Profit                    ⚙ │  header
├──────────────────────────────────────────┤
│ Sourcing:  [On-hand] Buy-order  Hybrid     │  sourcing mode
│ Valuation: [GE market]  Ironman (no-GE)    │  valuation lens
├──────────────────────────────────────────┤
│ Browse | On-hand | Watchlist | Shopping    │  tabs
├──────────────────────────────────────────┤
│ Gauntlets ✓  Jeweller's ✓  Hosidius —  auto│  active-modifier line
│ [All skills▾] [≤ my level▾]  🔍___  Cmpct|Cards│ filters + density
├──────────────────────────────────────────┤
│ Product ▾          Profit/ea  GP/hr Succ Lvl│  header (sortable)
│ Cannonball          +205      302k   —   35 │
│ Prayer potion(4)    +142      210k   —   38 │
│ Cut opal            +38        85k  70%   1 │  ← Success% only when fail-capable
│ Cook shark          −149     −201k  73%  80 │  ← loss shown; red
│ Tan green d'hide    +260       n/a   —   57 │  ← n/a GP/hr (no tick data)
│ • Cut dragonstone  +1,240      95k   —   55 │  ← • = stale/low-volume dot
├──────────────────────────────────────────┤
│  ▼ Cook shark · Cooking lvl 80             │  detail (row expanded)
│    Raw shark ×1 ................... 900     │
│    Sells ×1 ...................... 1,050    │
│      − GE tax 2% ................... −21    │
│      = net ...................... 1,029     │
│    ↳ on fail: Burnt shark ............ 2    │
│    Profit/ea −149   ROI −17%   GP/hr −201k  │  four dimensions
│    XP/hr 80k   Profit/XP −1.9   B/E in 1,225│
│   ┌ Success ─────────────────────────────┐ │
│   │ Base 55% @90 → Gauntlets stop→94 = 73%│ │  success breakdown
│   │ Expected: .73×1,029 + .27×2 − 900 =−146│ │
│   └──────────────────────────────────────┘ │
│   💡 Burn 27%. Cook to 94 or add Hosidius. │  actionable tip
│   [☆ Watchlist] [＋ Shopping] [Set qty…]   │
└──────────────────────────────────────────┘
```

### Layout — sidebar `PluginPanel` with tabs (v1)

1. **Browse** — the master sortable/filterable recipe table (all conversions under the active sourcing mode).
2. **On-hand** — only what your current bank/inventory can make, ranked by total achievable profit.
3. **Watchlist** — pinned recipes.
4. **Shopping list** — generated mat lists + running cost (buy-limit aware; in hybrid mode, only the shortfall).

*(No Ledger / history tab — realized-profit tracking is Stockpile's job.)*

### The recipe table (primary surface)

- **Columns** (toggleable, sortable, resizable): Product · Profit/ea · Margin/ROI % · GP/hr · XP/hr · Profit/XP · **Success %** · Buy limit · Volume · Level req · Makeable-now qty.
  - **Success %** is populated **only for fail-capable recipes** (cooking burn, semi-precious gem cuts) at your live level with modifiers applied; always-100% recipes show "—".
  - **GP/hr** renders **"n/a"** when a recipe has no `ticks`, so it can't be the default sort (n/a rows would dominate).
  - **Volume** comes from the `/5m` payload (buy/sell volume), not a separate `/24h` call.
- **Active-modifiers status line** above the table: e.g. "Gauntlets ✓ · Hosidius elite ✓ · Jeweller's chisel ✓ — auto-detected", so success numbers are trusted and correctable.
- **Filters**: skill (multi-select), "makeable now," **level gating** (hide locked / grey out / show all — per §6.4), members/F2P, min profit, min GP/hr, min volume, min success %, hide-unprofitable, text search.
- **Sort**: click any column; default sort = **Profit/ea** (or the active dimension focus), never GP/hr.
- **Color coding**: green→red profit; a **staleness dot** when prices are old/null. *(Trend arrows from `/timeseries` are v2.)*
- **Density**: **compact spreadsheet rows by default**, with a **cards toggle** for touch/readability.

### Detail view (click a row)

**v1:**
- **Cost breakdown table**: each input qty × price, tools/consumables, GE tax line, net.
- All four dimensions side by side + **break-even input price**.
- **Success breakdown** *(fail-capable recipes only)*: base curve at your level → active modifiers → final chance, with the expected-value split `p·good + (1−p)·failure` and the yield multiplier if any.
- Buttons: Add to watchlist · Add to shopping list · Set target quantity.

**v2+:**
- Recursive **recipe tree** with make-vs-buy per intermediate (chosen path highlighted, alternative shown).
- **Mini price graph** for product and key inputs (reuse existing graph code).

### Overlays (v2+)

- **Bank overlay/tooltip** *(v2)* — top makeable products + total profit while banking.
- **GE helper** *(stretch)* — break-even price + remaining qty on the buy screen (read-only).

### UX principles

- **Show your work.** Every profit number is one click from its full breakdown — trust in a profit tool comes from transparency, not just the number.
- **Never present a stale/illiquid number as fact.** Freshness + volume must be visible, not buried.
- **Sensible defaults, deep config.** Works out of the box on install; power users can tune price policy, efficiency %, and thresholds.
- **Fast.** Cache aggressively, compute off the client thread, and page/virtualize the table so a full-catalog sort doesn't stutter.

---

## 9. Configuration options

- **Sourcing mode** default: On-hand / Buy-to-order / Hybrid.
- **Price source**: `/5m` 5-minute averages — buy = `avgHighPrice`, sell = `avgLowPrice` (fixed default; `/1h` fallback when a window is null).
- **Sell/patient toggle**: optional — assume you sell into `avgLowPrice` (instant) by default; a "patient" mode could price nearer `avgHighPrice`.
- **Valuation lens**: GE market (default) / Ironman no-GE (alch or shop for outputs, shop or gather for inputs).
- **On-hand valuation**: current market price (opportunity cost). *(Realized/paid-cost tracking is out of scope — see Stockpile.)*
- **GE tax**: on/off, cap/exemption handling (default on).
- **Efficiency %** for GP/hr (banking/travel overhead).
- **Level gating**: hide locked / show greyed / show all.
- **Success modifiers**: toggles for owned items/areas/diaries (jeweller's chisel, cooking gauntlets, cooking cape, Kourend/Hosidius tier) — or **auto-detect** from equipment/inventory/varbits (default on).
- **Success-rate modeling**: on/off.
- **Thresholds**: min profit/ea, min GP/hr, min volume (`/5m`), min success %, min ROI.
- **Skill filter** persistence.
- **Price refresh interval** (default **60 s**) + manual refresh; **User-Agent** string handling (set correctly by default).
- **Members/F2P** filter.
- **Notifications**: processing-finished on/off; profit-milestone on/off.

---

## 10. OSRS-specific edge cases & gotchas

- **Inverted `high`/`low` semantics** — `high` = insta-buy, `low` = insta-sell. Get this wrong and every spread flips. Unit-test it.
- **GE tax** — 2% on sells, per-item cap, low-value exemption, and some items exempt entirely. Applies to product sale, not inputs.
- **Buy limits (4h)** — throttle how many mats you can actually buy; a "money maker" can be limit-bound to near-zero real throughput.
- **Thin liquidity** — theoretical margins on low-volume items are unrealizable; volume-gate and flag.
- **Null / never-traded prices** — inputs/outputs may have `null` high or low; handle gracefully, don't compute garbage.
- **Non-consumed tools vs. per-action consumables** — chisel/mould/hammer aren't consumed; needle *thread*, charges, and similar *are* — model both.
- **Success / failure rates** — burning food, gem-cut fails, etc. are level-scaled and reduce effective yield.
- **Multi-output & byproducts** — value all outputs; some actions return multiple items or occasional extras.
- **Multi-step chains** — recursive make-or-buy; watch for cycles and cap recursion depth.
- **Untradeable inputs/outputs** — shop-only secondaries, quest items, gathered mats have no GE price → fall back to shop/alch value or time-cost.
- **Members vs. F2P** — flag and filter; don't surface members recipes to F2P by default.
- **Quest / diary / achievement unlocks** — gate recipes that require them.
- **Charges & degradation** — charged tools/equipment add a per-action cost that's easy to miss.
- **Bank space & trip overhead** — the gap between tick-math GP/hr and reality; the efficiency % is a blunt but honest fix.
- **Price scale** — values can exceed 32-bit ints; use appropriate types (you've been burned by less).

---

## 11. Plugin Hub compliance notes

Given the redesign is Hub-bound, bake compliance in from day one:

- **Informational only** — display, overlay, sort. **No** input automation, click injection, or GE auto-fill of prices/quantities. Read-only helpers are fine; anything that *acts* for the player is not.
- **Approved data sources** — the OSRS Wiki Real-time Prices API is a RuneLite/Wiki partnership and is the standard, expected source. Follow its acceptable-use policy: descriptive `User-Agent`, caching, no aggressive polling.
- **No sensitive data / no external account requirements** for core function (contrast with premium-API-key plugins — fine, but keep the core free and self-contained).
- **Performance & threading** — network and heavy compute off the client thread; the Hub review will care about jank.
- **Clear config, no dark patterns**, MIT/BSD-style license, and a README that states data sources and non-affiliation with Jagex.
- Re-check current Hub review guidelines at submission time — rules evolve, and your prior menu-swapper compliance review is a useful reference point for how strict the "does it act for the player?" line is.

---

## 12. Suggested phasing

| Phase | Feature set |
|---|---|
| **MVP** | **`{{Recipe}}` wikitext parser** → full recipe catalog, all skills, all declared outputs; `/5m` price client (60 s refresh, `avgHigh`/`avgLow`, `/1h` fallback); Browse + On-hand + Watchlist + Shopping-list tabs; the four dimensions + Success % (fail-capable); compact table w/ cards toggle; three sourcing modes (market-value only); **level-based success math** (scraped `{{Skilling success chart}}` + burn levels) with modifier registry; per-product breakdown; level gating; GE tax; sortable/filterable table; basic config. |
| **v2** | Recursive make-or-buy trees; **Ironman / no-GE valuation lens**; bank overlay; presets + import/export; volume/buy-limit warnings; price graphs & `/timeseries` trend arrows. |
| **Stretch** | GE-screen break-even helper; profit-vs-XP scatter & efficient frontier; processing-finished notifications; user-editable recipe overrides. |

**Highest-leverage differentiators** (what nothing else does well): the **hybrid sourcing mode + shortfall shopping list**, **make-or-buy across chains**, and showing **all four dimensions together** so "best money" and "cheapest XP" live in one sortable table.

---

## 13. Decisions & remaining open items

**Locked:**

1. **Recipe data** — full auto-extraction from the Bucket `recipe` table (extractor verified end-to-end). No hand-authored catalog.
2. **Launch scope** — go **wide**: all skills in the UI at launch, not a curated subset.
3. **Valuation** — **current market price** (opportunity cost). Realized-profit / cost-basis / ledger tracking is **out of scope**, delegated to Stockpile; this plugin is forward-looking only.
4. **Ironman / no-GE valuation — in scope** (v2). A second valuation lens behind the same `PriceLookup`: outputs by high alch (net nature rune) or shop, inputs by shop/gather, ranked by GP/hr + XP/hr; GE tax/limits/volume disabled. The GE-market lens stays the v1 default.
5. **Time / GP-hr** — use wiki `ticks` × a global **efficiency %**; label GP/hr as *theoretical throughput*; show **n/a when `ticks` is missing** rather than guessing; allow curated per-method overrides for top earners.
6. **Success rates** — **always level-scaled** via a per-recipe `SuccessModel` (interpolation / burn / flat), computing expected profit across success + failure outcomes. Parameters and failure outputs live in a small curated table; recipes with no model default to 100%.
7. **Prices** — `/5m` averages (`avgHighPrice` buy, `avgLowPrice` sell), refreshed **60 s**, `/1h` fallback; volume from the same payload.
8. **Extraction / multi-output** — the general **`{{Recipe}}` wikitext parser** is the extraction backbone (enumerate via `embeddedin`, parse the template, capture **all declared outputs**). New content is picked up on re-run with no curation. Bucket `recipe` stays as a quick-query cross-check.
9. **Success parameters — scrape.** `LINEAR_INTERP` `low`/`high`/`req` from `{{Skilling success chart}}` (same parser, different template; covers gem-cut fails etc.); `BURN` stop-levels from `Cooking/Burn level` + the burn formula. **Modifiers** (jeweller's chisel +20pp & ×1.10 yield, cooking gauntlets = per-fish stop-level swap, Hosidius +5/+10%, cooking cape force-success) are a small curated registry with four shapes — `ADD_CHANCE` / `STOP_LEVEL` / `FORCE_SUCCESS` / `YIELD_MULT` — toggled by config or auto-detected. Failure outputs (crushed gem, burnt food) are a small companion list; unmatched recipes default to 100%.
10. **Re-extraction — bundled per release.** CI regenerates `recipes.json` + `success_params.json` and ships them in the jar each release. No runtime hosted re-pull (dropped as over-engineered for how rarely recipes change).

**Remaining niceties (not blockers):**

- **Cooking burn table parse** — the one piece of scraping left to implement concretely (standard wikitable; name → itemId + stop-level columns). Interpolation scraping is done via the reused parser.
- **Cascading success** (barbarian fishing / herbiboar `bonuslow`/`bonushigh`) — a known extension of the model; rare in pure processing, add only if a relevant recipe needs it.
