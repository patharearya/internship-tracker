package io.github.patharearya.internships;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.patharearya.internships.Discover.Board;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** URL shapes copied from real Simplify listings.json rows (2026-10-04). */
class DiscoverTest {

    @Test
    void boardFromEachUrlShape() {
        Board gh = Discover.board("https://job-boards.greenhouse.io/samsungresearchamericainternship/jobs/8416982002");
        assertEquals("greenhouse", gh.system());
        assertEquals("samsungresearchamericainternship", gh.key());
        assertEquals("https://boards-api.greenhouse.io/v1/boards/samsungresearchamericainternship/jobs?content=true", gh.api());

        assertEquals("https://boards-api.greenhouse.io/v1/boards/imc/jobs?content=true",
                Discover.board("https://job-boards.eu.greenhouse.io/imc/jobs/4823945101").api(), "EU boards use the main API host");
        assertEquals("https://api.ashbyhq.com/posting-api/job-board/Hippocratic%20AI",
                Discover.board("https://jobs.ashbyhq.com/Hippocratic AI/0b1c/application").api(), "spaces encoded, case kept");
        assertEquals("acme", Discover.board("https://boards.greenhouse.io/embed/job_app?for=acme&token=123").key());

        Board lv = Discover.board("https://jobs.lever.co/Xpansiv%20/8a1649ec-ef5f-425d-8a36-34f28d67e8a7/apply");
        assertEquals("xpansiv", lv.key(), "decoded and trimmed");
        assertEquals("https://api.lever.co/v0/postings/xpansiv?mode=json", lv.api());

        Board as = Discover.board("https://jobs.ashbyhq.com/persona.ai/ed9a7425-9798-471e-b46a-fefd59570630/application?embed=true");
        assertEquals("persona.ai", as.key());
        assertEquals("https://api.ashbyhq.com/posting-api/job-board/persona.ai", as.api());

        Board wd = Discover.board("https://mfs.wd1.myworkdayjobs.com/en-US/MFS-Careers/job/Boston/Summer-2027-Software-Engineer-Intern_MFS-231984");
        assertEquals("mfs.wd1.myworkdayjobs.com/MFS-Careers", wd.key(), "language segment skipped");
        assertEquals("https://mfs.wd1.myworkdayjobs.com/wday/cxs/mfs/MFS-Careers", wd.api());

        // shapes behind the HTTP 404/422 boards of 2026-10-05
        assertEquals("intel.wd1.myworkdayjobs.com/External",
                Discover.board("https://intel.wd1.myworkdayjobs.com/en-us/External/job/Santa-Clara/Intern_JR0271234").key(), "lower-case language");
        assertEquals("livenation.wd503.myworkdayjobs.com/TMExternalSite",
                Discover.board("https://livenation.wd503.myworkdayjobs.com/en/TMExternalSite/job/x_JR-1").key(), "language without region");
        assertEquals("kla.wd1.myworkdayjobs.com/UR",
                Discover.board("https://kla.wd1.myworkdayjobs.com/UR/job/Milpitas-CA/Intern_2641772-1").key(), "a two-letter site is not a language");
        assertEquals("jj.wd5.myworkdayjobs.com/JJ",
                Discover.board("https://jj.wd5.myworkdayjobs.com/JJ/details/Intern_R-012345").key(), "nor before details");
        assertEquals("https://osv-chegg.wd5.myworkdayjobs.com/wday/cxs/osv_chegg/Chegg",
                Discover.board("https://osv-chegg.wd5.myworkdayjobs.com/Chegg/job/x_R1").api(), "API tenant uses _ for -");
        Board moved = Discover.board("https://takeda.wd3.myworkdayjobs.com/external/job/x_R1");
        assertEquals("takeda.wd502.myworkdayjobs.com/external", moved.key());
        assertEquals("https://takeda.wd502.myworkdayjobs.com/wday/cxs/takeda/external", moved.api());

        assertNull(Discover.board("https://www.tesla.com/careers/search/job/123"));
        assertNull(Discover.board("not a url ::"));
    }

    @Test
    void aDroppedBoardStaysWhileItHasOpenPostings() {
        Board sereact = new Board("ashby", "sereact", "Sereact", "https://api.ashbyhq.com/posting-api/job-board/sereact", "simplify");
        Board dead = new Board("ashby", "gone", "Gone", "https://api.ashbyhq.com/posting-api/job-board/gone", "simplify");
        Board fresh = new Board("lever", "acme", "Acme", "https://api.lever.co/v0/postings/acme?mode=json", "simplify");
        Posting p = new Posting("ashby", "sereact", "1", "Intern", "Sereact", "https://x", List.of(), null, null, null, null, null, null, null, null);
        Main.Entry open = new Main.Entry("ashby:sereact:1", p, null, "T0", "T0", 0, null, null, false);
        Posting q = new Posting("ashby", "gone", "1", "Intern", "Gone", "https://x", List.of(), null, null, null, null, null, null, null, null);
        Main.Entry closed = new Main.Entry("ashby:gone:1", q, null, "T0", "T0", 3, "T1", "T1", false);
        List<Board> out = Main.keepBoardsWithOpenPostings(List.of(fresh), List.of(sereact, dead), List.of(open, closed));
        assertEquals(List.of("sereact", "acme"), out.stream().map(Board::key).toList(), "sorted by system, then key; the dead board drops");
    }

    @Test
    void oneBoardPerKeyIgnoringCaseWithMostCommonCompanyName() throws Exception {
        var rows = new ObjectMapper().readTree("""
                [{"company_name":"Abbott","url":"https://abbott.wd5.myworkdayjobs.com/AbbottCareers/job/a_1"},
                 {"company_name":"Abbott Labs","url":"https://abbott.wd5.myworkdayjobs.com/abbottcareers/job/b_2"},
                 {"company_name":"Abbott","url":"https://abbott.wd5.myworkdayjobs.com/en-US/abbottcareers/job/c_3"},
                 {"company_name":"Acme","url":"https://jobs.lever.co/acme/x"},
                 {"company_name":"Tesla","url":"https://www.tesla.com/careers/1"}]""");
        List<Board> boards = Discover.fromListings(rows).stream().filter(b -> !b.from().equals("hand")).toList();
        assertEquals(2, boards.size());
        assertTrue(Discover.fromListings(rows).containsAll(Discover.HAND), "hand-added boards are always listed");
        Board wd = boards.stream().filter(b -> b.system().equals("workday")).findFirst().orElseThrow();
        assertEquals("abbott.wd5.myworkdayjobs.com/abbottcareers", wd.key());
        assertEquals("Abbott", wd.company());
    }
}
