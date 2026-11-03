package app.vitaflow.com.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.vitaflow.com.AppContainer
import app.vitaflow.com.data.db.AccountEntity
import app.vitaflow.com.data.db.ProfileEntity
import app.vitaflow.com.max.DietPlanner
import app.vitaflow.com.max.PlanTemplates
import app.vitaflow.com.max.RoadmapBuilder
import app.vitaflow.com.ui.components.ChipFlow
import app.vitaflow.com.ui.components.ProgressBar
import app.vitaflow.com.ui.components.Segmented
import app.vitaflow.com.ui.components.toDoubleLoose
import app.vitaflow.com.ui.components.toIntLoose
import app.vitaflow.com.ui.theme.GlassButton
import app.vitaflow.com.ui.theme.GlassChip
import app.vitaflow.com.ui.theme.GlassSurface
import app.vitaflow.com.ui.theme.GlassTextField
import app.vitaflow.com.ui.theme.LocalGlass
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

/** Everything the person answers. Kept in one place so the steps can read each other's answers. */
private class Form {
    var name by mutableStateOf("")
    var age by mutableStateOf("")
    var gender by mutableStateOf("")
    var heightUnit by mutableIntStateOf(0) // 0 = feet+inch, 1 = cm
    var cm by mutableStateOf("")
    var ft by mutableStateOf("")
    var inch by mutableStateOf("")
    var weight by mutableStateOf("")
    var target by mutableStateOf("")
    var goal by mutableStateOf("")
    var goalNote by mutableStateOf("")
    var weeks by mutableIntStateOf(-1)
    var level by mutableStateOf("")
    var days by mutableIntStateOf(0)
    var minutes by mutableIntStateOf(0)
    var time by mutableStateOf("")
    var place by mutableStateOf("")
    var equipment by mutableStateOf(setOf<String>())
    var activity by mutableStateOf("")
    var sleep by mutableIntStateOf(0)
    var water by mutableIntStateOf(0)
    var smoker by mutableStateOf("")
    var meals by mutableIntStateOf(0)
    var rice by mutableIntStateOf(-1)
    var foods by mutableStateOf(setOf<String>())
    var junk by mutableStateOf("")
    var tea by mutableIntStateOf(-1)
    var dietType by mutableStateOf("")
    var allergies by mutableStateOf(setOf<String>())
    var allergyOther by mutableStateOf("")
    var foodNotes by mutableStateOf("")
    val health = mutableStateMapOf<String, Boolean>()
    var bodyParts by mutableStateOf(setOf<String>())
    var injuryOther by mutableStateOf("")
    var meds by mutableStateOf("")
    var doctorOk by mutableStateOf(false)
    var consent by mutableStateOf(false)

    fun heightCm(): Int? = if (heightUnit == 1) cm.toIntLoose() else {
        val f = ft.toIntLoose()
        val i = inch.toIntLoose() ?: 0
        if (f == null) null else ((f * 12 + i) * 2.54).roundToInt()
    }

    fun needsDoctor(): Boolean =
        listOf("heart", "chest", "dizzy", "bp", "diabetes", "asthma", "surgery", "pregnant").any { health[it] == true }

