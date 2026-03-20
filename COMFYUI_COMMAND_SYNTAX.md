# ComfyUI Command — Full Syntax Reference

## Base Command

```
_comfy [tags] [option1] [option2] ...
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
| `scale` | `scale:<number>` | `1.5` | Upscale multiplier (Множитель Апскейла). Range: 0.5-4.0. |
| `mode` | `mode:<1-4>` | `3` | Prompt assembly mode (see below). |
| `style` | `style:<rindex\|desuka>` | `rindex` | Style preset. Also accepts `style:1` / `style:2`. |
| `res` | `res:<preset>` | `landscape - 1344x768 (16:9)` | Resolution preset string. |
| `char` | `char:<name>` | *(empty)* | Manual character. Auto-switches to manual mode. |
| `random` | `random` | *(flag)* | Use random character from built-in list. |
| `tags` | `tags:<tags>` | *(workflow default)* | Gelbooru AND_tags for the reference image search. |
| `count` | `count:<number\|inf>` | `1` | Number of images to generate (max 50, or `inf` for infinite loop). |
| *(bare text)* | `word1 word2 ...` | *(empty)* | Extra tags appended to the final prompt (General Tag). |

---

## General Tags (Default Behavior)

Any argument that doesn't match a `key:value` parameter or a keyword (`random`, `help`, etc.) is treated as a **general tag** and appended to the final prompt via node 797.

```
_comfy 1girl red_hair beach sunset
```
This adds `1girl, red_hair, beach, sunset` to the prompt.

General tags work **independently** from `tags:` (Gelbooru AND_tags). You can use both:
```
_comfy 1girl red_hair tags:rwby,solo
```
- `1girl, red_hair` → injected into the prompt (node 797 text_b)
- `rwby,solo` → Gelbooru search tags (node 842 AND_tags)

---

## Upscale Multiplier (`scale:`)

Controls node 11 "Множитель Апскейла" (LatentUpscaleBy).

| Value | Effect |
|-------|--------|
| `scale:1.0` | No upscale |
| `scale:1.5` | Default (1.5x) |
| `scale:2.0` | 2x upscale |
| `scale:4.0` | Maximum (4x) — very slow, high VRAM |

Clamped to range 0.5-4.0.

---

## Image Count & Infinite Loop (`count:`)

| Value | Effect |
|-------|--------|
| `count:1` | Default — single image |
| `count:5` | Generate 5 images in sequence |
| `count:50` | Maximum finite count |
| `count:inf` | Infinite loop (also accepts `infinite`, `loop`) |

Use `_comfy stop` to halt a running loop. Only one loop per channel.

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

## LoRA Commands

### List Active LoRAs

```
_comfy loras
```

Shows all LoRAs with non-zero weights in the workflow chain, numbered for reference. Displays both model and clip weights, with an asterisk (`*`) marking any overridden weights.

### Set LoRA Weights

```
_comfy lora <index>:<model_weight> [<index>:<model_weight> ...]
_comfy lora <index>:<model_weight>:<clip_weight>
```

Set weights by LoRA index (from `_comfy loras` output).

| Format | Effect |
|--------|--------|
| `_comfy lora 1:0.2` | Set LoRA #1 model AND clip weight to 0.2 |
| `_comfy lora 1:0.3:0.4` | Set LoRA #1 model to 0.3, clip to 0.4 |
| `_comfy lora 1:0.2 2:0.6 5:0.8` | Set multiple LoRAs at once |
| `_comfy lora reset` | Clear all weight overrides |

**Weights persist** across generations until reset or bot restart. Use `_comfy loras` to see current state.

### LoRA Chain (Workflow Order)

| # | Node ID | LoRA | Default Model | Default Clip |
|---|---------|------|---------------|--------------|
| 1 | 1412 | ill_pandacorya_StyleV2 | 0.31 | 0.28 |
| 2 | 1413 | pijaC | 0.30 | 0.50 |
| 3 | 1414 | Fugtrup-ArtStyle | 0.40 | 0.50 |
| 4 | 1418 | Septya | 0.75 | 0.75 |
| 5 | 1415 | prywinko-guy90-Illust-Lycorisv1 | 0.45 | 0.36 |
| 6 | 1417 | 96YOTTEA-WAI | 0.35 | 0.45 |
| 7 | 1416 | ck-shadow-circuit-IL-000012 | 0.35 | 0.40 |

Node 10 (aidmaImageUpgrader) has weight 0/0 and is inactive.

---

## Subcommands

| Command | Description |
|---------|-------------|
| `_comfy help` | Show help embed |
| `_comfy loras` | List active LoRAs with weights |
| `_comfy lora <specs>` | Set persistent LoRA weight overrides |
| `_comfy lora reset` | Clear LoRA weight overrides |
| `_comfy stop` | Stop running generation loop |

---

## All Combinations — Examples

### Minimal (all defaults)
```
_comfy
```
Mode 3 (Booru), Style Rindex, Random character, 25 steps, CFG 6.5, landscape 16:9, default Gelbooru tags.

### Extra tags in prompt
```
_comfy 1girl beach sunset
```
Appends `1girl, beach, sunset` to the generated prompt. All other settings stay default.

### Tags + Gelbooru + upscale
```
_comfy 1girl red_hair tags:rwby,solo scale:2.0 steps:30
```
Extra prompt tags + Gelbooru search + 2x upscale + 30 steps.

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

### Infinite generation loop
```
_comfy count:inf random mode:1
```
Generates images until `_comfy stop` is called.

### Generate 5 images
```
_comfy count:5 1girl armor tags:fate/grand_order
```
5 sequential generations with extra tags + Gelbooru search.

### LoRA workflow
```
_comfy loras                    ← check current weights
_comfy lora 1:0.5 4:0.3        ← tweak LoRAs #1 and #4
_comfy count:3 1girl knight     ← generate 3 images with new weights
_comfy lora reset               ← restore defaults
```

### Full override
```
_comfy seed:42 steps:35 cfg:8 scale:2.0 mode:2 style:desuka res:portrait_-_768x1344_(9:16) char:hatsune_miku tags:vocaloid,solo
```
Everything specified explicitly.

---

## Parsing Behavior Notes

- Arguments are **space-delimited** — values containing spaces are NOT supported (Discord splits on whitespace before the bot receives them)
- `char:` values use commas for multi-tag characters: `char:1girl,ganyu,gloves`
- `tags:` values use commas for Gelbooru tag syntax: `tags:rwby,solo,weiss_schnee`
- `style:` accepts both names (`rindex`/`desuka`) and numbers (`1`/`2`)
- `mode:` values outside 1-4 silently default to 3
- `scale:` values outside 0.5-4.0 are clamped to range
- `count:` values above 50 are clamped to 50
- Invalid numeric values for `seed`/`steps`/`cfg`/`scale` are silently ignored (keeps default)
- Parameter order does not matter
- Unrecognized arguments become **general tags** (appended to prompt)
- LoRA weight overrides **persist** across commands until `_comfy lora reset` or bot restart
