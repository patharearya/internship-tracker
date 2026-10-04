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

        assertEquals("https://boards-api.eu.greenhouse.io/v1/boards/imc/jobs?content=true",
                Discover.board("https://job-boards.eu.greenhouse.io/imc/jobs/4823945101").api());
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

        assertNull(Discover.board("https://www.tesla.com/careers/search/job/123"));
        assertNull(Discover.board("not a url ::"));
    }

    @Test
    void oneBoardPerKeyIgnoringCaseWithMostCommonCompanyName() throws Exception {
        var rows = new ObjectMapper().readTree("""
                [{"company_name":"Abbott","url":"https://abbott.wd5.myworkdayjobs.com/AbbottCareers/job/a_1"},
                 {"company_name":"Abbott Labs","url":"https://abbott.wd5.myworkdayjobs.com/abbottcareers/job/b_2"},
                 {"company_name":"Abbott","url":"https://abbott.wd5.myworkdayjobs.com/en-US/abbottcareers/job/c_3"},
                 {"company_name":"Acme","url":"https://jobs.lever.co/acme/x"},
                 {"company_name":"Tesla","url":"https://www.tesla.com/careers/1"}]""");
        List<Board> boards = Discover.fromListings(rows);
        assertEquals(2, boards.size());
        Board wd = boards.stream().filter(b -> b.system().equals("workday")).findFirst().orElseThrow();
        assertEquals("abbott.wd5.myworkdayjobs.com/abbottcareers", wd.key());
        assertEquals("Abbott", wd.company());
    }
}