    fun toProfile(accountId: Long, waterMl: Int): ProfileEntity {
        val equip = when {
            "GYM" in equipment -> "GYM"
            "DUMBBELL" in equipment || "BARBELL" in equipment -> "DUMBBELL"
            else -> "NONE"
        }
        val parts = mutableListOf<String>()
        if (health["bones"] == true) {
            bodyParts.forEach { parts += it }
            if (injuryOther.isNotBlank()) parts += injuryOther.trim()
        }
        val allergyList = allergies.toMutableList().also { if (allergyOther.isNotBlank()) it += allergyOther.trim() }
        return ProfileEntity(
            accountId = accountId,
            gender = if (gender == "MALE" || gender == "FEMALE") gender else "",
            heightCm = heightCm(),
            weightKg = weight.toDoubleLoose(),
            goal = if (goal == "OTHER") "MAINTAIN" else goal,
            level = level,
            equipment = equip,
            dietPref = when (dietType) { "VEG" -> "নিরামিষ"; "VEGAN" -> "ভেগান"; else -> "" },
            allergies = allergyList.joinToString(", "),
            injuries = parts.joinToString(", "),
            waterTargetMl = waterMl,
            ageYears = age.toIntLoose(),
            targetWeightKg = target.toDoubleLoose(),
            targetWeeks = weeks.coerceAtLeast(0),
            goalNote = goalNote.trim(),
            daysPerWeek = days,
            minutesPerSession = minutes,
            preferredTime = time,
            place = place,
            equipmentList = equipment.joinToString(","),
            activityLevel = activity,
            sleepHours = sleep,
            waterGlasses = water,
            smoker = smoker == "YES",
            mealsPerDay = meals,
            riceMeals = rice.coerceAtLeast(0),
            foodsEaten = foods.joinToString(","),
            junkLevel = junk,
            teaCups = tea.coerceAtLeast(0),
            foodNotes = foodNotes.trim(),
            healthFlags = health.filter { it.value }.keys.joinToString(","),
            medications = meds.trim(),
            needsDoctor = needsDoctor(),
            onboardingDone = true
        )
    }
}

private val stepTitles = listOf("আপনার পরিচয়", "আপনার লক্ষ্য", "ব্যায়ামের অভ্যাস", "যন্ত্রপাতি", "দৈনন্দিন জীবন", "খাওয়াদাওয়া", "স্বাস্থ্য", "আপনার রোডম্যাপ")

