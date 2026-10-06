// Planner sync with the student's own Google Drive (Q26): one hidden file in the Drive app-data folder, which only
// this site can open. No server; the browser talks to Drive directly with a token Google gives it.
// Loaded before planner.js; sync.test.mjs runs mergePlans without a browser.

const DRIVE_FILE = "planner.json";

/**
 * Two copies of the planner (this browser, Drive) -> one. Without the times below, whatever one device removed would
 * come back from the other copy on every sync. Two kinds of removal, because they mean different things:
 * - unstarred[key]: the star was taken off on the browse page; the posting leaves the planner, the notes are kept
 *   (starring it again brings them back). The star wins only if it is newer (rows[key].starred).
 * - deleted[key]: removed from the planner, notes and all. The row lives only if edited or starred after that.
 * A live row takes the copy edited last. A copy is { saved: [key], rows: { key: {...} }, unstarred, deleted, cols }.
 */
function mergePlans(a, b) {
  a = a || {}; b = b || {};
  const later = (x = {}, y = {}) => { const o = { ...x }; for (const [k, t] of Object.entries(y)) o[k] = Math.max(o[k] || 0, t); return o; };
  const unstarred = later(a.unstarred, b.unstarred), deleted = later(a.deleted, b.deleted);
  const rowsA = a.rows || {}, rowsB = b.rows || {}, savedA = new Set(a.saved || []), savedB = new Set(b.saved || []);
  const alive = r => r ? Math.max(r.updated || 0, r.starred || 0) : -1;
  const out = { saved: [], rows: {}, unstarred: {}, deleted: {}, cols: a.cols || b.cols };
  for (const k of new Set([...savedA, ...savedB, ...Object.keys(rowsA), ...Object.keys(rowsB)])) {
    const ra = rowsA[k], rb = rowsB[k];
    if ((deleted[k] || 0) >= Math.max(alive(ra), alive(rb), 1)) { out.deleted[k] = deleted[k]; continue; }
    const newest = !ra ? rb : !rb ? ra : alive(rb) > alive(ra) ? rb : ra;
    if (newest) out.rows[k] = { ...(newest === ra ? rb : ra), ...newest, snap: newest.snap || (ra || rb).snap };
    const starredAt = Math.max(ra?.starred || 0, rb?.starred || 0);
    if ((savedA.has(k) || savedB.has(k)) && !(unstarred[k] >= Math.max(starredAt, 1))) out.saved.push(k);
    else if (unstarred[k]) out.unstarred[k] = unstarred[k];
  }
  for (const [k, t] of Object.entries(deleted)) if (!(k in out.rows)) out.deleted[k] = t;
  for (const [k, t] of Object.entries(unstarred)) if (!out.saved.includes(k)) out.unstarred[k] = t;
  return out;
}

const drive = {
  async call(token, url, init = {}) {
    const r = await fetch(url, { ...init, headers: { Authorization: `Bearer ${token}`, ...(init.headers || {}) } });
    if (r.status === 401) throw Object.assign(new Error("signed out"), { expired: true });
    if (!r.ok) throw new Error(`Google Drive answered ${r.status}`);
    return r;
  },
  async find(token) {
    const q = encodeURIComponent(`name='${DRIVE_FILE}'`);
    const r = await drive.call(token, `https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=${q}&fields=files(id)`);
    return (await r.json()).files?.[0]?.id || null;
  },
  async read(token, id) {
    return (await drive.call(token, `https://www.googleapis.com/drive/v3/files/${id}?alt=media`)).json();
  },
  async write(token, id, data) {
    const body = JSON.stringify(data);
    if (id) {
      await drive.call(token, `https://www.googleapis.com/upload/drive/v3/files/${id}?uploadType=media`,
        { method: "PATCH", headers: { "Content-Type": "application/json" }, body });
      return id;
    }
    const meta = JSON.stringify({ name: DRIVE_FILE, parents: ["appDataFolder"] }), cut = "irf" + Date.now();
    const multipart = `--${cut}\r\nContent-Type: application/json\r\n\r\n${meta}\r\n--${cut}\r\nContent-Type: application/json\r\n\r\n${body}\r\n--${cut}--`;
    const r = await drive.call(token, "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
      { method: "POST", headers: { "Content-Type": `multipart/related; boundary=${cut}` }, body: multipart });
    return (await r.json()).id;
  }
};
