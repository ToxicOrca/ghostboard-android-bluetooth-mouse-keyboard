# GhostBoard

Use your Android phone as a wireless Bluetooth mouse trackpad and keyboard for your PC. No server, no drivers, no app needed on the PC side — your phone connects directly as a standard Bluetooth HID device.

Built for the couch, the bed, or anywhere you want to control your PC without reaching for a physical keyboard and mouse.

<p align="center">
  <img src="ChatGPT Image Jun 28, 2026, 07_49_05 PM.png" alt="GhostBoard Icon" width="200"/>
</p>

## Screenshots

| Keyboard + Trackpad | Full Keyboard |
|---|---|
| ![Trackpad View](Screenshot_With_TP.jpg) | ![Keyboard View](Screenshot_With_KB.jpg) |

## Features

- **Bluetooth HID** — Connects directly to your PC as a standard keyboard and mouse. No companion app or server needed on the PC.
- **Trackpad** — Single finger drag to move the mouse. Tap to left-click. Two-finger tap to right-click. Two-finger drag to scroll.
- **Compact Keyboard** — Full QWERTY layout with numbers, symbols, modifiers (Shift, Ctrl, Alt, Win), arrow keys, Esc, Tab, Enter, Backspace, and Delete.
- **Dedicated Mouse Buttons** — Left and right click buttons at the bottom of the trackpad. Hold left click and drag on the trackpad to move windows.
- **Click-and-Drag** — Button state is preserved during mouse movement, so dragging windows and selecting text works properly.
- **Fullscreen Keyboard** — Toggle button to expand the keyboard to full screen for easier typing, then switch back to split view.
- **Sticky Modifiers** — Tap Ctrl, then tap a key for combos like Ctrl+C. Tap Win alone to open the Start menu.
- **Auto-Reconnect** — Remembers your last connected device and reconnects automatically when you reopen the app.
- **Dark Theme** — Easy on the eyes for use in low-light environments.
- **Idle Wake-Up** — Sends a wake-up pulse after inactivity so the first input after idle isn't delayed by Bluetooth sniff mode.
- **Optimized Input** — Mouse reports are throttled and sent on a dedicated background thread to minimize lag and keep the UI responsive.
- **Text Relay Bar** — Type with your phone's own keyboard (Chinese, Japanese, emoji…) and send it to the host as a keystroke sequence. Four modes, including **verbatim Unicode** — see [Text Relay](#text-relay--typing-chinese--cjk--emoji-from-your-phone).

## Requirements

