---
name: Internship & Research Finder
description: Every open internship and funded research programme from first-party sources, drawn as a river by major and browsed as a list.
colors:
  night-ground: "#0a0f15"
  night-raised: "#10161e"
  night-high: "#161e28"
  ink: "#eef1ec"
  ink-secondary: "#aab5c0"
  ink-tertiary: "#7f8b97"
  hairline: "rgba(238, 241, 236, 0.11)"
  hairline-strong: "rgba(238, 241, 236, 0.2)"
  major-engineering: "#f4a259"
  major-business: "#e6d27e"
  major-finance: "#4fd1a1"
  major-computing: "#6aa9ff"
  major-life-sciences: "#9bdc6b"
  major-data-maths: "#b892ff"
  major-marketing: "#ff8fa3"
  major-physical-sciences: "#5fd0e8"
  major-government-law: "#d3b58d"
  major-arts-media: "#ff7a5c"
  major-education: "#ffd34e"
  major-social-sciences: "#f0a6e8"
  major-other: "#8d98a4"
typography:
  display:
    fontFamily: "Bricolage Grotesque, system-ui, sans-serif"
    fontSize: "clamp(44px, 7.4vw, 96px)"
    fontWeight: 600
    lineHeight: 0.98
    letterSpacing: "-0.022em"
  headline:
    fontFamily: "Bricolage Grotesque, system-ui, sans-serif"
    fontSize: "clamp(32px, 4.2vw, 56px)"
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.025em"
  title:
    fontFamily: "Bricolage Grotesque, system-ui, sans-serif"
    fontSize: "clamp(24px, 2.6vw, 34px)"
    fontWeight: 600
    lineHeight: 1.02
    letterSpacing: "-0.025em"
  statement:
    fontFamily: "Bricolage Grotesque, system-ui, sans-serif"
    fontSize: "clamp(22px, 2.3vw, 30px)"
    fontWeight: 500
    lineHeight: 1.32
    letterSpacing: "-0.012em"
  body:
    fontFamily: "Schibsted Grotesk, system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.55
  body-small:
    fontFamily: "Schibsted Grotesk, system-ui, sans-serif"
    fontSize: "15px"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "Schibsted Grotesk, system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 500
    lineHeight: 1.2
  data:
    fontFamily: "Fragment Mono, ui-monospace, monospace"
    fontSize: "13px"
    fontWeight: 400
    lineHeight: 1.3
    fontFeature: "tnum"
rounded:
  row: "4px"
  focus: "6px"
  tooltip: "8px"
  bar: "10px"
  pill: "999px"
spacing:
  page: "clamp(16px, 4vw, 56px)"
  hair: "4px"
  tight: "8px"
  cluster: "12px"
  column: "20px"
  row-y: "16px"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.night-ground}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "0 22px"
    height: "50px"
  button-ghost:
    backgroundColor: "rgba(10, 15, 21, 0.35)"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "0 22px"
    height: "50px"
  chip:
    textColor: "{colors.ink-secondary}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "0 14px"
    height: "36px"
  chip-active:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.night-ground}"
    rounded: "{rounded.pill}"
  segment-active:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.night-ground}"
    rounded: "{rounded.pill}"
    padding: "0 14px"
  search-field:
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "0 16px"
    height: "44px"
  posting-row:
    textColor: "{colors.ink}"
    rounded: "{rounded.row}"
    padding: "16px 4px"
  posting-row-hover:
    backgroundColor: "{colors.night-raised}"
  detail-drawer:
    backgroundColor: "{colors.night-raised}"
    textColor: "{colors.ink}"
    width: "min(640px, 100%)"
  river-label:
    backgroundColor: "rgba(10, 15, 21, 0.86)"
    textColor: "{colors.ink}"
    typography: "{typography.label}"
    rounded: "{rounded.tooltip}"
    padding: "7px 11px"
---

# Design System: Internship & Research Finder

## Overview

**Creative North Star: "The Night River"**

The postings themselves are the art. Every open posting is one point of light on a night ground, flowing with the rest of its major in one of thirteen streams; below the river the same data turns into a working browser. The world is dark, quiet and typographic: one warm off-white ink on a blue-black ground, cool greys for everything secondary, and colour only where it means a field of study.

