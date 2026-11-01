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
