// Card styles: every background, border and effect is drawn pixel by pixel on a canvas at
// 1 GUI pixel per canvas pixel, then shown at 2x. All art is original and procedural, so it can
// animate in the mod the same way: t is a time in seconds, and each loop lasts LOOP seconds.
const LOOP = 8;

function rng(seed) { let s = seed >>> 0; return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; }; }
const hex = c => [parseInt(c.slice(1,3),16), parseInt(c.slice(3,5),16), parseInt(c.slice(5,7),16)];
const lerp = (a,b,k) => a + (b-a)*k;
const BAYER = [[0,8,2,10],[12,4,14,6],[3,11,1,9],[15,7,13,5]].map(r=>r.map(v=>(v+.5)/16));

class Px {
  constructor(w,h){ this.w=w; this.h=h; this.c=document.createElement('canvas'); this.c.width=w; this.c.height=h;
    this.x=this.c.getContext('2d'); this.im=this.x.createImageData(w,h); this.d=this.im.data; }
  set(x,y,rgb,a=1){ x|=0;y|=0; if(x<0||y<0||x>=this.w||y>=this.h)return; const i=(y*this.w+x)*4,d=this.d;
    d[i]=lerp(d[i],rgb[0],a); d[i+1]=lerp(d[i+1],rgb[1],a); d[i+2]=lerp(d[i+2],rgb[2],a); d[i+3]=255; }
  flush(){ this.x.putImageData(this.im,0,0); return this.c.toDataURL(); }
}

// vertical gradient quantised to a palette with ordered dither, the classic pixel-art sky
function ditherSky(p, stops, h = p.h) {
  for (let y=0;y<p.h;y++) for (let x=0;x<p.w;x++) {
    const v = Math.min(y/h,.9999)*(stops.length-1), i=Math.floor(v), f=v-i;
    p.set(x,y,hex(stops[f>BAYER[y%4][x%4]?i+1:i]));
  }
}

// value noise, wrapped in x so nebula and cloud layers can scroll forever
function noiseField(w,h,cell,seed){ const r=rng(seed),gw=Math.ceil(w/cell),gh=Math.ceil(h/cell)+1,g=[];
  for(let j=0;j<gh;j++){g.push([]);for(let i=0;i<gw;i++)g[j].push(r());}
  return (x,y)=>{ x=((x%w)+w)%w; const fx=x/cell,fy=y/cell,i=Math.floor(fx),j=Math.floor(fy),u=fx-i,v=fy-j;
    const s=t=>t*t*(3-2*t), a=g[j%gh][i%gw],b=g[j%gh][(i+1)%gw],c=g[(j+1)%gh][i%gw],d=g[(j+1)%gh][(i+1)%gw];
    return lerp(lerp(a,b,s(u)),lerp(c,d,s(u)),s(v)); }; }

function twinkle(p, n, seed, t, cols, region) {
  const r=rng(seed);
  for(let i=0;i<n;i++){ const x=r()*p.w,y=r()*(region||p.h),ph=r()*6.283,sp=1+Math.floor(r()*3),big=r()<.14,c=cols[Math.floor(r()*cols.length)];
    const b=.5+.5*Math.sin(ph+t/LOOP*6.283*sp);
    p.set(x,y,hex(c),.3+.7*b);
    if(big&&b>.75){ p.set(x+1,y,hex(c),.5); p.set(x-1,y,hex(c),.5); p.set(x,y+1,hex(c),.5); p.set(x,y-1,hex(c),.5); }
  }
}

// The moon: 7 tones, a soft terminator and hand-placed craters. Drawn at 1 GUI pixel.
function moon(p, cx, cy, R, t) {
  // halo: dithered rings that breathe slowly
  const breathe=.5+.5*Math.sin(t/LOOP*6.283);
  for(let y=cy-R-22;y<=cy+R+22;y++)for(let x=cx-R-22;x<=cx+R+22;x++){ const d=Math.hypot(x-cx,y-cy);
    if(d>R){ const k=1-(d-R)/(16+4*breathe); if(k>0&&k*k*.55>BAYER[y&3][x&3]) p.set(x,y,hex('#8FA6E8'),.34+.1*breathe); } }
  // the game's own moon texture, added over the sky the way the game blends it
  const S=MOON_TEX.length, sc=Math.max(1,Math.round(2*R/S));
  const ox=cx-Math.floor(S*sc/2), oy=cy-Math.floor(S*sc/2);
  for(let j=0;j<S;j++)for(let i=0;i<S;i++){ const [r,g,b,a]=MOON_TEX[j][i]; if(!a||r+g+b<12) continue;
    for(let u=0;u<sc;u++)for(let v=0;v<sc;v++){ const X=ox+i*sc+u,Y=oy+j*sc+v; if(X<0||Y<0||X>=p.w||Y>=p.h) continue;
      const q=(Y*p.w+X)*4,d=p.d; d[q]=Math.min(255,d[q]+r); d[q+1]=Math.min(255,d[q+1]+g); d[q+2]=Math.min(255,d[q+2]+b); } }
}

