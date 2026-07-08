package cl.smt.conductores.routing

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Punto geográfico mínimo para calcular el orden de una ruta.
 */
data class RutaGeoPoint(
    val id: Int,
    val lat: Double,
    val lng: Double
)

/**
 * Resultado de la optimización local.
 *
 * Las distancias son geográficas entre coordenadas. No representan todavía
 * kilómetros reales por calles ni tráfico.
 */
data class RutaOptimizationResult(
    val orderedIds: List<Int>,
    val originalDistanceKm: Double,
    val optimizedDistanceKm: Double,
    val exact: Boolean
) {
    val savedDistanceKm: Double
        get() = (originalDistanceKm - optimizedDistanceKm).coerceAtLeast(0.0)

    val improvementPercent: Double
        get() = if (originalDistanceKm > 0.0) {
            (savedDistanceKm / originalDistanceKm) * 100.0
        } else {
            0.0
        }
}

/**
 * Optimizador local y gratuito.
 *
 * - Hasta 12 entregas: encuentra el orden geográfico mínimo exacto desde el
 *   punto inicial, visitando cada entrega una sola vez y terminando en cualquier
 *   entrega.
 * - Sobre 12 entregas: usa múltiples rutas candidatas, vecino más cercano,
 *   2-opt y recolocación de paradas.
 *
 * El orden de entrada se usa únicamente como referencia para comparar la mejora.
 * No condiciona el resultado optimizado.
 */
object RutaOptimizer {

    private const val EXACT_LIMIT = 12
    private const val EPSILON = 1e-9

    fun optimizeShortestPath(
        points: List<RutaGeoPoint>,
        startLat: Double,
        startLng: Double
    ): RutaOptimizationResult {
        val validPoints = points
            .filter {
                it.lat.isFinite() &&
                        it.lng.isFinite() &&
                        it.lat in -90.0..90.0 &&
                        it.lng in -180.0..180.0
            }
            .distinctBy { it.id }

        if (validPoints.isEmpty()) {
            return RutaOptimizationResult(
                orderedIds = emptyList(),
                originalDistanceKm = 0.0,
                optimizedDistanceKm = 0.0,
                exact = true
            )
        }

        if (validPoints.size == 1) {
            val distance = haversineKm(
                startLat,
                startLng,
                validPoints.first().lat,
                validPoints.first().lng
            )

            return RutaOptimizationResult(
                orderedIds = listOf(validPoints.first().id),
                originalDistanceKm = distance,
                optimizedDistanceKm = distance,
                exact = true
            )
        }

        require(startLat.isFinite() && startLat in -90.0..90.0) {
            "Latitud inicial inválida"
        }
        require(startLng.isFinite() && startLng in -180.0..180.0) {
            "Longitud inicial inválida"
        }

        val startDistances = DoubleArray(validPoints.size) { index ->
            haversineKm(
                startLat,
                startLng,
                validPoints[index].lat,
                validPoints[index].lng
            )
        }

        val distances = Array(validPoints.size) { from ->
            DoubleArray(validPoints.size) { to ->
                if (from == to) {
                    0.0
                } else {
                    haversineKm(
                        validPoints[from].lat,
                        validPoints[from].lng,
                        validPoints[to].lat,
                        validPoints[to].lng
                    )
                }
            }
        }

        val originalRoute = validPoints.indices.toList()
        val originalDistance = routeDistance(
            route = originalRoute,
            startDistances = startDistances,
            distances = distances
        )

        val optimizedRoute = if (validPoints.size <= EXACT_LIMIT) {
            exactOpenPath(
                pointCount = validPoints.size,
                startDistances = startDistances,
                distances = distances
            )
        } else {
            heuristicOpenPath(
                pointCount = validPoints.size,
                startDistances = startDistances,
                distances = distances,
                originalRoute = originalRoute
            )
        }

        val optimizedDistance = routeDistance(
            route = optimizedRoute,
            startDistances = startDistances,
            distances = distances
        )

        return RutaOptimizationResult(
            orderedIds = optimizedRoute.map { validPoints[it].id },
            originalDistanceKm = originalDistance,
            optimizedDistanceKm = optimizedDistance,
            exact = validPoints.size <= EXACT_LIMIT
        )
    }

