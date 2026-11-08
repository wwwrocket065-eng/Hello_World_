# Arknights Recruitment Assistant — development checkpoints

## Product contract
- Android 16 on Xiaomi 25069PTEBG first; design for generic Android 8+.
- Global server recruitment only until CN data source validation.
- Continuous opt-in screen capture via foreground service; no automatic taps in Arknights.
- OCR of five recruitment tags, pass-through recommendation frames, left-side operator panel.
- Small movable gear opens settings overlay without leaving game.
- Mode default: highest guaranteed rarity, optional highest possible rarity.
- Full operator list and tag candidate set; never fabricate drop percentages.
- Separate Android APK builds via GitHub Actions and independent non-GitHub Global data updater.
- Settings: expandable highlight palette with brightness and opacity controls, panel toggle, priority toggle, permissions. No thickness or corner-radius controls.
- On Android 14+: screen-capture consent must be requested for each new MediaProjection session.

## Sources and data-safety rules
- Independent API: https://awedtan.ca/api/recruitpool (Global IDs) + /api/operator (operator details).
- Validated on 2026-11-01 via external web lookup; service availability after that is not guaranteed.
- Reject malformed or suspiciously small feeds; preserve last known good cache.
- Do not show a claimed 4/5/6★ guarantee when there is no validated database.
- Chance mode reports possible operators, not invented mathematical odds.
- Top Operator should be matched at 9h; Robot requires a shorter recruitment time.

## Implementation status
- RecruitmentEngine.java: deterministic subset search of 1–3 tags with rarity ranking.
- OperatorDatabase.java: fetch and cache validated Global operator roster from independent API.
- MainActivity.java: grant overlay, request capture, edit settings, manual tag test.
- ScreenService.java: MediaProjection + offline ML Kit OCR + foreground notification + pass-through boxes + movable gear/panel.
- .github/workflows/arknights-build.yml: automated debug APK build.

## Testing and known limitations
- CI results must be inspected after every commit; a pushed commit is not proof of a build.
- Device validation not yet done: Xiaomi/Android 16 screen-capture orientation, tag rectangle alignment, ML Kit accuracy, power use.
- Selecting tags in the game is not yet independently tracked by a reliable visual-state classifier. Do not claim it works.
- No permanent CI artifact distribution outside GitHub Actions yet.
- CN server source not implemented, and remote data parser must be tested against a live, full API response.
- Not production-ready; completion requires build success + interactive in-game testing.

## 2026-11-01 — Xiaomi user-test feedback, version 0.3.0
Issues consolidated from hands-on 0.2.0 inspection:
1. Permissions: overlay button now named "Разрешение"; try Xiaomi app-specific settings then open this app's details (Android 11+ cannot deep-link to the overlay toggle via public ACTION_MANAGE_OVERLAY_PERMISSION).
2. Global operator database: show loading spinner, phase labels, result and last successful update date. Percent is deliberately omitted when total work cannot be measured.
3. Screen capture and controls: previously gear appeared only after acquiring MediaProjection; now Start creates a persistent gear + ▶ control first. Android screen-capture consent is requested only upon ▶.
4. On Android 14+, explicitly request default-display capture instead of system single-app selection. User consent is still mandatory each new session.
5. Main app removes Stop Analysis; ▶ / ⏸ toggles analysis within game; gear opens settings, with "Выключить помощник" to fully stop and release resources.
6. OCR runs frequently only on detected recruitment tag screen (exactly five recognized tags); slow polling outside recruitment, with no old highlights and manual pause respected.
7. Immersive main UI hides 3-button navigation until swipe; uses status-bar/display-cutout insets to position the title clear of Xiaomi front-camera cutout.
8. Landscape transition resizes the existing VirtualDisplay without reusing a single-use capture token; avoids the previous unconditional stop on configuration changes.
9. Overlay-only waiting mode uses a user-visible specialUse foreground service; mediaProjection type is used once consent is granted. Before Google Play publication, specialUse declaration requires additional Play policy review.

Build: `.github/workflows/arknights-build.yml` triggers on every push to the isolated arknights branch; compare the latest successful run with this exact revision before distributing the APK.
Pending device validation: real Xiaomi Android 16 overlay window restrictions, prompt UX, orientation/pixel coordinate alignment, recruitment OCR quality, DB API availability, and power usage. Do not label as phone-tested until on-device verification.

## 2026-11-01 — UI cleanup after Xiaomi v0.3.0 feedback
- Main header: compact ⚙ button to the right of “Arknights Recruitment Assistant”; secondary caption is only “Global”.
- Removed verbose overlay/capture labels and bottom explanatory note from the main page.
- Moved “Разрешение” and “Уведомления” to the in-app settings dialog; dropped “(Android 13+)” from the notification label.
- “Запустить” continues to check `Settings.canDrawOverlays`; when missing, it invokes the **same** overlay-permission handler as the settings button.
- Fixed previously incorrect Xiaomi “Other permissions” deep link. Replaced with public `ACTION_MANAGE_OVERLAY_PERMISSION` (Android 10 and older: app URI; Android 11+: system overlay selection screen, as required by platform). App-details fallback only when the dedicated settings action is unavailable.
- IMPORTANT: Android 11+ ignores the package for the public overlay settings intent; an exact single-app toggle cannot be guaranteed by the documented public API. Do not claim that a direct toggle is implemented or Xiaomi-device-validated.
- Commit for MainActivity UI change: `3dec472beabd6de35fe284157091958dda46ece9`. CI/APK validation and Xiaomi functional testing are not established by this note.

## 2026-11-01 — unified dark UI and highlight control (source version 0.3.2)
- All main-app and floating controls share `UiStyle.java`: dark slate surfaces, muted settings gear, cyan accent, high-contrast text, rounded controls; no white-filled application buttons.
- Settings dialogs and floating settings have a compact top-right X instead of a bottom "Закрыть" button.
- `HighlightColorControl.java` is a reusable expandable color-swatch picker. Tapping "Цвет подсветки" toggles the palette. The trigger itself always previews the selected color; each palette swatch immediately updates it.
- The expanded palette includes a brightness slider (0–100%). Hue/saturation stay selected while brightness changes; selection and brightness are persisted in SharedPreferences. The main-screen reset clears those keys.
- Removed separate RGB, stroke-width and corner-radius UI controls. Kept opacity separate from brightness, with a 10–70% opacity slider labeled "Непрозрачность подсветки".
- Replaced outline highlight with a translucent *filled*, non-rounded rectangle, centered on the OCR text detection. Estimated tag size from the 1536×709 reference screenshot: approximately 142×46 screen-image pixels, scaled by capture size. These are estimates, **not pixel-accurate detection of tag edges**. Visual validation on the target Xiaomi and in-game layout variants is still required.
- Version code/name was bumped from 4 / 0.3.1 to 5 / 0.3.2 before these changes.
- GitHub Actions workflow triggers on branch push; final run success and APK integrity remain to be independently checked. Do not claim this is a verified distributable APK.

## 2026-11-01 — 0.3.3: floating controls, screen capture, OCR
- Device video `4706.mp4` showed a black portrait capture with a narrow game strip; cause not yet device-proven.
- Removed `FLAG_SECURE` from the transparent **full-screen** markers overlay and from floating control/settings windows. A secure full-screen overlay can black out screen recordings/MediaProjection and prevent OCR of the underlying game. Must re-test on Xiaomi.
- New `FloatingControlButton.java`: left ▶ / ⏸ control and right ⚙ control, fully custom Canvas path icons with softly rounded corners and the same muted cyan tone for Play and Pause; no emoji/system orange decoration.
- `ScreenService.java`: scan idle screens at 2.5s rather than 6s, analyze again at 1.3s in recruitment. Detect near-black capture frames and show a reason in status.
- OCR now accepts at least 3 of 5 distinct recognized tags, rather than discarding anything except exactly 5. Parses both full text lines and individual/pair text elements to handle split names. `RecruitmentEngine.normalizeOcr` does uniquely matched, limited edit-distance correction, while user-entered tags remain strict.
- Read OCR screenshot pixel dimensions and transform highlight tile coordinates into actual overlay view dimensions; estimate full rectangular tag extent from detected text size and reference screenshot rather than outline stroke or global screen percentage.
- Keep exactly one authorized MediaProjection session, resize its VirtualDisplay and ImageReader after screen rotation, including when Service receives size changes late.
- Keep the compact overlay status panel visible while scanning (unless disabled) so failure to recognize tags isn't silent.
- Added OCR smoke tests to `arknights/tests/EngineSmoke.java`.
- Version bumped to `0.3.3` (`versionCode=6`).
- **CI status and APK:** code changes committed; verify GitHub Actions for latest commit, extract artifact, inspect APK before providing a link.
- **Still unverified:** On-device Android 16 launch/pause consent, OCR quality on actual tags, accuracy of tile edges, and whether the black capture video is fixed. Do not claim phone validation until user tests.

## 2026-11-01 — 0.3.3 build/validation checkpoint
- GitHub Actions run **41331887793** completed successfully (including Java recruitment/OCR smoke test and Gradle assembleDebug). The build corresponds to source commit `0d777eafb8abeb659ebaf43ebe283c204a6528ed`, before this documentation-only commit.
- Artifact: `arknights-debug-apk`, ID **13093603084**. Extracted `app-debug.apk` as `Arknights_Recruitment_Assistant_v0.3.3.apk`.
- ZIP integrity and APK ZIP contents passed. DEX contains new `FloatingControlButton` and `normalizeOcr`. Manifest confirms versionName `0.3.3`; SHA-256: `825cd899e8785d09dab3e9626e2d39924afbb499daaba9fbac60067035466534`.
- **Upgrade caveat:** the Android debug signing certificate for 0.3.3 differs from 0.3.2 (v2 signing block certificate SHA-256: 0.3.2 `65bafc9102ea8b20c146233a834c812d42a3737985501c5cab5f2742d3c2308a`; 0.3.3 `be98ff30545084e26edaeabdab410f547cf7e6d5e3d13fa32e0c011b59dedbdf`). In-place update will be rejected. Future builds require a persistent, securely managed **Arknights-specific** signing key, rather than ephemeral CI debug keys.
- Phone testing of MediaProjection, controls, actual OCR accuracy and tag highlight positioning remains pending. Do not claim validated runtime behavior based on compile success alone.

## 2026-11-01 — 0.3.4: fix consent flow returning to assistant, not Arknights
- User video `4712.mp4` (7.2s, 2772×1280 landscape) shows Arknights Recruitment screen and floating ▶/⚙, then Android's MediaProjection consent prompt after ▶; during consent the display rotates to portrait and ends up showing the assistant's own MainActivity, not the game. OCR therefore cannot analyze the recruitment tags.
- Root cause in previous code: `ScreenService.togglePlayPause` launches `CapturePermissionActivity` with `FLAG_ACTIVITY_NEW_TASK` only; it shares the assistant app's default `taskAffinity`, so the main assistant task is foregrounded. This behavior is consistent with Android's documented task matching semantics.
- Manifest now gives `CapturePermissionActivity` distinct `android:taskAffinity="com.example.arkrecruit.capture"`, excludes from Recents, handles orientation/screenSize changes, and retains translucent no-title UI.
- Service passes actual current orientation measured from display to the consent activity, which requests landscape or portrait as appropriate rather than relying on foreground assistant orientation. The temporary consent task is removed via `finishAndRemoveTask()` upon result, allowing previous game task to resume.
- Keeps user-initiated Android screen sharing consent and prior MediaProjection handling intact. Never programmatically selects or bypasses the consent confirmation.
- Version `0.3.4`, versionCode 7. Commit SHA of version bump: `cb1f5f2f9e2dcdf5f19764b553d006d1c2cf2d3d`.
- Device validation is pending: this is the code-level fix; must verify that after selecting 'Показать экран' the user remains in horizontal Arknights and the overlay scanner reports OCR tag count and highlights.
- Follow-up: after CI success extract and verify 0.3.4 APK, provide direct .apk (not GitHub sources). Note CI debug keystore changes each run and may require uninstall/reinstall; need a persistent signing key for seamless updates.

