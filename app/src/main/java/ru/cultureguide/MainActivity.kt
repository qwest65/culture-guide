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
data class Place(val id:Long,val name:String,val category:String,val description:String,val address:String,val lat:Double,val lon:Double)
data class RouteLine(val id:Long,val name:String,val description:String,val placeIds:List<Long>)

class Db(ctx:Context):SQLiteOpenHelper(ctx,"culture.db",null,3){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE cities(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,country TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL)")
  db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER NOT NULL,name TEXT NOT NULL,category TEXT NOT NULL,description TEXT NOT NULL,address TEXT NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL)")
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
 fun addPlace(cityId:Long,name:String,category:String,description:String,address:String,lat:Double,lon:Double):Long{
  return writableDatabase.compileStatement("INSERT INTO places(city_id,name,category,description,address,lat,lon) VALUES(?,?,?,?,?,?,?)").apply{
   bindLong(1,cityId);bindString(2,name);bindString(3,category);bindString(4,description);bindString(5,address);bindDouble(6,lat);bindDouble(7,lon)
  }.executeInsert()
 }
 fun exportJson():String{
  val root=JSONObject().put("format","cultureguide").put("version",1)
  val jc=JSONArray();val jp=JSONArray();val jr=JSONArray();val jrp=JSONArray()
  readableDatabase.rawQuery("SELECT id,name,country,lat,lon FROM cities ORDER BY id",null).use{while(it.moveToNext()){jc.put(JSONObject().put("id",it.getLong(0)).put("name",it.getString(1)).put("country",it.getString(2)).put("lat",it.getDouble(3)).put("lon",it.getDouble(4)))}}
  readableDatabase.rawQuery("SELECT id,city_id,name,category,description,address,lat,lon FROM places ORDER BY id",null).use{while(it.moveToNext()){jp.put(JSONObject().put("id",it.getLong(0)).put("city_id",it.getLong(1)).put("name",it.getString(2)).put("category",it.getString(3)).put("description",it.getString(4)).put("address",it.getString(5)).put("lat",it.getDouble(6)).put("lon",it.getDouble(7)))}}
  readableDatabase.rawQuery("SELECT id,city_id,name,description FROM routes ORDER BY id",null).use{while(it.moveToNext()){jr.put(JSONObject().put("id",it.getLong(0)).put("city_id",it.getLong(1)).put("name",it.getString(2)).put("description",it.getString(3)))}}
  readableDatabase.rawQuery("SELECT route_id,place_id,station_order FROM route_places ORDER BY route_id,station_order",null).use{while(it.moveToNext()){jrp.put(JSONObject().put("route_id",it.getLong(0)).put("place_id",it.getLong(1)).put("station_order",it.getLong(2)))}}
  root.put("cities",jc).put("places",jp).put("routes",jr).put("route_places",jrp);return root.toString(2)
 }
 fun importJson(text:String){
  val root=JSONObject(text);require(root.optString("format")=="cultureguide"){"Неверный формат файла"}
  val db=writableDatabase;db.beginTransaction()
  try{
   db.execSQL("DELETE FROM route_places");db.execSQL("DELETE FROM routes");db.execSQL("DELETE FROM places");db.execSQL("DELETE FROM cities")
   val cityIds=HashMap<Long,Long>();val placeIds=HashMap<Long,Long>();val routeIds=HashMap<Long,Long>()
   val cj=root.getJSONArray("cities");for(i in 0 until cj.length()){val o=cj.getJSONObject(i);cityIds[o.getLong("id")]=addCity(o.getString("name"),o.getString("country"),o.getDouble("lat"),o.getDouble("lon"))}
   val pj=root.getJSONArray("places");for(i in 0 until pj.length()){val o=pj.getJSONObject(i);placeIds[o.getLong("id")]=addPlace(cityIds[o.getLong("city_id")]?:error("Город не найден"),o.getString("name"),o.getString("category"),o.getString("description"),o.getString("address"),o.getDouble("lat"),o.getDouble("lon"))}
   val rs=db.compileStatement("INSERT INTO routes(city_id,name,description) VALUES(?,?,?)");val rj=root.getJSONArray("routes");for(i in 0 until rj.length()){val o=rj.getJSONObject(i);rs.bindLong(1,cityIds[o.getLong("city_id")]?:error("Город маршрута не найден"));rs.bindString(2,o.getString("name"));rs.bindString(3,o.getString("description"));routeIds[o.getLong("id")]=rs.executeInsert()}
   val ls=db.compileStatement("INSERT INTO route_places(route_id,place_id,station_order) VALUES(?,?,?)");val lj=root.getJSONArray("route_places");for(i in 0 until lj.length()){val o=lj.getJSONObject(i);ls.bindLong(1,routeIds[o.getLong("route_id")]?:error("Маршрут не найден"));ls.bindLong(2,placeIds[o.getLong("place_id")]?:error("Объект маршрута не найден"));ls.bindLong(3,o.getLong("station_order"));ls.executeInsert()}
   db.setTransactionSuccessful()
  }finally{db.endTransaction()}
 }
 fun cities():List<City>{val r=readableDatabase.rawQuery("SELECT id,name,country,lat,lon FROM cities ORDER BY name",null);val a=mutableListOf<City>();r.use{while(it.moveToNext())a+=City(it.getLong(0),it.getString(1),it.getString(2),it.getDouble(3),it.getDouble(4))};return a}
 fun places(city:Long):List<Place>{val r=readableDatabase.rawQuery("SELECT id,name,category,description,address,lat,lon FROM places WHERE city_id=? ORDER BY id",arrayOf(city.toString()));val a=mutableListOf<Place>();r.use{while(it.moveToNext())a+=Place(it.getLong(0),it.getString(1),it.getString(2),it.getString(3),it.getString(4),it.getDouble(5),it.getDouble(6))};return a}
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
  for((id,rs) in ranks){val avg=rs.average();xById[id]=left+if(maxRank==0)0.5f else avg/maxRank.toFloat()*usable}
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
   p.color=Color.DKGRAY;c.drawCircle(q.x,q.y,radius,p)
   if(transfer){p.color=Color.WHITE;c.drawCircle(q.x,q.y,radius-5f,p)}
   p.color=Color.DKGRAY;p.textAlign=Paint.Align.CENTER;p.textSize=12f;p.typeface=android.graphics.Typeface.DEFAULT_BOLD
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
 private var userPlacemark:PlacemarkMapObject?=null
 private val pinProvider by lazy{ImageProvider.fromResource(this,R.drawable.ic_map_pin)}
 private val userPinProvider by lazy{ImageProvider.fromResource(this,R.drawable.ic_user_pin)}

 private val placeTapListener=MapObjectTapListener{mapObject,_->val place=mapObject.userData as? Place ?: return@MapObjectTapListener false;showPlace(place);true}
 private val userTapListener=MapObjectTapListener{_,_->AlertDialog.Builder(this).setTitle("Моё положение").setMessage(lastLocation?.let{"%.6f, %.6f".format(java.util.Locale.US,it.latitude,it.longitude)}?:"Координаты пока не получены").setPositiveButton("Закрыть",null).show();true}
 private val locationListener=object:LocationListener{
  override fun onLocationChanged(location:Location){
   lastLocation=location
   showUserLocation(location.latitude,location.longitude,false)
   status.text="GPS: %.5f, %.5f".format(java.util.Locale.US,location.latitude,location.longitude)
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
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(14,8,14,8)
  val title=TextView(this);title.text="Культурный маршрут · v${BuildConfig.VERSION_NAME}";title.textSize=23f;title.setPadding(0,0,0,4);root.addView(title)
  citySpinner=Spinner(this);root.addView(citySpinner,LinearLayout.LayoutParams(-1,48))
  searchBox=EditText(this);searchBox.hint="Поиск объекта";searchBox.singleLine=true;root.addView(searchBox,LinearLayout.LayoutParams(-1,52))
  val tabsScroll=HorizontalScrollView(this)
  val tabs=LinearLayout(this);tabs.orientation=LinearLayout.HORIZONTAL
  val schemeBtn=Button(this);schemeBtn.text="Схема"
  val mapBtn=Button(this);mapBtn.text="Карта"
  val linesBtn=Button(this);linesBtn.text="Линии"
  val routeBtn=Button(this);routeBtn.text="Маршрут"
  val gpsBtn=Button(this);gpsBtn.text="GPS"
  for(btn in listOf(schemeBtn,mapBtn,linesBtn,routeBtn,gpsBtn)){
   btn.setTextColor(Color.DKGRAY)
   btn.setAllCaps(false)
   btn.setBackgroundColor(Color.WHITE)
   tabs.addView(btn,LinearLayout.LayoutParams(0,52,1f))
  }
  tabsScroll.addView(tabs);root.addView(tabsScroll,LinearLayout.LayoutParams(-1,58))
  val adminRow=LinearLayout(this);adminRow.orientation=LinearLayout.HORIZONTAL
  fun adminButton(text:String,onClick:()->Unit){val b=Button(this);b.text=text;b.setAllCaps(false);b.setOnClickListener{onClick()};adminRow.addView(b,LinearLayout.LayoutParams(0,50,1f))}
  adminButton("Город"){showAddCity()};adminButton("Найти город"){searchCity()};adminButton("Объект"){showAddPlace()};adminButton("Экспорт"){exportCatalog()};adminButton("Импорт"){importCatalog()};root.addView(HorizontalScrollView(this).apply{addView(adminRow);layoutParams=LinearLayout.LayoutParams(-1,50)})
  val mapLayer=FrameLayout(this)
  mapView=MapView(this)
  schemeView=MetroView(this)
  schemeView.setBackgroundColor(Color.TRANSPARENT)
  mapLayer.addView(mapView,FrameLayout.LayoutParams(-1,-1))
  mapLayer.addView(schemeView,FrameLayout.LayoutParams(-1,-1))
  root.addView(mapLayer,LinearLayout.LayoutParams(-1,0,1.15f))
  status=TextView(this);status.textSize=15f;status.setPadding(4,5,4,5);root.addView(status)
  val sv=ScrollView(this);list=LinearLayout(this);list.orientation=LinearLayout.VERTICAL;sv.addView(list);root.addView(sv,LinearLayout.LayoutParams(-1,0,1f))
  setContentView(root)
  schemeBtn.setOnClickListener{showScheme()}
  mapBtn.setOnClickListener{showMap()}
  linesBtn.setOnClickListener{showLines()}
  routeBtn.setOnClickListener{buildRoute()}
  gpsBtn.setOnClickListener{requestLocation();lastLocation?.let{showMap();showUserLocation(it.latitude,it.longitude,true)}}
  searchBox.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){renderPlaces(s?.toString().orEmpty())};override fun afterTextChanged(s:android.text.Editable?){}})
  citySpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onItemSelected(parent:AdapterView<*>,view:View?,position:Int,id:Long){if(position in cities.indices && cityId!=cities[position].id){cityId=cities[position].id;selectedRoute=null;refresh()}}
   override fun onNothingSelected(parent:AdapterView<*>){}
  }
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
     val v=results[which];try{db.addCity(v.first,"",v.second,v.third);loadCities();cityId=db.cities().lastOrNull()?.id?:cityId;refresh();moveCamera(v.second,v.third,13f)}catch(_:Exception){Toast.makeText(this@MainActivity,"Не удалось добавить город",Toast.LENGTH_LONG).show()}
    }.show()
   }
   override fun onSearchError(error:Error){Toast.makeText(this@MainActivity,if(error is NetworkError)"Нет сети для поиска" else "Ошибка поиска",Toast.LENGTH_LONG).show()}
  })
 }
 private fun showAddCity(){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Город";val country=EditText(this);country.hint="Страна";val lat=EditText(this);lat.hint="Широта";val lon=EditText(this);lon.hint="Долгота";listOf(name,country,lat,lon).forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle("Добавить город").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить"){_,_->try{db.addCity(name.text.toString().trim(),country.text.toString().trim(),lat.text.toString().toDouble(),lon.text.toString().toDouble());loadCities();refresh()}catch(_:Exception){Toast.makeText(this,"Проверьте данные",Toast.LENGTH_LONG).show()}}.show()
 }
 private fun showAddPlace(){
  val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(24,8,24,0)
  val name=EditText(this);name.hint="Название";val cat=EditText(this);cat.hint="Категория";val desc=EditText(this);desc.hint="Описание";val address=EditText(this);address.hint="Адрес";val lat=EditText(this);lat.hint="Широта";val lon=EditText(this);lon.hint="Долгота";listOf(name,cat,desc,address,lat,lon).forEach{box.addView(it)}
  AlertDialog.Builder(this).setTitle("Добавить объект").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить"){_,_->try{db.addPlace(cityId,name.text.toString().trim(),cat.text.toString().trim(),desc.text.toString().trim(),address.text.toString().trim(),lat.text.toString().toDouble(),lon.text.toString().toDouble());refresh()}catch(_:Exception){Toast.makeText(this,"Проверьте данные",Toast.LENGTH_LONG).show()}}.show()
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
  mapView.visibility=View.VISIBLE;schemeView.visibility=View.VISIBLE
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=selectedRoute?.id;schemeView.route=emptyList();schemeView.invalidate()
  list.removeAllViews()
  val title=TextView(this);title.text="Схема культурных маршрутов";title.textSize=20f;title.setPadding(8,8,8,8);list.addView(title)
  for((index,line) in routeLines.withIndex()){val t=TextView(this);t.text=(index+1).toString()+". "+line.name+" · "+line.placeIds.size+" объектов\n"+line.description;t.textSize=16f;t.setPadding(14,12,8,12);t.setOnClickListener{selectRoute(line)};list.addView(t)}
  status.text=routeLines.size.toString()+" линий · общие объекты = пересадки"
 }

 private fun showMap(){
  mapView.visibility=View.VISIBLE;schemeView.visibility=View.GONE
  status.text=currentPlaces.size.toString()+" объектов · карта"
 }

 private fun showLines(){
  mapView.visibility=View.VISIBLE;schemeView.visibility=View.GONE
  drawRoutesOnMap(selectedRoute?.id)
  list.removeAllViews()
  for((index,line) in routeLines.withIndex()){
   val t=TextView(this);t.text=(index+1).toString()+". "+line.name+"\n"+line.description+"\n"+line.placeIds.size+" объектов";t.textSize=16f;t.setPadding(8,12,8,12)
   t.setOnClickListener{selectRoute(line)}
   list.addView(t)
  }
  status.text=routeLines.size.toString()+" тематических линий · повторяющиеся объекты являются пересечениями"
 }

 private fun selectRoute(line:RouteLine){
  selectedRoute=line;routePlaces=db.routePlaces(line,currentPlaces)
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=line.id;schemeView.route=emptyList();schemeView.invalidate()
  drawRoutesOnMap(line.id);list.removeAllViews()
  val head=TextView(this);head.text=line.name+"\n"+line.description;head.textSize=18f;head.setPadding(8,10,8,10);list.addView(head)
  routePlaces.forEachIndexed{index,p->
   val t=TextView(this);t.text=(index+1).toString()+". "+p.name+"\n"+p.category+"\n"+p.address;t.textSize=16f;t.setPadding(8,10,8,10);t.setOnClickListener{showMap();showPlace(p);moveCamera(p.lat,p.lon,16f)};list.addView(t)
  }
  status.text=line.name+" · "+routePlaces.size+" объектов"
 }

 private fun refresh(){
  routeLines=db.routes(cityId);currentPlaces=db.places(cityId);selectedRoute=null;routePlaces=emptyList()
  schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=null;schemeView.route=emptyList();schemeView.invalidate();drawPlacesOnMap();renderPlaces(searchBox.text.toString());showScheme()
 }
 private fun renderPlaces(query:String){
  val q=query.trim().lowercase();val filtered=currentPlaces.filter{q.isEmpty()||it.name.lowercase().contains(q)||it.category.lowercase().contains(q)||it.address.lowercase().contains(q)}
  list.removeAllViews()
  filtered.forEachIndexed{i,z->{val t=TextView(this);t.text="${i+1}. ${z.name}\n${z.category}\n${z.address}";t.textSize=16f;t.setPadding(8,12,8,12);t.setOnClickListener{showMap();showPlace(z);moveCamera(z.lat,z.lon,16f)};list.addView(t)}}
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
   val nearestIndex=lastLocation?.let{location->
    source.indices.minByOrNull{index->
     val place=source[index];val result=FloatArray(1)
     Location.distanceBetween(location.latitude,location.longitude,place.lat,place.lon,result);result[0]
    }
   }?:0
   source.drop(nearestIndex)+source.take(nearestIndex)
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
   status.text="Пешеходный маршрут построен · "+stopCount+" остановок"
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
    status.text="Пешеходный маршрут… "+(index+1)+"/"+(points.size-1)
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
 private fun showPlace(p:Place){
  val lines=db.routesForPlace(cityId,p.id)
  val lineText=if(lines.isEmpty())"Линии: —" else "Линии: "+lines.joinToString(", "){it.name}
  val box=TextView(this)
  box.text=p.category+"\n\n"+p.description+"\n\nАдрес: "+p.address+"\n\nКоординаты: "+"%.6f, %.6f".format(java.util.Locale.US,p.lat,p.lon)+"\n\n"+lineText
  box.textSize=16f;box.setPadding(28,8,28,8)
  val builder=AlertDialog.Builder(this).setTitle(p.name).setView(box).setPositiveButton("Открыть карту"){_,_->openMap(p)}.setNegativeButton("Закрыть",null)
  if(lines.size>1)builder.setNeutralButton("Показать пересечения"){_,_->showLines()}
  builder.show()
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
