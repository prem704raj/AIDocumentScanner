package com.example.aidocumentscanner.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageSpecParserTest {

    @Test
    fun selection_expandsRangesAndDeduplicates() {
        val result =
            PageSpecParser
                .parseSelection(
                    "1,3,5-8,3",
                    totalPages = 10
                )
                .getOrThrow()

        assertEquals(
            listOf(
                1, 3, 5, 6, 7, 8
            ),
            result
        )
    }

    @Test
    fun selection_rejectsOutOfRangePage() {
        val result =
            PageSpecParser
                .parseSelection(
                    "1,11",
                    totalPages = 10
                )

        assertTrue(result.isFailure)
    }

    @Test
    fun split_rejectsOverlappingGroups() {
        val result =
            PageSpecParser
                .parseSplitGroups(
                    "1-4;4-8",
                    totalPages = 8
                )

        assertTrue(result.isFailure)
    }

    @Test
    fun order_requiresEveryPageExactlyOnce() {
        assertTrue(
            PageSpecParser
                .parseOrder(
                    "3,1,4,2",
                    4
                )
                .isSuccess
        )

        assertTrue(
            PageSpecParser
                .parseOrder(
                    "1,1,3,4",
                    4
                )
                .isFailure
        )
    }

    @Test
    fun hugeSelection_remainsDeterministic() {
        val result =
            PageSpecParser
                .parseSelection(
                    "1-1000",
                    1000
                )
                .getOrThrow()

        assertEquals(1000, result.size)
        assertEquals(1, result.first())
        assertEquals(1000, result.last())
    }
}
