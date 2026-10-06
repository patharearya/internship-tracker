package io.github.patharearya.internships;

import io.github.patharearya.internships.Discover.Board;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/** A 403 pauses only the server that sent it (grill Q22): the first full run lost 1,090 Workday boards to one tenant. */
class PauseTest {

    static Board wd(String tenant, String site) {
        return new Board("workday", tenant + ".wd1.myworkdayjobs.com/" + site, tenant,
                "https://" + tenant + ".wd1.myworkdayjobs.com/wday/cxs/" + tenant + "/" + site, "test");
    }

    @Test
    void refusalPausesOnlyThatServer() throws Exception {
        Fetch fake = new Fetch("test@example.com", "") {
            @Override
            public Result board(Board b, Map<String, Posting> known) throws Refused {
                if (b.api().contains("amplify")) throw new Refused("amplify.wd1.myworkdayjobs.com", "HTTP 403 from amplify.wd1.myworkdayjobs.com");
                return new Result(List.of(), 0);
            }
        };
        List<Board> boards = List.of(wd("amplify", "careers"), wd("amplify", "campus"), wd("abbott", "abbottcareers"));
        Main.State state = Main.State.empty();
        List<Main.Unit> units = Main.system(fake, "workday", boards, Map.of(), state, "T1");

        assertEquals(List.of("refused", "skipped", "ok"), units.stream().map(Main.Unit::status).toList());
        assertEquals("amplify.wd1.myworkdayjobs.com", units.get(0).host());
        assertTrue(units.get(0).detail().matches("\\d{4}-\\d\\d-\\d\\dT\\S+Z HTTP 403 .*"), "pause stamped when the 403 came, not with the run start T1");

        // a pause saved from an earlier run is also per server
        Main.State saved = new Main.State(new TreeMap<>(), new TreeMap<>(), new TreeMap<>(Map.of("amplify.wd1.myworkdayjobs.com", "T0 HTTP 403")), null);
        units = Main.system(fake, "workday", boards, Map.of(), saved, "T1");
        assertEquals(List.of("skipped", "skipped", "ok"), units.stream().map(Main.Unit::status).toList());
    }

    @Test
    void aDropThatHoldsIsBelievedOnTheNextRun() throws Exception {
        // Raymond James early careers, 2026-10-05: 17 -> 2 for real; flagged on every run after, so 16 postings never closed
        Fetch two = new Fetch("test@example.com", "") {
            @Override
            public Result board(Board b, Map<String, Posting> known) { return new Result(List.of(), 2); }
        };
        List<Board> boards = List.of(wd("raymondjames", "raymondjamesearlycareers"));
        String id = "workday:" + boards.get(0).key();
        Main.State state = new Main.State(new TreeMap<>(Map.of(id, 17)), new TreeMap<>(), new TreeMap<>(), null);
        List<Main.Unit> run1 = Main.system(two, "workday", boards, Map.of(), state, "T1");
        assertEquals("anomaly", run1.get(0).status(), "one low reading may be a glitch");
        Main.record(state, run1, java.time.Instant.parse("2026-10-05T00:00:00Z"), "T1");
        assertEquals("ok", Main.system(two, "workday", boards, Map.of(), state, "T2").get(0).status(), "the same count again is real");
    }
}
