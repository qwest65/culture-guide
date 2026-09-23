package ru.cultureguide

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.RequestPoint
import com.yandex.mapkit.RequestPointType
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.Session as RouteSession
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError
import com.yandex.runtime.image.ImageProvider
import java.util.Locale
import java.lang.ref.WeakReference

class ModernMainActivity : Activity() {
    private lateinit var db: Db
    private lateinit var mapView: MapView
    private lateinit var locationManager: LocationManager
    private lateinit var cityButton: TextView
    private lateinit var searchBox: EditText
    private lateinit var sheet: LinearLayout
    private lateinit var sheetTitle: TextView
    private lateinit var sheetSubtitle: TextView
    private lateinit var routeButton: Button
    private lateinit var statusText: TextView

    private var cityId = 0L
    private var cities: List<City> = emptyList()
    private var places: List<Place> = emptyList()
    private var routes: List<RouteLine> = emptyList()
    private var selectedRoute: RouteLine? = null
    private var routePlaces: List<Place> = emptyList()
    private var lastLocation: Location? = null
    private var userPlacemark: PlacemarkMapObject? = null
    private val routeObjects = mutableListOf<MapObject>()
    private val routeSessions = mutableListOf<RouteSession>()
    private var pedestrianRouter: PedestrianRouter? = null
    private var routeDistanceMeters = 0.0

