# iOS Roadmap

Jetpack Compose Android app ported to SwiftUI iOS app. Every screen must look and
behave identically to the Android version. The iOS project lives in the `ios/`
subdirectory and is developed on macOS only.

---

## Pre-Phase — iOS Development Environment

The iOS project lives in `ios/`. It has its own `flake.nix` (Darwin-only devshell),
`.envrc`, and `justfile`. The root `justfile` gets `run-ios`, `deploy-ios`,
`build-ios`, `test-ios`, and `log-ios` recipes that delegate into `ios/`.

The iOS project is managed with **XcodeGen** — the canonical project definition is
`ios/project.yml`, from which `ios/Odyssey.xcodeproj` is regenerated whenever
dependencies or file lists change. This avoids committing the generated `.xcodeproj`
and makes diff reviews readable.

Dependencies are managed with **Swift Package Manager**, declared in `project.yml`
and resolved into `ios/Odyssey.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/`.

A `.device` file in `ios/` stores the UDID of the target physical device (same
pattern as the Android `.device` file). It is gitignored. `just devices` lists
connected device UDIDs; `just set-device <udid>` writes the file.

- [ ] Create `ios/` directory
- [ ] Create `ios/flake.nix` with Darwin-only devshell:
  ```nix
  {
    inputs = {
      nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    };

    outputs = { self, nixpkgs }: let
      system = "aarch64-darwin";
      pkgs = nixpkgs.legacyPackages.${system};
    in {
      devShells.${system}.default = pkgs.mkShell {
        packages = [
          pkgs.xcbeautify        # pretty-prints xcodebuild output
          pkgs.xcodegen          # generates Odyssey.xcodeproj from project.yml
          pkgs.ios-deploy        # installs and launches on physical device
          pkgs.libimobiledevice  # idevicesyslog + idevice_id
          pkgs.swiftformat       # source formatter
          pkgs.just
          pkgs.git
        ];
      };
    };
  }
  ```
- [ ] Create `ios/.envrc`:
  ```
  use flake
  ```
- [ ] Run `direnv allow` inside `ios/` to activate the shell
- [ ] Create `ios/project.yml` with XcodeGen project definition:
  - `name: Odyssey`
  - `bundleIdPrefix: xyz.chambaz`
  - iOS deployment target 17.0
  - Scheme `Odyssey` (debug + release)
  - Target `Odyssey` with sources `Odyssey/`, tests `OdysseyTests/`
  - Capabilities: `com.apple.developer.networking.wifi-info`,
    background modes `audio`, `fetch`, `processing`
  - Swift packages block (populated as dependencies are added)
- [ ] Run `xcodegen generate` to emit `ios/Odyssey.xcodeproj`
- [ ] Open project in Xcode, configure automatic code signing (team ID, provisioning)
- [ ] Create `ios/justfile`:
  ```just
  SCHEME  := "Odyssey"
  APP_ID  := "xyz.chambaz.odyssey"
  DERIVED := "build"

  # Build and launch on iPhone 16 simulator
  sim:
      xcodebuild -project Odyssey.xcodeproj -scheme {{SCHEME}} \
          -configuration Debug \
          -destination 'platform=iOS Simulator,name=iPhone 16' \
          -derivedDataPath {{DERIVED}} \
          build | xcbeautify
      xcrun simctl boot "iPhone 16" 2>/dev/null || true
      xcrun simctl install booted \
          {{DERIVED}}/Build/Products/Debug-iphonesimulator/Odyssey.app
      xcrun simctl launch --console booted {{APP_ID}}

  # Build and install on connected physical device (UDID in .device)
  run: _build-device
      ios-deploy \
          --bundle {{DERIVED}}/Build/Products/Debug-iphoneos/Odyssey.app \
          --no-wifi

  _build-device:
      xcodebuild -project Odyssey.xcodeproj -scheme {{SCHEME}} \
          -configuration Debug \
          -destination 'id=$(cat .device)' \
          -derivedDataPath {{DERIVED}} \
          build | xcbeautify

  # Build release archive
  release:
      xcodebuild -project Odyssey.xcodeproj -scheme {{SCHEME}} \
          -configuration Release \
          -destination 'generic/platform=iOS' \
          -derivedDataPath {{DERIVED}} \
          -archivePath {{DERIVED}}/Odyssey.xcarchive \
          archive | xcbeautify

  # Run unit tests on simulator
  test:
      xcodebuild test -project Odyssey.xcodeproj -scheme OdysseyTests \
          -destination 'platform=iOS Simulator,name=iPhone 16' \
          -derivedDataPath {{DERIVED}} \
          | xcbeautify

  # Stream logs from connected physical device
  log:
      idevicesyslog -m Odyssey

  # Stream logs from simulator
  log-sim:
      xcrun simctl spawn booted log stream \
          --predicate 'subsystem == "xyz.chambaz.odyssey"' \
          --level debug

  # List connected device UDIDs
  devices:
      idevice_id -l

  # List available simulators
  sims:
      xcrun simctl list devices available

  # Regenerate Xcode project from project.yml
  gen:
      xcodegen generate

  # Clean all build artifacts
  clean:
      rm -rf {{DERIVED}}

  # Store target device UDID
  set-device udid:
      echo '{{udid}}' > .device
  ```
