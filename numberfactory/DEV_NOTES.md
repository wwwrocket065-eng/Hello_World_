# DEV_NOTES — Numera Android

## 2026-10-31 / v0.1.0 graphical and gameplay prototype

- Repository: `wwwrocket065-eng/Hello_World_`; branch: `numberfactory-android`; project: `numberfactory/`. The existing main branch and GlassStudy/Hello_World_ files are not edited.
- Confirmed source commits:
  - `8093a163c365f2b6adbb7d3cd7f9426a5da97925`: Canvas-based playfield, game simulation, number sources, conveyors, add/multiply machines, touch construction, pan/pinch, UI, local persistence.
  - `bdc23dc4b80a24577620e777af38c4c723060906`: GitHub Actions debug APK build and conditional public preview release.
- Appearance: original dark graphite/navy grid, moving turquoise conveyors, amber number tokens, violet adders, blue multipliers, ringed target hub, modern HUD, 6 touch tools.
- Initial production line: source 2 + source 3 -> addition -> conveyor -> hub targeting 5, 12 successful deliveries to complete level 1. Level 2 target 8; further balancing and unlocks remain to be implemented.
- Build conditions: Gradle 8.9 / AGP 8.7.3, Java 17, compileSdk 35, Android 8.0+ (minSdk 26); package `com.numera.factory` separate from other apps.
- **Verified build 2026-10-31:** GitHub Actions run `41210157744`, run 7 for commit `33907d164b561a2f91a1234d6c74e6b28261ec22` finished `success`. `:app:assembleDebug` and upload artifact steps completed successfully; artifact ID `13046201283` (`numera-v0.1.0-debug-apk`). Downloaded the ZIP and extracted `app-debug.apk`, 17,473 bytes; APK ZIP CRC check passed, `classes.dex`, `classes2.dex`, AndroidManifest.xml and resources.arsc present, APK Signature Block magic found. SHA-256 of APK: `82b40eb0da568894136958f5eb26909d963e482ed0aa0e9075149c3f678abfb8`.
- SDK preparation failed initially on legacy `tools` package; fixed by using runner's preinstalled Android SDK in commit `33907d164b561a2f91a1234d6c74e6b28261ec22`.
- **Limitations:** real-device installation and touch usability are not verified. This debug APK is **not** guaranteed to install as an update over future independent builds (debug key may change). Downloads auto-update has **not** been implemented. A stable signing key and validated updater are outstanding; don't advertise them as done.
- CI planned output: `numera-v0.1.0-debug-apk` artifact; preview release tag `numera-v0.1.0-preview` only after successful build.
- Next: install on the target phone and test touch hit areas, enable persistent secure signing for in-place updates, implement/test downloads-based auto-update, then improve placement gestures, arithmetic choices, factories/upgrades and autosave.

## 2026-10-31 / v0.2.0 prototype upgrade checkpoint

- Isolated branch `numberfactory-android` only; main and GlassStudy untouched.
- Commits `7325525`, `d4042d7`, `e33c342`, `ff1b301`, `5721632`, `7368065`.
- Source now starts in Camera tool (one finger pan; two-finger pinch), supports zoom down to 0.12x, immersive Android full-screen navigation.
- Fresh world uses an empty 6x6 hub and visible 3x3 numeric deposits. Miners may only be placed at discovered deposit centres; their output reaches conveyor tiles outside the 3x3 visual region. New progress uses independent v2 preferences, so previously generated demo world is not auto-imported.
- Belts: draw mode (freehand), route draft mode (multiple drag legs, finger release leaves preview intact), Confirm / Cancel, edge auto-scroll. Turns draw as bent paths. Selection/copy/paste prototype is added, with known limitations (clipboard selection and constrained drag selection).
- First 20 level goal numbers/counts updated from public Beltmatic level guide. Not all tech unlocks/mechanics implemented.
- Four GLOBAL (not per-building) upgrade categories added: belt speed, extractor throughput (up to four cardinal outputs in this 2D prototype), adder speed, multiplier speed. First eight tier requirement examples each are encoded, with per-category delivered-number counters, costs subtracted on purchase, a HUD upgrade panel, persistent upgrade levels/counters. Note: exact number IDs may vary by world in original Beltmatic. Higher tier progression and subtract/divide/exponentiation tracks not implemented.
- Operators now honor approximate cycle times based on their upgrade tiers. Belt movement rate tied to global belt upgrade. Extraction has four possible output directions (original uses twelve), so not exact parity yet.
- Release packaging uses v0.2.0 preview tag. CI build and real-device usability need separate verification; an APK cannot be claimed installed or able to update over 0.1.0 based solely on CI compilation.
- **Outstanding**: no cross/bridge/priorities implementation, no full original tech tree (all 30+ stages), no unlimited upgrade paths, no robust selection UI/undo, no advanced logistics, no autosave of view/camera, no Downloads-based auto-updater, no stable signing key. Current debug APK may require uninstall of older debug signed version if certificate changes.

