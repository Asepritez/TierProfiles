// Settings, revision 3: "The Inventory". The whole screen works like a Minecraft inventory:
// creative-style category tabs, options as slots with the hotbar selection frame, help in real
// tooltips, a permanent Live Stage, and your settings profiles in a hotbar you swap with 1 to 9.
// One grid for every tab. GUI pixels, mirrored in docs/LAYOUT.md.
const S = { m:10, railX:10, railW:88, panelX:98, panelW:462, stageX:570, stageW:380, topY:40, bodyH:456, footY:506, footH:24,
            pad:10, rowH:28, rowGap:4, labelW:130, ctlX:140, ctlW:312, slot:20 };
const I = (n, s = 2) => `<img src="../_tmp/vanilla/cur/item/${n}.png" width="${16*s}" height="${16*s}">`;
const TABS = [['Tags','name_tag'],['Colours','red_dye'],['Rules','comparator'],['Profile','armor_stand'],['Position','compass_00'],['Sharing','writable_book'],['Keys','trial_key'],['More','redstone']];
const box = (x, y, w, h, html, st = '') => `<div class="abs" style="left:${R(x)}px;top:${R(y)}px;width:${R(w)}px;height:${R(h)}px;${st}">${html}</div>`;

// ---- controls. All sit on the control column, all are one slot tall ----
const slotBg = 'background:#0C0F12;box-shadow:inset 2px 2px 0 #06080A,inset -2px -2px 0 #252B31;';
const slot = (inner, on, w = S.slot) => `<span class="sl" style="position:relative;display:inline-flex;align-items:center;justify-content:center;flex:none;width:${R(w)}px;height:${R(S.slot)}px;${slotBg}">${inner}${on ? `<i class="abs" style="left:-4px;top:-4px;right:-4px;bottom:-4px;box-shadow:inset 0 0 0 4px #E8EEF2,0 0 0 2px #000;z-index:2"></i>` : ''}</span>`;
const slots = (list) => `<span class="row" style="gap:0">${list.join('')}</span>`;
const txt = (t, on, w) => slot(`<span style="color:${on ? '#fff' : '#AAB2BA'}">${t}</span>`, on, w);
const seg = (items, on, w) => slots(items.map(i => txt(i, i === on, w || Math.ceil(Math.max(textW(i) + 12, 36) / 2) * 2)));
const lever = on => `<span class="row gap8"><span style="display:inline-block;position:relative;width:${R(26)}px;height:${R(14)}px;${slotBg}"><i class="abs" style="top:4px;${on ? 'right:4px' : 'left:4px'};width:${R(9)}px;height:${R(10)}px;background:${on ? '#77D877' : '#6A737C'};box-shadow:inset 2px 2px 0 rgba(255,255,255,.3)"></i></span><span class="muted">${on ? 'On' : 'Off'}</span></span>`;
const sliderS = (pct, label, w = 200) => `<span class="row gap8"><span style="display:inline-block;position:relative;width:${R(w)}px;height:${R(8)}px;${slotBg}"><i class="abs" style="left:4px;top:4px;height:8px;width:calc(${pct}% - 8px);background:#68B5D5"></i><i class="abs" style="left:calc(${pct}% - 6px);top:-6px;width:12px;height:28px;background:#DDD;box-shadow:inset -2px -2px 0 #888,inset 2px 2px 0 #fff"></i></span><span class="nw">${label}</span></span>`;
const fieldS = (t, w, col) => `<span class="nw" style="display:inline-flex;align-items:center;height:${R(S.slot)}px;width:${R(w)}px;padding:0 8px;color:${col || '#DDD'};overflow:hidden;${slotBg}">${t}</span>`;
const btnS = (t, on) => `<span class="nw" style="display:inline-flex;align-items:center;justify-content:center;height:${R(S.slot)}px;padding:0 14px;color:#fff;background:${on ? '#3E4A50' : '#1B2025'};box-shadow:inset 0 0 0 2px ${on ? '#68B5D5' : '#3A4248'}">${t}</span>`;
const keycap = t => `<span class="nw" style="display:inline-flex;align-items:center;justify-content:center;min-width:${R(S.slot)}px;height:${R(S.slot)}px;padding:0 8px;color:#fff;${slotBg}">${t}</span>`;
const ladder = (min, w = 31) => slots(RANK.map(t => { const lit = RANK.indexOf(t) <= RANK.indexOf(min);
  return slot(`<span style="color:${lit ? TIER[t] : '#4A5157'}">${t}</span>`, t === min, w); }));

