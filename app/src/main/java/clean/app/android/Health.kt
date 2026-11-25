package clean.app.android

import clean.core.*
import clean.core.time.*
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime

/**
 * Health Connect, Android's on-the-phone store that health apps share. Clean writes a finished workout to it as a strength-training
 * session (its name, times and a summary of the sets), a weigh-in as a weight and a body fat figure as a body fat, and, when Settings
 * says so, reads today's step count back for Home. It reads nothing else, and nothing here goes online.
 *
 * This file is only the calls. What to send, what to take away, the batches, the retries and the words for a failure are decided in
 * core (`HealthSync.kt`) and tested there; the model (`AppModel.healthCatchUp`) keeps the list of what was sent.
 */
object Health {
    private val EXERCISE: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    private val WEIGHT: String = HealthPermission.getWritePermission(WeightRecord::class)
    private val BODY_FAT: String = HealthPermission.getWritePermission(BodyFatRecord::class)

    /** What switching the writing on asks for. Nothing is read back: Clean keeps its own list of what it sent (round 27). */
    val PERMISSIONS = setOf(EXERCISE, WEIGHT, BODY_FAT)

    /** The one thing read from Health Connect: the day's steps, asked for only when its own switch in Settings is turned on. */
    val STEPS_PERMISSION: String = HealthPermission.getReadPermission(StepsRecord::class)

    /** Set by tests only: Robolectric has no Health Connect, and the Settings pictures need its controls on show. */
    @androidx.annotation.VisibleForTesting var availableForTest: Boolean? = null

    fun available(context: Context): Boolean = availableForTest ?: (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)

    private fun permissionOf(kind: HealthKind) = when (kind) { HealthKind.WORKOUT -> EXERCISE; HealthKind.WEIGHT -> WEIGHT; HealthKind.BODY_FAT -> BODY_FAT }

    private fun typeOf(kind: HealthKind): KClass<out Record> = when (kind) {
        HealthKind.WORKOUT -> ExerciseSessionRecord::class
        HealthKind.WEIGHT -> WeightRecord::class
        HealthKind.BODY_FAT -> BodyFatRecord::class
    }

    /** Whether at least one kind of writing is allowed (asked once per run, not once per record). */
    suspend fun allowed(context: Context): Boolean = try {
        available(context) && HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().any { it in PERMISSIONS }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    /** Today's steps from every app that wrote them, or null when Health Connect is not on the phone, the reading is not allowed, it fails, or there are none. */
    suspend fun stepsToday(context: Context): Long? = try {
        if (!available(context)) null else {
            val client = HealthConnectClient.getOrCreate(context)
            if (STEPS_PERMISSION !in client.permissionController.getGrantedPermissions()) null else {
                val start = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(start, java.time.Instant.now())))[StepsRecord.COUNT_TOTAL]?.takeIf { it > 0 }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** The phone is the device that took the record (Clean is the only thing that writes it, by hand, on this phone). */
    private fun metadata(id: String) = Metadata.manualEntry(clientRecordId = id, clientRecordVersion = System.currentTimeMillis(), device = Device(type = Device.TYPE_PHONE))

    private fun toRecord(r: HealthRecord): Record {
        val zone = java.time.ZoneId.systemDefault()
        val start = parseStrongDate(r.date)!!.toJavaLocalDateTime().atZone(zone).toInstant()
        val offset = zone.rules.getOffset(start)
        return when (r) {
            is HealthWorkout -> {
                val end = start.plusSeconds(r.seconds)
                ExerciseSessionRecord(
                    startTime = start, startZoneOffset = offset, endTime = end, endZoneOffset = zone.rules.getOffset(end),
                    exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, title = r.title, notes = r.notes.ifEmpty { null },
                    metadata = metadata(r.id),
                )
            }
            is HealthWeight -> WeightRecord(time = start, zoneOffset = offset, weight = Mass.kilograms(r.kg), metadata = metadata(r.id))
            is HealthBodyFat -> BodyFatRecord(time = start, zoneOffset = offset, percentage = Percentage(r.percent), metadata = metadata(r.id))
        }
    }

    /**
     * Does [request]: the records are written in chunks of at most a thousand, the ids taken away likewise, the grant checked once for
     * the whole run. Kinds whose writing is not allowed are left alone and named in the report. Never throws except for cancellation.
     */
    suspend fun apply(context: Context, request: HealthRequest): HealthReport {
        if (!available(context)) return HealthReport(failed = request.write.size + request.remove.size, failure = HealthFailure(HealthFailureKind.OTHER, "Health Connect is not available"))
        val client: HealthConnectClient
        val granted: Set<String>
        try {
            client = HealthConnectClient.getOrCreate(context)
            granted = client.permissionController.getGrantedPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return HealthReport(failed = request.write.size + request.remove.size, failure = classifyHealthFailure(e::class.java.name, e.message))
        }
        val written = ArrayList<String>()
        val removed = ArrayList<String>()
        var failed = 0
        var failure: HealthFailure? = null
        val notAllowed = LinkedHashSet<HealthKind>()
        for (kind in HealthKind.entries) {
            val write = request.write.filter { it.kind == kind }
            val remove = request.remove.filter { healthKindOf(it) == kind }
            if (write.isEmpty() && remove.isEmpty()) continue
            if (permissionOf(kind) !in granted) { notAllowed += kind; continue }
            val put = runHealthChunks(write, pause = { delay(it) }) { chunk -> client.insertRecords(chunk.map(::toRecord)) }
            written += put.done.map { it.id }
            failed += put.failed
            if (failure == null) failure = put.failure
            // A permission or a rate limit that stopped the writing would stop the removing too.
            if (put.failure?.kind == HealthFailureKind.PERMISSION || put.failure?.kind == HealthFailureKind.RATE_LIMIT) { failed += remove.size; continue }
            val gone = runHealthChunks(remove, pause = { delay(it) }) { chunk -> client.deleteRecords(typeOf(kind), emptyList(), chunk) }
            removed += gone.done
            failed += gone.failed
            if (failure == null) failure = gone.failure
        }
        return HealthReport(written, removed, failed, failure, notAllowed)
    }

    /**
     * Takes away every record Clean wrote (any profile): a delete by time range, which Health Connect limits to the calling app's own
     * data, so nothing another app wrote is touched. Only the kinds whose writing is still allowed. Null when it went through.
     */
    suspend fun removeAll(context: Context): HealthFailure? {
        if (!available(context)) return null
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val everything = TimeRangeFilter.between(java.time.Instant.EPOCH, java.time.Instant.ofEpochSecond(4_102_444_800L))
            for (kind in HealthKind.entries) if (permissionOf(kind) in granted) client.deleteRecords(typeOf(kind), everything)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classifyHealthFailure(e::class.java.name, e.message)
        }
    }
}
