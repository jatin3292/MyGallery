# My Gallery

A simple personal-use Android gallery app built with Jetpack Compose.

## Features
- Grid view of all photos and videos on the device (via MediaStore — no broad storage permission needed on Android 13+)
- Tap to open a fullscreen, swipeable viewer
- Pinch-to-zoom and pan on photos
- Inline video playback (ExoPlayer) with play/pause/seek controls
- Runtime permission handling for Android 13+ and older versions

## How to build — Option A: Cloud build with GitHub Actions (no local install needed)

This repo includes a workflow (`.github/workflows/build-apk.yml`) that builds the APK entirely on GitHub's servers. You don't need Android Studio, Gradle, or the Android SDK on your own machine at all.

1. **Create a free GitHub account** at https://github.com if you don't have one.

2. **Create a new repository**:
   - Go to https://github.com/new
   - Name it anything (e.g. `my-gallery`)
   - Public or Private both work fine (Actions has a generous free monthly quota either way)
   - Click "Create repository", leave it empty (don't add a README there)

3. **Upload the project**:
   - Unzip `MyGallery.zip` on your computer first
   - On your new repo's page, click "uploading an existing file"
   - Drag the *contents* of the unzipped `MyGallery` folder (all files/folders, including the hidden `.github` folder) into the browser upload area — modern GitHub supports dragging whole folders and preserves the structure
   - Scroll down and click "Commit changes"

   > Tip: file managers usually hide folders starting with a dot (`.github`). If you don't see it after unzipping, enable "show hidden files" in your file manager, or just drag the whole unzipped `MyGallery` folder's contents at once — most drag-and-drop uploaders pick it up either way.

4. **Watch it build**:
   - Click the "Actions" tab on your repo
   - You should see a workflow run start automatically (or click "Run workflow" if it didn't)
   - It takes about 2–5 minutes

5. **Download the APK**:
   - Once the run finishes (green checkmark), open it
   - Scroll down to "Artifacts" and download `app-debug` — this is a zip containing `app-debug.apk`

6. **Install on your phone**:
   - Transfer `app-debug.apk` to your phone any way you like (email it to yourself, upload to Google Drive and download on phone, USB cable, etc.)
   - Tap the file on your phone to install
   - Android will ask you to allow installs from that source (e.g. Files app or Gmail) the first time — allow it, then install proceeds normally
   - No Play Store, no signing, no review needed

## How to build — Option B: On another machine with Android Studio

1. **Install Android Studio** (if not already installed): https://developer.android.com/studio

2. **Unzip** this project folder anywhere on that machine.

3. **Open the project**: Android Studio → `File > Open` → select the unzipped `MyGallery` folder.

4. **Gradle wrapper note**: This project does not include the Gradle wrapper binary (`gradle-wrapper.jar`), since it couldn't be downloaded in the sandboxed environment it was built in. When you open the project, Android Studio will detect this and offer to:
   - Automatically regenerate the wrapper, or
   - Just build using Android Studio's bundled Gradle directly.

   Either option works fine — accept the prompt, or if it doesn't appear, go to `File > Sync Project with Gradle Files`.

   Alternatively, if you have Gradle installed separately, run this once inside the project folder:
   ```
   gradle wrapper --gradle-version 8.7
   ```
   This generates the missing wrapper files, after which you can use `./gradlew` as usual.

5. **Let Gradle sync** (Android Studio will prompt automatically, downloading the required SDK/dependencies — needs internet on first sync).

6. **Run it**:
   - Connect your Android phone via USB with USB debugging enabled, OR use an emulator.
   - Click the green ▶ Run button in Android Studio, select your device.
   - The app installs directly — no Play Store, no signing, no review needed.

   Alternatively, to just get an installable file:
   - `Build > Build Bundle(s) / APK(s) > Build APK(s)`
   - Find the generated APK under `app/build/outputs/apk/debug/app-debug.apk`
   - Transfer that file to your phone (email, USB, cloud) and tap it to install
     (you'll need to enable "Install unknown apps" for whichever app you use to open it).

## Project structure
```
MyGallery/
├── app/
│   ├── build.gradle.kts          # Dependencies (Compose, Coil, Media3/ExoPlayer)
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions + app declaration
│       ├── java/com/example/mygallery/
│       │   ├── MainActivity.kt        # Entry point + permission gate
│       │   ├── MediaRepository.kt     # MediaStore query logic
│       │   ├── GalleryScreen.kt       # Grid UI
│       │   └── FullscreenViewer.kt    # Swipeable fullscreen viewer
│       └── res/                 # Strings, colors, theme, icons
├── build.gradle.kts              # Root build config
└── settings.gradle.kts
```

## Customization ideas for later
- Group photos into folders/albums (group by bucket/parent directory from MediaStore)
- Add a delete/share button in the fullscreen viewer
- Add a "Recently Deleted" trash bin instead of hard-deleting
- Swipe-down-to-dismiss gesture on the fullscreen viewer
- Multi-select mode in the grid for batch actions
