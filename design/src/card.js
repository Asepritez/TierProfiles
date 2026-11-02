// Default profile card, revision 3. The grid below is the one source of truth for the card, and is
// mirrored in docs/LAYOUT.md. All numbers are GUI pixels. Measured from the game's own font:
//   widest chip text 17 (HT1), widest list name 55 (McPvP beta), widest date 61 (28 May 2026).
const GRID = (() => {
  const pad = 6, gap = 6, rowH = 19, stripH = 32, stripGap = 4;
  const modelW = 60, logoW = 16, logoGap = 4, nameW = 56, iconW = 16, chipW = 24, slotW = 1 + iconW + 1 + chipW;
  const dateW = 62;
  const model = pad, list = model + modelW + gap, name = list + logoW + logoGap;
  const kit1 = list + logoW + logoGap + nameW + gap, kit2 = kit1 + slotW + gap, kit3 = kit2 + slotW + gap;
  const date = kit3 + slotW + gap, right = date + dateW, W = right + pad;
  return { pad, gap, rowH, stripH, stripGap, modelW, logoW, logoGap, nameW, iconW, chipW, slotW, dateW,
           model, list, name, kit1, kit2, kit3, date, right, W, rowsY: stripH + stripGap };
})();
const R = n => n * 2;                 // GUI pixels to real pixels at GUI scale 2
const REGION = { EU:['#16321F','#62E08C'], NA:['#3A1A1E','#FF7B7B'], ME:['#3A3216','#E8CE66'] };
const INK = '#0B0E13', PILL = '#151B21', HAIR = '#2B333C', MUTED = '#8E98A2';

const cellText = (x, y, w, h, html, st = '', attr = '') =>
  `<div class="abs nw" ${attr} style="left:${R(x)}px;top:${R(y)}px;width:${R(w)}px;height:${R(h)}px;line-height:${R(h)}px;${st}">${html}</div>`;

// the one chip: tier text on a dark pill. Retired is hollow, a higher peak is a gold corner.
function pillStyle(k) { return k.r ? `background:transparent;box-shadow:inset 0 0 0 2px #39424B;` : `background:${PILL};`; }
const peakCorner = `<i class="abs" style="right:0;top:0;width:8px;height:8px;background:#FFCF4A;clip-path:polygon(0 0,100% 0,100% 100%,75% 100%,75% 75%,50% 75%,50% 50%,25% 50%,25% 25%,0 25%)"></i>`;
function pill(k, x, y, o = {}) {
  const c = (o.pal || TIER)[k.t] || '#AAA';
  return `<div class="abs" data-a="chip" style="left:${R(x)}px;top:${R(y)}px;width:${R(GRID.slotW)}px;height:${R(18)}px;${pillStyle(k)}">
    <span class="abs" style="left:2px;top:2px">${ic(k.k)}</span>
    <span class="abs nw" style="left:${R(1 + GRID.iconW + 1)}px;top:0;width:${R(GRID.chipW)}px;height:${R(18)}px;line-height:${R(18)}px;text-align:center;color:${c};${k.r ? 'opacity:.72;' : ''}">${k.t}</span>${k.p ? peakCorner : ''}</div>`;
}

function strip(p, o, G, W) {
  const [rb, rc] = REGION[p.region] || ['#222', '#CCC'];
  const nameW = textW(p.name) * 1.5 + 6;
  return `<div class="abs" data-a="strip" style="left:0;top:0;width:${R(W)}px;height:${R(G.stripH)}px;background:${o.scene ? 'transparent' : '#12171D'};box-shadow:inset 0 -2px 0 ${o.scene ? 'rgba(255,255,255,.1)' : HAIR}">
    ${cellText(G.list, 3, 200, 15, `<span class="mid" style="color:#fff">${p.name}</span>`)}
    ${cellText(G.list + nameW, 6, 18, 10, `<span style="display:inline-block;background:${rb};color:${rc};padding:0 4px;line-height:20px">${p.region}</span>`)}
    ${cellText(G.list, 18, 200, 11, `<span class="row gap4" style="color:#E9C766">${p.badge ? `<img src="../_tmp/logos/${p.badge}" width="18" height="18" style="image-rendering:auto">` : ''}${p.title}</span>`)}
    ${cellText(o.statX ?? G.kit3, 3, 44, 15, `<span class="mid" style="color:#FFCF4A">${p.best.rank}</span>`)}
    ${cellText(o.statX ?? G.kit3, 18, 60, 11, `<span style="color:${MUTED}">${p.best.list}${p.best.beta ? ' ' + betaTag : ''}</span>`)}
    ${cellText(o.rightX - 60, 3, 60, 15, `<span class="mid" style="color:#fff">${p.total}</span>`, 'text-align:right')}
    ${cellText(o.rightX - 60, 18, 60, 11, `<span style="color:${MUTED}">points</span>`, 'text-align:right')}
  </div>`;
}

