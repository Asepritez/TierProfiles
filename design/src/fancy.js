// Profile cards with a scene behind them: full size, and the small tile used on pickers.
const MODEL = {
  card: `${BG}/m-armor-card.png`, hero: `${BG}/m-armor-hero.png`, sm: `${BG}/m-armor-sm.png`,
};

function heroCard(p, o, t = 0) { return defaultCard(p, {...o, t, model: MODEL.card, w: o.w || 732}); }

function miniCard(o, t = 0) {
  const W = 368, H = 190, gw = W / 2, gh = H / 2;
  const o2 = {mx:24, my:24, mr:16, fxw:gw, ...o};
  const scene = drawScene(gw, gh, o2, t);
  const border = drawBorder(gw, gh, o2, t);
  const ac = o.accent || '#FFCF4A';
  return `<div style="position:relative;width:${W}px;height:${H}px;overflow:hidden">
    <img src="${scene}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
    <img src="${MODEL.sm}" class="abs" style="left:34px;bottom:6px;height:136px;width:auto">
    <div class="abs col gap4" style="left:128px;top:12px;right:12px;padding:6px 8px;background:rgba(7,9,16,.7)">
      <span class="nw" style="color:#fff">JokingDK</span><span class="nw" style="color:${ac}">Combat Ace</span>
      <div class="row gap8">${kitTag({k:'smp',t:'HT1'},{kw:88})}${kitTag({k:'shield',t:'LT1'},{kw:88})}</div></div>
    <img src="${border}" width="${W}" height="${H}" class="abs" style="left:0;top:0">
  </div>`;
}
