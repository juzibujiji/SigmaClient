# 1.16.4 客户端注册表扩展到 1.21.11 —— 方案与调研

> 目标：让客户端原生认识 1.17–1.21.11 的物品与方块，使跨版本连服时显示、硬度、
> 挖掘预测、工具等级、附魔与重锤/长矛等行为与高版本一致。

---

## 1. 结论摘要

**可行，但这是一个需要分阶段推进的大工程。** 三个决定性的有利条件：

1. 本项目是**完整反编译的 1.16.4 源码**（`src/main/java/net/minecraft/` 下 3413 个文件），
   不是 Forge mod —— 可以真正往注册表里加物品，而不是只做贴图欺骗。
2. 注册表 ID **按源码书写顺序自增分配**，没有硬编码上限。在 `Items.java` / `Blocks.java`
   **末尾追加**不会改动任何现有 ID。
3. 资源（材质 / 模型 / blockstates / lang / tags）**直接打包在仓库里**，不依赖外部 jar，
   可以直接补充。

主要阻力不在"注册物品"本身，而在**让 ViaBackwards 别把物品降级掉**，以及
**把 1.20.5+ 的数据组件语义翻译回 1.16.4 的硬编码物品类**。

---

## 2. 现状与根因

客户端已集成 Via 全家桶（ViaVersion / ViaBackwards / ViaRewind / ViaLegacy 5.9.1），
GUI 可选 1.7.2 – 1.21.11（`de/florianmichael/viamcp/protocolinfo/ProtocolInfo.java`）。
原生版本硬编码为 754（`net/minecraft/util/SharedConstants.java:53`）。

但**客户端注册表仍是纯 1.16.4**。ViaBackwards 遇到 1.16.4 不存在的物品时，只能
**替换成最相似的旧物品**并覆盖显示名。实测降级链（16 步，1.21.11 → 1.16.4）结果：

| 1.21.11 真实物品 | 降级后客户端实际收到 | 后果 |
|---|---|---|
| `mace` 重锤 | `netherite_axe` | 攻击力/蓄力/砸落伤害全错 |
| `netherite_spear` 长矛 | `netherite_sword` | 攻击距离、穿刺失效 |
| `copper_pickaxe` 铜镐 | `iron_pickaxe` | 挖掘等级/速度/耐久错 |
| `copper_sword` 铜剑 | `iron_sword` | 攻击力错 |
| `deepslate` 深板岩 | `blackstone` | **硬度 3.0 → 1.5，挖掘预测错** |
| `cherry_planks` 樱花木板 | `acacia_planks` | 外观错 |
| `breeze_rod` 旋风棒 | `blaze_rod` | 外观错 |
| `trial_key` 试炼钥匙 | `tripwire_hook` | 外观错 |
| `copper_bulb` 铜灯 | `redstone_lamp` | 外观错 |
| `amethyst_shard` 紫水晶碎片 | `prismarine_shard` | 外观错 |

> 复现方式见 `docs/registry-diff/Chain.java`（用 Via 自带映射数据跑完整降级链）。

方块侧同理。项目已有的 `jello/util/game/world/ExtendedBlockStateMapper.java`
把现代方块状态映射回 1.16.4，映射失败时 **fallback 成 `Blocks.STONE`**
（该文件第 60-66 行）—— 这就是高版本方块变石头的直接原因。

---

## 3. 精确规模

数据由 Via 5.9.1 自带映射表导出（工具见 `docs/registry-diff/Diff.java`）。

| 维度 | 1.16.4 | 1.21.11 | 需新增 |
|---|---|---|---|
| 物品 | 976 | 1505 | **533** |
| 方块 | 763 | 1166 | **406** |
| 方块状态 | 17112 | 29671 | **12559** |
| 属性 attribute | 约 13 | 35 | 22 |
| 数据组件 data component | 0 | 104 | 全部（1.20.5 引入） |

各版本物品数增长：

```
1.16.2  976   1.19.4 1228   1.21    1333   1.21.6 1415
1.17   1100   1.20   1255   1.21.2  1375   1.21.7 1416
1.18   1101   1.20.3 1312   1.21.4  1385   1.21.9 1488
1.19   1152   1.20.5 1330   1.21.5  1396   1.21.11 1505
```

完整清单：
- `docs/registry-diff/added-items-1.16.4-to-1.21.11.txt`（533 行）
- `docs/registry-diff/added-blocks-1.16.4-to-1.21.11.txt`（406 行）

**重命名（不是删除，需要做别名）**：

| 1.16.4 | 1.21.11 |
|---|---|
| `grass` | `short_grass` |
| `grass_path` | `dirt_path` |
| `scute` | `turtle_scute` |
| `chain` | （物品与方块均已重整） |

### 值得单独点出的新增内容

- **铜装备全套**（1.21.9+）：`copper_sword` `copper_shovel` `copper_pickaxe`
  `copper_axe` `copper_hoe` `copper_helmet` `copper_chestplate` `copper_leggings`
  `copper_boots` `copper_nugget` `copper_horse_armor`
  → 需要新增一个 `ItemTier.COPPER` 等级
- **长矛全套 7 种**：`wooden_spear` `stone_spear` `copper_spear` `iron_spear`
  `golden_spear` `diamond_spear` `netherite_spear`
- **重锤**：`mace` + `breeze_rod` + `wind_charge`
- **深板岩系列**：含 7 种深板岩矿石，全部需要挖掘等级支持
- **铜氧化系列**：copper / exposed / weathered / oxidized × waxed 变体，
  含 `copper_grate` `copper_bulb` `copper_chest` `copper_golem_statue`

### 铜相关的真实工作量（常见误判点）

铜相关新增 **132 个物品 / 117 个方块**，数量看着吓人，但去掉
`waxed_` / `exposed_` / `weathered_` / `oxidized_` 前缀后，**基础种类只有约 30 种**：

```
copper_block  chiseled_copper  cut_copper  cut_copper_stairs  cut_copper_slab
copper_grate  copper_bulb  copper_chest  copper_door  copper_trapdoor
copper_bars  copper_chain  copper_lantern  copper_torch  copper_golem_statue
copper_ore  copper_ingot  copper_nugget  raw_copper  raw_copper_block
copper_sword  copper_shovel  copper_pickaxe  copper_axe  copper_hoe  copper_spear
copper_helmet  copper_chestplate  copper_leggings  copper_boots
copper_horse_armor  copper_nautilus_armor  copper_golem_spawn_egg
```

氧化 / 蜡封是规整的 4 × 2 机械组合，**由生成器批量产出**，不是 132 份独立工作。
真正需要单独处理的只有 `copper_chest`（方块实体）与 `copper_golem`（生物，见下）。

### 生物：独立工程，不在本次范围

| | 1.16.4 | 1.21.11 | 新增 |
|---|---|---|---|
| 实体类型 | 108 | 157 | **51** |

但 51 个里分三类，工作量差别极大：

1. **约 20 个是船 / 箱船变体**（`oak_boat` `birch_chest_boat` `bamboo_raft` …）。
   1.16.4 只有一个 `boat` 靠 NBT 区分木种，1.19+ 拆成独立实体类型。
   **不需要新模型**，只要注册 + 映射。
2. **5 个是技术性实体**（`block_display` `item_display` `text_display`
   `interaction` `marker`）、外加 `ominous_item_spawner` `breeze_wind_charge`
   `wind_charge` `splash_potion` `lingering_potion` 等无实体模型项。
3. **约 20 个是真正的新生物**，需要模型 + 渲染器 + 动画 + AI：
   `warden` `allay` `frog` `tadpole` `camel` `sniffer` `breeze` `bogged`
   `armadillo` `creaking` `happy_ghast` `copper_golem` `axolotl` `glow_squid`
   `goat` `nautilus` `zombie_nautilus` `parched` `mannequin` `camel_husk`

第 3 类每个都需要手写模型与渲染器，**建议物品与方块完工并验收后再单独立项**。
在此之前，这些生物在客户端会继续被 ViaBackwards 降级成相近的旧生物。

---

## 4. 关键约束与风险

### 4.1 raw ID 绝不能移位（硬约束）

物品 ID 0–975、方块 ID 0–762 必须逐一保持不变，否则连 1.8/1.12/1.16.4 服务器时
物品全部错位。**只能在文件末尾追加**：

- 物品追加点：`net/minecraft/item/Items.java:992`（`RESPAWN_ANCHOR` 之后），新 ID 从 976 起
- 方块追加点：`net/minecraft/block/Blocks.java:889`（`QUARTZ_BRICKS` 之后），新 ID 从 763 起
- `Blocks.java` 末尾的 static 块（978-986 行）填充 `BLOCK_STATE_IDS`，
  **必须保持在所有方块字段之后**

但 1.21.11 的原生 ID 是**穿插**的（`deepslate` 在 1.21.11 是 id=8），
所以不能直接沿用目标版本 ID。**必须建立"标识符字符串 ↔ 本地扩展 ID"的映射层。**

### 4.2 方块状态位宽（需要盯住）

`util/palette/PalettedContainer.java:94` 用 `log2(registry.size())` 决定全局调色板位宽。

