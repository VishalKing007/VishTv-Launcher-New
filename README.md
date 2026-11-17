# Vishie TV

Custom Android TV launcher for a Sony BRAVIA (Android TV 8), built to Vishal's brief:
tvOS-style home with big tiles, custom wallpapers, and a one-tap RAM cleaner.
100% local — no internet permission, no accounts, no analytics.

## Version 1.3 (versionCode 4)

- tvOS-style home: big gradient app tiles, name below the tile, ambient glows, 4 themes
- System apps hidden except major streaming apps + Play Store; long-press to hide/unhide any app
- Recents row
- Wallpaper screen with a built-in photo browser (scans TV + USB storage, D-pad friendly),
  up to 12 custom wallpapers, plus 4 presets
- "RAM Saaf" tile: kills killable background apps and reports real freed memory

## Notes

- Package: `com.vishal.vishietv`, minSdk 26, framework Views only
- The signing keystore is intentionally not in this repo.
