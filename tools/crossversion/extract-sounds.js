/*
 * 从官方 1.21.11 源码与资源提取「本项目实际需要」的音效，产出：
 *   1. src/main/resources/assets/minecraft/sounds-modern.json   —— 供 VirtualAssetsPack 运行时合并
 *   2. src/main/resources/assets/minecraft/sounds/**.ogg         —— 只复制真正引用到的音频
 *   3. target/crossversion-check/sound-manifest.txt              —— 事件清单 + 缺失报告
 *
 * 一切以官方文件为准，不猜：
 *   SoundEvents.java  常量名 -> 事件 id（"block.copper.break"）
 *   SoundType.java    SoundType 名 -> break/step/place/hit/fall 五个常量
 *   sounds.json       事件 id -> 音频条目（含 subtitle / type=file|event）
 *   block-props CSV   本项目 406 个新增方块各自的 1.21 SoundType
 *
 * 用法: node tools/crossversion/extract-sounds.js [--write]
 *   缺省只分析并打印清单；加 --write 才落地 json 与 ogg。
 */
'use strict';
const fs = require('fs');
const path = require('path');

const OFF = 'F:/HCMLNew/MCP-Reborn-release';
const SRC = OFF + '/src/main/java/net/minecraft';
const ASSETS = OFF + '/extracted-assets/assets/minecraft';
const REPO = path.resolve(__dirname, '../..');
const DIFF = 'F:/HCMLNew/SigmaClient/docs/registry-diff';
const WRITE = process.argv.includes('--write');

function read(p) { return fs.readFileSync(p, 'utf8'); }

// ---- 1. SoundEvents.java: 常量名 -> 事件 id ----
const constToId = new Map();
for (const m of read(SRC + '/sounds/SoundEvents.java')
    .matchAll(/public static final \S+(?:<[^>]*>)? (\w+)\s*=\s*\w+\("([^"]+)"/g)) {
  constToId.set(m[1], m[2]);
}

/**
 * 本项目（1.16.4 MCP 命名）已有的原版事件 id。
 *
 * <p><b>这是最关键的一道过滤。</b>overlay 里只能放 1.16.4 <b>没有</b>的事件：
 * 像 block.stone.step / block.wood.* / block.wool.* 这些 1.16.4 早就有，
 * 若把 1.21 的定义并进去就等于<b>改写原版条目</b>，既白占体积，又可能改变原版手感 ——
 * 正是要求里禁止的「破坏 1.16.4 原版音效」。
 * 用 1.16.4 SoundEvents.java 里的 register("...") 作为原版 id 的权威来源。
 */
const vanillaIds = new Set();
for (const m of read(REPO + '/src/main/java/net/minecraft/util/SoundEvents.java')
    .matchAll(/register\("([^"]+)"\)/g)) {
  vanillaIds.add(m[1]);
}

// ---- 2. SoundType.java: SoundType 名 -> 5 个事件 id ----
const soundTypeEvents = new Map();
for (const m of read(SRC + '/world/level/block/SoundType.java')
    .matchAll(/public static final SoundType (\w+)\s*=\s*new SoundType\(([\s\S]*?)\);/g)) {
  const refs = [...m[2].matchAll(/SoundEvents\.(\w+)/g)].map(x => x[1]);
  if (refs.length >= 5) soundTypeEvents.set(m[1], refs.slice(0, 5).map(c => constToId.get(c) || ('??' + c)));
}

// ---- 3. 本项目新增方块用到的 SoundType 集合 ----
const added = new Set(read(DIFF + '/added-blocks-1.16.4-to-1.21.11.txt').split(/\r?\n/).filter(Boolean));
const usedTypes = new Map(); // SoundType -> 方块数
const csv = read(DIFF + '/block-props-1.21.11.csv').split(/\r?\n/);
for (let i = 1; i < csv.length; i++) {
  const c = csv[i].split(',');
  if (c.length < 6 || !added.has(c[0])) continue;
  usedTypes.set(c[5], (usedTypes.get(c[5]) || 0) + 1);
}

// ---- 4. 显式调用点需要的事件（对照任务表 + 官方常量名）----
const CALLSITE_CONSTS = [
  'COPPER_BULB_TURN_ON', 'COPPER_BULB_TURN_OFF',
  'MACE_SMASH_GROUND', 'MACE_SMASH_GROUND_HEAVY', 'MACE_SMASH_AIR',
  'SPEAR_USE', 'SPEAR_HIT', 'SPEAR_ATTACK',
  'SPEAR_WOOD_USE', 'SPEAR_WOOD_HIT', 'SPEAR_WOOD_ATTACK',
  'LUNGE_1', 'LUNGE_2', 'LUNGE_3',
  'WIND_CHARGE_THROW', 'WIND_CHARGE_BURST',
  'CANDLE_EXTINGUISH', 'CAKE_ADD_CANDLE',
  'CAVE_VINES_PICK_BERRIES', 'AMETHYST_BLOCK_CHIME',
  'ARMOR_EQUIP_COPPER',
];

