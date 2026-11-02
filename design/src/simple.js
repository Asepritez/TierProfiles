// Revision 2 settings: each tab opens on a few essentials, one clear Advanced row, room to breathe.
const ess = (title, sub, ctl, o = {}) => `<div class="row" style="min-height:${o.h || 92}px;padding:12px 18px;gap:20px;background:#121619;box-shadow:inset 0 0 0 2px #262B2F">
  <div class="col" style="flex:1;gap:4px"><span class="nw" style="color:#fff">${title}</span><span class="muted" style="line-height:22px">${sub}</span></div>${ctl}</div>`;
const essCol = (title, sub, ctl, o = {}) => `<div class="col" style="gap:10px;padding:14px 18px;background:#121619;box-shadow:inset 0 0 0 2px #262B2F">
  <div class="row gap12"><span class="nw" style="color:#fff">${title}</span><span class="muted nw">${sub}</span></div>${ctl}</div>`;
function advanced(count, items, open) {
  return `<div class="row gap12" style="height:64px;padding:0 18px;background:#1A2024;box-shadow:inset 0 0 0 2px #4A5358">
    <span class="accent" style="width:14px">${open ? 'v' : '&gt;'}</span><span style="color:#fff">Advanced</span><span class="muted nw">${count} more</span>
    <span class="sp"></span><span class="muted nw">${items}</span></div>`;
}
const recommended = `<span class="nw" style="color:#77D877">Recommended</span>`;
const item = (n, s = 2) => `<img src="../_tmp/vanilla/cur/item/${n}.png" width="${16*s}" height="${16*s}">`;
const pick = (inner, on, w = 76, h = 76) => `<div class="col" style="width:${w}px;height:${h}px;align-items:center;justify-content:center;gap:2px;background:${on ? '#3E4A50' : '#191D20'};box-shadow:inset 0 0 0 2px ${on ? '#68B5D5' : '#33393E'}">${inner}</div>`;