## 2026-10-31 / v0.3.0 UI and gameplay preview candidate

- Isolated development branch: `numera-ui-fixes-20261031` forked from `numberfactory-android`. GlassStudy, the `main` branch and other projects are untouched.
- Source checkpoints: `b137a8c` restored the full-width animated turquoise conveyors from v0.1.0, with same-style elbow tiles, displayed target and delivered count on the 6x6 hub, 12 mining output perimeter locations and continuous unfinished route drafting; `92dd369` introduced deterministic irregular minefield generation; `60d978b` increased starter 1-deposits, enlarged 3x3 placed miners visually, added area-delete primitives; `1166036` added drag erasing, functional selection/copy/delete actions, and two conveyor modes plus a contextual Confirm/Cancel bar.
- Starter deposits now intentionally favour number 1 over other numbers near the hub. A 16-cell protected circle blocks generated deposits near the central hub, with a fixed starter layout around the ring. The terrain algorithm is an original approximation, *not* a verified byte-for-byte reproduction of Beltmatic's map generator.
- Controls: default camera; draw or long-range route drafting (finger lift never places; subsequent strokes continue last endpoint); Confirm/Cancel only when a route exists, one row above the two fixed mode buttons. Eraser drags across cells; Selection can copy into in-app clipboard or erase a rectangular selection. Native 3x3 resources and hub cannot be deleted; constructed nodes can.
- Diagnostic UI: add/multiply toolbar entries display the level where they unlock (2 and 7). Central hub contains live goal and progress and no "6×6" wording or decorative radial glow.
- `versionCode=3`, `versionName=0.3.0`, and a new isolated GitHub Actions preview-release tag `numera-v0.3.0-preview`. The Action must pass before claiming APK availability.
- **Not yet complete:** exact Beltmatic procedural map distribution, all levels and machines, full 12-stream balanced throughput, cross/bridge conveyors, advanced upgrades, robust touch usability on device, automatic signed in-place updates, original release signing key. Debug builds may differ in certificate between CI runs; installing over another version can fail. Do not claim complete parity. Preserve the user's save data when possible and do not uninstall without a backup.

## 2026-10-31 / v0.3.1 spacing fix (number deposits)

- User feedback: the 3×3 resource fields in v0.3.0 are too densely packed; keep many ones in the starting region, but make fields noticeably farther apart.
- Commit `8a8aa27`: increased deterministic procedural region pitch from 27 to 46 world grid cells, reduced second-candidate generation from 56% to 38%, randomized centre offsets over 36 cells, and enforced at least **30 cells from centre to centre** for nearby accepted deposits (was 11). Generated deposits remain outside a 23-cell exclusion radius from the 6×6 hub centre (was 16).
- Repositioned fixed starter deposits with the same minimum 30-cell separation; nine starter 1-deposits remain reachable around the hub, with some 2, 3, 4 and 8 fields farther away. This is not claimed to be Beltmatic's exact procedural algorithm.
- Simulated candidate and starter layout across 17×17 generation regions with signed 32-bit Java-equivalent hashing: 208 accepted generated fields; minimum inter-centre distance ≈30.07 cells, excluding overlaps. Gameplay and appearance still need on-device verification.
- Incremented Android `versionCode=4`, `versionName=0.3.1`, isolated release tag `numera-v0.3.1-preview` (commits `476b37e`, `dacac39`); check the run's completed-success state before calling its APK verified.
- Existing user-placed machines in previous world saves remain in preferences: changing map distribution does not delete saved factories. A miner placed at an old resource centre can be left on a position no longer generated as a deposit; this compatibility caveat still needs a migration design.
- Other v0.3.0 limitations remain: incomplete Beltmatic parity and tech tree; no stable APK signing or Downloads auto-update; on-device touch validation outstanding.

## 2026-10-31 / Mine placement touch-target fix (post-v0.3.1; unpublished)

