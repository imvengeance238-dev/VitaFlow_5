package app.vitaflow.com.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AccountEntity::class,
        TaskEntity::class,
        WorkoutPlanEntity::class,
        WorkoutExerciseEntity::class,
        MealEntity::class,
        WaterLogEntity::class,
        ChangeRecordEntity::class,
        ChatMessageEntity::class,
        ProfileEntity::class,
        WeightLogEntity::class,
        WorkoutSessionEntity::class,
        SetLogEntity::class,
        ConversationEntity::class,
        MemoryFactEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VitaDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun taskDao(): TaskDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun mealDao(): MealDao
    abstract fun waterDao(): WaterDao
    abstract fun changeDao(): ChangeDao
    abstract fun chatDao(): ChatDao
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun sessionDao(): SessionDao
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        fun build(context: Context): VitaDatabase =
            Room.databaseBuilder(context.applicationContext, VitaDatabase::class.java, "vitaflow.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/** Adds the detailed profile columns. Existing profiles are marked "not set up yet", so the questions are asked once. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE profiles ADD COLUMN ageYears INTEGER",
            "ALTER TABLE profiles ADD COLUMN targetWeightKg REAL",
            "ALTER TABLE profiles ADD COLUMN targetWeeks INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE profiles ADD COLUMN goalNote TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN daysPerWeek INTEGER NOT NULL DEFAULT 3",
            "ALTER TABLE profiles ADD COLUMN minutesPerSession INTEGER NOT NULL DEFAULT 30",
            "ALTER TABLE profiles ADD COLUMN preferredTime TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN place TEXT NOT NULL DEFAULT 'HOME'",
            "ALTER TABLE profiles ADD COLUMN equipmentList TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN activityLevel TEXT NOT NULL DEFAULT 'LIGHT'",
            "ALTER TABLE profiles ADD COLUMN sleepHours INTEGER NOT NULL DEFAULT 7",
            "ALTER TABLE profiles ADD COLUMN waterGlasses INTEGER NOT NULL DEFAULT 6",
            "ALTER TABLE profiles ADD COLUMN smoker INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE profiles ADD COLUMN mealsPerDay INTEGER NOT NULL DEFAULT 3",
            "ALTER TABLE profiles ADD COLUMN riceMeals INTEGER NOT NULL DEFAULT 2",
            "ALTER TABLE profiles ADD COLUMN foodsEaten TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN junkLevel TEXT NOT NULL DEFAULT 'MEDIUM'",
            "ALTER TABLE profiles ADD COLUMN teaCups INTEGER NOT NULL DEFAULT 2",
            "ALTER TABLE profiles ADD COLUMN foodNotes TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN healthFlags TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN medications TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE profiles ADD COLUMN needsDoctor INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE profiles ADD COLUMN onboardingDone INTEGER NOT NULL DEFAULT 0"
        ).forEach { db.execSQL(it) }
    }
}
