# DreamWorldEdit

WorldEdit / FastAsyncWorldEdit style world editing for **Paper, Folia and Spigot** (1.21.x / 26.x, Java 21).

Build: `mvn package` -> `target/DreamWorldEdit.jar`

## How it stays fast (and works on Folia)
Every edit is split into small jobs - one per chunk (or per 64-block-tall slab of a chunk). A few jobs
start each tick (`performance.chunks-per-tick`), each loads its chunk asynchronously and then runs on the
thread that **owns** that chunk: Folia's region thread, or the main thread on Paper/Spigot. No block is
ever read or written from the wrong thread, and big edits never freeze the server.

## Commands (`//` = two slashes, same as WorldEdit)

**Selection:** `//wand`, `//pos1`, `//pos2`, `//hpos1`, `//hpos2`,
`//sel <cuboid|poly|sphere|ellipsoid|cyl>`, `//desel`, `//expand <n|vert> [dir]`, `//contract <n> [dir]`,
`//shift <n> [dir]`, `//outset <n> [-h|-v]`, `//inset <n>`, `//size`, `//count <mask>`, `//distr`, `//chunk`

**Region:** `//set <pattern>`, `//replace [mask] <pattern>`, `//walls`, `//faces` (`//outline`), `//overlay`,
`//center`, `//naturalize`, `//smooth [iterations]`, `//stack [count] [dir] [-a]`,
`//move [count] [dir] [leftover] [-s] [-a]`, `//line <pattern> [thickness]`, `//forest [type] [density%]`,
`//setbiome <biome>`, `/biomelist`

**Generation:** `//sphere`, `//hsphere <pattern> <r>[,ry,rz] [-r]`, `//cyl`, `//hcyl <pattern> <r>[,rz] [height]`,
`//pyramid`, `//hpyramid <pattern> <size>`

**Clipboard:** `//copy [-m mask]`, `//cut [leftover] [-m mask]`, `//paste [-a] [-s] [-o]`, `//rotate <90|180|270>`,
`//flip [dir]`, `/clearclipboard`

**Schematics:** `/schem paste <name> [-a] [-s]` (load + paste at your feet in one go), `//schem save <name> [-f]`,
`//schem load <name>`, `//schem list`, `//schem delete <name>`
**Litematica `.litematic` files work too** (every region, all block states): copy them from your
`.minecraft/schematics` folder into `plugins/DreamWorldEdit/schematics/`, stand where Litematica's placement origin
would be and run `/schem paste <name>`. Names may have spaces and any upper/lower case (`/schem paste pew pew builds`).
Sponge `.schem` files (WorldEdit 7 / FAWE format, v2 + v3 read, v2 written). Files go in
`plugins/DreamWorldEdit/schematics/`; `plugins/WorldEdit/schematics` and `plugins/FastAsyncWorldEdit/schematics`
are searched too.

**History:** `//undo [n]`, `//redo [n]`, `/clearhistory`

**Utility:** `//fill <pattern> <r> [depth]`, `//drain <r>`, `//fixwater <r>`, `//fixlava <r>`, `//snow <r>`,
`//thaw <r>`, `//green <r>`, `//ex <r>`, `//replacenear <r> <mask> <pattern>`, `/removenear <mask> [r]`,
`/removeabove [size] [height]`, `/removebelow [size] [depth]`, `/butcher [r] [-a] [-n]`, `//limit`

**Brushes** (bound to the item in your hand, right-click to use):
`/brush sphere <pattern> [r] [-h]`, `/brush cylinder <pattern> [r] [height] [-h]`, `/brush smooth [r] [iterations]`,
`/brush gravity [r]`, `/brush butcher [r]`, `/brush paste [-a]`, `/mask [mask]`, `/material <pattern>`,
`/size <n>`, `/range <n>`, `/none`

**Navigation:** `/jumpto` (`/j`), `/thru`, `/up <n>`, `/ascend [levels]`, `/descend [levels]`, `/unstuck` (`/!`), `/ceil`

**General:** `//help [group]`, `/dwe <version|reload|help>`

### Patterns
`stone`, `oak_stairs[facing=north]`, `stone,dirt` (equal mix), `70%stone,30%cobblestone`, `hand`, `#clipboard`

### Masks
`stone,dirt` (any of), `!air` (not), `#existing`, `#solid`, `#liquid`, `>grass_block` (above grass),
`<stone` (below stone), several separated by spaces = all must match.

## Permissions
| Node | Default |
|---|---|
| `dreamworldedit.*` - everything | op |
| `dreamworldedit.command.<name>` (e.g. `.set`, `.copy`, `.schem`, `.brush`, `.wand`) | op |
| `dreamworldedit.brush.use` | op |
| `dreamworldedit.limit.unlimited` / `dreamworldedit.limit.<number>` | op / - (`limits.default` otherwise) |
| `dreamworldedit.admin` - `/dwe reload` | op |

## Not included (compared to FAWE)
Block entity contents (chest items, sign text) and entities are not copied/pasted - only blocks.
No `//regen`, `//deform`, `//generate` formulas, CUI support, or `/anvil`. Biome changes are not undoable.
Don't run it together with WorldEdit/FAWE - both use the same `//` commands.

## Protected zones (`/zone`)
Select an area (`//wand`, then left and right click), then `/zone create spawn` (add `-full` for sky to bedrock).
`/zone flag <zone> <rule> on|off` changes a rule. `/zone list | info | select | redefine | delete`.

| Rule | Default | Blocks |
|---|---|---|
| `build` | off | breaking / placing blocks, buckets, item frames, armor stands |
| `containers` | off | opening chests, barrels, furnaces, shulkers |
| `use` | on | doors, trapdoors, gates, buttons, levers |
| `pvp` | off | players hitting each other |
| `damage` | off | any damage (falling, mobs, fire...) |
| `hunger` | off | hunger going down |
| `mobs` | off | monsters spawning |
| `explosions` | off | TNT, creepers, crystals breaking blocks |
| `fire` | off | fire spreading |
| `pearls` | off | ender pearls (throwing inside, landing inside) and chorus fruit |
| `drops` | off | dropping items |
| `trample` | off | trampling farmland |

Ops and `dreamworldedit.zone.bypass` can still build, open containers, throw pearls and drop items; pvp, damage and
hunger rules apply to everyone.
