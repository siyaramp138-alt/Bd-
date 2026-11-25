package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Resolves the user's current foreground GPS/network location into a concise area/city label
 * (e.g., "Vijay Nagar, Indore") using Android [LocationManager] and [Geocoder].
 */
object DeviceLocationHelper {

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocationLabel(context: Context): Result<String> {
        if (!hasLocationPermission(context)) {
            return Result.failure(SecurityException("Location permission not granted."))
        }
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return Result.failure(IllegalStateException("Location service unavailable."))

        val location = resolveBestLocation(context, locationManager)
            ?: return Result.failure(
                IllegalStateException("Could not detect GPS fix. Turn on Location or enter area manually.")
            )

        val readable = reverseGeocode(context, location.latitude, location.longitude)
        val label = readable?.take(160)
            ?: String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude)
        return Result.success(label)
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveBestLocation(
        context: Context,
        locationManager: LocationManager
    ): Location? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        val enabledProvider = providers.firstOrNull { provider ->
            runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && enabledProvider != null) {
            val fresh = withTimeoutOrNull(4500L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    try {
                        locationManager.getCurrentLocation(
                            enabledProvider,
                            signal,
                            ContextCompat.getMainExecutor(context)
                        ) { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (fresh != null) return fresh
        }

        var bestLastKnown: Location? = null
        for (provider in providers) {
            val candidate = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (candidate != null) {
                if (bestLastKnown == null || candidate.accuracy < bestLastKnown.accuracy) {
                    bestLastKnown = candidate
                }
            }
        }
        return bestLastKnown
    }

    private suspend fun reverseGeocode(
        context: Context,
        latitude: Double,
        longitude: Double
    ): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address: Address? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(3500L) {
                suspendCancellableCoroutine<Address?> { cont ->
                    try {
                        geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                            if (cont.isActive) cont.resume(addresses.firstOrNull())
                        }
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
            }
        }

        if (address == null) return null
        val area = address.subLocality?.trim().orEmpty()
            .ifBlank { address.thoroughfare?.trim().orEmpty() }
            .ifBlank { address.featureName?.trim().orEmpty() }
        val city = address.locality?.trim().orEmpty()
            .ifBlank { address.subAdminArea?.trim().orEmpty() }
            .ifBlank { address.adminArea?.trim().orEmpty() }

        return listOf(area, city)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .joinToString(", ")
            .ifBlank { address.getAddressLine(0)?.trim().orEmpty() }
            .ifBlank { null }
    }
}
