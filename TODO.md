# Playlist TV (Android) — TODO

Native Android TV rewrite of a reverse-engineered IPTV app (`de.herber_edevelopment.m3uiptv`),
replacing an earlier misdirected attempt (Craft Anything built a *desktop* Rust app by mistake —
see `../base/app`, kept around only as a reference for already-vetted parsing rules).

**Read before starting any task below:**
- `../base/app/MECHANICS.md` and `../base/app/mechanics.json` — the clean-room fact sheet
  reverse-engineered from the original APK (parsing rules, timing rules, the exact color
  palette and D-pad focus/selection styling to stay visually inspired by the original).
  Re-implement behavior from here; never copy the original's JS/HTML/CSS source.
- `../base/app/src/*.rs` — the earlier (desktop, now superseded) Rust implementation of the
  same rules. Already-tested reference logic for porting, not something to build on directly.

## Environment facts (verify these still hold at the start of a new chat)

- Test device: Google TV Streamer, codename `kirkwood`, Android 14 (API 34), MediaTek MT8696,
  **armeabi-v7a only, no 64-bit ABI**. Was reachable over adb at `192.168.68.54:5555` —
  DHCP may have changed it; ask the user to check Settings → Network & Internet if
  `adb connect` fails, or re-run adb mDNS discovery.
- Android SDK at `~/Android/Sdk`. This shell does **not** auto-source `~/.bashrc`, so export
  `ANDROID_HOME`/`PATH` inline in every Bash call that needs `android`/`adb`/`gradle`:
  ```
  export ANDROID_HOME="$HOME/Android/Sdk"; export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
  export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"   # needed for ./gradlew
  ```
- Use the `android` CLI (Google's agent-oriented tool, skill installed at
  `~/.claude/skills/android-cli`) for build/install/run/screenshot/layout-dump — not raw
  `adb install` + manual screenshots. `android layout -p` dumps a JSON UI tree with tap
  coordinates; `android screen capture -o <path>` screenshots the device.
