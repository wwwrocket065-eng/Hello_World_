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
- Settings: colors (RGB), transparency, stroke, corner radius, panel toggle, priority toggle, permissions.
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
