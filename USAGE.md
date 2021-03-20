(assuming you already have Baritone [set up](SETUP.md))

# Prefix

Baritone's chat control prefix is `#` by default.

Baritone commands can also by default be typed in the chatbox. However if you make a typo, like typing "gola 10000 10000" instead of "goal" it goes into public chat, which is bad, so using `#` is suggested.

To disable direct chat control (with no prefix), turn off the `chatControl` setting. To disable chat control with the `#` prefix, turn off the `prefixControl` setting. Be careful that you don't leave yourself with all control methods disabled (if you do, reset your settings by deleting the file `minecraft/baritone/settings.txt` and relaunching).

# For Baritone 1.2.10+, 1.3.5+, 1.4.2+

Lots of the commands have changed, BUT `#help` is improved vastly (its clickable! commands have tab completion! oh my!).

Try `#help` I promise it won't just send you back here =)

"wtf where is cleararea" -> look at `#help sel`

"wtf where is goto death, goto waypoint" -> look at `#help wp` 

just look at `#help` lmao

Watch this [showcase video](https://youtu.be/CZkLXWo4Fg4)!

# Commands

[Tutorial playlist](https://www.youtube.com/playlist?list=PLnwnJ1qsS7CoQl9Si-RTluuzCo_4Oulpa)

**All** of these commands may need a prefix before them, as above ^.

`help`

To toggle a boolean setting, just say its name in chat (for example saying `allowBreak` toggles whether Baritone will consider breaking blocks). For a numeric setting, say its name then the new value (like `primaryTimeoutMS 250`). It's case insensitive. To reset a setting to its default value, say `acceptableThrowawayItems reset`. To reset all settings, say `reset`. To see all settings that have been modified from their default values, say `modified`.

Some common examples:
- `thisway 1000` then `path` to go in the direction you're facing for a thousand blocks
- `goal x y z` or `goal x z` or `goal y`, then `path` to set a goal to a certain coordinate then path to it
- `goto x y z` or `goto x z` or `goto y` to go to a certain coordinate (in a single step, starts going immediately)
- `goal` to set the goal to your player's feet
- `goal clear` to clear the goal
- `cancel` or `stop` to stop everything
- `goto portal` or `goto ender_chest` or `goto block_type` to go to a block.
- `mine diamond_ore iron_ore` to mine diamond ore or iron ore (turn on the setting `legitMine` to only mine ores that it can actually see. It will explore randomly around y=11 until it finds them.) An amount of blocks can also be specified, for example, `mine 64 diamond_ore`.
- `click` to click your destination on the screen. Right click path to on top of the block, left click to path into it (either at foot level or eye level), and left click and drag to select an area (`#help sel` to see what you can do with that selection).
- `follow player playerName` to follow a player. `follow players` to follow any players in range (combine with Kill Aura for a fun time). `follow entities` to follow any entities. `follow entity pig` to follow entities of a specific type.
- `wp` for waypoints. A "tag" is like "home" (created automatically on right clicking a bed) or "death" (created automatically on death) or "user" (has to be created manually). So you might want `#wp save user coolbiome`, then to set the goal `#wp goal coolbiome` then `#path` to path to it. For death, `#wp goal death` will list waypoints under the "death" tag (remember stuff is clickable!)
- `build` to build a schematic. `build blah.schematic` will load `schematics/blah.schematic` and build it with the origin being your player feet. `build blah.schematic x y z` to set the origin. Any of those can be relative to your player (`~ 69 ~-420` would build at x=player x, y=69, z=player z-420).
- `schematica` to build the schematic that is currently open in schematica
- `tunnel` to dig and make a tunnel, 1x2. It will only deviate from the straight line if necessary such as to avoid lava. For a dumber tunnel that is really just cleararea, you can `tunnel 3 2 100`, to clear an area 3 high, 2 wide, and 100 deep.
- `farm` to automatically harvest, replant, or bone meal crops
- `axis` to go to an axis or diagonal axis at y=120 (`axisHeight` is a configurable setting, defaults to 120).
- `explore x z` to explore the world from the origin of x,z. Leave out x and z to default to player feet. This will continually path towards the closest chunk to the origin that it's never seen before. `explorefilter filter.json` with optional invert can be used to load in a list of chunks to load.
- `invert` to invert the current goal and path. This gets as far away from it as possible, instead of as close as possible. For example, do `goal` then `invert` to run as far as possible from where you're standing at the start.
- `version` to get the version of Baritone you're running
- `damn` daniel

For the rest of the commands, you can take a look at the code [here](https://baritone.leijurv.com/baritone/api/Settings.html).

All the settings and documentation are <a href="https://github.com/cabaletta/baritone/blob/master/src/api/java/baritone/api/Settings.java">here</a>. If you find HTML easier to read than Javadoc, you can look <a href="https://baritone.leijurv.com/baritone/api/Settings.html#field.detail">here</a>.

There are about a hundred settings, but here are some fun / interesting / important ones that you might want to look at changing in normal usage of Baritone. The documentation for each can be found at the above links.
- `allowBreak`
- `allowSprint`
- `allowPlace`
- `allowParkour`
- `allowParkourPlace`
- `blockPlacementPenalty`
- `renderCachedChunks` (and `cachedChunksOpacity`) <-- very fun but you need a beefy computer
- `avoidance` (avoidance of mobs / mob spawners)
- `legitMine`
- `followRadius`
- `backfill` (fill in tunnels behind you)
- `buildInLayers`
- `buildRepeatDistance` and `buildRepeatDirection`
- `worldExploringChunkOffset`
- `acceptableThrowawayItems`
- `blocksToAvoidBreaking`
- `mineScanDroppedItems`
- `allowDiagonalAscend`




# Troubleshooting / common issues

## Why doesn't Automatone respond to any of my chat commands?

Automatone commands will not work if you are not an operator.
Alternatively, it could be that you did not install the mod properly - if you did,
an `automatone` directory should have been created in your minecraft instance's `config` folder.