const BG_FN = {
  moon(p,t,o){
    ditherSky(p,['#070A1F','#0D1433','#17204E','#26306B','#3A3F85'],p.h*1.05);
    twinkle(p,110,11,t,['#FFFFFF','#BFD0FF','#FFE9B8'],p.h*.8);
    moon(p,o.mx??56,o.my??46,o.mr??32,t);
    // thin clouds drifting across the moon, then a jagged hill line
    const cl=noiseField(120,p.h,24,5), sh=(t/LOOP)*120;
    for(let y=34;y<86;y++)for(let x=0;x<p.w;x++){ const n=cl(x+sh,y*2.4); if(n>.62){ const k=(n-.62)*3; if(k>BAYER[y&3][x&3]) p.set(x,y,hex('#5A5FA8'),.5); } }
    const hr=rng(3); let hgt=[]; for(let x=0;x<p.w;x+=6){ const v=8+hr()*10; for(let i=0;i<6;i++) hgt.push(v); }
    for(let x=0;x<p.w;x++) for(let y=p.h-hgt[x];y<p.h;y++) p.set(x,y,hex('#070A1A'));
  },
  stars(p,t){
    ditherSky(p,['#02030A','#050818','#0A0E26']);
    for(const [n,sp,col,seed] of [[170,0,['#4A5578'],2],[80,1,['#9AA8D0'],3],[36,2,['#FFFFFF','#BFE4FF','#FFD9B0'],4]]){ const r=rng(seed);
      for(let i=0;i<n;i++){ const x=(r()*p.w+t/LOOP*p.w*sp)%p.w, y=r()*p.h, c=col[Math.floor(r()*col.length)]; p.set(p.w-x,y,hex(c));
        if(sp==2){ p.set(p.w-x+1,y,hex(c),.4); p.set(p.w-x+2,y,hex(c),.15); } } }
    // one shooting star per loop
    const q=(t/LOOP+.55)%1; if(q<.3){ const k=q/.3, hx=p.w*(.95-k*.9), hy=8+k*p.h*.55, L=26; for(let i=0;i<L;i++) p.set(hx+i,hy-i*.45,[255,255,255],(1-i/L)*Math.sin(k*3.14159)); }
  },
  nebula(p,t){
    ditherSky(p,['#0A0520','#100830']);
    const a=noiseField(p.w,p.h,46,8), b=noiseField(p.w,p.h,22,9), c=noiseField(p.w,p.h,11,10), sh=t/LOOP*p.w;
    const pal=['#0A0520','#1E0F52','#3C1A86','#6B23B4','#A62CC6','#E8439F','#FF8FA6','#FFD9C2'].map(hex);
    for(let y=0;y<p.h;y++)for(let x=0;x<p.w;x++){ let n=a(x+sh,y)*.62+b(x-sh,y*1.2)*.28+c(x+sh,y)*.1;
      n=Math.pow(Math.max(0,n-.26)*1.72,1.5); const v=Math.min(.999,n)*(pal.length-1), i=Math.floor(v), f=v-i;
      p.set(x,y,pal[f>BAYER[y&3][x&3]?i+1:i]); }
    twinkle(p,70,21,t,['#FFFFFF','#FFD9F2','#CFE0FF']);
  },
  embers(p,t){
    ditherSky(p,['#0C0606','#1A0A08','#2E0F09','#511707','#8E2A05','#D84A08'],p.h*1.02);
    const r=rng(6); const pal=['#FFF2A8','#FFC233','#FF7A1A','#D23A0A'].map(hex);
    for(let i=0;i<46;i++){ const x0=r()*p.w, ph=r(), sp=1+Math.floor(r()*2), w=2+r()*5, big=r()<.3;
      const q=(ph+sp*t/LOOP)%1, y=p.h+4-q*(p.h+10), x=x0+Math.sin(q*12+ph*9)*w;
      const c=pal[Math.min(3,Math.floor(q*4))];
      p.set(x,y,c); if(big){ p.set(x+1,y,c); p.set(x,y+1,c); p.set(x+1,y+1,c); }
      p.set(x,y+1+(big?1:0),c,.5); p.set(x,y+2+(big?1:0),c,.22); }
    // rock crust along the bottom, its cracks pulsing with heat
    const hr=rng(13); let hgt=[]; for(let x=0;x<p.w;x+=4){ const v=4+hr()*7; for(let i=0;i<4;i++) hgt.push(v); }
    for(let x=0;x<p.w;x++) for(let y=p.h-hgt[x];y<p.h;y++) p.set(x,y,hex(((x>>2)+(y>>2))%5==0?'#3A1206':'#160807'));
  },
  snow(p,t){
    ditherSky(p,['#10203E','#1B3158','#2C4A78','#4A6C9C','#7F9FC4'],p.h*1.04);
    // pine silhouettes, far row then near row
    for(const [col,base,seed] of [['#274468',p.h-8,4],['#16294A',p.h-2,9]]){ const r=rng(seed);
      for(let x=-6;x<p.w;x+=14+Math.floor(r()*14)){ const ht=26+Math.floor(r()*22);
        for(let y=0;y<ht;y++){ const w=Math.floor((y%8+y/3)*.62)+1; for(let i=-w;i<=w;i++) p.set(x+i,base-ht+y,hex(col)); } } }
    for(let x=0;x<p.w;x++){ const hh=7+Math.round(2*Math.sin(x/19)+1.2*Math.sin(x/7)); for(let y=p.h-hh;y<p.h;y++) p.set(x,y,hex(y==p.h-hh?'#FFFFFF':y<p.h-hh+3?'#DCE9F7':'#B9CCE4')); }
    for(const [n,sp,sz,seed,a] of [[60,1,1,1,.55],[34,2,1,2,.85],[16,3,2,3,1]]){ const r=rng(seed);
      for(let i=0;i<n;i++){ const x0=r()*p.w, ph=r(), q=(ph+sp*t/LOOP)%1, y=q*(p.h+4)-2, x=(x0+Math.sin(q*9+ph*20)*5)%p.w;
        for(let a1=0;a1<sz;a1++)for(let b1=0;b1<sz;b1++) p.set(x+a1,y+b1,[255,255,255],a); } }
  },
  plain(p,t,o){ const c=hex(o.accent||'#68B5D5');
    for(let y=0;y<p.h;y++)for(let x=0;x<p.w;x++){ p.set(x,y,[26,31,35]); if((x+y)%4==0&&y<p.h) p.set(x,y,c,.05); } },
};

