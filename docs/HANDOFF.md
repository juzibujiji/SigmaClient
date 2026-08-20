# 新对话交接说明

> 在新对话里，把下面这段话发给我就行（可直接复制）：
>
> ```
> 继续 SigmaClient 的 1.16.4→1.21.11 跨版本物品移植。
> 先读 docs/HANDOFF.md，再读 docs/cross-version-registry-plan.md 的 §9.14 起。
> 要求：所有实现都对照 F:\HCMLNew\MCP-Reborn-release 的官方源码写，不要靠名字猜规则。
> 目标是新东西在我客户端里的行为跟高版本一样（本地预测和单机手感都要对），
> 不只是能拿在手里。派多个 subagent 并行做，别一个个串着写。
> ```

---

## 一、当前状态

| 项 | 数值 |
|---|---|
| 物品 | 1443（原版 976 + 扩展 467） |
| 方块 | 1091（原版 763 + 扩展 328） |
| 附魔 | 42（原版 38 + 扩展 4：致密 38 / 破甲 39 / 风爆 40 / 突进 41） |
| 方块状态总数 | 26497 / 32768（**余量只剩约 6270**，见红线第 2 条） |
| 未覆盖 | 方块 78 个；物品 66 个，其中 59 个只是在等方块，纯物品只剩 3 个桶 |
| 编译 | 通过 |
| 回归 | 注册表 ID 不移位、紫黑块检查、tag 引用、战利品表、数据包 全绿 |

跑一次回归确认基线：

```bash
MVN=/f/HCMLNew/tools/apache-maven-3.9.9/bin/mvn bash tools/crossversion/run-registry-check.sh
```

**`mvn` 不在 PATH 上**，在 `/f/HCMLNew/tools/apache-maven-3.9.9/bin/mvn`。回归脚本里有兜底路径，
但手工敲 `mvn -o -q compile` 会直接 `command not found`，白等一轮。

### 本轮（行为修复轮）做完的事 —— 下一轮直接开始 Via 透传

用户实机反馈驱动，全部已修复并编译 + 回归通过。**用户已确认：这些做完就做 Via 透传**（第六节）。

| 症状 | 根因 | 修法 |
|---|---|---|
| 风弹看不见但会炸 | `ClientPlayNetHandler.handleSpawnObject` 是硬编码 if-else 链，末尾 `else { entity = null; }`，没有 `WIND_CHARGE` 分支 —— 客户端丢弃生成包，服务端实体照跑 | 照官方 1.17+ 改成通用兜底 `entitytype.create(world)` + 补速度。**顺带修好了同样隐形的运输船**，以后加现代实体不用再逐个补分支 |
| 长矛右键减速 | 官方长矛 `USE_EFFECTS = (canSprint=true, _, speedMultiplier=1.0F)`，1.16.4 对任何「使用中」的手一律降到 0.2 并禁疾跑 | `Item.getUseSpeedMultiplier` / `canSprintWhileUsing` 两个可覆写方法（默认值 = 官方 `UseEffects.DEFAULT`），长矛覆写。`ClientPlayerEntity` 三处门都改读它 |
| 长矛右键完全没伤害 | **突刺速度门槛读 `getMotion()`，而服务端玩家的 motion 从不更新**（服务端只把玩家传送到上报坐标），疾跑时读出来≈0 | 照官方 1.20.5 实现 `getKnownMovement()`：移动包前后坐标差 → `ServerPlayerEntity.lastKnownClientMovement` → `Entity.getKnownMovement()` |
| 长矛远距离打不到 | 服务端 `processUseEntity` 硬编码 `getDistanceSq < 36`（6 格），长矛创造模式能打 6.5 格，整包被丢 | 手持长矛时按 `effectiveMaxRange + 3.0` 放宽（沿用原版「攻击距离 + 3 格余量」的比例） |
| 长矛第一/第三人称都不对 | **官方每把长矛有两个模型**，`items/<id>.json` 按显示语境选，手持用 `_in_hand`（32×32 加长贴图 + 专用变换矩阵）；项目只移植了背包那个扁平模型 | 补 8 个模型 + 7 张贴图，照原版处理三叉戟的方式在 `ItemRenderer` 分流、`ModelBakery` 强制烘焙 |
| 长矛第三人称「胳膊动矛不动」 | 只实现了手臂骨骼姿态，缺两段**物品级**变换 | 补 `SpearAnimations.thirdPersonUseItem` / `thirdPersonAttackItem`，照官方 `ItemInHandLayer` 的顺序接进 `HeldItemLayer`；另修 `BipedModel` 游泳混合会把举起的矛插值拉回体侧（官方混合系数里有一项 `armPose != SPEAR`） |
| 重锤「一次下落只能砸一次」，配合 fly 很难受 | 官方 `postHurtEnemy` 会 `resetFallDistance()` | **刻意偏离官方**：不重置下落距离。但原先的坠落免伤是「靠」清零顺带实现的，所以拆出显式标记 `LivingEntity.setIgnoreFallDamageFromSmash`（对应官方 `setIgnoreFallDamageFromCurrentImpulse`），落地用一次即清 |
| 缺重锤/长矛的附魔 | —— | 四个附魔全部实现，见下 |

**遗留的已知限制**（都在代码注释里注明了）：

