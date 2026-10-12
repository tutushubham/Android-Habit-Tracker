package com.habitsheet.presentation.onboarding

import com.habitsheet.domain.plan.PlanLabel
import com.habitsheet.domain.plan.PlanText
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.StringResource

/** The catalog entry for each [PlanText]. Exhaustive: a new key without wording does not compile. */
@Suppress("CyclomaticComplexMethod") // a lookup table: one branch per key, nothing to simplify
internal fun PlanText.resource(): StringResource = when (this) {
    PlanText.HABIT_RUNNING -> Res.string.plan_text_habit_running
    PlanText.HABIT_STRENGTH -> Res.string.plan_text_habit_strength
    PlanText.HABIT_STRETCH_MIN -> Res.string.plan_text_habit_stretch_min
    PlanText.HABIT_SLEEP_HOURS -> Res.string.plan_text_habit_sleep_hours
    PlanText.HABIT_HYROX_TRAINING -> Res.string.plan_text_habit_hyrox_training
    PlanText.HABIT_MOBILITY_MIN -> Res.string.plan_text_habit_mobility_min
    PlanText.HABIT_CODING -> Res.string.plan_text_habit_coding
    PlanText.HABIT_SYSTEM_DESIGN -> Res.string.plan_text_habit_system_design
    PlanText.HABIT_BEHAVIOURAL -> Res.string.plan_text_habit_behavioural
    PlanText.HABIT_MOCK_INTERVIEW -> Res.string.plan_text_habit_mock_interview
    PlanText.HABIT_REVIEW_NOTES_MIN -> Res.string.plan_text_habit_review_notes_min
    PlanText.HABIT_WORKOUT -> Res.string.plan_text_habit_workout
    PlanText.HABIT_STEPS -> Res.string.plan_text_habit_steps
    PlanText.HABIT_WATER_LITRES -> Res.string.plan_text_habit_water_litres
    PlanText.RUN_EASY_KM -> Res.string.plan_text_run_easy_km
    PlanText.RUN_LONG_KM -> Res.string.plan_text_run_long_km
    PlanText.RUN_INTERVALS_800 -> Res.string.plan_text_run_intervals_800
    PlanText.RUN_INTERVALS_1K -> Res.string.plan_text_run_intervals_1k
    PlanText.STRENGTH_CORE_MIN -> Res.string.plan_text_strength_core_min
    PlanText.STRENGTH_FULL_BODY_MIN -> Res.string.plan_text_strength_full_body_min
    PlanText.CARDIO_MIN -> Res.string.plan_text_cardio_min
    PlanText.ACTIVE_OUTDOORS_MIN -> Res.string.plan_text_active_outdoors_min
    PlanText.HYROX_STATIONS -> Res.string.plan_text_hyrox_stations
    PlanText.HYROX_COMPROMISED_RUN -> Res.string.plan_text_hyrox_compromised_run
    PlanText.RACE_MARATHON -> Res.string.plan_text_race_marathon
    PlanText.RACE_HYROX -> Res.string.plan_text_race_hyrox
    PlanText.CODING_ARRAYS_STRINGS -> Res.string.plan_text_coding_arrays_strings
    PlanText.CODING_HASHING -> Res.string.plan_text_coding_hashing
    PlanText.CODING_LINKED_LISTS -> Res.string.plan_text_coding_linked_lists
    PlanText.CODING_STACKS_QUEUES -> Res.string.plan_text_coding_stacks_queues
    PlanText.CODING_TREES -> Res.string.plan_text_coding_trees
    PlanText.CODING_GRAPHS -> Res.string.plan_text_coding_graphs
    PlanText.CODING_BINARY_SEARCH -> Res.string.plan_text_coding_binary_search
    PlanText.CODING_DYNAMIC_PROGRAMMING -> Res.string.plan_text_coding_dynamic_programming
    PlanText.DESIGN_URL_SHORTENER -> Res.string.plan_text_design_url_shortener
    PlanText.DESIGN_RATE_LIMITER -> Res.string.plan_text_design_rate_limiter
    PlanText.DESIGN_CHAT -> Res.string.plan_text_design_chat
    PlanText.DESIGN_NEWS_FEED -> Res.string.plan_text_design_news_feed
    PlanText.DESIGN_CACHE -> Res.string.plan_text_design_cache
    PlanText.BEHAVIOURAL_INTRO -> Res.string.plan_text_behavioural_intro
    PlanText.BEHAVIOURAL_CONFLICT -> Res.string.plan_text_behavioural_conflict
    PlanText.BEHAVIOURAL_FAILURE -> Res.string.plan_text_behavioural_failure
    PlanText.BEHAVIOURAL_LEADERSHIP -> Res.string.plan_text_behavioural_leadership
    PlanText.BEHAVIOURAL_QUESTIONS_TO_ASK -> Res.string.plan_text_behavioural_questions_to_ask
    PlanText.MOCK_INTERVIEW_MIN -> Res.string.plan_text_mock_interview_min
    PlanText.INTERVIEW_DAY -> Res.string.plan_text_interview_day
    PlanText.CATEGORY_FITNESS -> Res.string.plan_text_category_fitness
    PlanText.CATEGORY_HEALTH -> Res.string.plan_text_category_health
    PlanText.CATEGORY_SLEEP -> Res.string.plan_text_category_sleep
    PlanText.CATEGORY_CAREER -> Res.string.plan_text_category_career
    PlanText.CATEGORY_STUDY -> Res.string.plan_text_category_study
}

/** A plan label as text for the person: rendered in the UI, resolved once when the plan is applied (ADR 0006). */
fun PlanLabel.toUiText(): UiText = UiText.Res(text.resource(), args)
