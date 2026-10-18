# TrustNoOne (horror) — Part 4

Minecraft Java 1.21.11, Fabric, Java 21. Requires Fabric API.

New: the Sin God (a high-health Wither boss summoned at five sins, strengthened by later sins, and returned by lightning if players flee), a subtle static darkness tint at night and in dark caves (no pulsing/flicker; HUD and menus stay readable), night hunters (vanilla zombie/husk variants with faster movement and 2 HP raw attacks), fake-player join/story/leave chat event, cosmetic random lightning near players at night, and dark-red obfuscated chat messages. Horror features have JSON config toggles and timing/chance controls.

See [README_RU.md](README_RU.md) for setup and the full configuration reference. Upload the project contents to the repository root and run **Actions → Build TrustNoOne (horror)**.

The source could not be compiled locally in this environment because Gradle/dependency downloads are unavailable. Please verify with the repository GitHub Actions workflow before installing the JAR.


### Darkness settings

Nighttime uses a subtle static tint (alpha 20/255); dark underground caves get a slightly stronger tint (alpha 36/255). No pulsing vanilla Darkness effect is applied, so there is no screen flicker. HUD and menus stay readable. The alpha settings can be adjusted in `config/trustnoone.json` with `darkness.nightOverlayAlpha` and `darkness.caveOverlayAlpha` (0–80). Default visibility cap is 10 chunks.