- [ ] Add iOS recipes to the root `justfile` that delegate into `ios/`:
  ```just
  # Build iOS app (simulator)
  build-ios:
      cd ios && just sim

  # Install and run on connected iOS device
  run-ios:
      cd ios && just run

  # Build iOS release archive
  deploy-ios:
      cd ios && just release

  # Run iOS unit tests
  test-ios:
      cd ios && just test

  # Stream iOS device logs
  log-ios:
      cd ios && just log
  ```
- [ ] Add `ios/build/` and `ios/.device` to `.gitignore`
- [ ] Create directory skeleton:
  ```
  ios/
  ├── Odyssey/
  │   ├── App/
  │   │   ├── OdysseyApp.swift
  │   │   ├── Info.plist
  │   │   └── Odyssey.entitlements
  │   ├── Api/
  │   │   └── IliadApi.swift
  │   ├── Model/
  │   │   └── Models.swift
  │   ├── Player/
  │   │   └── Player.swift
  │   ├── Store/
  │   │   └── Store.swift
  │   └── UI/
  │       ├── Theme.swift
  │       ├── Fake.swift
  │       ├── LoginView.swift
  │       ├── MainView.swift
  │       ├── AudiobooksView.swift
  │       ├── LibraryView.swift
  │       ├── SearchView.swift
  │       ├── PlayerView.swift
  │       ├── CarModeView.swift
  │       └── SettingsView.swift
  └── OdysseyTests/
      ├── ApiTests.swift
      ├── ModelsTests.swift
      ├── PlayerTests.swift
      └── StoreTests.swift
  ```
- [ ] Verify `just build-ios` (from repo root) compiles a skeleton app and lands on the simulator

---

## Phase 1 — Models

Equivalent of `app/src/main/kotlin/xyz/chambaz/odyssey/model/Models.kt`. Four data
classes become four Swift structs conforming to `Codable`. The `@SerializedName`
annotations become `CodingKeys` enums with snake_case raw values. `Position`'s
`clientTimestamp` field maps to `timestamp` via `CodingKeys` (same rename as the
Gson annotation). All structs get `Equatable` for test assertions.

- [ ] `Credentials`: `baseUrl`, `username`, `password`, `token: String?` — plain struct, not `Codable` (never serialized to JSON, only to `UserDefaults`)
- [ ] `Audiobook`: `Codable` with `CodingKeys` for `archiveReady` (`archive_ready`); all optional fields (`date`, `description`, `genres`, `duration`, `size`, `cover`) remain optional
- [ ] `Chapter`: `title`, `path` — plain struct (parsed from YAML, not from JSON)
- [ ] `Position`: `Codable` with `CodingKeys` mapping `chapterIndex` → `chapter_index`, `chapterPosition` → `chapter_position`, `clientTimestamp` → `timestamp`; `clientTimestamp` is `Int64?`
- [ ] `DownloadState`: enum with cases `remote`, `inProgress`, `ready` (used by `Store`, not transmitted to server)
- [ ] Unit tests in `OdysseyTests/ModelsTests.swift`:
  - JSON round-trip for `Audiobook` (all optional fields present, all absent)
  - JSON round-trip for `Position` (timestamp present, timestamp nil)
  - `CodingKeys` correctness: decode a raw JSON literal and assert field values

---

## Phase 2 — Networking

Equivalent of `app/src/main/kotlin/xyz/chambaz/odyssey/api/IliadApi.kt`. All HTTP
calls use `URLSession` with `async/await`. JSON encoding/decoding uses `JSONEncoder`
/ `JSONDecoder` with `keyDecodingStrategy = .convertFromSnakeCase`. The auto-reauth
pattern — on 401 re-POST `/auth/login`, update stored token, retry once — is
reproduced in a private `execute(_:)` helper. Download streaming with `Range:` resume
and a progress callback uses `URLSession.bytes(for:)` (async byte stream). The active
download task is stored so it can be cancelled.

- [ ] `AuthError` and `ArchiveNotReadyError` error types
- [ ] `IliadApi` actor (or class with async methods) holding `credentials: Credentials`
- [ ] `func login(baseUrl:username:password:) async throws -> String`
  — POST `/auth/login`, decode `{ "token": "..." }`, return token
- [ ] `func register(baseUrl:username:password:) async throws -> String`
  — POST `/auth/register`, handle 409 as `"username taken"` error
