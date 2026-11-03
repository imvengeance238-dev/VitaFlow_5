package app.vitaflow.com.max

import app.vitaflow.com.data.db.ProfileEntity
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.roundToInt

data class RoadmapPhase(val title: String, val text: String)

data class Roadmap(
    val bmi: Double,
    val bmiLabel: String,
    val ratePerWeek: Double,
    val weeks: Int,
    val calories: Int,
    val proteinG: Int,
    val waterMl: Int,
    val phases: List<RoadmapPhase>,
    val notes: List<String>
)

/**
 * The trainer's first meeting in code: checks that the goal is safe, then works out the pace, the calories, the protein and water,
 * and a week-by-week plan. Everything is computed here, so it is instant and always the same for the same answers.
 */
object RoadmapBuilder {
    fun bmi(p: ProfileEntity): Double? {
        val w = p.weightKg ?: return null
        val h = p.heightCm ?: return null
        return w / (h / 100.0).pow(2)
    }

    /** Asian cut-offs (the usual ones for Bangladesh). */
    fun bmiLabel(b: Double): String = when {
        b < 18.5 -> "কম ওজন"
        b < 23.0 -> "স্বাভাবিক ওজন"
        b < 25.0 -> "একটু বেশি ওজন"
        else -> "বেশি ওজন"
    }

    /** Returns a message when the goal is not safe or does not add up, otherwise null. */
    fun checkGoal(p: ProfileEntity): String? {
        val w = p.weightKg ?: return "বর্তমান ওজন দিন।"
        val h = p.heightCm ?: return "উচ্চতা দিন।"
        val t = p.targetWeightKg ?: return "যে ওজনে পৌঁছাতে চান সেটা দিন।"
        val current = w / (h / 100.0).pow(2)
        val targetBmi = t / (h / 100.0).pow(2)
        val minSafe = 18.5 * (h / 100.0).pow(2)
        return when {
            targetBmi < 18.5 -> "এই লক্ষ্য কম ওজনের সীমায় পড়ে, যা স্বাস্থ্যের জন্য নিরাপদ নয়। আপনার উচ্চতায় অন্তত ${minSafe.roundToInt() + 1} কেজি রাখুন।"
            p.goal == "LOSE_FAT" && current < 18.5 -> "আপনার ওজন এখনই কম। ওজন কমানো নিরাপদ নয়, বরং ফিট থাকা বা ওজন বাড়ানো বেছে নিন।"
            p.goal == "LOSE_FAT" && t >= w -> "ওজন কমাতে চাইলে লক্ষ্য ওজন এখনকার চেয়ে কম হতে হবে।"
            (p.goal == "GAIN_WEIGHT" || p.goal == "BUILD_MUSCLE") && t < w - 0.5 -> "ওজন বা পেশি বাড়াতে চাইলে লক্ষ্য ওজন এখনকার চেয়ে বেশি হতে হবে।"
            abs(t - w) > 20 -> "এক ধাপে ২০ কেজির বেশি পরিবর্তন ঠিক নয়। প্রথমে ছোট একটা লক্ষ্য দিন, পরে আবার বাড়ানো যাবে।"
            else -> null
        }
    }

