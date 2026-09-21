---
name: update-claude-md
description: Use this skill IMMEDIATELY before ending any session in this project where you added a module, dependency, feature, screen, Room entity, API endpoint, or made an architectural decision, OR finished a step of a phase. Updates ONLY CLAUDE.md (there is no separate SPRINT_PROMPTS.md in this repo) — it is project memory and MUST stay in sync with the code in the same change set, and its own end-of-session protocol is mandatory reading before this skill is applied.
---

# Skill: update-claude-md

There is **exactly ONE** project-memory file at the root of this repo: **`CLAUDE.md`**. Unlike the POS repo, there is **no `SPRINT_PROMPTS.md`** here — all status tracking (phase, current step, what's next, what's blocked on the project owner) lives inside CLAUDE.md's own "🔚 تسليم الجلسة" header section and its "🗺️ المراحل" table. Do not create a second tracking file "to mirror POS's convention" — that would itself violate this repo's own documented structure.

A code change that doesn't update CLAUDE.md in the same commit is **incomplete**.

## ⚠️ This file is version-controlled here — unlike the backend repo

Read CLAUDE.md's own "🌿 Git والتحقق" section: `CLAUDE.md` and `.claude/` **are tracked in this repo** (there's no production server pulling this repo the way the backend's is), which is the **opposite** convention from `filamentv4-saas-whatsapp-qrmenu`, where `CLAUDE.md` is gitignored on purpose. Don't apply the backend repo's "never commit CLAUDE.md" rule here — that would silently stop memory from persisting across sessions in the one repo where it's supposed to.

## The mandatory session-end protocol (read this section of CLAUDE.md itself first)

CLAUDE.md's own "🔴 بروتوكول **نهاية** الجلسة" section is the actual instruction set — this skill exists to make sure you run it, not to duplicate it. Before your last message to the user, update the "أين وصلنا الآن" table with:

- Date · phase and step · last commit (hash + title) · pushed? · tree status · **green test counts**.
- What was accomplished this session (short bullets).
- **What's next, immediately** — one actionable sentence ("add screen X"), not "continue phase 1."
- Any uncommitted work, and why.
- What's waiting on the project owner.
- Any new decision → "سجلّ القرارات" table (dated row, WHAT and WHY).
- Any pitfall that cost more than 30 minutes → "المزالق" list.

## When to update which OTHER section (BLOCKING — never defer)

| Code change | CLAUDE.md section |
|---|---|
| Added a new `:core:*` or `:feature:*` module | "🧱 بنية المشروع والمكتبات" module tree |
| Added/renamed a dependency in `gradle/libs.versions.toml` | The versions block in "🧱 بنية المشروع والمكتبات" |
| Added a new convention plugin in `build-logic/` | Same section, plugin list |
| Added a `@Serializable Route` object / new screen | "🗺️ خريطة الشاشات" (the 18-screen table — this is this repo's equivalent of POS's §7 Navigation Map, but it's a screen inventory with notes, not just routes) |
| Bumped `DriverDatabase.version` or added an entity | "سجلّ مخطط Room" (new dated row) **with proof the migration was actually executed against a prior schema**, not just written |
| Added a Retrofit interface / wired a new endpoint | The relevant API section, AND `openapi/driver.v1.yaml` + its JSON mirror if the contract itself changed |
| Made an architectural choice not already frozen in the 47-decision table | "سجلّ القرارات" (dated row, WHAT and WHY) |
| Added a user-facing term (screen label, status word) | "🔤 مسرد المصطلحات" — fill ar/en immediately; leave ur/bn/hi as ⏳ unless a native speaker has actually reviewed them (do not invent those three columns yourself) |
| Finished a step within a phase | "🗺️ المراحل" — flip the step's status, and the phase-status table under "حالة المراحل" |
| Hit a pitfall costing > 30 minutes | "المزالق" list at the end of the file |

**Consistency rule (specific to this repo):** the "أين وصلنا الآن" header, the "🗺️ المراحل" table, and the "حالة المراحل" mini-table near the end of the file must all agree on what phase/step you're at. If they don't, fix them to match before ending the session — a mismatch here is exactly the "next session starts guessing" failure the protocol exists to prevent.

## 🔴 Never re-open a frozen decision from here

This skill updates the file — it does not authorize you to change a decision recorded in the "🧊 القرارات المُجمَّدة" table (47 entries) or in "سجلّ القرارات." If something in the code contradicts a frozen decision, the header instruction is explicit: **surface it to the project owner, don't silently "fix" the decision yourself.** Only add a *new* dated decision-log row for something genuinely not covered by the existing 47.

## How to update

1. Open `CLAUDE.md` at the repo root.
2. Locate the relevant section by its heading (the file uses `##`/`###` Arabic headings, not numbered sections like POS's `§N` — search by heading text, not by number).
3. Add/edit the row — terse, one line where possible.
4. For decision-log entries, always include the date and the *why* in one sentence.
5. Save and stage in the same commit as the code — but **do not commit or push yourself** unless explicitly asked; CLAUDE.md's own git rules for this repo say work stays on `main` uncommitted until the project owner approves, and pushes wait for the owner's go-ahead after seeing the on-device verification result.

## Verification before ending the session

- [ ] Did I add a module? → module tree updated?
- [ ] Did I bump a dependency version? → versions block updated?
- [ ] Did I add a route/screen? → "🗺️ خريطة الشاشات" updated?
- [ ] Did I touch Room? → schema-history row added AND migration proven to run (not just read)?
- [ ] Did I wire a new endpoint? → API section AND `openapi/driver.v1.yaml` (+ mirror) updated if the contract changed?
- [ ] Did I make an architectural choice? → decision log updated?
- [ ] Did I finish a phase-1 step? → "🗺️ المراحل" row and "حالة المراحل" table both flipped, and they agree with each other?
- [ ] Did I introduce a new user-facing term? → glossary updated (ar/en filled, ur/bn/hi left ⏳ if unreviewed)?
- [ ] Session-end header ("أين وصلنا الآن") fully rewritten, not just appended to?

If any answer is "yes but I didn't update," fix it now — before the last message, not after.

## Anti-patterns

- ❌ "I'll update CLAUDE.md next session" — no, do it now, same commit.
- ❌ Creating a `SPRINT_PROMPTS.md` or similar second tracking file — this repo deliberately has only one.
- ❌ Treating `CLAUDE.md` as gitignored here because it is in the backend repo — it is tracked in this repo.
- ❌ Silently editing an entry in "🧊 القرارات المُجمَّدة" instead of flagging the conflict to the project owner.
- ❌ Inventing Urdu/Bengali/Hindi glossary terms without a native-speaker review.
- ❌ Claiming a Room migration is documented as "done" without proof it was executed against a real prior-schema database.
- ❌ Leaving the phase/step header, the "🗺️ المراحل" table, and "حالة المراحل" disagreeing with each other at session end.