- [ ] `func getAudiobooks() async throws -> [Audiobook]`
  — GET `/audiobooks`, decode array
- [ ] `func getAudiobook(hash:) async throws -> Audiobook`
  — GET `/audiobooks/<hash>`, decode single object
- [ ] `func downloadAudiobook(hash:to:startByte:onProgress:) async throws`
  — GET `/audiobooks/<hash>/download` with optional `Range:` header;
  stream bytes into a `FileHandle` opened for writing at `startByte`;
  call `onProgress(received, total)` per chunk;
  store the `URLSessionDataTask`/task so `cancelDownload()` can cancel it;
  throw `ArchiveNotReadyError` on 503
- [ ] `func cancelDownload()`
  — cancel the stored download task
- [ ] `func getPosition(hash:) async throws -> Position`
  — GET `/positions/<hash>`, decode
- [ ] `func putPosition(hash:position:) async throws`
  — PUT `/positions/<hash>`, encode body with `chapter_index`, `chapter_position`, `timestamp`
- [ ] Private `func execute<T>(_ request: URLRequest) async throws -> T` helper:
  - Attaches `Authorization: Bearer <token>` header
  - On 401: calls `reauth()`, retries once; if second 401 throws `AuthError`
- [ ] Private `func reauth() async throws -> String`
  — re-POST `/auth/login` with stored credentials, update `credentials.token`
- [ ] Unit tests in `OdysseyTests/ApiTests.swift`:
  - Response parsing for each endpoint (use mock `URLSession` or `URLProtocol` stub)
  - 401 auto-retry: assert login called once, request retried, succeeds on retry
  - 401 double-failure: assert `AuthError` thrown
  - 503 on download: assert `ArchiveNotReadyError` thrown
  - Resume download: assert `Range:` header sent when `startByte > 0`

---

## Phase 3 — Storage

Equivalent of `app/src/main/kotlin/xyz/chambaz/odyssey/store/Store.kt`. A `Prefs`
protocol over `UserDefaults` (identical structure to the Android `Prefs` interface)
enables unit testing with a `FakePrefs` dictionary. All values are stored as strings.
Key namespacing is identical: `pos_<hash>`, `pos_server_<hash>`, `dl_<hash>`,
`dur_<hash>`. `libraryDir(hash:filesDir:)` resolves against the stored download
location or falls back to `<filesDir>/library/<hash>`. `loadLocalAudiobooks(filesDir:)`
scans the library directory and parses `info.yml` for each book.

- [ ] `Prefs` protocol: `func string(forKey:) -> String?`, `func set(_:forKey:)`, `func remove(forKey:)`, `func clear()`
- [ ] `UserDefaultsAdapter: Prefs` backed by `UserDefaults.standard`
- [ ] `FakePrefs: Prefs` backed by `[String: String]` dictionary for tests
- [ ] `Store` class holding a `Prefs` instance with:
  - `saveCredentials(_:)` / `loadCredentials() -> Credentials?`
  - `savePosition(_:forHash:)` / `loadPosition(forHash:) -> Position?`
  - `saveServerPosition(_:forHash:)` / `loadServerPosition(forHash:) -> Position?`
  - `saveDownloadState(_:forHash:)` / `loadDownloadState(forHash:) -> DownloadState`
  - `saveTheme(_:)` / `loadTheme() -> String` (default `"black"`)
  - `saveAccentColorIndex(_:)` / `loadAccentColorIndex() -> Int` (default `0`)
  - `saveRewindOnResume(_:)` / `loadRewindOnResume() -> Int` (default `10`)
  - `saveVolumeNormalization(_:)` / `loadVolumeNormalization() -> Bool` (default `false`)
  - `saveLoudnessGain(_:)` / `loadLoudnessGain() -> Int` (default `1000`)
  - `saveDownloadLocation(_:)` / `loadDownloadLocation() -> String` (default `""`)
  - `saveCellularDownload(_:)` / `loadCellularDownload() -> Bool` (default `false`)
  - `saveShakeToExtend(_:)` / `loadShakeToExtend() -> Bool` (default `true`)
  - `saveAutoCarMode(_:)` / `loadAutoCarMode() -> Bool` (default `false`)
  - `savePlaybackSpeed(_:)` / `loadPlaybackSpeed() -> Float` (default `1.0`)
  - `saveDuration(_:forHash:)` / `loadDuration(forHash:) -> Int64?`
  - `clearAll()`
  - `func libraryDir(hash: String, filesDir: URL) -> URL`
  - `func loadLocalAudiobooks(filesDir: URL) -> [(String, Audiobook)]`
    parses `info.yml` line by line (same logic as Android: title, author, date,
    description, genres list, chapter count, cover path)
