package com.example.aidocumentscanner.pdf

/**
 * Pure parser for PDF page specifications used by the Phase-4 tools UI.
 *
 * Selection examples:
 *   1,3,5-8
 *
 * Split-group examples:
 *   1-3;4-6;7-10
 *
 * Reorder examples:
 *   3,1,2,4
 */
object PageSpecParser {

    fun parseSelection(spec: String, totalPages: Int): Result<List<Int>> = runCatching {
        require(totalPages > 0) { "PDF has no pages" }
        require(spec.isNotBlank()) { "Enter at least one page" }

        val result = linkedSetOf<Int>()
        spec.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { token ->
                if ("-" in token) {
                    val parts = token.split("-").map(String::trim)
                    require(parts.size == 2) { "Invalid range: $token" }
                    val start = parts[0].toIntOrNull()
                        ?: error("Invalid page: ${parts[0]}")
                    val end = parts[1].toIntOrNull()
                        ?: error("Invalid page: ${parts[1]}")
                    require(start <= end) { "Range must go forward: $token" }
                    require(start in 1..totalPages && end in 1..totalPages) {
                        "Pages must be between 1 and $totalPages"
                    }
                    for (page in start..end) result += page
                } else {
                    val page = token.toIntOrNull() ?: error("Invalid page: $token")
                    require(page in 1..totalPages) {
                        "Pages must be between 1 and $totalPages"
                    }
                    result += page
                }
            }

        require(result.isNotEmpty()) { "No valid pages entered" }
        result.toList()
    }

    fun parseSplitGroups(spec: String, totalPages: Int): Result<List<List<Int>>> = runCatching {
        require(spec.isNotBlank()) {
            "Enter ranges separated by semicolons, for example 1-3;4-6"
        }

        val groups = spec.split(";")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { group ->
                parseSelection(group, totalPages).getOrThrow()
            }

        require(groups.size >= 2) {
            "Enter at least two split groups, for example 1-3;4-6"
        }

        val flattened = groups.flatten()
        require(flattened.size == flattened.toSet().size) {
            "A page cannot appear in more than one split group"
        }

        groups
    }

    fun parseOrder(spec: String, totalPages: Int): Result<List<Int>> = runCatching {
        require(totalPages > 0) { "PDF has no pages" }
        require(spec.isNotBlank()) { "Enter the complete page order" }

        val order = spec.split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map {
                it.toIntOrNull() ?: error("Invalid page: $it")
            }

        require(order.size == totalPages) {
            "Enter all $totalPages pages exactly once"
        }
        require(order.toSet().size == totalPages) {
            "Each page must appear exactly once"
        }
        require(order.all { it in 1..totalPages }) {
            "Pages must be between 1 and $totalPages"
        }
        order
    }

    fun allPagesSpec(totalPages: Int): String =
        if (totalPages <= 0) "" else (1..totalPages).joinToString(",")
}
