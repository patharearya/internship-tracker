package io.github.patharearya.internships;

import io.github.patharearya.internships.Main.Entry;
import io.github.patharearya.internships.Main.Kept;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Opened / missed / closed / reopened across consecutive runs (grill Q4). */
class MergeTest {

    static Kept kept(String board, String id) {
        Posting p = new Posting("lever", board, id, "Intern " + id, "Org", "https://x/" + id, List.of("Raleigh, NC"), "US",
                null, null, null, null, null, null, null);
        return new Kept(p, Rules.label(p));
    }

    static Entry find(List<Entry> es, String id) {
        return es.stream().filter(e -> e.posting().id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void openPostingsTakeTheCurrentRulesEvenWhenNotFetched() {
        Posting p = new Posting("workday", "b", "1", "Software Engineer Intern", "Org", "https://x", List.of("San Francisco"), null,
                null, null, null, null, null, null, null);
        Rules.Labels stale = new Rules.Labels("internship", new Rules.Label("Other", "old rules"), new Rules.Label("unknown", "old"),
                new Rules.Label("not stated", "old"), List.of(), false);
        Entry open = new Entry("workday:b:1", p, stale, "T0", "T0", 0, null, null, false);
        Entry closed = new Entry("workday:b:2", p, stale, "T0", "T0", 3, "T1", "T1", false);
        List<Entry> out = Main.relabel(List.of(open, closed), Map.of("workday:b:1", "Open to undergraduate students."), new java.util.ArrayList<>());
        assertEquals("Computer Science & IT", out.get(0).labels().major().value());
        assertEquals(List.of("CA"), out.get(0).labels().states());
        assertEquals("undergrad", out.get(0).labels().level().value(), "description from the shard files is used");
        assertSame(stale, out.get(1).labels(), "closed postings keep the labels they closed with");
    }

    @Test
    void openPostingsANewRuleRejectsLeaveAtOnceAndAreLogged() {
        // the Dhaka posting of 2026-10-06: kept before Bangladesh was rejected, then shown for three more runs
        Posting dhaka = new Posting("ashby", "commure", "9", "Intern, HR Operations (Bangladesh)", "Commure", "https://x",
                List.of("Dhaka, Bangladesh"), null, null, null, null, null, null, null, null);
        Posting ok = new Posting("ashby", "commure", "8", "Software Engineer Intern", "Commure", "https://y",
                List.of("San Francisco, CA"), null, null, null, null, null, null, null, null);
        Rules.Labels l = Rules.label(ok);
        Entry closedAbroad = new Entry("ashby:commure:7", dhaka, l, "T0", "T0", 3, "T1", "T1", false);
        List<String> log = new java.util.ArrayList<>();
        List<Entry> out = Main.relabel(List.of(new Entry("ashby:commure:9", dhaka, l, "T0", "T0", 0, null, null, false),
                new Entry("ashby:commure:8", ok, l, "T0", "T0", 0, null, null, false), closedAbroad), Map.of(), log);
        assertEquals(List.of("ashby:commure:8", "ashby:commure:7"), out.stream().map(Entry::key).toList(),
                "the open Dhaka posting is gone; closed postings are left as they closed");
        assertEquals(1, log.size());
        assertTrue(log.get(0).startsWith("ashby:commure\t9\tIntern, HR Operations (Bangladesh)\tstill open, now rejected: outside US"), log.get(0));
    }

    @Test
    void lifecycle() {
        List<Entry> r1 = Main.merge(List.of(), Map.of("lever:a", List.of(kept("a", "1"), kept("a", "2"))), "T1");
        assertEquals("T1", find(r1, "1").firstSeen());
        assertNull(find(r1, "1").closed());

        // posting 1 missing for two runs: counted, not closed
        List<Entry> r2 = Main.merge(r1, Map.of("lever:a", List.of(kept("a", "2"))), "T2");
        List<Entry> r3 = Main.merge(r2, Map.of("lever:a", List.of(kept("a", "2"))), "T3");
        assertEquals(2, find(r3, "1").misses());
        assertEquals("T2", find(r3, "1").firstMiss());
        assertNull(find(r3, "1").closed());

        // third miss closes it, dated to the first miss
        List<Entry> r4 = Main.merge(r3, Map.of("lever:a", List.of(kept("a", "2"))), "T4");
        assertEquals("T2", find(r4, "1").closed());
        assertEquals("T4", find(r4, "2").lastSeen());

        // further runs leave a closed posting alone
        List<Entry> r5 = Main.merge(r4, Map.of("lever:a", List.of(kept("a", "2"))), "T5");
        assertEquals(find(r4, "1"), find(r5, "1"));

        // reappears: open again, marked reopened, history kept
        List<Entry> r6 = Main.merge(r5, Map.of("lever:a", List.of(kept("a", "1"), kept("a", "2"))), "T6");
        Entry back = find(r6, "1");
        assertNull(back.closed());
        assertTrue(back.reopened());
        assertEquals("T1", back.firstSeen());
        assertEquals(0, back.misses());
    }

    @Test
    void oneSeenAgainResetsMisses() {
        List<Entry> r1 = Main.merge(List.of(), Map.of("lever:a", List.of(kept("a", "1"))), "T1");
        List<Entry> r2 = Main.merge(r1, Map.of("lever:a", List.of()), "T2");
        assertEquals(1, find(r2, "1").misses());
        List<Entry> r3 = Main.merge(r2, Map.of("lever:a", List.of(kept("a", "1"))), "T3");
        assertEquals(0, find(r3, "1").misses());
        assertNull(find(r3, "1").firstMiss());
        assertFalse(find(r3, "1").reopened(), "never closed, so not reopened");
    }

    @Test
    void nsfNeedsADayOfMissesBecauseItsPagingSkipsAwardsAtRandom() {
        Posting a = new Posting("nsf", "nsf-reu", "7", "REU Site: X", "U", "https://x", List.of("BOSTON, MA"), "US", "ENG",
                null, Parse.RESEARCH, null, null, null, null);
        List<Entry> r = Main.merge(List.of(), Map.of("nsf:nsf-reu", List.of(new Kept(a, Rules.label(a)))), "T0");
        for (int i = 1; i <= 23; i++) r = Main.merge(r, Map.of("nsf:nsf-reu", List.of()), "T" + i);
        assertNull(find(r, "7").closed(), "23 misses: still open");
        r = Main.merge(r, Map.of("nsf:nsf-reu", List.of()), "T24");
        assertEquals("T1", find(r, "7").closed());
    }

    @Test
    void boardNotFetchedOkLeavesItsPostingsUntouched() {
        List<Entry> r1 = Main.merge(List.of(), Map.of("lever:a", List.of(kept("a", "1")), "lever:b", List.of(kept("b", "9"))), "T1");
        // board a failed / was an anomaly this run, so it is absent from the map; b was fine
        List<Entry> r2 = Main.merge(r1, Map.of("lever:b", List.of(kept("b", "9"))), "T2");
        assertEquals(find(r1, "1"), find(r2, "1"));
        assertEquals(0, find(r2, "1").misses());
    }

    @Test
    void deadBoardsCloseAndOldClosedPostingsLeave() {
        Kept k = kept("b", "1");
        Entry open = new Entry("lever:b:1", k.posting(), k.labels(), "2026-09-01T00:00:00Z", "2026-09-20T00:00:00Z", 0, null, null, false);
        Entry closedRecently = new Entry("lever:c:2", kept("c", "2").posting(), k.labels(), "T0", "T0", 3, "2026-09-30T00:00:00Z", "2026-09-30T00:00:00Z", false);
        Entry closedLongAgo = new Entry("lever:c:3", kept("c", "3").posting(), k.labels(), "T0", "T0", 3, "2026-09-20T00:00:00Z", "2026-09-20T00:00:00Z", false);
        Entry onYoungFailure = new Entry("lever:d:4", kept("d", "4").posting(), k.labels(), "T0", "T0", 0, null, null, false);
        Map<String, Main.Failure> failures = Map.of(
                "lever:b", new Main.Failure(9, "x", "IOException: HTTP 404", "2026-09-28T00:00:00Z"),   // 8 days
                "lever:d", new Main.Failure(5, "x", "IOException: HTTP 404", "2026-10-01T00:00:00Z"),   // 5 days
                "lever:c", new Main.Failure(5, "x", "IOException: HTTP 404", null));                    // state from before since existed
        Map<String, Object> log = new java.util.TreeMap<>();
        List<Entry> out = Main.retire(List.of(open, closedRecently, closedLongAgo, onYoungFailure), failures, "2026-10-06T00:00:00Z", log);

        assertEquals("2026-09-28T00:00:00Z", find(out, "1").closed(), "closed from the first failure, 7+ days ago");
        assertNull(find(out, "4").closed(), "failing 5 days: still open");
        assertEquals("2026-09-30T00:00:00Z", find(out, "2").closed(), "closed 6 days ago: kept, so a return shows as reopened");
        assertTrue(out.stream().noneMatch(e -> e.posting().id().equals("3")), "closed 16 days ago: dropped");
        assertEquals(1, log.get("droppedClosed"));
        assertEquals(Map.of("lever:b (IOException: HTTP 404)", 1), log.get("closedOnDeadBoards"));
    }
}