@Composable
fun OnboardingScreen(
    account: AccountEntity,
    container: AppContainer,
    edit: Boolean,
    onDone: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val g = LocalGlass.current
    val scope = rememberCoroutineScope()
    val form = remember { Form() }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var oldWeight by remember { mutableStateOf<Double?>(null) }

    // fill in what is already known (the account, or an earlier profile when editing)
    LaunchedEffect(account.id) {
        form.name = account.name
        account.birthDate.takeIf { it.isNotBlank() }?.let { b ->
            runCatching { Period.between(LocalDate.parse(b), LocalDate.now()).years }.getOrNull()?.let { form.age = it.toString() }
        }
        container.profileRepo.get(account.id)?.takeIf { it.onboardingDone }?.let { p ->
            oldWeight = p.weightKg
            p.ageYears?.let { form.age = it.toString() }
            form.gender = p.gender.ifEmpty { "OTHER" }
            p.heightCm?.let { form.heightUnit = 1; form.cm = it.toString() }
            p.weightKg?.let { form.weight = "%.1f".format(it) }
            p.targetWeightKg?.let { form.target = "%.1f".format(it) }
            form.goal = p.goal; form.goalNote = p.goalNote; form.weeks = p.targetWeeks
            form.level = p.level; form.days = p.daysPerWeek; form.minutes = p.minutesPerSession
            form.time = p.preferredTime; form.place = p.place
            form.equipment = p.equipmentList.split(",").filter { it.isNotBlank() }.toSet()
            form.activity = p.activityLevel; form.sleep = p.sleepHours; form.water = p.waterGlasses
            form.smoker = if (p.smoker) "YES" else "NO"
            form.meals = p.mealsPerDay; form.rice = p.riceMeals
            form.foods = p.foodsEaten.split(",").filter { it.isNotBlank() }.toSet()
            form.junk = p.junkLevel; form.tea = p.teaCups
            form.dietType = when (p.dietPref) { "নিরামিষ" -> "VEG"; "ভেগান" -> "VEGAN"; else -> "ALL" }
            form.allergyOther = p.allergies
            form.foodNotes = p.foodNotes; form.meds = p.medications
            listOf("heart", "chest", "dizzy", "bp", "diabetes", "asthma", "bones", "surgery", "pregnant").forEach { k ->
                form.health[k] = p.healthFlags.split(",").contains(k)
            }
            form.injuryOther = p.injuries
        }
    }

    fun validate(s: Int): String? {
        val w = form.weight.toDoubleLoose()
        val t = form.target.toDoubleLoose()
        val h = form.heightCm()
        val a = form.age.toIntLoose()
        return when (s) {
            0 -> when {
                form.name.trim().length < 2 -> "আপনার নাম লিখুন।"
                a == null || a !in 10..90 -> "বয়স ১০ থেকে ৯০ বছরের মধ্যে লিখুন।"
                form.gender.isEmpty() -> "আপনি পুরুষ না মহিলা, বেছে নিন।"
                h == null || h !in 100..230 -> "উচ্চতা ঠিকমতো লিখুন।"
                w == null || w !in 25.0..250.0 -> "এখনকার ওজন কেজিতে লিখুন।"
                t == null || t !in 25.0..250.0 -> "যে ওজনে পৌঁছাতে চান, সেটা লিখুন।"
                else -> null
            }
            1 -> {
                val p = form.toProfile(account.id, 2500)
                when {
                    form.goal.isEmpty() -> "আপনি কী চান, বেছে নিন।"
                    form.goal == "OTHER" && form.goalNote.isBlank() -> "আপনার লক্ষ্যটা এক লাইনে লিখুন।"
                    form.weeks < 0 -> "কত সময়ের মধ্যে চান, বেছে নিন।"
                    form.goal == "MAINTAIN" && w != null && t != null && kotlin.math.abs(t - w) > 3 -> "ফিট থাকতে চাইলে লক্ষ্য ওজন এখনকার কাছাকাছি রাখুন।"
                    else -> RoadmapBuilder.checkGoal(p)
                }
            }
            2 -> when {
                form.level.isEmpty() -> "আগে ব্যায়াম করার অভিজ্ঞতা বেছে নিন।"
                form.days == 0 -> "সপ্তাহে কয়দিন পারবেন, বেছে নিন।"
                form.minutes == 0 -> "প্রতিদিন কতক্ষণ সময় দিতে পারবেন, বেছে নিন।"
                form.time.isEmpty() -> "কোন সময়ে সুবিধা, বেছে নিন।"
                form.place.isEmpty() -> "কোথায় ব্যায়াম করবেন, বেছে নিন।"
                else -> null
            }
            3 -> if (form.equipment.isEmpty()) "কী কী আছে বেছে নিন (কিছু না থাকলে \"কিছুই নেই\")।" else null
            4 -> when {
                form.activity.isEmpty() -> "সারাদিনের কাজের ধরন বেছে নিন।"
                form.sleep == 0 -> "রাতে কত ঘণ্টা ঘুমান, বেছে নিন।"
                form.water == 0 -> "দিনে কত গ্লাস পানি খান, বেছে নিন।"
                form.smoker.isEmpty() -> "ধূমপান করেন কি না, বেছে নিন।"
                else -> null
            }
            5 -> when {
                form.meals == 0 -> "দিনে কতবার খান, বেছে নিন।"
                form.rice < 0 -> "দিনে কত বেলা ভাত খান, বেছে নিন।"
                form.foods.isEmpty() -> "নিয়মিত কী কী খান, অন্তত একটা বেছে নিন।"
                form.junk.isEmpty() -> "ভাজাপোড়া বা মিষ্টি কতটা খান, বেছে নিন।"
                form.tea < 0 -> "চা বা কফি কত কাপ খান, বেছে নিন।"
                form.dietType.isEmpty() -> "খাদ্যের ধরন বেছে নিন।"
                else -> null
            }
            6 -> {
                val keys = listOf("heart", "chest", "dizzy", "bp", "diabetes", "asthma", "bones", "surgery") +
                    if (form.gender == "MALE") emptyList() else listOf("pregnant")
                when {
                    keys.any { form.health[it] == null } -> "সবগুলো প্রশ্নের উত্তর দিন, এটা আপনার নিরাপত্তার জন্য।"
                    form.health["bones"] == true && form.bodyParts.isEmpty() && form.injuryOther.isBlank() -> "কোথায় ব্যথা বা আঘাত, বেছে নিন।"
                    form.needsDoctor() && !form.doctorOk -> "চালিয়ে যেতে নিচের ঘরে টিক দিন।"
                    else -> null
                }
            }
            else -> if (!form.consent) "চালিয়ে যেতে নিচের ঘরে টিক দিন।" else null
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stepTitles[step], style = MaterialTheme.typography.headlineMedium, color = g.textPrimary, modifier = Modifier.weight(1f))
            Text("${step + 1}/${stepTitles.size}", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        ProgressBar((step + 1f) / stepTitles.size)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> StepIdentity(form)
                1 -> StepGoal(form)
                2 -> StepHabit(form)
                3 -> StepEquipment(form)
                4 -> StepLife(form)
                5 -> StepFood(form)
                6 -> StepHealth(form)
                else -> StepRoadmap(form, account)
            }
        }

        error?.let { Text(it, color = g.danger, style = MaterialTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                GlassButton("আগে", { step -= 1; error = null }, Modifier.weight(1f))
            } else if (onCancel != null) {
                GlassButton("বাতিল", onCancel, Modifier.weight(1f))
            }
            val last = step == stepTitles.size - 1
            GlassButton(if (last) "শুরু করি" else "পরের ধাপ", {
                val problem = validate(step)
                if (problem != null) {
                    error = problem
                } else {
                    error = null
                    if (!last) {
                        step += 1
                    } else if (!saving) {
                        saving = true
                        scope.launch {
                            val age = form.age.toIntLoose()
                            val draft = form.toProfile(account.id, 2500)
                            val road = RoadmapBuilder.build(draft, age)
                            val p = draft.copy(waterTargetMl = road.waterMl)
                            container.profileRepo.save(p)
                            val w = p.weightKg
                            if (w != null && w != oldWeight) container.profileRepo.logWeight(account.id, w)
                            if (!edit) {
                                val plan = PlanTemplates.workout(p)
                                container.workoutRepo.createPlan(account.id, plan.name, plan.exercises)
                                val diet = DietPlanner.build(p, age)
                                if (diet.error == null) container.mealRepo.replaceDietPlan(account.id, diet.drafts)
                            }
                            saving = false
                            onDone()
                        }
                    }
                }
            }, Modifier.weight(2f), enabled = !saving)
        }
    }
}

