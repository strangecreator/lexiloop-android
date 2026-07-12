# LexiLoop for Android

Native Android client for [lexiloop.ru](https://lexiloop.ru) — AI-assisted English
vocabulary flashcards with spaced repetition.

## Features (v0.1)

- **Sign in / sign up** against the LexiLoop backend (DRF token auth).
- **Overview** — due counter, daily reviews, streak, retention, and your pools
  with per-pool due badges. Selecting a pool scopes Study and Library to it.
- **Study** — due queue and practice mode, all three task types
  (word → definition, definition → word, word → sentence), LLM-graded answers
  with feedback, "show answer" reveal, card images, and term pronunciation (TTS).
- **Library** — infinite-scroll card list with server-side search, expandable
  card details, block/unblock, and delete.
- **Settings** — account info, study preferences read from the server, sign out.

## Architecture

Single-module Kotlin app, MVVM with unidirectional data flow:

```
ui/        Jetpack Compose (Material 3) screens + ViewModels (StateFlow)
data/api/  Retrofit interface + kotlinx.serialization DTOs
data/auth/ SessionManager (DataStore-persisted token) + OkHttp auth interceptor
data/repo/ Repositories returning ApiResult<T> with parsed DRF error messages
di/        Hilt modules (OkHttp, Retrofit, Json, DataStore, app scope)
```

Key choices:

- **Jetpack Compose + Material 3** with dynamic color (Android 12+) and full
  dark-theme support; edge-to-edge with the splash-screen API.
- **Hilt** for dependency injection, **KSP** for annotation processing.
- **Retrofit + OkHttp + kotlinx.serialization** — tolerant JSON decoding
  (`ignoreUnknownKeys`, `coerceInputValues`) so backend additions never crash
  older app versions.
- **Coil** shares the authenticated OkHttp client, so protected card images and
  their `image_key` cache-busting work out of the box, backed by a 128 MB disk
  cache.
- **DataStore** holds the auth token; it is excluded from Android cloud backups
  and device transfers.
- **Performance**: immutable UI state data classes, `LazyColumn` with stable
  keys, debounced server-side search, incremental paging, R8 minification +
  resource shrinking for release builds.

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
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # JVM unit tests
./gradlew assembleRelease      # minified release (configure signing first)
```

CI builds and tests every push on GitHub Actions and uploads the debug APK as
a workflow artifact (`lexiloop-debug-apk`).

## Versions

- minSdk 26 (Android 8.0), targetSdk 35
- Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.10.01, Hilt 2.52