Density is calm at the top and working-dense below. The first screen is a full-height canvas with a large headline anchored bottom-left; after it, the page is hairline rules, pill controls and a long list of rows with no cards. Motion is slow and exponential: sections sharpen out of a blur once as they arrive, list rows arrive in a short wave, and the river drifts continuously unless the visitor asks for reduced motion.

This system was recorded from the shipped build (site/index.html, site/style.css, site/app.js) on 2026-10-06. The North Star name is the documenter's, taken from the direction contract's thesis; the owner has not been asked to confirm it.

**Key Characteristics:**
- Blue-black night ground (three steps) with a single warm off-white ink; the accent is the ink, not a hue.
- Thirteen fixed major hues, used only as data: river stream, strip bar, row dot, chip dot.
- Bricolage Grotesque for display, Schibsted Grotesk for UI, Fragment Mono only for dates, deadlines, counts, codes and the river hint.
- Pill controls, hairline rules, no cards, no decorative imagery; the river is the only picture.
- One exponential ease-out curve for everything; blur-to-sharp reveals, once.

## Colors

A night palette of one ink and three cool greys, with thirteen saturated hues reserved for meaning.

### Primary
- **Warm Off-White Ink** (`ink`): the only accent. Headlines, body text, the filled primary pill, active chip and segment fills, focus outlines (`--accent` resolves to this same value), and the two moments of emphasis in a posting row ("Posted today", "Apply by" within 14 days).

### Neutral
- **Night Ground** (`night-ground`): page background, the text colour on filled ink controls, the river canvas clear colour and its trail wash.
- **Raised Night** (`night-raised`): row hover fill and the detail drawer surface.
- **High Night** (`night-high`): hover fill behind the round star button.
- **Cool Grey Secondary** (`ink-secondary`): lede paragraph, section subtitles, nav links at rest, organisation and location in rows, inactive chip and segment text, drawer description.
- **Slate Tertiary** (`ink-tertiary`): dates and deadlines at rest, the river hint, the major line in rows, counts in chips, footer, drawer fact labels, placeholder text.
- **Hairline** (`hairline`): every rule: row dividers, fact tables, sticky controls bottom edge, footer top, inactive chip border, drawer edge, river label border.
- **Strong Hairline** (`hairline-strong`): borders of interactive pills (buttons, search, select, segmented groups, icon buttons).

### Secondary
Data only. Thirteen hues, one per major (`major-*` tokens, mirrored in `MAJOR_COLOURS` in site/app.js). They colour the river streams (drawn with additive blending at 0.55 alpha at rest, 1 when lit, 0.1 when another stream is lit), the strip bars (with Night Ground text on them), the 7px dot before the major in each row, and the 8px dot inside each chip. Other is always a neutral grey.

### Named Rules
**The Ink Is The Accent Rule.** Emphasis is ink against grey, never a hue. A thing is important because it is brighter, not because it is coloured. (Round 1 of finish review moved the accent off Engineering's orange for exactly this reason: a major's hue used as accent claims that major.)

**The Hue Means A Major Rule.** A major hue appears only where it identifies that major's data. No hue on buttons, links, headings, backgrounds, borders, icons or brand marks.

**The Rare Mark Rule.** Within a row, ink replaces grey only for "Posted today" and for "Apply by" dates within 14 days. Yesterday and older stay Slate Tertiary, so the mark stays rare enough to scan for.

## Typography

**Display Font:** Bricolage Grotesque (with system-ui, sans-serif), weights 500 to 700, optical sizes 12 to 96
**Body Font:** Schibsted Grotesk (with system-ui, sans-serif), weights 400, 500, 600
**Label/Mono Font:** Fragment Mono (with ui-monospace, monospace), weight 400 only

**Character:** A wide, slightly quirky grotesque at large scale with tight but untouching tracking, over a sober newsroom grotesque for everything you read and operate. The mono is a data voice, not a style: it marks values that are machine facts.