// ---- rows ----
const row = (label, ctl, o = {}) => `<div class="row" style="height:${R(o.h || S.rowH)}px;margin-bottom:${R(S.rowGap)}px;${o.hl ? 'background:rgba(104,181,213,.08);' : ''}"><span class="nw" style="width:${R(S.labelW)}px;color:${o.muted ? '#AAB2BA' : '#E8E8E8'}">${label}</span><span class="row" style="width:${R(S.ctlW)}px;gap:${R(4)}px">${ctl}</span></div>`;
const heading = (t, s) => `<div class="row" style="height:${R(S.rowH)}px;margin-bottom:${R(S.rowGap)}px;gap:12px"><span class="mid nw" style="color:#fff">${t}</span><span class="muted nw" style="margin-left:auto">${s || ''}</span></div>`;
const advancedRow = (n, s, open) => `<div class="row" style="height:${R(S.rowH)}px;margin-top:${R(4)}px;margin-bottom:${R(S.rowGap)}px;background:#1A2025;box-shadow:inset 0 0 0 2px #3A4248;padding:0 ${R(5)}px"><span class="accent" style="width:${R(S.labelW - 5)}px">${open ? 'v' : '&gt;'} <span style="color:#fff;margin-left:6px">Advanced</span></span><span class="muted nw">${n ? n + ' more: ' : ''}${s}</span></div>`;

// real vanilla-style tooltip, replaces subtitle text everywhere
// The help tooltip keeps its own place at the end of the options, so it never covers a control.
const tip = (title, lines) => `<div style="margin-left:${R(S.ctlX - S.pad)}px;margin-top:${R(8)}px;width:${R(S.ctlW)}px;padding:${R(4)}px ${R(5)}px;background:rgba(16,0,16,.95);box-shadow:inset 0 0 0 2px #2A0A52,inset 0 0 0 4px #1A0636,0 0 0 2px #100010"><div style="color:#fff;line-height:24px">${title}</div>${lines.map(l => `<div style="color:#A8A8A8;line-height:22px">${l}</div>`).join('')}</div>`;

// ---- the frame every tab shares ----
function screen(active, body, o = {}) {
  const rail = TABS.map(([t, it], i) => { const on = t === active;
    return box(S.railX, S.topY + i * 34, S.railW + (on ? 2 : 0), 32, `<span class="row" style="height:100%;gap:${R(5)}px;padding-left:${R(7)}px">${I(it)}<span style="color:${on ? '#fff' : '#AAB2BA'}">${t}</span></span>`,
      on ? `background:#141A1F;box-shadow:inset 4px 0 0 #68B5D5,inset 0 2px 0 #3A4248,inset 0 -2px 0 #3A4248;z-index:3` : `background:#0E1215;box-shadow:inset 0 0 0 2px #22282D`); }).join('');
  const search = box(S.panelX, 10, S.panelW + 10 + S.stageW, 24, `<span class="row" style="height:100%;padding:0 12px;gap:10px;${slotBg}">${I('spyglass')}<span class="${o.search ? '' : 'muted'}">${o.search || 'Search all 118 settings'}</span><span class="sp"></span><span class="muted">${o.search ? 'Esc' : 'Ctrl F'}</span></span>`);
  const brand = box(S.railX, 10, S.railW, 24, `<span class="row gap6" style="height:100%;color:#fff">${I('name_tag')}<span>Tier</span></span>`).replace('Tier<','TierProfiles<').replace(I('name_tag'),'');
  const panel = box(S.panelX, S.topY, S.panelW, S.bodyH, `<div style="position:absolute;left:${R(S.pad)}px;top:${R(6)}px;width:${R(S.panelW - 2 * S.pad)}px">${body}</div>`, `background:#141A1F;box-shadow:inset 0 0 0 2px #3A4248`);
  const stage = o.noStage ? (o.right || '') : Stage(o.stage || {});
  // profiles live in a real hotbar: press 1 to 9 to swap your whole setup
  const P = ['ender_pearl','netherite_sword','mace','end_crystal','name_tag','','','',''], act = 0;
  const hot = box(S.panelX, S.footY, 9 * 20 + 4, 24, `<span class="row" style="position:absolute;left:4px;top:4px;gap:0">${P.map((n, i) => slot(n ? I(n) : '', i === act)).join('')}</span>`);
  const hotLabel = box(S.panelX + 190, S.footY, 170, 24, `<span class="col" style="height:100%;justify-content:center;line-height:22px"><span style="color:#fff">${o.profile || 'Casual'}</span><span class="muted">Press 1 to 9 to swap setups</span></span>`);
  const actions = box(S.stageX, S.footY, S.stageW, 24, `<span class="row" style="height:100%;gap:${R(4)}px;justify-content:flex-end">${o.changes ? `<span class="gold nw" style="margin-right:auto">${o.changes} change${o.changes > 1 ? 's' : ''} staged</span>` : ''}${btnS('Discard')}${btnS('Apply', 1)}</span>`);
  return `<div class="shell"></div>${brand}${search}${rail}${panel}${stage}${hot}${hotLabel}${actions}`;
}

