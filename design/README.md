# SmartGround — "D" card language · integration guide

This folder ports the tile/card design we iterated on (**version D** — solid
accent chip, dimmed hue border, faint gradient tint) into the real
`smart-ground-ui` Vue app. It touches two views plus the shared tokens.

The visual reference lives in the design project:
`SmartGround Remote.html`, `SmartGround Tile Distinctiveness.html`.

Files here:

| File | Goes into |
|---|---|
| `tokens.additions.css` | `src/assets/main.css` — inside `:root { … }` |
| `home-tiles.css` | `src/views/shooter/ShooterHomeView.vue` — `<style scoped>` |
| `remote-cards.css` | `src/views/shooter/ShooterRemoteView.vue` — `<style scoped>` |

Everything falls back to literal hex if you skip the tokens, so you can paste
the two view files first and add tokens later.

---

## 1 · Shared tokens

Append the contents of **`tokens.additions.css`** into the existing
`:root { … }` block in `src/assets/main.css`. These are the per-category and
per-session hues, centralised.

---

## 2 · Shooter Home  (`ShooterHomeView.vue`)

The tiles already carry per-category icon tints. Two small template edits turn
them into D tiles; the rest is CSS.

**Template** — for each *available* tile, add a hue class and make the glyph
dark so it reads on the solid chip:

```html
<!-- before -->
<button class="app-tile app-tile--available" @click="router.push('/remote')">
  <div class="tile-icon-wrap tile-icon-wrap--cyan">
    <Icons icon="target" :size="36" color="var(--sg-accent)" />
  </div>

<!-- after -->
<button class="app-tile app-tile--available app-tile--cyan" @click="router.push('/remote')">
  <div class="tile-icon-wrap">
    <Icons icon="target" :size="36" color="#1a1a2e" />
  </div>
```

Apply the same pattern to the other three available tiles:

- Trainings → `app-tile--orange`, `color="#1a1a2e"`
- Mein Profil → `app-tile--purple`, `color="#1a1a2e"`
- Verwaltung → `app-tile--green`, `color="#1a1a2e"`

Leave the two `app-tile--soon` tiles untouched — they keep the muted look.

**Style** — paste **`home-tiles.css`** into `<style scoped>`. It replaces the
three `.app-tile--available` rules and adds the four hue classes + the solid
chip. (You can drop the now-unused `tile-icon-wrap--*` classes from the
available tiles once the hue class is in place; the muted variant still uses
`--muted`.)

---

## 3 · Shooter Remote  (`ShooterRemoteView.vue`)

The cards already get a saturated border + glow per session mode. D adds the
gradient tint, the solid letter chip, and the card-style Solo/Pair toggle.

**Style** — paste **`remote-cards.css`** into `<style scoped>`. It:

1. defines `--card-accent` per session (Schiessen green / Erfassen red /
   Verzögert amber / Rufauslösung cyan);
2. adds a mode-tinted gradient to `.device-btn:not(:disabled)`;
3. fills `.btn-icon-wrap` with the accent;
4. restyles `.solo-pair-toggle` + `.toggle-btn.active` to match the cards.

**Script (one edit)** — the letter colour is set in JS. For a dark glyph on the
solid chip, change the final return in `iconColor()`:

```js
const iconColor = (position) => {
  if (!position.device) return 'rgba(255,255,255,0.15)';
  if (position.device.blocked || isLocked.value) return 'rgba(252,129,129,0.5)';
  if (!store.reservedByMe) return 'rgba(255,255,255,0.25)';
  return '#1a1a2e';            // ← was 'rgba(255,255,255,0.95)'
};
```

Disabled / blocked / free positions keep their muted grey chip and letter, so
the "not tappable" states still read correctly.

---

## Why it's built this way

- **Colour follows the active mode**, not decoration — so the card tint tells
  you what a tap does (green = Schiessen, red = Erfassen, …). It reuses the
  hues already on your border/glow and mode badge, so nothing new competes with
  the status vocabulary.
- **Solid chip + dark glyph** is the daylight-contrast fix — the earlier faint
  fills washed out on a tablet in sunlight (the same reason your border/glow
  got saturated).
- **`color-mix`** keeps every tint derived from one hue variable; no hand-typed
  alpha hexes. It's supported on your WebView/Safari 16.2+ targets — if you
  need to go older, replace each `color-mix(... N%, transparent)` with the
  equivalent `rgba()`.

---

## Test impact

`ShooterRemoteView.test.js` asserts behaviour (classes/labels), not colours, so
these are style-only changes — no test updates expected. Re-run
`npm run test:unit` to confirm.
