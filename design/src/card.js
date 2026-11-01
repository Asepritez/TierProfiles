// Default profile card (revision 2): the esports strip over sparse, minimal rows.
// Reference: mctiers.gg and mcpvp.com. Dark, thin, no coloured underlines, tier shown as coloured text.
const REGION = { EU:['#16321F','#62E08C'], NA:['#3A1A1E','#FF7B7B'], ME:['#3A3216','#E8CE66'] };

function tierTag(k, o = {}) {
  const c = (o.pal || TIER)[k.t] || '#AAA';
  return `<span class="row nw" style="height:24px;padding:0 5px;gap:3px;background:#171D24;color:${c};${k.r ? 'opacity:.62;' : ''}" data-a="chip">${k.t}${k.p ? '<span style="color:#FFCF4A;margin-left:2px">^</span>' : ''}${k.r ? '<span style="color:#AAA;margin-left:4px">R</span>' : ''}</span>`;
}
const iconTile = k => `<span class="row" style="width:34px;height:34px;justify-content:center;background:#1E252D;flex:none">${ic(k.k)}</span>`;
const kitTag = (k, o) => `<span class="row gap2" style="width:${o && o.kw || 92}px" data-a="chip">${iconTile(k)}${tierTag(k, o)}</span>`;

function topStrip(p, o, left) {
  const [rb, rc] = REGION[p.region] || ['#222', '#CCC'];
  return `<div class="abs row" style="left:0;top:0;right:0;height:62px;background:${o.scene ? 'rgba(10,13,18,.62)' : '#141A21'}" data-a="strip">
    <div class="col" style="position:absolute;left:${left}px;top:6px">
      <div class="row gap8" style="height:30px"><span class="mid nw" style="color:#fff">${p.name}</span><span class="nw" style="background:${rb};color:${rc};padding:0 6px;line-height:22px">${p.region}</span></div>
      <div class="row gap4 nw" style="height:22px;color:#E9C766">${p.badge ? `<img src="../_tmp/logos/${p.badge}" width="20" height="20" style="image-rendering:auto">` : ''}${p.title}</div>
    </div>
    <div class="abs row" style="right:0;top:0;height:62px">
      <div class="col" style="height:62px;justify-content:center;padding:0 14px 0 22px;clip-path:polygon(14px 0,100% 0,100% 100%,0 100%);background:#1C232C">
        <span class="mid nw" style="color:#FFCF4A;line-height:30px">${p.best.rank}</span><span class="muted nw" style="line-height:20px">${p.best.list}${p.best.beta ? ' ' + betaTag : ''}</span></div>
      <div class="col" style="height:62px;justify-content:center;padding:0 12px;min-width:92px">
        <span class="mid nw" style="color:#fff;line-height:30px">${p.total}</span><span class="muted nw" style="line-height:20px">points</span></div>
    </div></div>`;
}

const quietLine = (p) => {
  const none = p.rows.filter(r => r.none), down = p.rows.filter(r => r.down);
  const dim = r => `<span style="opacity:.55">${lm(LISTS[r.id].mark, 1)}</span>`;
  return (none.length ? `<span class="row gap4 muted nw">${none.map(dim).join('')} Not ranked</span>` : '') +
         (down.length ? `<span class="row gap4 muted nw">${down.map(dim).join('')} ${down.map(r => LISTS[r.id].name).join(', ')} offline</span>` : '');
};