## 2026-11-01 — 0.3.4 verified CI artifact
- CI run: `41333307022` (source commit `1aa93d0fb71458d1c7ad6fac8fd4dc244bf0ca94`), finished with **success**. Artifact: `arknights-debug-apk` ID `13094222348`.
- Extracted `app-debug.apk` to `/mnt/data/Arknights_Recruitment_Assistant_v0.3.4.apk` (size 46,354,930 bytes; 44.21 MiB).
- Artifact ZIP and APK central-directory CRC validation succeeded. Manifest's binary XML string pool confirms version `0.3.4` (not `0.3.3`), and DEX includes updated `CapturePermissionActivity`.
- SHA-256 APK: `33ea9641ecea5c47259dad632132c9c99081f77b1f131d2cadcd1a3e664556dc`.
- **Confirmed signing mismatch:** APK v2 signer certificate SHA-256 for 0.3.4: `f2df7939fa5259d769fabb700a38206abbc7a2a6198f40e15b17ca6e0cbdeef7`. Previous 0.3.3: `be98ff30545084e26edaeabdab410f547cf7e6d5e3d13fa32e0c011b59dedbdf`. Thus in-place install over 0.3.3 cannot work: uninstallation or a consistently signed new release is required. Do not ever place a signing private key in the public repository.
- Confirmed on CI and static APK only. Real on-device return-to-game/navigation, MediaProjection, OCR, highlight accuracy still require Xiaomi testing.

## 2026-11-01 — v0.3.5: detect actual button borders, left-align recommendations
- Device screenshot `4718.jpg` shows **OCR 5/5 correct**, ▶ became ⏸, and assistant correctly recommended `Medic + Ranged` but green fill extends beyond actual game button edges; operator panel is offset approximately 200 pixels from left edge.
- Problem: previously `Markers.onDraw` approximated the tile width/height from detected glyph height and placed it around the OCR text box. OCR returns text boxes **not the dimensions of game buttons**.
- New modular `TagBoundsDetector.java` searches the current MediaProjection bitmap for strong dark/light transitions around each OCR label: paired left/right and top/bottom edges, with basic aspect/shape checks; recognizes the whole rectangle, fills it without strokes or rounded corners. No screenshots saved. On-device behavior needs confirmation across other skins/tags.
- For tags with insufficient image contrast or obscured edges, size falls back to median dimensions of successfully detected neighbouring tags; only when none detected does it use the prior text-based estimate. Logic lives in `ScreenService.processText` while the bitmap is still available, and the existing OCR recognition/engine are retained.
- Validated detection concept on the user screenshot at 1536×709 pixels: actual **Medic** and **Ranged** tags approx. x=670..815, y=353..402 and y=424..473 respectively; previous guessed rectangles overshot right. This is offline image measurement, not a phone runtime validation.
- Recommendation panel `WindowManager.LayoutParams`: x changed from 65 dp to **3 dp** at LEFT gravity, almost flush to edge. Its width unchanged; at the screenshot scale, this frees the game's first tag column from panel overlap.
- VersionCode 8 / versionName **0.3.5**, commit `62a2e3f07ea7046639c98c64e6413afcc921b8ec`.
- CI build required to validate Java/Android integration and generate APK; real Xiaomi screenshot needed after install to verify pixel alignment and selected/disabled tag states. Existing MediaProjection and calculations intentionally preserved.

## v0.3.5 built APK verification
- Latest source+checkpoint run: `41336688636` — **success**, artifact `arknights-debug-apk` ID `13095587057`. Source commit of this build: `7d7835d9d9c1cc1f900409ef61a7b170cf0f9310`.
- Extracted `app-debug.apk` from the uploaded GitHub artifact to `/mnt/data/Arknights_Recruitment_Assistant_v0.3.5.apk`. Artifact ZIP and APK ZIP CRC passed; APK manifest contains `0.3.5`; DEX contains `TagBoundsDetector`.
- APK size: 46,354,926 bytes. SHA-256: `8f95abfa864acf7571bc2312b8556a24ce4dd6da01048f3e287d899a7388fb06`.
- Signing certificate fingerprint: `e9955180127ac4410b25c5f4be266b7021e3b11c9d81b0da00099ae3b40e9feb`, **not the same** as 0.3.4 (`f2df7939fa5259d769fabb700a38206abbc7a2a6198f40e15b17ca6e0cbdeef7`). In-place update over 0.3.4 will fail. Must implement an external persistent app signing key securely for subsequent versions; never commit private signing keys.
- Offline edge-check on user screenshot with centers approximating the actual tag labels: Medic button ~x669..815, y353..402; Ranged ~x669..815, y424..473. Live in-game pixel accuracy still unverified; request new screenshot after installation.

## 2026-11-01 — 0.3.6 persistent highlighting and movable compact recommendation panel
- User supplied screenshot of Arknights recruitment after 0.3.5. Tag recommendation and OCR 5/5 work, but highlights periodically disappear, and recommendation overlay is too large and cannot be repositioned.
- Found reset path: `ScreenService.processText` cleared `inRecruitment`, recognized tags, and recommendation after only **two** transient OCR misses. The blank-frame diagnostic also cleared immediately.
- Changed invalid/missing/black-frame handling to **retain the last validated tag selection and highlighted rectangles** during intermittent OCR failures. Clear on manual pause, display orientation change, or after multiple scans with positive evidence of leaving the recruitment screen; replace when a new valid (3–5) tag set is recognized. This favors stable highlighting without guessing unknown tags. Need test on actual game when leaving recruitment.
- Redesigned floating recommendation panel: width **192 dp** (was 244 dp), collapsed by default. Header `⠿ Рекомендация` serves as a touch drag handle. `Операторы (N) ▾` expands a 150-dp scrolling list. Saved fractional screen position in `panel_frac_x` and `panel_frac_y` preferences, restored when overlay is recreated and clamped on orientation changes. Left-side initial position at 3 dp.
- In `RecruitmentEngine.Result.description`, removed the final line `Вероятности не опубликованы`. Detailed result rarity and operators remain.
- Version: 0.3.6, versionCode=9, source update commits `ecb09d459d428a86fa453ebfed57df1f6aacc91d`, `4c3a1d624136728abc7f2eec9c825a14b5103bac`, `2b3b8e127018d5875e3dd48f6ce9a8ffe2712b31`.
- CI build and APK validation pending; do not claim on-device validation for this version until tested on Xiaomi.

## 2026-11-01 — v0.3.6 build and artifact verification
- Successful GitHub Actions run `41346930596`, on commit `2b3b8e127018d5875e3dd48f6ce9a8ffe2712b31`, produced artifact `arknights-debug-apk` (ID `13099428810`). The subsequent commit `46fab7d02e7d56e2459696e991cd1c228bdffe42` updated DEV_NOTES only, not app code.
- Extracted `app-debug.apk` into `/mnt/data/Arknights_Recruitment_Assistant_v0.3.6.apk`; ZIP CRC checks passed, binary manifest includes version `0.3.6`, classes DEX include new panel position key `panel_frac_x`; no `Вероятности не опубликованы` string remains in DEX.
- APK size 46,354,930 bytes (44.21 MiB); SHA-256 `7f0ed5552a91b1e1bb4d5819bbe554efbb53cc673ca528904974059e97f1af6a`.
- Verified APK Signing Scheme v2 certificate SHA-256 fingerprint: v0.3.5 `4f8c72e8641710fa1e9d6cc114a1175534b48641d98c6913ed8151bc919ff303`; v0.3.6 `711a8834ca17db791e8b35b3e0703dda2b63a31859b96611f45f7446eb6b4239`. Mismatch means direct installation over v0.3.5 will be rejected; requires uninstall and reinstallation (with settings reset) until stable app-specific signing is configured securely. Do not commit private keys to GitHub.
- This verifies CI/source/artifact integrity only; actual pause/panel dragging/highlight persistence on user's Xiaomi remains to be tested.


## 2026-11-01 — 0.3.7: content-height panel and stable physical button detection
- Repository `wwwrocket065-eng/Hello_World_`, actual working branch `arknights-recruitment-starter`, project `arknights/`. Starting revision `cf38ad6a14d5a431d7e39b2bd1bf4f52eb0c3d9e`, source version 0.3.6 (9).
- User screenshot `4735.jpg` shows a huge empty operator list, literal backslash-n in the text, Supporter highlight shifted right, missing DP-Recovery highlight. User reports highlights disappear/reappear partially.
- Operator ScrollView now measures content height with AT_MOST cap (150dp / half screen); two operators occupy only their text height. Corrected escaped newlines to actual line breaks. Overlay height remains WRAP_CONTENT and clamps to screen after layout.
- OCR ignores actual screen-space rectangles occupied by assistant panel, controls and settings. This prevents feedback from the assistant's own repeated tag names. Candidates require detected physical button edges; removed text-size guesses and neighbour-size estimates.
- Border detection chooses the nearest sustained high-contrast edge, rejects asymmetric / non-containing rectangles, and samples outside label glyphs. Prevents selecting a neighbouring button or the edge of the assistant's own translucent fill.
- New TagGrid validates the five physical buttons as a consistent three-column / two-row group. Only complete validated snapshots replace the current tag list/recommendation. Partial scans retain the ENTIRE previous snapshot, preventing two highlights from collapsing to one. Clear still occurs on pause, rotation or sustained positive main-menu recognition.
- Marker coordinates now map capture pixels through physical display dimensions and subtract actual overlay screen origin, avoiding inset-induced scaling shifts. Snapshot dimensions are committed with its rectangles; stale OCR results across pause/rotation are rejected by an epoch.
- Guarded Bitmap recycling when createBitmap returns its input (no row padding), avoiding recycled-frame failures.
- Local Java regression tests passed: nearest edges under partial green fill, five-tag grid, rejection of 3-tag partial snapshot, own-panel-like scattered boxes and duplicate positions. Same Java detector checked against the private screenshot: Sniper (507,354)-(649,400), Medic (671,354)-(814,400), Supporter (835,354)-(978,398), Starter (507,425)-(649,471), DP-Recovery (671,425)-(814,471), within 3px of manually measured visible borders. Screenshot is NOT committed.
- CI runs geometry tests plus existing recruitment engine smoke tests before assembleDebug. Version 0.3.7 / code 10. Phone runtime testing remains pending; static screenshot tests do not establish live Xiaomi behavior.
- Build command: Gradle 8.11.1, JDK17, Android SDK35, `cd arknights && gradle --no-daemon :app:assembleDebug`. Workflow `.github/workflows/arknights-build.yml` triggers on branch push.
- Known signing limitation retained: CI generates an ephemeral debug signer; updating over previous APK can require uninstall/reinstall. No private signing material is stored in source.
- Next checkpoint: inspect CI for this exact source revision, extract APK, verify version/CRC, deliver and collect real-device feedback.

