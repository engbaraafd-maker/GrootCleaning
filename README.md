# GrootCleaning
Build (no PC): push this folder to a GitHub repo -> Actions tab -> "Build APK" -> download artifact `GrootCleaning-debug-apk`.
Build (PC): `gradle assembleDebug` (Gradle 8.7, JDK 17, Android SDK 34) -> app/build/outputs/apk/debug/app-debug.apk
Install: enable "Install unknown apps", open the APK.
Enable: Settings > Accessibility > GrootCleaning > On (Android 8.1 may show a warning; accept).
Test safely: Simulation Mode is ON by default. Select 1 app, press START; check Logs. Turn it OFF in Settings to run for real.
