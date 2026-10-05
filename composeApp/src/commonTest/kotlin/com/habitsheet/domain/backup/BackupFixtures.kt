package com.habitsheet.domain.backup

/**
 * Frozen backup files, one per format version. Never regenerate these to make a test pass: they stand for files
 * users already hold. v1 has no plans/kind/planId, v2 adds weekly plans, v3 adds day plans, kinds and plan ids,
 * v4 adds settings and a SHA-256 checksum. Versions 1-3 describe the same habits so restores can be compared.
 */
object BackupFixtures {
    val V1 = """
{
  "version": 1,
  "timestamp": 1000,
  "data": {
    "categories": [
      {
        "id": "cat1",
        "name": "Fitness",
        "displayOrder": 0,
        "updatedAtEpochMillis": 100
      }
    ],
    "dailyHabits": [
      {
        "id": "run",
        "name": "Run",
        "categoryId": "cat1",
        "monthlyGoal": 12,
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200
      }
    ],
    "dailyCompletions": [
      {
        "habitId": "run",
        "date": "2026-08-05",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ],
    "weeklyHabits": [
      {
        "id": "gym",
        "name": "Gym",
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "categoryId": null
      }
    ],
    "weeklyCompletions": [
      {
        "weeklyHabitId": "gym",
        "weekStartDate": "2026-08-03",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ]
  }
}
"""

    val V2 = """
{
  "version": 2,
  "timestamp": 1000,
  "data": {
    "categories": [
      {
        "id": "cat1",
        "name": "Fitness",
        "displayOrder": 0,
        "updatedAtEpochMillis": 100
      }
    ],
    "dailyHabits": [
      {
        "id": "run",
        "name": "Run",
        "categoryId": "cat1",
        "monthlyGoal": 12,
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200
      }
    ],
    "dailyCompletions": [
      {
        "habitId": "run",
        "date": "2026-08-05",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ],
    "weeklyHabits": [
      {
        "id": "gym",
        "name": "Gym",
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "categoryId": null
      }
    ],
    "weeklyCompletions": [
      {
        "weeklyHabitId": "gym",
        "weekStartDate": "2026-08-03",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ],
    "weeklyPlans": [
      {
        "habitId": "run",
        "weekday": 2,
        "detail": "Intervals",
        "updatedAtEpochMillis": 250
      }
    ]
  }
}
"""

    val V3 = """
{
  "version": 3,
  "timestamp": 1000,
  "data": {
    "categories": [
      {
        "id": "cat1",
        "name": "Fitness",
        "displayOrder": 0,
        "updatedAtEpochMillis": 100
      }
    ],
    "dailyHabits": [
      {
        "id": "run",
        "name": "Run",
        "categoryId": "cat1",
        "monthlyGoal": 12,
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "kind": "ACTION",
        "datedOnly": true
      }
    ],
    "dailyCompletions": [
      {
        "habitId": "run",
        "date": "2026-08-05",
        "completed": true,
        "updatedAtEpochMillis": 300,
        "planId": "run|2026-08-05"
      }
    ],
    "weeklyHabits": [
      {
        "id": "gym",
        "name": "Gym",
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "categoryId": null
      }
    ],
    "weeklyCompletions": [
      {
        "weeklyHabitId": "gym",
        "weekStartDate": "2026-08-03",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ],
    "weeklyPlans": [
      {
        "habitId": "run",
        "weekday": 2,
        "detail": "Intervals",
        "updatedAtEpochMillis": 250
      }
    ],
    "dayPlans": [
      {
        "habitId": "run",
        "date": "2026-08-06",
        "detail": "Easy 6 km",
        "skipped": false,
        "updatedAtEpochMillis": 260,
        "id": "run|2026-08-06"
      }
    ]
  }
}
"""

    val V4 = """
{
  "version": 4,
  "timestamp": 1000,
  "checksum": "sha256:ec56a1a7c64844f564006886713339e60156e401d7254b2ade9f36462a2f8f92",
  "settings": {
    "themeMode": 2,
    "onboardingCompleted": true,
    "sheetUrl": "https://docs.google.com/spreadsheets/d/FIXTURESHEETID/edit"
  },
  "data": {
    "categories": [
      {
        "id": "cat1",
        "name": "Fitness",
        "displayOrder": 0,
        "updatedAtEpochMillis": 100
      }
    ],
    "dailyHabits": [
      {
        "id": "run",
        "name": "Run",
        "categoryId": "cat1",
        "monthlyGoal": 12,
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "kind": "ACTION",
        "datedOnly": true
      }
    ],
    "dailyCompletions": [
      {
        "habitId": "run",
        "date": "2026-08-05",
        "completed": true,
        "updatedAtEpochMillis": 300,
        "planId": "run|2026-08-05"
      }
    ],
    "weeklyHabits": [
      {
        "id": "gym",
        "name": "Gym",
        "displayOrder": 0,
        "active": true,
        "createdOn": "2026-08-01",
        "createdAtEpochMillis": 100,
        "updatedAtEpochMillis": 200,
        "categoryId": null
      }
    ],
    "weeklyCompletions": [
      {
        "weeklyHabitId": "gym",
        "weekStartDate": "2026-08-03",
        "completed": true,
        "updatedAtEpochMillis": 300
      }
    ],
    "weeklyPlans": [
      {
        "habitId": "run",
        "weekday": 2,
        "detail": "Intervals",
        "updatedAtEpochMillis": 250
      }
    ],
    "dayPlans": [
      {
        "habitId": "run",
        "date": "2026-08-06",
        "detail": "Easy 6 km",
        "skipped": false,
        "updatedAtEpochMillis": 260,
        "id": "run|2026-08-06"
      }
    ]
  }
}
"""
}