function listCell(r, y, h, G) {
  const l = LISTS[r.id];
  return `<span class="abs" style="left:${R(G.list)}px;top:${R(y + (h - G.logoW) / 2)}px">${lm(l.mark)}</span>` +
    cellText(G.name, y, G.nameW, h, `<span style="color:#E8E8E8">${l.name}</span>${l.beta ? ` <span style="color:#C9A94E">beta</span>` : ''}`);
}
const unranked = (x, y, h) => cellText(x, y, 60, h, `<span style="color:${MUTED}">Unranked</span>`);
const hasTiers = r => !r.none && !r.down;

function guides(G, W, H, xs) {
  return xs.map(([x, c]) => `<i class="abs" style="left:${R(x)}px;top:0;width:1px;height:${R(H)}px;background:${c};z-index:9"></i>`).join('') +
    Array.from({ length: Math.floor((H - G.rowsY) / G.rowH) + 1 }, (_, i) => `<i class="abs" style="left:0;top:${R(G.rowsY + i * G.rowH)}px;width:${R(W)}px;height:1px;background:#FF4FA399;z-index:9"></i>`).join('');
}

function defaultCard(p, o = {}) {
  const G = GRID, detail = o.detail || 'Standard', scene = !!o.bg;
  let W = G.W, H, body = '', rightX = G.right;
  if (detail === 'Minimal') {
    H = G.rowsY + G.rowH + G.pad;
    const rs = p.rows.filter(hasTiers).slice(0, 4), step = G.logoW + 3 + G.slotW + G.gap;
    body = `<div data-a="row">` + rs.map((r, i) => `<span class="abs" style="left:${R(G.list + i * step)}px;top:${R(G.rowsY + 1)}px">${lm(LISTS[r.id].mark)}</span>` +
      pill(r.kits[0], G.list + i * step + G.logoW + 3, G.rowsY, o)).join('') + `</div>`;
  } else if (detail === 'Full') {
    const slot = 46, rowH = 31, n = 5;
    const fx = i => G.kit1 + i * (slot + G.gap);
    W = fx(n - 1) + slot + G.pad; rightX = W - G.pad;
    H = G.rowsY + p.rows.length * rowH + 14 + G.pad;
    body = p.rows.map((r, i) => { const y = G.rowsY + i * rowH;
      return `<div data-a="row">${listCell(r, y, 18, G)}${hasTiers(r) ? r.kits.slice(0, n).map((k, j) => pill(k, fx(j), y, o) +
        cellText(fx(j), y + 18, slot, 11, `<span style="color:${MUTED}">${k.d ? k.d.replace(/^\d+ /, '') : 'No date'}</span>`, 'text-align:right')).join('') : unranked(G.kit1, y, 18)}</div>`; }).join('') +
      cellText(G.list, H - G.pad - 11, 300, 11, `<span style="color:${MUTED}">Gold corner: peak was higher. Hollow: retired. Dates are when each tier was achieved.</span>`);
  } else {
    H = G.rowsY + p.rows.length * G.rowH + G.pad;
    body = p.rows.map((r, i) => { const y = G.rowsY + i * G.rowH, xs = [G.kit1, G.kit2, G.kit3];
      return `<div data-a="row">${listCell(r, y, 18, G)}${hasTiers(r)
        ? r.kits.slice(0, 3).map((k, j) => pill(k, xs[j], y, o)).join('') + cellText(G.date, y, G.dateW, 18, `<span style="color:${MUTED}">${r.kits[0].d || ''}</span>`, 'text-align:right', 'data-a="chip"')
        : unranked(G.kit1, y, 18)}</div>`; }).join('');
  }
  const stats = { ...o, scene, rightX, statX: detail === 'Full' ? rightX - 116 : G.kit3 };
  const mh = H - 2 * G.pad > 125 ? 125 : H - 2 * G.pad, my = Math.round((H - mh) / 2);
  const scn = scene ? `<img class="abs" src="${drawScene(W, H, { fxw: G.list - G.gap, ...o }, o.t || 0)}" width="${R(W)}" height="${R(H)}" style="left:0;top:0">` : '';
  const brd = scene ? `<img class="abs" src="${drawBorder(W, H, o, o.t || 0)}" width="${R(W)}" height="${R(H)}" style="left:0;top:0">` : '';
  const shade = scene ? `<div class="abs" style="left:${R(G.list - G.gap / 2)}px;top:${R(G.stripH)}px;width:${R(W - G.list + G.gap / 2)}px;height:${R(H - G.stripH)}px;background:rgba(9,11,16,.66)"></div>` : '';
  const gd = o.guides ? guides(G, W, H, [[G.model, '#7CFC00'], [G.list, '#00E5FF'], [G.name, '#00E5FF'], [G.kit1, '#FFEA00'], [G.kit1 + G.slotW, '#FFEA0066'], [G.kit2, '#FFEA00'], [G.kit2 + G.slotW, '#FFEA0066'], [G.kit3, '#FFEA00'], [G.kit3 + G.slotW, '#FFEA0066'], [G.date, '#FF7A1A'], [G.right, '#FF2D2D']]) : '';
  const model = `<div class="abs" data-a="model" style="left:${R(G.model)}px;top:${R(my)}px;width:${R(G.modelW)}px;height:${R(mh)}px;display:flex;justify-content:center"><img src="${o.model || p.model}" style="height:100%;width:auto;max-width:100%;object-fit:contain"></div>`;
  return `<div style="position:relative;width:${R(W)}px;height:${R(H)}px;background:rgba(11,14,19,.95);overflow:hidden;box-shadow:inset 0 0 0 2px ${HAIR}" data-a="card">
    ${scn}${strip(p, stats, G, W)}${shade}${model}${body}${brd}${gd}
    <div class="abs" style="left:2px;bottom:2px;height:2px;width:${o.bar ?? 72}%;background:#4F6B78" data-a="bar"></div></div>`;
}

