# ShakeLight

ShakeLight is a Flutter + native Android app that keeps a foreground sensor service alive, detects shake while the phone is locked, and toggles the flashlight.

## Build / run

```bash
flutter pub get
flutter run
```

> This repository now includes Android platform code under `android/`.

## Lock-screen mode setup (Android)

1. Open app and tap **Request Camera / Notification Permissions**.
2. Enable **Enable ShakeLight** to start the foreground service.
3. (Recommended) Tap **Ignore battery optimizations** and allow exception.
4. Lock the phone and shake to toggle torch.

## Settings

- **Sensitivity**: shake threshold (higher = harder shake needed).
- **Cooldown**: minimum milliseconds between toggles.
- **Start on boot**: restarts service after reboot via `BOOT_COMPLETED`.

## Battery / OEM caveats

Some manufacturers (MIUI, EMUI, ColorOS, etc.) aggressively stop background services. If ShakeLight stops while locked:

- Disable battery optimization for ShakeLight.
- Add app to auto-start/background whitelist in OEM settings.
- Keep notification visibility enabled on Android 13+.
