// The three profile card directions. They share the same data objects from lib.js.
function rowStatus(r) { return r.down ? 'Unavailable right now' : r.none ? 'Not ranked' : ''; }
const legend = `<span class="row gap4 muted nw">${peakMark(true)} peak <i style="display:inline-block;width:14px;height:14px;box-shadow:inset 0 0 0 2px #AAA;margin-left:8px"></i> retired</span>`;

// A: Tallium Flat. Quiet slate panel, model on the left, dense rows on the right.
function cardA(p, o = {}) {
  const pal = o.pal || TIER, n = o.kits || 4;
  const row = (r, i) => {
    const l = LISTS[r.id], st = rowStatus(r);
    const kits = st ? `<span class="muted nw">${st}</span>`
      : r.kits.slice(0, n).map(k => `<div class="row gap2">${ic(k.k)}${chip(k.t, {ret:k.r, peak:k.p, pal})}</div>`).join('');
    return `<div class="row" style="height:36px;gap:8px;${i ? 'box-shadow:0 -2px 0 rgba(255,255,255,.06)' : ''}">
      ${lm(l.mark)}
      <div class="nw" style="width:124px;color:${st ? '#AAA' : '#E8E8E8'}">${l.name}${l.beta ? ' ' + betaTag : ''}</div>
      <div class="row" style="gap:6px">${kits}</div></div>`;
  };
  const f = p.focus;
  return `<div style="width:${o.w||716}px;background:rgba(30,38,44,.93);box-shadow:inset 0 0 0 2px #5C6B74;padding:12px 14px 14px;position:relative">
    <div class="row" style="align-items:stretch;gap:12px">
      <div class="row" style="width:112px;justify-content:center"><img src="${p.model}" style="height:210px;width:auto"></div>
      <div style="width:2px;background:#4E5C65"></div>
      <div class="sp">
        <div class="row gap8" style="height:26px"><span class="nw" style="color:#fff">${p.name}</span>
          <span class="nw" style="background:#2C3B44;padding:0 6px;color:#BFD4DE">${p.region}</span><span class="sp"></span>
          <span class="nw" style="color:${o.title||'#FFCF4A'}">${p.title}</span></div>
        <div style="margin-top:4px">${p.rows.map(row).join('')}</div>
      </div>
    </div>
    <div class="row gap8 nw" style="margin-top:8px;height:32px;color:#BFD4DE;box-shadow:0 -2px 0 #4E5C65;padding-top:6px">${ic(f.k)}<span>${f.list} ${f.kit}</span>${chip(f.t,{ret:f.r,pal})}
      <span class="muted">${f.ago}</span><span class="sp"></span>${legend}</div>
    <div class="abs" style="left:2px;bottom:2px;height:4px;width:74%;background:#9CD4A8"></div>
  </div>`;
}

// B: McPvP Bold. Saturated tiles, big icons, each kit is a coloured block you read from a distance.
function cardB(p, o = {}) {
  const pal = o.pal || TIER;
  const tile = k => {
    const c = pal[k.t];
    const bg = k.r ? `background:#14161A;box-shadow:inset 0 0 0 4px ${c}` : `background:${c};box-shadow:inset 0 4px 0 rgba(255,255,255,.28),inset 0 -4px 0 rgba(0,0,0,.25)`;
    return `<div style="width:102px;height:56px;${bg};position:relative;display:flex;align-items:center;gap:0;padding-left:4px">
      <div style="background:#14161A;width:40px;height:40px;display:flex;align-items:center;justify-content:center;margin-left:4px">${ic(k.k)}</div>
      <span class="nw" style="margin-left:6px;width:36px;font-family:MonoB;color:${k.r?c:'#101214'}">${k.t}</span>
      ${k.p?`<span style="position:absolute;right:4px;top:4px">${peakMark(true)}</span>`:''}</div>`;
  };
  const row = r => {
    const l = LISTS[r.id], st = rowStatus(r);
    return `<div class="row" style="gap:6px;height:56px">
      <div class="row gap6" style="width:124px;height:56px;background:#14161A;padding:0 6px;box-shadow:inset 4px 0 0 ${st?'#555':'#FF4FA3'}">${lm(l.mark)}
        <div class="col nw" style="line-height:18px"><span style="color:${st?'#AAA':'#fff'}">${l.short}</span>${l.beta?betaTag:`<span class="muted">${r.pts||'-'}</span>`}</div></div>
      ${st?`<div class="row muted nw" style="height:56px;padding:0 12px;background:#14161A;flex:1">${st}</div>`:r.kits.slice(0,4).map(tile).join('')}
    </div>`;
  };
  const f = p.focus;
  return `<div style="width:752px;background:#1B1030;box-shadow:0 0 0 2px #000,inset 0 0 0 4px #FF4FA3;padding:10px;position:relative">
    <div class="row" style="gap:10px;align-items:stretch">
      <div class="col" style="width:176px;background:linear-gradient(#3A1A66,#1D0F3A);align-items:center;justify-content:flex-end;position:relative;padding-bottom:8px;box-shadow:inset 0 0 0 2px #6A2FB8">
        <img src="${(p.model).replace('.png','-lg.png')}" style="height:300px;width:auto">
      </div>
      <div class="sp col gap4">
        <div class="row" style="height:44px;background:#FF4FA3;padding:0 8px;color:#fff;gap:10px">
          <span class="big nw sh" style="line-height:44px">${p.name}</span><span class="sp"></span>
          <span class="nw" style="background:#14161A;padding:0 8px;line-height:26px;color:#fff">${p.region}</span></div>
        ${p.rows.map(row).join('')}
      </div>
    </div>
    <div class="row gap8 nw" style="height:32px;margin-top:6px;background:#14161A;padding:0 8px;color:#fff">
      <span class="gold">${p.title}</span><span class="muted">|</span>${ic(f.k)}<span>${f.kit} ${f.t}</span><span class="muted">${f.ago}</span><span class="sp"></span>${legend}</div>
  </div>`;
}

