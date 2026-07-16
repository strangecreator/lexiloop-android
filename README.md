# LexiLoop for Android

Native Android client for [lexiloop.ru](https://lexiloop.ru) — AI-assisted English
vocabulary flashcards with spaced repetition. The UI is a faithful native
replica of the site's mobile design: same palette, fonts, components, and
flows, built entirely in Jetpack Compose (no WebViews).

## Design fidelity

The design system is translated 1:1 from the web app's `styles.css`:

- **Palette** — the exact dark (`#0a0b0f` background, surface/border ladder)
  and light themes, plus all seven user-selectable accent colors (emerald,
  blue, teal, indigo, violet, rose, orange). Theme and accent follow the
  server-side settings, like on the site.
- **Typography** — DM Sans body + Manrope display (bundled variable fonts),
  matching web weights and letter-spacing.
- **Components** — panels, badges, eyebrows, stat cards, status pills, pool
  dots, score orb, judge banners, queue chips, toasts, and the modal style all
  mirror their CSS counterparts.
- **Shell** — the site's off-canvas sidebar is the navigation drawer: brand
  row, nav links with the due badge, pool list with actions (rename + color,
  copy, merge, delete), theme switch, user chip.

## Features

- **Auth** — the site's tabbed sign-in/sign-up card with the violet-gradient
  brand mark.
- **Overview** — hero panel with streak badge, retention ring, stat grid,
  GitHub-style vertical activity heatmap (absolute thresholds), pool cards.
- **Study** — due/practice rounds with progress track and queue-composition
  chips, all three task types, recall context clues with the letter-hint
  button and masked examples, LLM judge banner with score orb, tinted answer
  reveal with examples and chips, response-time display, card blocking,
  TTS pronunciation. Card images appear full-bleed behind the prompt with the
  site's cinematic reveal animations (morning mist, ripple, slow drift,
  watercolor droplets — blur-up thumb, luminance-aware scrim, per-card
  deterministic pick), and upcoming images are prefetched. Once a review is
  saved, the next card is prefetched in the background while the feedback is
  on screen, so "Next task" switches instantly. Enter on the keyboard submits
  the answer, the answer box scrolls above the soft keyboard when focused
  (toggleable, device-local), the card topline carries the site's per-task
  diagonal-tape texture so task types are distinguishable at a glance, and
  the queue refetches every time the page opens, so settings changes apply
  immediately.
- **Library** — the AI generator panel, server-side search, expandable card
  details (definition, examples, forms, synonyms, collocations, usage notes),
  block/unblock/edit/delete, pagination, full manual card editor, and the
  two-stage Bulk AI modal with live durable-job progress and failure report.
- **AI usage** — cost stat grid, daily spend chart, by-pool breakdown,
  recent failures / healthy state.
- **Offline mode** — the full card set (minus images) is cached on device and
  refreshed on every sync. Without a connection the app stays usable:
  Definition → word study continues with exact-match checking, the library and
  overview serve from the cache, and settings edits are queued. Everything
  recorded offline replays automatically when connectivity returns — reviews
  in the order they were made, settings field-by-field with the last write
  winning; if the server rejects a replayed event (card deleted, state moved
  on), that event is dropped so the server always stays the source of truth.
- **Settings** — the complete web settings form: generation/judge/sentence/
  image model pickers with catalog cards, per-provider API keys with staged
  edits (save/remove/undo), acceptance-score sliders, task types, appearance,
  daily new cards, and the advanced scheduler tuning section. The task
  types, interface color, study-image display options, reveal animations,
  image prefetch count, and automatic review timing bands are
  **device-local**: they follow the account settings until customized in the
  app, then live in DataStore and never overwrite the site's values (task
  types ride along as `?directions=`, timing bands with each review as
  `easy_seconds`/`good_seconds`, prefetch as `?prefetch=` — progress and
  scheduling stay account-wide either way). Device preferences sit in their
  own DataStore file that participates in Android Auto Backup and device
  transfer, so they survive an uninstall + reinstall; only the session store
  with the auth token is excluded from backups.

## Architecture

Single-module Kotlin app, MVVM with unidirectional data flow:

```
ui/theme/      LexiPalette (styles.css tokens), fonts, LexiTheme
ui/components/ The shared component vocabulary (buttons, fields, modals, …)
ui/shell/      ShellViewModel (pages, pools, settings, toasts) + sidebar
ui/…           One package per page: auth, overview, study, library,
               analytics, settings
data/api/      Retrofit interface + kotlinx.serialization DTOs
data/auth/     SessionManager (DataStore-persisted token) + auth interceptor
data/offline/  OfflineCache (JSON files), NetworkMonitor, SyncManager
data/repo/     ContentRepository (offline-aware), shared stores
di/            Hilt modules (OkHttp, Retrofit, Json, DataStore, app scope)
```

Key choices:

- **Retrofit + OkHttp + kotlinx.serialization** — tolerant JSON decoding
  (`ignoreUnknownKeys`, `coerceInputValues`) so backend additions never crash
  older app versions; DRF error payloads are parsed into readable messages.
- **Coil** shares the authenticated OkHttp client, so protected card images and
  their `image_key` cache-busting work out of the box, backed by a 128 MB disk
  cache.
- **DataStore** holds the auth token; it is excluded from Android cloud backups
  and device transfers.
- **Performance**: immutable UI state data classes, `LazyColumn` with stable
  keys, debounced server-side search, R8 minification + resource shrinking for
  release builds.

## Backend API

The app consumes the existing production API — no server changes required:

| Endpoint | Use |
|---|---|
| `POST /api/auth/login/`, `register/`, `logout/` | token auth |
| `GET /api/overview/` | stats + streak |
| `GET /api/pools/` | pool list with due counts |
| `GET /api/flashcards/` | paginated library + search |
| `POST /api/flashcards/{id}/suspend|unsuspend/`, `DELETE …/{id}/` | card management |
| `GET /api/study/next/` | study queue (due / practice) |
| `POST /api/study/{id}/judge/` | LLM answer grading |
| `POST /api/study/{id}/review/` | record review + reschedule |
| `GET /api/flashcards/{id}/image/` | card images (auth via shared OkHttp) |
| `GET /api/pronunciation/?text=` | TTS audio |

The base URL is a `BuildConfig` field (`API_BASE_URL`, defaults to
`https://lexiloop.ru`) in `app/build.gradle.kts`.

## Building

Requirements: JDK 17, Android SDK 35 (Android Studio Ladybug or newer).

```bash
./gradlew assembleRelease      # minified, signed release APK (install this one)
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
```

CI builds and tests every push on GitHub Actions and uploads both APKs as
workflow artifacts. **Install `lexiloop-release-apk`** — debug builds of
Jetpack Compose apps run without ahead-of-time compilation or R8 and are
noticeably slower; the release build is the one meant for real use.

### Release signing

Release builds are signed with `signing/lexiloop-release.p12`, a self-signed
development key checked into the repo so CI can produce installable builds
(passwords in `app/build.gradle.kts`). Because the key is public, treat these
builds as sideload-only: anyone could sign an APK with the same key, so never
distribute them through a store or download page. Before any real
distribution, generate a private keystore and point the build at it via
`LEXILOOP_KEYSTORE`, `LEXILOOP_KEYSTORE_PASSWORD`, `LEXILOOP_KEY_ALIAS`, and
`LEXILOOP_KEY_PASSWORD` (e.g. from GitHub Actions secrets) — no code changes
needed. Note that switching keys changes the APK signature, which requires
uninstalling the previously installed build once.

## Versions

- minSdk 26 (Android 8.0), targetSdk 35
- Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.10.01, Hilt 2.52
