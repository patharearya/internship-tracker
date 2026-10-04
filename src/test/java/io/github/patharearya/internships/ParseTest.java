package io.github.patharearya.internships;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Replays raw responses saved by the spike (2026-10-03/04). Expected values were read from the files with Python, not from this code. */
class ParseTest {

    static String raw(String name) {
        try (var in = ParseTest.class.getResourceAsStream("/raw/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void greenhouse() {
        List<Posting> ps = Parse.greenhouse("AccuWeather", "accuweather", raw("greenhouse-accuweather.json"));
        assertEquals(16, ps.size());
        Posting p = ps.stream().filter(x -> x.id().equals("8189209")).findFirst().orElseThrow();
        assertEquals("Creative Writer Intern: Forecasting and Communications", p.title());
        assertEquals(List.of("State College, PA"), p.locations());
        assertEquals("Forecasting", p.category());
        assertEquals("2026-09-09", p.posted());
        assertEquals("https://job-boards.greenhouse.io/accuweather/jobs/8189209", p.url());
        for (Posting x : ps) {   // double-escaped HTML fully reduced to text
            assertFalse(x.description().contains("<") || x.description().contains("&lt;") || x.description().contains("&amp;"), x.id());
        }
    }

    @Test
    void lever() {
        List<Posting> ps = Parse.lever("Aledade", "aledade", raw("lever-aledade.json"));
        assertEquals(32, ps.size());
        Posting p = ps.get(0);
        assertEquals("Account Manager, California/Washington", p.title());
        assertEquals(5, p.locations().size());
        assertEquals("Placerville, CA", p.locations().get(0));
        assertEquals("US", p.country());
        assertEquals("Provider Networks", p.category());
        assertEquals("remote", p.workplace());
        assertEquals("Full Time", p.employment());
        assertEquals("2026-09-25", p.posted());
        assertTrue(p.description().contains("Minimum Qualifications:"), "lists section included");
        assertFalse(p.description().contains("null"));
    }

    @Test
    void ashby() {
        List<Posting> ps = Parse.ashby("1Password", "1password", raw("ashby-1password.json"));
        assertEquals(69, ps.size());
        Posting p = ps.get(0);
        assertEquals("Senior GTM Engineering Analyst", p.title());
        assertEquals(List.of("Remote (United States | Canada)"), p.locations());
        assertEquals("GTM", p.category());
        assertEquals("Remote", p.workplace());
        assertEquals("FullTime", p.employment());
        assertEquals("2026-09-30", p.posted());
    }

    @Test
    void workday() {
        List<String> paths = Parse.workdayPaths(raw("workday-abbott-list.json"));
        assertEquals(20, paths.size());
        assertTrue(paths.stream().allMatch(x -> x.startsWith("/job/")));
        Posting p = Parse.workday("Abbott", "abbott.wd5.myworkdayjobs.com/abbottcareers", raw("workday-abbott-detail.json"));
        assertEquals("31159432", p.id());
        assertEquals("2027 IT Intern", p.title());
        assertEquals(List.of("United States - Illinois - Waukegan"), p.locations());
        assertEquals("United States of America", p.country());
        assertEquals("2026-08-17", p.posted());
        assertEquals("2026-11-01", p.deadline());
        assertFalse(p.description().contains("<"));
    }

    @Test
    void usajobs() {
        List<Posting> ps = Parse.usajobs(raw("usajobs-keyword.json"));
        assertEquals(25, ps.size());
        Posting p = ps.get(0);
        assertEquals("886197300", p.id());
        assertEquals("Data Analyst Student Intern (Student Volunteer) (Spring 2027)", p.title());
        assertEquals("0099", p.category());
        assertEquals("student", p.eligibility());
        assertEquals("2026-09-24", p.posted());
        assertEquals("2026-10-16", p.deadline());
        assertEquals("United States", p.country());
        assertNull(p.workplace());
    }

    @Test
    void nsfUsesProgrammeLinkElseAwardPage() {
        List<Posting> ps = Parse.nsf(raw("nsf-reu.json"));
        assertEquals(25, ps.size());
        Posting p = ps.get(0);
        assertEquals("2548170", p.id());
        assertEquals("https://microbiology.medicine.uiowa.edu/undergraduate-education/research-opportunities/summer-undergraduate-research", p.url());
        assertEquals(List.of("IOWA CITY, IA"), p.locations());
        assertEquals("BIO", p.category());
        assertEquals("2026-06-22", p.posted());
        // 3 of 25 abstracts link a programme page; the other 22, including the 2 that only name etap.nsf.gov, fall back
        assertEquals(22, ps.stream().filter(x -> x.url().startsWith("https://www.nsf.gov/awardsearch/showAward?AWD_ID=")).count());
        assertTrue(ps.stream().noneMatch(x -> x.url().contains("etap.nsf.gov")));
    }

    @Test
    void nih() {
        List<Posting> ps = Parse.nih(raw("nih-r25-undergrad.json"));
        assertEquals(25, ps.size());
        Posting p = ps.get(0);
        assertEquals("11326326", p.id());
        assertEquals("Summer Undergraduate Research Internship Program", p.title());
        assertEquals(List.of("FORT WORTH, TX"), p.locations());
        assertEquals("https://reporter.nih.gov/project-details/11326326", p.url());
    }

    @Test
    void everyPostingHasIdentityTitleAndLink() {
        List<Posting> all = new ArrayList<>();
        all.addAll(Parse.greenhouse("AccuWeather", "accuweather", raw("greenhouse-accuweather.json")));
        all.addAll(Parse.lever("Aledade", "aledade", raw("lever-aledade.json")));
        all.addAll(Parse.ashby("1Password", "1password", raw("ashby-1password.json")));
        all.add(Parse.workday("Abbott", "abbott.wd5.myworkdayjobs.com/abbottcareers", raw("workday-abbott-detail.json")));
        all.addAll(Parse.usajobs(raw("usajobs-keyword.json")));
        all.addAll(Parse.nsf(raw("nsf-reu.json")));
        all.addAll(Parse.nih(raw("nih-r25-undergrad.json")));
        assertEquals(16 + 32 + 69 + 1 + 25 + 25 + 25, all.size());
        for (Posting p : all) {
            assertNotNull(p.id(), p.toString());
            assertNotNull(p.title(), p.toString());
            assertTrue(p.url() != null && p.url().startsWith("http"), p.toString());
            assertFalse(p.locations().isEmpty(), p.source() + " " + p.id() + " has no location");
        }
    }

    @Test
    void htmlToText() {
        assertEquals("a & b\nc <d>", Parse.text("<p>a &amp; b</p><p>c &lt;d&gt;</p>"));
        assertEquals("x\ny", Parse.text(Parse.unescape("&lt;p&gt;x&lt;/p&gt;&lt;br/&gt;y")));
        assertEquals("é'", Parse.unescape("&#233;&#x27;"));
    }
}