// ---- effects drawn over the scene (never over the data rows) ----
const FX_FN = {
  sparkles(p,t,o){ const r=rng(44), n=Math.round(34*(o.intensity??.6)), cols=[hex(o.accent||'#FFE9A8'),[255,255,255]];
    const size=[[[0,0]],[[0,0],[1,0],[-1,0],[0,1],[0,-1]],[[0,0],[1,0],[-1,0],[0,1],[0,-1],[2,0],[-2,0],[0,2],[0,-2]],[[0,0],[1,0],[-1,0],[0,1],[0,-1],[2,0],[-2,0],[0,2],[0,-2],[3,0],[-3,0],[0,3],[0,-3],[1,1],[-1,1],[1,-1],[-1,-1]]];
    for(let i=0;i<n;i++){ const x=r()*(o.fxw||p.w),y=r()*p.h,ph=r(),q=(ph+t/LOOP*(1+Math.floor(r()*2)))%1,c=cols[i%2];
      if(q>.42) continue; const s=Math.sin(q/.42*3.14159); const idx=Math.min(3,Math.floor(s*4));
      for(const [a,b] of size[idx]) p.set(x+a,y+b,(a==0&&b==0)?[255,255,255]:c,1); } },
  motes(p,t,o){ const r=rng(77), n=Math.round(70*(o.intensity??.6)), c=hex(o.accent||'#9FE8FF');
    for(let i=0;i<n;i++){ const x0=r()*(o.fxw||p.w), ph=r(), big=r()<.45, q=(ph+t/LOOP)%1, y=p.h-q*p.h, x=x0+Math.sin(q*6.283+ph*7)*7;
      const a=Math.min(1,Math.sin(q*3.14159)*1.4); p.set(x,y,c,a); if(big){ p.set(x+1,y,c,a*.8); p.set(x,y+1,c,a*.8); p.set(x+1,y+1,c,a*.5); p.set(x-1,y,c,a*.25); p.set(x+2,y,c,a*.25); p.set(x,y-1,c,a*.25); p.set(x,y+2,c,a*.25);} } },
};

