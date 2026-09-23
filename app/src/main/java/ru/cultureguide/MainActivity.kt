package ru.cultureguide

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject
import android.view.View
import android.widget.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.MapObject
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.GeoObjectCollection
import com.yandex.mapkit.search.Response
import com.yandex.mapkit.search.SearchFactory
import com.yandex.mapkit.search.SearchManagerType
import com.yandex.mapkit.search.SearchOptions
import com.yandex.mapkit.search.SearchManager
import com.yandex.mapkit.search.Session as SearchSession
import com.yandex.mapkit.transport.TransportFactory
import com.yandex.mapkit.transport.masstransit.PedestrianRouter
import com.yandex.mapkit.transport.masstransit.RouteOptions
import com.yandex.mapkit.transport.masstransit.FitnessOptions
import com.yandex.mapkit.transport.masstransit.TimeOptions
import com.yandex.mapkit.transport.masstransit.Session as RouteSession
import com.yandex.runtime.Error
import com.yandex.runtime.network.NetworkError
import com.yandex.runtime.image.ImageProvider
import kotlin.math.*
import java.lang.ref.WeakReference

data class City(val id:Long,val name:String,val country:String,val lat:Double,val lon:Double)
data class Place(val id:Long,val name:String,val category:String,val description:String,val address:String,val lat:Double,val lon:Double,val sourceUrl:String,val imageUrl:String)
data class RouteLine(val id:Long,val name:String,val description:String,val placeIds:List<Long>)