// Entrance: the card glides in from its nearest screen edge, rows and chips stagger in after it lands.
// Time-based, so the mod can drive it the same way from a clock.
const easeOut = x => 1 - Math.pow(1 - Math.min(1, Math.max(0, x)), 4);
const easeIn = x => Math.pow(Math.min(1, Math.max(0, x)), 3);
function animate(root, t, o = {}) {
  const dist = o.dist ?? 800, inT = .5, hold = o.hold ?? 1.7, outT = .3;
  const dir = { right:[1,0], left:[-1,0], top:[0,-1], bottom:[0,1] }[o.from || 'right'];
  const card = root.querySelector('[data-a=card]');
  const off = dist * (1 - easeOut(t / inT)) + dist * easeIn((t - inT - hold) / outT);
  card.style.transform = `translate(${Math.round(off * dir[0])}px,${Math.round(off * dir[1])}px)`;
  const m = root.querySelector('[data-a=model]');
  if (m) { const k = easeOut((t - .22) / .34); m.style.opacity = k; m.style.transform = `translateX(${Math.round(-18 * (1 - k))}px)`; }
  root.querySelectorAll('[data-a=row]').forEach((r, i) => {
    const st = .36 + i * .085, k = easeOut((t - st) / .3);
    r.style.opacity = k; r.style.transform = `translateX(${Math.round(26 * (1 - k))}px)`;
    r.querySelectorAll('[data-a=chip]').forEach((c, j) => { c.style.opacity = easeOut((t - st - .06 - j * .04) / .22); });
  });
  const b = root.querySelector('[data-a=bar]');
  if (b) b.style.width = `${Math.max(0, 100 * (1 - Math.max(0, t - .9) / hold))}%`;
}
