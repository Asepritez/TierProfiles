// Shared mockup helpers. Tier data below is REAL, fetched 2026-11-01 from the public endpoints in
// docs/SPEC.md. PVPHQ returned 502 during the fetch, so it is shown as unavailable.
const TEX = '../../mod/src/main/resources/assets/tierprofiles/textures';
const BG = '../_tmp/bg';

const TIER = { HT1:'#FFCF4A', LT1:'#E6B422', HT2:'#D0D6E0', LT2:'#9FA8B8', HT3:'#E08A4F', LT3:'#B8703F',
               HT4:'#7FB069', LT4:'#5E8A4E', HT5:'#8A8F98', LT5:'#6A6F78',
               // McPvP's extra steps sit between High and Low of the same level
               MT1:'#F2C236', MT2:'#B8BFCC', MT3:'#CC7D47', MT4:'#6E9D5B', MT5:'#7A7F88' };

function ic(kit, s = 2) { return `<img src="${TEX}/kit/${kit}.png" width="${16*s}" height="${16*s}">`; }
function lm(list, s = 2) { return `<img src="${TEX}/list/${list}.png" width="${16*s}" height="${16*s}">`; }

// one tier chip. o: {ret, peak, big}
function chip(t, o = {}) {
  const col = (o.pal||TIER)[t]||'#777';
  const st = o.ret ? `background:#0E1012;color:${col};box-shadow:inset 0 0 0 2px ${col};` : `background-color:${col};`;
  return `<span class="chip ${o.cls||''}" style="${st}${o.style||''}">${t}${o.peak?peakMark():''}</span>`;
}
// peak marker: a tiny pixel arrow that sits on the chip's corner
function peakMark(inline) {
  const pos = inline ? 'position:relative;display:inline-block;' : 'position:absolute;right:-6px;top:-8px;';
  return `<span style="${pos}width:10px;height:8px;background:#101214;clip-path:polygon(40% 0,60% 0,60% 25%,80% 25%,80% 50%,100% 50%,100% 100%,0 100%,0 50%,20% 50%,20% 25%,40% 25%)"><span style="position:absolute;left:4px;top:2px;width:2px;height:2px;background:#fff"></span><span style="position:absolute;left:2px;top:4px;width:6px;height:2px;background:#fff"></span></span>`;
}

// small original HUD art, as pixel grids drawn to a canvas
function pxArt(rows, pal, s = 2) {
  const h = rows.length, w = rows[0].length;
  const c = document.createElement('canvas'); c.width = w; c.height = h;
  const x = c.getContext('2d');
  rows.forEach((r, j) => [...r].forEach((ch, i) => { if (pal[ch]) { x.fillStyle = pal[ch]; x.fillRect(i, j, 1, 1); } }));
  return `<img src="${c.toDataURL()}" width="${w*s}" height="${h*s}">`;
}
const HEART = ['.kk.kk...','krrkrrk..','krwrrrrk.'.slice(0,9),'krrrrrrk.','.krrrrk..','..krrk...','...kk....'].map(s=>s.padEnd(9,'.'));
const SHANK = ['....kk...','...kbbk..','..kbbbk..','.kbbbbk..','.kbbbk...','kwkkk....','kk.......'].map(s=>s.padEnd(9,'.'));
function hud(sel = 0, items = ['sword','pot','early_game','smp','axe','shield','bed','cart','bow'], counts = {3:7, 2:12, 1:3}) {
  const P = {k:'#1a0a0a', r:'#F03030', w:'#FFB0B0', b:'#C98B4A'};
  const hearts = Array(10).fill(0).map(()=>pxArt(HEART,P,2)).join('');
  const food = Array(10).fill(0).map(()=>pxArt(SHANK,P,2)).join('');
  const slots = items.map((k,i)=>`<div style="width:40px;height:40px;background:rgba(0,0,0,.42);box-shadow:inset 0 0 0 2px rgba(120,120,120,.55);position:relative;display:flex;align-items:center;justify-content:center">
     ${ic(k,2)}${counts[i]?`<span class="sh" style="position:absolute;right:0;bottom:-4px;color:#fff">${counts[i]}</span>`:''}</div>`).join('');
  return `
  <div class="abs" style="left:958px;top:530px;width:4px;height:20px;background:#fff;mix-blend-mode:difference"></div>
  <div class="abs" style="left:950px;top:538px;width:20px;height:4px;background:#fff;mix-blend-mode:difference"></div>
  <div class="abs" style="left:778px;top:1002px;width:364px;height:14px;display:flex;justify-content:space-between"><div class="row" style="gap:0">${hearts}</div><div class="row" style="gap:0;transform:scaleX(-1)">${food}</div></div>
  <div class="abs" style="left:778px;top:1022px;width:364px;height:10px;background:#1a1a1a;box-shadow:0 0 0 2px rgba(0,0,0,.5)"><div style="height:10px;width:44%;background:#7ED848"></div></div>
  <div class="abs" style="left:778px;top:1036px;width:364px;height:44px;display:flex;gap:0;padding:2px;background:rgba(0,0,0,.25);box-shadow:inset 0 0 0 2px #222">${slots}</div>
  <div class="abs" style="left:${780+sel*40-2}px;top:1036px;width:44px;height:44px;box-shadow:inset 0 0 0 4px #E8E8E8"></div>`;
}