    /**
     * Held-Karp para una ruta abierta:
     * inicio fijo -> visitar todos -> terminar en cualquier punto.
     */
    private fun exactOpenPath(
        pointCount: Int,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): List<Int> {
        val stateCount = 1 shl pointCount
        val infinity = Double.POSITIVE_INFINITY
        val dp = Array(stateCount) { DoubleArray(pointCount) { infinity } }
        val parent = Array(stateCount) { IntArray(pointCount) { -1 } }

        for (last in 0 until pointCount) {
            dp[1 shl last][last] = startDistances[last]
        }

        for (mask in 1 until stateCount) {
            for (last in 0 until pointCount) {
                val lastBit = 1 shl last
                if (mask and lastBit == 0) continue

                val previousMask = mask xor lastBit
                if (previousMask == 0) continue

                var previous = 0
                while (previous < pointCount) {
                    val previousBit = 1 shl previous
                    if (previousMask and previousBit != 0) {
                        val previousCost = dp[previousMask][previous]
                        if (previousCost.isFinite()) {
                            val candidate = previousCost + distances[previous][last]
                            if (candidate + EPSILON < dp[mask][last]) {
                                dp[mask][last] = candidate
                                parent[mask][last] = previous
                            }
                        }
                    }
                    previous++
                }
            }
        }

        val fullMask = stateCount - 1
        var bestLast = 0
        var bestCost = dp[fullMask][0]

        for (last in 1 until pointCount) {
            if (dp[fullMask][last] < bestCost) {
                bestCost = dp[fullMask][last]
                bestLast = last
            }
        }

        val reversedRoute = ArrayList<Int>(pointCount)
        var mask = fullMask
        var last = bestLast

        while (last >= 0) {
            reversedRoute.add(last)
            val previous = parent[mask][last]
            mask = mask xor (1 shl last)
            last = previous
        }

        reversedRoute.reverse()
        return reversedRoute
    }

    private fun heuristicOpenPath(
        pointCount: Int,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>,
        originalRoute: List<Int>
    ): List<Int> {
        val candidateRoutes = mutableListOf<List<Int>>()

        // La ruta manual también entra como candidata y se mejora.
        candidateRoutes.add(improveRoute(originalRoute, startDistances, distances))

        // Vecino más cercano puro desde el laboratorio.
        candidateRoutes.add(
            improveRoute(
                greedyRoute(
                    pointCount = pointCount,
                    forcedFirst = null,
                    startDistances = startDistances,
                    distances = distances
                ),
                startDistances,
                distances
            )
        )

        // Probar distintos primeros destinos evita quedar atrapado en una mala
        // decisión inicial del vecino más cercano.
        val seeds = if (pointCount <= 32) {
            (0 until pointCount).toList()
        } else {
            (0 until pointCount)
                .sortedBy { startDistances[it] }
                .let { sorted ->
                    val near = sorted.take(12)
                    val far = sorted.takeLast(4)
                    (near + far).distinct()
                }
        }

        for (first in seeds) {
            val greedy = greedyRoute(
                pointCount = pointCount,
                forcedFirst = first,
                startDistances = startDistances,
                distances = distances
            )

            candidateRoutes.add(
                improveRoute(greedy, startDistances, distances)
            )
        }

        return candidateRoutes.minByOrNull {
            routeDistance(it, startDistances, distances)
        } ?: originalRoute
    }

