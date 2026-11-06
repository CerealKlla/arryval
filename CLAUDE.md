# Arryval

New-player onboarding mod for a modular Minecraft project (Minecraft Java Edition). Part of a suite
of intercommunicating mods that expose public APIs so other mods — the user's own and third
parties' — can integrate.

## Context directory — read this first

`context/` is a **separate private repo** (https://github.com/CerealKlla/arryval-context), not part
of this one — it's listed in `.gitignore` here and must never be committed to this repo. It's cloned
as a subdirectory at `context/` for local convenience. If this directory is missing (e.g. a fresh
clone of just this repo), restore it with:

```
git clone https://github.com/CerealKlla/arryval-context.git context
```

- `context/design-document.md` — the authoritative design spec. Start here for anything about
  intended shape or scope.
- `context/decisions.md` — dated log of decisions made during implementation that extend or
  override the design document, with rationale.
- `context/classes/` — one short markdown file per implemented class, once there is one.

Keep this current the same way every other mod in this suite does: update design-document.md/
decisions.md when a decision is made, update classes/ when a class's public surface changes,
commit+push context/ separately from this repo's own commits.

## Status

Scaffolded 2026-11-06 (see [context/decisions.md](context/decisions.md)):
- Loader: **NeoForge**, Minecraft **26.1.2**, Java **25**
- Group ID: `com.github.cerealklla.arryval` / Mod ID: `arryval`
- **Required dependencies on Nuclyus, TinyWyrlds, Cartographyr, Settlemynts, and Lyfe** — unlike
  most sibling dependencies elsewhere in this suite, none of these are optional soft integrations;
  the whole feature needs all five.

**Phase 1 built the same day** — `registration.ModAttachments#TUTORIAL_GRANT_STATE` +
`onboarding.FirstLoginListener` (first-login detection, mirrors Kyt's own `WelcomeListener` pattern
but deliberately as a separate attachment) spawns a fresh, private `WelcomeTutorial` TinyWyrld
entrance directly on a brand-new player at their true first login. `./gradlew build` green
standalone. **Phase 2 built the same day** — `tutorial.TutorialGuideEntity` (a real `Villager` subclass that
keeps real pathfinding navigation, unlike Myssions' own `QuestNpcEntity`), `tutorial.TutorialScript`
(6 hand-authored stages with real relative walk offsets derived from the captured Entrance's own
marker positions), and TTS via Nuclyus's new facade (`api.Nuclyus#speak`) played back locally via
`javax.sound.sampled.Clip` — deliberately not through Audyble's positional SVC pipeline, since the
tutorial Wyrld is always private to exactly one player. `./gradlew build` green standalone.

**Phases 3-4 built the same day** — TinyWyrlds gained a new generic exit-intercept hook (see its own
CLAUDE.md); this mod's new `starttown` package resolves up to 8 real settlements nearest world
origin (gracefully skipping any not yet generated/founded or with no vacant plot), shows a picker
screen (modeled on Settlemynts' own `SelectKytScreen`), and on pick assigns a vacant plot
(`api.Settlemynts#assignPlotOwner`), binds the Recallcinite Totem (`api.Lyfe#bindRecallciniteTotem`),
and teleports the player there for real. `./gradlew build` green across the whole suite.

**Not yet live-tested at all** — needs the one-time Production setup commands run first (see
decisions.md's "Pending" section) before any real playtest is even possible.

See [context/classes/](context/classes/) for per-class reference, once there is one.
