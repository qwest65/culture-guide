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
 private val p=Paint(1)
 private val colors=intArrayOf(
  Color.rgb(49,94,251),Color.rgb(235,87,87),Color.rgb(39,174,96),
  Color.rgb(155,89,182),Color.rgb(242,153,74)
 )
 override fun onDraw(c:Canvas){
  super.onDraw(c)
  // Keep the Yandex map visible underneath the schematic.
  p.style=Paint.Style.FILL
  p.color=Color.argb(205,255,255,255)
  c.drawRoundRect(8f,8f,width.toFloat()-8f,height.toFloat()-8f,18f,18f,p)
  if(places.isEmpty()||lines.isEmpty())return
  val byId=places.associateBy{it.id}
  val positions=HashMap<Long,PointF>()
  val laneStep=if(lines.size<=1)0f else (height-100f)/(lines.size-1).toFloat()
  val left=55f
  val right=(width-55f).coerceAtLeast(left+1f)
  val top=45f

  for((lineIndex,line) in lines.withIndex()){
   val count=line.placeIds.size
   if(count==0)continue
   for((stationIndex,id) in line.placeIds.withIndex()){
    val x=if(count==1)(left+right)/2f else left+(right-left)*stationIndex.toFloat()/(count-1).toFloat()
    val y=top+lineIndex.toFloat()*laneStep
    val old=positions[id]
    if(old==null)positions[id]=PointF(x,y)
    else positions[id]=PointF((old.x+x)/2f,(old.y+y)/2f)
   }
  }

  // Faint guide lanes.
  p.style=Paint.Style.STROKE
  p.strokeWidth=1f
  p.color=Color.rgb(232,234,238)
  for(i in lines.indices){
   val y=top+i.toFloat()*laneStep
   c.drawLine(left,y,right,y,p)
  }

  // Route strokes. Transfers use short orthogonal connectors.
  for((index,line) in lines.withIndex()){
   val pts=line.placeIds.mapNotNull{positions[it]}
   if(pts.size<2)continue
   p.style=Paint.Style.STROKE
   p.strokeWidth=if(line.id==selectedLineId)14f else 9f
   p.strokeCap=Paint.Cap.ROUND
   p.strokeJoin=Paint.Join.ROUND
   p.color=colors[index%colors.size]
   for(i in 1 until pts.size){
    val a=pts[i-1]
    val b=pts[i]
    if(kotlin.math.abs(a.y-b.y)<3f){
     c.drawLine(a.x,a.y,b.x,b.y,p)
    }else{
     val mid=(a.x+b.x)/2f
     c.drawLine(a.x,a.y,mid,a.y,p)
     c.drawLine(mid,a.y,mid,b.y,p)
     c.drawLine(mid,b.y,b.x,b.y,p)
    }
   }
  }

  // Stations and transfer rings.
  for((index,place) in places.withIndex()){
   val q=positions[place.id]?:continue
   val transfer=lines.count{place.id in it.placeIds}>1
   val radius=if(transfer)15f else 11f
   p.style=Paint.Style.FILL
   p.color=Color.WHITE
   c.drawCircle(q.x,q.y,radius+3f,p)
   p.color=Color.DKGRAY
   c.drawCircle(q.x,q.y,radius,p)
   if(transfer){
    p.color=Color.WHITE
    c.drawCircle(q.x,q.y,radius-5f,p)
   }
   p.color=Color.DKGRAY
   p.textSize=14f
   p.typeface=android.graphics.Typeface.DEFAULT_BOLD
   p.textAlign=Paint.Align.CENTER
   c.drawText((index+1).toString(),q.x,q.y+5f,p)
  }

  // Legend.
  p.textAlign=Paint.Align.LEFT
  p.typeface=android.graphics.Typeface.DEFAULT
  p.textSize=12f
  val columns=2
  val rows=(lines.size+columns-1)/columns
  for(i in lines.indices){
   val col=i%columns
   val row=i/columns
   val x=14f+col*(width/2f)
   val y=height-12f-(rows-1-row)*20f
   p.style=Paint.Style.STROKE
   p.strokeWidth=6f
   p.strokeCap=Paint.Cap.ROUND
   p.color=colors[i%colors.size]
   c.drawLine(x,y,x+22f,y,p)
   p.style=Paint.Style.FILL
   p.color=Color.DKGRAY
   c.drawText((i+1).toString()+" "+lines[i].name,x+30f,y+4f,p)
  }
  p.textAlign=Paint.Align.LEFT
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
  db=Db(this)
  locationManager=getSystemService(Context.LOCATION_SERVICE) as LocationManager
  ui();loadCities();refresh();requestLocation()
 }

 private fun ui(){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(14,8,14,8)
  val title=TextView(this);title.text="Культурный маршрут";title.textSize=25f;root.addView(title)
  citySpinner=Spinner(this);root.addView(citySpinner,LinearLayout.LayoutParams(-1,48))
  val tabs=LinearLayout(this)
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
  root.addView(tabs)
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
  citySpinner.onItemSelectedListener=object:AdapterView.OnItemSelectedListener{
   override fun onItemSelected(parent:AdapterView<*>,view:View?,position:Int,id:Long){if(position in cities.indices && cityId!=cities[position].id){cityId=cities[position].id;selectedRoute=null;refresh()}}
   override fun onNothingSelected(parent:AdapterView<*>){}
  }
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
  routeLines=db.routes(cityId);currentPlaces=db.places(cityId);schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=null;schemeView.route=emptyList();schemeView.invalidate();drawPlacesOnMap()
  list.removeAllViews()
  currentPlaces.forEachIndexed{i,z->
   val t=TextView(this);t.text="${i+1}. ${z.name}\n${z.category}\n${z.address}";t.textSize=16f;t.setPadding(8,12,8,12)
   t.setOnClickListener{mapView.visibility=View.VISIBLE;schemeView.visibility=View.GONE;showPlace(z);moveCamera(z.lat,z.lon,16f)}
   list.addView(t)
  }
  showScheme()
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
  if(source.isEmpty())return
  val start=lastLocation?.let{location->source.minByOrNull{place->val result=FloatArray(1);Location.distanceBetween(location.latitude,location.longitude,place.lat,place.lon,result);result[0]}}?:source.first()
  val r=mutableListOf(start);val left=source.filter{it.id!=start.id}.toMutableList()
  while(left.isNotEmpty()){val next=left.minBy{dist(r.last(),it)};r+=next;left.remove(next)}
  routePlaces=r;schemeView.places=currentPlaces;schemeView.lines=routeLines;schemeView.selectedLineId=selectedRoute?.id;schemeView.route=emptyList();schemeView.invalidate()
  routePolyline?.let{mapView.mapWindow.map.mapObjects.remove(it)}
  val points=r.map{Point(it.lat,it.lon)}
  if(points.size>1){routePolyline=mapView.mapWindow.map.mapObjects.addPolyline(Polyline(points)).apply{setStrokeColor(Color.rgb(49,94,251));setStrokeWidth(7f);zIndex=3f}}
  lastLocation?.let{showUserLocation(it.latitude,it.longitude,false)}
  showMap()
  val km=r.zipWithNext().sumOf{dist(it.first,it.second)}
  status.text="Маршрут по объектам: %.1f км · ".format(java.util.Locale.US,km)+r.size+" остановок"
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
