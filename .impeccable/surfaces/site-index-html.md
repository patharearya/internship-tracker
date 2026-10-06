---
version: 1
slug: "site-index-html"
primary_target: "site/index.html"
related_targets: []
---

# Public page (site/index.html)

Mode: Operate (the visitor browses their major and finds postings), opened by an Experience-grade first viewport the owner asked for.

Audience and job: US college students, browsing everything open in their own major, narrowing by state or remote, arrangement and level, opening postings and saving the ones worth applying to. Owner priority: browsing a major over spotting what's new.

Constraints: static HTML/CSS/JS, no build step, GitHub Pages; reads data/postings.json and data/descriptions/{n}.json; never promise hourly updates; no invented claims; Other is always a group; thin majors are named as thin.

Owner references (binding): calculo-hack.vercel.app, veriscan-official.vercel.app, scamshield-olive.vercel.app. A first screen that fills the page with calm moving art, content that arrives with motion on scroll, very modern. The owner chose the "original" data-art river after comparing it with a glow version (rejected: too bright, rainbow) and a starlight version.

## Direction contract

THESIS: The postings themselves are the art. The first screen is a slow river of 9,400 points, one per open posting, in 13 streams by major; the page then turns that river into a working browser. Refuses the category default of a search bar over a grid of logo cards.

OWN-WORLD: Night ground #0a0f15, warm off-white ink #eef1ec, cool grey secondaries; thirteen fixed major hues used only as data (stream, bar, row dot, chip when active), never as decoration. Bricolage Grotesque display at large scale with tight but untouching tracking, Geist for UI, Geist Mono only for dates, counts and codes. Pill buttons, hairline rules, no cards.

STORY: The visitor sees the field of real postings, learns what it is in one sentence with real counts, picks a major from the river or the strip, narrows with filters that apply instantly, opens a posting to read its description, stars it, and leaves through the employer's own link.

FIRST VIEWPORT: Full-height canvas river across the upper half (original demo: separate coloured streams, short trails, 1.3px points, hover names a stream and its count, click selects that major). Bottom-left: headline "Internships in your field, straight from the source." at up to 96px, one line of real counts, primary pill "Choose your major", secondary "See what's new". Top bar: wordmark left, Browse / How it works / Saved right. Mono hint bottom-right.

FORM: Owner-pinned direction from references, built code-first; candidate 1 of the owner's two demos; seed key 0bede2ee (roll set aside by the owner's references).

Signature interaction: choosing a stream in the river selects that major below and the strip bar grows; filters reflow the list live with a staggered arrival. Motion grammar: exponential ease-out reveals with blur-to-sharp on scroll, once per element; reduced motion shows everything at once.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
