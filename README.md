# ShakeLight (Flutter)

A simple Flutter cross-platform app that toggles the phone flashlight when a shake is detected from **gyroscope** readings.

## Features

- Uses `sensors_plus` gyroscope stream.
- Computes rotational magnitude (`sqrt(x² + y² + z²)`).
- Triggers flashlight ON/OFF when shake threshold is exceeded.
- Uses a cooldown to prevent rapid repeated toggles.
- Uses `torch_light` for camera flash control.

## Setup

1. Install Flutter SDK.
2. From project root:

```bash
flutter pub get
flutter run
```

## Notes

- Flashlight control works only on devices with a camera flash.
- Best tested on Android/iOS physical devices.
- You can tune shake behavior in `lib/main.dart`:
  - `_shakeThresholdRadPerSec`
  - `_toggleCooldown`