- 1.16.4：17112 → 15 bit
- 扩展后：29671 → 仍是 15 bit ✓（阈值 32768，余量约 3100）

**不会撞上限，但余量不大。** 若将来再加自定义方块或更高版本内容，超过 32768 就会
跳到 16 bit，与 ViaBackwards 重编码出的 15 bit 区块数据错位。**这条要写进回归测试。**

### 4.3 挖掘等级是硬编码 if-else，不是 tag（最大的坑）

`net/minecraft/item/PickaxeItem.java:22-49` 的 `canHarvestBlock` 是三层硬编码 if 链，
配合第 12 行一个约 90 项的 `EFFECTIVE_ON` ImmutableSet 决定挖掘速度。
`AbstractBlock.Properties` **没有** `harvestLevel()` / `harvestTool()`（那是 Forge 扩展），
原版只有 `requiresTool` 布尔。

→ 加 406 个方块时，如果不改这套体系，**深板岩矿石、铜矿、凝灰岩等会全部挖不动或挖了不掉落**。
同样问题存在于 `AxeItem` `ShovelItem` `HoeItem` `SwordItem`（各有自己的 `EFFECTIVE_ON`）。

建议改成 tag 驱动（对齐 1.17+ 的 `mineable/*` + `needs_*_tool`），一次性解决。

### 4.3.1 tag 重写对现有客户端模块的影响（已实测，结论：透明）

改 `PickaxeItem` 这类文件之前必须确认不会打坏 AutoTools / InvManager / AutoBuy /
ChestStealer。逐个查证结果：

| 模块 | 依赖点 | 用法 |
|---|---|---|
| `AutoTools.java:60` | → `InvManagerUtil.findBestToolFromHotbarSlotForBlock(state)` | 间接 |
| `InvManagerUtil.java:156` | `item.getDestroySpeed(state)` | 读取并比大小 |
| `InvManager.java:168,173,179` | `getDestroySpeed(stack, OAK_LOG/DIRT/STONE)` | 固定参照方块算分排序 |
| `AutoBuy.java:204-209` | `getDestroySpeed(stack, STONE/DIRT/OAK_LOG)` | 同上 |
| `ChestStealer.java` | 无 | **完全不依赖** |

四处用法**完全一致**：调 `getDestroySpeed` 拿一个 float 当"这把工具对这个方块有多好"的
分数，然后取最大值。`InvManagerUtil` 的核心就是：

```java
damage = item.getDestroySpeed(state);
if (damage > dmg) { slot = hotbarSlot; dmg = damage; }
```

**全部是只读调用，没有任何模块依赖 `canHarvestBlock` 或 `EFFECTIVE_ON` 的内部结构。**
只要 tag 重写保持两件事不变，这些模块**完全不用改**：

1. 签名不变：`Item.getDestroySpeed(ItemStack, BlockState)` 与
   `ItemStack.getDestroySpeed(BlockState)`
2. 语义不变：工具对该方块有效时返回 `efficiency`，无效时返回 `1.0F`

反过来，tag 重写还会**修掉一个现存 bug**：深板岩现在被降级成黑石，AutoTools 是按黑石
的属性选工具的，本来就是错的；tag 化后才会正确。

**唯一需要主动适配的点**：`InvManagerUtil.java:153` 在 `state == null`（选武器）分支里写的是

```java
if (!(item.getItem() instanceof SwordItem)) { continue; }
damage = ((SwordItem) item.getItem()).getAttackDamage();
```

它只认 `SwordItem`。新增的 `mace` 与 7 种 `spear` 如果不是 `SwordItem` 子类，
AutoTools 的选武器分支就会忽略它们。需要把这个判断改成基于攻击力的通用判断
（这是新增物品必须做的适配，与 tag 重写无关）。

### 4.4 1.16.4 没有数据组件

1.20.5 起物品行为由组件描述，重锤与长矛依赖的组件包括：
`tool` `weapon` `piercing_weapon` `kinetic_weapon` `minimum_attack_charge`
`attack_range` `blocks_attacks` `swing_animation` `enchantable` `equippable`。

1.16.4 把这些硬编码在物品类里。**不打算移植整套组件系统**，而是把用到的组件语义
翻译成 1.16.4 的物品类实现（见阶段 3）。

### 4.5 客户端与服务端的职责边界（影响验收标准）

多人游戏里伤害、挖掘、掉落**由服务器计算**，客户端只负责显示与预测。所以：

- **多人跨版本**：客户端需要正确的外观、tooltip、属性数值、挖掘速度预测、
  攻击距离预测。硬度错会导致挖掘不同步（ghost block）与反作弊误判。
- **单人游戏**：内置服务端会真的跑逻辑，需要完整实现配方、掉落表、
  方块交互、生成 —— 工作量约为前者的 3–5 倍。

这两个目标的工作量差别很大，需要先定范围。

### 4.6 三处硬编码 ladder 需同步

加版本时必须同时改：
- `de/florianmichael/viamcp/protocolinfo/ProtocolInfo.java:29-93`（GUI 版本列表）
- `jello/util/game/world/ExtendedBlockStateMapper.java:151-188`（19 个 protocol 类名）
- `jello/managers/ViaManager.java:476-511`（`CLIENT_TICK_END` 分支）

### 4.7 创造模式分类数量不够（必须处理）

`ItemGroup.GROUPS = new ItemGroup[12]`（`item/ItemGroup.java:15`）是全项目唯一真正的
硬编码上限。**要复刻 1.21.11 的创造栏，这个数字不够：**

| | 数量 | 分类 |
|---|---|---|
| 1.16.4 | **12** | buildingBlocks, decorations, redstone, transportation, misc, food, tools, combat, brewing + search / hotbar / inventory |
| 1.21.11 | **16** | buildingBlocks, coloredBlocks, natural, functional, redstone, tools, combat, foodAndDrink, ingredients, consumables, crafting, spawnEggs, op + search / hotbar / inventory |

需要做三件事：

1. `ItemGroup.GROUPS` 从 12 扩到 16，新增 `coloredBlocks` `natural` `functional`
   `ingredients` `consumables` `crafting` `spawnEggs` `op` 等分类
2. 改 `client/gui/screen/inventory/CreativeScreen.java` 的 tab 布局。当前硬编码
   "每行 6 个、上下两排" = 12 个位置（第 319/504/520/546/564/727/764/798/833/835 行
   都按 `ItemGroup.GROUPS` 遍历或索引）
3. **每个物品归属哪个 tab 的信息不在官方 report 里**（硬编码在 1.21.11 的
   `CreativeModeTabs` 类中），需要从反混淆后的源码提取

这是有界的已知工作量，不是风险点，但不能漏。

### 4.8 其他
- `ModelBakery.java:199-212` 会遍历所有注册项加载模型：**每个新方块需要
  `blockstates/<name>.json`，每个新物品需要 `models/item/<name>.json`**，
  否则启动时 log warn 且显示紫黑块。
- 磁盘与 NBT 用字符串 ID（`ItemStack.java:138,251`），协议用 VarInt raw ID，无宽度上限。

---

## 5. 方案架构

核心思想：**保留 0–975 原样，新内容追加在后面，用标识符字符串做跨版本桥接。**

### 阶段 0 —— 代码生成器（地基，已验证可行）

不手写 533 + 406 条注册代码，而是写一个离线生成器：

1. 从 Via 自带映射数据导出目标版本的完整标识符表（**已验证**，见
   `docs/registry-diff/Diff.java`，用 `MappingDataLoader.identifiersFromGlobalIds`）
2. 从官方 registry report + 反编译源码提取属性（硬度、爆炸抗性、材质、发光、
   耐久、攻击力、挖掘等级）
3. 生成 `Items` / `Blocks` 的追加代码片段、`ModernRegistry` 映射表、tag json

产物必须可重跑、可 diff，便于后续版本升级。

### 阶段 1 —— 注册表扩展 + 版本门控

- 在 `Items.java:992` / `Blocks.java:889` 之后追加注册
- 新建 `ModernRegistry`：标识符 ↔ 本地扩展 ID 双向表，含重命名别名
- **版本门控**：连 1.16.4 或更老时，新物品不得出现在创造模式、配方与发往服务器的包里
  （否则老服务器的 Via 映射查不到 ID，会变 air 或断连）
- **验收**：写一个测试断言物品 0–975、方块 0–762 的标识符逐一等于原版

### 阶段 2 —— 让 Via 透传而非降级

项目里已有两个现成模板可以照抄手法：
- `ExtendedBlockStateMapper`：反射读 ViaBackwards `MappingData` 串成映射链
- `NetworkManager.InteractionSequenceProtocol`：自定义 `AbstractProtocol` 反射插入管道

做法：用 `FullMappings.identifier(id)` 拿到服务器物品的**标识符字符串**，
绕过逐级降级，直接映射到本地扩展 ID。同时把 `ExtendedBlockStateMapper`
的 `fallback → STONE` 改成 `fallback → 新注册方块`。

### 阶段 3 —— 物品行为