function defaultCard(p, o = {}) {
  const detail = o.detail || 'Standard', scene = !!o.bg;
  const rows = p.rows.filter(r => !r.none && !r.down);
  const LEFT = 124;
  let W = o.w || 752, H, body;
  if (detail === 'Minimal') {
    W = o.w || 640; H = 112;
    body = `<div class="abs row" style="left:${LEFT}px;right:10px;top:70px;height:34px;gap:14px" data-a="row">
      ${rows.map(r => `<span class="row gap4" data-a="chip">${lm(LISTS[r.id].mark)}${tierTag(r.kits[0], o)}</span>`).join('')}</div>`;
  } else if (detail === 'Full') {
    W = o.w || 756; H = 62 + 8 + rows.length * 68 + 34;
    const tile = k => `<div class="col" style="width:112px;height:60px;padding:3px 4px;background:#12171D" data-a="chip"><div class="row gap4">${iconTile(k).replace(/34px/g,'32px')}${tierTag(k, o)}</div><span class="muted nw" style="line-height:20px;margin-top:2px">${k.d ? k.d.replace(/^\d+ /, '') : 'No date'}</span></div>`;
    body = `<div class="abs col" style="left:${LEFT}px;right:8px;top:68px;gap:6px">${rows.map(r => `<div class="row gap4" style="height:60px" data-a="row">
        <span class="col" style="width:44px;align-items:center;gap:0">${lm(LISTS[r.id].mark)}${LISTS[r.id].beta ? `<span style="line-height:18px">${betaTag}</span>` : ''}</span>${r.kits.slice(0, 5).map(tile).join('')}</div>`).join('')}</div>
      <div class="abs row gap12 nw muted" style="left:${LEFT}px;right:10px;bottom:6px;height:24px">${quietLine(p)}<span class="sp"></span><span class="nw"><span class="gold">^</span> peak higher</span><span>R retired</span></div>`;
  } else {
    const n = o.kits || 3;
    H = 62 + 8 + rows.length * 38 + (rows.length < p.rows.length ? 30 : 8);
    body = `<div class="abs col" style="left:${LEFT}px;right:10px;top:68px;gap:2px">${rows.map(r => {
      const l = LISTS[r.id];
      return `<div class="row" style="height:36px;gap:8px" data-a="row">${lm(l.mark)}
        <span class="nw" style="width:122px;color:#E8E8E8">${l.name}${l.beta ? ' ' + betaTag : ''}</span>
        <span class="row" style="gap:0;flex:1">${r.kits.slice(0, n).map(k => kitTag(k, o)).join('')}</span>
        ${o.noDate ? '' : `<span class="muted nw" style="width:128px;text-align:right" data-a="chip">${r.kits[0].d || ''}</span>`}</div>`;
    }).join('')}</div>
    <div class="abs row gap12" style="left:${LEFT}px;right:10px;bottom:4px;height:24px" data-a="row">${quietLine(p)}</div>`;
  }
  let sceneImg = '', border = '';
  if (scene) {
    sceneImg = `<img class="abs" src="${drawScene(W / 2, H / 2, { fxw: 56, ...o }, o.t || 0)}" width="${W}" height="${H}" style="left:0;top:0">`;
    border = `<img class="abs" src="${drawBorder(W / 2, H / 2, o, o.t || 0)}" width="${W}" height="${H}" style="left:0;top:0">`;
  }
  const mh = Math.min(H - 12, detail === 'Minimal' ? 100 : 226);
  const panel = scene ? `<div class="abs" style="left:${LEFT - 8}px;top:62px;right:0;bottom:0;background:rgba(9,11,16,.72)"></div>` : '';
  return `<div style="position:relative;width:${W}px;height:${H}px;background:rgba(11,14,19,.95);overflow:hidden;box-shadow:inset 0 0 0 2px #2B333C" data-a="card" data-h="${H}">
    ${sceneImg}${panel}${topStrip(p, { ...o, scene }, LEFT)}
    <img class="abs" src="${o.model || p.model}" style="left:${o.mx2 ?? 8}px;bottom:${detail === 'Minimal' ? 6 : 6}px;height:${mh}px;width:auto" data-a="model">
    ${body}${border}
    <div class="abs" style="left:2px;bottom:2px;height:2px;width:${o.bar ?? 72}%;background:#4F6B78" data-a="bar"></div>
  </div>`;
}

// Entrance: the card glides in with easing, then the strip, rows and chips stagger in after it lands.
// Time-based, so the mod can drive it the same way from a clock.
const easeOut = x => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 4);
const easeIn = x => Math.pow(Math.min(1, Math.max(0, x)), 3);
function animate(root, t, o = {}) {
  const dist = o.dist ?? 760, inT = .5, hold = o.hold ?? 2.4, outT = .32;
  const card = root.querySelector('[data-a=card]');
  const out = easeIn((t - inT - hold) / outT);
  const x = Math.round(dist * (1 - easeOut(t / inT)) + dist * out);
  card.style.transform = `translateX(${x}px)`;
  const reveal = (el, start, dur, dx) => { const k = easeOut((t - start) / dur);
    el.style.opacity = k; el.style.transform = `translateX(${Math.round(dx * (1 - k))}px)`; };
  const m = root.querySelector('[data-a=model]'); if (m) reveal(m, .22, .34, -18);
  root.querySelectorAll('[data-a=row]').forEach((r, i) => {
    r.style.opacity = 1; const st = .36 + i * .085; const k = easeOut((t - st) / .3);
    r.style.opacity = k; r.style.transform = `translateX(${Math.round(26 * (1 - k))}px)`;
    r.querySelectorAll('[data-a=chip]').forEach((c, j) => { const q = easeOut((t - st - .06 - j * .04) / .22); c.style.opacity = q; });
  });
  const b = root.querySelector('[data-a=bar]');
  if (b) b.style.width = `${Math.max(0, 100 * (1 - Math.max(0, t - .9) / hold))}%`;
}
