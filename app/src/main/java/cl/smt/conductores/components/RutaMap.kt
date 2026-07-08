package cl.smt.conductores.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** Entrega que se dibuja en el mapa. Las coordenadas vienen de Direcciones API. */
data class RutaMapaEntrega(
    val pedidoId: Int,
    val numero: Int,
    val nombre: String,
    val lat: Double,
    val lng: Double,
    val colorHex: String = "#00C853"
)

data class RutaMapaInicio(
    val nombre: String,
    val lat: Double,
    val lng: Double
)

private const val OPEN_FREE_MAP_DARK_STYLE =
    "https://tiles.openfreemap.org/styles/dark"

private const val ROUTE_SOURCE = "smt-route-source"
private const val ROUTE_GLOW_LAYER = "smt-route-glow-layer"
private const val ROUTE_LAYER = "smt-route-layer"
private const val STOPS_SOURCE = "smt-stops-source"
private const val STOPS_ICON_LAYER = "smt-stops-icon-layer"
private const val LAB_SOURCE = "smt-lab-source"
private const val LAB_CIRCLE_LAYER = "smt-lab-circle-layer"
private const val LAB_TEXT_LAYER = "smt-lab-text-layer"

@Composable
fun RutaMap(
    entregas: List<RutaMapaEntrega>,
    laboratorio: RutaMapaInicio?,
    rutaOptimizada: Boolean,
    modifier: Modifier = Modifier,
    onEntregaClick: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnEntregaClick by rememberUpdatedState(onEntregaClick)

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleLoaded by remember { mutableStateOf(false) }
    var lastBoundsKey by remember { mutableStateOf("") }

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply {
            onCreate(Bundle())
            isNestedScrollingEnabled = true
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false

        fun destroyMapViewOnce() {
            if (!destroyed) {
                destroyed = true
                runCatching { mapView.onDestroy() }
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> destroyMapViewOnce()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mapView.onStart()
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            mapView.onResume()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                runCatching { mapView.onPause() }
                runCatching { mapView.onStop() }
            }
            destroyMapViewOnce()
        }
    }

    DisposableEffect(mapView) {
        mapView.getMapAsync { map ->
            mapLibreMap = map

            map.uiSettings.apply {
                isCompassEnabled = false
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isAttributionEnabled = true
                isLogoEnabled = true
            }

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val features = map.queryRenderedFeatures(
                    screenPoint,
                    STOPS_ICON_LAYER
                )

                val pedidoId = features.firstOrNull()
                    ?.takeIf { it.hasProperty("pedido_id") }
                    ?.getNumberProperty("pedido_id")
                    ?.toInt()

                if (pedidoId != null) {
                    latestOnEntregaClick(pedidoId)
                    true
                } else {
                    false
                }
            }

            map.setStyle(OPEN_FREE_MAP_DARK_STYLE) { style ->
                createSmtLayers(style)
                styleLoaded = true
            }
        }

        onDispose { }
    }

    val boundsKey = remember(entregas, laboratorio) {
        buildString {
            laboratorio?.let {
                append("L:${it.lat}:${it.lng};")
            }
            entregas
                .sortedBy { it.pedidoId }
                .forEach {
                    append("${it.pedidoId}:${it.lat}:${it.lng};")
                }
        }
    }

    LaunchedEffect(
        entregas,
        laboratorio,
        rutaOptimizada,
        mapLibreMap,
        styleLoaded
    ) {
        val map = mapLibreMap ?: return@LaunchedEffect
        if (!styleLoaded) return@LaunchedEffect

        updateSmtMap(
            map = map,
            entregas = entregas,
            laboratorio = laboratorio,
            rutaOptimizada = rutaOptimizada,
            fitCamera = boundsKey != lastBoundsKey
        )

        lastBoundsKey = boundsKey
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF071018)),
        border = BorderStroke(1.dp, Color(0xFF17422F))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF071018))
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            if (!styleLoaded) {
                CircularProgressIndicator(
                    color = Color(0xFF00C853),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (styleLoaded && entregas.isEmpty()) {
                Text(
                    "No hay entregas con coordenadas",
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color(0xDD020617),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                )
            }

            Text(
                if (rutaOptimizada) "Ruta optimizada" else "Orden actual",
                color = if (rutaOptimizada) Color(0xFF00E676) else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(
                        Color(0xDD020617),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

private fun createSmtLayers(style: Style) {
    if (style.getSource(ROUTE_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(
                ROUTE_SOURCE,
                FeatureCollection.fromFeatures(emptyList<Feature>())
            )
        )
    }

    if (style.getLayer(ROUTE_GLOW_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_GLOW_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#183B2B")),
                PropertyFactory.lineWidth(3.2f),
                PropertyFactory.lineOpacity(0.70f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    if (style.getLayer(ROUTE_LAYER) == null) {
        style.addLayer(
            LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                PropertyFactory.lineColor(AndroidColor.parseColor("#60706A")),
                PropertyFactory.lineWidth(1.8f),
                PropertyFactory.lineOpacity(0.98f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    if (style.getSource(STOPS_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(
                STOPS_SOURCE,
                FeatureCollection.fromFeatures(emptyList<Feature>())
            )
        )
    }

    if (style.getLayer(STOPS_ICON_LAYER) == null) {
        style.addLayer(
            SymbolLayer(STOPS_ICON_LAYER, STOPS_SOURCE).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true)
            )
        )
    }

    if (style.getSource(LAB_SOURCE) == null) {
        style.addSource(
            GeoJsonSource(
                LAB_SOURCE,
                FeatureCollection.fromFeatures(emptyList<Feature>())
            )
        )
    }

    if (style.getLayer(LAB_CIRCLE_LAYER) == null) {
        style.addLayer(
            CircleLayer(LAB_CIRCLE_LAYER, LAB_SOURCE).withProperties(
                PropertyFactory.circleColor(AndroidColor.parseColor("#087F43")),
                PropertyFactory.circleRadius(18f),
                PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                PropertyFactory.circleStrokeWidth(2.5f)
            )
        )
    }

    if (style.getLayer(LAB_TEXT_LAYER) == null) {
        style.addLayer(
            SymbolLayer(LAB_TEXT_LAYER, LAB_SOURCE).withProperties(
                PropertyFactory.textField("L"),
                PropertyFactory.textSize(13f),
                PropertyFactory.textColor(AndroidColor.WHITE),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        )
    }
}

private fun updateSmtMap(
    map: MapLibreMap,
    entregas: List<RutaMapaEntrega>,
    laboratorio: RutaMapaInicio?,
    rutaOptimizada: Boolean,
    fitCamera: Boolean
) {
    val style = map.style ?: return

    val stopFeatures = entregas.map { entrega ->
        val iconId = markerImageId(entrega.numero, entrega.colorHex)

        if (style.getImage(iconId) == null) {
            style.addImage(
                iconId,
                createNumberPinBitmap(
                    number = entrega.numero,
                    colorHex = entrega.colorHex
                )
            )
        }

        Feature.fromGeometry(
            Point.fromLngLat(entrega.lng, entrega.lat)
        ).apply {
            addNumberProperty("pedido_id", entrega.pedidoId)
            addStringProperty("label", entrega.numero.toString())
            addStringProperty("nombre", entrega.nombre)
            addStringProperty("color", entrega.colorHex)
            addStringProperty("icon", iconId)
        }
    }

    (style.getSource(STOPS_SOURCE) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(stopFeatures))

    val labFeatures = laboratorio?.let { punto ->
        listOf(
            Feature.fromGeometry(
                Point.fromLngLat(punto.lng, punto.lat)
            ).apply {
                addStringProperty("nombre", punto.nombre)
            }
        )
    }.orEmpty()

    (style.getSource(LAB_SOURCE) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(labFeatures))

    val routePoints = buildList {
        laboratorio?.let {
            add(Point.fromLngLat(it.lng, it.lat))
        }
        entregas.forEach {
            add(Point.fromLngLat(it.lng, it.lat))
        }
    }

    val routeFeatures = if (routePoints.size >= 2) {
        listOf(Feature.fromGeometry(LineString.fromLngLats(routePoints)))
    } else {
        emptyList()
    }

    (style.getSource(ROUTE_SOURCE) as? GeoJsonSource)
        ?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

    (style.getLayer(ROUTE_GLOW_LAYER) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(
            AndroidColor.parseColor(
                if (rutaOptimizada) "#0B6B38" else "#26352F"
            )
        ),
        PropertyFactory.lineWidth(if (rutaOptimizada) 4.0f else 2.8f),
        PropertyFactory.lineOpacity(if (rutaOptimizada) 0.58f else 0.38f)
    )

    (style.getLayer(ROUTE_LAYER) as? LineLayer)?.setProperties(
        PropertyFactory.lineColor(
            AndroidColor.parseColor(
                if (rutaOptimizada) "#00E676" else "#60706A"
            )
        ),
        PropertyFactory.lineWidth(if (rutaOptimizada) 1.9f else 1.3f)
    )

    if (!fitCamera) return

    val allCoordinates = buildList {
        laboratorio?.let { add(LatLng(it.lat, it.lng)) }
        entregas.forEach { add(LatLng(it.lat, it.lng)) }
    }

    when (allCoordinates.size) {
        0 -> Unit
        1 -> map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(allCoordinates.first(), 14.0)
        )
        else -> {
            val builder = LatLngBounds.Builder()
            allCoordinates.forEach { coordinate -> builder.include(coordinate) }
            map.animateCamera(
                CameraUpdateFactory.newLatLngBounds(builder.build(), 54)
            )
        }
    }
}

private fun markerImageId(number: Int, colorHex: String): String {
    val safeColor = colorHex
        .replace("#", "")
        .lowercase()

    return "smt-stop-$number-$safeColor"
}

/**
 * Crea un marcador tipo caja con número y una pequeña flecha inferior.
 * El extremo de la flecha queda exactamente sobre la coordenada de entrega.
 */
private fun createNumberPinBitmap(
    number: Int,
    colorHex: String
): Bitmap {
    val density = android.content.res.Resources
        .getSystem()
        .displayMetrics
        .density
        .coerceAtLeast(1f)

    fun dp(value: Float): Float = value * density

    val width = dp(34f).toInt().coerceAtLeast(34)
    val height = dp(43f).toInt().coerceAtLeast(43)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val left = dp(1.5f)
    val top = dp(1.5f)
    val right = width - dp(1.5f)
    val boxBottom = dp(31f)
    val tailBottom = height - dp(1.5f)
    val radius = dp(7f)
    val tailHalfWidth = dp(5.5f)
    val centerX = width / 2f

    val pinPath = Path().apply {
        moveTo(left + radius, top)
        lineTo(right - radius, top)
        quadTo(right, top, right, top + radius)
        lineTo(right, boxBottom - radius)
        quadTo(right, boxBottom, right - radius, boxBottom)
        lineTo(centerX + tailHalfWidth, boxBottom)
        lineTo(centerX, tailBottom)
        lineTo(centerX - tailHalfWidth, boxBottom)
        lineTo(left + radius, boxBottom)
        quadTo(left, boxBottom, left, boxBottom - radius)
        lineTo(left, top + radius)
        quadTo(left, top, left + radius, top)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = runCatching {
            AndroidColor.parseColor(colorHex)
        }.getOrDefault(AndroidColor.parseColor("#00C853"))
        setShadowLayer(dp(2f), 0f, dp(1.5f), AndroidColor.argb(140, 0, 0, 0))
    }

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = AndroidColor.WHITE
    }

    canvas.drawPath(pinPath, fillPaint)
    canvas.drawPath(pinPath, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textAlign = Paint.Align.CENTER
        textSize = when {
            number >= 100 -> dp(9f)
            number >= 10 -> dp(11f)
            else -> dp(13f)
        }
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    val textY = top + ((boxBottom - top) - (textPaint.ascent() + textPaint.descent())) / 2f
    canvas.drawText(number.toString(), centerX, textY, textPaint)

    return bitmap
}

