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
7. EPG / TV guide grid screen (`ui/guide/GuideScreen.kt`): a header "Guide" button loads
   (and caches to `AppStore.guideCache`) the current playlist's XMLTV guide via a new
   `PlaylistsViewModel.loadGuide()` (manual-refresh only, matching "Playlist refresh on
   start: off"), then renders a synced-scroll timeline grid — channel names in a fixed left
   column, programme blocks positioned by absolute offset (not simple flow, so gaps between
   programmes line up correctly across channels), a pinned-to-viewport current-time marker,
   titles hidden under ~90dp (TV-scaled from the original's 12px/min rule), and a "No guide
   data" placeholder per channel with no match. Selecting a block tunes/plays that channel
   like a normal channel row; long-press favourites it. Verified on-device with a synthetic
   XMLTV file (live-highlight, narrow-block title-hiding, D-pad scroll-into-view, tune and
   playback all confirmed working).
8. Multi-playlist switcher (`ui/components/PlaylistSwitcher.kt`): a header "Playlists"
   button (shown once ≥2 playlists exist) opens a list wired to the already-existing
   `PlaylistsViewModel.selectPlaylist(id)`. First version rendered the list as a translucent
   overlay on top of the channel list, which on-device testing caught as a real D-pad bug —
   focus search isn't blocked by the scrim, so Down/Center could leak through and act on a
   channel row behind it. Fixed by making it a full content swap (same pattern as the
   add-playlist form and the Guide screen), which traps focus correctly; re-verified.

Items 1–8 are committed (`git log --oneline` in this directory).

9. Polish pass (committed; **built and verified on the Google TV Streamer** — the session that
   wrote it had no Android SDK and no network path to `dl.google.com`/`maven.google.com`, so it
   went in uncompiled and untested. A later on-device pass found and fixed three real bugs
   before committing:
   - `PlayerScreen.kt`'s Up/Down were swapped: Up tuned to the *next* channel and Down to the
     *previous* one, backwards from both the write-up below and normal list-navigation
     convention. Fixed.
   - On playback failure, `PlayerView`'s own native controller (`useController = true`) stayed
     visible and focusable underneath the custom "Try again"/"Back to channels" overlay, so it
     silently stole D-pad focus and `DPAD_CENTER` — the buttons were unreachable. Fixed by
     hiding the controller (`useController = errorMessage == null`) and moving focus onto
     "Try again" via a `FocusRequester` when an error appears.
   - The same error state also swallowed `DPAD_LEFT`/`RIGHT` for resize-mode cycling
     unconditionally, so focus could never move from "Try again" to "Back to channels" (laid
     out to its right) — the second button was unreachable by D-pad. Fixed by only intercepting
     Up/Down/Left/Right for tuning/resize while `errorMessage == null`.
   The rest (tab-switch perf fix, resume-last-channel, empty-state copy, icon/banner) matched
   the write-up below as-is, confirmed on-device with a synthetic 43-channel/20-category test
   playlist.):
   - Fixed a real perf bug behind "switching category tabs is slow": `items(groups)` in
     `ChannelsBody` (`ui/main/MainScreen.kt`) reads `selectedCategory` to highlight the active
     row, so *every* category row recomposed on each tab switch, and each one recomputed
     `state.channels.count { it.group == g }` — a full scan of the whole playlist, per row,
     per switch (O(channels × groups) every keypress). Now a `groupCounts` map is built once
     per channel-list change and each row just looks itself up.
   - Resume-last-channel: reopening the app (or switching playlists) now defaults the category
     sidebar to "Recently watched" instead of "All channels" when there's a channel to resume
     to, rather than auto-tuning a stream on cold start.
   - Context-aware empty-state copy per category (favourites/recent/this-category/whole-playlist)
     instead of one generic "No channels here."
   - Player: failures now show "Try again" (re-prepares ExoPlayer in place) and "Back to
     channels" buttons instead of just inline text; Up/Down tune the previous/next channel
     in place (by `pos`, skipping hidden categories, replacing the backstack entry rather than
     pushing a new one so Back still returns straight to the channel list) with a transient
     "Now playing: …" label since there's no visible list during playback; Left/Right cycle
     the picture's resize mode (Fit/Zoom/Stretch) with the same transient-label treatment.
     `Playback` (`NavigationKeys.kt`) now also carries `channelKey` to look the channel back up.
   - Replaced the stock Compose-template adaptive icon (`drawable/ic_launcher_background.xml`,
     `ic_launcher_foreground.xml`) and the placeholder `tv_banner.png` with a considered mark: a
     rounded teal-gradient tile (`ThemeLight`→`ThemeHighlight`) with a white play triangle on
     `ThemeDark` — the same visual grammar as the channel-initials badge `ChannelRow` already
     draws per channel, rather than an unrelated generic glyph. Legacy pre-API26 mipmap webp
     fallbacks regenerated to match (unexercised on the actual API 34 test device, which uses
     the adaptive XML).

## Not started — each is meant to be its own chat