- 重锤：`PlayerEntity.travel` 在 `abilities.isFlying` 时**每 tick 清零 `fallDistance`**（原版行为）。
  如果 fly 模块在下落过程中一直保持飞行，砸落仍然攒不起来。真要解决得把判定从
  `fallDistance` 改成读实际下降速度 —— 用户实测后再决定
- 长矛：命中回弹（官方 `ticksSinceKineticHitFeedback`）1.16.4 没有对应同步状态，传 0 恒不生效；
  突刺没有客户端预测（`onUse` 只在服务端结算），命中有轻微延迟感
- 风爆按官方是**宝藏附魔**，附魔台里出不来。1.16.4 没有不祥之兆宝库，
  所以只能靠 `/enchant`、创造模式附魔书或铁砧。想让它进附魔台：
  `WindBurstEnchantment.isTreasureEnchantment()` → `false` **且** `canGenerateInLoot()` → `true`
  （1.16.4 两个门在 `EnchantmentHelper.getEnchantmentDatas` 里是 AND 关系）

---

## 二、用户明确否掉的方向，别再做

原来这份文档把「创造栏分类改用官方数据」列为第一优先。**用户当面否了**，原话是：

> 与其这些物品顺序我更关心物品使用和多人服务器表现，到现在还有一堆物品没有添加

所以优先级是：**补完缺的东西 → 物品行为与高版本一致 → 多人服务器透传**。
创造栏分类已经改成数据驱动（见下），不要再花时间在上面。

用户对「行为」的要求很具体，不是「能拿在手里」就算完：

> 我希望所有新添加的物品到我客户端的时候实现的效果是和高版本一样的，不影响本地预测和单机手感。
> 比如长矛我认为应该有突刺和动画，而且重锤还是感觉差点意思，还有收纳袋什么的等等一系列的东西都应该要一样。
> 也就是说新生物啊还有新武器新的一系列新东西机制都应该一样。

还有一条关于节奏的：

> 而且现在都有源码看里你加东西怎么还是这么慢

**结论：这种活要并行做。** 派多个 subagent，每个拿一族方块或一个行为系统，各自在独立
git worktree 里对着 MCP-Reborn 官方源码写，主线只负责合并与注册。串着一个个写太慢。

分工的关键约定（踩过坑）：
- 每个 agent **只拥有自己新建的类**；`ModernBlocks.java` / `ModernItems.java` /
  `tools/crossversion/` 由主线独占，agent 只在报告里给出「需要加哪些 `TYPE_TO_CLASS` 映射、
  构造签名是什么」
- 共享文件（`PlayerEntity`、`LivingEntity`、渲染类）允许改，但必须在报告里逐个列出
  改了哪个方法、为什么 —— 否则合并时无从判断
- 告诉它们**别一次性 Write 大文件**。有一个 agent 连续两次因为单次输出过大被 API 掐断，
  分成「先写骨架 → 逐个方法 Edit → 每两三个方法编译一次」才写进去
- 用 `SendMessage` 恢复失败的 agent 时，它可能**丢掉 worktree、直接改主目录**。
  恢复后先 `git status` 看一眼主目录被谁动了

---

## 三、创造栏分类：已改成数据驱动，别再加名字规则

`tools/crossversion/CreativeGroups.java` 现在的判定链，按顺序：

| 依据 | 做法 | 适用 |
|---|---|---|
| 氧化铜家族 | 全家 8 员一起判，取自官方两张映射表 | 铜制品 |
| 类型投票 | 按 `blocks.json` 的 `definition.type` 跟原版同类方块投票 | 方块物品 |
| **原版同后缀** | 拿后缀去 1.16.4 原版找同后缀物品取众数 | **纯物品**（新增） |
| **纯 tab 众数** | 官方 tab 里原版物品高度一致时直接用 | **1.21 全新的一类**（新增） |
| 邻居窗口 | 官方 tab 里前后最近的原版物品加权投票 | 兜底 |

后两条是这轮加的，修掉了三类实际错判：

- `field_masoned_banner_pattern` 判成 **BREWING（酿造）** —— 它在官方 ingredients 栏里
  紧挨着 `phantom_membrane`。改用原版 6 个 `_banner_pattern` 投票 → MISC
- **7 把长矛裂成两栏**：木/石/铜/铁判 COMBAT，金/钻石/下界合金判 TOOLS ——
  后三把在官方 combat 栏里恰好挨着 `wooden_axe`。改用 tab 众数 → 7 把全 COMBAT
- `wind_charge`、三种鹦鹉螺盔甲从 MISC 归位到 COMBAT；`tinted_glass` 归位到 BUILDING_BLOCKS

两个必须记住的量化事实（都写在代码注释里了）：

1. **「只看 tab 众数 68.45%」这个数字会骗人。** 那是 12 个 tab 一视同仁的平均值。
   跑 `java verify.TabPurity .` 分 tab 量：spawn 100%、functional 88.8%、food 86%、
   ingredients 82.6%、combat 72.2% 都可用；building 69.2%、redstone 58.5%、
   colored 54.4%、natural 50.9%、tools 39.7% 不能用。阈值 0.70 正好切开
2. **同后缀匹配必须限制覆盖度。** 后缀要覆盖标识符至少一半的段，否则
   `music_disc_creator_music_box` 会一路缩到 `_box`，撞上 17 个潜影盒（纯度还 100%），
   被判成装饰品