## 2026-11-01 — 0.3.7 CI and APK checkpoint
- Source commit `801531a8a0e4bc05ab07b1e47ac42504cacd3722`; Actions run `41349354730` completed successfully. Engine smoke, button geometry / partial-grid regression, data API validation and assembleDebug all passed.
- Artifact `arknights-debug-apk` ID `13100755768`; ZIP and APK CRC checked. Manifest version 0.3.7 verified; assets/DEV_NOTES.txt included.
- APK `Arknights_Recruitment_Assistant_v0.3.7.apk`, 46,383,012 bytes; SHA256 `2a4b85f64340c82fb48db6ffaff85780ce8d7b27a71f24b099622e1904d9aaed`.
- V2 signer certificate SHA256 `849b4b0287d05c0b044594f8f9b5e6c556d0fcc70a526e42f4051aa69a3dc952`, differs from v0.3.6. User informed uninstall/reinstall is needed, resetting settings.
- Durable APK identity: `libfile_f7aed1a0c7948191afcca8bf8e328ebe`.
- Next: real Xiaomi Android16 testing of content-fit panel, aligned full button highlights, stable highlight set across repeated OCR scans. CI and screenshot tests do not prove device runtime behavior. Do not revert or redo completed changes after context interruption.


## 2026-11-02 — 0.3.8 all combinations, honest probability display and Support fix
- User asks for all available outcomes with rarity probabilities beside labels, including 3/4/5 stars instead of only the previous best recommendation.
- Critical database bug: API profession code SUPPORT means class Supporter, but the specialization tag Support means a different tag. The old addTag switch was used for BOTH fields and mislabeled Elysium/Zima as Supporter; this produced impossible DP-Recovery + Supporter recommendations. New pure OperatorTagMapper separates these namespaces. Cache schema 2 rejects the unrepairable old cache; first use needs a successful database download.
- RecruitmentEngine.combinations enumerates all viable zero-to-three-tag selections from the visible five tags, including an explicit no-tags baseline. best uses the same ranked list. Impossible mutually exclusive combinations have no matches and are omitted.
- Floating panel now has Все варианты (N), with clickable combinations. Choosing one updates highlight and candidate operators and is retained across identical OCR snapshots; a changed five-tag set resets to best. Operators and combination lists expand separately to keep the panel bounded.
- Every selected combination shows exact-rarity rows. 0% / 100% only follow from the eligible pool, explicitly conditional on 9h and preservation of all selected tags. Mixed rarity pools show шанс неизвестен; minimum-rarity-or-higher is conditional 100%. Individual operator chances remain unknown unless the candidate set has exactly one member. No uniform-per-operator or renormalized weights are fabricated.
- Research: https://arknights.wiki.gg/wiki/Recruitment lists approximately 87%/11%/2% for 3/4/5-star outcomes without a guaranteed rarity at >=7h40m, explicitly citing community estimates. These are NOT confirmed conditional probabilities for arbitrary chosen tags, so the app does NOT substitute them into tag-specific results. Official https://www.arknights.global/news/2868 documents Top/Senior Operator guarantees at 9h, not a full per-combination probability table.
- Relabeled chance-priority switch to maximum rarity: its scoring compares possible rarity and does not calculate an actual probability. Main manual result description uses the same probability semantics.
- Corrected short-time pool exclusion of five/six-star operators; existing integer hours API is used only for 9h UI and 3h smoke tests. Automatic game-time OCR is NOT implemented; all overlay results explicitly state 9h.
- Local engine regressions passed: profession vs specialization, impossible DP-Recovery+Supporter, lower-rarity alternatives, baseline, unknown mixed-pool chances, conditional 0/100, no 5-star below 4h. Existing geometry unchanged.
- Version 0.3.8 / code 11. Pending CI build and artifact verification; runtime Xiaomi testing still required.
- Existing signing limitation remains: ephemeral CI debug signer differs between builds. Never commit private signing keys.

## 2026-11-02 — 0.3.8 build and delivery checkpoint
- Source commit `6f153f4e61229367caaa1c1e97b6e43f38ecf675`; successful Actions run `41402721034`, artifact `13121454331`. Engine regressions, geometry regressions, live API validation and assembleDebug all passed.
- APK version 0.3.8, 46,384,896 bytes; SHA256 `ac1e1705fe28c5f00428b2bcbb239d094422f49b776e83fc32a8a639d35b0c94`. Artifact and APK CRC passed; manifest version verified; DEX contains OperatorTagMapper and chanceForRarity; DEV_NOTES asset present.
- V2 signer certificate SHA256 `cda2c51d8a7545180753ec57b50a838902e759d73ff10eaf6c701334dabe311c`, differs from 0.3.7; uninstall/reinstall needed, settings reset. Durable APK identity `libfile_73fce53170e48191864582706fbb7762`.
- Delivered all viable combinations and full eligible operator lists with exact conditional 0/100 only; other probabilities explicitly unknown. No exact conditional weights were found. Do not describe unknown rates as implemented numeric estimates.
- Next: Xiaomi test of variants selector, updated database and conditional probability UI. User may request empirical estimates; do not infer per-combination weights from the general 87/11/2 population estimate.


## 2026-11-02 — 0.3.9 simplified panel and selected-tag OCR contrast
- User reports selecting a tag sometimes causes it to stop being recognized; screenshot 4739 shows 4/5 retained while Medic is blue. Cause is consistent with lower/changed text contrast but has not been device-proven.
- Removed the entire All variants interface and its selection state; engine still ranks combinations internally for the best recommendation. Removed operator counts from the panel toggle. Removed requested bottom 9h/tag-preservation caption and its asterisk markers, preserving the underlying pool semantics. No numerical probability model was invented.
- TagContrastNormalizer prepares OCR pixels ONLY within previously confirmed physical button rectangles. Median background samples select light-text or dark-text polarity; normal dark, selected blue and rare yellow buttons become black text on white background. The original capture is preserved for edge detection; full validated snapshots still prevent partial OCR from erasing highlights. Known tile coordinates clear on pause, rotation and confirmed departure from recruitment.
- Local Java tests passed: existing engine and screenshot geometry tests; new three-state contrast test (dark, blue selected, yellow) confirms equal text contrast without modifying original pixels. Live ML Kit/Xiaomi behavior remains unverified.
- Probability research follow-up: https://www.reddit.com/r/arknights/comments/1r5cbkb/recruitment_data/ records 3200 9h recruitments but explicitly did NOT log tag occurrence/dropping. Its outcome frequencies are not conditional odds for the user's chosen tags. A commenter links a six-year spreadsheet with tags, but the spreadsheet could not be read via the available lookup tool. No per-operator estimate is added based solely on pool counts.
- Version 0.3.9 / code 12; build pending. Source baseline 032249a35372be7e7cd531392c2773f35328369a.
- User-facing panel intentionally omits the removed 9h caption. Engine still evaluates 9h, and 0/100 values still assume selected tags apply; this is an underlying limitation, not a new exact game probability model.

## 2026-11-02 — 0.3.9 CI and APK checkpoint
- Source commit `0e8353e95ebb1f82320e9647a380ca2db0b1d94f`; Actions run `41405832794` successful, artifact `13123357432`. Engine, geometry/contrast regression, API validation and APK build passed.
- APK 0.3.9, 46,386,356 bytes; SHA256 `49f87851ce58d559c47ddcacd050da417448b9677af188dacf1a5cefc30e50f2`. ZIP CRC and manifest version passed; DEX contains TagContrastNormalizer and does not contain removed All variants / tag-preservation caption strings.
- V2 certificate SHA256 `00b4ce0865ad77f2a292c5afe1549fd290216a48bf356892e3fca6c920afc539`, differs from 0.3.8. Uninstall/reinstall required with settings reset. Durable APK identity `libfile_8e59f483815c8191ab1633080ceeb639`.
- Pending: actual selected-tag OCR test on Xiaomi; full conditional operator probabilities remain unavailable. Counts alone do not establish equal odds. No fabricated empirical rates added.


## 2026-11-02 — 0.3.10 fast scene gate and enlarged per-button OCR
- User screenshot 4751 shows an Expedited Plan confirmation dialog while stale Guard + AoE rectangles and a 0/5 saved recommendation remain. Selected tags also still lose OCR in 0.3.9.
- Root cause confirmed in source: OCR throttle 1300ms in recruitment / 2500ms elsewhere; stale snapshot reset required specific main-menu words and never recognized this dialog. Contrast normalization alone did not remove the dependency on fresh text-height-based edge detection.
- New RecruitmentScreenProbe reads sparse pixels directly from the Image plane at a 33ms target interval, independently of OCR busy state. Checks original physical button edges and dark/blue/yellow interiors; own translucent green highlight over a white dialog does not qualify. Two consecutive absent samples hide panel and markers and invalidate outstanding OCR. Geometry is retained only to detect return; old labels/recommendation clear on exit.
- Scene gate TARGET is at most ~30 checks/s depending on delivered frames and device scheduling; no measured 30fps phone guarantee. OCR target interval 250ms while buttons are present (at most 4 starts/s, serial busy guard), 1000ms while away with known geometry, 500ms during first discovery. Heavy OCR is NOT run 30 times/s.
- New TagOcrInput crops the five confirmed button rectangles into an enlarged 460x720 black-text-on-white strip. OCR boxes map back to original physical coordinates; a positive physical probe allows using the already measured button rectangle instead of re-detecting its edges from changed label height/colour. Original capture retained for physical tests.
- Partial scans retain old identity only while the independent scene gate confirms physical buttons; if a newly recognized tag differs from the snapshot, stale recommendation clears until a complete five-tag set arrives. Atomic generation counter rejects OCR completing after exit, pause or rotation.
- Local Java regressions pass: original screenshot grid present; 4751 expedited dialog WITH old green fills rejected; 4739 selected blue Medic retained; enlarged-strip coordinates map back exactly; contrast, geometry and engine regressions retained. Private screenshots NOT committed.
- Device limitations: this sparse gate is tested on supplied frames, not live Xiaomi. Several assistant windows occluding multiple buttons, other skins or changed UI geometry may require tuning. Initial discovery still uses full-frame OCR; automatic game-time/actual user-selection tracking remains outside this change.
- Version 0.3.10 / code 13. Source baseline 2734ad557b3768e353ff6921cd1bd1605ba69494. CI and artifact verification pending.

## 2026-11-02 — 0.3.10 verified build checkpoint
- Source commit `f74f676f717f09343298517aa905e3a5b07df405`; successful Actions run `41408199130`, artifact `13123554756`. Engine smoke, geometry/contrast/probe/coordinate tests, live API validation and APK build passed.
- APK version 0.3.10, 46,387,900 bytes; SHA256 `e909ec24101aa17e48196d8e180221b7c4b2cf7f23e044a7c4d6f151c5461937`. Artifact and APK CRC checked, manifest version confirmed; DEX includes RecruitmentScreenProbe and TagOcrInput; notes asset included.
- V2 certificate SHA256 `018cf5c97ea381ccad92f5c094a7476631af1acd3a543d09cea67f5fa9ac4a66`, differs from 0.3.9. Uninstall/reinstall required with settings reset. Durable APK identity `libfile_4c92c7e20e80819196a09a9ac978523b`.
- Pending Xiaomi runtime test: selected tag retention, exit to accelerated recruitment dialog clearing overlays, return detection, real scene-check and OCR throughput. Sparse gate and coordinate tests are confirmed on supplied screenshots only, not measured device fps. Do not label 30fps as measured.


