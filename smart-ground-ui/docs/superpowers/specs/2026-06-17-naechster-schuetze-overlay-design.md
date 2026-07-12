# Design: "Nächster Schütze" Overlay

**Date:** 2026-06-17  
**Status:** Approved

## Summary

Between players in a multi-player session on `ShooterPlayPage`, a fullscreen overlay announces the next shooter before play resumes. This makes the handover explicit and avoids confusion about who should step up next.

## Trigger

When the current player completes their program (taps the "Getroffen / Fertig" card) in multi-player mode and `store.nextPlayer` is not null (i.e. there are still more players), the overlay is shown instead of immediately calling `store.advanceToNextPlayer()`.

If there is no next player (last player in the group), the overlay is skipped and the session proceeds to the final score screen as before.

## Overlay Content

- Small uppercase label: **NÄCHSTER SCHÜTZE**
- Large player name: `store.nextPlayer.displayName`
- Subtext: *Bitte schießbereit machen*
- Primary button: **Starten →** — calls `store.advanceToNextPlayer()` and hides the overlay

## Visual Design

- Fullscreen fixed overlay (`position: fixed; inset: 0`) over the play page
- Dark glass background matching the existing play page aesthetic (`rgba(10, 10, 18, 0.97)` + `backdrop-filter: blur`)
- Name displayed at ~36px bold, centered
- Animated entrance (fade + slight slide up) via Vue `<Transition>`
- Button styled to match the existing `.btn-primary` pattern

## Implementation Scope

All changes are self-contained in `src/views/shooter/ShooterPlayPage.vue`:

1. Add `showNextShooterOverlay` ref (Boolean, default `false`)
2. Modify `handlePlayerComplete()`: if `store.isMultiPlayer && store.nextPlayer`, set `showNextShooterOverlay = true` instead of calling `advanceToNextPlayer`
3. Add a `confirmNextShooter()` handler that calls `store.advanceToNextPlayer()` and resets `showNextShooterOverlay = false`
4. Add overlay markup inside a `<Transition name="next-shooter-fade">` block
5. Add scoped CSS for the overlay and transition

No new components, no store changes, no routing changes required.

## Out of Scope

- Showing the previous player's score in the overlay (decided against — keep it minimal)
- Auto-dismiss timer
- Sound/haptic feedback