- Android 9+ (API 28) — required for the Bluetooth HID Device API
- A PC with Bluetooth that supports HID input devices (most do)
- For **verbatim** text relay on macOS: the host must be switched to the **"Unicode Hex Input"** input source — see [the prerequisite](#️-host-prerequisite--switch-the-input-source-to-unicode-hex-input)

## Setup

1. **Download the APK** — Grab the latest APK from the [Releases](https://github.com/ToxicOrca/ghostboard-android-bluetooth-mouse-keyboard/releases) page. Transfer it to your phone and install (you may need to enable "Install from unknown sources" in your phone's settings).

   Or build it yourself:
   ```
   git clone https://github.com/ToxicOrca/ghostboard-android-bluetooth-mouse-keyboard.git
   cd ghostboard-android-bluetooth-mouse-keyboard
   ./gradlew assembleDebug
   ```
   The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

   > **On signing.** The git repository contains **no keystores and no prebuilt APKs** —
   > `.gitignore` covers `*.jks`, `*.keystore` and `*.apk` so they cannot be committed by
   > accident. An APK is only as trustworthy as the key it is signed with, so building it
   > yourself is preferred. If you want to publish your own build, generate your own keystore
   > and keep it **outside** the repository.

2. **Pair your phone with your PC:**
   - Open GhostBoard on your phone
   - Tap **Connect** > **Make Discoverable**
   - On your PC, go to Bluetooth settings and add a new device
   - Select your phone and pair

3. **Use it** — Once connected, the trackpad and keyboard are live. Minimize the app and reopen anytime — it auto-reconnects.

## How It Works

GhostBoard uses Android's [BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice) API to register your phone as a Bluetooth HID (Human Interface Device) combo device. It presents standard HID report descriptors for a keyboard and mouse, so the PC sees it as a regular input device — no special drivers or software needed.

## Text Relay — typing Chinese / CJK / emoji from your phone

Bluetooth HID is a *boot keyboard* protocol: it carries **keycodes only, never Unicode**.
There is no Unicode channel in the standard. So "send whatever I typed" needs a trick.

GhostBoard uses one that macOS already ships: **Unicode Hex Input**.

> ### ⚠️ Host prerequisite — switch the input source to "Unicode Hex Input"
>
> ```
> System Settings → Keyboard → Text Input → Input Sources → Edit… → ＋
>   → search "Unicode Hex Input" → Add → enable it
> ```
> Then switch to it — **Caps Lock**, `⌃Space`, or the menu-bar input menu.
>
> **Without this step every character arrives as the wrong symbol.** For example `测试`
> shows up as `§∂¢∫`. That is *not* a bug — it is macOS interpreting `Option`+hexdigit as
> ordinary Option-key symbols. If you see odd punctuation like `§∂¢∫•`, you are in this case:
> the pipeline is fine, the host input source is wrong.
>
> While "Unicode Hex Input" is active you cannot type Chinese *locally* on the Mac.
> That is expected and is the trade-off — the phone is supplying the Chinese.

### How it works

macOS's Unicode Hex Input inserts a character when you **hold `Option` and type the 4 hex digits
of its UTF-16 code unit, then release `Option`**. So `你` (U+4F60) is sent as
`⌥4` `⌥F` `⌥6` `⌥0`, then `Option` is released to commit it. Supplementary-plane characters
(emoji, rare CJK) are two adjacent UTF-16 surrogates, which this handles naturally.

**One detail that is easy to get wrong:** hosts coalesce **byte-identical consecutive HID
reports** into a single keypress. A real keyboard never produces those, because there is always a
key-up between two presses. A sender that only emits "key down" will therefore lose repeats —
`U+4E00` contains two consecutive `0`s, so one is dropped and *every following code point shifts
by one digit*. Symptom: `测试一下` comes out as `测试丄`. The fix is to explicitly release each key
while keeping the modifier held, so that no two consecutive reports are identical.

### Relay modes

| Mode | What is sent | Host requirement |
| --- | --- | --- |
| **Unicode · verbatim** | UTF-16 hex code units, with `Option` held | macOS input source = **Unicode Hex Input** |
| Pinyin · per character | Full pinyin + `Space` for each character | Host IME must be set to **full pinyin** |
| Pinyin · whole sentence | One full-pinyin run, then a single `Space` | Host IME must be set to **full pinyin** |
| Literal | Characters mapped straight to HID keycodes | US keyboard layout |

> **About the pinyin modes.** They emit **full pinyin** (`nihao`). If the host IME is set to a
> **shuangpin / double-pinyin** scheme (Xiaohe, Ziranma, Microsoft Shuangpin…), those letters get
> parsed as shuangpin codes, no candidate is ever committed, and you are left with raw letters on
> screen. If that happens, use **Unicode · verbatim** — it has no pinyin step at all and is
> completely unaffected by whichever scheme the host uses.

### Relay bar controls

- Type in the box, then press **Enter** or tap **Send**. Tap **Send** again while it is running to abort.
- **Long-press Send** (or tap the mode chip on the right) for relay options: mode, paste-macro target, key timing.
- `⇪中/英` sends **Caps Lock** — macOS switches between ABC and Chinese with Caps Lock.
- `⌃␣输入法` sends **Ctrl+Space** — switches the macOS input source.
  macOS never uses a bare **Shift** to switch input methods, so there is no Shift shortcut.

## Project Structure

```
app/src/main/java/com/example/remoteinput/
  MainActivity.kt              — Main activity, UI setup, connection flow, relay bar wiring
  bluetooth/
    BluetoothHidManager.kt      — Bluetooth HID registration, connection, report sending,
                                  ordered key sequences, held-modifier groups
  relay/
    RelayPlanner.kt             — Pure text → HID key sequence planning (all relay modes)
    PinyinRelay.kt              — Han character → full pinyin (TinyPinyin wrapper)
    UnicodeHexRelay.kt          — Text → macOS "Unicode Hex Input" key groups (verbatim mode)
    TextRelayController.kt      — Orchestration: persisted settings, send, progress, abort
  ui/
    TrackpadView.kt             — Touch trackpad with move, tap, scroll, two-finger gestures
    CompactKeyboardView.kt      — Custom-drawn QWERTY keyboard with HID keycodes
    HidKeyMapper.kt             — Character → HID keycode + modifier mapping
```

## Credits

- **App Icon** — Created by [OpenAI ChatGPT](https://chatgpt.com) (image generation)
- **Code** — Built with [Claude Code](https://claude.ai/code) by Anthropic

## License

MIT License — see [LICENSE](LICENSE) for details.
