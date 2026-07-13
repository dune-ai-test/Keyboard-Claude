# Aura Keyboard — Modern Android IME

A production-oriented custom Android keyboard (Input Method Editor) built with Kotlin,
MVVM, Jetpack Compose (settings & clipboard UI), Room, and DataStore.

## Features implemented

- **QWERTY keyboard** with Shift, Caps Lock (double-tap Shift), Backspace, Enter, Space
- **Long-press character popups** for accented letters (à, é, ü, ñ, ç, …) and punctuation
  variants on `,` / `.`
- **Haptic feedback** on every key press (configurable strength), plus optional key click sounds
- **Separate Number (`?123`) and Symbol (`=\<`) layouts**, matching Gboard-style navigation
- **Word prediction** — on-device frequency + bigram (next-word) model, zero network calls
- **Auto-correction** — bounded Damerau–Levenshtein correction against a local dictionary,
  learns new/typed words locally over time
- **Swipe (gesture) typing** — custom shape-matching decoder (`GestureTypingDecoder`) that scores
  dictionary words against the finger's path in real time
- **Voice typing** — wraps Android's system `SpeechRecognizer` (no proprietary speech backend)
- **Clipboard manager** — Room-backed history with pin/unpin, one-tap paste, an inline strip in
  the keyboard itself, and a full-screen manager Activity (Compose)
- **Light / dark / system themes** (`values-night` resource set + Compose theme)
- **Settings screen** (Jetpack Compose + Material 3): keyboard height, vibration on/off &
  strength, sound on/off & volume, prediction, auto-correct, swipe typing, voice typing,
  auto-capitalize, double-space-for-period
- **Portrait & landscape support** via `values` / `values-land` dimension resources
- **Privacy & security**
  - No analytics, ads, or network calls anywhere in the app
  - Clipboard DB and learned-word dictionary excluded from Android cloud backup/device transfer
    (`data_extraction_rules.xml`)
  - Prediction, auto-correct, and clipboard capture are all automatically disabled in
    password/PIN fields (`InputType` detection in `CustomIME.onStartInputView`)
  - Voice typing goes through the OS's own speech service, not a bundled/cloud model this app
    controls
- **Performance & battery**
  - The keyboard is a single custom `View` (`KeyboardView`) that draws all keys in one `onDraw`
    pass with pre-allocated `Paint`/`RectF` objects — no per-key child Views, no per-frame
    allocations
  - Gesture-typing decoder resamples paths to a fixed point count so CPU cost is constant
    regardless of swipe length
  - Auto-correct uses bounded edit-distance with early exits instead of scanning the full
    dictionary with unbounded distance
  - Clipboard history is capped (50 unpinned entries) to bound storage/IO
  - `SoundPool`/system `AudioManager.playSoundEffect` used instead of heavier `MediaPlayer`

## Architecture (MVVM)

```
data/                     Models + repositories (Dictionary, SettingsRepository)
data/db/                  Room entities/DAO/DB + ClipboardRepository
viewmodel/                ClipboardViewModel, SettingsViewModel (AndroidViewModel)
ui/                       KeyboardView (custom View), SettingsActivity, ClipboardManagerActivity
                           (Compose UI observing ViewModels via StateFlow)
gesture/                  GestureTypingDecoder (swipe-to-word)
voice/                    VoiceInputHelper (SpeechRecognizer wrapper)
util/                     HapticUtil, SoundUtil
service/CustomIME.kt      InputMethodService — the "controller" wiring KeyboardView events to
                           repositories/engines. Implements LifecycleOwner + SavedStateRegistryOwner
                           manually so it can safely collect Kotlin Flows (Settings, Clipboard).
```

Both the IME service and the Compose Settings/Clipboard screens read from the **same**
`SettingsRepository` (DataStore) and `ClipboardRepository` (Room) singletons, so changes made in
the app UI apply to the live keyboard immediately, and vice versa.

## Building

1. Open the `CustomKeyboard/` folder in **Android Studio (Koala or newer)**.
2. Let Gradle sync (requires the standard Google/Maven Central repositories — the project uses
   AGP 8.5, Kotlin 1.9.24, Compose BOM 2024.06, Room 2.6.1, KSP).
3. Run the `app` module on a device/emulator (**API 24+**).
4. On first launch, tap **Enable** to open Android's *Manage keyboards* settings and turn on
   "Aura Keyboard", then tap **Switch Keyboard** (or the globe key on any keyboard) to select it.

> This repo ships without a `gradle-wrapper.jar` binary (sandboxed build environment has no
> access to Gradle's distribution servers). Android Studio will generate/download it
> automatically on first sync, or you can run `gradle wrapper` yourself with a local Gradle 8.7+
> install.

## Extending

- **Bigger dictionary**: replace `CommonWords.kt` with an asset-backed word-frequency file loaded
  in `Dictionary.loadBaseDictionary()` — the rest of the prediction/auto-correct/gesture-typing
  pipeline is dictionary-source-agnostic.
- **Emoji picker**: `KeyType.EMOJI` currently commits a single emoji as a placeholder; wire it to
  a full emoji picker Fragment/View for production use.
- **More languages/layouts**: add new `KeyboardLayout` builders in `KeyboardLayouts.kt` and a
  matching IME `subtype` entry in `res/xml/method.xml`.
