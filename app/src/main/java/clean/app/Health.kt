package clean.app

import clean.core.parseStrongDate
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import java.time.ZoneId

/**
 * Health Connect, Android's on-the-phone store that health apps share. Clean only ever writes to
 * it: a finished workout as a strength-training session, and a weigh-in as a weight. It reads
 * nothing back, and nothing here goes online. A write says whether it went through; it does not
 * when Health Connect is not on the phone or the user has not allowed the writing.
 */
object Health {
    val PERMISSIONS = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class), HealthPermission.getWritePermission(WeightRecord::class))

    fun available(context: Context): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    private suspend fun client(context: Context): HealthConnectClient? = runCatching {
        if (!available(context)) return null
        HealthConnectClient.getOrCreate(context).takeIf { it.permissionController.getGrantedPermissions().containsAll(PERMISSIONS) }
    }.getOrNull()

    suspend fun allowed(context: Context): Boolean = client(context) != null

    // A record is known by the profile and the moment it was logged, so sending it again replaces it instead of doubling it.
    private fun workoutId(profile: Long, date: String) = "clean:workout:$profile:$date"
    private fun weightId(profile: Long, date: String) = "clean:weight:$profile:$date"

    /** [date] is the workout's start as stored (`yyyy-MM-dd HH:mm:ss`, the phone's own time). */
    suspend fun writeWorkout(context: Context, profile: Long, date: String, name: String, seconds: Long): Boolean {
        val client = client(context) ?: return false
        return runCatching {
            val zone = ZoneId.systemDefault()
            val start = parseStrongDate(date)!!.atZone(zone).toInstant()
            // A session has to end after it starts.
            val end = start.plusSeconds(seconds.coerceAtLeast(60))
            client.insertRecords(listOf(
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = zone.rules.getOffset(start), endTime = end, endZoneOffset = zone.rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, title = name,
                    metadata = Metadata.manualEntry(clientRecordId = workoutId(profile, date), clientRecordVersion = System.currentTimeMillis()),
                ),
            ))
        }.isSuccess
    }

    suspend fun removeWorkout(context: Context, profile: Long, date: String) {
        val client = client(context) ?: return
        runCatching { client.deleteRecords(ExerciseSessionRecord::class, emptyList(), listOf(workoutId(profile, date))) }
    }

    suspend fun removeWeight(context: Context, profile: Long, date: String) {
        val client = client(context) ?: return
        runCatching { client.deleteRecords(WeightRecord::class, emptyList(), listOf(weightId(profile, date))) }
    }

    suspend fun writeWeight(context: Context, profile: Long, date: String, kg: Double): Boolean {
        val client = client(context) ?: return false
        return runCatching {
            val zone = ZoneId.systemDefault()
            val time = parseStrongDate(date)!!.atZone(zone).toInstant()
            client.insertRecords(listOf(
                WeightRecord(
                    time = time, zoneOffset = zone.rules.getOffset(time), weight = Mass.kilograms(kg),
                    metadata = Metadata.manualEntry(clientRecordId = weightId(profile, date), clientRecordVersion = System.currentTimeMillis()),
                ),
            ))
        }.isSuccess
    }
}
