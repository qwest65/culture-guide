package ru.cultureguide

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.Animation
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session as RouteSession
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import com.yandex.runtime.image.ImageProvider
import com.yandex.runtime.network.NetworkError
import java.util.Locale
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

private enum class StopStatus { DONE, CURRENT, UPCOMING }

private data class RouteStop(
    val place: Place,
    val distance: String,
    val time: String,
    val status: StopStatus
)

class ModernMainActivity : ComponentActivity() {
    private lateinit var db: Db
    private lateinit var locationManager: LocationManager
    private var pedestrianRouter: PedestrianRouter? = null

    internal var mapView: MapView? = null
    internal var selectedPlace by mutableStateOf<Place?>(null)

    private var cities by mutableStateOf<List<City>>(emptyList())
    private var places by mutableStateOf<List<Place>>(emptyList())
    private var routes by mutableStateOf<List<RouteLine>>(emptyList())
    private var selectedRoute by mutableStateOf<RouteLine?>(null)
    private var routePlaces by mutableStateOf<List<Place>>(emptyList())
    private var activeStopIndex by mutableStateOf(0)
    private var searchQuery by mutableStateOf("")
    private var statusText by mutableStateOf("")
    private var cityId = 0L
    private var mapInitialized = false
    private var lastLocation: Location? = null
    private var userLocation by mutableStateOf<Location?>(null)
    private var userPlacemark: PlacemarkMapObject? = null
    private val routeSessions = mutableListOf<RouteSession>()
    private var routeDistanceMeters = 0.0
    private var builtDistanceMeters by mutableStateOf(0.0)
    private var routeBuilt by mutableStateOf(false)
    private var routeBuildGeneration = 0
    private val routePolylines = mutableListOf<com.yandex.mapkit.map.PolylineMapObject>()
    private var renderedPlacesKey = ""

    private val placeTapListener = MapObjectTapListener { obj, _ ->
        selectedPlace = obj.userData as? Place
        true
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            userLocation = location

            val activePlace = routePlaces.getOrNull(activeStopIndex)
            if (routeBuilt && activePlace != null) {
                val distanceToStop = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    activePlace.lat,
                    activePlace.lon,
                    distanceToStop
                )

                if (distanceToStop[0] <= 50f) {
                    statusText = "Вы достигли: " + activePlace.name
                    if (activeStopIndex < routePlaces.lastIndex) {
                        activeStopIndex += 1
                    }
                }

                mapView?.let { view ->
                    val map = view.mapWindow.map
                    val current = map.cameraPosition
                    map.move(
                        CameraPosition(
                            Point(location.latitude, location.longitude),
                            maxOf(current.zoom, 16f),
                            current.azimuth,
                            current.tilt
                        ),
                        Animation(Animation.Type.SMOOTH, 0.6f)
                    )
                }
            }

