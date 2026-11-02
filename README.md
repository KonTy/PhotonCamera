# PhotonCamera

Android Camera that uses Enhanced Image Processing

## Manual PHOTO candidate

Manual PHOTO keeps the existing exposure/ISO request generation and applies an
explicit, sensor-range-validated focus distance to still requests when preview AF
is off, including infinity (0 diopters). Auto/tap focus is not overridden.
`./gradlew :app:assembleManualPhoto` builds an in-place test candidate with package
`com.particlesdevs.photoncamera.videotest`, versionCode at least 27179, and the
existing debug signer. It does not import the separate Night/video qualification
or manual-panel UI changes.

On Android 11+, only `manualPhoto` makes gallery-read and SAF folder access
optional at ordinary camera startup; existing basic permission checks remain.
Other build types and legacy storage checks are unchanged. The existing saver
writes its own new JPEG/DNG media through direct file paths and scans successful
outputs, without adding a storage framework or granting access to other media.
Opening the camera does not establish that device writes or finalization work.
On the same modern `manualPhoto` path, an absent optional
`DCIM/PhotonCamera/Tuning/PhotonCameraTuning.ini` is reported explicitly and uses
the established built-in tuning defaults without creating an empty file.
Existing tuning files are still read; access and parse failures remain errors.
Other build types/APIs and GL cleanup behavior are unchanged.

Explicit manual PHOTO shots emit at most two `PhotonManualCapture` ASCII records
(at most 512 bytes each): a process-local capture ID and the first completed
frame's requested/actual AE, exposure nanoseconds, ISO, AF and focus diopters,
then sequence completion, capture error or abort. Missing/invalid values are
`UNKNOWN`; actual values never fall back to requests. No preview, Night or video
frames are logged. Sequence completion does not prove processing/save completion
or optical sharpness; finalized JPEG/DNG and device validation remain required.

## Autofocus responsiveness candidate

The existing Advanced > Auto Focus Mode setting is unchanged: Continuous Picture
(default) continuously adjusts still-camera focus; Continuous Video favors smooth
video adjustment; Auto performs a triggered single sweep and holds until another
acquisition. Auto acquires on preview entry, return from manual focus, reset, and
after a still capture; tap focus retains its tagged cancel/start protocol.
Preview completions no longer retrigger Auto. AF START/CANCEL are discrete
one-shots, and steady preview requests use IDLE, including after a failed/skipped
one-shot. Manual AF_OFF does not trigger autofocus.

These are Camera2 behavior modes, not a phase-detection/contrast-detection switch.
The device HAL chooses the underlying algorithm; breathing does not identify it.
This correction does not establish optical sharpness, speed, or hunt-free focus;
those require device testing. See the [Android 3A modes reference](https://source.android.com/docs/core/camera/camera3_3Amodes).

[![GPLv3 license](https://img.shields.io/badge/License-GPLv3-blue.svg)](./LICENSE)
## Links

[![Telegram - ENG](https://img.shields.io/badge/Telegram-ENG-blue)](https://t.me/PhotonCameraEN)
[![Telegram - RU](https://img.shields.io/badge/Telegram-RU-blue)](https://t.me/PhotonCamera)

[<img height="80px" src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=com.particlesdevs.photoncamera)
[<img height="80" src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it at IzzyOnDroid">](https://apt.izzysoft.de/packages/com.particlesdevs.photoncamera)

## Screenshots

<p align="center">
<img src="https://i.imgur.com/na2Tvx6.png" alt="PhotonCamera">
</p>

## Libraries used by Photon Camera

* [Glide](https://github.com/bumptech/glide)
* [SSIV](https://github.com/davemorrissey/subsampling-scale-image-view)
* [GSON](https://github.com/google/gson)
* [MaterialComponents](https://github.com/material-components/material-components-android)
* [CircleImageView](https://github.com/hdodenhof/CircleImageView)
* [HorizontalPicker](https://github.com/blazsolar/HorizontalPicker)

## License

    PhotonCamera
    Copyright (C) 2020-2022  Eszdman

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