class Db(ctx:Context):SQLiteOpenHelper(ctx,"culture.db",null,4){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE cities(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,country TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL)")
  db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,description TEXT NOT NULL,address TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,source_url TEXT NOT NULL DEFAULT '',image_url TEXT NOT NULL DEFAULT '')")
  db.execSQL("CREATE TABLE routes(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL)")
  db.execSQL("CREATE TABLE route_places(route_id INTEGER NOT NULL,place_id INTEGER NOT NULL,station_order INTEGER NOT NULL,PRIMARY KEY(route_id,place_id))")
  val city=db.compileStatement("INSERT INTO cities(name,country,lat,lon) VALUES('Троицк','Россия',54.0820,61.5596)").executeInsert()
  val ps=listOf(
   arrayOf("Троицкий краеведческий музей","Музеи","Городской краеведческий музей.","ул. Ленина, 70",54.082118,61.559624),
   arrayOf("Торговые ряды","Архитектура","Исторические торговые ряды Троицка.","ул. Климова, 5А",54.083491,61.559328),
   arrayOf("Пассаж братьев Яушевых","Архитектура","Памятник архитектуры федерального значения, построен в 1908–1910 годах.","ул. Малышева, 32",54.086718,61.560654),
   arrayOf("Свято-Троицкий собор","Религия","Кафедральный собор, одно из старейших сохранившихся каменных зданий города.","ул. Красногвардейская, 1А",54.077845,61.557944),
   arrayOf("Памятный камень Троицкой крепости","История","Памятный знак на месте основания Троицкой крепости в 1743 году.","ул. Красногвардейская, 1",54.077485,61.556362),
   arrayOf("Памятник Ф. Н. Плевако","Памятники","Памятник знаменитому адвокату и уроженцу Троицка.","ул. Октябрьская",54.081818,61.562668),
   arrayOf("Центральная площадь","История","Исторический центр города и место бывшей Михайловской площади.","Центральная площадь",54.082600,61.559233),
   arrayOf("Мечеть Гатауллы муллы","Религия","Историческая мечеть, построенная в 1894–1895 годах.","ул. Ленина, 117",54.087017,61.571150),
   arrayOf("Водонапорная башня","Архитектура","Памятник архитектуры 1927 года, городская высотная доминанта.","ул. Гагарина, 22А",54.080950,61.533317),
   arrayOf("Памятник И. И. Неплюеву","Памятники","Памятник основателю Троицка, установлен в 2001 году.","ул. Гагарина",54.0820,61.5620)
  )
  val ins=db.compileStatement("INSERT INTO places(city_id,name,category,description,address,lat,lon) VALUES(?,?,?,?,?,?,?)")
  for(p in ps){ins.bindLong(1,city);ins.bindString(2,p[0] as String);ins.bindString(3,p[1] as String);ins.bindString(4,p[2] as String);ins.bindString(5,p[3] as String);ins.bindDouble(6,p[4] as Double);ins.bindDouble(7,p[5] as Double);ins.executeInsert()}
  seedRoutes(db,city)
 }
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){
  if(oldVersion<3){
   db.execSQL("CREATE TABLE IF NOT EXISTS routes(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,description TEXT NOT NULL)")
   db.execSQL("CREATE TABLE IF NOT EXISTS route_places(route_id INTEGER NOT NULL,place_id INTEGER NOT NULL,station_order INTEGER NOT NULL,PRIMARY KEY(route_id,place_id))")
   val c=db.rawQuery("SELECT id FROM cities ORDER BY id LIMIT 1",null)
   c.use{if(it.moveToFirst())seedRoutes(db,it.getLong(0))}
  }
  if(oldVersion<4){
   db.execSQL("ALTER TABLE places ADD COLUMN source_url TEXT NOT NULL DEFAULT ''")
   db.execSQL("ALTER TABLE places ADD COLUMN image_url TEXT NOT NULL DEFAULT ''")
  }
 }
 private fun seedRoutes(db:SQLiteDatabase,cityId:Long){
  val count=db.compileStatement("SELECT COUNT(*) FROM routes WHERE city_id=?").apply{bindLong(1,cityId)}.simpleQueryForLong()
  if(count>0)return
  val routeData=listOf(
   arrayOf("Исторический центр","Главные исторические объекты центра города.",listOf("Памятный камень Троицкой крепости","Свято-Троицкий собор","Центральная площадь","Торговые ряды","Троицкий краеведческий музей","Пассаж братьев Яушевых","Памятник Ф. Н. Плевако")),
   arrayOf("Архитектура и купечество","Архитектурные и торговые памятники города.",listOf("Торговые ряды","Пассаж братьев Яушевых","Мечеть Гатауллы муллы","Водонапорная башня","Троицкий краеведческий музей")),
   arrayOf("История города","Точки, связанные с основанием и развитием Троицка.",listOf("Памятный камень Троицкой крепости","Центральная площадь","Памятник И. И. Неплюеву","Торговые ряды","Свято-Троицкий собор"))
  )
  val routeStmt=db.compileStatement("INSERT INTO routes(city_id,name,description) VALUES(?,?,?)")
  val linkStmt=db.compileStatement("INSERT INTO route_places(route_id,place_id,station_order) VALUES(?,?,?)")
  for(r in routeData){
   routeStmt.bindLong(1,cityId);routeStmt.bindString(2,r[0] as String);routeStmt.bindString(3,r[1] as String)
   val routeId=routeStmt.executeInsert()
   val names=r[2] as List<*>
   for((order,name) in names.withIndex()){
    val q=db.rawQuery("SELECT id FROM places WHERE city_id=? AND name=? LIMIT 1",arrayOf(cityId.toString(),name as String))
    q.use{if(it.moveToFirst()){linkStmt.bindLong(1,routeId);linkStmt.bindLong(2,it.getLong(0));linkStmt.bindLong(3,order.toLong());linkStmt.executeInsert()}}
   }
  }
 }
 fun addCity(name:String,country:String,lat:Double,lon:Double):Long{
  return writableDatabase.compileStatement("INSERT INTO cities(name,country,lat,lon) VALUES(?,?,?,?)").apply{
   bindString(1,name);bindString(2,country);bindDouble(3,lat);bindDouble(4,lon)
  }.executeInsert()
 }
 fun updateCity(cityId:Long,name:String,country:String,lat:Double,lon:Double){
  writableDatabase.compileStatement("UPDATE cities SET name=?,country=?,lat=?,lon=? WHERE id=?").apply{
   bindString(1,name);bindString(2,country);bindDouble(3,lat);bindDouble(4,lon);bindLong(5,cityId)
  }.executeUpdateDelete()
 }
 fun deleteCity(cityId:Long){
  writableDatabase.beginTransaction()
  try{
   val routeIds=mutableListOf<Long>()
   writableDatabase.rawQuery("SELECT id FROM routes WHERE city_id=?",arrayOf(cityId.toString())).use{while(it.moveToNext())routeIds+=it.getLong(0)}
   routeIds.forEach{writableDatabase.delete("route_places","route_id=?",arrayOf(it.toString()))}
   writableDatabase.delete("routes","city_id=?",arrayOf(cityId.toString()))
   writableDatabase.delete("places","city_id=?",arrayOf(cityId.toString()))
   writableDatabase.delete("cities","id=?",arrayOf(cityId.toString()))
   writableDatabase.setTransactionSuccessful()
  }finally{writableDatabase.endTransaction()}
 }
 fun addPlace(cityId:Long,name:String,category:String,description:String,address:String,lat:Double,lon:Double,sourceUrl:String="",imageUrl:String=""):Long{
  return writableDatabase.compileStatement("INSERT INTO places(city_id,name,category,description,address,lat,lon,source_url,image_url) VALUES(?,?,?,?,?,?,?,?,?)").apply{
   bindLong(1,cityId);bindString(2,name);bindString(3,category);bindString(4,description);bindString(5,address);bindDouble(6,lat);bindDouble(7,lon);bindString(8,sourceUrl);bindString(9,imageUrl)
  }.executeInsert()
 }
 fun updatePlace(placeId:Long,name:String,category:String,description:String,address:String,lat:Double,lon:Double,sourceUrl:String,imageUrl:String){
  writableDatabase.compileStatement("UPDATE places SET name=?,category=?,description=?,address=?,lat=?,lon=?,source_url=?,image_url=? WHERE id=?").apply{
   bindString(1,name);bindString(2,category);bindString(3,description);bindString(4,address);bindDouble(5,lat);bindDouble(6,lon);bindString(7,sourceUrl);bindString(8,imageUrl);bindLong(9,placeId)
  }.executeUpdateDelete()
 }
 fun createRoute(cityId:Long,name:String,description:String):Long{
  return writableDatabase.compileStatement("INSERT INTO routes(city_id,name,description) VALUES(?,?,?)").apply{
   bindLong(1,cityId);bindString(2,name);bindString(3,description)
  }.executeInsert()
 }
 fun updateRoute(routeId:Long,name:String,description:String){
  writableDatabase.compileStatement("UPDATE routes SET name=?,description=? WHERE id=?").apply{
   bindString(1,name);bindString(2,description);bindLong(3,routeId)
  }.executeUpdateDelete()
 }
 fun deleteRoute(routeId:Long){
  writableDatabase.beginTransaction()
  try{
   writableDatabase.delete("route_places","route_id=?",arrayOf(routeId.toString()))
   writableDatabase.delete("routes","id=?",arrayOf(routeId.toString()))
   writableDatabase.setTransactionSuccessful()
  }finally{writableDatabase.endTransaction()}
 }
 fun saveRoutePlaces(routeId:Long,placeIds:List<Long>){
  writableDatabase.beginTransaction()
  try{
   writableDatabase.delete("route_places","route_id=?",arrayOf(routeId.toString()))
   val stmt=writableDatabase.compileStatement("INSERT INTO route_places(route_id,place_id,station_order) VALUES(?,?,?)")
   placeIds.forEachIndexed{index,placeId->
    stmt.bindLong(1,routeId);stmt.bindLong(2,placeId);stmt.bindLong(3,index.toLong());stmt.executeInsert()
   }
   writableDatabase.setTransactionSuccessful()
  }finally{writableDatabase.endTransaction()}
 }
 fun exportJson():String{
  val root=JSONObject().put("format","cultureguide").put("version",1)
  val jc=JSONArray();val jp=JSONArray();val jr=JSONArray();val jrp=JSONArray()
  readableDatabase.rawQuery("SELECT id,name,country,lat,lon FROM cities ORDER BY id",null).use{while(it.moveToNext()){jc.put(JSONObject().put("id",it.getLong(0)).put("name",it.getString(1)).put("country",it.getString(2)).put("lat",it.getDouble(3)).put("lon",it.getDouble(4)))}}
  readableDatabase.rawQuery("SELECT id,city_id,name,category,description,address,lat,lon,source_url,image_url FROM places ORDER BY id",null).use{while(it.moveToNext()){jp.put(JSONObject().put("id",it.getLong(0)).put("city_id",it.getLong(1)).put("name",it.getString(2)).put("category",it.getString(3)).put("description",it.getString(4)).put("address",it.getString(5)).put("lat",it.getDouble(6)).put("lon",it.getDouble(7)).put("source_url",it.getString(8)).put("image_url",it.getString(9)))}}
  readableDatabase.rawQuery("SELECT id,city_id,name,description FROM routes ORDER BY id",null).use{while(it.moveToNext()){jr.put(JSONObject().put("id",it.getLong(0)).put("city_id",it.getLong(1)).put("name",it.getString(2)).put("description",it.getString(3)))}}
  readableDatabase.rawQuery("SELECT route_id,place_id,station_order FROM route_places ORDER BY route_id,station_order",null).use{while(it.moveToNext()){jrp.put(JSONObject().put("route_id",it.getLong(0)).put("place_id",it.getLong(1)).put("station_order",it.getLong(2)))}}
  root.put("cities",jc).put("places",jp).put("routes",jr).put("route_places",jrp);return root.toString(2)
 }
 fun importJson(text:String){
  val root=JSONObject(text)
  require(root.optString("format")=="cultureguide"){"Неверный формат файла"}
  require(root.optInt("version",-1)==1){"Неподдерживаемая версия каталога"}
  require(root.has("cities")&&root.has("places")&&root.has("routes")&&root.has("route_places")){"Неполный каталог"}
  val db=writableDatabase;db.beginTransaction()
  try{
   db.execSQL("DELETE FROM route_places");db.execSQL("DELETE FROM routes");db.execSQL("DELETE FROM places");db.execSQL("DELETE FROM cities")
   val cityIds=HashMap<Long,Long>();val placeIds=HashMap<Long,Long>();val routeIds=HashMap<Long,Long>()
   val cj=root.getJSONArray("cities");for(i in 0 until cj.length()){val o=cj.getJSONObject(i);cityIds[o.getLong("id")]=addCity(o.getString("name"),o.getString("country"),o.getDouble("lat"),o.getDouble("lon"))}
   val pj=root.getJSONArray("places");for(i in 0 until pj.length()){val o=pj.getJSONObject(i);placeIds[o.getLong("id")]=addPlace(cityIds[o.getLong("city_id")]?:error("Город не найден"),o.getString("name"),o.getString("category"),o.getString("description"),o.getString("address"),o.getDouble("lat"),o.getDouble("lon"),o.optString("source_url"),o.optString("image_url"))}
   val rs=db.compileStatement("INSERT INTO routes(city_id,name,description) VALUES(?,?,?)");val rj=root.getJSONArray("routes");for(i in 0 until rj.length()){val o=rj.getJSONObject(i);rs.bindLong(1,cityIds[o.getLong("city_id")]?:error("Город маршрута не найден"));rs.bindString(2,o.getString("name"));rs.bindString(3,o.getString("description"));routeIds[o.getLong("id")]=rs.executeInsert()}
   val ls=db.compileStatement("INSERT INTO route_places(route_id,place_id,station_order) VALUES(?,?,?)");val lj=root.getJSONArray("route_places");for(i in 0 until lj.length()){val o=lj.getJSONObject(i);ls.bindLong(1,routeIds[o.getLong("route_id")]?:error("Маршрут не найден"));ls.bindLong(2,placeIds[o.getLong("place_id")]?:error("Объект маршрута не найден"));ls.bindLong(3,o.getLong("station_order"));ls.executeInsert()}
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun mergeCatalogJson(text:String):Triple<Int,Int,Int>{
  val root=JSONObject(text)
  require(root.optString("format")=="cultureguide"){"Неверный формат каталога"}
  require(root.optInt("version",-1)==1){"Неподдерживаемая версия каталога"}
  require(root.has("cities")&&root.has("places")&&root.has("routes")&&root.has("route_places")){"Неполный каталог"}
  val db=writableDatabase
  db.beginTransaction()
  var addedCities=0;var addedPlaces=0;var addedRoutes=0
  try{
   val cityIds=HashMap<Long,Long>();val placeIds=HashMap<Long,Long>();val routeIds=HashMap<Long,Long>()
   val cj=root.getJSONArray("cities")
   for(i in 0 until cj.length()){
    val o=cj.getJSONObject(i)
    val sourceId=o.getLong("id")
    val name=o.getString("name").trim()
    val country=o.optString("country").trim()
    require(name.isNotBlank()){"У города нет названия"}
    val existing=db.rawQuery("SELECT id FROM cities WHERE name=? AND country=? LIMIT 1",arrayOf(name,country))
    val id=existing.use{
     if(it.moveToFirst()){
      val value=it.getLong(0)
      db.compileStatement("UPDATE cities SET lat=?,lon=? WHERE id=?").apply{
       bindDouble(1,o.getDouble("lat"));bindDouble(2,o.getDouble("lon"));bindLong(3,value)
      }.executeUpdateDelete()
      value
     }else{
      addedCities++
      addCity(name,country,o.getDouble("lat"),o.getDouble("lon"))
     }
    }
    cityIds[sourceId]=id
   }
   val pj=root.getJSONArray("places")
   for(i in 0 until pj.length()){
    val o=pj.getJSONObject(i)
    val sourceId=o.getLong("id")
    val city=cityIds[o.getLong("city_id")]?:error("Город объекта не найден")
    val name=o.getString("name").trim()
    require(name.isNotBlank()){"У объекта нет названия"}
    val category=o.optString("category").trim()
    require(category.isNotBlank()){"У объекта нет категории"}
    val description=o.optString("description").trim()
    val address=o.optString("address").trim()
    val lat=o.getDouble("lat")
    val lon=o.getDouble("lon")
    require(lat.isFinite()&&lat in -90.0..90.0&&lon.isFinite()&&lon in -180.0..180.0){"Некорректные координаты объекта"}
    val existing=db.rawQuery("SELECT id FROM places WHERE city_id=? AND name=? LIMIT 1",arrayOf(city.toString(),name))
    val id=existing.use{
     if(it.moveToFirst()){
      val value=it.getLong(0)
      db.compileStatement("UPDATE places SET category=?,description=?,address=?,lat=?,lon=?,source_url=?,image_url=? WHERE id=?").apply{
       bindString(1,category);bindString(2,description);bindString(3,address);bindDouble(4,lat);bindDouble(5,lon)
       bindString(6,o.optString("source_url").trim());bindString(7,o.optString("image_url").trim());bindLong(8,value)
      }.executeUpdateDelete()
      value
     }else{
      addedPlaces++
      addPlace(city,name,category,description,address,lat,lon,o.optString("source_url").trim(),o.optString("image_url").trim())
     }
    }
    placeIds[sourceId]=id
   }
   val rj=root.getJSONArray("routes")
   for(i in 0 until rj.length()){
    val o=rj.getJSONObject(i)
    val sourceId=o.getLong("id")
    val city=cityIds[o.getLong("city_id")]?:error("Город маршрута не найден")
    val name=o.getString("name").trim()
    require(name.isNotBlank()){"У маршрута нет названия"}
    val description=o.optString("description").trim()
    val existing=db.rawQuery("SELECT id FROM routes WHERE city_id=? AND name=? LIMIT 1",arrayOf(city.toString(),name))
    val id=existing.use{
     if(it.moveToFirst()){
      val value=it.getLong(0)
      db.compileStatement("UPDATE routes SET description=? WHERE id=?").apply{bindString(1,description);bindLong(2,value)}.executeUpdateDelete()
      value
     }else{
      addedRoutes++
      val st=db.compileStatement("INSERT INTO routes(city_id,name,description) VALUES(?,?,?)")
      st.bindLong(1,city);st.bindString(2,name);st.bindString(3,description);st.executeInsert()
     }
    }
    routeIds[sourceId]=id
   }
   val linksByRoute=HashMap<Long,MutableList<Pair<Int,Long>>>()
   val links=root.getJSONArray("route_places")
   for(i in 0 until links.length()){
    val o=links.getJSONObject(i)
    val routeId=o.getLong("route_id")
    val placeId=o.getLong("place_id")
    val route=routeIds[routeId]?:error("Маршрут связи не найден")
    val place=placeIds[placeId]?:error("Объект связи не найден")
    val order=o.getInt("station_order")
    require(order>=0){"Некорректный порядок остановки"}
    linksByRoute.getOrPut(route){mutableListOf()}+=order to place
   }
   linksByRoute.forEach{(routeId,items)->
    val seen=HashSet<Long>()
    val ordered=items.sortedBy{it.first}.map{it.second}.filter{seen.add(it)}
    db.delete("route_places","route_id=?",arrayOf(routeId.toString()))
    val stmt=db.compileStatement("INSERT INTO route_places(route_id,place_id,station_order) VALUES(?,?,?)")
    ordered.forEachIndexed{index,placeId->
     stmt.bindLong(1,routeId);stmt.bindLong(2,placeId);stmt.bindLong(3,index.toLong());stmt.executeInsert()
    }
   }
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
  return Triple(addedCities,addedPlaces,addedRoutes)
 }
 fun cities():List<City>{val r=readableDatabase.rawQuery("SELECT id,name,country,lat,lon FROM cities ORDER BY name",null);val a=mutableListOf<City>();r.use{while(it.moveToNext())a+=City(it.getLong(0),it.getString(1),it.getString(2),it.getDouble(3),it.getDouble(4))};return a}
 fun categories(city:Long):List<String>{val r=readableDatabase.rawQuery("SELECT DISTINCT category FROM places WHERE city_id=? ORDER BY category",arrayOf(city.toString()));val a=mutableListOf("Все");r.use{while(it.moveToNext())a+=it.getString(0)};return a}
 fun places(city:Long):List<Place>{val r=readableDatabase.rawQuery("SELECT id,name,category,description,address,lat,lon,source_url,image_url FROM places WHERE city_id=? ORDER BY id",arrayOf(city.toString()));val a=mutableListOf<Place>();r.use{while(it.moveToNext())a+=Place(it.getLong(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4),it.getDouble(5),it.getDouble(6),it.getString(7),it.getString(8))};return a}
 fun routes(city:Long):List<RouteLine>{val r=readableDatabase.rawQuery("SELECT id,name,description FROM routes WHERE city_id=? ORDER BY id",arrayOf(city.toString()));val a=mutableListOf<RouteLine>();r.use{while(it.moveToNext()){val id=it.getLong(0);val q=readableDatabase.rawQuery("SELECT place_id FROM route_places WHERE route_id=? ORDER BY station_order",arrayOf(id.toString()));val ids=mutableListOf<Long>();q.use{while(it.moveToNext())ids+=it.getLong(0)};a+=RouteLine(id,it.getString(1),it.getString(2),ids)}};return a}
 fun routesForPlace(city:Long,placeId:Long):List<RouteLine>{return routes(city).filter{placeId in it.placeIds}}
 fun routePlaces(route:RouteLine,all:List<Place>):List<Place>{val byId=all.associateBy{it.id};return route.placeIds.mapNotNull{byId[it]}}
}

fun dist(a:Place,b:Place):Double{val r=6371.0088;val p1=Math.toRadians(a.lat);val p2=Math.toRadians(b.lat);val dp=Math.toRadians(b.lat-a.lat);val dl=Math.toRadians(b.lon-a.lon);val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 2*r*asin(sqrt(h))}

class MetroView(c:Context):View(c){
 var places:List<Place> = emptyList()
 var route:List<Place> = emptyList()
 var lines:List<RouteLine> = emptyList()
 var selectedLineId:Long?=null
 var visitedPlaceIds:Set<Long> = emptySet()
 var nextPlaceId:Long?=null
 private val p=Paint(Paint.ANTI_ALIAS_FLAG)
 private val colors=intArrayOf(Color.rgb(49,94,251),Color.rgb(235,87,87),Color.rgb(39,174,96),Color.rgb(155,89,182),Color.rgb(242,153,74))
 private fun shortName(name:String):String{
  val clean=name.replace("Троицкий ","").replace("Памятник ","")
  return if(clean.length>20)clean.take(18)+"…" else clean
 }
 override fun onDraw(c:Canvas){
  super.onDraw(c)
  p.style=Paint.Style.FILL;p.color=Color.argb(238,255,255,255);c.drawRoundRect(8f,8f,width.toFloat()-8f,height.toFloat()-8f,18f,18f,p)
  if(lines.isEmpty())return
  val ranks=HashMap<Long,MutableList<Int>>()
  var maxRank=0
  for(line in lines)for((i,id) in line.placeIds.withIndex()){ranks.getOrPut(id){mutableListOf()}.add(i);maxRank=max(maxRank,i)}
  val xById=HashMap<Long,Float>()
  val left=70f;val right=(width-70f).coerceAtLeast(left+1f);val usable=(right-left)
  for((id,rs) in ranks){val avg=rs.average();xById[id]=left+if(maxRank==0)0.5f else (avg/maxRank.toFloat()*usable).toFloat()}
  val top=48f;val legendH=if(lines.size<=2)54f else 76f
  val bottom=(height-legendH).coerceAtLeast(top+40f)
  val laneGap=if(lines.size<=1)0f else (bottom-top)/(lines.size-1).toFloat()
  val pos=HashMap<Long,PointF>()
  for((li,line) in lines.withIndex())for(id in line.placeIds){val x=xById[id]?:continue;pos[id]=PointF(x,top+li*laneGap)}
  // Grid and route lines.
  for((li,line) in lines.withIndex()){
   val y=top+li*laneGap
   p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
   p.strokeWidth=if(line.id==selectedLineId)14f else 9f;p.color=colors[li%colors.size]
   var previous:PointF?=null
   for(id in line.placeIds){
    val q=pos[id]?:continue
    val a=previous
    if(a!=null){val bend=(a.x+q.x)/2f;c.drawLine(a.x,a.y,bend,a.y,p);c.drawLine(bend,a.y,bend,q.y,p);c.drawLine(bend,q.y,q.x,q.y,p)}
    previous=q
   }
  }
  // Station nodes. A transfer is a single shared node across lines.
  val byId=places.associateBy{it.id}
  for((id,q) in pos){
   val place=byId[id]?:continue
   val transfer=lines.count{it.placeIds.contains(id)}>1
   val radius=if(transfer)15f else 11f
   p.style=Paint.Style.FILL;p.color=Color.WHITE;c.drawCircle(q.x,q.y,radius+4f,p)
   val visited=place.id in visitedPlaceIds
   val next=place.id==nextPlaceId
   p.color=if(visited) Color.rgb(39,174,96) else Color.DKGRAY;c.drawCircle(q.x,q.y,radius,p)
   if(transfer){p.color=Color.WHITE;c.drawCircle(q.x,q.y,radius-5f,p)}
   if(next){
    p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=Color.rgb(49,94,251);c.drawCircle(q.x,q.y,radius+7f,p)
   }
   p.style=Paint.Style.FILL;p.color=if(visited) Color.rgb(39,174,96) else Color.DKGRAY;p.textAlign=Paint.Align.CENTER;p.textSize=12f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD
   c.drawText(shortName(place.name),q.x,q.y+radius+17f,p)
  }
  // Legend.
  p.textAlign=Paint.Align.LEFT;p.typeface=android.graphics.Typeface.DEFAULT;p.textSize=12f
  for(i in lines.indices){
   val col=i%2;val row=i/2;val x=14f+col*(width/2f);val y=height-16f-(lines.size/2-row-1).coerceAtLeast(0)*20f
   p.style=Paint.Style.STROKE;p.strokeWidth=6f;p.strokeCap=Paint.Cap.ROUND;p.color=colors[i%colors.size];c.drawLine(x,y,x+20f,y,p)
   p.style=Paint.Style.FILL;p.color=Color.DKGRAY;c.drawText((i+1).toString()+" "+shortName(lines[i].name),x+28f,y+4f,p)
  }
 }
}
class MainActivity:Activity(){
 private lateinit var db:Db
 private lateinit var mapView:MapView
 private lateinit var schemeView:MetroView
 private lateinit var list:LinearLayout
 private lateinit var status:TextView
 private lateinit var locationManager:LocationManager
 private var cityId=0L
 private var cities:List<City> = emptyList()
 private var routeLines:List<RouteLine> = emptyList()
 private var selectedRoute:RouteLine?=null
 private var currentPlaces:List<Place> = emptyList()
 private var routePlaces:List<Place> = emptyList()
 private lateinit var citySpinner:Spinner
 private lateinit var searchBox:EditText
 private lateinit var categorySpinner:Spinner
 private var selectedCategory="Все"
 private var schemeMode=false
 private val CREATE_JSON=2001
 private val OPEN_JSON=2002
 private lateinit var searchManager:SearchManager
 private var searchSession:SearchSession?=null
 private var pedestrianRouter:PedestrianRouter?=null
 private val routeSessions=mutableListOf<RouteSession>()
 private var routeBuildToken=0
 private var lastLocation:Location?=null
 private var routePolyline:MapObject?=null
 private val routePolylines=mutableListOf<MapObject>()
 private var routeDistanceMeters=0.0
 private var routeProgressIndex=0
 private val visitedRoutePlaceIds=mutableSetOf<Long>()
 private var userPlacemark:PlacemarkMapObject?=null
 private val pinProvider by lazy{ImageProvider.fromResource(this,R.drawable.ic_map_pin)}
 private val userPinProvider by lazy{ImageProvider.fromResource(this,R.drawable.ic_user_pin)}

 private val placeTapListener=MapObjectTapListener{mapObject,_->val place=mapObject.userData as? Place ?: return@MapObjectTapListener false;showPlace(place);true}
 private val userTapListener=MapObjectTapListener{_,_->AlertDialog.Builder(this).setTitle("Моё положение").setMessage(lastLocation?.let{"%.6f, %.6f".format(java.util.Locale.US,it.latitude,it.longitude)}?:"Координаты пока не получены").setPositiveButton("Закрыть",null).show();true}
 private val locationListener=object:LocationListener{
  override fun onLocationChanged(location:Location){
   lastLocation=location
   showUserLocation(location.latitude,location.longitude,false)
   if(routePolylines.isNotEmpty()) updateRouteProgress(location) else status.text="GPS: %.5f, %.5f".format(java.util.Locale.US,location.latitude,location.longitude)
  }
 }

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  MapKitFactory.initialize(this)
  searchManager=SearchFactory.getInstance().createSearchManager(SearchManagerType.COMBINED)
  pedestrianRouter=TransportFactory.getInstance().createPedestrianRouter()
  db=Db(this)
  locationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
  ui();loadCities();refresh();requestLocation()
 }

 private fun ui(){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(12,6,12,6)
  val title=TextView(this);title.text="Культурный маршрут · v"+BuildConfig.VERSION_NAME;title.textSize=22f;title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(4,2,4,2);root.addView(title)

  val cityRow=LinearLayout(this);cityRow.orientation=LinearLayout.HORIZONTAL;cityRow.gravity=android.view.Gravity.CENTER_VERTICAL
  val cityLabel=TextView(this);cityLabel.text="Город";cityLabel.textSize=14f;cityLabel.setTextColor(Color.DKGRAY);cityRow.addView(cityLabel,LinearLayout.LayoutParams(48,52))
  citySpinner=Spinner(this)
  citySpinner.background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setStroke(2,Color.LTGRAY);cornerRadius=18f}
  cityRow.addView(citySpinner,LinearLayout.LayoutParams(0,52,1f))
  val manage=Button(this);manage.text="⋮";manage.setAllCaps(false);manage.textSize=22f;manage.setOnClickListener{showAdminMenu()};cityRow.addView(manage,LinearLayout.LayoutParams(52,52))
  root.addView(cityRow)

  val searchRow=LinearLayout(this);searchRow.orientation=LinearLayout.HORIZONTAL;searchRow.setPadding(0,4,0,4)
  searchBox=EditText(this);searchBox.hint="Поиск объекта";searchBox.setSingleLine(true);searchBox.setPadding(16,0,12,0)
  searchBox.background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setStroke(2,Color.LTGRAY);cornerRadius=22f}
  searchRow.addView(searchBox,LinearLayout.LayoutParams(0,52,1f))
  categorySpinner=Spinner(this)
  categorySpinner.background=android.graphics.drawable.GradientDrawable().apply{setColor(Color.WHITE);setStroke(2,Color.LTGRAY);cornerRadius=18f}
  searchRow.addView(categorySpinner,LinearLayout.LayoutParams(132,52))
  root.addView(searchRow)

  val tabsScroll=HorizontalScrollView(this);tabsScroll.isHorizontalScrollBarEnabled=false
  val tabs=LinearLayout(this);tabs.orientation=LinearLayout.HORIZONTAL
  val schemeBtn=Button(this);schemeBtn.text="Схема"
  val mapBtn=Button(this);mapBtn.text="Карта"
  val linesBtn=Button(this);linesBtn.text="Линии"
  val routeBtn=Button(this);routeBtn.text="Маршрут"
  val gpsBtn=Button(this);gpsBtn.text="Моё место"
  for(btn in listOf(schemeBtn,mapBtn,linesBtn,routeBtn,gpsBtn)){
   btn.setTextColor(Color.DKGRAY);btn.setAllCaps(false)
   tabs.addView(btn,LinearLayout.LayoutParams(0,48,1f))
  }
  tabsScroll.addView(tabs);root.addView(tabsScroll,LinearLayout.LayoutParams(-1,52))

  val mapLayer=FrameLayout(this)
  mapView=MapView(this)
  schemeView=MetroView(this)
  schemeView.setBackgroundColor(Color.TRANSPARENT)
  mapLayer.addView(mapView,FrameLayout.LayoutParams(-1,-1))
  mapLayer.addView(schemeView,FrameLayout.LayoutParams(-1,-1))
  root.addView(mapLayer,LinearLayout.LayoutParams(-1,0,1.65f))

  status=TextView(this);status.textSize=14f;status.setPadding(8,3,8,3);root.addView(status,LinearLayout.LayoutParams(-1,34))
  val sv=ScrollView(this);list=LinearLayout(this);list.orientation=LinearLayout.VERTICAL;sv.addView(list);root.addView(sv,LinearLayout.LayoutParams(-1,0,0.7f))
  setContentView(root)

  schemeBtn.setOnClickListener{showScheme()}
  mapBtn.setOnClickListener{showMap()}
  linesBtn.setOnClickListener{showLines()}
  routeBtn.setOnClickListener{showRouteChooser()}
  gpsBtn.setOnClickListener{requestLocation();lastLocation?.let{showMap();showUserLocation(it.latitude,it.longitude,true)}}

  categorySpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onItemSelected(parent:AdapterView<*>,view:View?,position:Int,id:Long){
    val values=db.categories(cityId);if(position in values.indices){selectedCategory=values[position];renderPlaces(searchBox.text.toString())}
   }
   override fun onNothingSelected(parent:AdapterView<*>){}
  }
  searchBox.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
   override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){renderPlaces(s?.toString().orEmpty())}
   override fun afterTextChanged(s:android.text.Editable?){}
  })
  citySpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onItemSelected(parent:AdapterView<*>,view:View?,position:Int,id:Long){
    if(position in cities.indices&&cityId!=cities[position].id){cityId=cities[position].id;selectedRoute=null;refresh()}
   }
   override fun onNothingSelected(parent:AdapterView<*>){}
  }
 }

 private fun showAdminMenu(){
  val actions=arrayOf("Добавить город","Редактировать город","Найти город","Найти объект","Добавить объект","Создать линию","Обновить каталог","Экспорт каталога","Импорт каталога")
  AlertDialog.Builder(this).setTitle("Управление каталогом").setItems(actions){_,which->
   when(which){
    0->showAddCity();1->showEditCity();2->searchCity();3->searchPlace();4->showAddPlace();5->showRouteEditor(null);6->syncRemoteCatalog();7->exportCatalog();8->importCatalog()
   }
  }.show()
 }

 private fun selectCityInSpinner(){
  val idx=cities.indexOfFirst{it.id==cityId}
  if(idx>=0)citySpinner.setSelection(idx)
 }
 private fun searchCity(){
  val input=EditText(this);input.hint="Например: Москва, Россия";input.setSingleLine(true)
  AlertDialog.Builder(this).setTitle("Найти город").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Искать"){_,_->submitCitySearch(input.text.toString().trim())}.show()
 }
 private fun submitCitySearch(query:String){
  if(query.isBlank())return
  val city=cities.firstOrNull()
  val center=city?.let{Point(it.lat,it.lon)}?:Point(55.751244,37.618423)
  moveCamera(center.latitude,center.longitude,6f)
  searchSession=searchManager.submit(query,com.yandex.mapkit.map.VisibleRegionUtils.toPolygon(mapView.mapWindow.map.visibleRegion),SearchOptions(),object:SearchSession.SearchListener{
   override fun onSearchResponse(response:Response){
    val results=response.collection.children.mapNotNull{item->
     val obj=item.obj ?: return@mapNotNull null;val point=obj.geometry.firstOrNull()?.point;point?.let{Triple(obj.name ?: "Без названия",it.latitude,it.longitude)}
    }.take(8)
    if(results.isEmpty()){Toast.makeText(this@MainActivity,"Город не найден",Toast.LENGTH_LONG).show();return}
    val labels=results.map{it.first+" · %.5f, %.5f".format(java.util.Locale.US,it.second,it.third)}
    AlertDialog.Builder(this@MainActivity).setTitle("Выберите город").setItems(labels.toTypedArray()){_,which->
     val v=results[which];try{
      val existing=cities.firstOrNull{it.name.equals(v.first,ignoreCase=true)&&
       abs(it.lat-v.second)<0.05&&abs(it.lon-v.third)<0.08}
      cityId=existing?.id?:db.addCity(v.first,"",v.second,v.third)
      loadCities();selectCityInSpinner();refresh();moveCamera(v.second,v.third,13f)
     }catch(_:Exception){Toast.makeText(this@MainActivity,"Не удалось добавить город",Toast.LENGTH_LONG).show()}
    }.show()
   }
   override fun onSearchError(error:Error){Toast.makeText(this@MainActivity,if(error is NetworkError)"Нет сети для поиска" else "Ошибка поиска",Toast.LENGTH_LONG).show()}
  })
 }
 private fun searchPlace(){
  val input=EditText(this);input.hint="Например: музей, усадьба, собор";input.setSingleLine(true)
  AlertDialog.Builder(this).setTitle("Найти культурный объект").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Искать"){_,_->submitPlaceSearch(input.text.toString().trim())}.show()
 }
 private fun submitPlaceSearch(query:String){
  if(query.isBlank()||cityId==0L)return
  val city=cities.firstOrNull{it.id==cityId}?:return
  moveCamera(city.lat,city.lon,14f)
  val polygon=com.yandex.mapkit.map.VisibleRegionUtils.toPolygon(mapView.mapWindow.map.visibleRegion)
  searchSession=searchManager.submit(query,polygon,SearchOptions(),object:SearchSession.SearchListener{
   override fun onSearchResponse(response:Response){
    val results=response.collection.children.mapNotNull{item->
     val obj=item.obj ?: return@mapNotNull null
     val point=obj.geometry.firstOrNull()?.point ?: return@mapNotNull null
     obj to point
    }.take(10)
    if(results.isEmpty()){Toast.makeText(this@MainActivity,"Объекты не найдены",Toast.LENGTH_LONG).show();return}
    val labels=results.map{(obj,point)->(obj.name?:"Без названия")+" · %.5f, %.5f".format(java.util.Locale.US,point.latitude,point.longitude)}
    AlertDialog.Builder(this@MainActivity).setTitle("Добавить объект").setItems(labels.toTypedArray()){_,which->
     val (obj,point)=results[which]
     try{
      val name=obj.name?:"Без названия"
      val existing=currentPlaces.firstOrNull{
       it.name.equals(name,ignoreCase=true) ||
       (abs(it.lat-point.latitude)<0.0005 && abs(it.lon-point.longitude)<0.0008)
      }
      if(existing!=null){
       refresh();showMap();showPlace(existing);moveCamera(existing.lat,existing.lon,16f)
       return@setItems
      }
      val sourceUrl="https://yandex.ru/maps/?ll="+point.longitude+"%2C"+point.latitude+"&z=16&text="+Uri.encode(name)
      val categoryHint=obj.descriptionText?.split(",","·","—")?.firstOrNull()?.trim().orEmpty()
      val category=if(categoryHint.length in 3..40 && categoryHint.lowercase() !in setOf("адрес","город"))categoryHint else "Культура"
      val description=obj.descriptionText?.takeIf{it.isNotBlank()}?:"Найдено через Yandex Search"
      val id=db.addPlace(cityId,name,category,description,description,point.latitude,point.longitude,sourceUrl)
      refresh();showMap();currentPlaces.firstOrNull{it.id==id}?.let{showPlace(it)}
      moveCamera(point.latitude,point.longitude,16f)
     }catch(e:Exception){Toast.makeText(this@MainActivity,"Не удалось сохранить объект: "+e.message,Toast.LENGTH_LONG).show()}
    }.show()
   }
   override fun onSearchError(error:Error){Toast.makeText(this@MainActivity,if(error is NetworkError)"Нет сети для поиска" else "Ошибка поиска",Toast.LENGTH_LONG).show()}
  })
 }
 private fun showAddCity(){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Город";val country=EditText(this);country.hint="Страна";val lat=EditText(this);lat.hint="Широта";val lon=EditText(this);lon.hint="Долгота";listOf(name,country,lat,lon).forEach{box.addView(it)}
  val dialog=AlertDialog.Builder(this).setTitle("Добавить город").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    try{
     val n=name.text.toString().trim();val ctry=country.text.toString().trim();val la=lat.text.toString().trim().toDouble();val lo=lon.text.toString().trim().toDouble()
     require(n.isNotBlank()&&ctry.isNotBlank()&&la.isFinite()&&lo.isFinite()&&la in -90.0..90.0&&lo in -180.0..180.0)
     db.addCity(n,ctry,la,lo);loadCities();refresh();dialog.dismiss()
    }catch(_:Exception){Toast.makeText(this,"Проверьте название, страну и координаты",Toast.LENGTH_LONG).show()}
   }
  };dialog.show()
 }
 private fun showEditCity(){
  val city=cities.firstOrNull{it.id==cityId}?:return
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Город";name.setText(city.name)
  val country=EditText(this);country.hint="Страна";country.setText(city.country)
  val lat=EditText(this);lat.hint="Широта";lat.setText(city.lat.toString())
  val lon=EditText(this);lon.hint="Долгота";lon.setText(city.lon.toString())
  listOf(name,country,lat,lon).forEach{box.addView(it)}
  val builder=AlertDialog.Builder(this).setTitle("Редактировать город").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Сохранить"){_,_->
   try{
    db.updateCity(city.id,name.text.toString().trim(),country.text.toString().trim(),lat.text.toString().toDouble(),lon.text.toString().toDouble())
    loadCities();refresh()
   }catch(e:Exception){Toast.makeText(this,"Проверьте данные",Toast.LENGTH_LONG).show()}
  }
  builder.setNeutralButton("Удалить"){_,_->
   AlertDialog.Builder(this).setTitle("Удалить город?").setMessage("Будут удалены его объекты и линии.").setNegativeButton("Отмена",null).setPositiveButton("Удалить"){_,_->
    db.deleteCity(city.id);cityId=0L;loadCities();refresh()
   }.show()
  }
  val dialog=builder.create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    try{
     val n=name.text.toString().trim();val ctry=country.text.toString().trim();val la=lat.text.toString().trim().toDouble();val lo=lon.text.toString().trim().toDouble()
     require(n.isNotBlank()&&ctry.isNotBlank()&&la.isFinite()&&lo.isFinite()&&la in -90.0..90.0&&lo in -180.0..180.0)
     db.updateCity(city.id,n,ctry,la,lo);loadCities();refresh();dialog.dismiss()
    }catch(_:Exception){Toast.makeText(this,"Проверьте название, страну и координаты",Toast.LENGTH_LONG).show()}
   }
  }
  dialog.show()
 }

 private fun showPlaceEditor(place:Place){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Название";name.setText(place.name)
  val cat=EditText(this);cat.hint="Категория";cat.setText(place.category)
  val desc=EditText(this);desc.hint="Описание";desc.setText(place.description);desc.minLines=2
  val address=EditText(this);address.hint="Адрес";address.setText(place.address)
  val lat=EditText(this);lat.hint="Широта";lat.setText(place.lat.toString())
  val lon=EditText(this);lon.hint="Долгота";lon.setText(place.lon.toString())
  val source=EditText(this);source.hint="Источник (URL)";source.setText(place.sourceUrl)
  val image=EditText(this);image.hint="Изображение (URL)";image.setText(place.imageUrl)
  listOf(name,cat,desc,address,lat,lon,source,image).forEach{box.addView(it)}
  val dialog=AlertDialog.Builder(this).setTitle("Редактировать объект").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    try{
     val n=name.text.toString().trim();val category=cat.text.toString().trim();val d=desc.text.toString().trim();val a=address.text.toString().trim();val la=lat.text.toString().trim().toDouble();val lo=lon.text.toString().trim().toDouble()
     require(n.isNotBlank()&&category.isNotBlank()&&d.isNotBlank()&&a.isNotBlank()&&la.isFinite()&&lo.isFinite()&&la in -90.0..90.0&&lo in -180.0..180.0)
     db.updatePlace(place.id,n,category,d,a,la,lo,source.text.toString().trim(),image.text.toString().trim());refresh();dialog.dismiss();Toast.makeText(this,"Объект сохранён",Toast.LENGTH_SHORT).show()
    }catch(_:Exception){Toast.makeText(this,"Проверьте обязательные поля и координаты",Toast.LENGTH_LONG).show()}
   }
  }
  dialog.show()
 }

 private fun showRouteEditor(route:RouteLine?){
  if(cityId==0L)return
  val allPlaces=currentPlaces
  if(allPlaces.isEmpty()){
   Toast.makeText(this,"Сначала добавьте объекты города",Toast.LENGTH_LONG).show()
   return
  }
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(20,8,20,0)
  val name=EditText(this);name.hint="Название линии";name.setSingleLine(true);name.setText(route?.name.orEmpty())
  val description=EditText(this);description.hint="Описание";description.setText(route?.description.orEmpty());description.minLines=2
  box.addView(name);box.addView(description)
  val order=(route?.placeIds.orEmpty()+allPlaces.map{it.id}.filter{it !in route?.placeIds.orEmpty()}).toMutableList()
  val checked=allPlaces.associate{it.id to (route?.placeIds?.contains(it.id)==true)}.toMutableMap()
  val rows=LinearLayout(this);rows.orientation=LinearLayout.VERTICAL
  val scroll=ScrollView(this);scroll.addView(rows);box.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
  fun render(){
   rows.removeAllViews()
   order.forEachIndexed{index,id->
    val place=allPlaces.firstOrNull{it.id==id}?:return@forEachIndexed
    val row=LinearLayout(this);row.orientation=LinearLayout.HORIZONTAL;row.gravity=android.view.Gravity.CENTER_VERTICAL
    val cb=CheckBox(this);cb.text=place.name;cb.isChecked=checked[id]==true;cb.setOnCheckedChangeListener{_,value->checked[id]=value}
    row.addView(cb,LinearLayout.LayoutParams(0,52,1f))
    val up=Button(this);up.text="↑";up.setAllCaps(false);up.setOnClickListener{
     if(index>0){val v=order.removeAt(index);order.add(index-1,v);render()}
    }
    val down=Button(this);down.text="↓";down.setAllCaps(false);down.setOnClickListener{
     if(index<order.lastIndex){val v=order.removeAt(index);order.add(index+1,v);render()}
    }
    row.addView(up,LinearLayout.LayoutParams(48,48));row.addView(down,LinearLayout.LayoutParams(48,48));rows.addView(row)
   }
  }
  render()
  val builder=AlertDialog.Builder(this).setTitle(if(route==null)"Новая линия" else "Редактировать линию").setView(box)
    .setNegativeButton("Отмена",null)
    .setPositiveButton("Сохранить"){_,_->
     try{
      val title=name.text.toString().trim()
      if(title.isBlank())throw IllegalArgumentException("Введите название линии")
      val desc=description.text.toString().trim()
      val id=if(route==null)db.createRoute(cityId,title,desc) else {db.updateRoute(route.id,title,desc);route.id}
      db.saveRoutePlaces(id,order.filter{checked[it]==true})
      refresh();showLines()
     }catch(e:Exception){Toast.makeText(this,"Не удалось сохранить линию: "+(e.message?:"ошибка"),Toast.LENGTH_LONG).show()}
    }
  if(route!=null)builder.setNeutralButton("Удалить"){_,_->
   db.deleteRoute(route.id);refresh();showLines()
  }
  val dialog=builder.create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    try{
     val title=name.text.toString().trim()
     require(title.isNotBlank()){"Введите название линии"}
     val desc=description.text.toString().trim()
     val id=if(route==null)db.createRoute(cityId,title,desc) else {db.updateRoute(route.id,title,desc);route.id}
     db.saveRoutePlaces(id,order.filter{checked[it]==true})
     refresh();showLines();dialog.dismiss()
    }catch(e:Exception){Toast.makeText(this,"Не удалось сохранить линию: "+(e.message?:"ошибка"),Toast.LENGTH_LONG).show()}
   }
  }
  dialog.show()
 }

 private fun showAddPlace(){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Название";val cat=EditText(this);cat.hint="Категория";val desc=EditText(this);desc.hint="Описание";val address=EditText(this);address.hint="Адрес";val lat=EditText(this);lat.hint="Широта";val lon=EditText(this);lon.hint="Долгота";val source=EditText(this);source.hint="Источник (URL)";val image=EditText(this);image.hint="Изображение (URL)";listOf(name,cat,desc,address,lat,lon,source,image).forEach{box.addView(it)}
  val dialog=AlertDialog.Builder(this).setTitle("Добавить объект").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create()
  dialog.setOnShowListener{
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
    try{
     val n=name.text.toString().trim();val category=cat.text.toString().trim();val d=desc.text.toString().trim();val a=address.text.toString().trim();val la=lat.text.toString().trim().toDouble();val lo=lon.text.toString().trim().toDouble()
     require(n.isNotBlank()&&category.isNotBlank()&&d.isNotBlank()&&a.isNotBlank()&&la.isFinite()&&lo.isFinite()&&la in -90.0..90.0&&lo in -180.0..180.0)
     db.addPlace(cityId,n,category,d,a,la,lo,source.text.toString().trim(),image.text.toString().trim());refresh();dialog.dismiss()
    }catch(_:Exception){Toast.makeText(this,"Проверьте обязательные поля и координаты",Toast.LENGTH_LONG).show()}
   }
  };dialog.show()
 }
 private fun syncRemoteCatalog(){
  status.text="Обновляю каталог…"
  Thread{
   try{
    val connection=java.net.URL("https://raw.githubusercontent.com/qwest65/prog/main/data/catalog.json").openConnection() as java.net.HttpURLConnection
    connection.connectTimeout=10000;connection.readTimeout=20000;connection.requestMethod="GET"
    connection.setRequestProperty("Accept","application/json")
    if(connection.responseCode !in 200..299)throw IllegalStateException("HTTP "+connection.responseCode)
    val text=connection.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText()}
    val result=db.mergeCatalogJson(text)
    connection.disconnect()
    runOnUiThread{
     loadCities();refresh()
     status.text="Каталог обновлён · +" + result.first + " городов, +" + result.second + " объектов, +" + result.third + " маршрутов"
     Toast.makeText(this,"Каталог синхронизирован",Toast.LENGTH_SHORT).show()
    }
   }catch(e:Exception){
    runOnUiThread{status.text="Ошибка обновления каталога";Toast.makeText(this,"Не удалось обновить каталог: "+(e.message?:"ошибка"),Toast.LENGTH_LONG).show()}
   }
  }.start()
 }

 private fun exportCatalog(){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"cultureguide.json")},CREATE_JSON)}
 private fun importCatalog(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"},OPEN_JSON)}
 override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
  super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data?.data==null)return
  try{
   if(requestCode==CREATE_JSON){contentResolver.openOutputStream(data.data!!)?.use{it.write(db.exportJson().toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Каталог сохранён",Toast.LENGTH_SHORT).show()}
   else if(requestCode==OPEN_JSON){val text=contentResolver.openInputStream(data.data!!)?.bufferedReader()?.use{it.readText()}?:return;db.importJson(text);loadCities();refresh();Toast.makeText(this,"Каталог импортирован",Toast.LENGTH_SHORT).show()}
  }catch(e:Exception){Toast.makeText(this,"Ошибка файла: "+e.message,Toast.LENGTH_LONG).show()}
 }
 private fun loadCities(){
  cities=db.cities()
  val labels=cities.map{it.name+", "+it.country}
  citySpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,labels).apply{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)}
  if(cities.isNotEmpty()){if(cityId==0L)cityId=cities.first().id;val idx=cities.indexOfFirst{it.id==cityId}.coerceAtLeast(0);citySpinner.setSelection(idx)}
 }

 private fun showScheme(){
  schemeMode=true;mapView.visibility=View.GONE;schemeView.visibility=View.VISIBLE
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=selectedRoute?.id;schemeView.route=emptyList();schemeView.invalidate()
  list.removeAllViews()
  val title=TextView(this);title.text="Схема культурных маршрутов";title.textSize=20f;title.setPadding(8,8,8,8);list.addView(title)
  for((index,line) in routeLines.withIndex()){val t=TextView(this);t.text=(index+1).toString()+". "+line.name+" · "+line.placeIds.size+" объектов\n"+line.description;t.textSize=16f;t.setPadding(14,12,8,12);t.setOnClickListener{selectRoute(line)};list.addView(t)}
  status.text=routeLines.size.toString()+" линий · общие объекты = пересадки"
 }

 private fun showMap(){
  schemeMode=false;mapView.visibility=View.VISIBLE;schemeView.visibility=View.GONE
  drawPlacesOnMap()
  list.removeAllViews()
  val header=TextView(this)
  header.text="Объекты города · "+currentPlaces.size
  header.textSize=18f;header.setTypeface(null,android.graphics.Typeface.BOLD);header.setPadding(10,8,10,4);list.addView(header)
  val choose=Button(this);choose.text="Выбрать культурный маршрут";choose.setAllCaps(false);choose.textSize=16f
  choose.setOnClickListener{showRouteChooser()}
  list.addView(choose,LinearLayout.LayoutParams(-1,54))
  val filtered=currentPlaces.filter{selectedCategory=="Все"||it.category==selectedCategory}
  filtered.take(6).forEachIndexed{i,p->
   val t=TextView(this);t.text=(i+1).toString()+". "+p.name+"\n"+p.category+" · "+p.address
   t.textSize=15f;t.setPadding(10,8,10,8)
   t.setOnClickListener{showPlace(p);moveCamera(p.lat,p.lon,16f)}
   t.setOnLongClickListener{showPlaceEditor(p);true}
   list.addView(t)
  }
  status.text=currentPlaces.size.toString()+" объектов · карта"
 }

 private fun showLines(){
  mapView.visibility=View.VISIBLE;schemeView.visibility=View.GONE
  drawRoutesOnMap(selectedRoute?.id)
  list.removeAllViews()
  for((index,line) in routeLines.withIndex()){
   val t=TextView(this);t.text=(index+1).toString()+". "+line.name+"\n"+line.description+"\n"+line.placeIds.size+" объектов";t.textSize=16f;t.setPadding(8,12,8,12)
   t.setOnClickListener{selectRoute(line)}
   t.setOnLongClickListener{showRouteEditor(line);true}
   list.addView(t)
  }
  status.text=routeLines.size.toString()+" тематических линий · долгий тап — редактирование"
 }

 private fun renderRouteProgressUi(){
  schemeView.visitedPlaceIds=visitedRoutePlaceIds.toSet()
  schemeView.nextPlaceId=routePlaces.getOrNull(routeProgressIndex)?.id
  schemeView.invalidate()
  if(selectedRoute==null)return
  val selected=selectedRoute!!
  list.removeAllViews()
  val head=TextView(this)
  head.text=selected.name+"\n"+selected.description+"\n"+routePlaces.size+" остановок · расстояние по прямой: ≈ "+formatDistance(routePlaces)+" км"
  head.textSize=18f;head.setPadding(8,10,8,6);list.addView(head)
  val actions=LinearLayout(this);actions.orientation=LinearLayout.HORIZONTAL
  val start=Button(this);start.text="Начать маршрут";start.setAllCaps(false);start.setOnClickListener{buildRoute()};actions.addView(start,LinearLayout.LayoutParams(0,52,1f))
  val reset=Button(this);reset.text="Сбросить";reset.setAllCaps(false);reset.setOnClickListener{
   routeProgressIndex=0;visitedRoutePlaceIds.clear();renderRouteProgressUi()
   status.text="Прогресс маршрута сброшен"
   if(routePolylines.isNotEmpty())status.text=status.text.toString()+" · маршрут построен: %.2f км".format(java.util.Locale.US,routeDistanceMeters/1000.0)
  };actions.addView(reset,LinearLayout.LayoutParams(0,52,1f));list.addView(actions)
  routePlaces.forEachIndexed{index,p->
   val done=p.id in visitedRoutePlaceIds
   val current=p.id==routePlaces.getOrNull(routeProgressIndex)?.id
   val marker=when{done->"✓";current->"→";else->"○"}
   val t=TextView(this);t.text=marker+" "+(index+1)+". "+p.name+"\n"+p.category+" · "+p.address
   t.textSize=16f;t.setPadding(8,10,8,10)
   t.setTypeface(null,if(done)android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
   t.setOnClickListener{showMap();showPlace(p);moveCamera(p.lat,p.lon,16f)}
   t.setOnLongClickListener{showPlaceEditor(p);true};list.addView(t)
  }
 }

 private fun showRouteChooser(){
  if(routeLines.isEmpty()){Toast.makeText(this,"В этом городе пока нет культурных линий",Toast.LENGTH_LONG).show();return}
  val labels=routeLines.map{it.name+" · "+it.placeIds.size+" объектов"}.toTypedArray()
  AlertDialog.Builder(this).setTitle("Выберите культурный маршрут").setItems(labels){_,which->
   val line=routeLines[which];selectRoute(line)
  }.setNegativeButton("Отмена",null).show()
 }

 private fun selectRoute(line:RouteLine){
  selectedRoute=line;routePlaces=db.routePlaces(line,currentPlaces)
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=line.id;schemeView.route=emptyList();schemeView.invalidate()
  if(schemeMode)showScheme() else {showMap();drawRoutesOnMap(line.id)};list.removeAllViews()
  val head=TextView(this);head.text=line.name+"\n"+line.description+"\n"+routePlaces.size+" остановок · расстояние по прямой: ≈ "+formatDistance(routePlaces)+" км";head.textSize=18f;head.setPadding(8,10,8,6);list.addView(head)
  renderRouteProgressUi()
  status.text=line.name+" · "+routePlaces.size+" объектов"
 }

 private fun refresh(){
  routeLines=db.routes(cityId);currentPlaces=db.places(cityId);selectedRoute=null;routePlaces=emptyList();selectedCategory="Все"
  val categories=db.categories(cityId)
  categorySpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,categories).apply{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)}
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=null;schemeView.route=emptyList();schemeView.invalidate()
  drawPlacesOnMap();renderPlaces(searchBox.text.toString());showMap()
 }

 private fun renderPlaces(query:String){
  val q=query.trim().lowercase();val filtered=currentPlaces.filter{(selectedCategory=="Все"||it.category==selectedCategory)&&(q.isEmpty()||it.name.lowercase().contains(q)||it.category.lowercase().contains(q)||it.address.lowercase().contains(q))}
  list.removeAllViews()
  filtered.forEachIndexed{i,z->{val t=TextView(this);t.text="${i+1}. ${z.name}\n${z.category}\n${z.address}";t.textSize=16f;t.setPadding(8,12,8,12);t.setOnClickListener{showMap();showPlace(z);moveCamera(z.lat,z.lon,16f)};t.setOnLongClickListener{showPlaceEditor(z);true};list.addView(t)}}
  status.text=filtered.size.toString()+" объектов · "+if(q.isEmpty())"каталог" else "поиск"
 }

 private fun drawPlacesOnMap(){
  val objects=mapView.mapWindow.map.mapObjects
  objects.clear();routePolyline=null;userPlacemark=null;routePolylines.clear()
  currentPlaces.forEachIndexed{index,place->
   objects.addPlacemark().apply{
    geometry=Point(place.lat,place.lon)
    setIcon(pinProvider)
    zIndex=20f
    setText("${index+1}. ${place.name}")
    userData=place
    addTapListener(WeakReference(placeTapListener))
   }
  }
  cities.firstOrNull{it.id==cityId}?.let{moveCamera(it.lat,it.lon,14f)}
 }

 private fun showUserLocation(lat:Double,lon:Double,center:Boolean){
  val objects=mapView.mapWindow.map.mapObjects
  if(userPlacemark==null){userPlacemark=objects.addPlacemark().apply{setIcon(userPinProvider);addTapListener(WeakReference(userTapListener))}}
  userPlacemark?.geometry=Point(lat,lon);userPlacemark?.zIndex=10f
  if(center)moveCamera(lat,lon,16f)
 }

 private fun drawRoutesOnMap(selectedId:Long?){
  val objects=mapView.mapWindow.map.mapObjects
  routePolylines.forEach{objects.remove(it)}
  routePolylines.clear()
  val colors=listOf(Color.rgb(49,94,251),Color.rgb(235,87,87),Color.rgb(39,174,96),Color.rgb(155,89,182),Color.rgb(242,153,74))
  for((index,line) in routeLines.withIndex()){
   if(selectedId!=null && line.id!=selectedId)continue
   val pts=db.routePlaces(line,currentPlaces).map{Point(it.lat,it.lon)}
   if(pts.size>1){routePolylines+=objects.addPolyline(Polyline(pts)).apply{setStrokeColor(colors[index%colors.size]);setStrokeWidth(if(line.id==selectedId)8f else 5f);zIndex=2f}}
  }
 }

 private fun moveCamera(lat:Double,lon:Double,zoom:Float){mapView.mapWindow.map.move(CameraPosition(Point(lat,lon),zoom,0f,0f))}

 private fun formatDistance(places:List<Place>):String{
  if(places.size<2)return "0.0"
  var meters=0.0
  places.zipWithNext().forEach{(a,b)->meters+=dist(a,b)}
  return "%.1f".format(java.util.Locale.US,meters/1000.0)
 }

 private fun requestLocation(){
  if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION),1001);return
  }
  try{
   val provider=when{locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)->LocationManager.GPS_PROVIDER;locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)->LocationManager.NETWORK_PROVIDER;else->null}
   if(provider==null){status.text="GPS недоступен: включите геолокацию";return}
   locationManager.requestLocationUpdates(provider,5000L,5f,locationListener)
   locationManager.getLastKnownLocation(provider)?.let{locationListener.onLocationChanged(it)}
  }catch(_:SecurityException){status.text="Нет разрешения на геолокацию"}
 }

 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<String>,results:IntArray){
  super.onRequestPermissionsResult(requestCode,permissions,results)
  if(requestCode==1001&&results.any{it==PackageManager.PERMISSION_GRANTED})requestLocation()else if(requestCode==1001)status.text="Геолокация отключена пользователем"
 }

 private fun buildRoute(){
  val source=if(routePlaces.isNotEmpty())routePlaces else currentPlaces
  if(source.size<2){Toast.makeText(this,"Для маршрута нужно минимум 2 объекта",Toast.LENGTH_LONG).show();return}
  val ordered=if(selectedRoute!=null){
   // A thematic line has an explicit station order. Keep it intact;
   // the user's current location is only the physical route origin.
   source
  }else{
   val start=lastLocation?.let{location->
    source.minByOrNull{place->val result=FloatArray(1);Location.distanceBetween(location.latitude,location.longitude,place.lat,place.lon,result);result[0]}
   }?:source.first()
   val r=mutableListOf(start);val left=source.filter{it.id!=start.id}.toMutableList()
   while(left.isNotEmpty()){val next=left.minBy{dist(r.last(),it)};r+=next;left.remove(next)};r
  }
  routePlaces=ordered
  routeBuildToken++
  val token=routeBuildToken
  routeSessions.forEach{it.cancel()};routeSessions.clear()
  routePolylines.forEach{mapView.mapWindow.map.mapObjects.remove(it)};routePolylines.clear()
  routePolyline=null
  routeDistanceMeters=0.0
  routeProgressIndex=0
  visitedRoutePlaceIds.clear()
  schemeView.visitedPlaceIds=emptySet()
  schemeView.nextPlaceId=ordered.firstOrNull()?.id
  schemeView.invalidate()
  lastLocation?.let{showUserLocation(it.latitude,it.longitude,false)}
  showMap()
  val startPoint=lastLocation?.let{Point(it.latitude,it.longitude)}
  val routePoints=mutableListOf<Point>();if(startPoint!=null)routePoints+=startPoint;routePoints+=ordered.map{Point(it.lat,it.lon)}
  status.text="Строю пешеходный маршрут… 0/"+(routePoints.size-1)
  buildPedestrianLegs(routePoints,0,token,ordered.size)
 }

 private fun buildPedestrianLegs(points:List<Point>,index:Int,token:Int,stopCount:Int){
  if(token!=routeBuildToken)return
  if(index>=points.size-1){
   status.text="Маршрут построен · "+stopCount+" остановок · %.2f км".format(java.util.Locale.US,routeDistanceMeters/1000.0)
   return
  }
  val requestPoints=listOf(
   com.yandex.mapkit.RequestPoint(points[index],com.yandex.mapkit.RequestPointType.WAYPOINT,null,null,null),
   com.yandex.mapkit.RequestPoint(points[index+1],com.yandex.mapkit.RequestPointType.WAYPOINT,null,null,null)
  )
  val router=pedestrianRouter
  if(router==null){Toast.makeText(this,"Пешеходный роутер недоступен",Toast.LENGTH_LONG).show();return}
  val listener=object:RouteSession.RouteListener{
   override fun onMasstransitRoutes(routes:MutableList<com.yandex.mapkit.transport.masstransit.Route>){
    if(token!=routeBuildToken)return
    if(routes.isEmpty()){Toast.makeText(this@MainActivity,"Не удалось построить участок "+(index+1),Toast.LENGTH_LONG).show();return}
    val line=mapView.mapWindow.map.mapObjects.addPolyline(routes[0].geometry).apply{
     setStrokeColor(Color.rgb(49,94,251));setStrokeWidth(8f);zIndex=3f
    }
    routePolylines+=line
    routeDistanceMeters+=polylineDistanceMeters(routes[0].geometry)
    status.text="Пешеходный маршрут… "+(index+1)+"/"+(points.size-1)+" · %.2f км".format(java.util.Locale.US,routeDistanceMeters/1000.0)
    buildPedestrianLegs(points,index+1,token,stopCount)
   }
   override fun onMasstransitRoutesError(error:Error){
    if(token!=routeBuildToken)return
    val message=when(error){is NetworkError->"Нет сети для построения маршрута";else->"Yandex не построил участок "+(index+1)}
    Toast.makeText(this@MainActivity,message,Toast.LENGTH_LONG).show()
    status.text="Маршрут остановлен на участке "+(index+1)
   }
  }
  val session=router.requestRoutes(requestPoints,TimeOptions(),RouteOptions(FitnessOptions(false,false)),listener)
  routeSessions+=session
 }
 private fun polylineDistanceMeters(polyline:com.yandex.mapkit.geometry.Polyline):Double{ val p=polyline.points; var m=0.0; for(i in 1 until p.size){ val r=FloatArray(1); Location.distanceBetween(p[i-1].latitude,p[i-1].longitude,p[i].latitude,p[i].longitude,r); m+=r[0].toDouble() }; return m }
 private fun updateRouteProgress(location:Location){
  if(routePlaces.isEmpty()||routeProgressIndex>=routePlaces.size)return
  val next=routePlaces[routeProgressIndex]
  val r=FloatArray(1)
  Location.distanceBetween(location.latitude,location.longitude,next.lat,next.lon,r)
  if(r[0]<=50f){
   visitedRoutePlaceIds.add(next.id)
   routeProgressIndex++
   renderRouteProgressUi()
   if(routeProgressIndex>=routePlaces.size){
    renderRouteProgressUi()
    status.text="Маршрут завершён · посещено "+visitedRoutePlaceIds.size+" объектов · "+String.format(java.util.Locale.US,"%.2f км",routeDistanceMeters/1000.0)
    return
   }
  }
  val target=routePlaces[routeProgressIndex]
  val d=FloatArray(1)
  Location.distanceBetween(location.latitude,location.longitude,target.lat,target.lon,d)
  val distanceText=if(d[0]<1000f)"%.0f м".format(java.util.Locale.US,d[0]) else "%.2f км".format(java.util.Locale.US,d[0]/1000.0)
  status.text="Остановка "+(routeProgressIndex+1)+"/"+routePlaces.size+": "+target.name+" · "+distanceText+" · пройдено "+visitedRoutePlaceIds.size
 }
 private fun showPlace(p:Place){
  val lines=db.routesForPlace(cityId,p.id)
  val lineText=if(lines.isEmpty())"Линии: —" else "Линии: "+lines.joinToString(", "){it.name}
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(28,8,28,8)
  val info=TextView(this)
  info.text=p.category+"\n\n"+p.description+"\n\nАдрес: "+p.address+"\n\nКоординаты: "+"%.6f, %.6f".format(java.util.Locale.US,p.lat,p.lon)+"\n\n"+lineText
  info.textSize=16f;box.addView(info)
  val actions=LinearLayout(this);actions.orientation=LinearLayout.HORIZONTAL
  if(p.sourceUrl.isNotBlank()){
   val source=Button(this);source.text="Источник";source.setAllCaps(false);source.setOnClickListener{openUrl(p.sourceUrl)}
   actions.addView(source,LinearLayout.LayoutParams(0,52,1f))
  }
  if(p.imageUrl.isNotBlank()){
   val image=Button(this);image.text="Изображение";image.setAllCaps(false);image.setOnClickListener{openUrl(p.imageUrl)}
   actions.addView(image,LinearLayout.LayoutParams(0,52,1f))
  }
  if(actions.childCount>0)box.addView(actions)
  val builder=AlertDialog.Builder(this).setTitle(p.name).setView(box).setPositiveButton("Открыть карту"){_,_->openMap(p)}.setNegativeButton("Закрыть",null)
  if(lines.size>1)builder.setNeutralButton("Показать пересечения"){_,_->showLines()}
  builder.show()
 }

 private fun openUrl(value:String){
  try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(value)))}catch(_:Exception){Toast.makeText(this,"Не удалось открыть ссылку",Toast.LENGTH_LONG).show()}
 }

 private fun openMap(p:Place){
  val yandex=Uri.parse("yandexmaps://maps.yandex.ru/?ll="+p.lon+","+p.lat+"&z=16&text="+Uri.encode(p.name))
  val yandexIntent=Intent(Intent.ACTION_VIEW,yandex)
  try{if(yandexIntent.resolveActivity(packageManager)!=null){startActivity(yandexIntent);return}}catch(_:Exception){}
  val web=Uri.parse("https://yandex.ru/maps/?ll="+p.lon+"%2C"+p.lat+"&z=16&text="+Uri.encode(p.name))
  try{startActivity(Intent(Intent.ACTION_VIEW,web))}catch(_:Exception){Toast.makeText(this,"Не удалось открыть Яндекс Карты",Toast.LENGTH_LONG).show()}
 }


 override fun onStart(){super.onStart();MapKitFactory.getInstance().onStart();mapView.onStart()}
 override fun onStop(){mapView.onStop();MapKitFactory.getInstance().onStop();super.onStop()}
 override fun onDestroy(){if(::locationManager.isInitialized)locationManager.removeUpdates(locationListener);super.onDestroy()}
}