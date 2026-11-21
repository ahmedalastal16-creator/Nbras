package com.example.data.curriculum

data class GazaStage(
    val id: String,
    val title: String,
    val badge: String,
    val subjects: List<String>
)

object GazaCurriculumCatalog {
    const val OFFICIAL_PORTAL_URL = "https://www.moe.edu.ps/gaza/subjects"

    val stages: List<GazaStage> = listOf(
        GazaStage(
            id = "tawjihi_scientific",
            title = "التوجيهي — الفرع العلمي",
            badge = "ثاني عشر علمي",
            subjects = listOf(
                "الرياضيات (العلمي)",
                "الفيزياء",
                "الكيمياء",
                "العلوم الحياتية (الأحياء)",
                "اللغة العربية",
                "اللغة الإنجليزية",
                "التربية الإسلامية",
                "التكنولوجيا"
            )
        ),
        GazaStage(
            id = "tawjihi_literary",
            title = "التوجيهي — الفرع الأدبي",
            badge = "ثاني عشر أدبي",
            subjects = listOf(
                "اللغة العربية (المطالعة والنقد والبلاغة)",
                "اللغة الإنجليزية",
                "الدراسات التاريخية",
                "الدراسات الجغرافية",
                "الثقافة العلمية",
                "الرياضيات (الأدبي)",
                "التربية الإسلامية",
                "التكنولوجيا"
            )
        ),
        GazaStage(
            id = "tawjihi_entrepreneurship",
            title = "التوجيهي — الريادة والأعمال",
            badge = "الريادة والأعمال",
            subjects = listOf(
                "الإدارة والاقتصاد",
                "المشاريع الصغيرة",
                "المحاسبة",
                "الرياضيات",
                "اللغة العربية",
                "اللغة الإنجليزية",
                "التربية الإسلامية",
                "التكنولوجيا"
            )
        ),
        GazaStage(
            id = "grade_11",
            title = "الصف الحادي عشر (علمي / أدبي)",
            badge = "حادي عشر",
            subjects = listOf(
                "الفيزياء",
                "الكيمياء",
                "العلوم الحياتية",
                "الرياضيات",
                "اللغة العربية",
                "اللغة الإنجليزية",
                "الدراسات التاريخية",
                "التربية الإسلامية"
            )
        ),
        GazaStage(
            id = "basic_grades",
            title = "المرحلة الأساسية (الصفوف 1–10)",
            badge = "أساسي 1–10",
            subjects = listOf(
                "الرياضيات",
                "العلوم العامة",
                "اللغة العربية",
                "اللغة الإنجليزية",
                "التربية الإسلامية",
                "الدراسات الاجتماعية",
                "البرمجة والتكنولوجيا"
            )
        )
    )

    fun packageConversationId(stageId: String, subjectTitle: String): String {
        val stage = stages.firstOrNull { it.id == stageId }
        val idx = stage?.subjects?.indexOf(subjectTitle) ?: -1
        return if (idx >= 0) {
            "pkg_${stageId}_$idx"
        } else {
            val hash = (subjectTitle.hashCode().toUInt()).toString(16)
            "pkg_${stageId}_$hash"
        }
    }

    fun findPackageById(packageId: String): Pair<GazaStage, String>? {
        for (stage in stages) {
            stage.subjects.forEachIndexed { idx, subject ->
                if ("pkg_${stage.id}_$idx" == packageId) {
                    return stage to subject
                }
            }
        }
        return null
    }
}