## 2026-11-02 — 0.3.11 compact recommendation, full reachable operator list and target selection
- User screenshot 4756 asks to remove probability rows (3-star zero, 4/5-star unknown), keep only best recommendation, and make Operators show all outcomes reachable using the five available tags so a specific operator can be pursued.
- Result.raritySummary now returns only exact rarity or a compact minimum/maximum range. Removed all percentage and unknown-chance output, including individual operator suffixes; manual result descriptions use the same clean format.
- RecruitmentEngine.reachable unions and deduplicates operators across every viable NONEMPTY selection of 1-3 available tags, sorted by rarity descending then name. This differs from the previous list, which showed only recommendation.operators. The no-tags baseline is excluded: an operator with no matching available tag cannot be specifically targeted from this set. Top Operator and time-based rarity restrictions still apply.
- Each operator row is clickable. forTarget chooses a valid available-tag combination with the smallest candidate pool, then higher minimum rarity and fewer tags. This is a pool-narrowing rule, NOT a claimed measured probability. Panel shows target name and required tags; highlights follow that choice. No automatic taps in Arknights.
- A compact ЛучшаЯ рекомендация / Лучшая рекомендация action restores the normal ranked best result. Target is retained while reachable after OCR/DB refresh; unreachable target resets automatically. No All variants selector and no operator counts are reintroduced.
- Operator rows rebuild only when names/rarities/target change and preserve scroll position. Rows hide when the recommendation is invalid, avoiding showing a previous list during a changed partial snapshot.
- Local engine regression passed: best remains 5-star Mayer while list also includes 4-star Beanstalk and 3-star Kroos; deduplication; six-star exclusion without Top Operator; exclusion of operators with no matching tag; selecting Kroos outside the best combo; narrowing Mayer; absence of probability text.
- Screen probe, enlarged OCR, capture lifecycle and permissions unchanged. Version 0.3.11 / code 14. Source baseline b1a6bc6e0232850d497fe3cb854c1074824c7b20. CI/APK and Xiaomi target-selection testing pending.

## 2026-11-02 — 0.3.11 build/delivery checkpoint
- Source commit `06b3f9302216aa5a69b393c4560dc62cba39055c`; successful Actions run `41410027458`, artifact `13124828458`. Engine/target-list regressions, existing geometry/contrast/probe tests, API validation and APK build passed.
- APK version 0.3.11, 46,389,316 bytes; SHA256 `eec0be7b52ab91177455c1e99f8e4d4c4ed10c02963db9df7f3d55e115b9db78`. Artifact/APK CRC and manifest verified. DEX includes forTarget/reachable and contains no unknown-chance or 100-percent output string; notes asset present.
- V2 certificate SHA256 `ed87c591d1ed9c85372a67f795db52afea9fbfcbab2eea143986e1599c52ffa6`, differs from 0.3.10. Uninstall/reinstall required with settings reset. Durable APK identity `libfile_789e9e58b498819199cb2eb3e9ac952c`.
- Next Xiaomi check: Operators includes candidates outside current best combination; tap candidate updates goal/tags/highlight; best-recommendation action returns to auto; list scroll and compact layout. Runtime behavior still needs device validation.


## 2026-11-02 — 0.3.12 compact portrait rows and honest overlay-permission routing
- User requests removal of cyan Лучшая рекомендация text, elimination of large operator-row gaps, tiny portrait between rarity and name, and direct permission toggle opening instead of manually locating the app. Video 4754 confirms Android currently shows the correct overlay-app LIST, followed by the app-specific toggle after manual selection.
- Removed the auto-recommendation action entirely. Clicking an already selected operator still clears the target and restores auto ranking. Other selected-row accent and main recommendation heading remain.
- Replaced padded text rows with compact 22dp horizontal rows: rarity, 20dp square portrait, name. Removed 7dp top/bottom spacing per row; operator header spacing reduced. No counts or probability text reintroduced.
- New build-time tools/prepare_portraits.py downloads the current Global recruit pool and authentic client avatar PNGs from yuanyan3060/ArknightsGameResource pinned at 0f5efc6352337e2d63f81c0d2ca6d469b2ce3d96, converts to 72px WebP, and embeds all current portraits + name index in APK. EN character-table fallback maps IDs. Original image rights remain Arknights/Hypergryph; source notice included. Runtime list requires no image network fetch. Missing downloads fail the build; checked examples include Steward, Midnight, Plume, Popukar, Vanilla, Mayer, Beanstalk.
- New OperatorPortraits loads packaged avatars on two background threads with 64-entry memory cache, validates view tags before callbacks, and closes with service. Future DB additions not in this APK can have an empty portrait frame until assets are refreshed in a later build.
- Permission research (primary sources): https://developer.android.google.cn/about/versions/11/privacy/permissions documents that Android11+ ACTION_MANAGE_OVERLAY_PERMISSION ignores package URIs and requires app selection. AOSP Settings manifest protects AppDrawOverlaySettingsActivity with INTERNAL_SYSTEM_WINDOW: https://android.googlesource.com/platform/packages/apps/Settings/+/master/AndroidManifest.xml . Ordinary app cannot promise a direct toggle on Android16.
- New OverlayPermissionNavigator checks whether the explicit app-specific Settings activity is exported AND accessible to this caller on the OEM build; only then launches it. Otherwise uses public overlay app list, naming our app in a toast; app-details last fallback. No protected-permission bypass, root, accessibility service, Xiaomi unrelated Other permissions route, or extra dangerous permission. Added scoped package visibility for com.android.settings only. Both Start and settings Permission button share this handler.
- IMPORTANT: direct navigation remains dependent on OEM accessibility; not phone-validated. Do not claim it is fixed on user's Xiaomi or that a guaranteed direct toggle exists. Public fallback intentionally remains when Android blocks the direct screen.
- Version 0.3.12 / code 15; source baseline 01aea5b466be422966dad389c5234c1302d97e34. Pending CI portrait completeness, APK validation and Xiaomi layout/navigation check.

## 2026-11-02 — 0.3.13 multiple operator goals and tag colour dots
- User authorizes discussed multi-target UX after voice-transcription correction.
- Goal line always present within recruitment panel: Maximum rarity when no manual goals. Automatic ranking uses possible rarity then guaranteed rarity; removed obsolete priority toggles to avoid conflicting modes.
- Operators list now includes entire validated Global recruitment roster, not only reachable subset. Reachable by current nonempty tag combinations = yellow; selected = green; unavailable/unselected = muted. Existing portraits and compact rows preserved.
- Selecting another operator adds it; clicking an already selected operator removes only it. LinkedHashMap preserves goal order and stable per-goal HSV colour. Goals, colours and colour sequence persist in preferences. Unavailable goals remain with no-tags label and no circles.
- Goal names carry individual colours. Existing main highlight stays the automatic recommendation; additional bordered colour dots above each tag's top-right corner identify every goal whose independently narrowed valid plan uses that tag. Multiple dots wrap above the tag. Each goal plan has <=3 tags. Do NOT treat the union of differently coloured dots as a jointly valid selection or claim measured odds.
- Multi-target plan regression added to engine smoke. No false probability numbers, operator counts or All variants interface restored. Capture, scene probe, permission routing and offline portraits unchanged.
- Version 0.3.13 / code16; baseline 9df11333d076b70a07bc795c4852a4b3e79f33f6. Build and device UX validation pending.

## 2026-11-02 — 0.3.14 target management popup and reachable-only filter
- User requests neutral single goal without circles; current-tag-only filter near panel switch; selector label Выбор цели; tap a selected goal name for styled remove/clear popup.
- Single target: neutral name with no bullet/dots, primary fill follows that target's valid plan. Multiple targets retain stable colours and per-goal dots. Stored colours are retained internally when reducing to one so remaining goals regain their colours when more are selected.
- Added persistent only_current_tags setting both on main UI and floating settings beside panel toggle. It filters selector rows to currently reachable operators (including selected reachable rows); unavailable selected goals remain in goal summary for management.
- Renamed selector to Выбор цели, no counts. Goal names use individually clickable spans, neutral when alone. Stable display-key prevents OCR refresh from replacing text while tapping.
- New centered overlay popup uses UiStyle surface, border, rounded controls and X; actions Remove NAME from goals / Clear all goals. Tap outside closes without changes. Removal/clear persists and recomputes plans; clearing restores automatic Maximum rarity goal.
- While management popup is open, frames are drained without OCR/probe because our modal occludes game buttons. Epoch guards reject in-flight results; closing forces immediate rescan. No new capture session or permissions.
- Version 0.3.14/code17; baseline 913e6c234a8222f08e436003ee74111f326d3e5d. CI and device interaction tests pending.

## 2026-11-02 — 0.3.15 time-aware ranking and faster confirmation-panel dismissal
- Steering during 0.3.14 build: accelerate hiding on lower cancel/blue confirmation controls and account for current recruitment time including intermediate values. 0.3.15 combines all 0.3.14 goal-management changes; do not distribute the superseded 0.3.14 artifact.
- Added RecruitmentClock parsing HH:MM:SS from the actual timer ROI, 60..540 minutes in 10-minute steps. TagOcrInput appends the timer to the enlarged OCR strip; initial full-frame scan restricts timer recognition spatially. Unknown time is shown explicitly rather than silently assuming 9h. UI recommendation includes detected H:MM.
- Engine combinationsAtMinutes applies exact rarity cutoffs at 240 and 460 minutes; whole-hour methods retained for compatibility/manual tests. Yellow availability and goal plans now use detected time.
- Sparse scene checks target a 16ms minimum interval, still contingent on actual delivered frames. Confirmation/cancel button pixels are sampled independently. Once their normal appearance was seen, disappearance/state change hides immediately on one probe and suppresses reappearance for 300ms. Geometry loss also hides after one absent probe instead of two.
- This DOES NOT intercept touches in Arknights. A normal pass-through overlay cannot monitor all foreign-window taps without additional input/accessibility privileges. The implementation reacts to visible button changes; a press that produces no visible change is only detected when the screen changes. No new accessibility service or dangerous permission added.
- Tests passed: timer 1h/7h40/9h syntax, rejection out-of-range, minute boundaries 60/230/240/450/460/540, original bottom controls positive, expedited dialog controls negative, existing geometry/selected-Medic/contrast/strip-coordinate regressions.
- Goal popup also closes on Back and pauses its own scene/OCR checks until dismissed; close forces rescan. UI behaviour still requires Xiaomi validation.
- Version 0.3.15/code18; builds from 0.3.14 source 267daa4b436bde5d83d983ab40eecf1c5104b8a4. CI/APK pending.

## 0.3.15 verified combined delivery
- Final source be68fca63459671df682c0455689787cea88ecd1 (includes split-block / colonless timer token handling). Run 41421500824 SUCCESS; artifact 13129069250. Tests, API, portraits and APK build passed.
- APK 47,040,112 bytes; SHA256 23a01bf6b07ee9ece382ff4565e194c9ec929c6d56ade8302b4e8ffb2f76787e. ZIP CRC, manifest, timer/filter/menu DEX and 158 portraits checked.
- Durable APK libfile_4c1c4b154e148191812eaa8a3854bd66. Includes neutral single target, filter, Выбор цели label, styled goal removal/clear menu, timer-driven pools and lower-button visual dismissal. Actual Xiaomi timer OCR / popup touch / dismissal latency require device testing. No global touch interception or added accessibility permission. Ephemeral signer can require uninstall/reinstall.


## 0.3.16 — overlay occlusion and compact recommendation
Own panel, controls and settings are excluded from sparse scene and lower-button evidence. Occluded evidence is unknown, preserving prior state; real visible exits remain detectable. Moving/layout overlays briefly defer probes (100 ms), including gear drag. Added regression tests for partial/full occlusion and real scene exit. Goal default: turquoise «Макс цель». Recommendation yellow: time (min★–max★) · tags. Native Xiaomi validation pending; no measured latency claim.


## 0.3.17 — target duration and exact label colors
Recommendation plans are independent of the current timer: 9:00 for maximum rarity and 3–6 star targets, 3:50 for robots, 7:30 for 2-star targets. Result stores exact minutes. Reachable target chooser unions valid durations, keeping robots selectable with a 9h dial. Only «Выбрать:» is yellow; duration, rarity and tags are white. Only «Цель:» is turquoise, default «Макс цель» white; individual multiple-goal colors preserved. Rarity interval describes selected tags at recommended time; game Possible Rarity is a general time-only interval. Added duration/low-rarity availability tests; engine passes locally. 0.3.16 occlusion fix retained. Device testing pending. Signing remains ephemeral CI debug key.


