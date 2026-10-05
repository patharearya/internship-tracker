package io.github.patharearya.internships;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** rejections.tsv is how rejections are sampled and replayed, so it must carry what the rules read. */
class RejectionLogTest {

    @Test
    void rowCarriesWhatTheLocationAndEligibilityRulesRead() {
        Posting p = new Posting("usajobs", "usajobs", "123", "Management\tIntern", "Org", "https://x",
                List.of("Columbus, Ohio", "Remote"), "US", "0343", null, null, "fed-internal-search", null, null, null);
        String[] cols = Main.rejectionRow("usajobs", p, "not open to students").split("\t", -1);
        assertEquals(Main.REJECTION_HEADER.split("\t").length, cols.length, "a tab inside a field would shift every column");
        assertEquals("Management Intern", cols[2]);
        assertEquals("Columbus, Ohio | Remote", cols[4]);
        assertEquals("US", cols[5]);
        assertEquals("fed-internal-search", cols[6]);
    }

    @Test
    void workdaySearchResultsUseTheRequisitionIdOnlyWhenThePathConfirmsIt() {
        // shapes from the GE Appliances and Abbott boards (2026-10-05, src/test/resources/raw/workday-abbott-list.json)
        assertEquals("REQ-26320", Fetch.listId("REQ-26320", "/job/IND-Bangalore-KA/Intern--Dimensional-Management_REQ-26320"));
        assertEquals("31149049", Fetch.listId("31149049", "/job/Malaysia/IT-Intern_31149049-1"));
        assertEquals("R-00190764", Fetch.listId("R-00190764", "/job/Bethesda-MD/Cybersecurity-Engineer-Co-op_R-00190764"));
        // a tenant whose first bullet is something else keeps the path
        assertEquals("/job/x/Intern_R123", Fetch.listId("Posted Today", "/job/x/Intern_R123"));
        assertEquals("/job/x/Intern_R123", Fetch.listId("", "/job/x/Intern_R123"));
        assertEquals("/job/x/Intern_R1234", Fetch.listId("R123", "/job/x/Intern_R1234"), "a prefix of the id is not the id");
    }
}