            mapView?.let { showUserLocation(it, location.latitude, location.longitude) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MapKitFactory.initialize(this)
        db = Db(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        pedestrianRouter = TransportFactory.getInstance().createPedestrianRouter()
        loadCatalog()

        setContent {
            CultureGuideTheme {
                RouteScreen(
                    activity = this,
                    cities = cities,
                    routes = routes,
                    selectedRoute = selectedRoute,
                    places = places,
                    routePlaces = routePlaces,
                    activeStopIndex = activeStopIndex,
                    userLocation = userLocation,
                    builtDistanceMeters = builtDistanceMeters,
                    routeBuilt = routeBuilt,
                    searchQuery = searchQuery,
                    statusText = statusText,
                    onSearchQueryChange = { searchQuery = it },
                    onSelectCity = { city ->
                        cityId = city.id
                        selectedRoute = null
                        routePlaces = emptyList()
                        activeStopIndex = 0
                        mapInitialized = false
                        loadCatalog()
                    },
                    onSelectRoute = { route ->
                        clearBuiltRoute(clearStops = false)
                        selectedRoute = route
                        routePlaces = db.routePlaces(route, places)
                        activeStopIndex = 0
                        statusText = "Выбрано: ${route.name}"
                        moveCameraToRoute()
                    },
                    onTogglePlace = { place -> togglePlaceInRoute(place) },
                    onMoveStop = { from, to -> moveStop(from, to) },
                    onClearRoute = { clearBuiltRoute(clearStops = true) },
                    onSelectStop = { index ->
                        activeStopIndex = index
                        routePlaces.getOrNull(index)?.let { moveCamera(it.lat, it.lon, 16f) }
                    },
                    onSearch = {
                        statusText = if (searchQuery.isBlank()) {
                            "Показаны все объекты"
                        } else {
                            "Найдено: " + filterPlaces(searchQuery).size
                        }
                    },
                    onBuildRoute = ::buildWalkingRoute,
                    onLocate = {
                        requestLocation()
                        lastLocation?.let { moveCamera(it.latitude, it.longitude, 16f) }
                    },
                    onZoomIn = { zoomBy(1f) },
                    onZoomOut = { zoomBy(-1f) }
                )
            }
        }
        requestLocation()
    }

    private fun loadCatalog() {
        cities = db.cities()
        if (cities.isEmpty()) return
        val city = cities.firstOrNull { it.id == cityId } ?: cities.first()
        cityId = city.id
        places = db.places(cityId)
        routes = db.routes(cityId)
        selectedRoute = selectedRoute?.let { old -> routes.firstOrNull { it.id == old.id } }
        routePlaces = if (selectedRoute != null) db.routePlaces(selectedRoute!!, places) else emptyList()
        builtDistanceMeters = 0.0
        routeBuilt = false
        statusText = ""
    }

    private fun filterPlaces(query: String): List<Place> {
        val q = query.trim().lowercase(Locale.getDefault())
        return places.filter {
            q.isEmpty() ||
                it.name.lowercase(Locale.getDefault()).contains(q) ||
                it.category.lowercase(Locale.getDefault()).contains(q) ||
                it.address.lowercase(Locale.getDefault()).contains(q)
        }
    }

    internal fun renderMap(view: MapView) {
        val map = view.mapWindow.map
        val renderKey = buildString {
            append(cityId).append('|')
            append(searchQuery).append('|')
            append(places.joinToString(",") { it.id.toString() })
        }
        if (renderedPlacesKey == renderKey) {
            lastLocation?.let { showUserLocation(view, it.latitude, it.longitude) }
            return
        }

        map.mapObjects.clear()
        userPlacemark = null
        renderedPlacesKey = renderKey

        val icon = ImageProvider.fromResource(this, R.drawable.ic_place_pin)
        val visible = if (searchQuery.isBlank()) places else filterPlaces(searchQuery)

        visible.forEach { place ->
            map.mapObjects.addPlacemark().apply {
                geometry = Point(place.lat, place.lon)
                setIcon(icon, IconStyle().apply {
                    anchor = android.graphics.PointF(0.5f, 1f)
                    scale = 0.72f
                    zIndex = 30f
                })
                userData = place
                addTapListener(java.lang.ref.WeakReference(placeTapListener))
            }
        }

        lastLocation?.let { showUserLocation(view, it.latitude, it.longitude) }

        if (!mapInitialized) {
            val first = routePlaces.firstOrNull()
            if (first != null) {
                map.move(CameraPosition(Point(first.lat, first.lon), 14.5f, 0f, 0f))
            } else {
                cities.firstOrNull { it.id == cityId }?.let {
                    map.move(CameraPosition(Point(it.lat, it.lon), 14.5f, 0f, 0f))
                }
            }
            mapInitialized = true
        }
    }

    private fun showUserLocation(view: MapView, lat: Double, lon: Double) {
        if (userPlacemark == null) {
            userPlacemark = view.mapWindow.map.mapObjects.addPlacemark().apply {
                setIcon(
                    ImageProvider.fromResource(this@ModernMainActivity, R.drawable.ic_user_pin),
                    IconStyle().apply {
                        anchor = android.graphics.PointF(0.5f, 0.5f)
                        scale = 0.8f
                        zIndex = 60f
                    }
                )
            }
        }
        userPlacemark?.geometry = Point(lat, lon)
    }

    internal fun moveCamera(lat: Double, lon: Double, zoom: Float) {
        mapView?.mapWindow?.map?.move(CameraPosition(Point(lat, lon), zoom, 0f, 0f))
    }

    private fun moveCameraToRoute() {
        routePlaces.firstOrNull()?.let { moveCamera(it.lat, it.lon, 14.8f) }
    }

    private fun moveCameraToGeometry(geometry: Polyline) {
        mapView?.mapWindow?.map?.let { map ->
            val cameraPosition = map.cameraPosition(Geometry.fromPolyline(geometry))
            map.move(
                cameraPosition,
                Animation(Animation.Type.LINEAR, 0.8f)
            )
        }
    }

    internal fun openInYandexMaps(place: Place) {
        val uri = Uri.parse(
            "https://yandex.ru/maps/?rtext=~" + place.lat + "," + place.lon + "&rtt=auto"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun zoomBy(delta: Float) {
        mapView?.let { view ->
            val current = view.mapWindow.map.cameraPosition
            view.mapWindow.map.move(
                CameraPosition(
                    current.target,
                    (current.zoom + delta).coerceIn(3f, 20f),
                    current.azimuth,
                    current.tilt
                )
            )
        }
    }

    private fun buildWalkingRoute() {
        routeDistanceMeters = 0.0
        if (routePlaces.size < 2) {
            statusText = "Выберите минимум две точки посещения"
            return
        }
        clearBuiltRoute(clearStops = false)
        val generation = routeBuildGeneration
        val requestPoints = routePlaces.map {
            RequestPoint(Point(it.lat, it.lon), RequestPointType.WAYPOINT, null, null, null)
        }
        statusText = "Строю пешеходный маршрут… 0/${requestPoints.size - 1}"

        fun requestLeg(index: Int) {
            if (generation != routeBuildGeneration) return
            if (index >= requestPoints.lastIndex) {
                builtDistanceMeters = routeDistanceMeters
                routeBuilt = true
                statusText = "Маршрут построен · %.2f км · %d остановок".format(
                    Locale.US, routeDistanceMeters / 1000.0, routePlaces.size
                )
                fitBuiltRoute()
                return
            }
            val router = pedestrianRouter ?: run {
                statusText = "Пешеходный маршрутизатор недоступен"
                return
            }
            val listener = object : RouteSession.RouteListener {
                override fun onMasstransitRoutes(result: MutableList<com.yandex.mapkit.transport.masstransit.Route>) {
                    if (generation != routeBuildGeneration) return
                    if (result.isEmpty()) {
                        statusText = "Не удалось построить участок ${index + 1}"
                        return
                    }
                    val geometry = result[0].geometry
                    val map = mapView?.mapWindow?.map ?: return
                    val line = map.mapObjects.addPolyline(geometry).apply {
                        setStrokeColor(AndroidColor.rgb(39, 112, 239))
                        setStrokeWidth(8f)
                        zIndex = 6f
                    }
                    routePolylines += line
                    val legDistanceMeters = result[0].metadata.weight.walkingDistance.value
                    routeDistanceMeters += legDistanceMeters
                    statusText = "Строю пешеходный маршрут… ${index + 1}/${requestPoints.size - 1} · %.2f км".format(
                        Locale.US, routeDistanceMeters / 1000.0
                    )
                    requestLeg(index + 1)
                }
                override fun onMasstransitRoutesError(error: Error) {
                    if (generation != routeBuildGeneration) return
                    statusText = if (error is NetworkError) "Нет сети для построения маршрута" else "Не удалось построить маршрут"
                    clearBuiltRoute(clearStops = false)
                }
            }
            routeSessions += router.requestRoutes(
                listOf(requestPoints[index], requestPoints[index + 1]),
                TimeOptions(),
                RouteOptions(FitnessOptions(false, false)),
                listener
            )
        }
        requestLeg(0)
    }

    private fun fitBuiltRoute() {
        val points = routePolylines.flatMap { it.geometry.points }
        if (points.size >= 2) moveCameraToGeometry(Polyline(points))
    }


    internal fun clearBuiltRoute(clearStops: Boolean) {
        val map = mapView?.mapWindow?.map
        if (map != null) {
            routePolylines.toList().forEach { polyline ->
                map.mapObjects.remove(polyline)
            }
        }
        routePolylines.toList().forEach { polyline ->
            map?.mapObjects?.remove(polyline)
        }
        routePolylines.clear()
        routeSessions.forEach { it.cancel() }
        routeSessions.clear()
        routeDistanceMeters = 0.0
        builtDistanceMeters = 0.0
        routeBuilt = false
        routeBuildGeneration++

        if (clearStops) {
            selectedRoute = null
            routePlaces = emptyList()
            activeStopIndex = 0
            statusText = "Маршрут очищен"
        } else if (routePlaces.isEmpty()) {
            statusText = ""
        }
    }

    internal fun togglePlaceInRoute(place: Place) {
        val currentPlaces = routePlaces.toMutableList()
        val wasAdded = currentPlaces.none { it.id == place.id }
        if (wasAdded) {
            currentPlaces.add(place)
            statusText = "Добавлено в маршрут"
        } else {
            currentPlaces.removeAll { it.id == place.id }
            statusText = "Удалено из маршрута"
        }
        clearBuiltRoute(clearStops = false)
        routePlaces = currentPlaces
        activeStopIndex = 0
        if (currentPlaces.size >= 2) {
            buildWalkingRoute()
        }
    }

    internal fun moveStop(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in routePlaces.indices || toIndex !in routePlaces.indices) return
        val currentPlaces = routePlaces.toMutableList()
        val item = currentPlaces.removeAt(fromIndex)
        currentPlaces.add(toIndex, item)
        clearBuiltRoute(clearStops = false)
        routePlaces = currentPlaces
        activeStopIndex = 0
    }

    private fun requestLocation() {
        if (
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                1001
            )
            return
        }

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: return

        try {
            locationManager.requestLocationUpdates(provider, 5000L, 5f, locationListener)
            locationManager.getLastKnownLocation(provider)?.let {
                lastLocation = it
                mapView?.let { view -> showUserLocation(view, it.latitude, it.longitude) }
            }
        } catch (_: SecurityException) {}
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView?.onStart()
    }

    override fun onStop() {
        mapView?.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroy() {
        routeSessions.forEach { it.cancel() }
        locationManager.removeUpdates(locationListener)
        mapView?.destroy()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteScreen(
    activity: ModernMainActivity,
    cities: List<City>,
    routes: List<RouteLine>,
    places: List<Place>,
    selectedRoute: RouteLine?,
    routePlaces: List<Place>,
    activeStopIndex: Int,
    userLocation: Location?,
    builtDistanceMeters: Double,
    routeBuilt: Boolean,
    searchQuery: String,
    statusText: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectCity: (City) -> Unit,
    onSelectRoute: (RouteLine) -> Unit,
    onSelectStop: (Int) -> Unit,
    onTogglePlace: (Place) -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onClearRoute: () -> Unit,
    onSearch: () -> Unit,
    onBuildRoute: () -> Unit,
    onLocate: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit
) {
    var cityDialog by remember { mutableStateOf(false) }
    var routeDialog by remember { mutableStateOf(false) }
    var searchDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 350.dp,
        sheetContainerColor = Color.White,
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = Color(0xFFF1F3F6),
        sheetDragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFBEC2C9))
            )
        },
        sheetContent = {
            RouteSheetContent(
                stops = buildRouteStops(routePlaces, activeStopIndex, userLocation),
                selectedRoute = selectedRoute,
                builtDistanceMeters = builtDistanceMeters,
                routeBuilt = routeBuilt,
                onSelectStop = onSelectStop,
                onMoveStop = onMoveStop,
                onBuildRoute = onBuildRoute,
                onClearRoute = onClearRoute
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    MapView(context).also { view ->
                        activity.mapView = view
                        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            view.onStart()
                        }
                    }
                },
                update = { view ->
                    activity.mapView = view
                    activity.renderMap(view)
                }
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 5.dp
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Культурный маршрут",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                (cities.firstOrNull()?.name ?: "Город") + ", " +
                                    (cities.firstOrNull()?.country ?: "Россия"),
                                fontSize = 13.sp,
                                color = Color(0xFF6D7380)
                            )
                        }
                        Surface(
                            modifier = Modifier.clickable { routeDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5F6F8)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Карта", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(3.dp))
                                Text("▾", fontSize = 13.sp, color = Color(0xFF6D7380))
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { searchDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFF5F6F8)
                        ) {
                            Row(
                                Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    null,
                                    Modifier.size(18.dp),
                                    tint = Color(0xFF737985)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (searchQuery.isBlank()) "Найти объект" else searchQuery,
                                    fontSize = 13.sp,
                                    color = Color(0xFF737985),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, "Меню")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Выбрать город") },
                                    onClick = {
                                        menuExpanded = false
                                        cityDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Моё положение") },
                                    onClick = {
                                        menuExpanded = false
                                        onLocate()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Сбросить поиск") },
                                    onClick = {
                                        menuExpanded = false
                                        onSearchQueryChange("")
                                        onSearch()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 176.dp, end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControlButton(Icons.Default.Add, "Увеличить", onZoomIn)
                MapControlButton(Icons.Default.Close, "Уменьшить", onZoomOut)
                MapControlButton(Icons.Default.LocationOn, "Моё положение", onLocate)
            }

            if (statusText.isNotBlank()) {
                Surface(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 176.dp, start = 20.dp, end = 76.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.94f),
                    shadowElevation = 3.dp
                ) {
                    Text(
                        statusText,
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        fontSize = 11.sp,
                        color = Color(0xFF5F6672),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (cityDialog) {
        AlertDialog(
            onDismissRequest = { cityDialog = false },
            title = { Text("Город") },
            text = {
                Column {
                    cities.forEach { city ->
                        Text(
                            city.name + ", " + city.country,
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    cityDialog = false
                                    onSelectCity(city)
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { cityDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (routeDialog) {
        AlertDialog(
            onDismissRequest = { routeDialog = false },
            title = { Text("Культурные маршруты") },
            text = {
                Column {
                    routes.forEach { route ->
                        Text(
                            route.name + " · " + route.placeIds.size + " объектов",
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    routeDialog = false
                                    onSelectRoute(route)
                                }
                                .padding(vertical = 12.dp),
                            fontSize = 16.sp
                        )
                    }
                    if (routes.isEmpty()) {
                        Text("В этом городе пока нет маршрутов.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { routeDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (searchDialog) {
        var draft by remember(searchQuery) { mutableStateOf(searchQuery) }
        AlertDialog(
            onDismissRequest = { searchDialog = false },
            title = { Text("Найти объект") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text("Название, категория или адрес") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSearchQueryChange(draft)
                        onSearch()
                        searchDialog = false
                    }
                ) { Text("Найти") }
            },
            dismissButton = {
                TextButton(onClick = { searchDialog = false }) { Text("Отмена") }
            }
        )
    }

    activity.selectedPlace?.let { place: Place ->
        val inRoute = routePlaces.any { it.id == place.id }
        AlertDialog(
            onDismissRequest = { activity.selectedPlace = null },
            title = { Text(place.name) },
            text = {
                Column {
                    Text(place.category, fontWeight = FontWeight.SemiBold, color = Color(0xFF5F6672))
                    Spacer(Modifier.height(12.dp))
                    Text("Описание", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(place.description.ifBlank { "Описание пока не добавлено в каталог." }, fontSize = 15.sp, lineHeight = 21.sp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = { activity.openInYandexMaps(place) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocationOn, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Повести по навигатору")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Адрес", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(place.address)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onTogglePlace(place)
                    activity.selectedPlace = null
                }) { Text(if (inRoute) "Убрать из маршрута" else "Добавить в маршрут") }
            },
            dismissButton = {
                TextButton(onClick = {
                    activity.selectedPlace = null
                    activity.moveCamera(place.lat, place.lon, 16.5f)
                }) { Text("Показать") }
            }
        )
    }
}

@Composable
private fun RouteSheetContent(
    stops: List<RouteStop>,
    selectedRoute: RouteLine?,
    builtDistanceMeters: Double,
    routeBuilt: Boolean,
    onSelectStop: (Int) -> Unit,
    onMoveStop: (Int, Int) -> Unit,
    onBuildRoute: () -> Unit,
    onClearRoute: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Маршрут: " + stops.size + " объектов",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        null,
                        Modifier.size(16.dp),
                        tint = Color(0xFF737985)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (routeBuilt) {
                            "%.2f км · %d мин".format(
                                Locale.US,
                                builtDistanceMeters / 1000.0,
                                maxOf(1, (builtDistanceMeters / 75.0).roundToInt())
                            )
                        } else {
                            "Расстояние будет рассчитано по пешеходному маршруту"
                        },
                        color = Color(0xFF737985),
                        fontSize = 13.sp
                    )
                }
            }
            Icon(
                Icons.Default.ArrowForward,
                null,
                tint = Color(0xFF737985)
            )
        }

        Divider(color = Color(0xFFE3E5E9))

        if (stops.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                stops.forEachIndexed { index, stop ->
                    StatusIcon(stop.status, index + 1)
                    if (index < stops.lastIndex) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(2.dp)
                                .background(
                                    if (stops[index + 1].status == StopStatus.UPCOMING) {
                                        Color(0xFFD7DADF)
                                    } else {
                                        Color(0xFF49A96A)
                                    }
                                )
                        )
                    }
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(stops) { index, stop ->
                val current = stop.status == StopStatus.CURRENT
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (current) Color(0xFFE3F2FD) else Color.Transparent)
                        .clickable { onSelectStop(index) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusIcon(stop.status, index + 1)
                    Spacer(Modifier.width(12.dp))
                    Icon(
                        if (stop.place.category.contains("музе", true)) {
                            Icons.Default.LocationOn
                        } else {
                            Icons.Default.LocationOn
                        },
                        null,
                        Modifier.size(22.dp),
                        tint = if (current) Color(0xFF1976D2) else Color(0xFF4C525E)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stop.place.name,
                        Modifier.weight(1f),
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                        color = if (current) Color(0xFF151922) else Color(0xFF3F444E),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(stop.distance, fontSize = 11.sp, color = Color(0xFF7A808B))
                        Text(stop.time, fontSize = 11.sp, color = Color(0xFF7A808B))
                        Row {
                            IconButton(onClick = { onMoveStop(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowUp, "Выше", Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onMoveStop(index, index + 1) }, enabled = index < stops.lastIndex, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.KeyboardArrowDown, "Ниже", Modifier.size(18.dp))
                            }
                        }
                    }                }
            }
        }

        Button(
            onClick = onBuildRoute,
            enabled = stops.size >= 2,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
            shape = RoundedCornerShape(12.dp)
        ) { Text("Построить маршрут", fontSize = 16.sp) }

        OutlinedButton(
            onClick = onClearRoute,
            enabled = stops.isNotEmpty() || routeBuilt,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).height(44.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Clear, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (routeBuilt) "Отменить / сбросить" else "Очистить точки")
        }
    }
}

@Composable
private fun StatusIcon(status: StopStatus, number: Int) {
    val bg = when (status) {
        StopStatus.DONE -> Color(0xFF4CAF50)
        StopStatus.CURRENT -> Color(0xFF1976D2)
        StopStatus.UPCOMING -> Color(0xFFE0E0E0)
    }
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            StopStatus.DONE -> Icon(
                Icons.Default.Check,
                "Пройдено",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            StopStatus.CURRENT -> Icon(
                Icons.Default.ArrowForward,
                "Текущий",
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
            StopStatus.UPCOMING -> Text(
                number.toString(),
                color = Color(0xFF6D7380),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MapControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
            Icon(icon, description, tint = Color(0xFF343943))
        }
    }
}

@Composable
private fun CultureGuideTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1976D2),
            background = Color(0xFFF2F4F7),
            surface = Color.White
        ),
        content = content
    )
}

private fun buildRouteStops(
    places: List<Place>,
    activeIndex: Int,
    userLocation: Location?
): List<RouteStop> =
    places.mapIndexed { index, place ->
        val distanceMeters = if (index == activeIndex && userLocation != null) {
            val result = FloatArray(1)
            Location.distanceBetween(
                userLocation.latitude,
                userLocation.longitude,
                place.lat,
                place.lon,
                result
            )
            result[0].toDouble()
        } else {
            null
        }

        RouteStop(
            place = place,
            distance = when {
                index == 0 && index != activeIndex -> "Старт"
                distanceMeters != null -> if (distanceMeters < 1000.0) {
                    "%.0f м".format(Locale.US, distanceMeters)
                } else {
                    "%.1f км".format(Locale.US, distanceMeters / 1000.0)
                }
                else -> "—"
            },
            time = if (distanceMeters != null) {
                (maxOf(1, (distanceMeters / 75.0).roundToInt())).toString() + " мин"
            } else {
                "—"
            },
            status = when {
                index < activeIndex -> StopStatus.DONE
                index == activeIndex -> StopStatus.CURRENT
                else -> StopStatus.UPCOMING
            }
        )
    }
