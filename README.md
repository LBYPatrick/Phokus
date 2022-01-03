# Phokus

[![Build Status](https://travis-ci.com/LBYPatrick/Phokus.svg?branch=main)](https://travis-ci.com/LBYPatrick/Phokus)

An elegant yet powerful camera app.

# Feature List

- [x] *~~Filmic cropmark~~
- [x] **FPS Toggler
- [x] **EV Slide bar
- [x] ZH_CN/EN_US Locale
- [x] ***8-bit Log Profile
- [x] AWB/AE Auto Lock
- [x] ****Travis CI
- [ ] Image Gallery     
- [ ] Advanced Settings Panel
- [x] Touch-to-focus
- [ ] Face tracking (Based on ML Kit from Google) 
- [x] Continuous AF

> *Nah, it's ugly and useless, REMOVED  
> **I am redesigning UI (again?!) so these features are temporarily unavailable.  
> ***Currently not visible to user but could be manually turned on by the developer.  
> ****Travis CI broke again! Google makes changes to their compilation process behind the scenes all the time without letting us know!  

# Installation

Currently, only android devices meeting **ALL** criteria below can run the app:

- API 26+ (i.e. Android Oreo or above)
- LEVEL_3 Devices (The ones supporting DNG output)
- Support ``TONEMAP_CONTRAST_CURVE`` (i.e. Emulators can't)

For building the project, you need:

- Android Studio
- build-tools-32.0.0
- android-30
- Stable Internet Connection for Gradle Sync (Downloading external resources)

Then simply open the project with ``Android Studio`` and deploy the build to your favorite android device, and enjoy :)

# Credits

- [Android Jetpack - CameraX module](https://developer.android.com/training/camerax) 
- [Material Design](https://material.io)
- [Google ML Kit](https://developers.google.com/ml-kit)

# License

This repository is under MIT License with `LBYPatrick` being the owner of the license. See ``LICENSE`` for details.