// ------------------------------------------------------------------ small building blocks

@Composable
private fun Question(title: String, content: @Composable () -> Unit) {
    val g = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = g.textPrimary, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun PickOne(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    ChipFlow(options, setOf(selected), onSelect)
}

@Composable
private fun PickNumber(options: List<Pair<Int, String>>, selected: Int, onSelect: (Int) -> Unit) {
    ChipFlow(options.map { it.first.toString() to it.second }, setOf(selected.toString())) { onSelect(it.toInt()) }
}

@Composable
private fun PickMany(options: List<Pair<String, String>>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    ChipFlow(options, selected) { key -> onChange(if (key in selected) selected - key else selected + key) }
}

// ------------------------------------------------------------------ steps

@Composable
private fun StepIdentity(f: Form) {
    GlassTextField(f.name, { f.name = it }, "আপনার নাম")
    GlassTextField(f.age, { f.age = it }, "বয়স (বছর)", keyboardType = KeyboardType.Number)
    Question("আপনি") {
        PickOne(listOf("MALE" to "পুরুষ", "FEMALE" to "মহিলা", "OTHER" to "বলতে চাই না"), f.gender) { f.gender = it }
    }
    Question("উচ্চতা") {
        Segmented(listOf("ফুট-ইঞ্চি", "সেন্টিমিটার"), f.heightUnit, { f.heightUnit = it })
        if (f.heightUnit == 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(f.ft, { f.ft = it }, "ফুট", Modifier.weight(1f), keyboardType = KeyboardType.Number)
                GlassTextField(f.inch, { f.inch = it }, "ইঞ্চি", Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
        } else {
            GlassTextField(f.cm, { f.cm = it }, "সেন্টিমিটার", keyboardType = KeyboardType.Number)
        }
    }
    GlassTextField(f.weight, { f.weight = it }, "এখনকার ওজন (কেজি)", keyboardType = KeyboardType.Decimal)
    GlassTextField(f.target, { f.target = it }, "যে ওজনে পৌঁছাতে চান (কেজি)", keyboardType = KeyboardType.Decimal)
}

@Composable
private fun StepGoal(f: Form) {
    Question("আপনি কী চান?") {
        PickOne(
            listOf("LOSE_FAT" to "ওজন কমাতে", "GAIN_WEIGHT" to "ওজন বাড়াতে", "BUILD_MUSCLE" to "পেশি বানাতে", "MAINTAIN" to "ফিট ও সুস্থ থাকতে", "OTHER" to "অন্য কিছু"),
            f.goal
        ) { f.goal = it }
        if (f.goal == "OTHER") GlassTextField(f.goalNote, { f.goalNote = it }, "আপনার লক্ষ্য এক লাইনে লিখুন")
    }
    Question("কত সময়ের মধ্যে চান?") {
        PickNumber(listOf(4 to "১ মাস", 13 to "৩ মাস", 26 to "৬ মাস", 52 to "১ বছর", 0 to "তাড়া নেই"), f.weeks) { f.weeks = it }
    }
}

@Composable
private fun StepHabit(f: Form) {
    Question("আগে কি নিয়মিত ব্যায়াম করেছেন?") {
        PickOne(
            listOf("BEGINNER" to "না, একদম নতুন বা অনেকদিন করিনি", "INTERMEDIATE" to "মাঝে মাঝে বা কয়েক মাস নিয়মিত করেছি", "ADVANCED" to "এক বছরের বেশি নিয়মিত করছি"),
            f.level
        ) { f.level = it }
    }
    Question("সপ্তাহে কয়দিন ব্যায়াম করতে পারবেন?") {
        PickNumber(listOf(2 to "২ দিন", 3 to "৩ দিন", 4 to "৪ দিন", 5 to "৫ দিন", 6 to "৬ দিন"), f.days) { f.days = it }
    }
    Question("প্রতিদিন কতক্ষণ সময় দিতে পারবেন?") {
        PickNumber(listOf(20 to "২০ মিনিট", 30 to "৩০ মিনিট", 45 to "৪৫ মিনিট", 60 to "১ ঘণ্টা"), f.minutes) { f.minutes = it }
    }
    Question("কোন সময়ে সুবিধা?") {
        PickOne(listOf("MORNING" to "সকাল", "NOON" to "দুপুর", "EVENING" to "বিকাল", "NIGHT" to "রাত"), f.time) { f.time = it }
    }
    Question("কোথায় ব্যায়াম করবেন?") {
        PickOne(listOf("HOME" to "ঘরে", "GYM" to "জিমে", "OUTDOOR" to "বাইরে (পার্ক, মাঠ)"), f.place) { f.place = it }
    }
}

@Composable
private fun StepEquipment(f: Form) {
    Question("আপনার কাছে কী কী আছে?") {
        PickMany(
            listOf(
                "NONE" to "কিছুই নেই", "DUMBBELL" to "ডাম্বেল", "BARBELL" to "বারবেল", "BAND" to "রেজিস্ট্যান্স ব্যান্ড",
                "PULLUP" to "পুল-আপ বার", "CARDIO" to "সাইকেল / ট্রেডমিল", "GYM" to "জিমের সব মেশিন"
            ),
            f.equipment
        ) { new ->
            // "nothing" cannot go together with the others
            f.equipment = if ("NONE" in new && "NONE" !in f.equipment) setOf("NONE") else new - "NONE"
        }
    }
}

@Composable
private fun StepLife(f: Form) {
    Question("সারাদিন কেমন কাটে?") {
        PickOne(
            listOf("SEDENTARY" to "বেশিরভাগ সময় বসে থাকি", "LIGHT" to "কিছুটা হাঁটাহাঁটি করি", "ACTIVE" to "সারাদিন দাঁড়িয়ে বা পরিশ্রমের কাজ করি"),
            f.activity
        ) { f.activity = it }
    }
    Question("রাতে কত ঘণ্টা ঘুমান?") {
        PickNumber(listOf(5 to "৫ বা কম", 6 to "৬", 7 to "৭", 8 to "৮ বা বেশি"), f.sleep) { f.sleep = it }
    }
    Question("দিনে কত গ্লাস পানি খান?") {
        PickNumber(listOf(3 to "৩ বা কম", 5 to "৪-৬", 8 to "৭-৯", 10 to "১০ বা বেশি"), f.water) { f.water = it }
    }
    Question("ধূমপান করেন?") {
        PickOne(listOf("NO" to "না", "YES" to "হ্যাঁ"), f.smoker) { f.smoker = it }
    }
}

@Composable
private fun StepFood(f: Form) {
    Question("দিনে কতবার খান? (নাস্তা, ভাত, স্ন্যাক সব মিলিয়ে)") {
        PickNumber(listOf(2 to "২ বার", 3 to "৩ বার", 4 to "৪ বার", 5 to "৫ বা বেশি"), f.meals) { f.meals = it }
    }
    Question("দিনে কত বেলা ভাত খান?") {
        PickNumber(listOf(0 to "খাই না", 1 to "১ বেলা", 2 to "২ বেলা", 3 to "৩ বেলা"), f.rice) { f.rice = it }
    }
    Question("নিয়মিত কী কী খান?") {
        PickMany(
            listOf(
                "fish" to "মাছ", "chicken" to "মুরগি", "beef" to "গরু / খাসি", "egg" to "ডিম",
                "milk" to "দুধ / দই", "dal" to "ডাল", "veg" to "সবজি", "fruit" to "ফল"
            ),
            f.foods
        ) { f.foods = it }
    }
    Question("ভাজাপোড়া, মিষ্টি বা ফাস্টফুড কতটা খান?") {
        PickOne(listOf("LOW" to "খুব কম", "MEDIUM" to "মাঝারি", "HIGH" to "বেশি"), f.junk) { f.junk = it }
    }
    Question("দিনে কত কাপ চা বা কফি?") {
        PickNumber(listOf(0 to "খাই না", 1 to "১-২ কাপ", 3 to "৩-৪ কাপ", 5 to "৫ বা বেশি"), f.tea) { f.tea = it }
    }
    Question("আপনার খাদ্যের ধরন?") {
        PickOne(listOf("ALL" to "সব খাই", "VEG" to "নিরামিষ", "VEGAN" to "ভেগান (দুধ-ডিমও না)"), f.dietType) { f.dietType = it }
    }
    Question("কোনো খাবারে অ্যালার্জি বা যা খেতে পারেন না?") {
        PickMany(
            listOf("ডিম" to "ডিম", "দুধ" to "দুধ / দই", "বাদাম" to "বাদাম", "মাছ" to "মাছ", "গ্লুটেন" to "আটা / গম"),
            f.allergies
        ) { f.allergies = it }
        GlassTextField(f.allergyOther, { f.allergyOther = it }, "অন্য কিছু থাকলে লিখুন")
    }
    GlassTextField(f.foodNotes, { f.foodNotes = it }, "খাবারের আর কিছু পছন্দ-অপছন্দ (ইচ্ছা হলে)")
}

@Composable
private fun YesNo(f: Form, key: String, text: String) {
    val g = LocalGlass.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text, color = g.textPrimary, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip("হ্যাঁ", f.health[key] == true, { f.health[key] = true })
            GlassChip("না", f.health[key] == false, { f.health[key] = false })
        }
    }
}

