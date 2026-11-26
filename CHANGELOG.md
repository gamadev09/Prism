# Changelog

All notable changes to **Prism** are documented in this file.

## [1.1.1] - 2026-11-26

### Added
- **Full Spanish Localization (`es`)**: Translated the entire launcher UI — including Home Screen, App Library, Universal Search, Widgets, Onboarding, Backup & Restore, and Launcher Settings.
- **In-App Language Selector**: Added a **Language** section under **Settings › General** with **System**, **English**, and **Español** options powered by Android 13+'s `LocaleManager`.
- **Automatic System Language Detection**: Defaults to **System**, automatically detecting whether the device is set to Spanish or English.
- **Per-App Locale Configuration**: Added `locales_config.xml` and wired `android:localeConfig` in `AndroidManifest.xml` for Android's system per-app language settings.

## [1.1.0]

### Highlights
- **Liquid Glass & Fast Glass (120 fps)** with Clear ↔ Tinted clarity control.
- **8 Built-In Icon Shapes** with live 4×2 preview in Settings.
- **True Biometric App Lock** for hidden apps.
- **Adaptive & 120 fps Spring Physics** with native Rust blur (`libliquidy_native.so`).
- **App Library, Folders, Widgets, Today View, and Layout Backup & Restore**.
