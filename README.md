# File Manager

Sci-fi HUD (Jarvis style) file manager for Android 8.0+. Same design language as Dialer, Scanner, Keyboard, Launcher, Messages, Weather, Terminal and Code.

- Package: `com.mux.android.files` - Version: `1.0.0`

## Features
- HOME: storage cards with used/free bar, categories (images, videos, audio, documents, APKs, archives, downloads), shortcuts, pinned folders, recent files
- FILES: list or grid with thumbnails (images, videos, APK icons), breadcrumb path, sort by name / date / size / type, hidden files switch, search in the current folder tree
- Select many: copy, cut, paste, delete, zip, share, rename, properties, invert selection, pin folder to home
- Properties: size (folders counted in the background), dates, MD5 / SHA-1 / SHA-256
- Viewers: image viewer (pinch zoom, swipe, share), text and code editor (up to 1 MB), zip browser with extract, APK info with install
- Zip: create and extract (zip-slip safe). Other archive types open in another app
- Recycle bin: deleted items go to a hidden folder and can be restored (can be turned off)
- TOOLS: top-50 largest files, junk finder (tmp / log / bak, empty folders, 0-byte files), recycle bin, Wi-Fi transfer
- Wi-Fi transfer: open an address like `http://192.168.0.104:8080/4821/` on a computer on the same network to browse, download and upload files
- SET: cyan / amber, default view, sort, hidden files, recycle bin, confirm delete
- SYS: app info, update check, author

## Limits (Android)
- Needs "All files access". Android 11+ blocks `Android/data` and `Android/obb` for every app except the system file manager, so they cannot be opened.
- Archives: only zip is created and extracted (no 7z / rar).
- Paste into a folder that already has the same name keeps both: the new one becomes `name (1)`.
- Wi-Fi transfer is plain HTTP protected only by the PIN in the address. Use it on a network you trust. It works only while the app is open.
- Thumbnails come from the Coil library; very large videos may show a placeholder first.
- Not tested on a device yet: if a build or runtime error appears, send the log.

## Build
`gradle assembleDebug` (Gradle 8.9, JDK 17) or the GitHub Action. `app/debug.keystore` is shared with the other apps.
Updates are read from GitHub releases of `mahfuz0712/Files` (edit `AppInfo.kt`).
