# RamBox +

A music app for Android, made by [raihan-dev404](https://github.com/raihan-dev404).

## Features
- Stream and play music, queue, lyrics, equalizer, downloads
- **Sing / hum to find a song**: tap the mic on the Home screen, hum about 12 seconds, get matches.
  Uses an ACRCloud project with *Humming* enabled. Put your keys in `local.properties` (see `local.properties.example`) before building, so users need no setup.
- Song recognition for music playing nearby

## Build
1. Install Android Studio (latest stable) and open this folder.
2. Let Gradle sync, then pick the `app` module and press Run (or `./gradlew assembleDebug`).

## License
GNU General Public License v3.0, see [LICENSE](LICENSE). RamBox + is built on open-source code released under that licence.
