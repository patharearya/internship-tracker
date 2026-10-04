package io.github.patharearya.internships;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Workday is due by time since its last run, not by clock hour: GitHub drops scheduled runs (devlog 2026-10-04). */
class WorkdayDueTest {

    @Test
    void dueByTimeSinceLastWorkdayRun() throws Exception {
        // the 2026-10-04 runs: last Workday at 15:12, then scheduled runs at 19:36 and 23:08 that skipped it
        assertTrue(Main.workdayDue("2026-10-04T15:12:06Z", Instant.parse("2026-10-04T19:36:15Z")));
        assertTrue(Main.workdayDue(null, Instant.parse("2026-10-04T19:36:15Z")));
        assertFalse(Main.workdayDue("2026-10-04T15:12:06Z", Instant.parse("2026-10-04T16:17:00Z")));
        // a run a few minutes short of 3 h still counts; one well short does not
        assertTrue(Main.workdayDue("2026-10-04T15:17:00Z", Instant.parse("2026-10-04T18:12:00Z")));
        assertFalse(Main.workdayDue("2026-10-04T15:17:00Z", Instant.parse("2026-10-04T17:17:00Z")));

        // state.json written before this field existed still loads, and then Workday is due
        Main.State old = Main.JSON.readValue("{\"lastCount\":{},\"failures\":{},\"paused\":{}}", Main.State.class);
        assertNull(old.workdayStarted());
    }
}
