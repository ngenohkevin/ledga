# Design assets

Each script vendors one asset set into the app. Each is pinned to an exact upstream version, and re-running it produces identical files. Requires Python 3 with `pip install pillow fonttools`.

| Script | Source (pinned) | Writes |
|---|---|---|
| `fetch_inter.py` | rsms/inter v4.1 release zip (SHA-256 `9883fdd4…`) | `app/src/main/res/font/inter_*.ttf` (subset, all OpenType features), `assets/licenses/inter-OFL.txt` |
| `fetch_phosphor.py` | `@phosphor-icons/core` 2.1.1 | `ui/design/icons/Ph.kt`, `assets/licenses/phosphor-MIT.txt` |
| `fetch_fluent.py` | `microsoft/fluentui-emoji` @ `1ffb34c` | `res/drawable-nodpi/fluent_*.webp` (96 px lossless), `ui/design/icons/FluentIcons.kt`, `assets/licenses/fluentui-emoji-MIT.txt` |

`fetch_fluent.py` groups: `CATEGORY` (one per built-in category), `CHROME` (onboarding, You, empty states), `USER_CHOICE` (what a category of your own can take, R68; listed again in `data/edit/CategoryLooks.kt`).

To add an icon:
1. Add its name to the script's list and run the script.
2. Update the expected list in `PhosphorTest` or the count in `FluentIconsTest`.
3. Run `./gradlew :app:testDebugUnitTest --tests 'com.ledga.app.ui.design.icons.*'`.