## 0.3.18 — compact panel labels and chooser defaults
Default goal renamed «Макс редкость». Header «Рекомендация · Global»; routine OCR row hidden (recognition unchanged). Chooser «Выбор цели (N)» counts the actual filtered/unfiltered list; 15dp rounded triangle rendered as font-independent Drawable/ImageSpan, accessibility label includes state. Setting renamed «Цели: только текущие теги» in both screens, white text; filter defaults true for unset preferences, panel remains default true. Saved user choices preserved. 0.3.17 target durations and 0.3.16 occlusion handling unchanged. Device validation pending.


## 0.3.19 — three chooser modes and independent Global/China data
Settings first row is discrete Global/China selector, then panel toggle, then 3-position chooser: Все операторы / Текущие теги / Выбранные операторы. Fixed single-line «Выбор цели:» prefix and marquee value; no second line. Legacy boolean filter migrates to first/second mode; default current tags. Selected-only includes selected operators in current server pool even when unavailable under visible tags; empty selection renders no rows or message, arrow still toggles. Count uses same centralized TargetListFilter; tests cover all modes and empty state.
China uses client recruitDetail-specific pool (Kengxxiao CN gacha/character tables), not all released operators. Build validates all listed names resolve exactly, bundles CN roster and union portraits; runtime updates with separate recruit_china cache and tagId mapping to EN engine tags. Global existing EN API/cache remains separate. Names align by ID with EN client names; future CN-only entries use appellation. Generation guard rejects delayed previous-region callbacks. Header preserves six-dot drag handle; Global muted blue, China muted red, selector tint follows region. Region selector changes roster/plans immediately. Existing English tag OCR retained; Chinese-script OCR is not added by this roster feature.
Target timing remains 3:50/7:30/9:00; no fabricated probabilities. CI and native device verification pending; ephemeral debug signing limitation remains.


### 0.3.19 verified delivery checkpoint
Source 8edb9841ae804ae3cc420dac7f74a5939023a03b; CI run 41432884999 success (engine/filter smoke tests, geometry regressions, CN roster extraction and portraits, assembleDebug). CN pool163 / offline portraits163, APK47064458 bytes, SHA256669043582a4fc31e8a040974211bf7ba4fb3cfe7e7cb84c8dec75d368b18c256. Saved APK identity libfile_8a3863285aa0819190fa8d8d9bde0517. Native Xiaomi16 interactions, marquee/slider and live CN network refresh remain unverified. Build-time CN snapshot validated from client data; English tag OCR remains unchanged. Settings selector is first row as requested. No source code changed after successful build.


## 0.3.20 — tap buttons and region label colors
Replaced both discrete sliders with equally sized 82x42dp tap-to-cycle buttons in shared RecruitSettings. Server stays first: fixed white «Сервер:», dynamic Global muted blue #8B9FBD / China muted red #BE888D; matching muted button backgrounds and ↔ glyph. Panel header uses same muted server text colors. Chooser cycle selected (stored2, visual1/3) → current tags (stored1, visual2/3) → all (stored0, visual3/3, full accent fill); defaults current tags without overriding saved choices. Fixed white prefix and single-line marquee mode label retained. Stored filter semantics/data/recognition unchanged. FilterCycle regression passes locally. Build/native validation pending; debug signing limitation unchanged.


### 0.3.20 delivery checkpoint
Source ac0cc7a7af5208d11a0be63fe0890c10ab26ae93; CI run 41434908598 success, all existing checks and APK build passed. APK47065118 bytes, SHA25626fb02b53056032a931b89011697795ecfaaaff2fe5a0510a14d89f45508c09c. Saved libfile_1c1e5222cea08191b9a59104ca197379. User reiterated chooser must cycle on tap and selected/all swapped; implemented selected→tags→all. Native Xiaomi UI unverified; debug signing limitation remains.


## 0.3.21 — compact chooser labels and equal settings controls
Chooser labels shortened to «Выбранные» → «Теги» → «Все» after fixed «Выбор цели:». Stored values, cycle and default unchanged. All three settings controls (server, panel visibility, chooser) now share the same button factory (82x42dp, 13sp symbols, shared background/radius/padding); panel stock switch replaced by tap button. Panel preference listener updates overlay from main settings too. Calculation/data/recognition unchanged. Native layout validation pending.

User superseded previous empty selected-only behavior: show «Цели не выбраны» when selected-mode list is empty. Chooser count stays zero; label appears only while list is expanded, not in other modes. Included in 0.3.21 final source build.

Final wording per user correction: «Список операторов» replaces chooser title in both panel and settings/accessibility labels. Mode names remain «Выбранные / Теги / Все»; «Цели не выбраны» placeholder retained. Supersedes the briefly discussed «Выбранный оператор» wording.


### 0.3.21 final verified delivery
Final source a7bc1dcfef6daf3158b01358c5d6868ae5461f24; CI run 41436066230 success. Supersedes intermediate 0.3.21 builds; only final APK delivered. Size47065794, SHA256f4eb2b4964a3b31e29f7bb93d16683e5ea14ea449b9be0468d1b966115ab9e2b. Saved libfile_d2159eddc4dc81918205672830322666. Contains compact Выбранные/Теги/Все labels, equal settings buttons, final «Список операторов (N)» title with existing arrow and empty-selected message. Native Xiaomi layout still unverified; ephemeral CI signing caveat unchanged.


## 0.3.22 — restore actual switch appearance
User rejected rectangular arrow/check/fraction buttons: they intended tap-to-cycle switches with a round thumb. Replaced controls with CycleToggle capsule track and circular 20dp thumb, no symbols/text inside. Server and panel use normal 40dp width; ONLY the three-position operator-list switch is 60dp (1.5x). Same 42dp hit height, 14dp rail height, 140ms thumb transition; tap cycles without dragging. Fixed meanings/order/defaults, all labels/colors/count/empty message retained. Two-state accessibility reports Switch/check state. No recruitment/data/OCR changes. Native device validation pending. Do not reintroduce rectangular settings buttons or lengthen every switch.


### 0.3.22 verified delivery
Source 87ef31f0853933c7085b40be1883042945c5b11d; CI run 41443304470 success. APK47066370 bytes SHA256d6ede162988a741cd365981ca792bbc436cf4dc4cb0366255e0de9c3bfb571d5. Saved libfile_cccfa3c704e48191893ff28b298f0337. All native device appearance/tap/animation checks remain pending. User explicitly corrected: ONLY operator-list switch longer; server and panel normal length. Capsule switches restored, no rectangular buttons or inside symbols. No other UI/data behavior changed.


## 0.3.23 — server direction and partial three-state rail
Server visual positions reversed only: Global right, China left, stored selected server/default unchanged. Three-state operator-list switch paints a neutral rail and clips accent fill to thumb center: selected left/off, tags center/left half lit, all right/full lit (thumb covers end). Standard server/panel switches keep ordinary full-track tint. Widths40/40/60dp, colors, tap order and data unchanged. Native validation pending.


### 0.3.23 verified delivery
Source 26994a5ba402b2c2fc62e14a8b1c21bf03a9d906; CI run 41444396467 success. APK47066806 bytes SHA256056b01e737bb42ed6a0b1d166f09bda9a77d6652f600c433ceaeec6b94b144f0. Saved libfile_f6dcb99109d88191b8da23d1255cafba. Native screenshot validation pending. Global now right without changing selected server; list rail half-lit at tags. Widths unchanged. Debug signing limitation remains.


## 0.3.24 — nested highlight page and reference-inspired gold rendering
Full-width 50dp «Подсветка ›» entry retained in main and floating settings, rendered in the new gold reference style. Tapping navigates inside the same container/window to a highlight page, with back button; main Android Back also restores the previous content. No expanding dropdown or extra dialog. Page holds continuous hue/saturation wheel, brightness, «Прозрачность подсветки» (0 opaque,100 invisible), twinkle toggle, star haze strength, reset and live preview. Removed external opacity/reset controls. Weak navigation actions avoid retaining closed windows.
New HighlightStyle migrates once to reference gold #FFB600, transparency20%, haze35%, twinkle on; reset restores same. HighlightRenderer reconstructs reference design procedurally: layered gold glow with thin white rim, quiet diagonal bands, fixed-shape warm-white four-point stars with haze. Different deterministic pattern per tag. Stars animate opacity only, never shrink; protected OCR text box excluded from stars/bands. Uses actual measured tile bounds. Reuses halo bitmap/path, schedules marker animation at target33ms only while valid screen/recommendation visible; actual phone performance not measured. Images used as design references, not pasted opaque over game. Existing region/toggle settings retained. Native Xiaomi visual/occlusion/performance validation pending; no pixel-identical claim.

Video4790 reviewed locally: HyperOS push navigation. Full-width entry arrow is now trailing right (not inline next to text). Shared SettingsPageTransition pushes child page from right over old page parallax25%,280ms; pop reverses260ms. Back arrow plain left in page heading. Child has independent ScrollView; original outer scroll restored on return. Reset refreshes controls in place (no page transitions). Supersedes instantaneous navigation in first intermediate source. No video screenshots persisted or published.


### 0.3.24 final verified delivery
Final source f915cf5e5266abfd7fd1b763dda14760735aa2d4; CI run 41448227420 success, compile and existing engine/geometry/data checks passed. APK47084366 bytes SHA256b6dda69a4515c4a6eb84fdb65645ea652452153045054fe0cd01855fba6a8ee5. Saved libfile_52871938d27c819196d59f54a347c795. Only final video-navigation build delivered. Native Xiaomi animation, palette gestures, star performance and screen-probe interaction remain unverified. Gold effect is a procedural reconstruction of supplied design, not a pixel-identical copy. Signing limitation unchanged.


## 0.3.25 — spoken review corrections
Addressed factory-style feedback: stronger 3.6unit gold rim/1.6unit white core with layered glow; larger fixed-shape four-point stars and softer/smaller fixed haze. HighlightPattern stratifies18 stars into3 rows x6 with bounded jitter and per-tag seed, eliminating missing lower rows/clumps. Continuous opacity pulse72–100%, no shape/size changes. Removed rectangular text clip; continuous distance attenuation near letters prevents sharp cutouts. Tests cover bounds, exactly6 stars per row and pulse range/continuity over12s for Guard/DPS/Survival.
ColorWheel accepts DOWN only inside the visible circle; outside corners return false and remain available to scroll. Brightness and transparency retained; removed twinkle/haze controls, replaced with independent Frame/Fill/Stars round-thumb toggles default on; reset includes toggles. Animation and haze are built-in, obsolete preferences ignored. SettingsPageTransition now inherits host background instead of creating a lighter full-page rectangle. Main stays BACKGROUND, floating settings keep their own SURFACE. Native visual/touch testing pending; no native-test claim.


### 0.3.25 verified delivery
Source 6ee01d86bbff777f3ee439c94e267de5160b6f91; CI run 41454162159 success including new star-pattern/pulse regressions. APK47085194 bytes SHA256b069be75a5dcd6874bc04d2987b1f340e2bb41cb04e81c6dd1cf6b44afb51735. Saved libfile_3eb5d5b142948191b3e5f02d94a57abd. Native Xiaomi palette touch/scroll, background matching and actual appearance still pending; automatic tests are not native rendering validation. Signing limitation unchanged.