// ---------------- real data ----------------
const LISTS = {
  mctiers:  { name:'MCTiers',  short:'MCT',  mark:'mctiers' },
  mcpvp:    { name:'McPvP',    short:'McPvP', mark:'mcpvp', beta:true },
  pvptiers: { name:'PvPTiers', short:'PvPT', mark:'pvptiers' },
  subtiers: { name:'SubTiers', short:'SubT', mark:'subtiers' },
  pvphq:    { name:'PVPHQ',    short:'HQ',   mark:'pvphq' },
};
const betaTag = `<span style="color:#FFCF4A">beta</span>`;

// k: kit icon id, t: tier, p: peak if different, r: retired, m: months since attained
const JOKING = {
  name:'JokingDK', region:'EU', country:'DK', title:'Combat Ace', points:102,
  model:`${BG}/m-joking.png`,
  rows:[
    { id:'mctiers',  pts:102, kits:[{k:'smp',t:'HT2',r:1,m:2},{k:'vanilla',t:'LT2',r:1,m:18},{k:'nethop',t:'HT3',m:11},{k:'sword',t:'LT3',p:'HT3',m:18},{k:'mace',t:'HT4',p:'LT3',m:10}]},
    { id:'mcpvp',    pts:280, kits:[{k:'smp',t:'HT1',m:0},{k:'shield',t:'LT1',m:0},{k:'cart',t:'LT1',m:0},{k:'mace',t:'HT2',m:0},{k:'vanilla',t:'HT2',m:0}]},
    { id:'pvptiers', pts:64,  kits:[{k:'vanilla',t:'HT3',m:18},{k:'smp',t:'LT3',p:'LT2',m:14},{k:'pot',t:'LT3',p:'HT3',m:20},{k:'axe',t:'LT3',m:20},{k:'sword',t:'HT4',p:'HT3',m:20}]},
    { id:'subtiers', pts:83,  kits:[{k:'diamond_smp',t:'LT1',r:1,m:21},{k:'bed',t:'LT3',m:22},{k:'speed',t:'LT3',m:22},{k:'vanilla',t:'LT3',m:18},{k:'trident',t:'HT4',m:13}]},
    { id:'pvphq',    down:true, kits:[] },
  ],
  focus:{ list:'MCTiers', k:'smp', kit:'SMP', t:'HT2', r:1, ago:'attained 2 months ago' },
};
const MARLOWWW = {
  name:'Marlowww', region:'NA', title:'Combat Grandmaster', points:450, model:`${BG}/m-marlowww.png`,
  rows:[
    { id:'mctiers', pts:450, kits:[{k:'sword',t:'HT1',r:1,m:2},{k:'mace',t:'HT1',r:1,m:2},{k:'nethop',t:'HT1',r:1,m:2},{k:'pot',t:'HT1',r:1,m:10},{k:'vanilla',t:'HT1',r:1,m:23}]},
    { id:'mcpvp', none:true, kits:[] },
    { id:'pvptiers', none:true, kits:[] },
    { id:'subtiers', none:true, kits:[] },
    { id:'pvphq', down:true, kits:[] },
  ],
  focus:{ list:'MCTiers', k:'sword', kit:'Sword', t:'HT1', r:1, ago:'attained 2 months ago' },
};
function listLabel(id) { const l = LISTS[id]; return `${l.name}${l.beta?' '+betaTag:''}`; }
function mount(html) { document.body.insertAdjacentHTML('beforeend', html); }