**试过并撤掉的做法**：按标识符最后一段给新物品分组、汇总同类邻居得分。听着合理，
实测引入 4 个新错 —— `raw_copper` 跟氧化铜块归成一类进了建筑材料、
`breeze_rod` 跟着 `lightning_rod` 进了装饰。**它本质还是在赌名字，别再试。**

改这个文件之前先跑 `java verify.EvalGroups .`（留一验证，用原版 959 个已知分组的物品当答案）。

---

## 三之二、并行铺开那一轮的产出与教训

一次派了 7 个 subagent，全部合并完成。这些是<b>只有实际做过才会知道</b>的坑，
下一轮开工前务必读完。

### 新发现的第四条红线：实体类型 raw ID 也不能移位

和物品 0-975、方块 0-762 完全同级。`SSpawnObjectPacket` 写的是
`Registry.ENTITY_TYPE.getId(type)` 的**裸 id**，而 `register(...)` 按声明顺序发号。
官方 Minecraft 按字母序插新类型，照抄那个顺序会让它后面每个类型的 id 都偏移，
连原版 1.16.4 服务器发来的实体都会生成成错误类型。

**已经踩过并修好**：`wind_charge` 一度被插在 `PLAYER` / `FISHING_BOBBER` 之前，
把这两个原版类型各挤后一位。现在 `EntityType.java` 末尾有一段醒目注释和专门的扩展区。

### 战利品表：没有物品的方块不能写「掉落自己」

`ConvertLootTables` 的兜底逻辑是「官方没这张表就写掉落自己」。墙上告示牌这类方块
**没有自己的物品**（靠 `Properties.lootFrom(地面变体)` 重定向），兜底给它们造出 16 张
引用未注册物品的表，破坏时会抛异常。数据包检查抓到了。

已修：`canDropSelf(id)` 会先确认同名物品在白名单里。**加新的「墙上/附着变体」方块时，
记得在生成器里给它配 `lootFrom`**（`GenerateBlocks.lootFromTarget`），漏了就是挖掉不掉东西。

### 音效资源打不进去（影响所有后续音效工作）

`VirtualAssetsPack.getInputStreamVanilla` **先查启动器的 ResourceIndex，再查 classpath**，
而真实的 1.16 asset index 里就有 `minecraft/sounds.json` —— 所以我们打进
`src/main/resources/assets/minecraft/sounds.json` 的内容**被遮蔽了**，
新注册的 `SoundEvent` 绑不到实际音频。

现在的做法是**懒解析**：`SoundHandler.getAccessor` 找得到就用官方名，找不到就退回最接近的
1.16.4 原版音效，将来资源包合并修好了会自动升级。
（`minecraft/lang/en_us.json` **不在** index 里，所以语言文件的追加是有效的。）

### 动态亮度要按 id，不只按类名

8 个铜灯共用一个类，但四个氧化阶段点亮时亮度**各不相同**（15/12/8/4）。
按类名的 `DYNAMIC_LIGHT` 表达不了，已加 `DYNAMIC_LIGHT_BY_ID`。

而且铜灯这种情况比蜡烛更糟：`block-props-1.21.11.csv` 提取的是默认状态
（`lit=false`）的亮度，8 个铜灯那一列**全是 0**，不配就是永远不发光。

### 原有代码里被挖出来的两个真 bug

都在重锤上，不是移植引入的：

1. `canSmash` 用 `!isOnGround()`，官方是 `!isFallFlying()` —— 鞋子滑翔时也能砸
2. 砸落伤害被当成暴击的**替代品**、还加在 1.5 倍**之后**。官方 `Player.attack` 的顺序是
   `f *= 冷却缩放; f += 砸落加成; if (暴击) f *= 1.5F;`，而 `canCriticalAttack`
   并没有排除砸落 —— 两者**叠加**，且暴击乘在「基础 + 砸落」上。
   修完 3 格非疾跑砸击从 18 变 27，这才是原版数值

改 `PlayerEntity.attackTargetEntityWithCurrentItem` 时**必须确认非重锤路径零影响**
（`smashBonus` 恒为 0），那个方法被 KillAura 等战斗模块依赖。

### 派 agent 的几条实操约定

- **文件归属要写死**。每个 agent 只拥有自己新建的类；`ModernBlocks` / `ModernItems` /
  `tools/crossversion/` 由主线独占，agent 在报告里给出「需要加哪些 `TYPE_TO_CLASS` 映射、
  构造签名是什么、需不需要 `DYNAMIC_LIGHT`」
- **共享文件允许改，但必须逐个报告改了哪个方法、为什么**。合并时 `git apply --check`
  逐文件试，冲突的手工合。这一轮 `WorldRenderer`（重锤 case 2013 + 避雷针 case 3002）、
  `EntityType`（风弹 + 运输船）、`BipedModel`（长矛突刺姿态 + 望远镜姿态）都撞过
- **告诉它们别一次性 Write 大文件**。有个 agent 连续两次因为单次输出过大被 API 掐断，
  改成「先写骨架 → 逐个方法 Edit → 每两三个方法编译一次」才写进去
- **用 `SendMessage` 恢复失败的 agent 会丢 worktree、直接改主目录**。这一轮长矛 agent
  就是这样，它的改动直接落在主目录，还顺手替别人补了缺的枚举常量。恢复后先 `git status`
  看主目录被谁动了
