# Ritual Blade Mod (Fabric)

Server-side ritual test command for Minecraft Java Edition **1.21.11**.

## Build setup

This project targets:
- Minecraft `1.21.11`
- Fabric Loader stable
- Fabric API for `1.21.11`
- Fabric Loom `1.14.10`
- Java 21+

> Note: Loom 1.14.10 requires a Gradle version compatible with plugin API 9.x (for example Gradle 9.2.x).

## Command

- `/test`

The command scans for the closest **activated** ritual site in `searchRadius`.

## Exact ritual pattern

Given enchanting table position `T`:
- `C = T.down()`
- Must have redstone dust at all of:
  - `C.north()`
  - `C.south()`
  - `C.east()`
  - `C.west()`

Activation at trigger time requires at least one player's feet block (`player.getBlockPos()`) to be one of those four dust positions.

## In-game test instructions

1. Place an enchanting table at `T`.
2. Place redstone dust in the exact 4-way cross around `C = T.down()`.
3. Place one or more players on any of the four dust blocks.
4. (Optional) Place a chest within `chestSearchRadius` for blood-trail path effects.
5. Run `/test` as an operator.
6. Verify:
   - the nearest activated site is chosen,
   - dust powers up during the lift,
   - sword lifts smoothly with particles,
   - completion burst + sound occur,
   - dust state is restored after ritual,
   - item is dropped or granted to executor based on config.

## Config

Generated at:
- `config/ritual-blade-mod.json`

Fields:
- `searchRadius`
- `chestSearchRadius`
- `liftTicks`
- `timeFadeEnabled`
- `restoreTimeAfter`
- `particleCaps`
- `giveItemToExecutor`