@Composable
private fun StepHealth(f: Form) {
    val g = LocalGlass.current
    Text("এই প্রশ্নগুলো আপনার নিরাপত্তার জন্য। যা সত্যি, তাই বলুন।", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
    YesNo(f, "heart", "ডাক্তার কি কখনো বলেছেন আপনার হৃদরোগ আছে, বা শুধু ডাক্তারের পরামর্শ নিয়ে ব্যায়াম করতে?")
    YesNo(f, "chest", "হাঁটলে, সিঁড়ি ভাঙলে বা ব্যায়াম করলে বুকে ব্যথা বা চাপ লাগে?")
    YesNo(f, "dizzy", "মাথা ঘোরে, বা কখনো অজ্ঞান হয়ে গেছেন?")
    YesNo(f, "bp", "উচ্চ রক্তচাপ (প্রেশার) আছে?")
    YesNo(f, "diabetes", "ডায়াবেটিস আছে?")
    YesNo(f, "asthma", "হাঁপানি বা শ্বাসকষ্টের সমস্যা আছে?")
    YesNo(f, "bones", "হাড়, জোড়া, কোমর, হাঁটু বা কাঁধে ব্যথা বা আগের কোনো আঘাত আছে?")
    if (f.health["bones"] == true) {
        PickMany(
            listOf("হাঁটু" to "হাঁটু", "কাঁধ" to "কাঁধ", "কোমর" to "কোমর / পিঠ", "কব্জি" to "কব্জি", "ঘাড়" to "ঘাড়"),
            f.bodyParts
        ) { f.bodyParts = it }
        GlassTextField(f.injuryOther, { f.injuryOther = it }, "আরও কিছু থাকলে লিখুন")
    }
    YesNo(f, "surgery", "গত ৬ মাসে কোনো অপারেশন হয়েছে?")
    if (f.gender != "MALE") YesNo(f, "pregnant", "আপনি কি গর্ভবতী, বা সম্প্রতি সন্তান হয়েছে?")
    GlassTextField(f.meds, { f.meds = it }, "নিয়মিত কোনো ওষুধ খেলে নাম লিখুন (না খেলে ফাঁকা)")
    if (f.needsDoctor()) {
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("আগে ডাক্তারের সাথে কথা বলুন", color = g.danger, style = MaterialTheme.typography.titleMedium)
                Text(
                    "আপনার উত্তর অনুযায়ী কঠিন ব্যায়াম শুরুর আগে ডাক্তারের অনুমতি দরকার। আমি আপনার জন্য খুব হালকা পরিকল্পনা বানাব।",
                    color = g.textSecondary, style = MaterialTheme.typography.bodyMedium
                )
                CheckRow("আমি ডাক্তারের সাথে কথা বলেছি বা বলব। তাঁর অনুমতি ছাড়া কঠিন ব্যায়াম করব না।", f.doctorOk) { f.doctorOk = it }
            }
        }
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val g = LocalGlass.current
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange, colors = CheckboxDefaults.colors(checkedColor = g.accent, uncheckedColor = g.textSecondary, checkmarkColor = g.onAccent))
        Text(text, color = g.textPrimary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StepRoadmap(f: Form, account: AccountEntity) {
    val g = LocalGlass.current
    val age = f.age.toIntLoose()
    val p = f.toProfile(account.id, 2500)
    val r = RoadmapBuilder.build(p, age)
    val w = p.weightKg ?: 0.0
    val t = p.targetWeightKg ?: w

    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${f.name.trim()}, আপনার শুরু", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            Text("${"%.1f".format(w)} → ${"%.1f".format(t)} কেজি", color = g.textPrimary, style = MaterialTheme.typography.headlineMedium)
            Text("BMI ${"%.1f".format(r.bmi)} (${r.bmiLabel})", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            if (r.ratePerWeek > 0) {
                Text(
                    "নিরাপদ গতিতে সপ্তাহে প্রায় ${"%.2f".format(r.ratePerWeek)} কেজি, মানে প্রায় ${r.weeks} সপ্তাহ।",
                    color = g.accent, style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
    GlassSurface(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("প্রতিদিন", color = g.textPrimary, style = MaterialTheme.typography.titleMedium)
            if (r.calories > 0) Text("• প্রায় ${r.calories} কিলোক্যালরি খাবার", color = g.textSecondary)
            else Text("• ১৮ বছরের কম বয়সে ক্যালরির হিসাব দিচ্ছি না, পুষ্টিবিদের সাথে কথা বলুন", color = g.textSecondary)
            Text("• প্রায় ${r.proteinG} গ্রাম প্রোটিন (প্রতি বেলায় ডিম, মাছ, মুরগি বা ডালের একটা উৎস)", color = g.textSecondary)
            Text("• ${r.waterMl / 250} গ্লাস পানি", color = g.textSecondary)
            Text("• সপ্তাহে ${p.daysPerWeek} দিন, ${p.minutesPerSession} মিনিট করে ব্যায়াম", color = g.textSecondary)
        }
    }
    r.phases.forEach { ph ->
        GlassSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(ph.title, color = g.accent, style = MaterialTheme.typography.titleMedium)
                Text(ph.text, color = g.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (r.notes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            r.notes.forEach { Text("• $it", color = g.textSecondary, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    CheckRow("আমি বুঝেছি, এই app ডাক্তার বা পুষ্টিবিদের বিকল্প নয়। শরীর খারাপ লাগলে আমি থেমে যাব।", f.consent) { f.consent = it }
}
