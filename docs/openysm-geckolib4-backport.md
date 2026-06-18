# OpenYSM GeckoLib 4 Backport Status

## Scope

This branch contains a source-level GeckoLib 4 architectural backport slice for MCP 1.16.4 and wires it into the OpenYSM player path. It is not a runtime dependency on a GeckoLib jar, and it is not a tiny cube demo. It is also not yet a complete upstream GeckoLib 4 transplant.

The current default renderer is `openysm`. The old self renderer remains available as an explicit `legacy_self` fallback.

## Source Baselines

- GeckoLib source: `.codex-cache/geckolib`
- GeckoLib branch: `1.20.1`
- GeckoLib commit: `d789c27`
- GeckoLib version target: `4.8.3`
- OpenYSM reference: `.codex-cache/OpenYSM-2.6.5`
- OpenYSM tag: `ysm-2.6.5-forge+mc1.20.1`
- OpenYSM commit: `4917ec4babde43ae9d506f0c095c3f8007aad2e2`

## Backported GL4 Modules

The MCP-local GL4 package lives under `src/main/java/com/elfmcys/yesstevemodel/geckolib4`.

- Bootstrap and source marker: `GeckoLibBackport`
- Cache: `GeckoLibCache`
- Baked model objects: `BakedGeoModel`, `GeoBone`, `GeoCube`, `GeoQuad`, `GeoVertex`
- Animatable and controller core: `GeoAnimatable`, `AnimatableManager`, `AnimationController`, `AnimationState`, `RawAnimation`, `PlayState`
- Model base: `GeoModel`
- Renderer core: `GeoRenderer`
- Renderer base classes: `GeoEntityRenderer`, `GeoItemRenderer`, `GeoBlockRenderer`, `GeoArmorRenderer`
- Renderer layers: `GeoRenderLayer`, `GeoRenderLayersContainer`
- MCP adapter shim: `McpRenderAdapter`

## MCP 1.16.4 Adapter Map

- `PoseStack` -> `MatrixStack`
- `VertexConsumer` -> `IVertexBuilder`
- `MultiBufferSource` -> `IRenderTypeBuffer`
- `BlockEntity` -> `TileEntity`
- `BlockEntityRenderer` -> `TileEntityRenderer`
- `BlockEntityWithoutLevelRenderer` -> `ItemStackTileEntityRenderer`
- `ItemDisplayContext` -> `ItemCameraTransforms.TransformType`
- `HumanoidModel` -> `BipedModel`
- `EntityRendererProvider.Context` -> `EntityRendererManager`
- 1.20 render type and buffer calls -> centralized `McpRenderAdapter`

## OpenYSM Integration

OpenYSM keeps the existing resource and package layer, then bakes into GL4-style geometry:

`.ysm/json -> YsmCrypt/YSMBinaryDeserializer/RawYsmModel/YSMFolderDeserializer -> OpenYsmModelLoader -> BakedGeoModel/GeoBone/GeoCube -> GeoRenderer -> PlayerRenderer`

Current integration points:

- `OpenYsmModelLoader` builds both legacy `OpenYsmBone` wrappers and GL4-style `GeoBone`/`GeoCube` trees for the main player model.
- `OpenYsmModelLoader` also bakes optional JSON `files.player.model.arm` and binary `.ysm` `armModel` geometry for OpenYSM's dedicated first-person arm path.
- `OpenYsmBakedPlayerModel` carries separate main and arm `BakedGeoModel` instances and registers them in `GeckoLibCache` under sanitized OpenYSM resource keys.
- `OpenYsmBone` mirrors pose and visibility state into its linked `GeoBone`.
- `OpenYsmGl4PlayerModel` renders the player model through `GeoRenderer`.
- `PlayerRenderer` routes player body and first-person arm rendering through the selected renderer mode, preferring the dedicated arm model when the package provides one and falling back to the main model arms otherwise.

