import json
import sqlite3
from pathlib import Path

class Database:
    def __init__(self, data_dir):
        self.path = Path(data_dir) / "culture_guide.sqlite3"
        self.path.parent.mkdir(parents=True, exist_ok=True)
        self.conn = sqlite3.connect(self.path)
        self.conn.row_factory = sqlite3.Row
        self.conn.execute("PRAGMA foreign_keys=ON")
        self.conn.executescript("""
        CREATE TABLE IF NOT EXISTS cities(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            country TEXT NOT NULL,
            lat REAL NOT NULL,
            lon REAL NOT NULL
        );
        CREATE TABLE IF NOT EXISTS places(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            city_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            category TEXT NOT NULL,
            description TEXT DEFAULT '',
            lat REAL NOT NULL,
            lon REAL NOT NULL,
            photo TEXT DEFAULT '',
            FOREIGN KEY(city_id) REFERENCES cities(id) ON DELETE CASCADE
        );
        CREATE INDEX IF NOT EXISTS idx_places_city ON places(city_id);
        """)
        self.conn.commit()

    def seed_if_empty(self, path):
        if self.conn.execute("SELECT COUNT(*) FROM cities").fetchone()[0]:
            return
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        for c in data["cities"]:
            cid = self.add_city(c["name"],c["country"],c["lat"],c["lon"])
            for p in c.get("places",[]):
                self.add_place(cid,p["name"],p["category"],p.get("description",""),p["lat"],p["lon"])

    def list_cities(self):
        return [dict(x) for x in self.conn.execute("SELECT * FROM cities ORDER BY name")]

    def get_city(self, city_id):
        x=self.conn.execute("SELECT * FROM cities WHERE id=?",(city_id,)).fetchone()
        return dict(x) if x else None

    def add_city(self,name,country,lat,lon):
        x=self.conn.execute("INSERT INTO cities(name,country,lat,lon) VALUES(?,?,?,?)",(name,country,lat,lon))
        self.conn.commit()
        return x.lastrowid

    def add_place(self,city_id,name,category,description,lat,lon):
        x=self.conn.execute("INSERT INTO places(city_id,name,category,description,lat,lon) VALUES(?,?,?,?,?,?)",(city_id,name,category,description,lat,lon))
        self.conn.commit()
        return x.lastrowid

    def list_places(self,city_id):
        return [dict(x) for x in self.conn.execute("SELECT * FROM places WHERE city_id=? ORDER BY name",(city_id,))]

    def search_places(self,text,city_id):
        return [dict(x) for x in self.conn.execute("SELECT * FROM places WHERE city_id=? AND name LIKE ? ORDER BY name",(city_id,"%"+text+"%"))]
