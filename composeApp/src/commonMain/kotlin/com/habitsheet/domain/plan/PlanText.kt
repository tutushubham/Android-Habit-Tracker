package com.habitsheet.domain.plan

/**
 * Every piece of wording a template can produce, with the number of integer arguments it takes. The words themselves
 * are in the string catalog (`plan_text_*`), mapped by `presentation/onboarding/PlanTextResources.kt`.
 */
enum class PlanText(val argCount: Int = 0) {
    // Habit names
    HABIT_RUNNING,
    HABIT_STRENGTH,
    HABIT_STRETCH_MIN(1),
    HABIT_SLEEP_HOURS(1),
    HABIT_HYROX_TRAINING,
    HABIT_MOBILITY_MIN(1),
    HABIT_CODING,
    HABIT_SYSTEM_DESIGN,
    HABIT_BEHAVIOURAL,
    HABIT_MOCK_INTERVIEW,
    HABIT_REVIEW_NOTES_MIN(1),
    HABIT_WORKOUT,
    HABIT_STEPS(1),
    HABIT_WATER_LITRES(1),

    // Running and fitness sessions
    RUN_EASY_KM(1),
    RUN_LONG_KM(1),
    RUN_INTERVALS_800(1),
    RUN_INTERVALS_1K(1),
    STRENGTH_CORE_MIN(1),
    STRENGTH_FULL_BODY_MIN(1),
    CARDIO_MIN(1),
    ACTIVE_OUTDOORS_MIN(1),
    HYROX_STATIONS(1),
    HYROX_COMPROMISED_RUN(1),
    RACE_MARATHON,
    RACE_HYROX,

    // Interview sessions
    CODING_ARRAYS_STRINGS,
    CODING_HASHING,
    CODING_LINKED_LISTS,
    CODING_STACKS_QUEUES,
    CODING_TREES,
    CODING_GRAPHS,
    CODING_BINARY_SEARCH,
    CODING_DYNAMIC_PROGRAMMING,
    DESIGN_URL_SHORTENER,
    DESIGN_RATE_LIMITER,
    DESIGN_CHAT,
    DESIGN_NEWS_FEED,
    DESIGN_CACHE,
    BEHAVIOURAL_INTRO,
    BEHAVIOURAL_CONFLICT,
    BEHAVIOURAL_FAILURE,
    BEHAVIOURAL_LEADERSHIP,
    BEHAVIOURAL_QUESTIONS_TO_ASK,
    MOCK_INTERVIEW_MIN(1),
    INTERVIEW_DAY,

    // Category names, used only when the person no longer has the matching default category
    CATEGORY_FITNESS,
    CATEGORY_HEALTH,
    CATEGORY_SLEEP,
    CATEGORY_CAREER,
    CATEGORY_STUDY,
}
