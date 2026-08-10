# Glyph GIF Rotator

[![Downloads](https://img.shields.io/github/downloads/ChichoXD/glyph-gif-rotator/total?style=flat-square&label=downloads&color=black)](../../releases)
[![Latest release](https://img.shields.io/github/v/release/ChichoXD/glyph-gif-rotator?style=flat-square&color=black)](../../releases/latest)
[![License](https://img.shields.io/github/license/ChichoXD/glyph-gif-rotator?style=flat-square&color=black)](LICENSE)

Turn the Glyph Matrix on the back of your **Nothing Phone (3)** into a rotating gallery of your
own GIFs — and let it react to what the phone is actually doing.

> ⚠️ **Nothing Phone (3) only.** It needs the Glyph Matrix (the dot display). Phones with the
> Glyph Interface light strips can't show images, so there's nothing to port.

---

## What it does

**Your GIFs, on every unlock.** Load whatever animated GIFs or images you want. Each time you
lock or unlock the phone, a different one plays on the back — never the same one twice in a row.

**It reads the room.** The Matrix isn't just a slideshow; it changes with context:

| When | What you see |
|---|---|
| Music playing | A vinyl record **spinning**. Pause it and the record stops mid-turn. Resume within a few seconds and it picks the spin back up. |
| Long-press the Glyph button | Your battery as **liquid physics** — tilt the phone and it sloshes, driven by the accelerometer, with the percentage counting up as it fills. |
| Screen off, nothing else going on | A dim idle **clock**. |
| Battery below your threshold | Dims itself. Below a second threshold, goes dark entirely. |
| Bluetooth headphones connect | A design of your choice flashes for a moment. |
| A notification animation plays | It steps aside so the two don't fight over the display. |
| You open another Glyph app | It releases the Matrix completely until you leave. |

**Everything is a switch.** Clock, vinyl, both battery thresholds, and the Bluetooth design are
yours to configure or turn off.

---

## Where to get designs: Glyph Museum

You don't have to make your own art. **[Glyph Museum](https://glyphmuseum.com/)** is a
community gallery with thousands of designs already drawn for the Glyph Matrix — browse by
trending, latest or tags, and export what you like.

The two apps complement each other rather than compete:

- **Glyph Museum** is where you find and create designs, one at a time.
- **This app** takes whatever you've saved and cycles through it automatically, plus adds the
  context stuff (clock, vinyl, battery) that a gallery doesn't do.

Save a design from Glyph Museum to your phone, then add it here with **Add GIFs or images**.
Since it's already drawn for a 25×25 circular matrix, it comes out sharp — no downscaling
artefacts, no guessing at contrast.

> This app **steps aside automatically** while Glyph Museum is in the foreground, so the two
> don't fight over the display. That's what the optional *usage access* permission is for.

Glyph Museum is an independent community project by [pauwma](https://pauwma.com/projects/glyph-museum),
not affiliated with this one and not affiliated with Nothing.

---

## Two things worth knowing

Both of these cost real debugging time, so they're written down in case they help someone else.

**The Matrix is 489 LEDs in a circle, not a 25×25 grid of 625.** Treat it as a square and
everything outside the circle silently disappears — no error, no warning, just missing corners.
The row widths that add up to exactly 489 are in
[`GlyphLedLayout.kt`](app/src/main/java/dev/glyphrotator/app/glyph/GlyphLedLayout.kt).

**GIFs looked like they were vibrating.** The cause was cropping and contrast-stretching each
frame independently: the bounding box shifted by a pixel between frames, so the whole animation
jittered. The fix was computing the crop bounds and the contrast range **once across the entire
animation** instead of per frame. See
[`MediaFrameDecoder.kt`](app/src/main/java/dev/glyphrotator/app/glyph/MediaFrameDecoder.kt).

---

## Install

Grab the APK from [Releases](../../releases) and install it. Android will warn you about
installing outside the Play Store — that's expected for a sideloaded app.

After first launch:

1. Add at least one GIF or image.
2. Turn on **Automatic rotation**.
3. Accept the notification permission — the app runs as a foreground service and Android requires
   an ongoing notification for that.
4. **Remove the battery restriction** when the app asks. Without it, Android will kill the service
   in the background and the rotation stops while you sleep.

Two optional permissions, both requested in-app and both skippable:

- **Notification access** — lets it tell *playing* from *paused* music, so the vinyl can freeze
  instead of just disappearing.
- **Usage access** — lets it detect when another Glyph app is in the foreground and get out of
  the way.

---

## Build it yourself

The Nothing Glyph Matrix SDK is a proprietary binary and **is not included in this repository**.

1. Download `glyph-matrix-sdk-2.0.aar` from the
   [official Nothing developer kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).
2. Drop it in `app/libs/`.
3. `./gradlew assembleDebug`

Requires Android Studio and a device running Android 14 or newer.

---

## Found a bug?

There's a **Report a bug** button inside the app (Settings tab). It opens a pre-filled issue with
your phone model, Android version and app version already attached — those three lines are what
make a report actionable, and they're the ones people usually forget.

Or open one directly in [Issues](../../issues).

---

## Languages

English, Spanish, German, French and Portuguese. The app follows your phone's language
automatically — nothing to configure.

**Want yours?** Open an issue with the `translation` label saying which language, and I'll add
it. It's about 55 short strings; if you want to send them translated, even better — but a plain
request is enough.

---

## Credits

The **liquid battery** and the **spinning vinyl** aren't original ideas — both were found online
and reimplemented here.

The Glyph Matrix SDK belongs to Nothing Technology Limited and is used, not redistributed.

## License

MIT — see [LICENSE](LICENSE).