### Hierarchy
- **Display** (600, clamp(44px, 7.4vw, 96px), 0.98): the single first-screen headline, balanced wrap.
- **Headline** (600, clamp(32px, 4.2vw, 56px), 1.02): section titles ("Pick a field.", "How it works").
- **Title** (600, clamp(24px, 2.6vw, 34px)): the live list summary ("10,940 open across every field"), tabular numerals. The drawer's posting title uses the same face at clamp(28px, 3.2vw, 40px).
- **Statement** (500, clamp(22px, 2.3vw, 30px), 1.32): the one large prose sentence about coverage; numbers inside it at 600. Source counts in the facts table use the display face at 500, 22px.
- **Body** (400, 17px, 1.55): page default. Lede at clamp(16px, 1.4vw, 19px), max 60ch; notes max 70ch; drawer description 16px / 1.65, max 68ch. Posting titles are body at 500.
- **Body small** (400, 15px): nav, search and select text, organisation and location in rows, footer at 14px.
- **Label** (500, 14px, 1.2): chips, segmented options, the river hover label, drawer fact labels (400).
- **Data** (Fragment Mono 400, 12 to 13px): "Posted …" and "Apply by …" in rows, counts in chips and strip bars, the saved count badge, the river hint (13px / 1.45), counts in the river label.

### Named Rules
**The Mono Is For Facts Rule.** Fragment Mono only sets dates, deadlines, counts, codes and the river hint. Field names, drawer labels and prose are never mono (mono was taken off field names and drawer labels in finish review round 1). "Funded programme" in the date column switches back to the UI face because it is not a date.

**The Counted Numbers Rule.** Every number that a visitor compares (lede counts, summary, chip and strip counts, source facts) is set with tabular numerals.

## Layout

The first screen is a full-viewport header (100svh, min 600px) with the canvas river filling it and a vertical gradient (50% night at the top, clear through the middle, 88% night at the bottom) so the bar and lede stay legible. The river occupies a band starting 12% down and spanning 27% of the height (10% and 22% under 760px). The lede is pinned bottom-left, max 1000px wide; the mono hint pins bottom-right, max 240px; both sit clamp(36px, 8vh, 88px) above the bottom. The top bar holds the wordmark left and three links right.

Below, content sits in a centred column, max 1280px, with page padding of clamp(16px, 4vw, 56px) on both sides. Sections are separated by large viewport-relative gaps (fields clamp(80px, 14vh, 150px) from the hero, list clamp(56px, 9vh, 96px), "How it works" clamp(110px, 18vh, 200px)), not by rules or backgrounds.

Posting rows are a five-column grid: 130px date, flexible title (1.5fr), location (0.7fr), 170px deadline, 44px star, with a 20px gap and 16px vertical padding. The filter controls are sticky at the top of the list on a 90% night backdrop with 12px blur.

Responsive: under 900px rows collapse to one text column plus the star (date, title, location, deadline stacked) and the two-column "How it works" grids go to one column. Under 760px the nav keeps only Saved, the hint hides, the strip shortens to 92px with no labels, chips become a single horizontally scrolling line bleeding to the page edge, and the select takes the full width.

## Elevation & Depth

Flat and tonal. Depth comes from three ground steps and from transparency over the river, not from shadows. The only shadow belongs to the detail drawer, which also brings a blurred scrim; the sticky controls and the river label use translucent night with blur instead of a lift.

### Shadow Vocabulary
- **Drawer edge** (`box-shadow: -24px 0 60px rgba(0,0,0,.4)`): the right-hand detail drawer only, a soft cast to the left over the 55% scrim.

### Named Rules
**The No Lift Rule.** Rows, chips and controls never gain a shadow on hover; they change fill or border. The primary pill rises 2px on hover; that is the only movement-as-elevation.

## Shapes

Two shapes do almost all the work: the full pill (999px) for every control, button, chip, segment group, search field, select and badge, and the circle for icon buttons (close, star). Data bars in the strip use a gentle 10px radius; the river hover label 8px; rows only a 4px radius so their hover fill does not look like a card. Rules are 1px hairlines. There are no cards and no bordered content boxes.

## Components

