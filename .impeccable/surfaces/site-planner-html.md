---
version: 1
slug: "site-planner-html"
primary_target: "site/planner.html"
related_targets: []
---

# Planner (site/planner.html)

Mode: Operate. The student plans and tracks applying to the postings they starred.

Audience and job: US college students who starred postings while browsing. They open the planner repeatedly through the season to decide what to apply to next, tick what they applied to, and keep notes. Browser storage only (Q26); Google sign-in comes later and must be able to sync the same data.

Constraints: static HTML/CSS/JS, no build step; inherits DESIGN.md (The Night River) whole; reads data/postings.json for current state (open, closed with date, or no longer listed); every computed value (apply-by suggestion, days open) is computed in code; no reminders (owner). Owner answers 2026-10-06: own page, the top bar's Saved becomes Planner; core six columns by default (Posting, Status, Applied, Apply by, Deadline, Notes), all others one tick away and remembered; Table / Timeline toggle; on phones the sheet scrolls sideways with the Posting column pinned.

## Direction contract

THESIS: The planner is the student's own ledger of the season, kept in the same night world: the browse list's rows become a sheet of editable cells, and our own monitoring (first seen, closed) is the column no generic spreadsheet has. Refuses the category default of kanban cards and coloured status badges.

OWN-WORLD: DESIGN.md unchanged: night ground, ink as the only accent, major hues only as the row's data dot and timeline start mark, Bricolage headline, Schibsted cells, Fragment Mono for every date, count and day number. Cells are hairline-ruled with no boxes; editors are native controls (select, checkbox, date, text) dressed as quiet inline fields that show a border only on hover and focus. Controls are pills; the column picker is a row of tick pills.

STORY: The student lands on their starred postings already laid out with suggested apply-by dates, sees at once which closed, ticks Applied (which stamps today's date and moves Status to Applied), adds notes, switches to Timeline to see the season as lines against today, and exports a CSV when they want it elsewhere.

FIRST VIEWPORT: Top bar as on the browse page with Planner current. Headline "Your planner." at Headline scale, then one mono-counted line ("8 postings · 3 applied · next apply-by Oct 12 · 1 closed"). A control row: segmented Table | Timeline, a Columns pill, an Export CSV ghost pill. Below, the sheet starts within the first screen: sticky header, Posting column pinned left. Empty state: a short sentence and a primary pill back to browsing.

FORM: shaped directly, no concept roll, so no seed key exists. The roll was skipped on purpose, under new-work section 3 ("Create a whole surface inside an established world"), whose closing sentence reads: "Never run the script for a local extension or a precisely specified narrow request; shape those directly." The owner specified the form (a spreadsheet-like planner with optional columns, a timeline and checkmarks) and answered placement, defaults, timeline and phone behaviour on 2026-10-06.

Signature interaction: ticking Applied stamps the date and advances Status in the same motion, and the counts line updates; switching to Timeline draws each posting's line from first seen to deadline against a today rule, closed postings ending in a cut. Motion grammar as DESIGN.md: rows arrive in the wave, views cross-fade with the one ease, reduced motion shows everything at once.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