- **并发 7 个 agent 会把内存挤爆**，`javac` / `java` 起不来 JVM。加 `-J-Xmx256m` / `-Xmx384m`
- 生成器里加静态字段要注意**声明必须在 static 块之前**，Java 按文本顺序初始化。
  `CTOR_TEMPLATE` 声明放在后面，`putSignItems()` 直接拿到 null

### 这一轮各家做到了什么、明确没做到什么

| 内容 | 做到 | 没做到（1.16.4 缺基础设施） |
|---|---|---|
| 长矛 | 突刺、穿透、2.0-6.5 格攻击距离（含 2.0 下限）、STAB 挥击时长、第一/第三人称动画 | `DamageTypes.SPEAR`、命中回弹（状态字节 2 在 1.16.4 还是受伤动画）、`getKnownSpeed`（用 `getMotion` 近似）、生物 AI |
| 重锤 | 分段砸落曲线、击退波、冲击波粒子（世界事件 2013）、落地扬尘、免摔伤 | 密役/破甲/风爆三附魔（无附魔注册表与效果组件） |
| 收纳袋 | 装取、拖放插入、右键倒出、4×3 网格 tooltip、容量条、嵌套 1/16 计重 | 滚轮选物（要 1.21 的 `ServerboundSelectBundleItemPacket`）、`UseAction.BUNDLE` 姿态、开袋模型 |
| 望远镜 | FOV 0.1、瞄准镜叠加层、鼠标灵敏度 1/8、隐手、第三人称姿态 | —— |
| 风弹 | 完整投掷物实体、1.2 半径气流击退、触发按钮/拉杆/门（含铁门被排除的官方怪癖） | —— |
| 刷子 | 动作与动画、10 tick 一笔、粒子 | 挖不出东西（`suspicious_sand/gravel` 还没注册，没有 `BrushableBlock`） |
| 避雷针 | 朝向、含水、红石信号 15、雷击 powered + 世界事件 3002 客户端粒子 | 吸引闪电（要改天气逻辑找 128 格内最高避雷针） |
| 铜灯 | 红石**上升沿**翻转 `lit`（不是跟随电平）、四阶段各自亮度、比较器读数 | 氧化推进、打蜡刮蜡（要 `ModernBlocks` 里的映射表与物品侧接线） |
| 尖牙滴水石 | thickness 随邻居重算、生长、塌落砸伤、石笋尖刺 | 装水锅（1.16.4 没有 `AbstractCauldronBlock`）、泥变黏土 |
| 洞穴藤蔓 | 向下生长、浆果采摘、骨粉催熟、发光浆果动态亮度 14 | 无 |
| 发光地衣/幽匿脉络 | 六面附着、骨粉蔓延（完整移植 `MultifaceSpreader`） | 幽匿充能系统（要 `SculkSpreader` 与方块实体） |
| 幽匿催发体/尖啸体 | 状态属性、形状、tick 清状态 | 全部核心行为（要方块实体 + 振动系统 + 监守者） |
| 告示牌 | 32 个方块状态数与官方一致、悬挂牌新 TileEntity 与渲染器、能放能编辑 | 正反双面文本、上锁、荧光文本（`SignTileEntity` 只有一组 4 行） |
| 船 | 4 种新木船、竹筏**真模型**、10 种运输船（27 格箱子） | 运输船配方（拿不到官方 recipe JSON 核对） |

---



## 四、最重要的一条：对照官方源码写，别猜

这是用户明确提出的要求，也是前面大部分返工的根源。

`F:\HCMLNew\MCP-Reborn-release` 有 **1.21.11 的完整反编译源码 + 全套资源**：

| 内容 | 路径 |
|---|---|
| 官方 Java 源码 | `src/main/java/net/minecraft/` |
| 材质 / 模型 / blockstates | `extracted-assets/assets/minecraft/` |
| **全套语言文件（含 zh_cn）** | `extracted-assets/assets/minecraft/lang/` |

写任何方块或物品的实现前，**先读官方对应类**。已经因为「凭印象写」返工过的例子：

- 蜡烛点亮没有火 —— 官方火焰是 `animateTick` 的**粒子**，不在模型里
- 蜡烛蛋糕上的蜡烛没有碰撞 —— 官方 `SHAPE` 是蛋糕 + 蜡烛柱两段，只写了蛋糕
- 深层红石矿点亮不发光 —— 官方是动态亮度，照抄静态值 0
- 铜门打不开 —— `Material.IRON` 会被 `DoorBlock` 当成铁门

类名对照：官方是 `world/item/`、`world/level/block/`（Mojang 官方映射），
本项目是 `net/minecraft/item/`、`net/minecraft/block/`（1.16.4 MCP 命名）。
**属性名和方法名都要转换，不能直接抄。**

常撞到的「1.17+ 才有的 API」及替代：

| 官方 API | 1.16.4 替代 |
|---|---|
| `ParticleTypes.SMALL_FLAME` | `ParticleTypes.FLAME` |
| `SoundEvents.BLOCK_CANDLE_EXTINGUISH` | `BLOCK_FIRE_EXTINGUISH` |
| `SoundEvents.BLOCK_CAKE_ADD_CANDLE` | `BLOCK_WOOL_PLACE` |
| `BlockItemUseContext.isPlacerSneaking()` | `getPlayer().isSneaking()` |
| `@OnlyIn(Dist.CLIENT)` | 本项目不是 Forge，去掉 |
| `Block.column(...)` | `makeCuboidShape(...)` + `VoxelShapes.or(...)` |

