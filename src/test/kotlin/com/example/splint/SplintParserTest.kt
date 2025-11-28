package com.example.splint

import org.junit.Assert.assertEquals
import org.junit.Test

class SplintParserTest() {

    @Test
    fun `test parsing specific splint json output`() {

        // This is the exact JSON you provided
        val jsonOutput = """
            {"alt":"\"w-1/3 text-right mt-1 pr-1\"","column":28,"end-column":62,"end-line":132,"exception":null,"filename":"src/main/my_app/screen.cljs","form":"(str \"w-1/3 text-right mt-1 pr-1\")","line":132,"message":"Use the literal directly.","rule-name":"lint/redundant-str-call"}
            {"alt":"(zero? precise-quantity)","column":27,"end-column":49,"end-line":180,"exception":null,"filename":"src/main/my_app/screen.cljs","form":"(= precise-quantity 0)","line":180,"message":"Use `zero?` instead of recreating it.","rule-name":"style/eq-zero"}
        """.trimIndent()

        val issues = parseSplintJsonOutput(jsonOutput)

        // 1. Check we found issues
        assertEquals("Should find 2 issues", 2, issues.size)

        // 2. Validate first issue
        val issue1 = issues[0]
        assertEquals(132, issue1.line)
        assertEquals(28, issue1.column)
        assertEquals("lint/redundant-str-call", issue1.rule)
        assertEquals("Use the literal directly.", issue1.message)
        assertEquals("\"w-1/3 text-right mt-1 pr-1\"", issue1.alt)

        // 3. Validate second issue
        val issue2 = issues[1]
        assertEquals(180, issue2.line)
        assertEquals("style/eq-zero", issue2.rule)

        println("? Parser works! Found ${issues.size} issues correctly.")
    }
}