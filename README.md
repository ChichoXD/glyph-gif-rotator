# Glyph GIF Rotator

[![Downloads](https://img.shields.io/github/downloads/ChichoXD/glyph-gif-rotator/total?style=flat-square&label=downloads&color=black)](../../releases)
[![Latest release](https://img.shields.io/github/v/release/ChichoXD/glyph-gif-rotator?style=flat-square&color=black)](../../releases/latest)
[![License](https://img.shields.io/github/license/ChichoXD/glyph-gif-rotator?style=flat-square&color=black)](LICENSE)

Turn the Glyph Matrix on the back of your **Nothing Phone (3)** or **Phone (4a) Pro** into a
rotating gallery of your own GIFs — and let it react to what the phone is actually doing.

> ⚠️ **Needs a Glyph Matrix** (the dot display): Nothing Phone (3) or Phone (4a) Pro. Phones with
> the Glyph Interface light strips can't show images, so there's nothing to port.
>
> 🧪 **Phone (4a) Pro support is new in 1.2-beta and has not been tested on a real device yet.**
> It compiles, it passes the tests, and every drawing was checked against a 13×13 grid — but
> nobody has seen it light up on actual hardware. If you have one, your report is the most
> useful thing this project can get right now.

---

## What it does

**Your GIFs, on every unlock.** Load whatever animated GIFs or images you want. Each time you
lock or unlock the phone, a different one plays on the back — never the same one twice in a row.

**It reads the room.** The Matrix isn't just a slideshow; it changes with context:

| When | What you see |
|---|---|
| Music playing | A vinyl record **spinning**. Pause it and the record stops mid-turn. Resume within a few seconds and it picks the spin back up. |
| Long-press the Glyph button *(Phone (3) only)* | Your battery as **liquid physics** — tilt the phone and it sloshes, driven by the accelerometer, with the percentage counting up as it fills. |
| Every few minutes *(Phone (4a) Pro)* | The same liquid battery, on its own. The (4a) Pro has **no Glyph button**, so it can't be asked for — it shows up by itself instead. |
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

**The designs belong to the people who drew them.** Glyph Museum's terms
([section 3](https://glyphmuseum.com/developers)) ask that the author is credited and the original
post linked wherever a design is shown. This app never handles that side of it: it has no access to
the catalogue, ships no designs of its own, and only opens image files you already saved to your
phone through the system file picker — the file is copied byte for byte, so any metadata it carries
survives untouched. If that ever changes — bundled designs, an in-app browser, a share button —
author and link have to travel with the design.

---

## Two things worth knowing

Both of these cost real debugging time, so they're written down in case they help someone else.

**The Matrix is LEDs in a circle, not a square grid.** On the Phone (3) that's 489 LEDs inside a
25×25 grid of 625; on the (4a) Pro, 137 inside 13×13. Treat it as a square and everything outside
the circle silently disappears — no error, no warning, just missing corners. The row widths are
computed in [`GlyphLedLayout.kt`](app/src/main/java/dev/glyphrotator/app/glyph/GlyphLedLayout.kt)
with the same circle parameters Nothing's own SDK uses internally; for the Phone (3) they match,
row by row, a table counted by hand from exported designs. For the (4a) Pro there is no such
cross-check yet.

**Hard-coded positions break on the smaller screen.** Text rows written for 25×25 (the clock at
row 6, AM/PM at row 15) put things *off the screen* on 13×13, and `12:34` is 20 pixels wide —
it doesn't fit in 13. On the (4a) Pro the clock splits into two lines, hours over minutes, and
every text position is computed from the real matrix size in
[`GlyphTextMetrics.kt`](app/src/main/java/dev/glyphrotator/app/glyph/GlyphTextMetrics.kt).

**GIFs looked like they were vibrating.** The cause was cropping and contrast-stretching each
frame independently: the bounding box shifted by a pixel between frames, so the whole animation
jittered. The fix was computing the crop bounds and the contrast range **once across the entire
animation** instead of per frame. See
[`MediaFrameDecoder.kt`](app/src/main/java/dev/glyphrotator/app/glyph/MediaFrameDecoder.kt).

---

## Experimental: game mode

There's a Pokémon-style game built on top of the Matrix — wild encounters while the phone is
idle, training, eggs, evolutions. It is **hidden by default and not considered finished**: it
hasn't been confirmed working end to end on real hardware, and on the (4a) Pro the core gesture
(catching with the Glyph button) doesn't exist, so it's played from inside the app instead.

It's off unless you go looking for it. To unlock it: **Settings → scroll to the bottom → tap the
version number seven times**, the same way you unlock Android's developer options. A switch then
appears; it starts off, and you turn it on yourself.

Reports about it are welcome, but expect rough edges.

Pokémon and the names, sprites and sounds of the Pokémon characters are trademarks and property
of Nintendo, Game Freak, Creatures and The Pokémon Company. This is an unofficial fan project,
not affiliated with or endorsed by any of them. The sprite and cry files are not this project's
work, and where they were originally obtained is not documented in this repository yet — if you
recognise them as yours, open an issue and they get credited properly.

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

### Spinning vinyl — Glyph Beat

On the **Phone (3)**, the 8 frames of the spinning vinyl are **taken verbatim** from
`VinylTheme.kt` in **[Glyph Beat](https://github.com/pauwma/GlyphBeat)** by
[pauwma](https://pauwma.com), used under the MIT license. The frame data is the original author's
work, not a reimplementation — an earlier version of this README said otherwise, and that was
wrong. See [NOTICE](NOTICE) for the full copyright notice.

On the **Phone (4a) Pro** those frames can't be used: they're drawn pixel by pixel for the 489-LED
circle of the Phone (3) and don't fit a 13×13 matrix. There the vinyl is generated by this
project's own code (`pixelValue()` in the same file) — a much simpler disc with a spinning
highlight. It's a different drawing, not a scaled copy of Glyph Beat's.

### Liquid battery

The liquid battery is **not an original idea** — it was seen elsewhere on the Glyph Matrix and
rebuilt here. Unlike the vinyl, no code or data was copied: the tilt simulation in
[`LiquidPhysics.kt`](app/src/main/java/dev/glyphrotator/app/glyph/LiquidPhysics.kt) is this
project's own. If you recognise it as yours, open an issue and it gets credited here.

### Nothing

The Glyph Matrix SDK belongs to Nothing Technology Limited and is used, not redistributed.

## License

MIT — see [LICENSE](LICENSE).
