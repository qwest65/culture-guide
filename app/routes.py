from math import radians,sin,cos,asin,sqrt

def distance_km(a,b):
    r=6371.0088
    lat1,lon1,lat2,lon2=map(radians,[a["lat"],a["lon"],b["lat"],b["lon"]])
    dlat,dlon=lat2-lat1,lon2-lon1
    h=sin(dlat/2)**2+cos(lat1)*cos(lat2)*sin(dlon/2)**2
    return 2*r*asin(sqrt(h))

def mst(points):
    if len(points)<2:return []
    used={0};edges=[]
    while len(used)<len(points):
        best=None
        for i in used:
            for j in range(len(points)):
                if j in used:continue
                d=distance_km(points[i],points[j])
                if best is None or d<best[2]:best=(i,j,d)
        edges.append(best);used.add(best[1])
    return edges

def build_metro_lines(points):
    if not points:return []
    edges=mst(points);adj=[[] for _ in points]
    for eid,(a,b,_) in enumerate(edges):
        adj[a].append((b,eid));adj[b].append((a,eid))
    used=set();lines=[]
    for start in [i for i,x in enumerate(adj) if len(x)!=2]:
        for nxt,eid in adj[start]:
            if eid in used:continue
            line=[start];prev,cur=start,nxt;used.add(eid);line.append(cur)
            while len(adj[cur])==2:
                z=next((x for x in adj[cur] if x[0]!=prev and x[1] not in used),None)
                if not z:break
                prev,cur=cur,z[0];used.add(z[1]);line.append(cur)
            lines.append([points[i] for i in line])
    return lines or [points]

def build_tour(points):
    if not points:return {"route":[],"distance_km":0,"text":"Нет объектов"}
    left=list(points);route=[left.pop(0)];total=0
    while left:
        cur=route[-1]
        nxt=min(left,key=lambda p:distance_km(cur,p))
        total+=distance_km(cur,nxt);route.append(nxt);left.remove(nxt)
    return {"route":route,"distance_km":total,"text":"\n".join(f"{i+1}. {p['name']}" for i,p in enumerate(route))}
