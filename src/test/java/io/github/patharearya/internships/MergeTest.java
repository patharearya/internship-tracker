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
        List<Entry> out = Main.relabel(List.of(open, closed), Map.of("workday:b:1", "Open to undergraduate students."));
        assertEquals("Computer Science & IT", out.get(0).labels().major().value());
        assertEquals(List.of("CA"), out.get(0).labels().states());
        assertEquals("undergrad", out.get(0).labels().level().value(), "description from the shard files is used");
        assertSame(stale, out.get(1).labels(), "closed postings keep the labels they closed with");
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
}