- [ ] Unit tests in `OdysseyTests/StoreTests.swift`:
  - Round-trip for every `save`/`load` pair using `FakePrefs`
  - `loadDownloadState` returns `.remote` for unknown hash
  - `loadCredentials` returns nil when any required key is missing
  - `clearAll` wipes every persisted key

---

## Phase 3.5 — UI Scaffolding

All screens built as navigable skeletons with hardcoded fake data. No real API calls.
Layout and navigation are not final. This phase exists so every screen can be walked
through before real logic is wired in. Equivalent of the Android Phase 3.5 which
produced `Fake.kt`, `Theme.kt`, and all the screen files.

The navigation model mirrors Android: a single `ContentView` owns a `screen` state
enum and dispatches to the correct view. No `NavigationStack` — direct conditional
rendering, same as the Android `when(screen)` block.

Files to create:

- `UI/Theme.swift` — `OdysseyTheme` applying Material-style colors via SwiftUI
  `ColorScheme`, accent color computed property, `accentColors` array
- `UI/Fake.swift` — `fakeBooks: [Audiobook]` (3 entries), `fakeChapters: [Chapter]`
  (5 entries), `fakePosition: Position`
- `App/OdysseyApp.swift` — `@main` entry point, creates `Store` and root `ContentView`
- `UI/LoginView.swift` — server URL, username, password fields, Login / Register buttons
- `UI/MainView.swift` — horizontal pager with Audiobooks and Library tabs,
  custom bottom tab bar with animated sliding underline indicator
- `UI/AudiobooksView.swift` — local book list with progress, sort tabs, long-press detail
- `UI/LibraryView.swift` — remote book list with download state chips, pull-to-refresh
- `UI/SearchView.swift` — shared search layout, separate instances for each tab
- `UI/PlayerView.swift` — cover, chapter row, seekbar, 5-button controls,
  4-button action row, all four bottom sheets as stubs
- `UI/CarModeView.swift` — large controls, cover, progress bar, always-on screen
- `UI/SettingsView.swift` — all setting rows, logout dialog

Navigation wired: Login → Main ↔ Settings, Main → Player → Car Mode.

- [ ] `Screen` enum: `login`, `main`, `librarySearch`, `audiobooksSearch`, `player`, `carMode`, `settings`
- [ ] `ContentView` with `@State var screen: Screen` and `switch screen { }` dispatch
- [ ] `OdysseyTheme` applying light/dark/black color schemes and accent color
- [ ] `accentColors: [Color]` array (same palette as Android)
- [ ] `Fake.swift` with 3 fake audiobooks and 5 fake chapters
- [ ] `LoginView` skeleton
- [ ] `MainView` skeleton with `TabView`-style pager and custom bottom bar
- [ ] `AudiobooksView` skeleton
- [ ] `LibraryView` skeleton with `BookDetailSheet` and `FilterSheet` stubs
- [ ] `SearchView` skeleton
- [ ] `PlayerView` skeleton with all four sheets wired as `showChapters`, `showSpeed`, `showTimer`, `showTrackDetail` bools
- [ ] `CarModeView` skeleton
- [ ] `SettingsView` skeleton with logout confirmation dialog
- [ ] `just build-ios` produces a working skeleton navigable through all screens

---

## Phase 4 — Login

Equivalent of Android Phase 4. `LoginView` calls real `IliadApi` methods instead of
no-ops. On success, saves `Credentials` via `Store` and switches `screen` to `.main`.
On launch with stored credentials, skips login entirely. Error states shown inline
below the button. Implemented in `LoginView.swift`; auth logic in `ContentView`.

- [ ] Read stored credentials on app launch; if present navigate to `.main` directly
- [ ] `onLogin`: call `api.login(baseUrl:username:password:)`, save `Credentials`, set `api`, navigate
- [ ] `onRegister`: call `api.register(...)`, handle 409 as "username already taken" error message
- [ ] Display inline error text below button for: wrong password, server unreachable, username taken
- [ ] Show a loading indicator on the button while the request is in flight
- [ ] Keyboard return on password field triggers login action
- [ ] Secure text entry on password field

---

## Phase 5 — Library

Equivalent of Android Phase 5. `LibraryView` fetches `GET /audiobooks` on appear and
displays the server catalog. Download flow polls `GET /audiobooks/<hash>` until
`archiveReady`, then streams the binary to a `.tar.gz` file, extracts it, and marks
the book ready. Implemented in `LibraryView.swift`, `ContentView` (download logic),
`IliadApi.swift`. Android equivalents: `LibraryScreen.kt`, `MainActivity.kt`
(`startDownload`, `onDownload`).

