# ComfyUI Command — Full Syntax Reference

## Base Command

```
_comfy [option1] [option2] ...
```

Aliases: `comfy`, `_generate`, `_gen`

Owner-only command (hardcoded to `253963350944251915`).

---

## All Parameters

| Parameter | Format | Default | Description |
|-----------|--------|---------|-------------|
| `seed` | `seed:<number>` | `-1` (random) | Main generation seed. Controls reproducibility. |
| `steps` | `steps:<number>` | `25` | Sampling steps. Higher = more detail, slower. |
| `cfg` | `cfg:<number>` | `6.5` | CFG scale. Higher = stronger prompt adherence. |
| `mode` | `mode:<1-4>` | `3` | Prompt assembly mode (see below). |
| `style` | `style:<rindex\|desuka>` | `rindex` | Style preset. Also accepts `style:1` / `style:2`. |
| `res` | `res:<preset>` | `landscape - 1344x768 (16:9)` | Resolution preset string. |
| `char` | `char:<name>` | *(empty)* | Manual character. Auto-switches to manual mode. |
| `random` | `random` | *(flag)* | Use random character from built-in list. |
| `tags` | `tags:<tags>` | *(workflow default)* | Gelbooru AND_tags for the reference image search. |

---

## Prompt Modes (`mode:`)

| Value | Label | Source Node | What it does |
|-------|-------|-------------|--------------|
| `1` | Normal | Node 793 "Сборщик мусора" | Direct prompt assembly |
| `2` | Combo | Node 786 "Сборщик мусора 2" | Combined prompt sources |
| `3` | Booru Prompter | Node 843 "Сборщик мусора 3" | Uses Gelbooru reference image tags as prompt (**default**) |
| `4` | Combo+Auto | Node 794 "Сборщик мусора 4" | Combined + auto-generated |

The mode switch (node 785) routes one of four StringFunction assembler outputs into the final CLIP encode.

---

## Character Modes (`char:` / `random`)

Controlled by node 778 (CR Text Input Switch):

| Switch Value | Trigger | Source |
|---|---|---|
| `1` (Random) | `random` or `randomchar` flag | Node 775 → picks random line from node 774 "Built-in Characters" |
| `2` (Manual) | `char:<name>` | Node 779, your text goes here directly |

Default is `1` (random). Using `char:` auto-sets switch to `2`.

---

## Gelbooru Tags (`tags:`)

Sets the `AND_tags` field on node 842 "Gelbooru (Random)".

- Workflow default: `rwby, solo`
- When provided, **fully replaces** the default AND_tags
- These tags control what reference image Gelbooru returns, which is most relevant in **mode 3** (Booru Prompter)
- Node 1300 (Rule34 Random) is a separate Gelbooru node — `tags:` does NOT affect it

---

## What You CANNOT Control Right Now

| Thing | Why | What controls it |
|---|---|---|
| **General prompt text** | No parameter wired up | Internal StringFunction chain (nodes 793/786/843/794) |
| **Negative prompt** | Hardcoded in node 1120 | `"worst quality, off-topic, comic, jpeg artifacts..."` |
| **Quality tags** | Hardcoded in node 777 | `"(masterpiece, best quality, very aesthetic...)"` |
| **Emotions / Clothing / Poses / etc.** | Randomized internally | DPRandomGenerator nodes 1281-1288 (seeds randomized each run) |
| **LoRA stack** | Hardcoded in workflow | CR LoRA Stack nodes |
| **Batch count** | Always 1 image | No parameter exists |
| **Node 1300 tags** | Separate Gelbooru node | Hardcoded to `bishoujo_senshi_sailor_moon, solo` |
| **Exclude tags** | Hardcoded per node | Node 842: `1boy, 2boys` / Node 1300: `3d, furry, 1boy, 2boys` |

---

## All Combinations — Examples

### Minimal (all defaults)
```
_comfy
```
Mode 3 (Booru), Style Rindex, Random character, 25 steps, CFG 6.5, landscape 16:9, default Gelbooru tags.

### Specific character + tags
```
_comfy char:1girl,ganyu tags:genshin_impact,solo
```
Manual character "1girl,ganyu", Gelbooru searches `genshin_impact,solo`.

### Random character, different mode
```
_comfy random mode:1 cfg:7
```
Random character from built-in list, Normal prompt mode, CFG 7.

### Reproducible generation
```
_comfy seed:12345 steps:30 style:desuka
```
Fixed seed for reproducibility, 30 steps, Desuka style preset.

### Full override
```
_comfy seed:42 steps:35 cfg:8 mode:2 style:desuka res:portrait_-_768x1344_(9:16) char:hatsune_miku tags:vocaloid,solo
```
Everything specified explicitly.

### Tags only (keep other defaults)
```
_comfy tags:fate/grand_order,solo,saber
```
Just change what Gelbooru searches for. Everything else stays default.

---

## Parsing Behavior Notes

- Arguments are **space-delimited** — values containing spaces are NOT supported (Discord splits on whitespace before the bot receives them)
- `char:` values use commas for multi-tag characters: `char:1girl,ganyu,gloves`
- `tags:` values use commas for Gelbooru tag syntax: `tags:rwby,solo,weiss_schnee`
- `style:` accepts both names (`rindex`/`desuka`) and numbers (`1`/`2`)
- `mode:` values outside 1-4 silently default to 3
- Invalid numeric values for `seed`/`steps`/`cfg` are silently ignored (keeps default)
- Parameter order does not matter
- Unknown arguments are silently ignored
