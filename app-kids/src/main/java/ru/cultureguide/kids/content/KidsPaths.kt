package ru.cultureguide.kids.content

import android.content.Context
import org.json.JSONException
import org.json.JSONObject
import ru.cultureguide.navigation.GeoPoint
import java.io.IOException

/**
 * Пешеходные линии из `assets/kids/paths.json` (их строит `app-kids/tools/build_paths.py`).
 * Элемент `i` ведёт от точки `i` к точке `i + 1`. Пустой список — линий нет или они
 * устарели после правки маршрута; тогда карта соединяет точки прямыми.
 */
object KidsPathsLoader {
    private const val PATHS_ASSET = "kids/paths.json"

    fun load(context: Context, route: KidsRoute): List<WalkPath> {
        val text = try {
            context.assets.open(PATHS_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: IOException) {
            return emptyList()
        }
        return try {
            parse(text, route)
        } catch (_: JSONException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun parse(text: String, route: KidsRoute): List<WalkPath> {
        val json = JSONObject(text)
        val legs = json.getJSONArray("legs")
        if (json.optString("route") != route.id || legs.length() != route.stops.size - 1) return emptyList()
        return List(legs.length()) { i ->
            val points = legs.getJSONObject(i).getJSONArray("points")
            WalkPath(List(points.length()) { j ->
                val p = points.getJSONArray(j)
                GeoPoint(lat = p.getDouble(1), lon = p.getDouble(0))
            })
        }
    }
}