---

## 五、第二优先：补完剩余 78 个方块

按类型分组（`target/crossversion-check/gen-blocks-skipped.txt` 是完整清单）：

| 类型 | 数量 | 官方参照类 | 备注 |
|---|---|---|---|
| `ceiling_hanging_sign` / `wall_hanging_sign` | 24 | `CeilingHangingSignBlock` 等 | 需要 TileEntity 与 WoodType |
| `shelf` | 12 | `ShelfBlock` | 1.21.9 新增 |
| `standing_sign` / `wall_sign` | 8 | `StandingSignBlock` | 同样要 TileEntity |
| `flower_pot` | 8 | `FlowerPotBlock` | 构造要内容方块，多数内容还没注册 |
| `copper_bulb` | 8 | `CopperBulbBlock` | 属性 `[lit, powered]`，**记得动态亮度** |
| `copper_chest` | 8 | `CopperChestBlock` | 需要 TileEntity |
| `copper_golem_statue` | 8 | — | 属性含 `copper_golem_pose` |
| `lightning_rod` | 8 | `LightningRodBlock` | 属性 `[facing, powered, waterlogged]` |
| `amethyst_cluster` | 4 | `AmethystClusterBlock` | |
| 其余零散 | ~49 | | 洞穴藤蔓、火把花作物、尖牙、幽匿脉络等 |

物品侧基本做完了，只剩 17 个纯物品：船 12、筏 2、桶 3。这三类都需要新实体
（`BoatEntity.Type` 要加木种、运输船要新实体、桶要液体或生物），和「新生物」是同一批依赖。

本轮已补的 95 个纯物品，做法记在 `GenerateItems.SPECIAL_CLASS`：
陶片 23 与旗帜图案 4（官方就是普通 `Item`，之前被 `NEEDS_CLASS` 的后缀误拦）、
锻造模板 19（`SmithingTemplateItem`，官方那个类除 tooltip 外没有游戏逻辑）、
刷怪蛋 23（1.21 改成每生物一张 PNG，不再依赖 `SpawnEggItem` 染色；生成由服务端裁决，
客户端只要 ID 对得上）、铜工具 5 与铜盔甲 4（新增 `ArmorMaterial.COPPER`）、
长矛 7（`SpearItem`）、马铠 2、唱片 8。

---

## 六、第三优先：Via 透传（用户的最终目标）

用户最在意的是**多人服务器场景**：现在连 1.21.11 服务器时，服务器发来的深板岩
仍被 ViaBackwards 降级成黑石，客户端注册表扩展了也用不上。

关键事实（已调研，见 §9.x）：

- `ExtendedBlockStateMapper` 是现代方块状态进入客户端的**唯一入口**
  （`ChunkDataInterceptor` 与 `ExtendedHeightBlockUpdateHandler` 都走它）
- Via 的 `MappingData` 能反查**物品和方块**的标识符（`getFullItemMappings()` /
  `getFullBlockMappings()`），但**方块状态只有 ID→ID，没有标识符**
- 所以要自己打包一张「1.21.11 blockstate ID → 本地 blockstate ID」映射表，
  这也是为什么前面坚持让新方块的**状态数与官方一致**（见 §9.11）

### 认知修正：不是「映射失败兜底成石头」，是「成功地降级了」

原来这份文档写的是「它现在映射失败就 fallback → Blocks.STONE，这是深板岩变石头的
直接原因」。**这个判断是错的**，会把人引到错的地方去改。

`slowMapToNativeId` 的实际行为是把 `rawStateId` 交给 Via 的 `MappingChain`，
而 ViaBackwards 在 1.21→1.16.4 这条链上**本来就把深板岩降级成石头**——映射是成功的，
`STONE_ID` 那个兜底根本没被走到。所以改兜底没有任何用，必须在**进 Via 链之前**截住。

### 已确认的两个前提（动手前必读，键错了整张表都白做）

**一、拦截点在 Via 解码器之前，拿到的是服务器原生 ID。**

`de/florianmichael/viamcp/MCPVLBPipeline.java` 把拦截器装在
`addBefore(VIA_DECODER_HANDLER_NAME, ChunkDataInterceptor.HANDLER_NAME, ...)`，
所以 `ChunkDataInterceptor.convertSection` 里那个 `palette.idAt(x, y, z)` 读到的是
**服务器那个版本原生的、未降级的** blockstate ID。这也能反过来印证：
正因为是原生 ID，后面才需要 `chain.map()` 去降级。

**二、所以映射表只对 1.21.11 服务器有效。**

`loadMappings(target)` 是从服务器版本往下逐级组装降级链
（1.21.11→1.21.9→…→1.17→1.16.4）。连 1.20 服务器时 `rawId` 在 1.20 的 ID 空间里，
1.21.11 的表对不上；而且**指望不上让它先升到 1.21.11 再查表**——降级链只往一个方向走，
比 1.21.11 老的服务器的 ID 永远不会经过 1.21.11 的空间。

结论：要么按版本各生成一张表，要么先只支持 1.21.11。集成时**必须加版本门**，
否则连 1.20 服务器会按错的键查表，出来的是随机方块 —— 比降级成石头糟得多，
而且没有任何报错。

生成其它版本的表是纯机械活：拿那个版本的 server.jar 跑 `--reports` 得到它的
`blocks.json`，同一个生成器再跑一遍。