## 0.3.26 — follow-up spoken highlight review and completed entry placement
Recovered prepared local source commit e15268f from sibling workspace; not previously pushed/built. Compared against current remote3978fbe. Completed missing button relocation: «Подсветка» directly below «Запустить», same UiStyle primary color/radius/min-height/padding/font/margins, right-edge arrow, no effect/preview on entry and no redundant section heading. Same shared entry styling in floating settings.
Frame centerline now uses exact measured tile bounds (no2unit inset); gold/white core fully opaque. Stored opacity retained only for fill/stars/soft halo; transparency slider removed. Preview label «Пример», measured text bounds exclude overlapping stars. Preview reserves12dp external halo margin, preventing cut-off rectangle. Irregular cached best-candidate star scatter replaces rows, with independent per-tag patterns/sizes and broad coverage. Slow continuous baseline plus rare smooth19–31s peaks, no movement/scaling or binary flashes.90s continuity/bounds/spacing/upper-lower coverage regressions pass locally using JDK compiler module. Native visuals remain pending. CI build pending; no claim APK exists yet. User already requested complete APK; avoid redundant permission questions unless an actual active review rejection blocks upload.


### 0.3.26 verified delivery
Final source 840717d5a6d7704d599c809f7afdc22b9f48398e; CI run 41459134741 success. APK47086086 bytes SHA256581faa6bca00def53cbc10d9fca516e28510957eef8ef13ccebcb96da7c3727e. Saved libfile_411dd1975bdc8191898bc6db282d230b. Recovered unpushed source, completed missing button move/style and built without another user permission prompt. Native phone appearance/animation/halo validation remains pending. Ephemeral debug signing caveat unchanged.


## 0.3.27 — video4809 rapid navigation flicker, entry color/arrow, persistent stars
Inspected local video4809: repeated fast push/pop shows transient/misaligned pages. NewPage now INVISIBLE until measured and translated outside stage; reveal and start animation together. Stage consumes touch events while transitioning, disallowing nested tap-triggered pages. BACK action remains registered through closing (hardware Back consumed idempotently), entry open guard rejects already-active host. Pop restores once, no premature BACK removal. Native rapid-tap validation remains pending.
Highlight entry now custom Button drawing a rounded trailing chevron in its own onDraw (no elevated button hiding sibling arrow); background follows selected RGB and refreshes on attach after return. Text/arrow contrast selected for light/dark colors. Same placement/shape as Start retained.
Stars no longer pulse core alpha: core opacity fixed to saved overlay opacity, only core luminance and halo vary continuously. Slow baseline0.90±0.035, rare broader smooth peaks (cosine exponent24 vs100), no resize/movement. Updated90s pulse regression passes locally; actual perceived smoothness requires phone. No list-filter changes. APK/CI pending; signing caveat unchanged.


### 0.3.27 verified delivery
Source ba1439cb86a7ed515626ca2ab82ec65fdf921a34; CI run 41461596630 success. APK47086902 bytes SHA256c2a9696a6e04503d1f43ce2504005bd066c978f547ed4a152ad91c00d65fef40. Saved libfile_80b8ed2eb0c481919182886016a1b471. Video inspected, source fixes compiled; native rapid-tap/page-flash and perceived star smoothness unverified on Xiaomi. Signing caveat unchanged.


## 0.3.28 — independent visible twinkle and main-screen cleanup
Stable label exclusion derived from tile geometry/name replaces per-OCR text rectangles that could toggle groups of stars. Core alpha remains constant, independent frequencies/phases/depths drive stronger smooth luminance (no motion or size changes), same renderer in live preview. Regression covers90s continuity, nonzero minimum, visible excursion and independent neighboring trajectories.
Removed staticGlobal, manual calculator, database heading/status/block from main. Update database button now in gear settings with transient progress. Footer retains app version plus current-region cache update date dd.MM.yyyy without time. FillViewport/spacer anchors footer low. Existing rapid-page-transition guard retained. Build/native test pending; no claim phone flicker verified.


### 0.3.28 verified delivery
Source b62feb965d594a3aa80d9fd025fd0bedfbfef46b; CI41474610449 success including engine, geometry and APK compilation. APK47087462 bytes SHA25648d670f7993c3eb287524fd2216ed3f7cc35d4e1a71a5c0d3a3bfd969eabfdac. Saved libfile_dbcfe67beff48191bb56edaaaad0ba03. Native perceived twinkle and rapid navigation still unverified; ephemeral debug signing caveat unchanged.


## 0.3.29 — spoken review: visible twinkle range, edge crop, 200% brightness
Removed5unit internal clip: fill bands and stars now clip only to actual rounded button boundary. Letter exclusion checks star core rather than full haze, so small top/bottom stars survive. Added4 small stars (22total) in stable central top/bottom positions. Existing independent smooth phases/speeds retained; deeper pulse .59±(.31–.40)±.045 makes luminance clearly vary while keeping constant core alpha and nonzero minimum. Shape/position/size unchanged.
Brightness now separate0–200% gain, default/reset100%, applied to stars and effect; color wheel hue/saturation separate. Above100 adds star intensity/halo and blends selected rim toward light without clipping HSV value. No further opacity/settings changes.90s continuity, independent trajectories, visible range, top/bottom placement and uncropped core checks pass. Native appearance pending, no claim phone validation.


### 0.3.29 verified delivery
Source22a43e95ce7ee73d19fca6b60ee8d3007336fdb4; CI41486339777 success. APK47088034 bytes SHA2568b706c2dda2ab1997134733cac7867dfcbc1e25a402998e6353c2cfc35be37b6. Saved libfile_e93ff49497048191ae9e5cdd3e1429c8. Native smoothness/edge appearance/brightness range unverified on device; no native-test claim. Signing caveat unchanged.


## 0.3.30 — shared Arknights UI themes (2026-11-03)
Base: branch arknights-recruitment-starter, source962972db; verified prior APK0.3.29. User completed spoken review: preserve placements and tag highlight renderer/pattern/style; new common rectangular UI, condensed Cyrillic-capable system typeface, reference-inspired neutral light/dark palettes, brighter Global/China, bright cyan thumbs and darker cyan rails/Start. Theme entry at top of both settings opens existing animated nested page; light/dark selection saved and applied to registered activity/overlay trees. Shared drawn navigation chevrons replace font glyphs; list disclosure uses same stroked family. Removed inherited double outline from pushed settings pages (plain child background). Replaced200dp wheel with88dp two unlabeled continuous strips: rainbow, then white->selected hue->black. Separate effect gain0–200% retained; renderer/pattern/style untouched. Recommendation tags start after explicit newline below time/rarity.
Build/CI and native Xiaomi verification pending. Do not claim native appearance verified. New UI must use UiStyle, NavigationButton and shared controls; no per-screen alternate palette/icon family. Existing capture/OCR/data/target behavior stays frozen.


### 0.3.30 delivery checkpoint
Source ac0e4a229100b18e7899411ace70878957249025; CI41591781916 success, all algorithm/geometry/API/compile steps passed. APK47088774 bytes SHA25654247e5c94182e21fcc4172cba2b555025b11a279c48e65300197791dddfd90a. Saved libfile_9ef235945e708191a7444c012cef75aa. No emulator/adb executable or /dev/kvm available in current environment; native visual review was NOT performed. Prior spoken promise of emulator review was premature. User asks to avoid repeated caveats/screenshots but report status accurately. Future preflight must reason about long text, overflow, measured sizing, screen constraints, single outer border and minimal elements before building.


## 0.3.31 — local auto-translator, implementation checkpoint (2026-11-03)
Recovered current0.3.30 source d71fcb6d from GitHub after scratch cleanup. User freezes recruitment selection/OCR/probe/data/highlights; add translation separately. Name everywhere now Arknights Assistant (same appID). Widget order play/pause → A/Я → settings; translation default off, requires explicitly downloaded/imported model. Shared marker canvas keeps single pass-through overlay alpha (avoids stacking opacity above Android touch-security threshold). Capture image is only read, never closed/mutated by translation before recruitment consumes it. Recruitment algorithm and data files unchanged.
Confirmed voice contract: local Qwen3-4B-Instruct-2507 Q4_K_M, English→Russian; glossary proper names and terms (Mudrock=Мадрок, Oripathy=орипатия), screen-specific instructions, recent story context, persistent cache. Readability prioritized; close original with sampled background and fit Russian inside original text rect; multiple lines are clipped, no tapping automation. Track scrolling separately, hide uncertain/stale translations; infer screens from short text anchors rather than wallpaper; expected route neighbors then inverted anchor candidates. Own app activities/settings/pause suppress translation.
Implemented modules: translate/TranslationPolicy (prompt/context/cachekey/generation), QwenEngine native JNI via pinned llama.cpp fd1234cb468935ea087d6929b2487926c3afff4b(b6096), ModelStore (DownloadManager + public Downloads copy + private atomic verified import), TranslationStore (SQLite20k bounded cache,256screen templates/routes), ScreenMemory (anchor index/routes), MotionTracker (small grayscale confident shift), TranslationController (separate OCR and serial inference, cancel/epoch gates), TranslationOverlay (non-touchable clipped text). TranslationSettingsActivity model download/import. Qwen GGUF pinned Unsloth revisiona06e946bb6b655725eafa393f4a9745d460374c9;2497281120bytes;SHA2563605803b982cb64aead44f6c1b2ae36e3acdb41d8e46c8a94c6533bc4c67e597; model not bundled in APK. Model hash validated before replace; download resumable via system DownloadManager; import does not overwrite working model before success. No new accessibility/storage permission. All known EN operator names bundled separately (unknown transliterations kept original).
Touch hints: WATCH_OUTSIDE_TOUCH on existing small widget, only nonzero coordinates accepted. Android may redact/not deliver game coordinates; visual transition graph remains fallback. Do NOT claim universal actual touch tracking or learned exact button semantics. Motion matching currently global/coarse, scroll panel segmentation not yet semantic. Each translated item clips to its own rect inside screen; cannot claim exact arbitrary panel tracking. MediaProjection includes own overlays, so a clean capture temporarily hides translation before OCR (160ms scheduling/45ms wait); phone flicker needs validation and future capture-exclusion solution. Do not conceal this prototype limitation.
Pure JVM regressions pass: canceled generation rejects late results, UI cache independent of history, story keys context-specific, glossary/name prompt,6px scroll estimated, unrelated scene rejected. Recruitment smoke tests still pass. Android/JNI build pending; no claim complete translator phone-tested. Next: CI compile, actual model inference validation, stable delivery signing, preserve model through same-ID same-key future updates, deliver prototype with accurate status.

Permanent signing identity created privately and backed up as Arknights_Assistant_private_signing_backup.zip in user Library. Never publish key/password or include in APK/repo. Future delivered APKs must be locally re-signed with this same identity after CI; keep appID. A one-time reinstall may be necessary to move from old ephemeral CI debug signature. No claim that old0.3.30 can be updated with lost debug key. Model copy in public Downloads permits reimport after uninstall. Separate Linux-CPU CI real-Qwen smoke added with exact size/hash model validation, actual Russian/glossary inference; not phone performance evidence.


