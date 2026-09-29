package com.pace.tracker.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.pace.tracker.data.PaceRepository
import java.time.LocalDate
import java.time.ZoneId

enum class HealthConnectAvailability { AVAILABLE, NEEDS_INSTALL_OR_UPDATE, UNSUPPORTED }

/**
 * Reads daily step totals from Health Connect (foreground only). Everything degrades to manual
 * entry when Health Connect is missing or permission is denied.
 */
class HealthConnectManager(private val context: Context, private val repository: PaceRepository) {

    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.NEEDS_INSTALL_OR_UPDATE
        else -> HealthConnectAvailability.UNSUPPORTED
    }

    /** Looked up on each use so installing Health Connect later works without restarting the app. */
    private val client: HealthConnectClient?
        get() = if (availability() == HealthConnectAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        PermissionController.createRequestPermissionResultContract()

    suspend fun hasPermission(): Boolean = try {
        client?.permissionController?.getGrantedPermissions()?.containsAll(permissions) == true
    } catch (e: Exception) {
        false
    }

    suspend fun readSteps(day: LocalDate): Long? {
        val c = client ?: return null
        val zone = ZoneId.systemDefault()
        return try {
            val result = c.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(
                        day.atStartOfDay(zone).toInstant(),
                        day.plusDays(1).atStartOfDay(zone).toInstant(),
                    ),
                ),
            )
            result[StepsRecord.COUNT_TOTAL]
        } catch (e: Exception) {
            null
        }
    }

    /** Pulls the last [days] days of steps into the daily logs. Returns true if anything was read. */
    suspend fun sync(days: Int = 7): Boolean {
        if (!hasPermission()) return false
        var any = false
        val today = LocalDate.now()
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            val steps = readSteps(d) ?: continue
            repository.setHealthConnectSteps(d.toEpochDay(), steps.toInt())
            any = true
        }
        return any
    }

    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW).apply {
        setPackage("com.android.vending")
        data = Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding")
        putExtra("overlay", true)
        putExtra("callerId", context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
    }
}
