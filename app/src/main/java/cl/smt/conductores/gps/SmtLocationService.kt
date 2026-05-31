package cl.smt.conductores.gps

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import cl.smt.conductores.R
import cl.smt.conductores.data.SessionManager
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SmtLocationService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        Log.d("SMT_GPS", "SERVICE CREATED")

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    enviarUbicacion(location)
                }
            }
        }

        crearCanalNotificacion()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        Log.d("SMT_GPS", "SERVICE STARTED")

        startForeground(
            1001,
            crearNotificacion()
        )

        iniciarGps()

        return START_STICKY
    }

    private fun iniciarGps() {

        Log.d("SMT_GPS", "INICIANDO GPS")

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30000L
        )
            .setMinUpdateIntervalMillis(15000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        try {

            fusedClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            )

            Log.d("SMT_GPS", "LOCATION UPDATES ACTIVADAS")

        } catch (e: SecurityException) {

            Log.e(
                "SMT_GPS",
                "SIN PERMISOS GPS",
                e
            )

            stopSelf()
        }
    }

    private fun enviarUbicacion(
        location: Location
    ) {

        val user = SessionManager.getUser(this)

        val traccarId =
            user?.traccarId
                ?.trim()
                .orEmpty()

        Log.d(
            "SMT_GPS",
            "LOCATION lat=${location.latitude} lon=${location.longitude} traccarId=$traccarId"
        )

        if (traccarId.isBlank()) {

            Log.e(
                "SMT_GPS",
                "TRACCAR_ID VACIO"
            )

            return
        }

        scope.launch {

            try {

                val id =
                    URLEncoder.encode(
                        traccarId,
                        "UTF-8"
                    )

                val url = URL(
                    "http://gps.smtransportes.app:5055/" +
                            "?id=$id" +
                            "&lat=${location.latitude}" +
                            "&lon=${location.longitude}" +
                            "&speed=${location.speed}" +
                            "&bearing=${location.bearing}" +
                            "&altitude=${location.altitude}"
                )

                Log.d(
                    "SMT_GPS",
                    "ENVIANDO -> $url"
                )

                val conn =
                    url.openConnection() as HttpURLConnection

                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val code = conn.responseCode

                Log.d(
                    "SMT_GPS",
                    "TRACCAR HTTP=$code"
                )

                if (code in 200..299) {
                    conn.inputStream.close()
                } else {
                    conn.errorStream?.close()
                }

                conn.disconnect()

            } catch (e: Exception) {

                Log.e(
                    "SMT_GPS",
                    "ERROR ENVIANDO GPS",
                    e
                )
            }
        }
    }

    private fun crearCanalNotificacion() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                "smt_gps",
                "SMT GPS",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    private fun crearNotificacion(): Notification {

        return NotificationCompat.Builder(
            this,
            "smt_gps"
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("SMT GPS activo")
            .setContentText("Enviando ubicación durante la ruta")
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setPriority(
                NotificationCompat.PRIORITY_LOW
            )
            .build()
    }

    override fun onDestroy() {

        Log.d(
            "SMT_GPS",
            "SERVICE DESTROYED"
        )

        try {
            fusedClient.removeLocationUpdates(
                locationCallback
            )
        } catch (_: Exception) {
        }

        try {

            if (GpsController.estaActivo(this)) {

                Log.d(
                    "SMT_GPS",
                    "REINICIANDO SERVICIO"
                )

                val intent =
                    Intent(
                        this,
                        SmtLocationService::class.java
                    )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            }

        } catch (e: Exception) {

            Log.e(
                "SMT_GPS",
                "ERROR REINICIANDO",
                e
            )
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}