### 0.3.31 verified prototype delivery
Final APK source e628c08ea02ed9d9fe08c656a860f6d825d63c07; Android CI41679287095 success, JNI/arm64 compilation + existing recruitment/geometry/API + translation regressions pass. First intermediate CI41678849939 also success but not delivered. Real Qwen CPU inference CI41679287252 success: downloaded exact2497281120byte model, SHA256 verified, actual output «Мадрок имеет орипатию.» preserved both glossary terms. This validates runtime/model, NOT Android latency, overlay visual quality or full route/scroll behavior.
Delivered APK locally re-signed with permanent RSA4096 identity; v2/v3 verification PASS, all native ELF load segments16KiB aligned, ZIPCRC/library/glossary checks PASS. Size25008560 bytes SHA256460ede40a5edef13cad86c0f9a7cf7b85c022d0c5490209b4ab659f114d519d7. CertificateSHA2562278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476. Private key backup filename Arknights_Assistant_private_signing_backup.zip (private Library; never repo). Tool tools/sign_delivery.py signs using key directory from backup and verifies. Future APKs MUST use that key.
APK Library identity libfile_28b38d818f808191b5bd5bac7a644a9a version1. Direct create transfer unexpectedly truncated signed APK to24937664 bytes and broke ZIP; detected by post-save signature/CRC check, restored signing output and replaced SAME Library identity via current library_upload.py prepared helper with expected_version0. Final local bytes/hash/signature rechecked intact. ALWAYS verify installable binary after Library write, prefer prepared helper for APKs. Signing backup localZIPCRC and exactkeystore equality also pass.
User setup: Перевод→Скачать Qwen(2.50GB) or import existing exactGGUF fromDownloads→returngame→play→A/Я. Translation off by default. Old0.3.30 ephemeral signature cannot be recovered; one-time uninstall may be required; from0.3.31 preserve sameID+permanentkey, model/cache persist on normalupdate.
Known prototype follow-up: clean-capture hiding may blink; motion matcher global/coarse rather than exact panel-local optical flow; actual taps can be redacted, so graph falls back to observed visual transitions; no universal button-semantic guarantee. Android16 phone experience not yet tested. Do not call full promised adaptive translator/route learning finished. Next native validation should prioritize stale text suppression, overlay OCR feedback, skill scrolling/clipping, model import/download lifecycle and sustained memory. Keep recruitment untouched.


## 0.3.32 — fix confirmed Xiaomi crash in translator frame (2026-11-04)
User4831/4832 screenshots: IllegalStateException Immutable bitmap passed to Canvas constructor at TranslationController.frame:74, propagated into ScreenService.consumeFrame:218. Root cause: Bitmap.createBitmap(int[]colors,...) returns immutable bitmap; plain JVM/CI compile did not exercise Android Canvas mutability. Added TranslationFrame factory using mutable width/height allocation + setPixels, then same scaled overlay-exclusion masking. Source capture/pixel array unchanged. Factory recycles working bitmap on synchronous masking failure.
Added Robolectric native Android34 graphics regression for real Canvas-backed mutable frame, exact source preservation, scaled exclusion geometry, and subsequent drawing; separate no-exclusion pixel preservation case. CI runs :app:testDebugUnitTest before APK. Translator synchronous OCR invocation and async result-processing exceptions release/reset resources; frame RuntimeException contained at service boundary so recruitment/capture can continue. Failure disables translation, hides its overlay, records stacktrace privately, emits toast. No recruitment engine/probe/data/highlight changes. Version0.3.32code35, sameID; MUST locally sign with permanent0.3.31 key, no reinstall/model redownload. Native phone test pending.


### 0.3.32 verified crash-fix delivery
Source b2cd3735c8e4996957077bfefbc838fba47b833a; CI41795252829 success. New Robolectric native Android34 mutable-frame Canvas/masking/pixel-source regressions PASS; existing translation, recruitment, geometry and APK checks PASS. Same permanent0.3.31 certificateSHA2562278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 signature verified. APK25008560 bytes SHA256cfae449815cb04c1802f059d52bf7b2bedf85efcf500c48dec72e933b3fe2444. Saved via prepared helper as libfile_0a9d35f652208191a69ccda32cac59fb version0; post-save signature/ZIP/hash/class reverified. Install over0.3.31, preserve model/cache; no uninstall/redownload. Xiaomi native behavior not retested here; exact reported bitmap crash addressed by Android graphics regression.



## 0.3.33 — separate recruitment/translation settings and visible translation diagnostics
User-authorized 2026-11-04: main order Start → Translation → Recruitment, shared NavigationButton styling. Recruitment child page contains existing highlight entry, region and recommendation controls; nested Back works before leaving recruitment. Recruitment algorithms/rendering/data untouched. Translation retains model management, adds automatic source-language instruction and Russian/Ukrainian target selector persisted separately. Target is included in every cache key; language change invalidates pending generations. Qwen detects source language from OCR text; bundled Latin OCR remains a limitation for non-Latin source scripts. No claim of full CJK OCR support.
Replaced A/Я with native Canvas two-card folded translation symbol preserving reference composition, cyan/charcoal palette. Both symbols retained; off-state desaturated.
Possible silent translation starvation found by inspection: unrelated animated background can fail global motion matching repeatedly, advancing epoch/canceling ongoing inference. Uncertain visual movement now requests clean OCR and hides overlay; actual OCR text change, explicit touch, pause, disable, target change still invalidate requests. Avoid revealing inference during pending clean OCR. Candidate fix, not device-confirmed root cause. Live status exposed on Translation settings: waiting, model load, translation, missing model, OCR/Qwen error.
Added target-cache isolation and Ukrainian/auto-source prompt regressions. Version36/0.3.33. Android CI and phone validation pending. Final delivery must use permanent0.3.31 key and preserve model on update.


### 0.3.33 verified delivery checkpoint
Source a03d58d5aade5d3e67cad1d55fc3f9cf698093a8; Android CI41797998106 success, translation/target-language, recruitment, geometry, external API and Android mutable-frame tests passed. Signed APK25012656bytes SHA2565be8a574a6ac1646171ce9e76bc86952fff3f7d91bf8f29a5adf1ba2d79c0e5b. Permanent0.3.31 certificate2278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 PASS, ZIPCRC/new methods checked. Saved libfile_0d0753c82430819192c3a181ae8df553 version0 via prepared helper; post-save signature/hash verified. Install over0.3.32 without uninstall. Phone translation appearance/speed, language detection quality and animation starvation fix remain unverified. Latin OCR only; Russian/Ukrainian target via Qwen. No device-success claim.


## 0.3.34 — no translation visible on4838 home screenshot (2026-11-04)
User screenshot shows active play/translation and no visible translated labels. Screenshot alone cannot identify OCR vs Qwen stage. Further source inspection found full-transcript similarity still advanced global epoch on every changed transcript, aborting stable source jobs; worker could set hidden=scanning while OCR was in flight, and OCR completion never restored visibility. Fixed per-visible-source admission (TranslationVisibility): unrelated OCR changes/counters do not invalidate all jobs; removed old-source results are not published, explicit touch/pause/disable/target still invalidate. Source disappearance cancels current native work. Rendering uses latest source box. OCR finally now releases hidden state unless paused/disabled/clean read pending.
Added small OCR N / translated M/N progress below widget, included in own-window capture exclusion. Translation settings expose live counts/stage and user-initiated Copy diagnostics. Large uncertain image changes can briefly hide output; ordinary animation no longer triggers a long clean-pending hide. Existing real movement and actual OCR screen changes still reconcile visible labels. Added fixed bilingual InterfaceGlossary for known home menu labels for immediate first results; arbitrary prose remains Qwen, no claim dictionary replaces inference. Digit-bearing labels scheduled after stable labels.
Pure regression: dynamic counter/noise preserves Terminal eligibility, new screen rejects old Terminal output, OCR visibility guard, RU/UK exact UI labels and unknown-prose fallback. Native Android graphics regression draws actual Russian text after simulated OCR completion and verifies mask/outside region/glyph pixels without recruitment dependency. Model/engine, region/recruitment selection and highlight logic unchanged. Version37/0.3.34, same private signing identity as0.3.31. Phone confirmation pending; do not claim screenshot proves the unique root cause.


### 0.3.34 verified delivery
Source a587996a021ccc90659d752572997b7d58e369eb; Android CI41848058113 success, including native Android graphics actual Cyrillic overlay draw test and existing mutable-frame tests, translation source-admission/visibility/UI-glossary checks, recruitment/geometry/data/compile checks. Signed same permanent0.3.31 certificate2278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 PASS. APK25012656 bytes SHA2569f462042874e07f5e7ce42a2a2410a142f6200e7ff24937a00060398ae658436. Saved libfile_e55ea936ab648191b4842524a19ff59a version0 via prepared helper; post-save ZIP/hash/signature verified. Install over0.3.33 without uninstall/redownloading model. Exact phone OCR/Qwen stage at4838 remains unknown until new progress is seen; no false device-success claim. If still absent, use OCR/translated counters and Copy diagnostics rather than another unobserved pipeline patch.


## 0.3.35 work checkpoint — geometry and diagnostic detail
User confirmed translated labels appear but are small, horizontal and sample orange decoration above Missions. Controller now uses per-line OCR corners, preserves angle through cache/live/motion results, samples 45 points within text region, and retains native capture resolution up to2048 instead of1280. Renderer removes26px size cap and rotates mask/text together. Copy diagnostics lists each OCR source, translated output or waiting/current-Qwen state, geometry and hidden state. These are candidate fixes; Android compilation/graphics tests and device validation NOT yet performed. Recruitment unchanged. Version still0.3.34 until validated release.


## 0.3.35 — completed geometry/diagnostic implementation (2026-11-04)
Continued uncommitted geometry checkpoint in repo334, preserving prepared corner/line/native-resolution work. Reviewed user Library4840,4844,4846: some home labels translated too small and English shows through, Missions mask samples orange decoration, terminal screen small labels mostly untranslated, story OCR4/translation0. This confirms OCR/overlay partially work; screenshot does not establish why Qwen has no story result. No demand for video.
TextPlacement uses OCR quadrilateral (perspective/skew/rotation), computes label-local bounds, samples dominant quantized color INSIDE the glyph region, and generates inverse-alpha compensation mask. Renderer maps local text plane through4point Matrix, scales font to original line height, removes26px cap, wraps paragraphs/fits within quad. Source captures remain intact. Translation-only shared overlay alpha.79 (reset.70 when disabled); no second pass-through layer. Compensation clamps target background to feasible blending range, so very dark/bright fills may be slightly lifted/dimmed to hide original glyphs while preserving game taps. Crop/mask bitmaps owned by immutable Items and shared on cache/result/motion updates, garbage collected rather than prematurely recycled during draw.
Controller retains native capture resolution up to2048, collects paragraphs for long multiline blocks and lines for UI; second OCR pass2x on4 overlapping quadrants at most once12s when few/small labels. Coordinates mapped back; spatial duplicate/punctuation correction merged. Clean source bitmap already copied before restoring overlay: no need to hide throughout all asynchronous OCR work. Story paragraphs prioritized before tiny topbar controls; UI glossary adds Auto/Off/Skip/Log, arbitrary prose remains Qwen.
TranslationTrace bounded96rows logs source, queued/current/returned/cached/failed/skipped state, attempts, elapsed seconds, actual output, geometry and errors. Native progress reports loading/prefill done-total/generated tokens. One-second heartbeat persists local report; translation settings get dedicated selectable report dialog and compact summary, Copy diagnostics includes report. Source disappear/pause/session cancellation shown separately. Native inference has bounded token limit64–480 and abort callback90s/180s deadline; per-item failures recorded and do not silently block rest of screen. No claim tokens indicate correct translation.
Pure diagnostics/cache/visibility regressions pass. New native Android graphics tests cover tilted polygon, gray-vs-orange sampling, inverse-alpha hiding, source-scale font above26px, and keeping decoration outside quad. Previous bitmap/overlay/recruitment tests retained. Android build/test pending. Same appID/private signing key; no model redownload. Phone geometry, zoom cost, narrative quality and real progress require validation; fixed primitives are not a device-success claim.


