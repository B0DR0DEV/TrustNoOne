# TrustNoOne (horror) — Fabric 1.21.11

A cooperative psychological-horror mod for Minecraft Java Edition. Targets Java 21, Fabric Loader and Fabric API. No Simple Voice Chat or other gameplay mods are required. Fabric API is required. The mod does not check license keys or call external services; offline play still depends on the Minecraft launcher being used.

## Build with GitHub Actions

1. Extract this ZIP.
2. Upload **all files and folders** into the root of a GitHub repository; include the hidden `.github` folder.
3. Open **Actions**. The workflow runs on pushes to `main`/`master`, pull requests, or manually using **Build TrustNoOne (horror) → Run workflow**.
4. Open a successful run and download the `TrustNoOne-Fabric-1.21.11` artifact.
5. Install Fabric Loader for Minecraft 1.21.11 and the matching Fabric API, then place the generated JAR in `mods`. The host and every LAN player need the mod installed.

## Implemented gameplay

- Shared round lives are stored in the world save. A death creates a corpse, consumes one shared life and reduces a living partner's health by 2 HP. At zero lives, the round ends by switching connected players to spectator; world files are not deleted.
- To revive a connected teammate, aim at the corpse and hold right-click for two seconds within four blocks. Progress resets when the player releases right-click, looks away or walks away. The revived player returns with 2 HP.
- The impostor spawns at a configurable interval/chance, uses an online player's skin profile, imitates crouching/sprinting/jumping, may open nearby doors/trapdoors/fence gates, and briefly displays a held item. It freezes while seen and accelerates/hunts while unobserved.
- Player/impostor overhead nameplates are hidden by default.
- Darkness can be applied at night and in low-light locations; the client render distance can be limited by config. There is no monochrome filter.
- Killing animals, villagers or iron golems raises world sin. Thresholds strengthen nearby hostile mobs, apply Unluck and wear down equipped armor.
- Endermen can rarely drop an Ender Eye as an optional alternative to visiting the Nether.

## Configuration

After the first launch, the game generates `config/trustnoone.json`. Restart the world after changing settings to reliably apply them. The default values are also documented in `trustnoone.default.json`.

If `darkness.showSunAndMoon` is `false`, the current implementation hides the whole sky pass, including stars, because vanilla renders these together.

## Fixes after failed Build #2

- Updated Entity world/position calls and entity NBT hooks for the Minecraft 1.21.11 Yarn API.
- Updated the held-item renderer constructor, renderer-state visibility, and skin texture path access.
- Bumped the mod version to `0.2.1` so the artifact is distinguishable from the failed build.

## Build status note

These changes address the API mismatches shown in GitHub Actions Build #2. This ZIP has not been compiled locally because Gradle and dependency downloads were unavailable in the build environment. GitHub Actions is configured to perform the actual Gradle compile; if that run fails, use its complete error log to identify API mismatches.

## GitHub Actions layout

The repository root should contain `build.gradle`, `settings.gradle`, `gradle.properties`, and `src`. The workflow must be at `.github/workflows/build.yml` in the repository root. The workflow can detect the Gradle project in a nested directory, but GitHub only discovers workflows under the root `.github/workflows` directory. If the project is stored in `TrustNoOne-Fabric-1.21.11/`, replace the repository-root workflow with the version in this archive or move the project contents to the repository root.
