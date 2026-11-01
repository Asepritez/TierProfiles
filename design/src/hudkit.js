// Tab list, nametags, key hints and chat. Names and tiers here are invented samples for the lobby,
// so no real player is shown with a tier they do not hold.
const K = t => `<span style="display:inline-flex;align-items:center;justify-content:center;min-width:26px;height:26px;padding:0 6px;background:#2A2F33;color:#fff;box-shadow:inset 0 0 0 2px #7A858C,inset 0 -4px 0 #15181A">${t}</span>`;

const SAMPLE = { // name: [kit, tier]
  KitSmith:['sword','HT4'], PearlPanic:['smp','HT4'], Bedwrecker:['bed','HT4'], lowroll_:['pot','HT4'], Maceling:['mace','HT3'],
  CrystalKid:['vanilla','HT4'], OneTap_Oli:['sword','HT1'], ShieldNan:['shield','HT4'], Totemless:['vanilla','HT4'], ByteBow:['bow','LT3'],
  TacoStrafe:['axe','HT4'], Wtapper:['sword','HT4'], SoupGhost:['pot','LT2'], NoHitNora:['uhc','HT2'], PotDrinker:['pot','HT4'],
  GappleGus:['early_game','HT4'], DripCart:['cart','LT4'], FenceJump:['smp','HT4'], SpearSam:['spear','HT4'], ElytraEd:['elytra','LT4'],
  JokingDK:['smp','HT2'], Marlowww:['sword','HT1'],
};
const RANK = ['HT1','LT1','HT2','LT2','HT3','LT3','HT4','LT4','HT5','LT5'];
const atLeast = (t, min) => RANK.indexOf(t) <= RANK.indexOf(min);

// nametag in world space. s3 = nearer player, drawn one font step larger
function nametag(x, y, name, o = {}) {
  const t = o.tier, kit = o.kit, near = o.near;
  const nameCol = o.nameCol || '#fff';
  const tag = t ? `${ic(kit, near ? 1.5 : 1)}<span class="tag-txt" style="color:${TIER[t]}">${t}</span><span style="color:#999">|</span>` : '';
  return `<div class="nametag ${near ? 's3' : ''}" style="left:${x}px;top:${y}px;z-index:${o.z||1};${o.extra||''}">${o.prefix||''}${tag}<span class="tag-txt" style="color:${nameCol};${o.nameStyle||''}">${name}</span></div>`;
}

// vanilla-shaped tab list, with tier tags and keyboard selection
function tablist(players, sel, o = {}) {
  const row = (n, i) => {
    const p = SAMPLE[n], on = n === sel;
    const col = (o.names && o.names[n]) || '#FFFFFF';
    return `<div class="row gap6" style="height:22px;padding:0 4px;background:${on ? '#3E4A50' : 'rgba(255,255,255,.07)'};${on ? 'box-shadow:inset 0 0 0 2px #68B5D5' : ''}">
      ${ic(p[0], 1)}<span class="nw" style="color:${TIER[p[1]]};width:36px">${p[1]}</span>
      <span class="nw sp" style="color:${col}">${o.star && o.star[n] ? '<span style="color:#FFCF4A">*</span>' : ''}${n}</span>
      <span class="row" style="gap:2px;align-items:flex-end;height:14px">${[6,8,10,12,14].map((h,j)=>`<i style="width:2px;height:${h}px;background:${j<4||i%3?'#55FF55':'#555'}"></i>`).join('')}</span></div>`;
  };
  const half = Math.ceil(players.length / 2);
  return `<div style="width:${o.w||600}px;background:rgba(0,0,0,.74);padding:8px">
    <div class="sh" style="text-align:center;height:24px;color:#FFFF55">Practice Arena</div>
    <div class="row gap4" style="align-items:flex-start">
      <div class="col gap2 sp">${players.slice(0, half).map(row).join('')}</div>
      <div class="col gap2 sp">${players.slice(half).map((n,i)=>row(n,i+half)).join('')}</div>
    </div>
    <div class="sh muted" style="text-align:center;height:20px;margin-top:6px">${players.length} online</div>
  </div>`;
}
const TAB = ['JokingDK','OneTap_Oli','NoHitNora','SoupGhost','Maceling','ByteBow','KitSmith','PearlPanic','Bedwrecker','lowroll_','CrystalKid','ShieldNan','Totemless','TacoStrafe','Wtapper','PotDrinker','GappleGus','FenceJump','SpearSam','DripCart'];

function keyHint(extra = '') {
  return `<div class="row gap8 nw" style="height:38px;background:rgba(9,11,12,.86);padding:0 10px;box-shadow:inset 0 0 0 2px #4E5C65">${K('↑')}${K('↓')}${K('←')}${K('→')}<span>Move</span><span style="width:8px"></span>${K('Enter')}<span>Pin card</span>${extra}</div>`;
}