- [ ] Fetch `GET /audiobooks` on view appear; store in `@State var books: [Audiobook]`
- [ ] Pull-to-refresh via SwiftUI `.refreshable` modifier
- [ ] Per-book `DownloadState` from `Store` displayed as a chip/badge on each row
- [ ] `BookDetailSheet` download action:
  - If `archiveReady == false`: poll `GET /audiobooks/<hash>` every 3 s until true, show "Preparing…" state
  - Fetch with optional `Range:` resume if partial `.tar.gz` already exists
  - Stream to `<libraryDir>/<hash>.tar.gz` with per-chunk progress callback
  - Update `downloadProgress[hash]` shown as progress bar in notification and UI
  - Extract tar.gz to `<libraryDir>/<hash>/` (see tar.gz extraction note below)
  - Delete `.tar.gz` after successful extraction
  - Save `duration` to `Store` from book metadata
  - Reload `localAudiobooks` after extraction
  - Fetch and cache server position after download completes
- [ ] Download notification: `UNUserNotificationCenter` notification with title, `%` progress, Cancel action
  - Cancel action sends a `NotificationCenter` post that cancels the `URLSession` task and deletes the partial file
- [ ] Cellular download guard: if `!store.loadCellularDownload()` and active interface is cellular, show confirmation dialog before starting
- [ ] `FilterSheet`: date range slider, duration range slider, genre multi-select chips; Apply recomputes `filteredBooks`
- [ ] `LibrarySearch` screen: text input, filter `filteredBooks` by title/author substring (case-insensitive)
- [ ] **tar.gz extraction**: add `swift-libarchive` or equivalent Swift package to `project.yml`;
  extract each entry from the `.tar.gz` into `<libraryDir>/<hash>/`, creating intermediate directories
- [ ] **Background downloads**: use `URLSession(configuration: .background(withIdentifier: "xyz.chambaz.odyssey.download"))` so downloads survive app suspension; implement `URLSessionDownloadDelegate` to handle completion and progress in the background
- [ ] Unit tests: response parsing for book list, 503 handling, resume byte offset in `Range:` header

---

## Phase 6 — Audiobooks

Equivalent of Android Phase 6. `AudiobooksView` lists books from `localAudiobooks`
(scanned from `filesDir/library/`). Covers come from `info.yml` `cover:` field.
Progress derives from stored `Position` and chapter count. Sort order: in-progress
→ not started → finished, then by most recent position timestamp, then most recent
download, then alphabetical. Implemented in `AudiobooksView.swift`, `Store.swift`
(`loadLocalAudiobooks`). Android equivalent: `BooksScreen.kt`, `MainActivity.kt`
(cover/chapter/timestamp loading).

- [ ] `loadLocalAudiobooks(filesDir:)` already implemented in Phase 3; wire it into `ContentView` state on appear
- [ ] Load cover image from `<libraryDir>/<hash>/<cover-path>` (parsed from `info.yml`); cache as `[String: UIImage]`
- [ ] Load chapter count from `info.yml` `chapters:` block; cache as `[String: Int]`
- [ ] Load download timestamp from directory `modificationDate`; cache as `[String: Date]`
- [ ] Load description from `info.yml` `description:` field; cache as `[String: String?]`
- [ ] Progress bar: `(chapterIndex / chapterCount)` as a fraction, shown as a short horizontal bar; percentage label
- [ ] Sort: bucket into in-progress (0 < position < last chapter end) / not started / finished; within bucket sort by position timestamp desc, then download date desc, then title asc
- [ ] Long-press on a row opens `BookDetailSheet` (same sheet as Library) with delete action
- [ ] Delete: remove `<libraryDir>/<hash>/`, clear `DownloadState` in `Store`, reload `localAudiobooks`
- [ ] `AudiobooksSearch` screen: filter `localAudiobooks` by title/author substring
- [ ] `BookDetailSheet` cover image sourced from the `Audiobook.cover` field (base64 string from API response) when available, else from local file

---

## Phase 7 — Player

Equivalent of Android Phase 7. `PlayerView` plays audio using `AVPlayer`. Chapter
files are loaded one at a time from `<libraryDir>/<hash>/<chapter.path>`. Chapter
sequencing, seek, speed, and position sync are all implemented here. Before allowing
playback, the app fetches `GET /positions/<hash>` and resolves the starting position
using the same merge algorithm as Android. Implemented in `PlayerView.swift`,
`Player.swift`, `ContentView` (state + navigation). Android equivalents: the large
`LaunchedEffect` block in `MainActivity.kt`, `Player.kt` (`parseInfoYml`).

**AVFoundation audio architecture**: use `AVPlayer` (not `AVQueuePlayer`). Replace the
player item each time `currentChapter` changes. Speed control via `AVPlayer.rate`
with `AVAudioTimePitchAlgorithm.timeDomain` for pitch-preserving playback at non-1×
speeds. Chapter completion via `AVPlayerItem.didPlayToEndTime` notification.

