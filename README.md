# Loadout companion mod

A small client-side Fabric mod for Minecraft 26.2 that marks players who are also using
the [Loadout launcher](https://github.com/ComplexKing/loadout).

Client only, and deliberately small. A mod that ships alongside a launcher has to be the
kind of thing somebody is happy to have running, which means it should be obvious what it
does and short enough to read in one sitting.

## What it does

- Reads which launcher started the game, from properties Loadout sets on the command line.
- Adds a mark beside the names of players known to be using it.

## How it knows

This is the interesting part, and the honest answer is that a client cannot simply ask.
Minecraft gives clients no way to talk to each other: a custom payload goes to the server,
and only a server taught to relay it passes anything on. So there are three possibilities:

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

## Compatibility

The badge is composed onto the name component rather than substituted for it, and the
injection is at the return of `getNameTag` rather than into any drawing. Other mods
decorate names too — Essential most obviously — and adding to what is already there means
whichever loads second wraps the first's work instead of discarding it. Load order stops
mattering.

The mark is a glyph, not a texture. Drawing an image into a name tag means owning its
layout — width, baseline, and the background that sizes itself to the text — and every mod
that has tried has fought every other mod doing the same.

## Building

```bash
./gradlew build
```

Java 25, which Minecraft 26.1+ requires. Gradle fetches a toolchain itself if the machine
has none.