- 新增 `ItemTier.COPPER`（`item/ItemTier.java`，介于 STONE 与 IRON 之间）
- 7 种长矛：攻击距离、蓄力投掷、穿刺
- `mace`：蓄力砸落伤害曲线、下落高度加成、击退
- 新附魔：`density` `breach` `wind_burst`（重锤）、`swift_sneak`
- 补 22 个新 attribute（至少让 tooltip 正确显示）
- 把 `tool` / `weapon` / `attack_range` / `minimum_attack_charge` 组件语义
  翻译进对应物品类

### 阶段 4 —— 资源

从官方 1.21.11 客户端 jar 提取并合并：材质 PNG、`models/item`、`models/block`、
`blockstates`、`lang`。工具见 `tools/crossversion/ExtractAssets.java`，会递归跟随
blockstate → 模型 → parent 链 → 材质 → mcmeta 的全部依赖，且**只写入项目中尚不存在的
文件**，绝不覆盖原版资源。

**关于 1.21.4+ 的模型新格式（实测结论，比预想的简单）**：官方 jar 里确实新增了
`assets/minecraft/items/*.json`（item model definition），1.16.4 不认识 —— 但
`models/item/*.json` 与 `models/block/*.json` **仍然是 1.16.4 完全兼容的旧格式**，
所以<b>不需要做格式转换，只要忽略 `items/` 目录</b>。

唯一的例外是**方块物品的模型**：1.21 把 `models/item/<方块名>.json` 删掉了，只保留新格式。
这种情况从 `items/<名>.json` 读出它引用的模型，生成旧式 `{"parent": "<模型>"}`。
新格式恰好把特殊情况也说清楚了（例如 `items/cherry_door.json` 指向 `item/cherry_door`
而不是 `block/cherry_door`），比按规则猜可靠。

少数物品用了按状态切换模型的新特性（`condition` / `select` / `range_dispatch`），
1.16.4 表达不了，工具会取第一个分支并列出清单供人工复核。

还需扩 `ItemGroup.GROUPS` 从 12 到 16 并改 `CreativeScreen` tab 布局，
tab 归属需从反混淆源码提取 `CreativeModeTabs`（见 4.7 节）。

### 阶段 5 —— 方块属性与挖掘体系

- 406 个方块的硬度、爆炸抗性、`Material`、`MaterialColor`、发光、音效
- 把 `PickaxeItem` 等的硬编码 if 链与 `EFFECTIVE_ON` 改成 tag 驱动
- 补 `data/minecraft/tags/blocks/mineable/*` 与 `needs_*_tool`
- **这一步直接决定"硬度和石镐性质与高版本一致"能否达成**

### 阶段 6 —— 回归

- 逐版本冒烟测试：1.8 / 1.12.2 / 1.16.4 / 1.20.4 / 1.21.11
- 断言 0–975 / 0–762 ID 未移位
- 断言 blockstate 总数 < 32768
- 反作弊兼容复查（项目大量代码针对 Grim 的具体检查编写，
  挖掘速度与攻击距离预测变化可能触发误判）

---

## 6. 数据源：已全部就位并交叉验证

jar 位置：`F:\HCMLNew\SigmaClient\1.21.11\{client.jar, server.jar}`
（构建日期 2025-12-09，与 `ProtocolInfo` 中 1.21.11 的日期一致）。
本机 `~/.jdks/liberica-21.0.3` 满足 1.21.11 要求的 Java 21。

| # | 数据源 | 状态 | 提供什么 |
|---|---|---|---|
| 1 | `server.jar --reports` → `registries.json` | ✅ 已生成 | 1505 物品 / 1166 方块的权威标识符与 protocol_id |
| 2 | `server.jar --reports` → `items.json` | ✅ 已生成 | **全部 1505 个物品的官方默认数据组件** |
| 3 | `server.jar --reports` → `blocks.json` | ✅ 已生成 | 全部方块状态定义与精确 state id |
| 4 | `client.jar` | ✅ 就位 | 材质 / 模型 / blockstates / lang（混淆不影响资源提取） |
| 5 | 官方 client mappings | ✅ 已确认可下载（11.7 MB） | 反混淆后提取方块硬度与创造栏 tab 归属 |
| 6 | **反编译的 1.21.11 源码** | ✅ 就位 | `F:\HCMLNew\MCP-Reborn-release\src\main\java\` —— **不用再自己反编译** |

第 6 项是后来补上的，价值很大：**需要精确移植行为逻辑时直接读它**，
比从组件数据反推或查 wiki 可靠得多。后续会用到的位置：

| 需求 | MCP-Reborn 路径（相对 `src/main/java/`） |
|---|---|
| 重锤砸落伤害曲线 | `net/minecraft/world/item/MaceItem.java` |
| 长矛（蓄力、投掷、穿刺） | `net/minecraft/world/item/` 下 spear 相关类 |
| 铜工具 / 盔甲数值 | `net/minecraft/world/item/ToolMaterial.java`、`ArmorMaterials.java` |
| 重锤三附魔的效果实现 | `net/minecraft/world/item/enchantment/` |
| 官方创造栏顺序与分类 | `net/minecraft/world/item/CreativeModeTabs.java` |
| 铜氧化 / 打蜡逻辑 | `net/minecraft/world/level/block/WeatheringCopper*.java` |
| 蜡烛、悬挂告示牌、架子 | `net/minecraft/world/level/block/` 下同名类 |

注意它是 **1.21.11 的类结构与 Mojang 官方映射**（`world/item/`、`world/level/block/`），
与本项目 1.16.4 的 MCP 命名（`net/minecraft/item/`、`net/minecraft/block/`）不同 ——
属性名、方法名都要对照转换，不能直接抄。

**不用它的 `CreativeModeTabs` 做分类**：那是 1.21 的划分，把门归到「功能方块」，
而 1.16.4 原版门在红石。目标是与本客户端的原版体验一致，所以继续用
「按方块类型跟原版同类投票」的方案（见 §9.13）。

生成命令（Java 21 + bundler）：

```bash
java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --reports
```

### 6.1 交叉验证结果：零差异

用两个**完全独立**的数据源比对，确认清单没有遗漏：

| 注册表 | 官方 registries.json | Via 5.9.1 映射数据 | 逐 ID 顺序不一致 | 仅一方有 |
|---|---|---|---|---|
| `minecraft:item` | 1505 | 1505 | **0** | **0** |
| `minecraft:block` | 1166 | 1166 | **0** | **0** |

验证工具：`docs/registry-diff/Verify.java`。这条验证会作为阶段 1 的自动化测试保留。

### 6.2 属性数据：不需要手抄，官方直接给

`items.json` 含每个物品的完整默认组件。例如 `mace`：

```json
"minecraft:attribute_modifiers": [
  { "type": "attack_damage", "amount": 5.0,  "operation": "add_value", "slot": "mainhand" },
  { "type": "attack_speed",  "amount": -3.4, "operation": "add_value", "slot": "mainhand" } ],
"minecraft:max_damage": 500,
"minecraft:enchantable": { "value": 15 },
"minecraft:rarity": "epic",
"minecraft:repairable": { "items": "minecraft:breeze_rod" },
"minecraft:tool": { "damage_per_block": 2, "can_destroy_blocks_in_creative": false, "rules": [] },
"minecraft:weapon": {}, "minecraft:swing_animation": {}
```

`netherite_spear` 更完整，长矛的手感参数全在里面：

```json
"minecraft:attack_range": { "max_reach": 4.5, "min_reach": 2.0,
                            "hitbox_margin": 0.125, "mob_factor": 0.5 },
"minecraft:minimum_attack_charge": 1.0,
"minecraft:piercing_weapon": { "sound": "item.spear.attack" },
"minecraft:kinetic_weapon": { "damage_multiplier": 1.2, "forward_movement": 0.38,
    "damage_conditions": { "min_relative_speed": 4.6, "max_duration_ticks": 175 },
    "dismount_conditions": { "min_speed": 7.0, "max_duration_ticks": 50 } },