### 已完成的部分（2 个 agent + 主线，回归全绿）

| 层 | 做法 | 覆盖 |
|---|---|---|
| **区块方块** | `ChunkDataInterceptor` 无条件捕获区块包（Via 解码器之前），`ExtendedBlockStateMapper` 先查表再退 Via 链 | 26745/29671 = **90.1%** |
| **单/多方块更新** | 改写 Via 解码出来的包（见下） | 同上 |
| **物品** | 读 ViaBackwards 存在 NBT 里的 `VB|<协议>|id` 还原真实物品 | 467/533 已注册的全覆盖 |

产物：`tools/crossversion/GenerateBlockStateMap.java`（生成器，自校验后才输出）、
`src/main/resources/crossversion/blockstate-map-1.21.11.txt`（1166 行 / 72 KB，按方块分组）、
`net/minecraft/crossversion/ModernBlockStateMap.java`（运行时展开，建表 43 ms / 常驻 231 KB）、
`tools/crossversion/BlockStateMapCheck.java`（身份 + 撞名 + 抽样三重校验）、
`com/mentalfrostbyte/jello/util/game/inventory/ExtendedItemMapper.java`。

### 状态表的展开顺序：必须按属性名字典序，不是 JSON 键序

`blocks.json` 里 `properties` 的**键顺序不是状态展开顺序**。官方
`StateDefinition.propertiesByName` 是 `ImmutableSortedMap`（`StateDefinition.java:33-66`），
按**属性名字典序**展开，且**最后一个属性变化最快**；本项目 `StateContainer.java:33-60` 同理。

按 JSON 键序展开的话，有 **12 个方块整体错位**（箱子家族、`piston_head`、`moving_piston`
把键按声明顺序列出）。按字典序展开：1166 个方块 / 29671 个状态**零错位**。
生成器每次运行都重新验证（ID 连续 + 笛卡尔积大小 + 逐元组比对），不过就 `exit(1)`。

### pipeline 顺序（改这条链之前必读，有行号支撑）

```
splitter → decompress → chunk-data-interceptor → via-decoder → extended-height-block-update → decoder
```

- `VLBPipeline.java:43` 先 `addBefore("decoder", via-decoder)`；开压缩后 `:52-64` 把它移到
  `addAfter(decompress)`
- `MCPVLBPipeline.java:31-41` 的 `userEventTriggered` 先 `super`、再
  `moveChunkInterceptorAfterDecompression`（`:89` 是 `addAfter(decompress, ...)`），
  所以拦截器正好落在 decompress 与 via-decoder **之间**
- `MCPVLBPipeline.java:162` 用 `addAfter(VIA_DECODER_HANDLER_NAME, ...)`。全仓 grep 过
  `addAfter/addBefore`，via-decoder 与 vanilla `decoder` 之间**只有这一个** handler

关键时序事实：`VLBViaDecodeHandler` 是 `MessageToMessageDecoder`，每帧只 `out.add` 一次，
netty 在 `decode()` 返回后才 fire。上游是 `ByteToMessageDecoder`，一帧走完整条链才发下一帧。
所以「原始包 → Via 译码包」**必定在同一个同步调用栈里、原始包先到**。

### 方块更新为什么不能用「注入第二个包」

原来那套子系统只捕获 **Y 越界**的更新（`isTranslatedBlockYInBounds` 取反），因为它的职责是
把 Via 因为 Y 超出 0-255 而**丢弃**的包重新注入 —— 没有竞争者，所以直接注入没问题。

Y 在界内时 Via 会**正常投递降级版本**。这时若只放宽捕获条件去注入，同一位置会被设置两次，
**谁最后到谁生效**；而 `drain()` 原本排在 `super.channelRead` 之前，正确方块会先到、
石头后到覆盖掉 —— 等于没修。

现在的做法是**改写 Via 解码出来的那个包**：界内的原始 stateId 按「线格式位置 long」寄存，
handler 认出 1.16.4 的 CHANGE_BLOCK / MULTI_BLOCK_CHANGE 后用寄存值重建包、
`release()` 掉 Via 那个。**出口永远只有一个包**，二选一，不存在重复。
位置键 `remove()`-on-read，`channelReadComplete` 清掉没配上的寄存项。

副作用是好的：石头那个包在到达 vanilla decoder 前就被释放，**玩家看不到闪一下**。
挖掉方块（→空气）时 `nativeStateId == viaStateId`，不介入、零开销。

**已知边界**：MULTI 那半只在 ViaBackwards 原样转发 section-position long 时生效
（1.16.2 起该包格式与 1.17+ 相同，推断成立但未反编译验证）。配不上就不介入，
维持现状不是回归 —— 但活塞、爆炸这类一次改一片的场景可能仍是降级的。

### 调试开关

| 开关 | 作用 |
|---|---|
| `-Dsigma.viamcp.debugBlockUpdateReinject=true` | 打出成对的 `PARKED_BLOCK_UPDATE` / `OVERRIDE_BLOCK_UPDATE`。只有 PARKED 没有 OVERRIDE = 该状态不在表里（那 9.9%）或 Via 的值本来就对 |
| `-Dsigma.viamcp.reinject.modernState=false` | 单独关掉方块更新改写这条新路 |
| `-Dsigma.viamcp.debugItemMapping=true` | 打出每次物品替换 |
| `-Dsigma.viamcp.testBlockUpdateReinject=true` | 启动时跑 44 个 round-trip 自检 |