    private val placeTapListener = MapObjectTapListener { obj, _ ->
        val place = obj.userData as? Place ?: return@MapObjectTapListener false
        showPlace(place)
        true
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocation = location
            showUserLocation(location.latitude, location.longitude, false)
            statusText.text = "Ваше положение обновлено"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)
        db = Db(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        pedestrianRouter = TransportFactory.getInstance().createPedestrianRouter()
        buildModernUi()
        loadCatalog()
        requestLocation()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeWidthDp: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), stroke)
        }

    private fun makeIconButton(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 19f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(40, 44, 52))
        background = rounded(Color.WHITE, Color.rgb(220, 223, 229), 14, 1)
        elevation = dp(3).toFloat()
    }

    private fun makeModeButton(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(55, 59, 68))
        background = rounded(Color.rgb(246, 247, 249), Color.TRANSPARENT, 13, 0)
    }

    private fun buildModernUi() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        mapView = MapView(this)
        root.addView(mapView, FrameLayout.LayoutParams(-1, -1))

        val topCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.WHITE, Color.TRANSPARENT, 20, 0)
            elevation = dp(8).toFloat()
        }
        root.addView(topCard, FrameLayout.LayoutParams(-1, dp(142)).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            topMargin = dp(30)
        })

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "Культурный маршрут"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(28, 31, 38))
        }
        titleRow.addView(title, LinearLayout.LayoutParams(0, dp(34), 1f))
        val menu = makeIconButton("⋯")
        menu.setOnClickListener { showCatalogMenu() }
        titleRow.addView(menu, LinearLayout.LayoutParams(dp(44), dp(44)))
        topCard.addView(titleRow)

        cityButton = TextView(this).apply {
            text = "Выбор города"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(Color.rgb(45, 49, 57))
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.rgb(247, 248, 250), Color.TRANSPARENT, 14, 0)
            setOnClickListener { chooseCity() }
        }
        topCard.addView(cityButton, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(4) })

        val searchRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        searchBox = EditText(this).apply {
            hint = "Найти место или памятник"
            textSize = 15f
            setSingleLine(true)
            setTextColor(Color.rgb(35, 38, 45))
            setHintTextColor(Color.rgb(125, 130, 139))
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(Color.WHITE, Color.rgb(220, 223, 229), 16, 1)
        }
        searchRow.addView(searchBox, LinearLayout.LayoutParams(0, dp(42), 1f))
        val filter = makeIconButton("☰")
        filter.setOnClickListener { chooseCategory() }
        searchRow.addView(filter, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(8) })
        topCard.addView(searchRow, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(6) })

        searchBox.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderMap()
                renderSheet()
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })

        val locate = makeIconButton("⌖")
        root.addView(locate, FrameLayout.LayoutParams(dp(48), dp(48)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dp(188)
            rightMargin = dp(18)
        })
        locate.setOnClickListener {
            requestLocation()
            lastLocation?.let { moveCamera(it.latitude, it.longitude, 16f) }
        }

        sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(9), dp(18), dp(12))
            background = rounded(Color.WHITE, Color.TRANSPARENT, 26, 0)
            elevation = dp(16).toFloat()
        }
        root.addView(sheet, FrameLayout.LayoutParams(-1, dp(326)).apply { gravity = Gravity.BOTTOM })

        val handle = View(this).apply { setBackgroundColor(Color.rgb(195, 198, 204)) }
        sheet.addView(handle, LinearLayout.LayoutParams(dp(42), dp(4)).apply { gravity = Gravity.CENTER_HORIZONTAL })

        sheetTitle = TextView(this).apply {
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(28, 31, 38))
        }
        sheet.addView(sheetTitle, LinearLayout.LayoutParams(-1, dp(32)).apply { topMargin = dp(8) })

        sheetSubtitle = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(105, 110, 120))
        }
        sheet.addView(sheetSubtitle, LinearLayout.LayoutParams(-1, dp(24)))

        routeButton = TextView(this).apply {
            text = "Выбрать культурный маршрут"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(49, 94, 251), Color.TRANSPARENT, 16, 0)
            elevation = dp(3).toFloat()
            setOnClickListener {
                if (selectedRoute == null) showRouteChooser() else buildWalkingRoute()
            }
        }
        sheet.addView(routeButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) })

        val modes = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val mapBtn = makeModeButton("Карта")
        val schemeBtn = makeModeButton("Схема")
        val routesBtn = makeModeButton("Маршруты")
        modes.addView(mapBtn, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(4) })
        modes.addView(schemeBtn, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(4) })
        modes.addView(routesBtn, LinearLayout.LayoutParams(0, dp(38), 1f))
        sheet.addView(modes, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(5) })
        mapBtn.setOnClickListener { showMapMode() }
        schemeBtn.setOnClickListener { showSchemeMode() }
        routesBtn.setOnClickListener { showRouteChooser() }

        statusText = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.rgb(120, 124, 132))
            gravity = Gravity.CENTER_VERTICAL
        }
        sheet.addView(statusText, LinearLayout.LayoutParams(-1, dp(20)))

        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, insets ->
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                val topLp = topCard.layoutParams as FrameLayout.LayoutParams
                topLp.topMargin = bars.top + dp(8)
                topCard.layoutParams = topLp
                val sheetLp = sheet.layoutParams as FrameLayout.LayoutParams
                sheetLp.bottomMargin = bars.bottom
                sheet.layoutParams = sheetLp
            }
            insets
        }
        root.requestApplyInsets()
    }
    private fun loadCatalog() {
        cities = db.cities()
        if (cities.isEmpty()) return
        val city = cities.firstOrNull { it.id == cityId } ?: cities.first()
        cityId = city.id
        cityButton.text = "⌖  ${city.name}, ${city.country}"
        places = db.places(cityId)
        routes = db.routes(cityId)
        renderMap()
        renderSheet()
    }

    private fun chooseCity() {
        val labels = cities.map { "${it.name}, ${it.country}" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Город").setItems(labels) { _, which ->
            cityId = cities[which].id
            selectedRoute = null
            routePlaces = emptyList()
            loadCatalog()
        }.show()
    }

    private fun chooseCategory() {
        val categories = db.categories(cityId).toTypedArray()
        AlertDialog.Builder(this).setTitle("Фильтр объектов").setItems(categories) { _, which ->
            statusText.text = "${filterPlaces(searchBox.text.toString(), categories[which]).size} объектов · ${categories[which]}"
        }.show()
    }

    private fun filterPlaces(query: String, category: String? = null): List<Place> {
        val q = query.trim().lowercase(Locale.getDefault())
        return places.filter {
            (category == null || category == "Все" || it.category == category) &&
                (q.isEmpty() || it.name.lowercase(Locale.getDefault()).contains(q) ||
                    it.category.lowercase(Locale.getDefault()).contains(q) ||
                    it.address.lowercase(Locale.getDefault()).contains(q))
        }
    }

    private fun renderMap() {
        val objects = mapView.mapWindow.map.mapObjects
        objects.clear()
        routeObjects.clear()
        userPlacemark = null
        val visible = filterPlaces(searchBox.text.toString())
        val pin = ImageProvider.fromResource(this, R.drawable.ic_map_pin)

        visible.forEachIndexed { index, place ->
            objects.addPlacemark().apply {
                geometry = Point(place.lat, place.lon)
                setIcon(pin)
                setIconStyle(IconStyle().apply {
                    anchor = android.graphics.PointF(0.5f, 1f)
                    scale = 1.35f
                    zIndex = 30f
                    tappableArea = android.graphics.Rect(-14, -14, 14, 14)
                })
                setText("K{index + 1}")
                setTextStyle(TextStyle().apply {
                    size = 12f
                    color = Color.WHITE
                    outlineColor = Color.rgb(49, 94, 251)
                    placement = TextStyle.Placement.CENTER
                })
                userData = place
                addTapListener(WeakReference(placeTapListener))
            }
        }

        cities.firstOrNull { it.id == cityId }?.let { moveCamera(it.lat, it.lon, 14.8f) }
        lastLocation?.let { showUserLocation(it.latitude, it.longitude, false) }
        drawSelectedRoute()
        statusText.text = "K{visible.size} объектов на карте"
    }
    private fun renderSheet() {
        val visible = filterPlaces(searchBox.text.toString())
        val city = cities.firstOrNull { it.id == cityId }

        sheetTitle.text = if (selectedRoute == null) "Культурные места" else selectedRoute!!.name
        sheetSubtitle.text = if (selectedRoute == null) {
            "K{city?.name ?: ""} · K{visible.size} объектов на карте"
        } else {
            "K{routePlaces.size} остановок · K{selectedRoute!!.description}"
        }
        routeButton.text = if (selectedRoute == null) "Выбрать культурный маршрут" else "Построить пеший маршрут"
        statusText.text = if (selectedRoute == null) {
            "K{visible.size} объектов · нажмите маркер для подробностей"
        } else {
            "K{routePlaces.size} остановок · маршрут готов к построению"
        }
    }
    private fun showRouteChooser() {
        if (routes.isEmpty()) {
            Toast.makeText(this, "В этом городе пока нет культурных маршрутов", Toast.LENGTH_LONG).show()
            return
        }
        val labels = routes.map { "${it.name} · ${it.placeIds.size} объектов" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Культурные маршруты").setItems(labels) { _, which ->
            selectedRoute = routes[which]
            routePlaces = db.routePlaces(selectedRoute!!, places)
            drawSelectedRoute()
            renderSheet()
            moveCameraToRoute()
        }.setNegativeButton("Отмена", null).show()
    }

    private fun moveCameraToRoute() {
        routePlaces.firstOrNull()?.let { moveCamera(it.lat, it.lon, 14.8f) }
    }

    private fun drawSelectedRoute() {
        val objects = mapView.mapWindow.map.mapObjects
        routeObjects.forEach { objects.remove(it) }
        routeObjects.clear()
        val line = selectedRoute ?: return
        if (routePlaces.size < 2) return
        routeObjects += objects.addPolyline(Polyline(routePlaces.map { Point(it.lat, it.lon) })).apply {
            setStrokeColor(Color.rgb(49, 94, 251))
            setStrokeWidth(7f)
            zIndex = 5f
        }
    }

    private fun buildWalkingRoute() {
        val selected = selectedRoute ?: run { showRouteChooser(); return }
        if (routePlaces.size < 2) return
        routeSessions.forEach { it.cancel() }
        routeSessions.clear()
        routeObjects.forEach { mapView.mapWindow.map.mapObjects.remove(it) }
        routeObjects.clear()
        routeDistanceMeters = 0.0

        val points = mutableListOf<Point>()
        lastLocation?.let { points += Point(it.latitude, it.longitude) }
        points += routePlaces.map { Point(it.lat, it.lon) }
        statusText.text = "Строю пешеходный маршрут…"

        fun leg(index: Int) {
            if (index >= points.size - 1) {
                statusText.text = "Маршрут построен · %.2f км".format(Locale.US, routeDistanceMeters / 1000.0)
                return
            }
            val router = pedestrianRouter ?: return
            val request = listOf(
                RequestPoint(points[index], RequestPointType.WAYPOINT, null, null, null),
                RequestPoint(points[index + 1], RequestPointType.WAYPOINT, null, null, null)
            )
            val listener = object : RouteSession.RouteListener {
                override fun onMasstransitRoutes(result: MutableList<com.yandex.mapkit.transport.masstransit.Route>) {
                    if (result.isEmpty()) {
                        statusText.text = "Не удалось построить участок ${index + 1}"
                        return
                    }
                    routeObjects += mapView.mapWindow.map.mapObjects.addPolyline(result[0].geometry).apply {
                        setStrokeColor(Color.rgb(49, 94, 251))
                        setStrokeWidth(8f)
                        zIndex = 6f
                    }
                    routeDistanceMeters += polylineDistance(result[0].geometry)
                    statusText.text = "Маршрут · участок ${index + 1}/${points.size - 1} · %.2f км".format(Locale.US, routeDistanceMeters / 1000.0)
                    leg(index + 1)
                }

                override fun onMasstransitRoutesError(error: Error) {
                    statusText.text = when (error) {
                        is NetworkError -> "Нет сети для построения маршрута"
                        else -> "Не удалось построить маршрут"
                    }
                }
            }
            routeSessions += router.requestRoutes(request, TimeOptions(), RouteOptions(FitnessOptions(false, false)), listener)
        }
        leg(0)
    }

    private fun polylineDistance(polyline: Polyline): Double {
        var meters = 0.0
        val p = polyline.points
        for (i in 1 until p.size) {
            val result = FloatArray(1)
            Location.distanceBetween(p[i - 1].latitude, p[i - 1].longitude, p[i].latitude, p[i].longitude, result)
            meters += result[0]
        }
        return meters
    }

    private fun showPlace(place: Place) {
        val lines = db.routesForPlace(cityId, place.id)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 4, 24, 4)
        }
        box.addView(TextView(this).apply {
            text = "${place.category}\n\n${place.description}\n\nАдрес: ${place.address}\n\n${if (lines.isEmpty()) "Маршруты: —" else "Маршруты: " + lines.joinToString(", ") { it.name }}"
            textSize = 16f
            setTextColor(Color.rgb(45, 48, 55))
        })
        AlertDialog.Builder(this).setTitle(place.name).setView(box)
            .setPositiveButton("Показать на карте") { _, _ -> moveCamera(place.lat, place.lon, 16.5f) }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun showSchemeMode() {
        AlertDialog.Builder(this).setTitle("Схема маршрутов")
            .setItems(routes.map { "${it.name} · ${it.placeIds.size} объектов" }.toTypedArray()) { _, which ->
                selectedRoute = routes[which]
                routePlaces = db.routePlaces(selectedRoute!!, places)
                drawSelectedRoute()
                renderSheet()
            }.setNegativeButton("Закрыть", null).show()
    }

    private fun showMapMode() {
        selectedRoute = null
        routePlaces = emptyList()
        renderMap()
        renderSheet()
    }

    private fun showCatalogMenu() {
        AlertDialog.Builder(this).setTitle("Культурный маршрут")
            .setItems(arrayOf("Обновить каталог", "Моё положение", "О приложении")) { _, which ->
                when (which) {
                    0 -> loadCatalog()
                    1 -> {
                        requestLocation()
                        lastLocation?.let { moveCamera(it.latitude, it.longitude, 16f) }
                    }
                    2 -> AlertDialog.Builder(this).setTitle("Культурный маршрут")
                        .setMessage("Карта города, культурные объекты и тематические пешие маршруты.")
                        .setPositiveButton("Закрыть", null).show()
                }
            }.show()
    }

    private fun requestLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
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
                showUserLocation(it.latitude, it.longitude, false)
            }
        } catch (_: SecurityException) {}
    }

    private fun showUserLocation(lat: Double, lon: Double, center: Boolean) {
        val objects = mapView.mapWindow.map.mapObjects
        if (userPlacemark == null) {
            userPlacemark = objects.addPlacemark().apply {
                setIcon(ImageProvider.fromResource(this@ModernMainActivity, R.drawable.ic_user_pin))
                setIconStyle(IconStyle().apply {
                    anchor = android.graphics.PointF(0.5f, 0.5f)
                    scale = 1.0f
                    zIndex = 50f
                })
            }
        }
        userPlacemark?.geometry = Point(lat, lon)
        if (center) moveCamera(lat, lon, 16f)
    }

    private fun moveCamera(lat: Double, lon: Double, zoom: Float) {
        mapView.mapWindow.map.move(CameraPosition(Point(lat, lon), zoom, 0f, 0f))
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }

    override fun onDestroy() {
        routeSessions.forEach { it.cancel() }
        locationManager.removeUpdates(locationListener)
        super.onDestroy()
    }
}