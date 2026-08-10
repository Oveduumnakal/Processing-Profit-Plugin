# Data extraction tools

Build-/release-time scripts that regenerate the recipe and success-rate data
bundled in the plugin jar. **They are never run at runtime** — recipes change
rarely, so the generated JSON is committed and shipped, and refreshed per
release. See `processing-profit-plugin-features.md` §4 for the full design.

All scripts use only the Python 3 standard library — no `pip install` needed.

## `extract_recipes.py` → `src/main/resources/recipes.json`

Parses the `{{Recipe}}` template out of OSRS Wiki page wikitext (enumerated via
`embeddedin=Template:Recipe`), capturing **every declared output** (not just
`output1`, which is all the Bucket API exposes — so this is the authoritative
extraction path). Resolves item names → ids via the prices `/mapping` endpoint.

```sh
python3 tools/extract_recipes.py                 # full extraction (~1 min)
python3 tools/extract_recipes.py --limit 200     # first 200 pages (quick test)
python3 tools/extract_recipes.py -o out.json     # custom output path
```

Output is a sorted array of recipes in the normalized schema (outputs[],
inputs[], tools[], skills[], ticks, members, facility, requirements, success,
onFailure, source). Every numeric wiki field is a string upstream and is parsed
defensively; `experience` may be non-numeric (`"Varies"`) and becomes `null`.

**Skipped by design:**

- Rows with an empty `output` (gathering / minigame / bonus `{{Recipe}}` uses).
- Recipes whose **primary output name does not resolve to a GE item id**. The
  prices `/mapping` endpoint only covers GE-tradeable items, so untradeable
  outputs (quest items, crystal gear, `(kp)` spears, etc.) are dropped — they
  have no GE price and can't be valued under the v1 GE-market lens. The Ironman
  / no-GE lens (v2) may later re-include these valued by alch/shop.

## When to re-run

- Before each plugin release (new content, renamed items, changed recipes).
- Wired into CI so the bundled snapshot stays current (see issue #4).

## `extract_success.py` → `src/main/resources/success_params.json`

Scrapes level-based success models, keyed by item id, merged into the recipes at
build time. Reuses the extractor's helpers (`api`, `wikitext_batch`, `parse_recipe`,
`load_name_index`, …). Standard library only.

```sh
python3 tools/extract_success.py            # ~10s
python3 tools/extract_success.py -o out.json
```

- **`LINEAR_INTERP {low, high, req}`** from every page transcluding
  `{{Skilling success chart}}` (semi-precious gem cuts, etc.). Verified: opal
  `low=128` → `129/256 = 0.5039` at level 1.
- **`BURN {stopLevel, gauntletStopLevel}`** from the `Cooking/Burn level` tables.
  A header-aware wikitable parser reads the **Range** column as the base stop
  level and the **Gauntlets Default** column as the gauntlet stop level. A dashed
  base (food still burns at 99 on a plain range, e.g. shark) is modeled as stop
  level `100`. Hosidius/cape are runtime modifiers (see `success_modifiers.json`),
  not scraped here; `reqLevel` is filled from the recipe at merge.

BURN overrides LINEAR_INTERP on the same id (a cooked fish is never a gem cut),
which also suppresses stray fishing/gathering chart entries landing on food ids.

The curated `success_modifiers.json` + failure-outputs list (issue #3) are the
small hand-maintained companion; recipes with no scraped model default to 100%.

## Licensing / etiquette

Recipe and item data come from the **OSRS Wiki** (CC BY-NC-SA 3.0 — non-commercial,
share-alike, attribution required) and the **OSRS Wiki Real-time Prices API**.
Both scripts send a descriptive `User-Agent` and sleep between requests. Keep the
plugin free and non-commercial, and attribute the wiki in the README.
