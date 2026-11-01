// The settings screen frame. Same structure as Tallium (tab row, staged Apply bar), with more
// character: kit icons on tabs, pixel bevels, a dither backdrop and a search that spans every tab.
const TABS = [['Tags','sword'],['Colours','pot'],['Rules','end_game'],['Profile','vanilla'],['Sharing','elytra'],['Keys','speed'],['More','shield']];

function shell(active, body, o = {}) {
  return `<div class="shell"></div>
  <div class="abs row gap12" style="left:20px;top:12px;right:20px;height:40px">
    ${ic('vanilla')}<span class="nw" style="color:#fff">TierProfiles</span><span class="muted nw">Settings: ${o.profile||'Casual'}</span>
    <span class="sp"></span>
    <div class="field" style="width:560px">${searchGlyph()}<span class="${o.search?'':'muted'}">${o.search||'Search every setting'}</span><span class="sp"></span>${o.search?'<span class="muted">x</span>':'<span class="muted">Ctrl F</span>'}</div>
    <div class="btn">Manage profiles</div>
  </div>
  <div class="abs row gap6" style="left:20px;top:60px;right:20px">${TABS.map(([t,k])=>`<div class="tab ${t===active?'on':''}">${ic(k)}<span>${t}</span></div>`).join('')}</div>
  ${body}
  <div class="abs row gap12" style="left:20px;right:20px;bottom:12px;height:36px">
    <span class="row gap8 nw" style="color:${o.changes?'#FFCF4A':'#AAA'}">${o.changes?`<i style="width:12px;height:12px;background:#FFCF4A;display:inline-block"></i>${o.changes} changes staged until Apply`:'Changes are staged until Apply.'}</span>
    <span class="sp"></span><div class="btn">Reset tab</div><div class="btn">Discard</div><div class="btn on">Apply</div>
  </div>`;
}
function searchGlyph() {
  return pxArt(['..kkkk..','.k....k.','k......k','k......k','k......k','.k....k.','..kkkkk.','......kk'.slice(0,8)],{k:'#AAAAAA'},2);
}
const chev = open => `<span style="width:12px;display:inline-block;color:#68B5D5">${open?'v':'&gt;'}</span>`;
function group(title, kit, open, count, inner = '') {
  return `<div class="group">${chev(open)}${kit?ic(kit):''}<span class="nw">${title}</span><span class="sp"></span>${count?`<span class="muted nw" style="font-size:18px">${count}</span>`:''}</div>
    ${open?`<div class="col" style="padding:6px 2px 8px;gap:4px">${inner}</div>`:''}`;
}
const opt = (label, ctl) => `<div class="row" style="min-height:36px;gap:8px"><span class="nw sp">${label}</span>${ctl}</div>`;
const drop = (v, w = 190) => `<div class="btn" style="width:${w}px;justify-content:space-between;padding:0 8px"><span class="nw">${v}</span><span class="muted">v</span></div>`;
const seg = (items, on) => `<div class="row" style="gap:0">${items.map(i=>`<div class="btn ${i===on?'on':''}" style="margin-left:-2px;padding:0 10px">${i}</div>`).join('')}</div>`;
const sw = on => `<span class="switch ${on?'on':''}"></span>`;
const chk = on => `<span class="check ${on?'on':''}"></span>`;
const slider = (pct, label) => `<div class="row gap8" style="width:230px"><div class="slider"><i></i><b style="width:${pct}%"></b><u style="left:${pct}%"></u></div>${label?`<span class="nw" style="width:48px;text-align:right">${label}</span>`:''}</div>`;
const tierLadder = (min) => `<div class="row" style="gap:2px">${RANK_ALL.map(t=>{const lit=RANK_ALL.indexOf(t)<=RANK_ALL.indexOf(min);return `<span class="chip" style="min-width:0;width:44px;padding:0;background:${lit?TIER[t]:'#23272A'};color:${lit?'#101214':'#666'};${t===min?'box-shadow:0 0 0 2px #fff;z-index:1;':''}">${t}</span>`}).join('')}</div>`;
const RANK_ALL = ['HT1','LT1','HT2','LT2','HT3','LT3','HT4','LT4','HT5','LT5'];