"minecraft:max_damage": 2031, "minecraft:damage_type": "minecraft:spear"
```

普通武器 reach 是 3.0，长矛 4.5 —— 这就是长矛"更长"的确切来源。
**这些数值全部来自官方数据，不是手抄 wiki**，所以数值层面能做到与高版本完全一致。

### 6.3 方块硬度：已提取完成（1166 / 1166）

`blocks.json` 只有方块状态定义，**不含硬度 / 爆炸抗性**（搜 `hardness`
`destroy_time` `explosion_resistance` 均零命中），且 client.jar 完全混淆
（类名形如 `fzu` `gds`）。已用官方 mappings 反射提取解决，工具见
`docs/registry-diff/ExtractHardness.java`，产物 `docs/registry-diff/block-hardness-1.21.11.csv`。

做法：下载官方 client mappings 解析出混淆名，用 Java 21 加载 server.jar，
`Bootstrap` 初始化后遍历 `BuiltInRegistries.BLOCK`，反射读 `destroySpeed`
与 `explosionResistance`。**遍历注册表而不是 `Blocks` 类字段**——因为 1.21.9 起
`copper_bars` / `copper_chain` / `copper_lantern` 的字段类型是容器类
`WeatheringCopperBlocks`（把氧化四阶段与蜡封变体打包），按字段遍历会漏掉它们。

1.21.11 关键混淆名（升版本后需重新解析）：

| 符号 | 混淆名 |
|---|---|
| `Blocks` / `Block` / `BlockState` / `BlockBehaviour` | `dzs` / `dzq` / `eoh` / `eog` |
| `BuiltInRegistries` + 其 `BLOCK` 字段 | `mi` + `e` |
| `Registry.getKey(Object)` | `jq.b` |
| `Block.defaultBlockState` 字段 | `d` |
| `BlockStateBase.destroySpeed` 字段 | `p` |
| `BlockBehaviour.explosionResistance` 字段 | `G` |
| `SharedConstants.tryDetectVersion` / `Bootstrap.bootStrap` | `w.a` / `amv.a` |

**结果：1166 个方块全部提取成功，零失败。** 抽样核对官方已知值：

```
stone 1.5/6.0    obsidian 50.0/1200.0    ancient_debris 30.0/1200.0
deepslate 3.0/6.0    polished_deepslate 3.5/6.0    deepslate_diamond_ore 4.5/3.0
calcite 0.75/0.75    mud 0.5/0.5    copper_block 3.0/6.0    vault 50.0/50.0
bedrock -1.0/3600000.0
```

注意 `deepslate` 硬度是 **3.0**，而它现在被降级成的 `blackstone` 只有 **1.5**
—— 这就是挖掘速度预测错误的确切数值来源。

### 6.4 仍需提取的部分

- **创造栏 tab 归属**：每个物品属于哪个分类，硬编码在 1.21.11 的 `CreativeModeTabs`
  中，不在官方 report 里，需要反混淆后提取（与 4.7 节的 tab 扩容配套）

---

## 7. 已确认的范围

| 项 | 决定 |
|---|---|
| 目标场景 | 多人跨版本与单人**都要**，**多人优先**（先做通并验收多人链路，再补单人逻辑） |
| 覆盖范围 | **一次做完全部 533 物品 / 406 方块** |
| 挖掘体系 | **重写成 tag 驱动**，对齐 1.17+ 的 `mineable/*` + `needs_*_tool` |
| 生物 | **不在本次范围**，物品与方块验收后单独立项 |
| 资源来源 | 从 HCML 下载官方 1.21.11，属性数据由反编译提取 |

---

## 8. 工期与一致性预期

### 8.1 推进方式

这不是一次能做完的工程，按 6 个阶段推进，**每个阶段结束时都能编译通过并单独验收**，
不会出现"做到一半整个客户端起不来"的状态。粗略的阶段权重：

| 阶段 | 内容 | 相对工作量 |
|---|---|---|
| 0 | 代码生成器 + 官方数据提取流程 | 中（一次性投入，后续升版本可复用） |
| 1 | 注册表扩展 533 + 406、版本门控、ID 不移位测试 | 中（生成器产出，主要成本在校验） |
| 2 | Via 透传而非降级 | **高（最核心的难点，风险集中在这里）** |
| 3 | 物品行为：铜等级、长矛、重锤、新附魔、属性 | 高 |
| 4 | 资源提取与合并（含 1.21.4+ 模型格式转换） | 中 |
| 5 | 方块属性 + 挖掘体系 tag 化 | 高 |
| 6 | 逐版本回归、反作弊复查 | 中 |

建议的验收顺序是**先打通一条竖切**：拿 `deepslate` + `copper_pickaxe` + `mace` 三个
样本走完阶段 1→2→3→4→5 的全流程，确认整条链路无误，再让生成器批量铺开其余 530 项。
这样如果阶段 2 的设计有问题，返工面只有 3 个物品而不是 533 个。

### 8.2 能做到多一致（分维度，诚实评估）

| 维度 | 预期一致度 | 依据 |
|---|---|---|
| 外观：材质 / 模型 / 名称 / 翻译 | 几乎 100% | 直接用官方 1.21.11 资源，不是重画 |
| 数值：硬度 / 爆炸抗性 / 耐久 / 攻击力 / 挖掘等级 / 附魔能力 | 几乎 100% | 从官方反编译提取，不是手抄 wiki |
| 多人：显示 + 挖掘速度与攻击距离预测 | 95%+ | 伤害与掉落本就由服务器计算，客户端只需预测一致 |
| 单人：普通方块与工具武器 | 90%+ | 行为简单，可直接移植 |
| 单人：复杂机制 | 60–80% | 见下 |

**做不到 100% 的地方，以及原因**（这些不是偷懒，是架构差异）：

1. **数据组件是翻译而非移植**。1.20.5 起物品行为由 104 个组件描述
   （`tool` `weapon` `piercing_weapon` `kinetic_weapon` `minimum_attack_charge`
   `attack_range` …），1.16.4 是硬编码在物品类里。我们把用到的组件语义翻译进物品类，
   常见组合能完全对上，但服务器若用数据包下发非常规组件组合，客户端无法通用地表达。
2. **部分新机制依赖 1.16.4 不存在的底层系统**：试炼刷怪笼与宝库的状态机、
   铜傀儡、蜂巢行为、锻造台模板系统、`copper_chest` 等新方块实体。
3. **22 个新 attribute 里有几个在 1.16.4 无对应系统**
   （如 `waypoint_transmit_range` `camera_distance`），只能显示、不能生效。

对你点名的几样东西的具体预期：

- **方块硬度**：100% 一致（数值直接来自官方源码），挖掘速度预测随之正确
- **石镐 / 铜镐等工具性质**：100% 一致（新增 `ItemTier.COPPER`，
  等级 / 耐久 / 效率 / 附魔能力照官方值）
- **附魔**（含 `density` `breach` `wind_burst` `swift_sneak`）：数值与上限 100%，
  多人下实际伤害由服务器算，客户端负责 tooltip 与预测正确
- **重锤 mace**：蓄力砸落伤害曲线、下落高度加成、击退可完整实现
- **长矛 spear**：攻击距离、蓄力、穿刺可实现；投掷实体的碰撞细节需要实测校准

---

## 9. 实施进度

### 9.1 已完成：竖切（deepslate + copper_pickaxe + mace）

按「先打一条竖切」的策略，三个样本已跑通注册 → 资源 → 属性全链路，
回归检查全绿。运行方式：

```bash
bash tools/crossversion/run-registry-check.sh
```

验收结果：

```
物品 0-975：全部一致 OK          方块 0-762：全部一致 OK
deepslate 物品 id=976  方块 id=763  方块状态=[17112, 17113, 17114]
copper_ingot 977   copper_pickaxe 978   mace 979
short_grass -> minecraft:grass id=89        （改名别名生效）
方块状态位宽：17115 需要 15 bit（与原版一致）

deepslate 硬度 3.0 OK              deepslate 爆炸抗性 6.0 OK
blackstone 硬度 1.5 OK             （对照：降级后会用这个错值）
copper_pickaxe 挖 deepslate 5.0 OK  stone 4.0 OK   iron 6.0 OK
COPPER 耐久 190 / 速度 5.0 / 附魔 13 / 等级 1  全部 OK
mace 耐久 500 / 附魔 15 / 攻击力 5.0 / 攻速 -3.4  全部 OK
```

**原版 `Items.java` 与 `Blocks.java` 一个字节都没改。** 新内容全在
`ModernItems` / `ModernBlocks` 里，由 `Bootstrap` 在原版初始化完成后注册。
改动的原版文件都是纯追加：`MaterialColor`（+3 个地图颜色）、`ItemTier`（+COPPER）、
`Bootstrap`（+2 行初始化）。

### 9.2 过程中的发现

**项目已有跨版本硬度修正框架。** `AbstractBlock$AbstractBlockState.getBlockHardness`
已经按目标版本修正末地石砖（1.15 从 0.8 提到 3.0）、活塞（1.16 从 0.5 提到 1.5）、
蠹虫方块（三段不同语义）的硬度。新方块的硬度应当融入这个既有模式，而不是另起一套。

**`JelloPortal.getVersion()` 在 Via 未初始化时会 NPE。** 它被 `getBlockHardness`
直接调用，意味着任何在 Via 初始化之前走到挖掘相关代码的路径都会崩。项目别处
（`WorldHeightHelper.getTargetVersionSafe`）已有 try-catch 回退模式，因此给
`JelloPortal` 补了 `getVersionSafe()` 并在 `getBlockHardness` 中改用它 ——
回退到 1.16.4 语义上正确：没有目标版本就意味着不需要跨版本修正。

**创造栏顺序必须与 raw ID 解耦。** 原版 `ItemGroup.fill` 按 `Registry.ITEM` 的迭代
顺序（即 raw ID 顺序）填充。扩展内容的 raw ID 必须追加在原版之后，结果新物品全部堆在
创造栏末尾 —— 深板岩不在石头旁边而在最后一页。解法是 `ModernRegistry.creativeOrder()`：
按打包在 classpath 的官方 1.21.11 顺序表排列，让新旧物品交错回应有位置，
表里没有的（1.21.11 已移除或改名的）追加在末尾。验证结果 `deepslate` 位置从 976 变成 8。

**语言文件不能走资源包。** 本客户端的资源包语言 JSON 不可靠 —— 项目里
`MmdSkinLangInjector` 的注释明确记录了这一点，它为此改成了编译期嵌入。
往 `assets/minecraft/lang/en_us.json` 加键能生效，是因为 `LanguageMap` 有一条
**硬编码的 classpath 直读**路径专门加载 en_us；其他语言没有这条路。
因此中文等语言的补充翻译放在 `resources/crossversion/lang/<code>.json`，
由 `CrossVersionLang.inject()` 在 `ClientLanguageMap` 构建时以「只补不覆盖」方式合入。
官方非 en_us 语言文件不在 client.jar 里，需按 asset index 从
`resources.download.minecraft.net` 下载（工具：`tools/crossversion/ExtractLang.java`）。

**战利品表格式不兼容，不能直接复制。** 这是与模型资源相反的情况，有三处差异：

| | 1.16.4 | 1.21.11 |
|---|---|---|
| 目录名 | `data/minecraft/loot_tables/` | `data/minecraft/loot_table/`（单数） |
| `match_tool` 谓词 | `predicate.enchantments[].enchantment` | `predicate.predicates["minecraft:enchantments"][].enchantments` |
| 额外字段 | 无 | `random_sequence`、`bonus_rolls` |

所以掉落物需要按 1.16.4 的模式生成而不是搬运。竖切用的模板是原版 `stone.json`
（精准采集掉自己、否则掉圆石），深板岩与它同构。

顺带暴露一个依赖问题：**新方块的掉落物本身也得注册**。深板岩掉落深板岩圆石，
只注册深板岩会导致挖了没掉落。批量铺开时要按战利品表做依赖闭包检查。

### 9.3 下一步

| 阶段 | 状态 |
|---|---|
| 数据源与提取工具链 | 完成（清单、硬度、地图颜色、资源提取） |
| 竖切注册 + 资源 + 属性 | 完成，回归检查全绿 |
| 代码生成器批量铺开 533 + 406 项 | 待做 |
| Via 物品与方块透传 | 待做，**需要真实 1.21.11 服务器才能完整验证** |
| 挖掘体系 tag 化 | 待做 |
| 创造栏扩容（12 → 16 个分类） | 待做 |
| 附魔、药水效果、新属性 | 待做 |

注：竖切验证顺带说明了一件事 —— `deepslate` 用 `Material.ROCK`，因此 1.16.4 现有的
`PickaxeItem` fallback 逻辑（按 Material 判断）已经能正确处理它。tag 化主要影响
**需要特定挖掘等级的方块**（如深板岩矿石需要铁镐）与非 ROCK 材质但应当用镐挖的方块，
不是每个新方块都依赖它。

### 9.3 批量铺开的分批依据

533 个物品 = 179 个纯物品（工具、材料、食物等）+ 354 个方块物品。
406 个方块按「1.16.4 是否有对应方块类」分成三批（工具见
`docs/registry-diff/Split.java`，清单见 `gen-batch1-blocks.txt` /
`gen-needs-newclass-blocks.txt`）：

| 批次 | 方块数 | 说明 |
|---|---|---|
| 第一批 | **190** | 类型直接对应 1.16.4 现有类（`block` / `slab` / `stair` / `rotated_pillar` / `wall` / `fence` / `door` / `trapdoor` / `button` / `pressure_plate` / `drop_experience` / `leaves` …），纯生成 |
| 第二批 | **68** | 铜氧化家族。基类可复用（`weathering_copper_slab` 本质就是 `SlabBlock`），先按静态方块生成，氧化行为后补 |
| 第三批 | **148** | 真正需要手写新类 |

第三批的构成（这些不是生成能解决的）：

| 类型 | 数量 | 1.16.4 缺什么 |
|---|---|---|
| `candle` + `candle_cake` | 34 | 蜡烛点燃状态、多支堆叠、蛋糕插蜡烛 |
| `ceiling_hanging_sign` + `wall_hanging_sign` | 24 | 悬挂告示牌，1.20 的新方块实体 |
| `shelf` | 12 | 1.21.9 的架子 |
| `amethyst_cluster` / `budding_amethyst` / `amethyst` | 6 | 紫水晶生长 |
| `lightning_rod` / `weathering_lightning_rod` | 8 | 避雷针 |
| `copper_bulb` / `copper_chest` / `copper_golem_statue` | 12 | 1.21.9 铜制方块，含方块实体 |
| `cave_vines` / `big_dripleaf` / `sculk_*` / `brushable` 等零散 | 52 | 各自独立的机制 |

**为什么不能一次全塞进去**：如果对第三批也套用普通 `Block`，台阶会变成完整方块、
门打不开、蜡烛点不亮、可疑砂砾刷不出东西 —— 那是「加进去了但是错的」，
比暂时没有更难排查。所以按批推进，每批跑一次回归检查。

前两批合计 **258 个方块 + 179 个纯物品 = 437 个物品**（占 533 的 82%），
是可以生成并验证的部分。

### 9.4 修复记录

**进入旧存档崩溃（Exception ticking world）** —— 与注册表扩展无关，是既有 bug。

```
IllegalArgumentException: bound must be positive
  at java.util.Random.nextInt
  at WorldEntitySpawner.getRandomHeight(WorldEntitySpawner.java:356)
```

`getRandomHeight` 里 `nextInt(k + 1)` 假设高度图不会返回小于 -1 的值。但跨版本连接
1.18+ 服务器时世界底部到 y=-64，高度图可能出现负值，`k + 1 <= 0` 就抛异常，
表现为进入旧存档后崩服、必须新建世界。修法是夹住上界
`nextInt(Math.max(k + 1, 1))`：空列退化成在 y=0 尝试生成，后续的可生成性检查会过滤掉。

**中文名不生效** —— 这个是本次改动引入的。`CrossVersionLang` 原本用 `putIfAbsent`
「只补不覆盖」，但语言列表是 `[en_us, 当前语言]` 按顺序叠加、后者优先级更高，
而 en_us 的键此时已经在表里（走 `LanguageMap` 的 classpath 直读通道），
于是中文值被英文挡住。改成 `put` 覆盖即可 —— 该文件只含跨版本新增物品的键，
不会碰原版翻译。

### 9.5 重锤已完成与未完成的部分

已完成：攻击力 5.0、攻速 -3.4、耐久 500、附魔能力 15、稀有度 epic、旋风棒修复、
砸落伤害分段（前 3 格每格 4、之后 5 格每格 2、再往上每格 1，已单测）、
砸落攻击替代普通暴击而非叠加、命中后免疫本次摔落伤害。

未完成（属阶段 3）：`wind_burst` / `density` / `breach` 三个重锤专用附魔、
砸落时的范围击退波与粒子、专用音效（1.16.4 没有 `item.mace.smash_ground`，
当前用暴击音效近似）。多人游戏下这些由服务器计算，客户端缺失只影响预测与表现；
单人游戏会直接缺这些效果。

### 9.6 第一批已完成（203 方块 / 271 物品）

生成器跑通并落地。规模变化：

| | 原版 1.16.4 | 现在 | 目标 1.21.11 |
|---|---|---|---|
| 物品 | 976 | **1247** | 1505 |
| 方块 | 763 | **966** | 1166 |
| 方块状态 | 17112 | **24000**（15 bit） | 29671 |

产出：203 个方块、271 个物品（203 方块物品 + 67 纯物品 + 手写铜镐）、
1110 个资源文件、201 张战利品表、269 个英文键 + 274 个中文键。
回归检查全绿，原版 ID 零移位。

**过程中修掉的两个数据陷阱：**

1. **官方 tag 必须递归展开。** `mineable/axe` 里写的是 `#minecraft:planks` 这类 tag
   引用，不展开会漏判 —— `cherry_planks` 只通过 `#minecraft:planks` 出现在 axe tag 里，
   于是被推断成 `Material.ROCK`（石头材质的木板）。展开后 pickaxe 从 0 涨到 482、
   axe 到 286，材质推断才正确。

2. **战利品表引用未注册物品会抛异常。** `JSONUtils.getItem` 用的是 `orElseThrow`，
   引用一个没注册的物品会让整张表加载失败，挖那个方块就出错。樱花树叶掉樱花树苗、
   杜鹃树叶掉杜鹃花丛，而树苗与花丛的方块类型需要专门类、属于第三批 —— 所以转换器
   加了已注册物品白名单，把这类条目整条剔除（本批剔除 4 条）并列出清单。
   注意潜影盒的 `minecraft:contents` 不是物品标识符而是 dynamic 条目名，不能一起剔掉。

**待补：** 生成器目前跳过需要额外构造参数的类型（`flower_pot` 需要内容物、
`standing_sign` 需要 WoodType、`pressure_plate` 需要 Sensitivity、`button`、
`sapling` 需要 Tree），以及第三批需要手写新类的 148 个方块。
铜氧化家族已按静态方块生成，氧化行为尚未实现。

### 9.7 第一批的问题修复（实测反馈驱动）

实机测试暴露了六个问题，其中两个是系统性的。修复后规模：
物品 **1230**、方块 **949**、方块状态 **23926**（15 bit）。

**系统性问题一：状态属性必须与 1.16.4 方块类完全一致。**
官方 blockstate json 为每个状态组合指定模型。`waxed_copper_bulb` 有
`lit=false,powered=true` 这样的变体，而我用普通 `Block` 生成 —— 方块提供不了这两个
属性，模型匹配失败就渲染成紫黑块。同理铜格栅（官方多 `waterlogged`）、
1.19+ 的树叶（官方多 `waterlogged`）。

现在生成器用 `DumpProps` 从运行时注册表导出的属性集做**强校验**，不一致就不生成。
第一批因此从 203 降到 186 —— 少了 17 个，但少的那些本来就是坏的。

**系统性问题二：镂空方块必须显式 `notSolid()`。**
1.16.4 的渲染器默认把方块当完整立方体、剔除相邻面，门/活板门/栏杆/锁链/灯笼/树叶
不声明就会**从缝隙看穿地形**。原版这些方块全都显式调了 `notSolid()`，
生成器漏了。活板门还要补 `setAllowsSpawn(...)`（原版 `Blocks::neverAllowSpawn`
是私有方法，跨类引用不了，改用等价 lambda）。

**其余四个：**

| 问题 | 根因 | 修法 |
|---|---|---|
| 铜门、铜活板门打不开 | `DoorBlock.onBlockActivated` 与 `TrapDoorBlock` 检查 `material == Material.IRON` 就直接 PASS（铁门只能红石开）。生成器因名字含 copper 判成 IRON | 门类退到 `Material.ROCK`：一样能用镐挖（`PickaxeItem` 对 ROCK/IRON 同等对待），但不会被当铁门 |
| 新墙、新栅栏之间不连接 | `WallBlock` 靠 `isIn(BlockTags.WALLS)`、`FenceBlock` 靠 `FENCES`/`WOODEN_FENCES` 判断同类，新方块没进 tag | 新增 `MergeBlockTags.java`，并入 18 个结构性 tag、98 条。**必须过滤未注册方块** —— `TagCollectionReader` 遇到缺失引用会丢弃整张 tag，一个坏条目能让原版的墙也不连 |
| 创造栏出现两个锁链 | 1.21.9 把 `chain` 改名成 `iron_chain`，我当新方块注册了 | 生成器排除，`ModernRegistry.normalize` 加改名映射 |
| 矿石归到红石分类 | 生成器把含 `ore` 的归 REDSTONE | 原版 `IRON_ORE`/`COAL_ORE` 都在 BUILDING_BLOCKS，改回；栏杆与锁链归 DECORATIONS |

**还有一个隐蔽的正则 bug**：提取资源清单时用了 `register\("[a-z_]+"`，
**不匹配数字**，于是 `disc_fragment_5` 这类含数字的标识符资源没被提取、显示成紫黑块。
改成 `[a-z0-9_]+`。

**已知可接受的降级**：`recovery_compass` 在 1.21 用 32 张材质做 `range_dispatch`
（指向死亡点旋转），1.16.4 表达不了，现在是静态图标。

### 9.8 「数据包出现错误，世界无法加载」——tag 引用前缀

上一轮的 tag 合并引入了一个致命 bug，症状是进世界时弹出
「当前选中的数据包中出现了错误，导致世界无法加载」。日志给出了确切原因：

```
Couldn't load block tag minecraft:slabs as it is missing following references:
  minecraft:#wooden_slabs (from Default)
Failed to load datapacks, can't proceed with server load
```

注意 `minecraft:#wooden_slabs` —— 前缀顺序错了。**tag 引用与方块 id 的前缀位置不同**：

| | 写法 |
|---|---|
| 方块 id | `minecraft:iron_door` |
| 对另一个 tag 的引用 | `#minecraft:wooden_doors`（`#` 在命名空间**之前**） |

`MergeBlockTags` 归一化时用 `replace("minecraft:", "")` 把 `#minecraft:wooden_slabs`
变成 `#wooden_slabs`，写回时又无条件拼 `"minecraft:" + v`，就成了 `minecraft:#wooden_slabs`。
1.16.4 解析不了这个引用，于是丢弃整张 `slabs` tag，数据包加载随之失败。

修法是让归一化与其逆操作都识别 `#`。同时把合并的 tag 范围**收窄**到真正影响表现的那些
（墙与栅栏连接、门与活板门同类判断、台阶楼梯合并），
不再动 `guarded_by_piglins`、`dragon_immune` 这类与新方块无关的杂项 tag。

### 9.9 补上数据包完整性检查

这个 bug 本该被自动发现，而不是靠实机测试。它有两个特点让它特别容易漏：
编译期看不出来（纯数据文件），而且 1.16.4 对两类数据错误的处理方式完全不同 ——

| | 出错时的行为 | 表现 |
|---|---|---|
| tag 引用缺失或前缀错 | 记 error 并**丢弃整张 tag** | 数据包加载失败，世界打不开 |
| 战利品表引用未注册物品 | `JSONUtils.getItem` 抛异常 | 挖那个方块时报错 |

新增 `tools/crossversion/DataPackCheck.java`，并接入回归脚本，四项检查：
JSON 语法（2968 个文件）、tag 引用可解析（36 个引用）、tag 里的方块已注册、
战利品表引用的物品已注册（887 张表）。

检查本身也验证过有效性：故意注入 `minecraft:#wooden_slabs` 后它精确报出
「前缀顺序错误 slabs.json」并以非零码退出，不是永远返回 OK 的空壳。

### 9.10 创造栏分组改为数据驱动，樱花树叶用含水子类救回

**分组之前是靠名字猜的，错得很广。** 原版的门、活板门、栅栏门在 REDSTONE
（它们是红石元件），栅栏与墙在 DECORATIONS，而按名字猜会全部落进 BUILDING_BLOCKS。

改成从原版 `Items.java` 读实际分组、按 `blocks.json` 的 type 聚合取众数，
学到 145 种方块类型的归属。效果：

```
cherry_door -> REDSTONE      bamboo_trapdoor -> REDSTONE
bamboo_fence_gate -> REDSTONE   bamboo_fence -> DECORATIONS
tuff_wall -> DECORATIONS     tuff_slab -> BUILDING_BLOCKS
copper_ore -> BUILDING_BLOCKS
```

**关于 `waterlogged`：1.16.4 是有含水机制的**（`IWaterLoggable`，而且项目已为它加了
「连 1.12.2 及更老时禁用」的跨版本 gate；`SlabBlock`、`StairsBlock`、`WallBlock`、
`TrapDoorBlock`、`PaneBlock`、`ChainBlock`、`LanternBlock` 全都带这个属性）。
之前把树叶与铜格栅整块跳过、理由写成「1.16.4 表达不了 waterlogged」是**错的**——
真正的情况是**特定的类**没有这个属性：`LeavesBlock` 是 1.19 才加的，
普通 `Block` 本来就没有。

所以正确解法不是裁剪官方 blockstate 变体，而是补两个子类：
`ModernLeavesBlock`（树叶 + 含水）与 `ModernWaterloggedBlock`（整块 + 含水，铜格栅）。
这样渲染正确，行为也跟高版本一致（树叶能真的被水淹），裁剪路径最终一条都没用上。

方块数 186 → **198**（樱花/苍白橡/杜鹃 4 种树叶 + 8 个铜格栅）。
当前总量：物品 **1242**、方块 **961**、方块状态 **24054**（15 bit）。

### 9.11 认知修正：属性不匹配为什么要处理

前面几节把「属性不匹配」的后果一律写成「渲染成紫黑块」，这个说法**不准确**，
差点让我做出错误取舍。实测 blockstate json 后：

```
cherry_leaves.json  → {"variants": {"": {"model": "block/cherry_leaves"}}}
oak_leaves.json     → {"variants": {"": {"model": "block/oak_leaves"}}}
copper_grate.json   → {"variants": {"": {"model": "block/copper_grate"}}}
tuff_slab.json      → {"variants": {"type=bottom": …, "type=double": …, "type=top": …}}
```

**variant key 是条件子集，不是状态全描述。** 树叶与铜格栅的 key 是空串
（所有状态共用一个模型），台阶只按 `type` 区分、不提 `waterlogged`
（含水不改变外观），栅栏门不提 `powered`。匹配规则是「key 的每个条件都被状态满足」，
未列出的属性一概忽略。

所以属性不匹配的真正后果分两种：

| 情况 | 后果 |
|---|---|
| blockstate **用到**该属性（铜灯的 `lit`/`powered` 各有模型） | 那些状态匹配不到模型 → **紫黑块** |
| blockstate **没用到**该属性（树叶、铜格栅的 `waterlogged`） | 渲染正常，但**状态数与官方不一致** |

第二种才是树叶与铜格栅的情况。补 `waterlogged` 的真正理由是**状态数**：

| 方块 | 官方状态数 | 补属性后 | 用原版类 |
|---|---|---|---|
| `cherry_leaves` | 28 | 28 ✓ | 14 ✗ |
| `copper_grate` | 2 | 2 ✓ | 1 ✗ |

跨版本透传要建立「目标版本状态 → 本地状态」的对应，状态数不一致就无法一一对上 ——
服务器发来含水的树叶，客户端表达不出来。所以这是为阶段 2 铺路，
顺带让行为也跟高版本一致（树叶能真的被水淹）。

**检查工具自己也有过同样的错。** 新加的「紫黑块检查」第一版要求 variant key 与状态
完整相等，一次报出 85 个「不匹配」—— 全是误报。改成子集匹配后归零。
两次注入验证确认它不是空壳：删掉 `tuff_slab` 的 `type=top` 变体后，
它精确报出「状态 [type=top,waterlogged=true] 无对应模型变体」。

教训：**校验规则本身要拿真实数据验证**，否则它会用一个错误的标准去否决正确的实现。

### 9.12 生成器不直接改源码，改完必须手工同步

`GenerateItems` / `GenerateBlocks` **只写到 `target/crossversion-check/gen-*.java.txt`**，
不会覆盖 `ModernItems.java` / `ModernBlocks.java`。改了生成器后必须手工把字段区同步过去，
否则「改了生成器、重跑、结果没变」会让人误以为修复无效 —— 这个坑已经踩过一次。

同步时**必须按 `// === 生成字段结束 ===` 标记切分**，不要按「最后一个字段行」切：
标记之后是手工维护区（`COPPER_PICKAXE` 用了 `ItemTier.COPPER`，生成器不产出它）。
按字段行切会把手工区连带覆盖掉。

正确做法：

```bash
F=src/main/java/net/minecraft/item/ModernItems.java
GEN_END=$(grep -n "生成字段结束" "$F" | cut -d: -f1)
{ sed -n '1,30p' "$F"; cat target/crossversion-check/gen-items.java.txt; \
  sed -n "$GEN_END,\$p" "$F"; } > /tmp/mi.new
# 同步前后字段数必须一致（当前 266），否则说明吃掉了手工区
grep -c '^    public static final Item ' /tmp/mi.new
cp /tmp/mi.new "$F"
```

**在 Git Bash 里跑生成器要用 `;` 分隔 classpath，并且路径要过 `cygpath -w`**，
否则 `java` 报 `ClassNotFoundException` 而 class 文件其实已经生成好了：

```bash
GSON=$(cygpath -w ~/.m2/repository/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar)
OUT=$(cygpath -w target/crossversion-gen)
javac -encoding UTF-8 -cp "$GSON" -d target/crossversion-gen tools/crossversion/GenerateItems.java
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.GenerateItems .
```

另外别把中间产物放 `/tmp`：Git Bash 的 `/tmp` 会被清理，跑到一半目录就没了。用 `target/`。

### 9.13 氧化铜家族的类型名要剥前缀，且下划线不统一

未打蜡的铜门类型是 `weathering_copper_door`，1.16.4 没有同类可参照，
按类型查创造栏分组会落空并 fallback 到 `BUILDING_BLOCKS`；而打过蜡的铜门类型就是普通
`door`，正常分到 `REDSTONE`。结果**同一种门被分到两个类别** —— 这是这个 bug 的指纹。

修法是剥掉氧化前缀按底层形态再查一次，但官方类型名的下划线**不统一**：

| 方块 | 官方 type | 剥前缀后 | 原版同类 type |
|---|---|---|---|
| `copper_door` | `weathering_copper_door` | `door` | `door` ✓ |
| `copper_trapdoor` | `weathering_copper_trap_door` | `trap_door` | `trapdoor` ✗ |

所以剥完还要去掉下划线再试一次，否则活板门修不好。见 `GenerateItems.stripWeathering`。

**顺带说明为什么不用 MCP-Reborn 的 `CreativeModeTabs`**：它是 1.21 的分类，
把门归到「功能方块」，而 1.16.4 原版门在红石。目标是与本客户端的原版体验一致，
所以继续用「按类型跟原版同类方块投票」的方案，只修 fallback。

### 9.14 恢复工作时从这里接手

上下文被压缩后，按这个顺序读就能继续，不必重新摸索：

1. **本节** —— 当前数字、已验证项、已知缺口
2. **§9.12 / §9.13** —— 生成器的两个坑：改了不同步等于白改；Git Bash 下 classpath 要用 `;`
3. **§9.11** —— 属性校验的认知修正，别再拿「防紫黑」当跳过方块的理由
4. 跑 `bash tools/crossversion/run-registry-check.sh`，确认基线仍全绿

**当前状态**（截至本次）：

| 项 | 数值 |
|---|---|
| 物品 | 1295（原版 976 + 扩展 319） |
| 方块 | 1032（原版 763 + 扩展 269） |
| 未覆盖 | 物品约 215 项、方块 137 项 |
| 回归 | 注册表 ID 不移位、紫黑块检查、数据包检查 全绿 |

本轮新增 71 个方块，分四批收割：

| 批次 | 数量 | 做法 |
|---|---|---|
| 楼梯 base 推断 | 3 | 木质楼梯的 base 是 `<木>_planks` 而非 `<木>`，补进 `inferBase` 的候选 |
| 按钮 + 压力板 | 8 | 全是木质，映射到 `WoodButtonBlock` / `PressurePlateBlock`；后者构造需要 `Sensitivity`，新增 `EXTRA_FIRST_ARG` 机制 |
| 无属性方块 | 26 | 紫水晶、苔藓、泥、沙化土、染色玻璃、各类植物等，映射到 `Block` / `GlassBlock` / `BushBlock` / `ModernWaterloggedBlock` / `RedstoneOreBlock` |
| 蜡烛 + 蜡烛蛋糕 | 34 | 新写 `ModernCandleBlock`（`candles`/`lit`/`waterlogged`）与 `ModernCandleCakeBlock`（`lit`） |

蜡烛这批有两个要点：

1. **属性名必须是 `candles`**。1.16.4 的 `SeaPickleBlock` 有现成的「数量 1-4 + 含水」形状，
   但它的属性叫 `pickles`，对不上官方 blockstate 的 variant key，所以自建
   `IntegerProperty.create("candles", 1, 4)`。
2. **亮度必须动态**。官方是 `3 * candles`（取自 `CandleBlock.LIGHT_EMISSION`），
   而提取到的官方数据每个方块只有一个静态亮度值，照抄会让熄灭的蜡烛也发光。
   为此加了 `DYNAMIC_LIGHT` 映射，生成 `.setLightLevel(ModernCandleBlock.lightFromCandles())`。

移植时踩到两个 1.16.4 缺失的 API，已换成等价物并在代码里注明：
`BlockItemUseContext.isPlacerSneaking()` 不存在（改从 `getPlayer().isSneaking()` 取）、
`SoundEvents.BLOCK_CANDLE_EXTINGUISH` 是 1.17 才有的（改用 `BLOCK_FIRE_EXTINGUISH`）。

**已由用户实机验证通过**：贴图正常、深板岩掉圆石、铜矿掉粗铜、台阶双层掉 2 个、
中文名生效、可进世界不崩、墙与栅栏互连、樱花树叶在、铜门与铜活板门在红石分类。

**已知缺口**（按用户反馈的优先级）：

- 服务器场景完全没变化 —— 需要 Via 透传（主线 B）
- 重锤效果不完善 —— 只有属性，没有砸落伤害曲线（主线 C）
- 新物品缺专门行为类：望远镜、收纳袋、风弹等仍是普通 `Item`

**三条候选主线**：

| 主线 | 内容 | 说明 |
|---|---|---|
| **A. 补齐剩余项** | 蜡烛(17)+蜡烛蛋糕(17)、悬挂告示牌(24)、架子(12)、铜灯/铜箱/铜傀儡雕像、7 种长矛、整套铜工具与盔甲 | 需写专门类，照 MCP-Reborn（§6 表格） |
| **B. Via 透传** | 让服务器发来的深板岩就是深板岩。要打包「1.21.11 blockstate ID → 本地 ID」映射表，并改造 `ExtendedBlockStateMapper` 的 `fallback → STONE` | **用户的最终目标**；但依赖 A 的覆盖度，方块不铺满就无处可透 |
| **C. 行为精修** | 重锤砸落伤害、长矛、铜工具挖掘等级、重锤三附魔、8 个新药水效果 | 可穿插进行 |

用户已明确**多人场景优先**，所以 B 是终点；但 B 需要 A 先铺开，
建议顺序 **A → B**，C 穿插。

### 9.15 中文翻译改为从 MCP-Reborn 自动生成

之前 `crossversion/lang/zh_cn.json` 是手工维护的，新增一批方块后中文跟不上，
表现为「一部分有中文、新加的还是英文」。

MCP-Reborn 解出了官方全套语言文件（8406 条），
`ExtractAssets.mergeChineseLang` 现在从它按当前注册集合筛选并**整体重写**该文件。
与 en_us 的「只追加」策略不同 —— 那个文件有 29 万字符且含原版内容必须保护，
而中文文件完全由工具产出，重写能保证它始终与注册集合一致。

```
F:\HCMLNew\MCP-Reborn-release\extracted-assets\assets\minecraft\lang\zh_cn.json
```

**这个目录还有全套材质 PNG**，将来 client.jar 提取不到的资源可以从这里找。

### 9.16 「亮度不对」要先分清是 bug 还是原版行为

用户报告紫水晶块、蜡烛、深层红石矿都「不亮」。查官方 `light` 字段后，三者性质完全不同：

| 现象 | 官方 light | 判定 |
|---|---|---|
| 紫水晶块不发光 | 0 | **正确**，原版就不发光 |
| 单支蜡烛很暗 | `3 × candles` = 3 | **正确**，亮度 3 本来就暗 |
| 深层红石矿点亮不发光 | 默认 0、`lit` 时 9 | **真 bug** |

第三个是生成器的缺陷：**提取到的是默认状态的亮度**，而这类方块的亮度依赖状态。
1.16.4 原版 `REDSTONE_ORE` 写的是 `.setLightLevel(getLightValueLit(9))`，
照抄静态值 0 就让点亮的矿石不发光了。

已加进 `DYNAMIC_LIGHT` 映射（连同蜡烛的 `3 * candles`）。
**判断标准：方块有 `lit` / `powered` 之类的状态属性且原版对应物用动态亮度时，
静态值必然是错的。** 后续补铜灯（`lit`/`powered`）时会再遇到同一个坑。

### 9.17 蜡烛插蛋糕要在 CakeBlock 里拦截

官方在 `CakeBlock` 判断手持物是蜡烛就换成对应的蜡烛蛋糕，用一张
`BY_CANDLE` 映射反查（17 种颜色各一种蛋糕）。本项目照同样结构做：
`ModernCandleCakeBlock` 在构造时把自己登记进映射，`CakeBlock.onBlockActivated`
在**吃蛋糕之前**拦截 —— 顺序反了蛋糕会先被吃掉一口，就插不上了。

两个 1.17 才有的 API 已换等价物并在代码注明：
`SoundEvents.BLOCK_CAKE_ADD_CANDLE`（改用 `BLOCK_WOOL_PLACE`）、
`BLOCK_CANDLE_EXTINGUISH`（改用 `BLOCK_FIRE_EXTINGUISH`）。

### 9.18 每次新增方块后必须重跑的三件事

新增一批方块不是「改生成器 + 同步字段区」就完了。这几步漏掉都不会在编译期暴露：

| 步骤 | 漏掉的后果 |
|---|---|
| `ExtractAssets` | 新方块渲染成紫黑块；中文名缺失 |
| `ConvertLootTables` | **挖掉什么都不掉落** |
| `MergeBlockTags` | 墙、栅栏之间不连接 |
| `run-registry-check.sh` | —— |

战利品表这条已经踩过：`candle`、`amethyst_block`、`cherry_stairs` 等 71 个方块
一直没有战利品表，破坏后什么都不掉，看起来像「打不掉」。
`ConvertLootTables` 要传的是 **server.jar 里的内嵌 jar**
（`META-INF/versions/1.21.11/server-1.21.11.jar`），战利品表在那里面，
外层 jar 里没有。另外 1.21 的目录名是 `loot_table/`（单数），1.16.4 是 `loot_tables/`（复数）。

**已注册清单改为自动导出。** `reg-blocks.txt` / `registered-items.txt` 原先是手工快照，
新增方块后没更新，数据包检查就拿着过期清单把已注册的物品报成未注册 ——
一次 68 条假告警。现在 `RegistryCheck.exportRegisteredLists()` 每次从活的注册表导出。

### 9.19 行为缺失往往在「模型之外」

蜡烛点亮后顶上没有火，不是模型或 blockstate 的问题 —— 官方的火焰是
`AbstractCandleBlock.animateTick` 产生的**粒子**，不在模型里。烛芯坐标取自官方
`CandleBlock.PARTICLE_OFFSETS`（1-4 支各有一套）。

判断方法：**在 MCP-Reborn 里搜 `animateTick`**，有实现就说明该方块有粒子/音效层面的
表现，只补资源是不够的。

这类移植的常见障碍是 1.17+ 才有的 API，已换等价物并在代码注明：

| 1.21 的 API | 1.16.4 替代 |
|---|---|
| `ParticleTypes.SMALL_FLAME` | `ParticleTypes.FLAME`（略大） |
| `SoundEvents.BLOCK_CANDLE_EXTINGUISH` | `BLOCK_FIRE_EXTINGUISH` |
| `SoundEvents.BLOCK_CAKE_ADD_CANDLE` | `BLOCK_WOOL_PLACE` |
| `BlockItemUseContext.isPlacerSneaking()` | `getPlayer().isSneaking()` |
| `@OnlyIn(Dist.CLIENT)` | 本项目不是 Forge，直接去掉 |

### 9.20 创造栏分类的 fallback 规则要用精确匹配

按方块类型跟原版同类投票是主路径，但原版没有同类时会落到名字规则，
那里用 `contains` 很容易误伤：

| 方块 | 错误分类 | 原因 |
|---|---|---|
| `sculk` 幽匿块 | REDSTONE | `contains("sculk")` 本意抓幽匿感测体，误伤了幽匿块本身 |
| `moss_block` 苔藓块 | DECORATIONS | `contains("moss")` 本意抓苔藓地毯 |

改成精确匹配（`equals("sculk_sensor")`、`endsWith("_carpet")`）后归位到 BUILDING_BLOCKS。

**排查方法**：把 REDSTONE / DECORATIONS 这类「特殊」分类里的扩展方块全列出来扫一遍，
错位项在同类中会非常显眼 —— 红石分类里除了门/活板门/按钮/压力板/栅栏门之外的任何东西
都值得怀疑。

---

## 10. 附：调研产物

| 文件 | 说明 |
|---|---|
| `docs/registry-diff/added-items-1.16.4-to-1.21.11.txt` | 533 个新增物品标识符（按 1.21.11 注册顺序） |
| `docs/registry-diff/added-blocks-1.16.4-to-1.21.11.txt` | 406 个新增方块标识符 |
| `docs/registry-diff/added-entities-1.16.4-to-1.21.11.txt` | 51 个新增实体标识符（供生物立项时使用） |
| `docs/registry-diff/official-items-1.21.11.txt` | 官方 1505 个物品，按 protocol_id 排序 |
| `docs/registry-diff/official-blocks-1.21.11.txt` | 官方 1166 个方块，按 protocol_id 排序 |
| `docs/registry-diff/block-hardness-1.21.11.csv` | **1166 个方块的硬度与爆炸抗性**（提取自官方 jar） |
| `docs/registry-diff/renamed-or-removed-items.txt` | 4 项重命名物品 |
| `docs/registry-diff/renamed-or-removed-blocks.txt` | 3 项重命名方块 |
| `docs/registry-diff/Diff.java` | 从 Via 映射数据导出任意版本注册表并求差集 |
| `docs/registry-diff/Chain.java` | 跑完整 ViaBackwards 降级链，验证物品实际降级结果 |
| `docs/registry-diff/Ent.java` | 导出实体差集 |
| `docs/registry-diff/Verify.java` | **交叉验证**官方 registries.json 与 Via 映射数据是否一致 |
| `docs/registry-diff/ExtractHardness.java` | 从官方混淆 jar + mappings 提取方块硬度 |

`1.21.11/`（已加入 `.gitignore`）存放官方 jar 与 `--reports` 产物
`registries.json` / `items.json` / `blocks.json`，可随时用 server.jar 重新生成。

### 长期维护的工具（`tools/crossversion/`）

| 文件 | 说明 |
|---|---|
| `run-registry-check.sh` | **回归检查入口**。改动 Items/Blocks/ModernItems/ModernBlocks 后必跑 |
| `RegistryCheck.java` | 断言原版 ID 未移位、扩展 ID 排在原版之后、方块状态位宽安全、硬度与工具参数符合官方值 |
| `GenerateBlocks.java` | 生成方块注册代码。含状态属性强校验、Material 反推、音效映射 |
| `GenerateItems.java` | 生成物品注册代码。方块物品 / 纯物品 / 需专门类三分 |
| `ExtractAssets.java` | 从 client.jar 递归提取模型/材质/blockstate 并合并 lang，不覆盖原版资源 |
| `ExtractLang.java` | 从官方语言文件挑出新增物品的翻译（非 en_us 需先按 asset index 下载） |
| `ConvertLootTables.java` | 战利品表格式转换 + 过滤引用未注册物品的条目 |
| `MergeBlockTags.java` | 并入结构性方块 tag（墙/栅栏/门的互相识别），过滤未注册方块 |
| `DataPackCheck.java` | **数据包完整性检查**，已接入回归脚本：JSON 语法、tag 引用可解析与前缀正确、tag 与战利品表引用的对象已注册 |

**每次改动后的验证顺序**：生成 → 编译 → 提取资源 → 转换战利品表 → 合并 tag →
跑 `run-registry-check.sh` → 复查依赖闭包（战利品表与 tag 引用的对象必须都已注册，
否则前者抛异常、后者静默丢弃整张表）。

Via 相关工具的编译运行方式（`VIA` / `VB` 指向本地 m2 里的 Via jar）：

```bash
javac -encoding UTF-8 -cp "$VIA:$VB" -d . Diff.java && java -cp "$VIA:$VB:." Diff
```
