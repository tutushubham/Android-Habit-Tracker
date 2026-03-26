package com.tutushubham.pokidex.core.engine

/**
 * Human-readable explanation attached to any intelligence output.
 *
 * [title] is a short label (e.g. "High Priority").
 * [description] is a sentence explaining why (e.g. "You are behind by 2.4 units/day").
 * [factors] lists the contributing signals for transparency.
 */
data class InsightExplanation(
    val title: String,
    val description: String,
    val factors: List<String>
)