### 还没做的

- **物品名残留占位名**（「1.21 mace」）与**附魔被 Via 降级成字面文本**（`wind_burst` 显示英文）。
  这两条是同一类问题：Via 把不认识的内容转成显示文本。**风险点是 Via 用两套不同的移除标记**
  （NBT 时代 `nbtTagName("customName")` 在 `display` 里，1.20.5+ 结构化组件用
  `added_custom_name`），弄错会破坏出站的 `restoreDisplayTag`
- **只支持 1.21.11**（见前面的版本门说明）
- 那 9.9% 未映射：2576 个状态属于 75 个未注册方块，350 个是 `note_block` 的 7 种新乐器
  （后者按设计留给 Via，它能映射成真实的 1.16.4 音符盒）

### 数据源是完整的，不用问 Via

`1.21.11/reports/blocks.json`（在 `.gitignore` 里，但文件在磁盘上）每个方块都带：

```json
"minecraft:acacia_button": {
  "properties": { "face": [...], "facing": [...], "powered": [...] },
  "states": [ { "id": 10569, "properties": { "face":"floor", "facing":"north", "powered":"true" } }, ... ]
}
```

`id` 就是 1.21.11 的 blockstate ID，`properties` 是完整属性取值。配上
`ModernRegistry.blockByModernId(标识符)`（它已处理 `short_grass`→`grass` 这类改名）
就能算出本地 `BlockState`，再 `Block.getStateId(...)`。

**属性匹配是这活儿真正的难点**：属性名可能对不上（1.16.4 的 `SeaPickleBlock` 用
`pickles`、官方蜡烛用 `candles`，为此专门自建过属性），本地属性集合可能比官方少或多。
**匹配不到必须留空让调用方走兜底，绝不能猜一个** —— 静默映射错块比降级成石头更难排查。


---

## 七、每次新增方块后必须跑的四步

**漏任何一步都不会在编译期暴露。** 前面每一步都因为漏跑而返工过。

```bash
# 1) 生成代码（注意：Git Bash 下 classpath 用 ; 且路径过 cygpath -w）
GSON=$(cygpath -w ~/.m2/repository/com/google/code/gson/gson/2.8.9/gson-2.8.9.jar)
OUT=$(cygpath -w target/crossversion-gen)
javac -encoding UTF-8 -cp "$GSON" -d target/crossversion-gen tools/crossversion/GenerateBlocks.java
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.GenerateBlocks .
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.GenerateItems .
```

**2) 手工同步字段区** —— 生成器只写到 `target/crossversion-check/gen-*.java.txt`，
**不会**覆盖源码。必须按 `// === 生成字段结束 ===` 标记切分：

```bash
F=src/main/java/net/minecraft/block/ModernBlocks.java
GE=$(grep -n "生成字段结束" "$F" | cut -d: -f1)
{ sed -n '1,38p' "$F"; cat target/crossversion-check/gen-blocks.java.txt; \
  sed -n "$GE,\$p" "$F"; } > /tmp/mb.new
grep -c '^    public static final Block ' /tmp/mb.new   # 字段数必须对得上
cp /tmp/mb.new "$F"
```

物品侧同理，头部取 `1,31p`。**`ModernItems.java` 的手工区现在是空的** ——
铜镐曾经手写在那里，现在整套铜工具、铜盔甲、长矛、锻造模板、马铠都由生成器按
`GenerateItems.SPECIAL_CLASS` 产出。同步后字段数应等于生成器报的总数，
多一个就说明手工区还有残留（会重复注册同一个标识符）。

给生成器加专门类时的两个坑：

- `SPECIAL_CLASS` 的值是「类名 + Properties 之前的构造参数」。参数照 1.16.4 的签名写，
  不是官方的 —— 1.16.4 的 `SwordItem`/`PickaxeItem`/`HoeItem` 攻击力是 `int`，
  `ShovelItem`/`AxeItem` 是 `float`
- 往 `DURABILITY_FROM_MATERIAL` 里加类名前，先确认那个类真的会调 `defaultMaxDamage`。
  把 `MaceItem` 列进去过，结果重锤耐久变成 0（它 `extends Item` 不是 `TieredItem`），
  被回归的「mace 耐久」一项抓到

**3) 资源、战利品表、tag** —— 清单要用**实际注册表导出**的那份（含手工区物品），
不能用生成器产物，否则手工区的物品会漏掉中文（铜镐就这么漏过）：

```bash
bash tools/crossversion/run-registry-check.sh   # 它会导出 reg-blocks.txt / registered-items.txt
comm -13 <(sort docs/registry-diff/baseline-1.16.4-items.txt) \
         <(sort target/crossversion-check/registered-items.txt) > target/crossversion-check/ext-items.txt
comm -13 <(sort docs/registry-diff/baseline-1.16.4-blocks.txt) \
         <(sort target/crossversion-check/reg-blocks.txt) > target/crossversion-check/ext-blocks.txt

# 资源 + 中文（中文从 MCP-Reborn 的官方 zh_cn.json 生成）
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.ExtractAssets \
  1.21.11/client.jar src/main/resources/assets/minecraft \
  target/crossversion-check/ext-items.txt target/crossversion-check/ext-blocks.txt

# 战利品表（注意用 server.jar 里的内嵌 jar，外层没有战利品表）
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.ConvertLootTables \
  target/vdp/META-INF/versions/1.21.11/server-1.21.11.jar \
  target/crossversion-check/ext-blocks.txt \
  src/main/resources/data/minecraft/loot_tables/blocks \
  target/crossversion-check/ext-items.txt

# tag（墙、栅栏的互连靠它）
java -Dfile.encoding=UTF-8 -cp "$GSON;$OUT" verify.MergeBlockTags ...
```