- User requirement: when the miner placement tool is active, touching **any of the nine cells** of a visible 3×3 deposit places the one extractor at that deposit's **centre**. The miner does not shift to the touched edge cell.
- Commit `8df82063a42d1fefd8b04d9d5e5db0d03ea647e3`: `FactoryView.depositCenterAt()` resolves a touch to the canonical deposit centre. Mine placement checks the centred value against unlocked extraction and only inserts when that central node is vacant, so further touches cannot spawn duplicates.
- Prior development commits also animate chevrons through conveyor bends (`067953b`), allow the adder at level one (`39e68c6`), and stop GitHub Actions from automatically publishing unfinished preview releases (`4cabfea`).
- **Status:** source commits recorded on branch `numera-ui-fixes-20261031`. Build and Android real-device validation are separate steps; neither full Beltmatic parity nor a finished APK is claimed here.
- Still needed before final release: rigorous regression testing for miner placement, 12 output sockets and stream upgrades; conveyor intersections/bridges and other advanced mechanics; complete levels and technology unlocks; save migration and stable signing; device interaction and performance QA.


## 2026-10-31 / v0.4.0 advanced machinery and conveyor checkpoint

- Working branch: `numera-ui-fixes-20261031`; game in `numberfactory/`; no edits to Hello_World_, GlassStudy, Panspermia, or their update processes.
- `2c1070b` and `d52cb96`: right-angle belt bridges, two independent lanes and animated crossing art. Bridges become available after completing level 4. This is a first-pass implementation, not on-device verified.
- `f6c2c7e`: extended scripted goal/count list to 30 tiers and extraction thresholds.
- `0ce0c7e`: core operations for subtraction, quotient/remainder, exponentiation, and a 10,000-item single-number storage. **CI success** on run 41219126781. At that stage the tools were not yet selectable.
- `377475d` and `f9b4c13`: six-slot operator picker attached to the existing mathematical-machine toolbar; higher-tier tools only selectable after their unlock levels. Added visible symbols and storage content counter; CI success on run 41220233784.
- Unlock stages: adder 1, multiplier 7, subtractor 13, divider 16, exponentiator 19, storage 22, per public Beltmatic wiki. Original textures/assets not copied.
- `341588c`: item movement snapshot prevents a number from traversing multiple belt tiles in one tick due to hash iteration order; miner output scan rotates fairly. **CI success** on run 41220340194.
- `65fa505` and `ecdda4c`: shared subtract/divide and independent exponent global upgrades; six rows in upgrade screen. First four historical upgrade tracks and saves remain in same order. Initial cost entries for subtract/divide are **provisional**, not verified original world-specific values; number IDs may vary by seed. Both commits failed CI due to type dimensionality, fixed in `9a6a790`.
- `e1e1f67`: fix tapping rows five and six to actually buy their upgrades.
- `0460177`: Android versionCode 5/versionName 0.4.0 (build metadata, not a released APK).
- Save compatibility: persistent SharedPreferences name `numera_world_v2` and existing world records are kept. Newly saved machine records append a `storedCount` field after `crossDir`, while loading old records still accepts records lacking it. Upgrades now serialize six tracks while the loader accepts legacy four-track saves.
- Built-in tests: GitHub Actions compiles `:app:assembleDebug`, but no Android emulator/UI interaction test. **Do not call the candidate a verified finished game, in-place update, or an official release** based only on Gradle compilation. Current CI after `e1e1f67` must be checked before distributing.
- Remaining critical QA: simulate real phone touch, test long belt drafts/elbows and crosses with opposing streams, validate arithmetic A/B port ordering, storage buffering and persistence after restart, large factories/performance. Production stable signing and in-app patch/updater are not implemented. Public releases intentionally disabled for unfinished builds; GitHub Actions keeps debug artifacts for QA only.

### Safe QA packaging / confirmed CI

- `9a6a790`, `e1e1f67`, and `168312f` compiled successfully in GitHub Actions (run IDs 41220621014, 41220677013, 41220724011). The APK was extracted and checked as a valid Android package.
- Signature comparison from the CI build confirmed `Numera-0.1.0-Android.apk` was signed with certificate SHA-256 `dbb3a76f...`, but debug CI build signatures differ between runs. Do **not** recommend installing CI builds over the original, uninstalling the old app, or losing saves.
- `edf69b8` adds a `.qa` application ID suffix to debug builds only, preserving the release package. `e9edbb0` adds a debug-only manifest label `Numera Test`. Both passed CI; final run `41221028436` completed with success.
- Confirmed the packaged QA APK has application ID `com.numera.factory.qa`, visible label `Numera Test`, versionName `0.4.0-test`, and valid ZIP structure. QA APK SHA-256: `9abc6e52e7fd17f855e7f1486f76196a01f3f0dae11001270863b4e58e7cb573`. Local artifact filename: `Numera_Test_v0.4.0.apk`.
- QA can be installed side-by-side without altering the previously installed Numera app or its app-private saves. This is a **test artifact**, not a production APK or completed parity with Beltmatic. Stable release signing and real phone interaction/performance remain outstanding.

