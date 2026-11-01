// Shared mockup helpers. Tier data below is REAL, fetched 2026-11-01 from the public endpoints in
// docs/SPEC.md. PVPHQ returned 502 during the fetch, so it is shown as unavailable.
const TEX = '../_tmp/vanilla';
const LOGO = '../_tmp/logos/marks';
const BG = '../_tmp/bg';

const TIER = { HT1:'#FFCF4A', LT1:'#E6B422', HT2:'#D0D6E0', LT2:'#9FA8B8', HT3:'#E08A4F', LT3:'#B8703F',
               HT4:'#7FB069', LT4:'#5E8A4E', HT5:'#8A8F98', LT5:'#6A6F78',
               // McPvP's extra steps sit between High and Low of the same level
               MT1:'#F2C236', MT2:'#B8BFCC', MT3:'#CC7D47', MT4:'#6E9D5B', MT5:'#7A7F88' };

function ic(kit, s = 2) { return `<img src="${TEX}/kit/${kit}.png" width="${16*s}" height="${16*s}">`; }
const HUDT = `${TEX}/cur/gui/sprites/hud`;
const sprite = (p, w, h, s = 2, st = '') => `<img src="${HUDT}/${p}.png" width="${w*s}" height="${h*s}" style="${st}">`;
function lm(list, s = 2) { const n = 16*s, f = n > 64 ? 128 : n > 32 ? 64 : 32; return `<img src="${LOGO}/${list}-${f}.png" width="${n}" height="${n}" style="image-rendering:auto">`; }

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
  const row = (bg, full) => Array(10).fill(0).map(() => `<span style="position:relative;display:block;width:16px;height:18px">${sprite(bg,9,9,2,'position:absolute;left:0;top:0')}${sprite(full,9,9,2,'position:absolute;left:0;top:0')}</span>`).join('');
  const slots = items.map((k, i) => `<div class="abs" style="left:${6 + i*40}px;top:6px;width:32px;height:32px">${ic(k,2)}${counts[i]?`<span class="sh abs" style="right:-4px;bottom:-6px;color:#fff">${counts[i]}</span>`:''}</div>`).join('');
  return `
  <div class="abs" style="left:944px;top:524px;mix-blend-mode:difference">${sprite('crosshair',15,15,2)}</div>
  <div class="abs" style="left:778px;top:992px;display:flex">${row('heart/container','heart/full')}</div>
  <div class="abs" style="left:982px;top:992px;display:flex;transform:scaleX(-1)">${row('food_empty','food_full')}</div>
  <div class="abs" style="left:778px;top:1012px;width:364px;height:10px;overflow:hidden">${sprite('experience_bar_background',182,5,2)}<div class="abs" style="left:0;top:0;width:160px;height:10px;overflow:hidden">${sprite('experience_bar_progress',182,5,2)}</div></div>
  <div class="abs" style="left:778px;top:1036px;width:364px;height:44px">${sprite('hotbar',182,22,2)}${slots}</div>
  <div class="abs" style="left:${776+sel*40}px;top:1034px">${sprite('hotbar_selection',24,23,2)}</div>`;
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
  name:'JokingDK', region:'EU', country:'DK', title:'Combat Ace', badge:'combat_ace.webp', points:102,
  best:{rank:'#1', list:'McPvP', beta:1}, total:529,
  model:`${BG}/m-joking.png`,
  rows:[
    { id:'mctiers',  pts:102, rank:56,  kits:[{k:'smp',t:'HT2',r:1,d:'19 Aug 2026'},{k:'vanilla',t:'LT2',r:1,d:'23 Apr 2025'},{k:'nethop',t:'HT3',d:'16 Nov 2025'},{k:'sword',t:'LT3',p:'HT3',d:'6 Apr 2025'},{k:'mace',t:'HT4',p:'LT3',d:'8 Dec 2025'}]},
    { id:'mcpvp',    pts:280, rank:1,   kits:[{k:'smp',t:'HT1'},{k:'shield',t:'LT1'},{k:'cart',t:'LT1'},{k:'mace',t:'HT2'},{k:'vanilla',t:'HT2'}]},
    { id:'pvptiers', pts:64,  rank:175, kits:[{k:'vanilla',t:'HT3',d:'26 Apr 2025'},{k:'smp',t:'LT3',p:'LT2',d:'28 Aug 2025'},{k:'pot',t:'LT3',p:'HT3',d:'2 Feb 2025'},{k:'axe',t:'LT3',d:'2 Feb 2025'},{k:'sword',t:'HT4',p:'HT3',d:'2 Feb 2025'}]},
    { id:'subtiers', pts:83,  rank:160, kits:[{k:'diamond_smp',t:'LT1',r:1,d:'7 Jan 2025'},{k:'bed',t:'LT3',d:'3 Dec 2024'},{k:'speed',t:'LT3',d:'3 Dec 2024'},{k:'vanilla',t:'LT3',d:'23 May 2025'},{k:'trident',t:'HT4',d:'30 Sep 2025'}]},
    { id:'pvphq',    down:true, kits:[] },
  ],
  focus:{ list:'MCTiers', k:'smp', kit:'SMP', t:'HT2', r:1, ago:'attained 2 months ago' },
};
const MARLOWWW = {
  name:'Marlowww', region:'NA', title:'Combat Grandmaster', badge:'combat_grandmaster.webp', points:450,
  best:{rank:'#1', list:'MCTiers'}, total:450, model:`${BG}/m-marlowww.png`,
  rows:[
    { id:'mctiers', pts:450, rank:1, kits:[{k:'sword',t:'HT1',r:1,d:'19 Aug 2026'},{k:'mace',t:'HT1',r:1,d:'19 Aug 2026'},{k:'nethop',t:'HT1',r:1,d:'19 Aug 2026'},{k:'pot',t:'HT1',r:1,d:'8 Dec 2025'},{k:'vanilla',t:'HT1',r:1,d:'24 Nov 2024'}]},
    { id:'mcpvp', none:true, kits:[] },
    { id:'pvptiers', none:true, kits:[] },
    { id:'subtiers', none:true, kits:[] },
    { id:'pvphq', down:true, kits:[] },
  ],
  focus:{ list:'MCTiers', k:'sword', kit:'Sword', t:'HT1', r:1, ago:'attained 2 months ago' },
};
function listLabel(id) { const l = LISTS[id]; return `${l.name}${l.beta?' '+betaTag:''}`; }
function mount(html) { document.body.insertAdjacentHTML('beforeend', html); }
