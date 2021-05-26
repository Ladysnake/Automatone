------------------------------------------------------
Version 0.3.7
------------------------------------------------------
**Additions**
- Exposed an IBaritone factory to allow controlling other living entities through Automatone

------------------------------------------------------
Version 0.3.6
------------------------------------------------------
**Fixes**
- Head yaw should not get synchronized properly when a fake player spawns

------------------------------------------------------
Version 0.3.5
------------------------------------------------------
**Changes**
- Automatone no longer comes with a default fake player type - making it usable as a serverside-only

**Fixes**
- Fixed a crash when a player stood on a block with a dynamic collision box

------------------------------------------------------
Version 0.3.4
------------------------------------------------------
- Hopefully fixed chunk saving error when unloading fake players

------------------------------------------------------
Version 0.3.3
------------------------------------------------------
**Fixes**
- Fixed a crash on dedicated servers (yup, caused by the last fix)
- Fixed sneaking being reset every tick for all players, preventing sneak interactions
- Fixed fake players not being properly unloaded with their chunk

------------------------------------------------------
Version 0.3.2
------------------------------------------------------
**Fixes**
- Fixed a crash when a mod tried to create a fake player clientside

------------------------------------------------------
Version 0.3.1
------------------------------------------------------
**Fixes**
- Fixed a crash during world save when a fake player rides another entity

------------------------------------------------------
Version 0.3.0
------------------------------------------------------
**Additions**
- Added experimental swimming abilities
  - Can be enabled with `Settings#enableSwimming`
  - Automatone will attempt to keep the entity from drowning on the way
    - This can be disabled with `Settings#ignoreBreath`

**Changes**
- Some methods and fields have been made available from API:
  - `IPathingBehavior#pathStart`
  - `GoalNear#x`/`y`/`z`
  - `RotationUtils#calcRotationFromVec3d`
- Avoidance lists can now be externally supplied

**Fixes**
- Fixed fake players being unable to open doors while pathing

------------------------------------------------------
Version 0.2.0
------------------------------------------------------
**Additions**
- Added `syncWithOps` setting to control whether chat and render info is sent to operators

**Changes**
- Block and item lists in settings now use tags

**Fixes**
- Fixed crash at launch outside of development environments
- Fixed tab complete for command aliases

------------------------------------------------------
Version 0.1.1
------------------------------------------------------
**Additions**
- Added an icon

**Changes**
- Fake players now use the name from their display profile if any is set
- Added dependencies to fabric.mod.json

**Fixes**
- Fixed bridging over long distances

------------------------------------------------------
Version 0.1.0
------------------------------------------------------
First alpha release of Automatone, forked from Baritone 1.6.3.

**EVERYTHING IS EXPERIMENTAL AND SUBJECT TO CHANGE**

**Additions**
- Fake Player API: easily create fake players that can be commanded with Baritone
  - Fake players do not load chunks, and do not prevent other players from sleeping
- Local settings: change pathfinding settings for one entity at a time
    - If a local setting doesn't have its value explicitly set, it will use the value of the equivalent global setting

**Changes**
- All operations now runs serverside
- Clientside commands have been replaced with serverside brigadier
    - All commands must be prefixed with `/automatone`
    - Only operators can use commands
- Operators can visualize paths for any entity that is using Automatone's pathfinding
- Automatone will consider ascending through a column of water
    - this does not include bubble columns yet
- Pathfinding can now use hanging vines and scaffolding, as well as any modded climbable block
    - modded scaffolding is unlikely to work though
- Automatone should detect which modded doors cannot be opened
- Parkour movements should overshoot a bit less
- Chunk scanning for mining operations should be slightly faster
- Simple movements can now occur while the entity is looking in some other direction
- Differently sized entities are *kind of, somewhat, vaguely handled* by Automatone's pathfinding
  - entities that are smaller than a player should work as expected
  - entities that are taller than a player should more or less work
  - entities that are wider than a player fit in some rather broad definition of "working"
    - they do not fit in any definition of "good" though

**Fixes**
- Fixed a crash when an entity fell into the void while pathfinding
- Fixed a crash when a player attempted to auto-build with ladders in their inventory
- Automatone will no longer attempt to place water buckets in `ultrawarm` dimensions
- Pathfinding will now consider the speed modifiers on any block, not just soulsand
- WIP: some moves should now work with varying player sizes
- Players will no longer attempt to mine or place blocks in protected areas

**Removals**
- Removed cheating