    fun build(p: ProfileEntity, age: Int?): Roadmap {
        val w = p.weightKg ?: 60.0
        val b = bmi(p) ?: 22.0
        val target = p.targetWeightKg ?: w
        val diff = abs(target - w)
        val rate = when (p.goal) {
            "LOSE_FAT" -> if (b < 25) 0.35 else 0.5
            "GAIN_WEIGHT" -> 0.3
            "BUILD_MUSCLE" -> 0.25
            else -> 0.0
        }
        val safeWeeks = if (rate > 0) ceil(diff / rate).toInt().coerceIn(2, 78) else 12
        val notes = mutableListOf<String>()
        if (p.targetWeeks > 0 && rate > 0 && safeWeeks > p.targetWeeks) {
            notes += "আপনি ${p.targetWeeks} সপ্তাহ চেয়েছেন, কিন্তু নিরাপদ গতিতে প্রায় $safeWeeks সপ্তাহ লাগবে। তাড়াহুড়ো করলে স্বাস্থ্যের ক্ষতি হয়, তাই $safeWeeks সপ্তাহের হিসাবেই চলব।"
        }

        val kcal = DietPlanner.target(p.copy(ageYears = p.ageYears ?: age), age).kcal
        val perKg = when (p.goal) { "BUILD_MUSCLE" -> 1.6; "LOSE_FAT" -> 1.5; "GAIN_WEIGHT" -> 1.4; else -> 1.2 }
        val protein = ((w * perKg) / 5).roundToInt() * 5
        val water = (((w * 35) / 250).roundToInt() * 250).coerceIn(1500, 4000)

        val days = p.daysPerWeek.coerceIn(2, 6)
        val light = p.needsDoctor
        val phases = listOf(
            RoadmapPhase(
                "সপ্তাহ ১-২: অভ্যাস তৈরি",
                if (light) "ডাক্তারের অনুমতি নিন। অনুমতি পেলে সপ্তাহে ২ দিন খুব হালকা হাঁটা ও সহজ নড়াচড়া। পানি আর ঘুম ঠিক করুন।"
                else "সপ্তাহে ${maxOf(2, days - 1)} দিন, প্রতিবার ${p.minutesPerSession} মিনিট। শুধু ঠিকভাবে করা শিখুন, ভার বা গতি নয়। পানি আর ঘুম ঠিক করুন।"
            ),
            RoadmapPhase(
                "সপ্তাহ ৩-৬: গতি বাড়ানো",
                if (light) "ডাক্তার ঠিক বললে ধীরে ধীরে দিন বাড়ান, কিন্তু কঠিন নয়।"
                else "সপ্তাহে $days দিন। প্রতি exercise-এ একটা করে set বা কয়েকটা rep বাড়ান। ওজন প্রতি সপ্তাহে একবার মাপুন।"
            ),
            RoadmapPhase(
                "সপ্তাহ ৭ থেকে: পুরোদমে",
                "দুই সপ্তাহে ওজন প্রায় না বদলালে খাবার সামান্য বদলাব (১০০-১৫০ কিলোক্যালরি কমানো বা বাড়ানো)। প্রতি ৪ সপ্তাহে ওয়ার্কআউট নতুন করে সাজাব।"
            )
        )
        if (p.sleepHours < 6) notes += "আপনার ঘুম কম। ওজন কমানো বা পেশি বাড়াতে ঘুম খুব জরুরি, চেষ্টা করুন অন্তত ৭ ঘণ্টা।"
        if (p.waterGlasses < 6) notes += "পানি কম খাচ্ছেন। দিনে প্রায় ${water / 250} গ্লাস লক্ষ্য রাখুন।"
        if (p.junkLevel == "HIGH") notes += "ভাজাপোড়া, মিষ্টি ও ফাস্টফুড আগে কমান। শুধু এতেই ফল আসবে।"
        if (p.smoker) notes += "ধূমপান ব্যায়ামের ক্ষমতা কমায়। কমানোর চেষ্টা করুন।"
        if ((age ?: p.ageYears ?: 0) >= 45 && p.level == "BEGINNER") notes += "আপনার বয়সে শুরুতে ওয়ার্ম-আপ লম্বা করুন এবং ধীরে বাড়ান।"
        if (p.needsDoctor) notes += "আপনার স্বাস্থ্যের উত্তর অনুযায়ী আগে ডাক্তারের অনুমতি নিন। অনুমতি না পাওয়া পর্যন্ত কঠিন ব্যায়াম করবেন না।"
        return Roadmap(b, bmiLabel(b), rate, if (rate > 0) safeWeeks else 12, kcal, protein, water, phases, notes)
    }
}