### Buttons
Quiet pills that put the ink where the action is.
- **Shape:** full pill (999px), 50px tall, 22px side padding, 10px gap to a trailing 18px icon.
- **Primary:** ink fill, night text, 500 weight 16px. Used once per context: "Choose your major" on the first screen, "Apply on the employer's site" in the drawer.
- **Hover / Focus:** primary rises 2px over 0.25s on the shared ease; ghost brightens its border to 45% ink. Focus is a 2px ink outline, 3px offset.
- **Ghost:** 35% night fill over whatever is behind, strong-hairline border. "Show more", "Clear filters", the drawer's Save.
- **Disabled:** 50% opacity, no movement.

### Chips
- **Style:** 36px pill, hairline border, no fill, Cool Grey text at 14px, an 8px dot in the major's hue, then the count in mono Slate.
- **State:** hover brightens text to ink and border to strong hairline; selected fills with ink, night text, count at 60% night. The hue stays in the dot only; the fill is always ink.

### Segmented controls
- **Style:** a 44px pill outline with 3px inner padding holding pill options at 14px.
- **State:** the pressed option fills with ink and night text; others are Cool Grey, ink on hover.

### Inputs / Fields
- **Style:** search is a 44px outlined pill with a leading 18px search icon; the select is a matching outlined pill on night with a drawn chevron.
- **Focus:** search brightens its border to Cool Grey (focus-within); the select takes the 2px ink outline.

### Navigation
Wordmark (display face, 600, 17px, with a three-wave line mark in ink) left; Browse, How it works and Saved right in Cool Grey 15px, ink on hover. Saved carries an ink pill badge with the mono count, hidden at zero. Under 760px only Saved remains.

### Posting row
A ruled list item, not a card. Mono date, then title (500) over organisation in Cool Grey and a small line with the major's dot, major and type in Slate; location; mono deadline; a round star. Hover fills the row with Raised Night; the title underlines on hover. The star is outline Slate at rest, filled ink when saved.

### Detail drawer
A right-hand panel, max 640px, on Raised Night with a hairline left edge and the drawer shadow, sliding 40px in over 0.5s over a blurred 55% scrim. Inside: organisation, title, a two-column hairline fact table (130px labels, 104px on mobile), the primary and ghost actions, then the description at 16px / 1.65. Focus is trapped while open; Escape closes.

### The river (signature)
A canvas drawing one 1.3px point per open posting, banded into thirteen streams sized by count and coloured by major, bending on two slow sine waves and drifting left to right. Each frame washes the canvas with 22% night, which leaves short trails; points blend additively. The cursor parts a stream within 80px; hovering names the stream and its count in a translucent label; clicking selects that major in the list below and lights its stream while the others dim to 10%. Drawing stops while the canvas is off screen.

### Field strip
A 150px row of rounded bars, each as wide as its major's share of open postings, in the major's hue with Night text (mono count over the name) when the bar is wide enough. Selecting a major dims the others to 28% and grows the chosen bar to 1.6 times its share. On first reveal the bars grow up from the baseline in a 45ms stagger.

## Do's and Don'ts

### Do:
- **Do** keep the ink as the only accent; emphasis is ink against grey.
- **Do** use a major's hue only to identify that major's data, the same hue everywhere that major appears.
- **Do** set dates, deadlines, counts and codes in Fragment Mono with tabular numerals, and nothing else.
- **Do** use the one ease, cubic-bezier(.16, 1, .3, 1), for every transition and arrival.
- **Do** reveal a section once, rising 26px from an 8px blur over 0.9s; list rows rise 14px from a 6px blur over 0.7s in a 40ms wave counted from the first row on screen.
- **Do** keep everything visible without script and show everything at once, with the river frozen, under prefers-reduced-motion.
- **Do** separate content with hairline rules and space, and make every control a pill or a circle.

### Don't:
- **Don't** colour a button, link, heading, border or mark with a major hue.
- **Don't** put content in cards or bordered boxes; rows are ruled list items.
- **Don't** set field names, labels or prose in mono.
- **Don't** add shadows to rows, chips or controls; the drawer is the only lifted surface.
- **Don't** mark more than today's postings and deadlines within 14 days in ink; a mark on everything marks nothing.