// C: Esports Card. A broadcast lower third: name plate plus one column per list.
function cardC(p, o = {}) {
  const pal = o.pal || TIER;
  const col = r => {
    const l = LISTS[r.id], st = rowStatus(r);
    const k = r.kits || [];
    const best = k[0];
    return `<div class="col" style="width:136px;height:150px;overflow:hidden;background:rgba(9,11,12,.9);box-shadow:inset 0 2px 0 #2E3438;padding:6px 8px">
      <div class="row gap4 nw" style="height:32px">${lm(l.mark)}<span style="color:${st?'#AAA':'#fff'}">${l.short}</span></div>
      ${l.beta?`<div style="height:18px;line-height:18px">${betaTag}</div>`:`<div class="muted" style="height:18px;line-height:18px">${r.pts?r.pts+' pts':''}</div>`}
      ${st?`<div class="muted" style="margin-top:12px;line-height:20px">${r.down?'Offline':'Not<br>ranked'}</div>`:`
       <div class="row gap4" style="margin-top:6px;height:34px">${ic(best.k)}<span class="row" style="position:relative;height:30px;padding:0 6px;font-family:MonoB;${best.r?`color:${pal[best.t]};box-shadow:inset 0 0 0 2px ${pal[best.t]}`:`background:${pal[best.t]};color:#101214`}">${best.t}${best.p?peakMark():''}</span></div>
       <div class="row" style="gap:4px;margin-top:8px">${k.slice(1,4).map(x=>`<span style="position:relative;display:block;width:32px;height:32px">${ic(x.k)}<i style="position:absolute;left:0;right:0;bottom:-4px;height:4px;background:${pal[x.t]}"></i></span>`).join('')}</div>`}
    </div>`;
  };
  const f = p.focus;
  return `<div style="width:760px;height:258px;position:relative">
    <div class="abs" style="left:0;top:32px;width:760px;height:44px;background:#0E1417"></div>
    <div class="abs" style="left:0;top:32px;width:6px;height:226px;background:#68B5D5"></div>
    <div class="abs" style="left:6px;top:76px;width:118px;height:182px;background:rgba(9,11,12,.82)"></div>
    <img class="abs" src="${p.model.replace('.png','-lg.png')}" style="left:8px;top:0;height:256px;width:auto">
    <div class="abs row gap8" style="left:124px;top:32px;height:44px;right:0;padding-right:10px">
      <span class="mid nw b" style="color:#fff">${p.name}</span>
      <span class="nw" style="background:#68B5D5;color:#0E1417;padding:0 6px;line-height:24px">${p.region}</span>
      <span class="sp"></span><span class="gold nw">${p.title}</span></div>
    <div class="abs row" style="left:124px;top:78px;gap:0;width:636px;justify-content:space-between;overflow:hidden">${p.rows.slice(0,5).map(r=>col(r).replace('width:136px','width:126px')).join('')}</div>
    <div class="abs row gap8 nw" style="left:124px;top:228px;height:30px;right:0;background:#0E1417;padding:0 8px;color:#BFD4DE">
      <span>${f.kit} ${f.t}</span><span class="muted">${f.ago}</span><span class="sp"></span>${legend}</div>
  </div>`;
}
