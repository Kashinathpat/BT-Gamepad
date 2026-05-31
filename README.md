# BT Gamepad

[![Android API](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat-square&logo=android)](https://developer.android.com/about/versions/pie)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0%2B-blue.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org/)
[![License](https://img.shields.io/badge/License-GPLv3-orange.svg?style=flat-square)](LICENSE)

An elegant Android application that transforms your smartphone into a fully-featured, wireless Bluetooth HID gamepad. By advertising itself as a standard **Bluetooth HID (Human Interface Device)**, it is recognized natively by PCs, consoles, and other mobile devices without requiring any custom drivers, client software, or server utilities on the host.

---

## Features

- **Native Bluetooth HID Protocol:** Leverages the Android system's standard Bluetooth HID Device profile for plug-and-play compatibility.
- **Dual Connection Modes:**
  * **Modern HID Mode:** Supports 12 flat buttons, a 2-axis D-pad (hat switch), and 4 separate analog axes.
  * **Windows Legacy DInput Mode:** Supports 16 flat buttons and 4 axes to maximize compatibility with retro systems and direct input wrappers.
- **Intuitive Floating Layout Editor:** Drag, resize, and arrange your on-screen buttons freely. Panels float translucent-glass style directly over a full-bleed grid-snapping canvas.
- **Precision Motion Controls:** Maps your device's built-in gyroscope to the right analog stick, with adjustable sensitivity.
- **Haptic Feedback:** Adjustable vibration strength on button presses.
- **Background Service:** A foreground service keeps the Bluetooth connection alive while the app is in the background.

---

## System Requirements

- **Android 9 (API level 28) or newer.**
- **Bluetooth HID Device Profile:** Your Android device hardware must support and have the HID peripheral profile enabled (most modern flagship/mid-range devices do).

---

## Building the Project

The application is built using standard Gradle.

### Build
Compile and output a debug build to `app/build/outputs/apk/debug/`:
```bash
./gradlew assembleDebug
```

---

## Usage Instructions

1. **Permissions:** Launch the app and grant the necessary Bluetooth (Discovery/Scanning/Advertise) and Notification permissions.
2. **Pairing:** Open the **Connect** tab. Go to your Host PC/Console's Bluetooth Settings and search for your phone. Connect and complete the standard pairing process.
3. **Layout Selection:** On the **Layouts** tab, pick a preset or hit edit to rearrange control placement.
4. **Play:** Start the controller interface. Touch inputs are instantly translated into HID packets.

---

## Troubleshooting & FAQs

### The host device is paired, but does not register any button presses.
- **Mode Mismatch:** Some operating systems do not automatically bind the secondary report descriptor layout. Switch the app mode in **Settings** between standard **HID Mode** and **Windows DInput Mode**, disconnect, and pair again.
- **Profile Authorization:** Ensure the Host OS has marked your phone specifically as an "Input Device" inside the Bluetooth properties panel.

### High Latency / Laggy Inputs
- **Coexistence Interference:** If your phone is connected to a 2.4 GHz Wi-Fi network while transmitting Bluetooth packages, bandwidth sharing may cause micro-stutters. Switch to a 5 GHz network or temporarily disable Wi-Fi.
- **Battery Optimizations:** Add `BT Gamepad` to your device's "Don't Optimize Battery" list so the system does not throttle the Bluetooth connection.

### The app fails to register the HID Profile.
- Some carrier-locked ROMs or budget devices intentionally strip the Bluetooth HID peripheral profile out of the kernel. If registration fails repeatedly, verify your device's profile capabilities using a third-party Bluetooth HID Tester app.

---

## License

This project is licensed under the GNU General Public License v3.0. You may use,
study, and modify the code, but any distributed derivative must also be released
under the GPL-3.0 — it cannot be made into a closed-source product. See the
`LICENSE` file for the full text.

Copyright (C) 2026 Kashinath Patkar
