---
name: driver-ui-standards
description: Use this skill BEFORE designing or building any user-facing screen, dialog, sheet, or component in the driver app. Triggers — "design X", "build the Y screen", "polish the Z UI", "add UI for…", reviewing UI changes, before any phase-1 week that ships a screen. Loads this project's UX/UI quality bar — a ONE-HANDED PHONE screen used while driving/parking, not a stationary tablet — before writing layout code.
---

# Skill: driver-ui-standards

This is **not the POS app**. The cashier stands at a fixed tablet under shop lighting; the driver holds this phone **in one hand, sometimes gloved, in a moving car, at night, under direct sun, on a screen that has to survive a glance while parking**. Every deviation below from POS's `pos-ui-standards` exists for that reason — don't copy POS's numbers back in.

**This skill loads the rules. Follow them or fail the brief.**

---

## The Bar — deviations from POS, and why

| Concern | POS (tablet, cashier) | **Driver (phone, one hand, moving)** | Why |
|---|---|---|---|
| Trip-action touch target | 56dp minimum | **64dp minimum for every trip action** (accept/decline/picked-up/delivered/navigate/call) | A thumb on a moving vehicle, possibly gloved, cannot reliably land on 56dp. See driver CLAUDE.md's ⚡ performance table — 64dp is written there as a frozen number, not a suggestion. |
| Type scale | Fixed via `fontScale=1f` (invoices must not reflow) | **Respects system font scale up to 200%**, one step larger baseline than POS for the amount/title fields | POS locks scale because invoices break; the driver has no invoice layout to protect and DOES need large-print support for an older driver in bright sun — see the ⛔ "four deliberate deviations" table in driver CLAUDE.md. |
| Dark mode | None — cashier works under shop lighting | **Full dark theme, auto-switches with system** | Driver works at night. This is a designed theme, not an afterthought inversion — verify contrast in BOTH `@Preview` variants. |
| Per-device UI scale (`PosUiScale`) | Yes — tablet size varies | **Not ported.** System font scale covers it. | No tablet-size variance to compensate for on a phone form factor. |
| Accent color picker | Yes — cashier customizes their terminal | **None.** Color comes from the flavor (`driver_brand_colors.xml`) + `accent_color` from the admin panel. | Driver identity = platform identity, not a personal terminal. |
| Font | IBM Plex Sans Arabic | **Same font, same reasoning** (902 glyphs vs Cairo's 701; matches the POS app on the same device at the same restaurant; sharp Latin digits for money/distance/phone). | See CLAUDE.md's measured `fontTools` comparison — don't re-litigate, don't substitute Cairo. |
| Verification device | Emulator screenshot is often enough | **In-pocket, on a real phone, for anything touching location/background** | An emulator proves nothing about Doze, background kill, or battery optimization prompts. |

The **Three Hard Rules carry over unchanged** from POS and are non-negotiable here too:

1. **No `CircularProgressIndicator` mid-screen, ever.** Skeleton shimmer matching the final layout shape.
2. **No `tween()` on user-driven motion.** `spring()` only — `tween` is allowed only for ambient loops (shimmer).
3. **No screen ships without its four states: loading skeleton, designed empty state, inline error with retry, and offline.** (Driver CLAUDE.md calls this "الحالات الأربع إلزامية" — four, not three, because "offline" is a first-class state for a phone on a moving vehicle's mobile data, distinct from a one-off request error.)

---

## Non-negotiable driver-specific rules (not shared with POS)

- **The three facts that must never require a scroll:** the branch name, the customer's delivery area, and the amount to collect. This is stricter than POS's general "info above the fold" guidance — driver CLAUDE.md names these three explicitly ("أهم ٣ معلومات بلا تمرير").
- **Before an offer is accepted, the screen must show ONLY area + distance + amount** — never the customer's name, phone, or full address. This is a privacy/data rule enforced by the backend contract (`DriverOrderOffered` vs `DriverOrderAssigned` in `openapi/driver.v1.yaml`), but the UI must not attempt to backfill it from a cache of a previous assigned trip either.
- **RTL for ar/ur, LTR for en/bn/hi** — five locales, not two. Every `@Preview` block needs `locale=` for all five (Urdu/Bengali/Hindi previews may render with fallback glyphs until a native reviewer signs off — see CLAUDE.md's "مسرد المصطلحات" table, which is intentionally left with ⏳ for those three columns; don't fill them in yourself).
- **Numbers, phone numbers, and money are always Latin-digit and direction-isolated** (`BidiText.ltr` equivalent) regardless of locale — this app is nothing but numbers (amounts, distances, countdowns, phone numbers).
- **The 45-second offer screen is full-screen, with a visible countdown, continuous sound, and vibration** — this is a phone ringing, not a notification. Treat it like an alarm-clock UI, not a dialog.
- **A trip screen must survive being reopened after the app was killed mid-trip** — state comes back from Room + a server refetch, not from process memory. Design the loading state for "resuming a trip in progress," which looks different from "loading a fresh list."

## Steps

### 1. Plan the screen as a system, not a layout

Before any code, list:
- Every state (loading, resuming-a-trip, empty, partial, error, offline, success).
- Every interactive element and its touch target (64dp minimum for a trip action — see table above).
- Every animation and its `spring()` parameters.
- Every string across the five locales.
- Every number/money/phone value and how it goes through the centralized formatter (Latin digits always).

### 2. Build skeleton-first

1. Skeleton loader matching the eventual layout.
2. Empty state — and for this app, empty is usually not "nothing exists," it's "no orders right now" (decision 47): the empty state **must name the reason** (my linked branches by name / I'm outside the nearest branch's radius / the branch is closed / I'm marked unavailable) — a silent blank screen is explicitly the bug decision 47 was written to prevent.
3. Offline state — distinct visual language from "error," since a driver losing signal in a moving car is routine, not exceptional.
4. Error state (inline banner + retry).
5. Happy path.
6. Animations (`spring()`).
7. Accessibility pass (TalkBack, `contentDescription` on every action).
8. Localization pass — five `@Preview(locale=…)` variants, light AND dark.

### 3. Pre-merge checklist

- [ ] Every trip action is ≥ 64dp.
- [ ] Loading is a skeleton, never a spinner.
- [ ] Empty state names the reason (branch list / out of radius / branch closed / offline toggle) — never a blank silent screen.
- [ ] Offline state is visually distinct from a request error.
- [ ] Pre-acceptance screens show ONLY area + distance + amount — no name/phone/full address.
- [ ] Tested in ar, en, ur, bn, hi via `@Preview(locale=…)`, light AND dark.
- [ ] All animations use `spring()`, never `tween()` for user-driven motion.
- [ ] Numbers/phone/money are Latin-digit and direction-isolated.
- [ ] Verified on the real device (S25) in-pocket if the screen touches location/availability — an emulator screenshot does not clear this box.
- [ ] Driver CLAUDE.md session-end protocol run (screens map, glossary, decision log) if this added a screen or a term.

---

## Anti-patterns (instant rejection)

- Copying POS's 56dp touch targets onto a trip action here.
- `fontScale` locked to 1f, or system scale ignored.
- A picker for accent color / terminal appearance.
- A blank "no orders" screen with no reason named.
- Full customer name/phone/address visible before acceptance.
- Only two locale `@Preview`s (ar/en) when the app ships five.
- Verifying a location-adjacent change on emulator only.
- `tween()` on the offer countdown or any user-caused transition.

## When in doubt

Look at how a food-delivery **courier** app (not a merchant POS) handles the offer screen, the trip screen, and the earnings screen — this product is judged against that category, not against Foodics/Square. Ask before downgrading the 64dp rule or the pre-acceptance privacy rule; those two are frozen decisions (driver CLAUDE.md decisions 23 and the ⚡ performance table), not defaults to relax under deadline pressure.