**4) 编译 + 回归**：

```bash
mvn -o -q compile && bash tools/crossversion/run-registry-check.sh
```

---

## 八、绝对不能碰的五条红线

1. **物品 ID 0-975、方块 ID 0-762 必须逐一保持不变。** 只能在
   `Items.java:992` / `Blocks.java:889` 之后追加。中间插入会让连 1.8/1.12/1.16.4
   服务器时物品全部错位。回归脚本第一项就在查这个。
2. **方块状态总数不能超过 32768。** 现在 26497（原版 17112），超过阈值
   `PalettedContainer` 的位宽会从 15 跳到 16 并与跨版本区块数据错位。
   **余量只剩约 6270** —— 这一轮从 24734 涨到 26497，光告示牌 32 个方块就吃掉 1000 多
   （悬挂告示牌单个 64 状态）。剩下 78 个方块里 `copper_chest` / `copper_golem_statue` /
   `crafter` / `vault` 都是几十个状态的大户，**补之前先算一遍别撞上限**。回归脚本会报当前值。
3. **重命名映射不要注册成新方块。** `short_grass`/`dirt_path`/`turtle_scute`/`iron_chain`
   是 1.16.4 的 `grass`/`grass_path`/`scute`/`chain` 改名，已在
   `ModernRegistry.RENAMED_TO_LEGACY` 里指向原版。它们出现在「未覆盖」清单里是正确的。
4. **实体类型只能追加在 `EntityType.java` 末尾。** 与第 1 条同理：
   `SSpawnObjectPacket` 写的是 `Registry.ENTITY_TYPE.getId(type)` 的裸 id，
   而 `register` 按声明顺序发号。官方按字母序插新类型，照抄会让后面每个类型都偏移，
   连原版服务器发来的实体都生成成错误类型。已踩过：`wind_charge` 一度插在
   `PLAYER` / `FISHING_BOBBER` 之前。文件末尾有专门的扩展区和注释。

5. **绝对不要给原版方块增删属性。** 原版方块状态 id 0-17111 必须与真实 1.16.4
   **逐一相同**，这是整个 Via 透传的地基。

   `ExtendedBlockStateMapper.slowMapToNativeId` 里这一行是要害：

   ```java
   int nativeId = Block.BLOCK_STATE_IDS.getByValue(mappedId) != null ? mappedId : STONE_ID;
   ```

   `mappedId` 是 Via 降级链输出的**真实 1.16.4 状态 id**，这里**直接当本地 id 用**。
   给任何原版方块加一个属性，它之后所有方块的状态 id 全部移位，于是**每个经 Via 来的
   方块、以及连真实 1.16.4 服务器时的每个方块都会变成错的方块** —— 而且不抛任何异常。

   **这条差点被踩。** 方块状态表的覆盖率报告建议「给 6 种树叶 + 4 种铁轨 + 屏障补
   `waterlogged`、给 12 个头颅补 `powered`，代价只有 +299 状态，能把 90.1% 的覆盖率
   往上推、并救回 251 个被折叠的状态」。状态总数确实装得下，但**这个改法是错的**。

   ID 安全的做法是把含水变体注册成**独立的扩展方块**（追加在 762 之后），
   §「并行铺开那一轮」里樱花树叶就是这么救回来的（`ModernWaterloggedBlock` 子类）。
   丑，但不动原版 id。

   `RegistryCheck` 已经在查一半：扩展方块的状态 id 若落进原版区间会报
   「** 方块状态 id N 落在原版区间内」。但它**查不出**「原版方块自己的属性被改了」，
   所以这条只能靠人守。


---

## 九、教训：校验工具本身也会错

这个项目里检查器出错过两次，都差点导致把正确的实现改坏：

- **紫黑块检查**第一版要求 blockstate variant key 与状态完整相等，一次报 85 个
  「不匹配」，全是误报 —— 官方 variant key 是**条件子集**，未列出的属性会被忽略
- **数据包检查**用的已注册清单是手工快照，新增方块后没更新，把已注册的物品报成
  未注册，一次 68 条假告警

所以：**报告一批异常时，先验证检查逻辑，别急着改被报的代码。**
已修：紫黑块检查改成子集匹配，注册清单改成每次从活注册表导出。

---

## 十、详细背景

`docs/cross-version-registry-plan.md` 有完整记录，重点章节：

| 章节 | 内容 |
|---|---|
| §9.14 | 上一轮的状态与主线规划 |
| §9.11 | 属性不匹配的真正后果（不是紫黑，是状态数不一致） |
| §9.12 / §9.13 | 生成器的坑：不同步等于白改、classpath 分隔符、氧化铜类型名 |
| §9.15 | 中文改从 MCP-Reborn 自动生成 |
| §9.16 | 「亮度不对」怎么分清是 bug 还是原版行为 |
| §9.18 | 每次新增后必跑的步骤 |
| §9.20 | 创造栏 fallback 规则的误伤 |