- **This device has no reliable touchscreen.** `adb shell input tap` works for focusing a
  text field but not for pressing `Surface`-based buttons (tv-material3 `Surface.onClick`
  doesn't reliably respond to synthesized touch taps). Always drive the UI with real D-pad
  key events instead: `adb shell input keyevent KEYCODE_DPAD_{UP,DOWN,LEFT,RIGHT,CENTER}`,
  and `adb shell input keyevent --longpress KEYCODE_DPAD_CENTER` for long-press actions.
  Do **not** use `KEYCODE_TAB` to "fix" stuck focus during testing — a real remote doesn't
  have one, and it previously masked a genuine focus-traversal bug (now fixed, see below —
  same tunneling-key-intercept pattern will be needed anywhere else a text field sits above
  another focusable element).
- The window uses `android:windowSoftInputMode="adjustResize"` — on-screen element Y
  coordinates shift when the soft keyboard opens/closes. Re-dump `android layout` after any
  keyboard show/hide before tapping by coordinate; don't reuse stale coordinates.
- `adb shell run-as dev.playlisttv.app cat files/state.json` reads the persisted save file
  for debugging without pulling it off-device.
- `adb shell pm clear dev.playlisttv.app` resets to first-run (empty) state for testing.

## Done

1. Project scaffolded (Kotlin + Jetpack Compose, AGP 9.0.1, Kotlin 2.3.20, compileSdk 36 /
   targetSdk 34), builds/installs/runs on the real device.
2. Restyled for Android TV conventions (Compose-for-TV `androidx.tv` foundation/material,
   proper D-pad focus handling) using the original app's real palette and focus/selection
   language (teal `#118599` highlight, scale-on-focus, rounded corners, ⭐ favourites) —
   not a generic TV restyle. Leanback launcher manifest entries, banner, `INTERNET`
   permission added.
3. Data layer ported from the Rust prototype to Kotlin, verified against the same test
   cases (`app/src/test/kotlin/dev/playlisttv/app/data/`):
   - `data/Playlist.kt` — M3U/M3U8 parsing
   - `data/Guide.kt` — XMLTV parsing (Android's `XmlPullParser`; `kxml2` provides it for
     local JVM unit tests)
   - `data/Clock.kt` — timestamp rules, on `java.time` (needs core library desugaring,
     already configured, since minSdk 23 predates API 26)
   - `data/SavedState.kt` / `data/AppStore.kt` — settings/favourites/hidden-categories/
     recently-watched, JSON persistence via kotlinx.serialization, downloads via OkHttp
4. Wired into real screens: an empty-state "Add a playlist" form (downloads + parses +
   persists a real playlist), a two-pane layout (category sidebar with live counts +
   channel list), long-press to favourite a channel, long-press a category to hide/show it,
   a header "Show/Hide hidden" toggle. Selecting a channel records it to "Recently watched"
   and starts playback (see below).
5. Fixed a D-pad focus-traversal bug where Up/Down got stuck in the add-playlist text
   fields (single-line `EditText` consumes Up/Down itself for cursor-to-start/end before
   Compose's focus system sees it) — fixed via `Modifier.onPreviewKeyEvent` intercepting
   Up/Down and calling `focusManager.moveFocus()` directly. See
   `ui/components/AddPlaylistForm.kt`'s `dpadMovesFocus` for the pattern; reuse it anywhere
   else a text field sits next to other focusable controls.
6. ExoPlayer playback wired up: selecting a channel (not long-press) navigates to a new
   `Playback` destination (`NavigationKeys.kt`) rendering `ui/player/PlayerScreen.kt` —
   in-app full-screen `PlayerView` via `AndroidView`, not an external player. The Rust
   desktop prototype shelled out to an external player (MECHANICS.md "Estimated/chosen")
   because that's the natural fit for a desktop app with no bundled player UI; on Android TV
   there's no guarantee any external player is installed, and in-app playback is the
   idiomatic Leanback-expected experience, so that choice didn't carry over — decided, not
   just assumed. Per-channel `userAgent`/`referrer`/`headers` (from `Channel`, already
   parsed) are passed through to `DefaultHttpDataSource.Factory`. HLS and other stream types
   are handled by `DefaultMediaSourceFactory`'s automatic extension-based delegation (no
   manual `HlsMediaSource` wiring needed since `media3-exoplayer-hls` is on the classpath).
   Found and fixed a real bug during on-device testing: the device blocks cleartext
   (`http://`) traffic by default, which is extremely common for IPTV stream URLs — added
   `android:usesCleartextTraffic="true"` to the manifest. Verified with `android screen
   capture` that video actually renders (not just "no crash"), and that Back cleanly
   releases the player and returns to the channel list.

All of the above is committed (`git log --oneline` in this directory). No uncommitted work.

## Not started — each is meant to be its own chat

### 1. EPG / TV guide grid screen
Port the guide-grid rules already documented in `MECHANICS.md` ("Guide grid"): a timeline
(12 px/min desktop-scale → pick a TV-appropriate scale), 65px-equivalent rows, titles
hidden when a block is too narrow, current-time marker. `data/Guide.kt` already has the
parsed `Programme`/`Guide` data and `onAir()`; this task is almost entirely new UI plus
wiring a "download and parse the guide for the current playlist" action into
`PlaylistsViewModel` (the guide isn't fetched anywhere yet — only the playlist is).
Needs: a way to trigger guide refresh (manual button, matching "Playlist refresh on start:
off" decision), a grid Composable, per-channel "on now / on next" in the channel list or a
details pane.

### 2. Multi-playlist switching UI
`PlaylistsViewModel.selectPlaylist(id)` and the data model already support multiple
playlists; there's just no screen for it. Needs a small "Playlists" list (reuse
`CategoryItem`-style rows) in the header or a side panel, plus a way to re-open the
add-playlist form to add a 2nd/3rd playlist without it being the only option (the header
"+ Add playlist" button already does this — just needs a playlist switcher alongside it).

### 3. Smaller polish items (bundle into whichever task above makes sense, or their own pass)
- Replace the placeholder generated banner (`res/drawable-xhdpi/tv_banner.png`, made with a
  quick PIL script) with something more considered.
- Decide on a real app icon (still the Compose-template default).
- Error/empty states: what the channel list shows when a playlist has 0 channels, what a
  failed playlist download looks like beyond the current inline error text.
- Player polish: remember/resume last channel, a visible channel-switch affordance from
  inside playback (currently Back exits to the list rather than jumping to the next/prev
  channel), aspect-ratio/resize controls.