## 2026-10-31 / v0.4.1 number-motion and belt UX QA checkpoint

- User screenshot/feedback: curved conveyor cells showed an unwanted uninterrupted turquoise centre stripe; numbers jumped cell-to-cell instead of travelling; long belt dragging needed a larger auto-camera-scroll trigger zone.
- Commit `674441c`: removes the continuous central bright line from elbow segments only; preserves dark/turquoise body of original belts and animated V-shaped chevrons following a PathMeasure along bends. Enlarges the active auto-scroll band from 42 dp to up to 94 dp horizontally / 104 dp vertically (capped at 24% of width / 18% of height). Camera speed ramps quadratically from zero on entering the band to higher speed at the screen edge, while a belt draw/route/erase gesture is active. GitHub Actions run `41222848963` succeeded.
- Commit `291c4e2`: adds *render-only* per-lane Travel animation fields on Node, records each logical transfer's source and tick duration, and draws moving numbers above all belts. Uses cubic position interpolation, with tangent matching the destination's outgoing direction on bends, on main and upper bridge lanes. Miner outputs and storage outputs also initialize travel; newly computed machine results remain centred. Stationary/blocked numbers settle in place after their duration. Old SharedPreferences world and upgrade formats are unchanged. GitHub Actions run `41222931120` succeeded.
- Commit `404bf5b`: QA versionCode 6 / versionName 0.4.1; debug build continues to use isolated `com.numera.factory.qa` application ID / `Numera Test` name to protect the production installation from a changing CI debug signing certificate.
- **Important remaining limitation:** The user's two additional movement bugs were not demonstrated by a video in the current turn, so they are not claimed to be specifically reproduced or resolved. Device-level visual and gesture QA is outstanding, including checking bends, overlap streams and UI safe areas.
- Compiling is not a real-device pass. Do not claim a production update is safe or an entire Beltmatic mechanics port is complete. No change to GlassStudy or other applications.

### v0.4.1 QA APK verification

- Successful GitHub Actions run `41223082951` (commit `699ad326d` after source commits `674441c`, `291c4e2` and version bump `404bf5b`); output artifact ID `13050683094` (`numera-development-check-apk`).
- Downloaded `Numera_Test_v0.4.1.zip`, ZIP integrity verified, extracted the single `app-debug.apk` to `Numera_Test_v0.4.1.apk` (36,753 bytes). APK ZIP CRC and Signing Block presence confirmed; SHA-256 `97c66af60838320fb886ae3ac62b7e4aec526d48f7a964df74bf91bedf317846`.
- Android phone installation and runtime interaction NOT yet verified. This is an isolated `com.numera.factory.qa` debug build, not a production signed update. A changing CI debug signature might prevent in-place upgrade even over an older `Numera Test`; keep the original production installation untouched.

## 2026-10-31 / v0.4.2 chain-transport bug reproduced from video

- Evidence: user video `4668.mp4` shows many closely packed number-1 cargo items on a long extractor-to-hub conveyor, but the HUD still displays `0 / 10` while filming. A first number may also be in transit, so the video alone does **not** prove the final hub contact is broken; long travel distances matter.
- Confirmed *source-level* issue: old `moveLane()` mutates cells in arbitrary `HashMap` iteration order and rejects an incoming number when its target cell is still occupied, even though the occupant will move during that same tick. This causes needless waits, low effective throughput, and visible stop-and-go motion on filled long conveyors.
- Commit `76969a2`: replaced incremental in-place moves with `Transfer` proposals; one claim per target belt/bridge lane, explicit independent bridge lanes, deterministic cell iteration, snapshot occupancy, and all-or-nothing move application. Every movable source is cleared before any target is written. Preserves hub, operators, storage, miner outputs, and interpolation timestamps. GitHub Actions run `41224227645`: success.
- Commit `015872a`: optimized chain vacancy propagation to a linear-time queue from free exits to upstream belt positions; no quadratic full-map scans. GitHub Actions run `41224343642`: success.
- Algorithmic sanity checks with full 40-cell chain feeding a hub, 30/40 populated chain, blocked full chain, and one-cell path confirmed cargo count conservation and advancement.
- Commits `adab80f` and `5447767`: versionCode 7 / `0.4.2`; debug app ID `com.numera.factory.qa2` with name `Numera Test 2`. Side-by-side QA package is intentional because CI debug keys change between builds; never replace production `com.numera.factory`, delete a user's old application or claim in-place update compatibility.
- **Remaining verification:** GitHub Actions run for latest `5447767` still needs completion check; unpack and verify output artifact. Real-device test of the exact recorded scenario and final hub delivery still required; do not claim the two user-reported bugs are definitively resolved until that test.
