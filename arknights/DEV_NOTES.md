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
