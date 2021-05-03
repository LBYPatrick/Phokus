# Phokus

[![Build Status](https://travis-ci.com/LBYPatrick/Phokus.svg?branch=main)](https://travis-ci.com/LBYPatrick/Phokus)

The filming app that does it all.

# Feature List

- [x] Filmic cropmark
- [x] FPS Toggler
- [x] EV Slide bar
- [x] ZH_CN/EN_US Locale
- [x] 8-bit Log Profile*
- [x] AWB/AE Auto Lock
- [x] Travis CI
- [ ] Image Gallery     
- [ ] Advanced Settings Panel
- [ ] Touch-to-focus
- [ ] Face tracking (Based on ML Kit from Google) 
- [ ] Continuous AF

> *Currently not visible to user but could be manually turned on by the developer.

# Installation

Currently, only android devices that meets **ALL** criteria below could run the app smoothly:

- API 26+ (i.e. Android Oreo or above)
- LEVEL_3 Devices (The ones supporting DNG output)
- Support TONEMAP_CONTRAST_CURVE (i.e. Emulators can't)

For building the project, you need:

- Android Studio
- build-tools-29.0.3
- android-30
- Stable Internet Connection for Gradle Sync (Downloading external resources)

Then simply open the project with ``Android Studio`` and deploy the build to your favorite android device, and enjoy :)

# Credits

- [Android Jetpack - CameraX module](https://developer.android.com/training/camerax) 
- [Material Design](https://material.io)
- [Google ML Kit](https://developers.google.com/ml-kit)

# License

This repository is under MIT License with `LBYPatrick` being the owner of the license. See ``LICENSE`` for details.