**Background audio**: configure `AVAudioSession` with `.playback` category and
`.mixWithOthers` option removed; activate session when playback begins; declare
`audio` background mode in `Info.plist` (already in `project.yml`).

**Lock screen + headphone controls**: `MPNowPlayingInfoCenter` for metadata and cover;
`MPRemoteCommandCenter` for play/pause, seek, next/previous track commands.

- [ ] `parseInfoYml(file:) -> [Chapter]` in `Player.swift` — identical logic to `Player.kt`:
  scan lines, find `chapters:` block, collect `- title:` / `path:` pairs
- [ ] `AVPlayer` setup in `ContentView` (or a `PlayerController` observable):
  - On `playerBook` or `currentChapter` change: create new `AVPlayerItem`, replace player item, seek to `pendingSeekMs`
  - Apply speed: `player.rate = speedRaw` after `play()`; set `currentItem?.audioTimePitchAlgorithm = .timeDomain`
  - Chapter end: observe `AVPlayerItem.didPlayToEndTimeNotification`; if not last chapter, advance `currentChapter` and set `pendingSeekMs = 0`; else set `playing = false`
  - Slider update: `AVPlayer.addPeriodicTimeObserver` every 0.5 s while playing
- [ ] `AVAudioSession` category `.playback` activated on first play, deactivated when stopped
- [ ] `MPNowPlayingInfoCenter` updated whenever `playing`, `playerBook`, `sliderValue`, or `currentChapter` changes
- [ ] `MPRemoteCommandCenter` handlers: play/pause toggle, change playback position, next/previous track
- [ ] Position fetch-before-play: same merge algorithm as Android (`MainActivity.kt` lines 746–808):
  - Fetch server position; load local `pos_<hash>` and `pos_server_<hash>`
  - Nearly identical (same chapter, < 30 s apart): pick earlier, sync both
  - Server unchanged since last sync, local newer: local wins, push to server
  - Server timestamp newer: server wins, save locally
  - Local timestamp newer: conflict dialog (see Phase 9)
  - Fallback on network error: use cached local position
- [ ] Rewind on resume: subtract `store.loadRewindOnResume() * 1000` ms from resolved position (clamped to 0)
- [ ] `syncPosition(hash:)`: write to `Store`, call `api.putPosition`, update `pos_server_<hash>`
  - Triggered on: pause, chapter change, `scenePhase == .background`, app terminate
- [ ] Position sync loop: `Task` with `try await Task.sleep(for: .seconds(30))` while playing
- [ ] `scenePhase` observation (`.onChange(of: scenePhase)`): sync on `.background`
- [ ] `playerChapters` set after position is resolved (same ordering as Android: resolve position first, then set chapters, so chapter setup fires once with correct seek target)

---

## Phase 8 — Player Sheets

Equivalent of Android Phase 8. All four bottom sheets on `PlayerView` wired to real
state. The chapter list sheet gains the new **scroll-to-current** behavior absent from
Android. Implemented in `PlayerView.swift`. Android equivalents: the four
`ModalBottomSheet` blocks inside `PlayerScreen.kt`.

**Chapter list scroll**: when the chapter list sheet appears, use a `ScrollViewReader`
to call `proxy.scrollTo(currentChapter, anchor: .center)` inside `.onAppear`. This
ensures the user immediately sees the currently playing chapter without needing to
scroll manually.

- [ ] **Chapter list sheet**: `ScrollViewReader` wrapping a `LazyVStack` of chapter rows;
  on `.onAppear` call `proxy.scrollTo(currentChapter, anchor: .center)`;
  current chapter highlighted in accent color with bold weight;
  tap row calls `onCurrentChapterChange(index)` and dismisses sheet
- [ ] **Speed sheet**: slider 0.5–3.5×, –/+ step buttons (0.5 step), preset chips (0.5×, 1×, 1.5×, 2×, 2.5×, 3×);
  apply to `AVPlayer.rate` immediately; persist via `store.savePlaybackSpeed`;
  draw a vertical 1× tick mark on the slider track using `Canvas`
- [ ] **Sleep timer sheet**: options Off / 5 min / 10 min / 15 min / 30 min / 45 min / 60 min / End of chapter;
  store `timerEndMs: Date?` (nil = off, `.distantPast` sentinel = end-of-chapter);
  countdown display in sheet header; on expiry begin 10 s volume fade-out then pause + sync;
  grace window and shake logic → see Phase 10
- [ ] **Track detail sheet**: title, author, date, genres, elapsed/duration, description; read-only metadata display
- [ ] **Car mode button**: navigates to `.carMode` screen (or triggers auto-detection check; see Phase 10)

---

## Phase 9 — System

