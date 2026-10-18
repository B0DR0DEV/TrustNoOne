# TrustNoOne (Horror) — Part 5

A cooperative psychological horror mod for Minecraft Java Edition 1.21.11. Requires Java 21, Fabric Loader, and Fabric API. No other gameplay mods or voice-chat add-ons are required. TrustNoOne does not delete world saves after a lost round and does not contact external services.

## New in Part 5

- **The Observer:** a rare, motionless player-shaped silhouette appears at a distance in darkness. It never attacks and vanishes after a player watches it for a short moment.
- **Footsteps behind you:** while moving at night or in a dark cave, players may hear extra footsteps nearby, sometimes from behind an obstruction. The sequence stops when the player stops moving.
- **Someone visited your home:** a rare nighttime event can trigger near a bed. A nearby closed door may open, a door sound plays, and unsettling messages appear in chat.
- **Fake friend messages:** the chat stages a disturbing line using the visible name of an online player, then an unknown message contradicts it and the name claims they sent nothing. This is an in-game scripted event, not a real player message.
- **God of Sins premonitions:** early sins escalate from private warnings to distant cosmetic lightning and red chat messages. After the fourth sin, a motionless Wither silhouette briefly appears in the distance. The fifth sin still summons the real boss.

## Existing features retained

- God of Sins boss fight after the fifth sin, with the vanilla boss bar, increased health, storm arrival, and escape punishment.
- A subtle static darkness overlay: slightly darker nights and deeper caves, without the pulsing Darkness effect.
- Night hunters, the player-mimicking Imposter, shared lives, corpses and revival, the sin system, fake player story events, red glitch messages, cosmetic lightning, and an alternative low-chance Ender Eye drop.

## Build with GitHub Actions

1. Extract this archive.
2. Upload the **contents of the project folder** to the root of your repository. The root should contain `build.gradle`, `settings.gradle`, `gradle.properties`, `src`, and `.github/workflows/build.yml`.
3. Open **Actions → Build TrustNoOne (horror)** and choose **Run workflow**. Builds also run after a push to `main` or `master`.
4. After a successful run, download the `TrustNoOne-Fabric-1.21.11` artifact.
5. Install Fabric Loader for Minecraft 1.21.11, matching Fabric API, and the JAR in `mods`. For LAN, install the mod on the host and every player.

## Configuration

After the first launch, the mod creates `config/trustnoone.json`. New Part 5 settings:

- `observer.enabled`, `intervalTicks`, `chancePercent`, `minDistance`, `maxDistance`;
- `mimicFootsteps.enabled`, `checkIntervalTicks`, `chancePercent`, `minSteps`, `maxSteps`;
- `homeHaunting.enabled`, `intervalTicks`, `chancePercent`, `radius`, `openNearbyDoor`;
- `fakeFriendMessages.enabled`, `intervalTicks`, `chancePercent`;
- `godPremonitions.enabled`, `omenDurationTicks`.

Existing settings for shared lives, the Imposter, darkness, night hunters, sins, the God of Sins, and chat events remain. Defaults retain a subtle no-flicker overlay: `darkness.nightOverlayAlpha=20`, `darkness.caveOverlayAlpha=36`, `darkness.visibilityRadiusChunks=10`.

## Build verification

This archive contains the version `0.6.0` source project. Gradle is not installed in this environment and network access to download Gradle/dependencies was unavailable. **Run GitHub Actions before replacing your working JAR.** If compilation fails, send the log beginning at the first `error:` line.
