package io.github.patharearya.internships;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The page finds a description by computing the shard itself. Expected values come from the page's JS formula
 * ((31 * h + charCodeAt(i)) | 0, then a non-negative mod 64), so a change on either side shows up here.
 */
class ShardTest {

    @Test
    void javaShardMatchesThePageFormula() {
        assertEquals(55, Main.shard("greenhouse:10alabs:4567890005"));
        assertEquals(51, Main.shard("workday:abbott.wd5.myworkdayjobs.com/abbottcareers:/job/Chicago-IL/Intern_R1"));
        assertEquals(47, Main.shard("nsf:nsf-reu:2349035"));
        assertEquals(35, Main.shard("lever:café:é😀"));   // non-ASCII and a surrogate pair: both sides count UTF-16 units
    }
}