// ---- 5. 汇总需要的事件 id ----
const need = new Map(); // id -> 来源说明
function want(id, why) {
  if (!id) return;
  if (!need.has(id)) need.set(id, new Set());
  need.get(id).add(why);
}
for (const [t, n] of usedTypes) {
  const ev = soundTypeEvents.get(t);
  if (!ev) { want('!!MISSING_SOUNDTYPE:' + t, 'SoundType(' + n + ' blocks)'); continue; }
  for (const id of ev) want(id, 'SoundType.' + t + '(' + n + ')');
}
for (const c of CALLSITE_CONSTS) {
  const id = constToId.get(c);
  if (!id) { want('!!MISSING_CONST:' + c, 'callsite'); continue; }
  want(id, 'callsite:' + c);
}

// ---- 6. 从官方 sounds.json 取条目，递归展开 type=event，收集 ogg ----
const official = JSON.parse(read(ASSETS + '/sounds.json'));
const overlay = {};
const oggs = new Set();
const missingEvents = [];
const missingOggs = [];
const alreadyVanilla = [];

function collect(id, depth) {
  if (id.startsWith('!!') || overlay[id] !== undefined || depth > 4) return;
  // 1.16.4 已有的事件一律不动：原版条目保持原样，overlay 只做纯追加。
  if (vanillaIds.has(id)) { if (depth === 0) alreadyVanilla.push(id); return; }
  const entry = official[id];
  if (!entry) { missingEvents.push(id); return; }
  overlay[id] = entry;
  for (const s of entry.sounds || []) {
    const name = typeof s === 'string' ? s : s.name;
    const type = typeof s === 'string' ? 'file' : (s.type || 'file');
    if (type === 'event') { collect(name.replace(/^minecraft:/, ''), depth + 1); continue; }
    const rel = 'sounds/' + name.replace(/^minecraft:/, '') + '.ogg';
    if (fs.existsSync(ASSETS + '/' + rel)) oggs.add(rel);
    else missingOggs.push(rel + '  (from ' + id + ')');
  }
}
for (const id of need.keys()) collect(id, 0);

// ---- 7. 报告 ----
let bytes = 0;
for (const rel of oggs) bytes += fs.statSync(ASSETS + '/' + rel).size;
const out = [];
out.push('# 需要的事件: ' + need.size
  + '  | 1.16.4 已有(不动): ' + alreadyVanilla.length
  + '  | 需新增并写入 overlay: ' + Object.keys(overlay).length);
out.push('# ogg: ' + oggs.size + ' 个, ' + (bytes / 1048576).toFixed(2) + ' MiB');
out.push('');
out.push('## SoundType 家族 -> 官方事件 (依据 world/level/block/SoundType.java)');
out.push('##   [v]=1.16.4 已有, [+]=需新增');
for (const [t, n] of [...usedTypes].sort((a, b) => b[1] - a[1])) {
  const ev = soundTypeEvents.get(t);
  out.push('  ' + t.padEnd(26) + n.toString().padStart(3) + ' blocks  ' +
    (ev ? ev.map(e => (vanillaIds.has(e) ? '[v]' : '[+]') + e).join(' ') : '*** 官方无此 SoundType ***'));
}
out.push('');
out.push('## 需要 append 到 net/minecraft/util/SoundEvents.java 的新事件 id ('
  + Object.keys(overlay).length + ')');
out.push('##   顺序无所谓，但必须<b>追加在文件末尾扩展区</b>，绝不能插在原版常量中间（raw ID 会挤位）');
Object.keys(overlay).sort().forEach(e => out.push('  ' + e));
out.push('');
out.push('## 命中 1.16.4 原版、已被跳过的事件 (' + alreadyVanilla.length + ')');
alreadyVanilla.sort().forEach(e => out.push('  ' + e));
out.push('');
out.push('## 官方 sounds.json 里找不到的事件 (' + missingEvents.length + ')');
missingEvents.sort().forEach(e => out.push('  ' + e));
out.push('');
out.push('## sounds.json 引用但 ogg 不存在 (' + missingOggs.length + ')');
missingOggs.sort().forEach(e => out.push('  ' + e));
out.push('');
out.push('## 需要但官方常量/SoundType 不存在');
[...need.keys()].filter(k => k.startsWith('!!')).sort().forEach(e => out.push('  ' + e));

const rep = REPO + '/target/crossversion-check';
fs.mkdirSync(rep, { recursive: true });
fs.writeFileSync(rep + '/sound-manifest.txt', out.join('\n') + '\n');
console.log(out.join('\n'));

// ---- 8. 落地 ----
if (WRITE) {
  const dst = REPO + '/src/main/resources/assets/minecraft';
  const sorted = {};
  for (const k of Object.keys(overlay).sort()) sorted[k] = overlay[k];
  fs.writeFileSync(dst + '/sounds-modern.json', JSON.stringify(sorted, null, 2) + '\n');
  // 先清空再写，否则上一轮多带的 ogg（例如误并进来的原版音频）会留成幽灵文件。
  fs.rmSync(dst + '/sounds', { recursive: true, force: true });
  let n = 0;
  for (const rel of oggs) {
    const to = dst + '/' + rel;
    fs.mkdirSync(path.dirname(to), { recursive: true });
    fs.copyFileSync(ASSETS + '/' + rel, to);
    n++;
  }
  console.log('\nwrote sounds-modern.json (' + Object.keys(sorted).length + ' events) + ' + n + ' ogg');
}