// ---- borders, on their own canvas so they frame the card cleanly ----
function perim(w,h){ const pts=[]; for(let x=0;x<w;x++)pts.push([x,0]); for(let y=1;y<h;y++)pts.push([w-1,y]); for(let x=w-2;x>=0;x--)pts.push([x,h-1]); for(let y=h-2;y>0;y--)pts.push([0,y]); return pts; }
function clearPx(p){ p.d.fill(0); }
const BORDER_FN = {
  thin(p,t,o){ for(const [x,y] of perim(p.w,p.h)) p.set(x,y,hex(o.accent)); },
  double(p,t,o){ for(const [x,y] of perim(p.w,p.h)) p.set(x,y,hex(o.accent)); for(const [x,y] of perim(p.w-6,p.h-6)) p.set(x+3,y+3,hex(o.accent),.8); },
  glow(p,t,o){ const c=hex(o.accent), b=.65+.35*Math.sin(t/LOOP*6.283);
    for(const [i,a] of [[3,.16*b],[2,.34*b],[1,.62*b]]) for(const [x,y] of perim(p.w-i*2,p.h-i*2)) p.set(x+i,y+i,c,a);
    for(const [x,y] of perim(p.w,p.h)) p.set(x,y,mix(c,[255,255,255],.55)); },
  shimmer(p,t,o){ const c=hex(o.accent), pts=perim(p.w,p.h), L=pts.length, head=(t/LOOP)*L;
    pts.forEach(([x,y],i)=>{ let d=(head-i+L)%L, d2=(head+L/2-i+L)%L; const k=Math.max(0,1-Math.min(d,d2)/110);
      p.set(x,y,mix(c,[255,255,255],k),1); const nx=x==0?1:x==p.w-1?-1:0, ny=y==0?1:y==p.h-1?-1:0; p.set(x+nx,y+ny,mix(c,[255,255,255],k*.8),.25+k*.75); if(k>.5) p.set(x+nx*2,y+ny*2,mix(c,[255,255,255],k*.6),k*.7); });
    for(const off of [0,L/2]){ const [hx,hy]=pts[Math.floor(head+off)%L]; for(const [a,b] of [[0,0],[1,0],[-1,0],[0,1],[0,-1],[2,0],[-2,0],[0,2],[0,-2],[3,0],[-3,0],[0,3],[0,-3]]) p.set(hx+a,hy+b,[255,255,255]); } },
  corners(p,t,o){ const c=hex(o.accent), w=mix(c,[255,255,255],.6);
    for(const [x,y] of perim(p.w,p.h)) p.set(x,y,c,.55);
    const G=['..wccccccccc','.wwcc.......','wwccc.......','cccc........','cc.c........','c...........','c...........','c...........','c...........'];
    const put=(fx,fy)=>G.forEach((r,j)=>[...r].forEach((ch,i)=>{ if(ch=='.')return; for(let a=0;a<2;a++)for(let b=0;b<2;b++) p.set(fx?p.w-1-i*2-a:i*2+a, fy?p.h-1-j*2-b:j*2+b, ch=='w'?w:c); }));
    put(0,0);put(1,0);put(0,1);put(1,1); },
};
const mix = (a,b,k) => [lerp(a[0],b[0],k),lerp(a[1],b[1],k),lerp(a[2],b[2],k)];

function drawScene(w,h,o,t){ const p=new Px(w,h); BG_FN[o.bg](p,t,o); (o.fx||[]).forEach(f=>FX_FN[f](p,t,o)); return p.flush(); }
function drawBorder(w,h,o,t){ const p=new Px(w,h); BORDER_FN[o.border||'thin'](p,t,o); return p.flush(); }