## Renderer Selector

System property: `-Dyes_steve_model.renderer=<mode>`

Supported values:

- `vanilla`: skip YSM rendering and use vanilla player rendering.
- `legacy_self`: use the old `OpenYsmModelRenderer` fallback.
- `gl4`: use the MCP-local GL4 renderer path.
- `openysm`: use the OpenYSM GL4 renderer stack alias.

Initial default: `openysm`.

The `gl4` and `openysm` modes initialize `GeckoLibBackport` and use `OpenYsmGl4PlayerModel`. The old `OpenYsmModelRenderer` is retained only for `legacy_self` fallback and now rate-limits invalid custom quad warnings.

## Animation, Controller, and Molang Scope

Implemented OpenYSM-side support includes:

- Animation source categories: `main`, `arm`, `fp_arm`, `extra`, `custom`, `gui_preview`, `controller_referenced`
- Main/arm/extra/custom/gui preview action resolution
- Binary animation type `11` maps to `fp_arm`, matching OpenYSM's first-person arm animation bucket.
- `PLAY_ONCE`, `LOOP`, and `HOLD_ON_LAST_FRAME`
- Default-hidden conditional bones and active-clip visibility behavior
- Controller state parsing, initial state, transitions, weights, blend transition timing, multiple controllers, and controller reset
- `query.all_animations_finished`, `query.any_animation_finished`, `ctrl.*`, `query.*`, `variable.*`, and `temp.*` binding categories used by the controller runtime
- A bounded AST-style Molang parser/evaluator with arithmetic, comparison, boolean operators, and selected math functions

Security constraints:

- No `ScriptEngine`
- No JavaScript/Java eval
- No reflection execution
- No command execution
- No file access from Molang expressions
- OpenYSM folder resource paths stay constrained by `normalizeRelativePath` and `resolveInside`

## Missing Upstream Modules

These upstream GL4 areas are not fully transplanted yet:

- Fabric, NeoForge, ForgeGradle, datagen, loader event bus, and Mixin-specific code. These need MCP equivalents rather than direct copy.
- Full GeckoLib resource reload registry. The current OpenYSM selected-model cache clears and unregisters its baked model, but there is no complete GL4 reload bus.
- Full GL4 model parser and loader stack. OpenYSM currently bakes through `OpenYsmModelLoader` into GL4-style baked objects.
- Specialized GL4 render layers such as held-item, translucent, texture-swap, and emissive layers.
- Full upstream item/entity/block/armor in-game validation matrix. Base classes compile, but broad gameplay validation is still pending.
- NativeModelRenderer/SIMD path. It has not been ported; Java `GeoRenderer` remains the fallback renderer.

## Verification

Compile command:

```powershell
mvn -DskipTests compile
```

Status: compile succeeds for the current MCP source tree with the GL4 backport slice present. The build still emits existing project warnings around `sun.misc.Unsafe` and deprecated APIs, but no compile errors were observed.

Runtime status:

- Player renderer routing is implemented.
- GL4/OpenYSM mode can be selected by system property.
- `openysm` is the default renderer path.
- Legacy fallback remains available.
- Dedicated OpenYSM first-person arm geometry and `fp_arm` animations are wired for both JSON folders and binary `.ysm` packages.
- Broad in-game verification across entity, item, armor, block/tile entity, renderer layers, resource reload, and a wider real OpenYSM model matrix is still pending.

## Known TODOs

- Complete upstream GL4 parser/resource reload/cache parity instead of only the OpenYSM bake path.
- Add concrete GL4 item/entity/block/armor validation fixtures inside this MCP client.
- Port or replace specialized GL4 render layers.
- Verify more real encrypted `.ysm` crypto=3 packages, JSON folder models, texture variants, first-person arms, extra/custom actions, and controller transitions in-game.
- Evaluate NativeModelRenderer/SIMD portability separately without blocking the Java renderer path.
