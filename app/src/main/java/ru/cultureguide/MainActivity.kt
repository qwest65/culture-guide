package ru.cultureguide
import android.app.Activity
import android.os.Bundle
import android.graphics.*
import android.view.View
import android.widget.*
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import kotlin.math.*

data class Place(val name:String,val category:String,val lat:Double,val lon:Double)
class Db(ctx:Context):SQLiteOpenHelper(ctx,"culture.db",null,1){
 override fun onCreate(db:SQLiteDatabase){
  db.execSQL("CREATE TABLE cities(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,country TEXT,lat REAL,lon REAL)")
  db.execSQL("CREATE TABLE places(id INTEGER PRIMARY KEY AUTOINCREMENT,city_id INTEGER,name TEXT,category TEXT,description TEXT,lat REAL,lon REAL)")
  val city=db.compileStatement("INSERT INTO cities(name,country,lat,lon) VALUES('Троицк','Россия',54.0977,61.5686)").executeInsert()
  val ps=listOf(arrayOf("Исторический центр","История",54.0980,61.5680),arrayOf("Памятник Троицка","Памятники",54.0990,61.5700),arrayOf("Культурный объект","Культура",54.0965,61.5665),arrayOf("Архитектурный объект","Архитектура",54.1000,61.5655),arrayOf("Музей","Музеи",54.0955,61.5715))
  val s=db.compileStatement("INSERT INTO places(city_id,name,category,description,lat,lon) VALUES(?,?,?, 'Демонстрационный объект',?,?)")
  for(p in ps){s.bindLong(1,city);s.bindString(2,p[0] as String);s.bindString(3,p[1] as String);s.bindDouble(4,p[2] as Double);s.bindDouble(5,p[3] as Double);s.executeInsert()}
 }
 override fun onUpgrade(db:SQLiteDatabase,o:Int,n:Int){}
 fun places(city:Long):List<Place>{val r=readableDatabase.rawQuery("SELECT name,category,lat,lon FROM places WHERE city_id=? ORDER BY id",arrayOf(city.toString()));val a=mutableListOf<Place>();r.use{while(it.moveToNext())a+=Place(it.getString(0),it.getString(1),it.getDouble(2),it.getDouble(3))};return a}
 fun addCity():Long{val d=writableDatabase;val id=d.compileStatement("INSERT INTO cities(name,country,lat,lon) VALUES('Челябинск','Россия',55.1644,61.4368)").executeInsert();d.execSQL("INSERT INTO places(city_id,name,category,description,lat,lon) VALUES(?,?,?,?,?,?)",arrayOf(id,"Центральная площадь","Культура","Демонстрационный объект",55.1644,61.4368));return id}
}
fun dist(a:Place,b:Place):Double{val r=6371.0088;val p1=Math.toRadians(a.lat);val p2=Math.toRadians(b.lat);val dp=Math.toRadians(b.lat-a.lat);val dl=Math.toRadians(b.lon-a.lon);val h=sin(dp/2).pow(2)+cos(p1)*cos(p2)*sin(dl/2).pow(2);return 2*r*asin(sqrt(h))}
class MetroView(c:Context):View(c){
 var places:List<Place> = emptyList();var route:List<Place> = emptyList();val p=Paint(1)
 override fun onDraw(c:Canvas){c.drawColor(Color.rgb(248,249,251));p.color=Color.rgb(225,227,232);p.strokeWidth=1f;for(x in 0..width step 40)c.drawLine(x.toFloat(),0f,x.toFloat(),height.toFloat(),p);for(y in 0..height step 40)c.drawLine(0f,y.toFloat(),width.toFloat(),y.toFloat(),p);if(places.isEmpty())return
  val la0=places.minOf{it.lat};val la1=places.maxOf{it.lat};val lo0=places.minOf{it.lon};val lo1=places.maxOf{it.lon}
  fun xy(z:Place)=PointF((35+(z.lon-lo0)/((lo1-lo0).coerceAtLeast(1e-9))*(width-70)).toFloat(),(height-35-(z.lat-la0)/((la1-la0).coerceAtLeast(1e-9))*(height-70)).toFloat())
  val o=if(route.isEmpty())places else route;p.color=Color.rgb(49,94,251);p.style=Paint.Style.STROKE;p.strokeWidth=9f;p.strokeCap=Paint.Cap.ROUND
  for(i in 1 until o.size){val a=xy(o[i-1]);val b=xy(o[i]);c.drawLine(a.x,a.y,b.x,b.y,p)}
  p.style=Paint.Style.FILL;for((i,z) in places.withIndex()){val q=xy(z);p.color=Color.WHITE;c.drawCircle(q.x,q.y,11f,p);p.color=Color.rgb(49,94,251);c.drawCircle(q.x,q.y,6f,p);p.color=Color.DKGRAY;p.textSize=24f;c.drawText("${i+1}",q.x+13,q.y+8,p)}
 }
}
class MainActivity:Activity(){
 lateinit var db:Db;lateinit var map:MetroView;lateinit var list:LinearLayout;lateinit var status:TextView;var cityId=1L
 override fun onCreate(b:Bundle?){super.onCreate(b);db=Db(this);ui();refresh()}
 fun ui(){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(18,18,18,10)
  val title=TextView(this);title.text="Культурный маршрут";title.textSize=24f;root.addView(title)
  val sub=TextView(this);sub.text="Троицк, Челябинская область";sub.textSize=16f;root.addView(sub)
  val add=Button(this);add.text="Добавить Челябинск";root.addView(add,LinearLayout.LayoutParams(-1,55))
  map=MetroView(this);root.addView(map,LinearLayout.LayoutParams(-1,0,1.4f))
  val br=LinearLayout(this);val tour=Button(this);tour.text="Построить маршрут";val reset=Button(this);reset.text="Схема";br.addView(tour,LinearLayout.LayoutParams(0,55,1f));br.addView(reset,LinearLayout.LayoutParams(0,55,1f));root.addView(br)
  status=TextView(this);root.addView(status)
  val sv=ScrollView(this);list=LinearLayout(this);list.orientation=LinearLayout.VERTICAL;sv.addView(list);root.addView(sv,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)
  add.setOnClickListener{cityId=db.addCity();sub.text="Челябинск, Россия";refresh()}
  reset.setOnClickListener{map.route=emptyList();map.invalidate();status.text="Схема маршрутов"}
  tour.setOnClickListener{val ps=db.places(cityId);if(ps.isNotEmpty()){val r=mutableListOf(ps.first());val left=ps.drop(1).toMutableList();while(left.isNotEmpty()){val n=left.minBy{dist(r.last(),it)};r+=n;left.remove(n)};map.route=r;map.invalidate();status.text="Маршрут: %.1f км".format(java.util.Locale.US,r.zipWithNext().sumOf{dist(it.first,it.second)})}}
 }
 fun refresh(){val ps=db.places(cityId);map.places=ps;map.route=emptyList();map.invalidate();list.removeAllViews();ps.forEachIndexed{i,z->val t=TextView(this);t.text="${i+1}. ${z.name}\n   ${z.category}";t.textSize=16f;t.setPadding(8,12,8,12);list.addView(t)};status.text="${ps.size} объектов · демонстрационные данные"}
}