// ---- Live Stage: every surface, always on screen ----
function Stage(o = {}) {
  const X = S.stageX, Y = S.topY, inner = S.stageW - 14, G = GRID;
  const name = o.nameCol || '#fff';
  const tagHtml = `<span class="row gap4 nw" style="height:22px;padding:0 6px;background:rgba(0,0,0,.6)">${o.noTag ? '' : `${ic(o.kit || 'smp',1)}<span style="color:${(o.pal||TIER)[o.tier || 'HT2']}">${o.tier || 'HT2'}</span><span style="color:#888">|</span>`}<span style="color:${name}">JokingDK</span></span>`;
  const world = o.world || `<img src="../_tmp/bg/stage.png" width="${R(inner)}" height="${R(264)}" style="image-rendering:auto"><div class="abs" style="left:${R(inner * STAGE_POS.x)}px;top:${R(264 * STAGE_POS.y)}px;transform:translate(-50%,-100%)">${tagHtml}</div>`;
  const tab = `<span class="row gap6 nw" style="height:${R(16)}px;width:${R(180)}px;padding:0 6px;background:rgba(255,255,255,.08)">${ic(o.kit || 'smp',1)}<span style="color:${(o.pal||TIER)[o.tier || 'HT2']}">${o.tier || 'HT2'}</span><span style="color:${name}">JokingDK</span><span class="sp"></span><span class="row" style="gap:2px;align-items:flex-end">${[4,6,8,10].map(h => `<i style="width:2px;height:${h}px;background:#55FF55"></i>`).join('')}</span></span>`;
  const chat = `<span class="row gap6 nw" style="height:${R(16)}px;padding:0 4px">${ic(o.kit || 'smp',1)}<span style="color:${(o.pal||TIER)[o.tier || 'HT2']}">${o.tier || 'HT2'}</span><span style="color:${name}">JokingDK</span><span>: gg</span></span>`;
  return box(X, Y, S.stageW, S.bodyH, `
    <div class="abs row" style="left:${R(7)}px;top:${R(5)}px;height:${R(14)}px;gap:8px;width:${R(inner)}px"><span class="accent nw">Live Stage</span><span class="muted nw">${o.caption || 'Updates as you change things'}</span></div>
    <div class="abs" style="left:${R(7)}px;top:${R(22)}px;width:${R(inner)}px;height:${R(264)}px;overflow:hidden;box-shadow:0 0 0 2px #2B333C">${world}</div>
    <div class="abs row" style="left:${R(7)}px;top:${R(290)}px;height:${R(18)}px;gap:${R(6)}px">${tab}${chat}</div>
    <div class="abs" style="left:${R(7)}px;top:${R(312)}px">${o.card || defaultCard(JOKING, o.cardOpts || {})}</div>
`,
    `background:#10151A;box-shadow:inset 0 0 0 2px #2B333C`);
}