Equivalent of Android Phase 9. Offline handling, position conflict dialog, and logout.
Implemented in `ContentView` (conflict state, logout action), `PlayerView` (conflict
dialog presentation), `SettingsView` (logout button). Android equivalents:
`MainActivity.kt` (positionConflict state, onLogout lambda, lifecycle observer),
the conflict `Dialog` composable at the bottom of `setContent`.

- [ ] Position conflict dialog: triggered when local timestamp > server timestamp after fetch-before-play
  (case already identified in Phase 7 merge algorithm);
  show chapter + timestamp for both local and server positions;
  "Keep Local" pushes local position to server; "Take Server" seeks to server position
- [ ] Offline handling: if position fetch fails, fall back to `loadPosition(forHash:)` and play from cached;
  no conflict dialog shown when server was unreachable (only when both positions are present and differ)
- [ ] Logout confirmation dialog in `SettingsView`: "Are you sure?" with Cancel / Logout buttons
- [ ] Logout action: `store.clearAll()`, delete `<libraryBase>/` recursively, set `api = nil`, navigate to `.login`

---

## Phase 10 — Polish

Equivalent of Android Phase 10, with two modifications: car mode detection by device
name heuristic instead of manual device list, and the volume normalization approach is
simplified.

### Always-on screen in car mode

When `CarModeView` appears, disable the idle timer (`UIApplication.shared.isIdleTimerDisabled = true`)
and call `UIApplication.shared.isIdleTimerDisabled = false` on disappear via
`.onAppear` / `.onDisappear`. Android equivalent: `DisposableEffect(Unit)` setting
`FLAG_KEEP_SCREEN_ON`, `setShowWhenLocked(true)`, `setTurnScreenOn(true)` in
`CarModeView.kt`.

- [ ] `.onAppear`: `UIApplication.shared.isIdleTimerDisabled = true`
- [ ] `.onDisappear`: `UIApplication.shared.isIdleTimerDisabled = false`

### Sleep timer fade-out and shake to extend

When the sleep timer fires (or the end-of-chapter sentinel triggers), the app does
not pause immediately. Instead it begins a 10-second linear volume fade-out, stepping
`AVPlayer.volume` from 1.0 to 0.0 in 0.1 increments every second via a `Task` loop.
Playback pauses and `syncPosition` fires once the fade completes. Android equivalent:
a coroutine in the `LaunchedEffect(timerEndMs)` block in `MainActivity.kt` calling
`mediaPlayer.setVolume` in a step loop before pausing.

The grace window opens at the moment the fade begins — not after silence — giving the
user the fading audio as an audible cue. Total grace duration is 70 seconds: the 10 s
fade plus 60 s of silence. During the entire window `CMMotionManager` samples the
accelerometer at 10 Hz; a reading where `sqrt(x²+y²+z²) >= 15.0` counts as a shake.
On shake: restore `AVPlayer.volume` to 1.0 immediately, restart the timer with the
original duration, resume playback, and close the grace window. If no shake arrives
within 70 s, close the grace window and leave the player paused. Surfaced as a toggle
in Settings (default on). Android equivalent: `DisposableEffect(timerGraceEndMs)` in
`MainActivity.kt` registering `TYPE_ACCELEROMETER`, with `timerGraceEndMs` set at
fade start.

- [ ] On timer expiry (or end-of-chapter): begin fade `Task` — 10 iterations of `AVPlayer.volume -= 0.1` with `Task.sleep(for: .seconds(1))`; restore volume to 1.0 at task end regardless of outcome (shake may cancel it early)
- [ ] At fade start: if `store.loadShakeToExtend()`, set `graceEndsAt = Date() + 70` and start `CMMotionManager` accelerometer updates
- [ ] After fade completes (if grace still open): pause playback and `syncPosition`
- [ ] On shake (magnitude ≥ 15 m/s²): cancel fade task if still running, restore `AVPlayer.volume = 1.0`, restart timer to `originalTimerDuration` from now, set `playing = true`, nil out grace state, stop accelerometer
- [ ] After 70 s without shake: nil out grace state, stop accelerometer, keep paused
- [ ] Settings toggle: `shakeToExtend` bool, saved to `Store`

### Background position sync

The 30 s sync loop (`Task.sleep` inside a `Task` keyed on `playing`) runs for the
lifetime of the playing session. When `scenePhase` transitions to `.background`,
`syncPosition` is called immediately. This mirrors the Android `ON_PAUSE` lifecycle
observer and the `LaunchedEffect(playing)` sync loop in `MainActivity.kt`.

- [ ] `Task` while `playing`: `try await Task.sleep(for: .seconds(30))`; `await syncPosition(hash)`; loop
- [ ] Cancel that task when `playing` becomes false or `playerBook` changes
- [ ] `.onChange(of: scenePhase)`: on `.background`, fire `syncPosition` immediately

### Download location picker

