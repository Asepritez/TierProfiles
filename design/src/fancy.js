// Profile cards with a scene behind them: full size, and the small tile used on pickers.
const MODEL = {
  hero: `${BG}/m-armor-hero.png`, sm: `${BG}/m-armor-sm.png`,
};

function heroCard(p, o, t = 0) {
  const W = o.w || 732, H = o.h || 400, gw = W / 2, gh = H / 2;
  const scene = drawScene(gw, gh, {...o, fxw: 112}, t);
  const border = drawBorder(gw, gh, o, t);
  const ac = o.accent || '#FFCF4A';
  const row = (r) => {
    const l = LISTS[r.id], st = r.down ? 'Unavailable right now' : r.none ? 'Not ranked' : '';
    return `<div class="row" style="height:46px;gap:8px;padding:0 8px;background:rgba(0,0,0,.22)">${lm(l.mark)}
      <span class="nw" style="width:${l.beta?124:124}px;color:${st?'#AAA':'#fff'}">${l.name}${l.beta?' '+betaTag:''}</span>
      ${st?`<span class="muted nw">${st}</span>`:r.kits.slice(0,3).map(k=>`<span class="row gap2">${ic(k.k)}${chip(k.t,{ret:k.r,peak:k.p})}</span>`).join('<span style="width:2px"></span>')}</div>`;
  };
  const f = o.featured || {k:'smp',t:'HT1',list:'McPvP',beta:1};
  return `<div style="position:relative;width:${W}px;height:${H}px;overflow:hidden">
    <img src="${scene}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
    <img src="${o.model||MODEL.hero}" class="abs" style="left:${o.mdx??34}px;bottom:6px;height:${o.mh||310}px;width:auto">
    <div class="abs col" style="left:186px;top:16px;right:14px;bottom:14px;background:rgba(7,9,16,.74);box-shadow:inset 0 0 0 2px rgba(255,255,255,.08);padding:8px">
      <div class="row gap8" style="height:30px"><span class="mid nw" style="color:#fff;text-shadow:2px 2px 0 #000">${p.name}</span>
        <span class="nw" style="background:${ac};color:#101214;padding:0 6px;line-height:22px">${p.region}</span></div>
      <div class="row gap8" style="height:36px"><span class="nw" style="color:${ac}">${p.title}</span><span class="sp"></span>
        ${ic(f.k)}${chip(f.t,{cls:'',style:'height:26px'})}<span class="muted nw">${f.list}${f.beta?' '+betaTag:''}</span></div>
      <div class="col" style="gap:2px;margin-top:2px">${p.rows.map(row).join('')}</div>
      <div class="sp"></div>
      <div class="row gap8 nw muted" style="height:24px;padding:0 8px">${ic(p.focus.k,1)}${p.focus.list} ${p.focus.kit} <span>${p.focus.ago}</span></div>
    </div>
    <img src="${border}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
  </div>`.replace(/<img src="data:image\/png;base64,([^"]+)" width="(\d+)" height="(\d+)" class="abs" style="left:0;top:0">/g,(m)=>m);
}

function miniCard(o, t = 0) {
  const W = 368, H = 190, gw = W / 2, gh = H / 2;
  const o2 = {mx:24, my:24, mr:19, fxw:gw, ...o};
  const scene = drawScene(gw, gh, o2, t);
  const border = drawBorder(gw, gh, o2, t);
  const ac = o.accent || '#FFCF4A';
  return `<div style="position:relative;width:${W}px;height:${H}px;overflow:hidden">
    <img src="${scene}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
    <img src="${MODEL.sm}" class="abs" style="left:34px;bottom:6px;height:136px;width:auto">
    <div class="abs col gap4" style="left:128px;top:12px;right:12px;padding:6px 8px;background:rgba(7,9,16,.7)">
      <span class="nw" style="color:#fff">JokingDK</span><span class="nw" style="color:${ac}">Combat Ace</span>
      <div class="row gap4">${ic('smp')}${chip('HT1')}${ic('shield')}${chip('LT1')}</div></div>
    <img src="${border}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
  </div>`;
}
