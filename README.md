# Loadout companion mod

A client-side Fabric mod for Minecraft 26.2 that lets the
[Loadout launcher](https://github.com/ComplexKing/loadout) reach into a running game — so
changing your mods does not mean going back to a launcher window, and a game that stops
starting has something better than a log to go on.

Client only, and deliberately small. A mod that ships alongside a launcher has to be the
kind of thing somebody is happy to have running, which means it should be obvious what it
does and short enough to read in one sitting.

## What it does

Press `\` in game.

| | |
| --- | --- |
| **Mods** | The instance's mod list, with on/off switches. |
| **Resource packs** | The same, for packs — and these apply immediately. |
| **Apply and rejoin** | Restart into the server or world you were already in. |
| **Frame times** | `F6`. What a stutter looks like as a number. |

It also marks players known to be using the launcher, and tells the launcher when the
client has finished starting.

## The one thing it cannot do

It cannot add a mod to the running game, or take one out of it. Fabric resolves the mod
list, loads the classes and applies every mixin during bootstrap, before any of this code
exists. That is the platform, not a gap, and no amount of effort changes it.

So the mod screen is honest about which half of the job it is doing: turning something on
or off is recorded against the *next* launch and labelled that way, and the footer says how
many changes are waiting rather than pretending they have happened.

Which leaves the real question — not whether a restart happens, but what it costs. A change
that means two minutes of menus is a change nobody makes twice. So when something is
waiting, the footer offers to spend the restart there and then and put you back where you
were: **Apply and rejoin `play.example.net`**, or the world you were in. About ten seconds,
and the reason the queued half is worth having at all.

It shuts down properly rather than quitting. A bare `stop()` would leave a singleplayer
world unsaved and a server holding a session that never said goodbye, which is how a rejoin
arrives to be told it is already logged in.

Resource packs are the genuinely live half, on their own screen. The game already knows how
to throw its resources away and rebuild them — that is what the vanilla pack screen does
every time somebody presses Done — so a pack switched on here is on, now. The two are kept
apart because one list covering both would make each harder to trust: nobody should have to
remember which rows apply immediately.

Only resource packs, though. A shader pack is a loader's idea rather than the game's, and
the loader that reads `shaderpacks/` does not run on 26.2's Vulkan path at all, so there is
nothing here to switch on.

## Frame times

`F6`, or the button on the mod screen.

The launcher offers memory sizes and garbage collector presets, and every one of them is a
claim about how the game will feel. The number vanilla already shows cannot check them: F3
reports an average frames per second, which is precisely the statistic a stutter hides
inside. Sixty frames where fifty-nine take 10ms and one takes 400ms average out to
something that looks fine, and the 400ms frame is the entire problem.

So the overlay reports the slow tail — the **1% low**, the mean of the worst hundredth.
Change a setting, play for a few minutes, watch whether that number moves. If it does not,
the setting did not help.

The collector line sits next to it on purpose. A collection's duration is not a pause: G1's
young collections stop the world for nearly all of theirs, while ZGC does almost everything
concurrently and can post long durations while nothing stopped at all. Long collections
beside steady frames are a success, and would read as a problem if the two were reported
apart.

## Telling the launcher it started

One call, once the client is up. It earns its own endpoint because nothing outside the
process can tell the difference: from the launcher's side, a game sitting happily at the
menu and one that died before the window opened both look like a process that was running
and then was not. From in here it is not ambiguous — if that line runs, the mod list
resolved, every mixin applied, and the client exists.

The launcher uses it to remember the mod set as one that works. So when a game stops
starting, "these mods did not start, those ones did" is a fact somebody has, rather than
four thousand lines of log to read.

## The badge, and how it knows

A client cannot simply ask who else is using the launcher. Minecraft gives clients no way
to talk to each other: a custom payload goes to the server, and only a server taught to
relay it passes anything on. So there are three possibilities:

| Source | Works where | Needs |
| --- | --- | --- |
| Yourself | always | nothing — the launcher already told this process |
| A server that relays it | servers running a companion plugin | a plugin |
| A presence service | everywhere | a service that knows who is playing where |

Only the first is implemented. `BadgeRegistry` is the seam for the others: nothing else in
the mod has to change when one is added.

The third is a product decision rather than a missing function. It means running a service
that learns which players are online and where, which is a real obligation and worth
deciding on deliberately rather than arriving at.

### Compatibility

The badge is composed onto the name component rather than substituted for it, and the
injection is at the return of `getNameTag` rather than into any drawing. Other mods decorate
names too — Essential most obviously — and adding to what is already there means whichever
loads second wraps the first's work instead of discarding it. Load order stops mattering.

The mark is a glyph, not a texture. Drawing an image into a name tag means owning its
layout — width, baseline, and the background that sizes itself to the text — and every mod
that has tried has fought every other mod doing the same.

## How it reaches the launcher

The launcher is still running, already owns every operation, and is listening on loopback.
The mod does not need its own copy of any of that — it needs a phone line, and gets one
through properties Loadout sets on the command line.

The token handed to the game is not the one the launcher's own window uses. Anything in
this JVM can read a system property, so whatever goes there has to be something it is
acceptable for *every* mod in the pack to hold. That token reaches a deliberately small set
of endpoints — read this instance, turn things on and off, install, report that it started,
and start a successor to this game — and is refused for accounts, settings, and deleting
anything.

## Layout

`src/main` is for code that touches nothing from Minecraft and can therefore be unit
tested — currently the frame-time arithmetic, because a measuring tool with unverified
measurements is not worth having. `src/client` is everything else.

## Building

```bash
./gradlew build
```

Java 25, which Minecraft 26.1+ requires. Gradle fetches a toolchain itself if the machine
has none.
