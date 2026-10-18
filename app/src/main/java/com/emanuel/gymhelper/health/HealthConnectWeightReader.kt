package com.emanuel.gymhelper.health

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

class HealthConnectWeightReader(private val context: Context) {

    enum class Availability {
        AVAILABLE,
        UPDATE_REQUIRED,
        UNAVAILABLE
    }

    data class LatestWeight(
        val kilograms: Double,
        val measuredAt: Instant,
        val sourceLabel: String?
    )

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class)
    )

    fun availability(): Availability = when (
        HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE_NAME)
    ) {
        HealthConnectClient.SDK_AVAILABLE -> Availability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Availability.UPDATE_REQUIRED
        else -> Availability.UNAVAILABLE
    }

    suspend fun hasPermission(): Boolean {
        return client().permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun readLatest(): LatestWeight? {
        val endTime = Instant.now()
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.before(endTime),
                ascendingOrder = false,
                pageSize = 1
            )
        )
        val record = response.records.firstOrNull() ?: return null
        return LatestWeight(
            kilograms = record.weight.inKilograms,
            measuredAt = record.time,
            sourceLabel = applicationLabel(record.metadata.dataOrigin.packageName)
        )
    }

    private fun client(): HealthConnectClient {
        return HealthConnectClient.getOrCreate(context, HEALTH_CONNECT_PACKAGE_NAME)
    }

    private fun applicationLabel(packageName: String): String? {
        if (packageName.isBlank()) return null
        return try {
            val applicationInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    companion object {
        const val HEALTH_CONNECT_PACKAGE_NAME = "com.google.android.apps.healthdata"
    }
}