### 0.3.35 verified delivery
Source28652bcaa65982a90e43e6f252497da2e981ffb8; Android CI41854881770 success. Native Android graphics regressions pass: gray interior selected instead of orange decoration, tilted quad placement, inverse-alpha erasure of old white glyphs through.79 window alpha, large Russian font above26px and decoration outside quad preserved. Existing glyph/immutable-frame, diagnostics/visibility/cache, recruitment/geometry/data checks pass. JNI progress export and updated native runtime compile/link checked; no new real-phone inference/latency claim.
Signed same0.3.31 identity certificateSHA2562278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 PASS. APK25045424bytes SHA256a8fb2a930aa9b81edf850c6322288ab0ed1c8a8a00c8d48cc5168522e658785e. Saved libfile_654bac1250f48191aa61199e979d80cf version0 via prepared helper; post-save ZIP/hash/signature/native-export rechecked. Update in place, keep model/cache.
Next phone feedback should use Перевод→Отчёт перевода / Скопировать диагностику: raw OCR, per-source returned output, processing seconds, native input/generated tokens, cancellations/errors, quad/color/font/drawn state. Story0/4 screenshot alone cannot establish a hang; do not assert all narrative translation fixed. Reports retain last96sources and private source text by user request, no automatic sending. Minor follow-up: Trace.start should clear previous output when same source is retried for a different target to avoid stale-answer confusion in an error report; currently first-use rows correct.


## 0.3.36 — local motion, readback feedback fix and owned-frame lifetime (2026-11-04)
User diagnosis: Image is already closed at ScreenService.consumeFrame:219; Qwen canceled before first output; OCR contains own Cyrillic translations mangled as Latin. User requests ready translations follow individual moving controls up to60Hz, no disappearance between position updates, no OCR/Qwen on each movement frame.
Capture: added FrameLifetime retirement on same capture-worker queue for old ImageReader and shutdown of translator/recognizer. consumeFrame now has sole acquired Image owner with finally-close and skips callbacks from retired readers. Main-thread rotation/destroy no longer closes reader underneath active width/plane reads. Closing acquisition exceptions drop a retired frame rather than canceling translation.
RegionalTracker sparse textured3x2 features tracks each label independently using previous per-label clean thumbnail, bounded integer search plus subpixel fit, robust consensus and small similarity transform. Static labels probed90ms; moving labels16ms, capture thumbnail480wide only when fresh frame arrives. No smoothing/frame backlog; freshest-image acquisition retained. Failed tracks hidden individually after4 misses and request OCR, reliable tracks retained across transient OCR misses. This is a60Hz target, not device-measured guarantee. Report shows measured samplingHz, worker trackingms and UI drawms.
Overlay now caches each label's local rendered bitmap/font layout; changing position only reprojects it. OverlayReadback retains timestamped4 snapshots sharing cached pixels, selects capture-time raster and mathematically removes known .79alpha overlay from capture. Raw full frame copied natively while Image owned; inversion and OCR prep run on vision executor. OCR no longer blanks overlay or waits for a hidden clean frame. Full OCR remains1800ms, separate2x small-text passes12s; no Qwen/mask/font regeneration per motion update. Bilinear readback and 8ms compositor timing margin are heuristics: real-device capture clocks/quantization/motion must be verified; not perfect screenshot reconstruction claim. No new permissions.
Qwen no longer canceled on every outside touch or one OCR source miss. Touch only accelerates tracking/requests OCR; completed off-screen results may be cached but not drawn on unrelated source. Own app/settings suppress display and new capture analysis but let inference finish, allowing reports to observe native progress instead of canceling at report opening. Explicit user pause/translation-off/target change/session stop still cancel. Trace retry clears old answer and newly enabled session clears old saved error so stale failure isn't mislabeled current.
Tests: pure60-frame separate moving/static labels against unrelated animated area; single displacement, static no jitter, reader retirement waits behind active frame, stale retry answer cleared. Native Android readback test repeatedly composites a visible Russian overlay then recovers the original source without hiding, with bounded8RGB quantization error. Existing quad/mask/font/bitmap/recruitment tests retained. Pure regressions pass locally; Android CI pending. Version39/0.3.36 same appID/key/model. No phone60fps/no-flicker claim yet.

Follow-up before delivery: completed translations now live in a source+target concurrent result map independent of position snapshots. A model result racing a motion update cannot be discarded until the next OCR; the next tracking frame republishes it. Updated native readback regression moves the still-visible overlay subpixel each frame, rather than checking only a stationary layer.

0.3.36 CI41861965150 failed the newly strengthened fractional-motion readback regression. Analytic bilinear inverse did not exactly match Skia raster sampling; do not weaken tolerance. Renderer now rasterizes a cached local pose with Canvas, draws that integer-aligned bitmap and shares its exact ARGB pixels with readback (including partial edge alpha). Source inversion moved before OCR downsampling. Pose cache64 bounds retention; stationary poses reused, font/mask rendering remains cached. Android CI pending.

Build follow-up: source306ca333687d4074afad2b6d4c5ef772041bba5a contains exact raster readback fix. Build has not appeared after ref update; triggering the verification through this contents commit. No APK delivered yet.

### 0.3.36 verified delivery — 2026-11-04
Final tested source306ca333687d4074afad2b6d4c5ef772041bba5a; Android CI41880851361 success, job126752642341. All seven Android graphics tests pass, including unchanged strict fractional-motion readback check; translation/motion/lifetime, recruitment/geometry/data checks passed and native runtime built. Failed run41861965150 was addressed by exact Canvas raster sharing and native-resolution correction, not weaker assertions.
APK25049520bytes SHA2568be99bdf03dc349d8f985db98277da0854524cb157fea561ba025c780cb1abfe. Permanent0.3.31 signing certificate2278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 verified. Saved libfile_59aa74f63a508191895da270dcf2b533 version0 via prepared helper; post-save SHA/ZIP/signature verified. Install over existing version, preserve downloaded model/cache. Actual Xiaomi motion/flicker/60Hz behaviour remains unmeasured; no phone-success claim. Source keeps own-overlay raster for OCR restoration and independent per-source ready-results; no periodic hide-for-OCR.


## 0.3.37 — baseline live translation, 2026-11-05
User4853 current screenshot and4854 Google reference: prioritize usable base translation before node-map UI. Preserve tiny label coverage, original text plane/colors; shared panel motion, no hiding merely for movement, reuse persisted source+target cache. Google is an appearance reference, not an exact implementation requirement. Full node map/button transition graph stays a separate requested follow-up.
Fixes: sample close outside the ink quad rather than dominant color among dense glyphs (white text must not become a white mask/black translation). Maintain existing quadrilateral projection. Sparse tracker no longer discards pure scale when rotation is small. Temporal co-motion groups require3 agreeing frames, fit common similarity using local axes and centers, split immediately on disagreement, bridge missed members using at least2 reliable peers. All tracked references advance on a common fresh-frame interval. Brief local failure retains ready text up to700ms since last direct visual match, then hides and requests OCR to avoid permanent ghosts. Motion does not restart inference. Small-text zoom rescan interval6s instead of12s. Existing persistent cache/model untouched, recruitment frozen.
New pure checks:60-frame shared pose, independent element split, transient group-member loss, shared scale/rotation. New Android graphics regression: dense white glyphs on dark/blue button and dark glyphs on white retain background color. Existing strict fractional readback/geometry tests kept. CI/build and device checks pending; no claim of Google quality, zero flicker,60fps, texture inpainting, exact touch semantics, or completed screen-node UI. Version40/0.3.37 same appID/permanent key. Next: Android CI, durable-key sign, APK integrity verification and delivery; real device feedback required.

Before delivery follow-up: common pure translations cannot invent scale/rotation from local noise. Successful OCR geometry/color refresh is aligned from its capture thumbnail to latest tracked thumbnail before replacing the node, avoiding stale-frame jumps and permanently frozen initial OCR angles. Progress count now follows actual displayed items.

### 0.3.37 verified baseline delivery
Tested source a8702f69b8f04ea3546d903a03aa0758260640fa; CI42018990472, job127216662188 success. Pure co-motion/cache/translation and frozen recruitment/geometry checks PASS; native Android graphics including dense-ink color sampling and strict fractional moving-overlay readback PASS. Prior candidate CI42018791073 also success; delivered final includes delayed OCR alignment/no artificial scale.
Signed same permanent certificate2278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 verified. APK25065904 bytes SHA2567d622fc42e21ecffafed1a585d73427eeaed56d68362db85434b0bc7055ebe8e. Saved libfile_adbd8926828c81918142c665c04d425e version0 via prepared helper; post-save SHA/ZIP rechecked. Update in place, preserve Qwen model and persistent translations/settings. No real-device flicker/latency/visual quality success claim. Full node-map UI and exact button transition semantics not implemented in this baseline release; remain requested next stage. Current frame-capture recording conflict remains unverified.

## 0.3.38 — screen-memory map implementation, 2026-11-07
User authorized read-only ComfyUI-style map; no more confirmation needed. Added Translation→Карта экранов, pan/pinch canvas, screen screenshot nodes, observed touch-zone route arrows, tap for numbered text quads/source/cached translation/angle/color. Edits deferred by user. Existing SQLite memory remains the source; v1→v2 adds bounded 640px JPEG/label/target records without dropping translations/model/settings. Inference updates saved labels with screen+target isolation. Screens/transitions require agreeing OCR observations from distinct frame bitmaps >=500ms apart; uncertain observations do not overwrite the prior screen. Routes shown only when associated with observed touch and confirmed screen change; no claim exact button semantic recognition or instant full-screen cache restoration. Existing no-touch legacy routes excluded. Snapshots recorded only when translation active outside assistant UI, up to256 screens. No new permissions. Recruitment and inference runtime untouched.
Validation pending: Android CI/build, SQLite migration/independent-frame regression, phone map gestures/learning/translation fidelity. Must sign with permanent0.3.31 key before delivery.


### 0.3.38 verified delivery — 2026-11-08 Europe/Kiev
Tested code889c23325765808d6baf613c3ff4948b482cc599; Android CI42418362103 job128541241393 SUCCESS. Native graphics tests, new ScreenMemory independent-frame/touch-route and SQLite v1 migration/target isolation tests, existing translation/motion/recruitment/geometry tests PASS. Only newly confirmed touch routes shown; legacy unconfirmed edges excluded via migrated confirmed column. APK versionCode41/versionName0.3.38, map class and package checked, ZIP CRC PASS. Same permanent0.3.31 certificate2278ac5532d689ca76ef727a7a11e0dfe9ae5cb4badc32462d2ed0dba1ac4476; v2/v3 PASS. APK25065904 bytes SHA2560a9eb02ebdf09a9e1425df8772f61b4a87667ffded56847650319dd6df8ce005. Saved libfile_56b71154499881919b980bdfc199184d version0. Update in place; keep model/settings/cache. Translation→Карта экранов. Phone gestures, screen-learning accuracy and route timing not yet device-tested. No claim exact button semantics or completed instant-screen reuse. APK was built before this documentation checkpoint; bundled DEV_NOTES contains prior baseline notes.
Automatic review initially rejected git push without explicit publication consent; user explicitly authorized it. Shell git push then lacked credentials; connected GitHub tree/commit/ref tools succeeded. Future work should reuse connected GitHub tools rather than assume shell authentication. User authorized work and does not need repeated feature confirmations.


## 0.3.39 — completeness across windows
- Settings screenshot showed OCR 44 but rendered 1/44: OCR admission is working; only the small main-menu glossary was immediate and serial inference kept an obsolete screen snapshot.
- Inference now selects the next untranslated item from the latest visible screen after each result. Two OCR observations of an absent in-flight source cancel obsolete generation; one missed OCR or motion alone does not cancel. Already completed translations/cache remain intact.
- Added RU/UK settings labels, explanatory lines and home/event labels. Cosmetic bullets, dash variants and final punctuation normalize for exact glossary lookup; unknown text still uses Qwen, no fuzzy substring substitution.
- Regression fixture covers screenshot settings labels in both languages. Recruitment logic remains untouched.
- Pending: CI Android tests/build and permanent delivery signing. This is not proof that arbitrary unseen windows translate instantly.