`UIDocumentPickerViewController` in directory-selection mode (`forOpeningContentTypes: [.folder]`).
On selection, bookmark the security-scoped URL with `url.bookmarkData(options: .withSecurityScope)`,
store the bookmark data in `UserDefaults`. On each launch, resolve the bookmark and
call `url.startAccessingSecurityScopedResource()`. `store.libraryDir(hash:filesDir:)` already
resolves against the stored location. If no location is stored, falls back to
`filesDir/library/<hash>`. Android equivalent: `ActivityResultContracts.OpenDocumentTree` in
`MainActivity.kt` with `takePersistableUriPermission`.

- [ ] Wrap `UIDocumentPickerViewController` as a SwiftUI representable `FolderPicker`
- [ ] On successful pick: create security-scoped bookmark, save bookmark data to `UserDefaults` under `"downloadLocationBookmark"`; also save the path string to `Store` for display
- [ ] On app launch: resolve bookmark if present, call `startAccessingSecurityScopedResource`
- [ ] Settings row: current path display (or "Internal storage"); "Change" button launches picker
- [ ] File migration: on location change, move existing `library/` contents to the new path (same logic as Android)

### Volume normalization

iOS has no direct equivalent to Android's `LoudnessEnhancer`. Route audio through
`AVAudioEngine` with an `AVAudioUnitEQ` node configured as a low-shelf boost, or
use `AVAudioUnitTimePitch` in a pipeline. This is architecturally more complex than
plain `AVPlayer`. For the initial iOS port: implement as a toggle that, when enabled,
routes through `AVAudioEngine` + `AVAudioPlayerNode` + `AVAudioUnitEQ`; when disabled,
use plain `AVPlayer`. Keep the two paths clearly separated. Android equivalent:
`LoudnessEnhancer(mediaPlayer.audioSessionId)` attached in the chapter-setup block of
`MainActivity.kt`.

- [ ] Settings toggle: `volumeNormalization` bool, saved to `Store`
- [ ] When enabled: create `AVAudioEngine`, attach `AVAudioPlayerNode` and `AVAudioUnitEQ` (single low-shelf band, gain from `store.loadLoudnessGain()` in dB, centred at 1 kHz); schedule audio file for playback
- [ ] When disabled: tear down engine, switch back to `AVPlayer` for current chapter
- [ ] Chapter change: re-schedule new file on the engine player node (or reset `AVPlayer` depending on which path is active)
- [ ] On chapter seek: use `AVAudioPlayerNode.scheduleSegment` with the correct frame offset when engine is active

### Automatic car mode

When audio routes to a Bluetooth output, the app checks the device's `portName`
against a name heuristic to decide whether it is likely a car stereo. The heuristic
is a case-insensitive match against known car audio brand names (Pioneer, Kenwood,
Alpine, JVC, Clarion, Sony, Blaupunkt, Harman, Jensen, Boss, Dual, Rockford) and
generic keywords ("car", "auto", "stereo", "radio", "head unit", "handsfree",
"hands-free", "carplay", "bt-"). It is intentionally permissive — a false positive
(switching to car mode unexpectedly) is less harmful than a false negative.

When `AVAudioSession.routeChangeNotification` fires with `.newDeviceAvailable` reason:
- Get the new route's outputs
- Find any output with `portType == .bluetoothA2DP` or `.bluetoothHFP`
- Run the name heuristic against `portType.portName`
- If match and `store.loadAutoCarMode()`, navigate to `.carMode`

Settings: single "Automatic car mode" toggle.

- [ ] Settings: single `Switch` for "Automatic car mode" backed by `store.saveAutoCarMode`
- [ ] Register `AVAudioSession.routeChangeNotification` observer in `ContentView.onAppear`
- [ ] On `.newDeviceAvailable`: inspect `AVAudioSession.sharedInstance().currentRoute.outputs`
- [ ] Name heuristic function `isCarDevice(portName: String) -> Bool`:
  ```swift
  private let carBrands = ["pioneer", "kenwood", "alpine", "jvc", "clarion",
                            "sony", "blaupunkt", "harman", "jensen", "boss",
                            "rockford", "dual"]
  private let carKeywords = ["car", "auto", "stereo", "radio", "head unit",
                              "handsfree", "hands-free", "carplay", "bt-"]
  func isCarDevice(_ name: String) -> Bool {
      let lower = name.lowercased()
      return carBrands.any { lower.contains($0) } ||
             carKeywords.any { lower.contains($0) }
  }
  ```
- [ ] On match + `autoCarMode` enabled + currently playing: navigate to `.carMode`
- [ ] On `.oldDeviceUnavailable`: if current screen is `.carMode`, navigate back to `.player`
- [ ] When user manually enters player (`navigateToPlayer`): run the same heuristic check against current route outputs to decide `.player` vs `.carMode` at open time
