// node site/sync.test.mjs : the planner merge, the one place sync can lose a student's data
import { readFileSync } from "node:fs";
import vm from "node:vm";
import assert from "node:assert/strict";

const ctx = {};
vm.runInNewContext(readFileSync(new URL("./sync.js", import.meta.url), "utf8") + "\nthis.mergePlans = mergePlans; this.signedOutCopy = signedOutCopy;", ctx);
const merge = (a, b) => JSON.parse(JSON.stringify(ctx.mergePlans(a, b)));
const sorted = xs => [...xs].sort();

// first sync: a laptop's planner and an empty Drive file -> the laptop's planner, untouched
const laptop = { saved: ["a", "b"], rows: { a: { status: "applied", updated: 10, snap: { title: "A" } }, b: { starred: 5, notes: "b notes" } }, cols: ["status"] };
let m = merge(laptop, null);
assert.deepEqual(m.saved, ["a", "b"]);
assert.equal(m.rows.a.status, "applied");
assert.deepEqual(m.cols, ["status"]);

// two devices: each keeps what the other lacks; a row edited on both takes the later edit
const phone = { saved: ["a", "c"], rows: { a: { status: "interviewing", updated: 20 }, c: { notes: "call Dana", updated: 15 } } };
m = merge(laptop, phone);
assert.deepEqual(sorted(m.saved), ["a", "b", "c"]);
assert.equal(m.rows.a.status, "interviewing", "the phone edited a later");
assert.equal(m.rows.a.snap.title, "A", "the copy of the posting survives when the newer side lacks it");
assert.equal(m.rows.c.notes, "call Dana");

// unstarred on the phone at 30 (starred at 5): it leaves the planner everywhere, and its notes are kept
m = merge(laptop, { saved: ["a"], rows: {}, unstarred: { b: 30 } });
assert.ok(!m.saved.includes("b"), "the older copy does not star it again");
assert.equal(m.rows.b.notes, "b notes", "unstarring keeps the notes");
assert.equal(m.unstarred.b, 30, "kept, so the next sync honours it too");
// starred again at 40: back in the planner with its notes
m = merge({ ...m, rows: { ...m.rows, b: { ...m.rows.b, starred: 40 } }, saved: [...m.saved, "b"] }, { saved: ["a"], unstarred: { b: 30 } });
assert.ok(m.saved.includes("b") && m.rows.b.notes === "b notes");

// deleted from the planner on the phone at 30: gone everywhere, notes too, and it stays gone
m = merge(laptop, { saved: ["a"], rows: {}, deleted: { b: 30 } });
assert.ok(!m.saved.includes("b") && !("b" in m.rows), "deleted at 30, starred at 5");
assert.equal(m.deleted.b, 30);
// ...unless starred again after the delete
m = merge({ saved: ["a", "b"], rows: { b: { starred: 40 } } }, { deleted: { b: 30 } });
assert.ok(m.saved.includes("b"));

// postings starred before stars carried a time still sync (starred missing = 0)
m = merge({ saved: ["old"] }, { saved: [] });
assert.deepEqual(m.saved, ["old"]);

// which postings live does not depend on which copy comes first
assert.deepEqual(sorted(merge(phone, laptop).saved), sorted(merge(laptop, phone).saved));
// signing in after working signed out (owner, 2026-10-06): the browser's stars and notes join the account, its
// removals do not touch it; the account's column choice stays
const account = { saved: ["a", "b"], rows: { a: { status: "applied", updated: 10 }, b: { notes: "keep me", updated: 10 } }, cols: ["status", "notes"] };
const signedOut = { saved: ["a", "g"], rows: { a: { notes: "signed-out note", updated: 50 }, g: { starred: 60 } }, deleted: { b: 70 }, unstarred: { a: 70 }, cols: ["deadline"] };
m = merge(account, ctx.signedOutCopy(signedOut));
assert.deepEqual(sorted(m.saved), ["a", "b", "g"], "b deleted and a unstarred while signed out: both stay in the account; g joins it");
assert.equal(m.rows.b.notes, "keep me");
assert.equal(m.rows.a.notes, "signed-out note", "a newer edit made signed out is still an edit");
assert.deepEqual(m.cols, ["status", "notes"]);
// without signedOutCopy the same merge would delete b from the account: the bug the owner found
assert.ok(!merge(account, signedOut).saved.includes("b"));
console.log("sync merge: all checks pass");