    private fun greedyRoute(
        pointCount: Int,
        forcedFirst: Int?,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): List<Int> {
        val remaining = (0 until pointCount).toMutableSet()
        val route = ArrayList<Int>(pointCount)

        var current: Int? = null

        if (forcedFirst != null && remaining.remove(forcedFirst)) {
            route.add(forcedFirst)
            current = forcedFirst
        }

        while (remaining.isNotEmpty()) {
            val currentIndex = current
            val next = if (currentIndex == null) {
                remaining.minByOrNull { startDistances[it] }
            } else {
                remaining.minByOrNull { distances[currentIndex][it] }
            } ?: break

            remaining.remove(next)
            route.add(next)
            current = next
        }

        return route
    }

    private fun improveRoute(
        initialRoute: List<Int>,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): List<Int> {
        var route = initialRoute.toMutableList()
        var iteration = 0
        var changed = true

        while (changed && iteration < 60) {
            changed = false
            iteration++

            if (applyBestTwoOpt(route, startDistances, distances)) {
                changed = true
            }

            if (applyBestRelocation(route, startDistances, distances)) {
                changed = true
            }
        }

        return route
    }

    /**
     * Invierte un segmento cuando reduce la longitud total del recorrido.
     */
    private fun applyBestTwoOpt(
        route: MutableList<Int>,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): Boolean {
        if (route.size < 3) return false

        var bestGain = 0.0
        var bestStart = -1
        var bestEnd = -1

        for (segmentStart in route.indices) {
            for (segmentEnd in segmentStart + 1 until route.size) {
                val beforeSegment = if (segmentStart == 0) null else route[segmentStart - 1]
                val first = route[segmentStart]
                val last = route[segmentEnd]
                val afterSegment = route.getOrNull(segmentEnd + 1)

                val oldLeft = if (beforeSegment == null) {
                    startDistances[first]
                } else {
                    distances[beforeSegment][first]
                }

                val newLeft = if (beforeSegment == null) {
                    startDistances[last]
                } else {
                    distances[beforeSegment][last]
                }

                val oldRight = if (afterSegment == null) {
                    0.0
                } else {
                    distances[last][afterSegment]
                }

                val newRight = if (afterSegment == null) {
                    0.0
                } else {
                    distances[first][afterSegment]
                }

                val gain = (oldLeft + oldRight) - (newLeft + newRight)

                if (gain > bestGain + EPSILON) {
                    bestGain = gain
                    bestStart = segmentStart
                    bestEnd = segmentEnd
                }
            }
        }

        if (bestStart < 0) return false

        route.subList(bestStart, bestEnd + 1).reverse()
        return true
    }

    /**
     * Mueve una parada completa a otra posición cuando mejora el recorrido.
     */
    private fun applyBestRelocation(
        route: MutableList<Int>,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): Boolean {
        if (route.size < 3) return false

        val currentDistance = routeDistance(route, startDistances, distances)
        var bestDistance = currentDistance
        var bestRoute: List<Int>? = null

        for (fromIndex in route.indices) {
            val without = route.toMutableList()
            val moved = without.removeAt(fromIndex)

            for (toIndex in 0..without.size) {
                // Reinsertarlo en su misma posición no cambia nada.
                if (toIndex == fromIndex) continue

                val candidate = without.toMutableList()
                candidate.add(toIndex.coerceAtMost(candidate.size), moved)

                val candidateDistance = routeDistance(
                    candidate,
                    startDistances,
                    distances
                )

                if (candidateDistance + EPSILON < bestDistance) {
                    bestDistance = candidateDistance
                    bestRoute = candidate
                }
            }
        }

        val improved = bestRoute ?: return false
        route.clear()
        route.addAll(improved)
        return true
    }

    private fun routeDistance(
        route: List<Int>,
        startDistances: DoubleArray,
        distances: Array<DoubleArray>
    ): Double {
        if (route.isEmpty()) return 0.0

        var total = startDistances[route.first()]

        for (index in 1 until route.size) {
            total += distances[route[index - 1]][route[index]]
        }

        return total
    }

    private fun haversineKm(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val earthRadiusKm = 6371.0088
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLng / 2).pow(2)

        return 2 * earthRadiusKm * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
