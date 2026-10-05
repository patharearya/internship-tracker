package io.github.patharearya.internships;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {

    static Posting p(String source, String title, List<String> locations, String category, String workplace, String description) {
        return new Posting(source, "b", "1", title, "Org", "https://x", locations, null, category, workplace, null, null, null, null, description);
    }

    static Posting job(String title) { return p("greenhouse", title, List.of(), null, null, null); }

    @Test
    void internshipFilterKeeps() {
        for (String t : List.of("Software Engineer Intern", "Product Manager Intern", "Summer Analyst - Investment Banking",
                "Co-op, Mechanical Engineering", "Data Science Internship (Summer 2027)", "Student Trainee (Accounting)",
                "Apprentice Electrician", "Graduate Intern - PhD", "Marketing Interns", "Student Volunteer",
                // from the labelled sample (2026-10-04)
                "Engineering/Manufacturing Co-op_Spring 2027", "Thermal Associate Engineer (Summer 2027)",
                "Payments, Alternate Solutions Group, Summer 2027 Analyst", "Early Career Intern - ETF Product",
                "Product Manager, Intern", "Junior IWMS Project Manager - Intern"))
            assertNull(Rules.reject(job(t)), t);
    }

    @Test
    void internshipFilterRejectsWithReason() {
        assertEquals("no internship keyword in title", Rules.reject(job("Internal Audit Analyst")));
        assertEquals("no internship keyword in title", Rules.reject(job("International Sales Manager")));
        assertEquals("no internship keyword in title", Rules.reject(job("Senior Software Engineer")));
        assertTrue(Rules.reject(job("Internship Program Manager")).startsWith("runs the programme"));
        assertTrue(Rules.reject(job("Recruiter, Early Talent & Interns")).startsWith("runs the programme"));
        assertTrue(Rules.reject(job("Postdoctoral Fellow, Genomics")).startsWith("not a student role"));
        assertTrue(Rules.reject(job("New Grad Software Engineer (Intern Conversion)")).startsWith("not a student role"));
        assertTrue(Rules.reject(job("Coordinator, Internship Programs")).startsWith("runs the programme"));
        assertTrue(Rules.reject(job("2027 Early Career Program - Associate Underwriter")).startsWith("no internship keyword"));
        assertTrue(Rules.reject(job("Associate Product Manager (starting summer 2027)")).startsWith("no internship keyword"));
        assertTrue(Rules.reject(job("Materials Engineer (New Grad Summer 2027)")).startsWith("not a student role"));
    }

    @Test
    void usajobsInternalOnlyIsRejected() {
        Posting internal = new Posting("usajobs", "usajobs", "1", "Management Intern", "Org", "https://x", List.of(), null,
                "0301", null, null, "fed-internal-search", null, null, null);
        assertTrue(Rules.reject(internal).startsWith("not open to students"));
        Posting open = new Posting("usajobs", "usajobs", "1", "Management Intern", "Org", "https://x", List.of(), null,
                "0301", null, null, "student,public", null, null, null);
        assertNull(Rules.reject(open));
    }

    @Test
    void outsideUsIsRejectedButUnknownIsKept() {
        assertEquals("outside US", Rules.reject(p("lever", "Software Intern", List.of("Toronto, ON"), null, null, null)));
        assertEquals("outside US", Rules.reject(p("ashby", "Software Intern", List.of("Remote - Canada"), null, null, null)));
        assertNull(Rules.reject(p("lever", "Software Intern", List.of("Indianapolis"), null, null, null)), "india inside Indianapolis");
        assertNull(Rules.reject(p("lever", "Software Intern", List.of("Remote (United States | Canada)"), null, null, null)));
        assertNull(Rules.reject(p("lever", "Software Intern", List.of("Toronto, ON", "Austin, TX"), null, null, null)), "one US location is enough");
        Posting gb = new Posting("lever", "b", "1", "Software Intern", "Org", "https://x", List.of("London"), "GB", null, null, null, null, null, null, null);
        assertEquals("outside US", Rules.reject(gb));
    }

    @Test
    void researchProgrammesSkipTheTitleFilter() {
        Posting r = new Posting("nsf", "nsf-reu", "1", "REU Site: Scaffolds Across Length Scales", "U", "https://x",
                List.of("BOSTON, MA"), "US", "ENG", null, Parse.RESEARCH, null, null, null, null);
        assertNull(Rules.reject(r));
        assertEquals("research programme", Rules.label(r).type());
    }

    @Test
    void majorByTitleThenCategoryThenOther() {
        String[][] cases = {
                {"Software Engineer Intern", "Computer Science & IT"},
                {"Data Science Intern", "Data & Mathematics"},
                {"Mechanical Engineering Co-op", "Engineering"},
                {"Design Engineer Intern", "Engineering"},
                {"Audit Intern", "Finance & Accounting"},
                {"Account Manager Intern", "Business & Management"},
                {"Marketing Intern", "Marketing & Communications"},
                {"UX Design Intern", "Arts, Design & Media"},
                {"Clinical Research Intern", "Life Sciences & Health"},
                {"Chemistry Intern", "Physical Sciences"},
                {"Psychology Research Intern", "Social Sciences & Psychology"},
                {"Legal Intern", "Government, Law & Policy"},
                {"Teaching Intern", "Education"},
                {"Operations Intern", "Business & Management"},
        };
        for (String[] c : cases) assertEquals(c[1], Rules.major(job(c[0])).value(), c[0]);
        assertEquals("Finance & Accounting", Rules.major(p("greenhouse", "Summer Intern", List.of(), "Finance", null, null)).value());
        assertEquals("Other", Rules.major(job("Summer Intern")).value());
        assertEquals("no rule matched", Rules.major(job("Summer Intern")).why());
    }

    @Test
    void majorFromSourceCodes() {
        assertEquals("Finance & Accounting", Rules.major(p("usajobs", "Student Trainee", List.of(), "0599", null, null)).value());
        assertEquals("Computer Science & IT", Rules.major(p("usajobs", "Student Trainee", List.of(), "2299", null, null)).value());
        assertEquals("Computer Science & IT", Rules.major(p("usajobs", "Student Trainee", List.of(), "1550", null, null)).value());
        assertEquals("Data & Mathematics", Rules.major(p("usajobs", "Student Trainee", List.of(), "1599", null, null)).value());
        assertEquals("Other", Rules.major(p("usajobs", "Student Volunteer", List.of(), "0099", null, null)).value());
        assertEquals("Engineering", Rules.major(p("nsf", "REU Site: Scaffolds Across Length Scales", List.of(), "ENG", null, null)).value());
        assertEquals("Life Sciences & Health", Rules.major(p("nih", "Summer Undergraduate Research Experience (SURE)", List.of(), "NIH", null, null)).value());
        assertEquals("Data & Mathematics", Rules.major(p("nsf", "REU Site: Applied and Computational Mathematics", List.of(), "MPS", null, null)).value(),
                "title beats directorate");
        // every directorate the API returned on 2026-10-05; a misplaced comment once dropped ENG and MPS silently (devlog)
        Map<String, String> dirs = Map.of("CSE", "Computer Science & IT", "ENG", "Engineering", "MPS", "Physical Sciences",
                "BIO", "Life Sciences & Health", "GEO", "Physical Sciences", "SBE", "Social Sciences & Psychology", "EDU", "Education");
        dirs.forEach((d, major) -> assertEquals(major, Rules.major(p("nsf", "REU Site: Scaffolds Across Length Scales", List.of(), d, null, null)).value(), d));
    }

    @Test
    void genericWordsOnlyWhenNothingElseNamesAField() {
        assertEquals("Engineering", Rules.major(job("Quality Intern")).value());
        assertEquals("Physical Sciences", Rules.major(job("Air Quality Intern")).value());
        assertEquals("Life Sciences & Health", Rules.major(job("Structural Biology Research Intern")).value());
        assertEquals("Life Sciences & Health", Rules.major(p("nsf", "REU Site: Quality of Life", List.of(), "BIO", null, null)).value(),
                "directorate beats a generic word");
        assertEquals("Physical Sciences", Rules.major(job("Quantum Research Intern")).value(), "not Finance's \"quant\"");
        assertEquals("Finance & Accounting", Rules.major(job("Quantitative Intern")).value());
        assertNotNull(Rules.reject(job("Avionics Internship - SkillBridge")), "SkillBridge is for service members only");
    }

    @Test
    void level() {
        assertEquals("grad", Rules.level(job("PhD Research Intern")).value());
        assertEquals("grad", Rules.level(p("x", "Intern", List.of(), null, null, "Currently pursuing a Master’s degree")).value(), "curly apostrophe");
        assertEquals("both", Rules.level(p("x", "Intern", List.of(), null, null, "pursuing a Bachelor's or Master's degree")).value());
        assertEquals("undergrad", Rules.level(p("x", "Intern", List.of(), null, null, "Open to rising juniors and seniors")).value());
        assertEquals("unknown", Rules.level(p("x", "Intern", List.of(), null, null, "Must be graduating in 2027")).value(),
                "bare graduating is not grad");
        assertEquals("unknown", Rules.level(job("Software Engineering Intern")).value());
    }

    @Test
    void arrangement() {
        assertEquals("hybrid", Rules.arrangement(p("ashby", "Intern", List.of(), null, "Hybrid", null)).value());
        assertEquals("in-person", Rules.arrangement(p("ashby", "Intern", List.of(), null, "OnSite", null)).value());
        assertEquals("hybrid", Rules.arrangement(p("usajobs", "Intern", List.of(), null, "Telework eligible", null)).value());
        assertEquals("remote", Rules.arrangement(p("lever", "Intern", List.of("Remote - US"), null, "unspecified", null)).value());
        assertEquals("remote", Rules.arrangement(p("x", "Intern", List.of("Austin, TX"), null, null, "This role is fully remote.")).value());
        assertEquals("not stated", Rules.arrangement(p("x", "Intern", List.of("Raleigh, NC"), null, null, "See our remote work policy.")).value(),
                "boilerplate does not count; a bare city is not in-person");
    }

    @Test
    void states() {
        assertEquals(List.of("NC"), List.copyOf(Rules.statesIn("Raleigh, NC")));
        assertEquals(List.of("IL"), List.copyOf(Rules.statesIn("United States - Illinois - Waukegan")));
        assertEquals(List.of("MA"), List.copyOf(Rules.statesIn("US-MA-Boston")));
        assertEquals(List.of("WV"), List.copyOf(Rules.statesIn("Morgantown, West Virginia")));
        assertEquals(List.of("DC"), List.copyOf(Rules.statesIn("Washington, District of Columbia")));
        assertEquals(List.of("DC"), List.copyOf(Rules.statesIn("Washington, DC")));
        assertEquals(List.of("WA"), List.copyOf(Rules.statesIn("Seattle, Washington")));
        assertEquals(List.of("OR"), List.copyOf(Rules.statesIn("Portland, OR")));
        assertEquals(List.of("IA"), List.copyOf(Rules.statesIn("IOWA CITY, IA")));
        assertEquals(List.of(), List.copyOf(Rules.statesIn("IT Department, Remote")));
        var w = Rules.where(p("lever", "Intern", List.of("Remote (United States | Canada)", "Raleigh, NC"), null, null, null));
        assertEquals(List.of("NC"), w.states());
        assertTrue(w.remoteUs());
    }
}
