package com.wellsey.bants.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the phone is right now (needs location permission). */
object Here {
    data class Fix(val lat: Double, val lng: Double, val accuracy: Double)

    @SuppressLint("MissingPermission")
    suspend fun now(context: Context): Fix = suspendCancellableCoroutine { cont ->
        val cancel = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.token)
            .addOnSuccessListener { loc ->
                if (loc == null) cont.resumeWithException(IllegalStateException("Couldn't find you. Is location turned on?"))
                else cont.resume(Fix(loc.latitude, loc.longitude, loc.accuracy.toDouble()))
            }
            .addOnFailureListener { cont.resumeWithException(it) }
        cont.invokeOnCancellation { cancel.cancel() }
    }
}
