from kivy.graphics import Color, Line, Ellipse, Rectangle
from kivy.uix.widget import Widget
from kivy.properties import ListProperty

class MetroWidget(Widget):
    lines = ListProperty([])
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.bind(pos=self.redraw, size=self.redraw)
        self.bind(lines=self.redraw)
    def set_lines(self, lines):
        self.lines = lines or []
        self.redraw()
    def _project(self, p, all_points):
        if not all_points: return self.center
        lats=[x["lat"] for x in all_points]; lons=[x["lon"] for x in all_points]
        minlat,maxlat,minlon,maxlon=min(lats),max(lats),min(lons),max(lons)
        return (self.x+24+(p["lon"]-minlon)/((maxlon-minlon) or 1)*max(1,self.width-48),
                self.y+24+(p["lat"]-minlat)/((maxlat-minlat) or 1)*max(1,self.height-48))
    def redraw(self,*_):
        self.canvas.clear()
        points=[p for line in self.lines for p in line]
        with self.canvas:
            Color(.98,.98,.99,1); Rectangle(pos=self.pos,size=self.size)
            Color(.86,.87,.90,1)
            for x in range(int(self.x),int(self.right),35): Line(points=[x,self.y,x,self.top],width=.45)
            for y in range(int(self.y),int(self.top),35): Line(points=[self.x,y,self.right,y],width=.45)
            palette=[(.12,.42,.86,1),(.88,.24,.18,1),(.12,.62,.36,1),(.62,.25,.78,1),(.92,.56,.12,1),(.10,.60,.66,1)]
            for idx,line in enumerate(self.lines):
                if not line: continue
                Color(*palette[idx%len(palette)]); pts=[]
                for p in line:
                    x,y=self._project(p,points); pts += [x,y]
                if len(pts)>=4: Line(points=pts,width=2.4)
                for p in line:
                    x,y=self._project(p,points)
                    Color(1,1,1,1); Ellipse(pos=(x-6,y-6),size=(12,12))
                    Color(*palette[idx%len(palette)]); Ellipse(pos=(x-3.5,y-3.5),size=(7,7))
