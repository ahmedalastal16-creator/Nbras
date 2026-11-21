package com.example.data.curriculum

import com.example.data.local.StudyScheduleEntity

enum class StudyIntensity(val label: String, val sessionsPerDay: Int, val sessionMinutes: Int) {
    LIGHT("خفيف (ساعتان)", 2, 45),
    BALANCED("متوازن (4 ساعات)", 3, 50),
    INTENSIVE("مكثف توجيهي (6 ساعات)", 4, 60)
}

enum class PreferredStudyPeriod(val label: String, val startSlots: List<String>) {
    MORNING("صباحي", listOf("08:00 ص", "09:15 ص", "10:30 ص", "12:00 ظ")),
    AFTERNOON("بعد الظهر", listOf("02:30 م", "03:45 م", "05:00 م", "06:15 م")),
    EVENING("مسائي", listOf("05:30 م", "06:45 م", "08:00 م", "09:15 م"))
}

object ScheduleGenerator {

    val weekDays: List<String> = listOf(
        "السبت",
        "الأحد",
        "الإثنين",
        "الثلاثاء",
        "الأربعاء",
        "الخميس",
        "الجمعة"
    )

    private val subjectPalette: List<Long> = listOf(
        0xFFE2C068L,
        0xFF5EEAD4L,
        0xFF7AA2F7L,
        0xFFF59E0BL,
        0xFF34D399L,
        0xFFF472B6L
    )

    private val focusTypes: List<String> = listOf(
        "فهم وشرح الرزمة",
        "حل مسائل وتمارين",
        "بطاقات استرجاع ومراجعة",
        "اختبار قصير وتثبيت"
    )

    fun generateWeeklySchedule(
        stage: GazaStage,
        selectedSubjects: List<String>,
        intensity: StudyIntensity,
        preferredPeriod: PreferredStudyPeriod
    ): List<StudyScheduleEntity> {
        val subjects = selectedSubjects
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { stage.subjects.take(4) }

        val result = mutableListOf<StudyScheduleEntity>()
        var subjectCursor = 0

        weekDays.forEachIndexed { dayIndex, dayLabel ->
            // Keep Friday lighter for weekly cumulative review & rest.
            val dailyCount = if (dayIndex == 6) {
                (intensity.sessionsPerDay - 1).coerceAtLeast(1)
            } else {
                intensity.sessionsPerDay
            }
            val slots = preferredPeriod.startSlots.take(dailyCount)

            slots.forEachIndexed { slotIndex, timeSlot ->
                val subject = subjects[subjectCursor % subjects.size]
                val focusType = when {
                    dayIndex == 6 -> "مراجعة أسبوعية شاملة"
                    else -> focusTypes[(dayIndex + slotIndex) % focusTypes.size]
                }
                val topic = when {
                    dayIndex == 6 -> "تثبيت أهم قوانين وأفكار $subject خلال الأسبوع"
                    focusType.contains("حل") -> "حل أنشطة وتمارين رزمة $subject"
                    focusType.contains("بطاقات") -> "مراجعة ملخص وبطاقات $subject"
                    focusType.contains("اختبار") -> "حل نموذج امتحان سابق في $subject"
                    else -> "دراسة الدرس التالي من رزمة $subject مع نبراس"
                }
                val accent = subjectPalette[subjects.indexOf(subject).coerceAtLeast(0) % subjectPalette.size]

                result.add(
                    StudyScheduleEntity(
                        stageId = stage.id,
                        dayIndex = dayIndex,
                        dayLabel = dayLabel,
                        subjectTitle = subject,
                        topic = topic,
                        timeSlot = timeSlot,
                        durationMinutes = intensity.sessionMinutes,
                        focusType = focusType,
                        accentHex = accent,
                        isCompleted = false
                    )
                )
                subjectCursor++
            }
        }
        return result
    }
}
