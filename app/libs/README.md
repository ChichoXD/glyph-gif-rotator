# Nothing Glyph Matrix SDK

The `.aar` is **not** in this repository: it is a proprietary binary belonging to Nothing
Technology Limited and redistributing it isn't ours to do.

To build the project:

1. Download `glyph-matrix-sdk-2.0.aar` from the official developer kit:
   https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
2. Place it in this folder (`app/libs/`).
3. Build normally: `./gradlew assembleDebug`

Without it the project will not compile — `app/build.gradle.kts` references the file directly.
