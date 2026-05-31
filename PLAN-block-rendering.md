# Plan: GuideME-Based Item and Block Render Export

## Status

Draft plan for review. Decisions so far: export every loaded block/item, use GuideME’s WebP strategy for newly rendered animated outputs, and keep the existing animated texture GIF output during migration without additional GIF work.

## Context

ArsAddonBuilder currently exports Ars Nouveau documentation JSON, language JSON, glyph metadata, and animated texture GIFs from the `doc-exporter-mod` client runtime. It does not yet export full rendered block images.

GuideME has a matching Minecraft 1.21.1 branch/tag (`v21.1.16`) whose site export pipeline already renders `<BlockImage>` content by constructing a one-block `GuidebookScene`, rendering it off-screen, and writing image bytes. Since we are willing to relicense the repository as LGPL-compatible, we can copy the relevant rendering code instead of depending on GuideME internals.

## GuideME findings to reuse

- `GuideME` 1.21.1 branch targets Minecraft `1.21.1` and NeoForge `21.1.113`, close to this project’s `doc-exporter-mod` target.
- `guideme.scene.BlockImageTagCompiler` creates a `GuidebookLevel`, inserts one `BlockState` at `BlockPos.ZERO`, applies `CameraSettings`, centers the scene, and wraps it in `LytGuidebookScene`.
- `guideme.scene.LytGuidebookScene.exportAsPng(scale, hideAnnotations)` renders a scene to PNG via `OffScreenRenderer`.
- `guideme.internal.siteexport.OffScreenRenderer` owns the reusable framebuffer/`NativeImage` capture mechanics and animated-frame stepping/export support via `captureAsWebp`; we should reuse/adapt this WebP path directly for rendered animated block/item outputs.
- `guideme.scene.GuidebookLevelRenderer` handles block rendering, block entities, fluids, render layer flushing, lighting, and model data against a minimal fake `Level`.

## Recommended approach

Copy a small, adapted subset of GuideME’s renderer into `doc-exporter-mod` under `dev.qther.doc_exporter.render` and use it as the canonical render path for both items and blocks.

The new render path should:

1. Use a local `OffScreenRenderer` adapted from GuideME for framebuffer capture.
2. Use a local minimal scene/level renderer adapted from GuideME for true block-state rendering.
3. Render static blocks/items to PNG.
4. Render animated blocks/items to WebP by reusing/adapting GuideME `OffScreenRenderer.captureAsWebp` so the off-screen renderer itself steps animated sprites and writes the animated image.
5. Keep the existing CPU texture-strip GIF exporter during migration, but do no additional GIF work; retire it later once the WebP rendered output covers the needed cases.

## Files to change

Critical files expected to change:

- `doc-exporter-mod/build.gradle`
- `doc-exporter-mod/gradle.properties`
- `doc-exporter-mod/src/main/templates/META-INF/neoforge.mods.toml`
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/DocExportHelper.java`
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/ExportPaths.java`
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/AnimatedTextureExporter.java` — keep during migration, later delete or replace
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/GifGenerator.java` — keep unchanged during migration for legacy GIF output only
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/TextureReloader.java` — keep only while the legacy texture-strip exporter remains
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/mixin/*AnimatedTexture*Accessor.java` — reassess; keep only if needed for rendered animation timing
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/mixin/FrameInfoAccessor.java` — reassess; keep only if needed for rendered animation timing
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/mixin/SpriteContentsAccessor.java` — reassess; keep only if needed by legacy texture-strip export
- `doc-exporter-mod/src/main/resources/doc_exporter.mixins.json`
- `doc-exporter-mod/src/main/resources/META-INF/accesstransformer.cfg`
- `.github/workflows/*.yml` where generated outputs are cleaned/archived
- repository-level license files/metadata

New files expected:

- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/render/OffScreenRenderer.java` with GuideME-derived PNG capture and `captureAsWebp` animated export methods
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/render/RenderImageExporter.java`
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/render/BlockRenderExporter.java`
- `doc-exporter-mod/src/main/java/dev/qther/doc_exporter/render/ItemRenderExporter.java`
- Supporting GuideME-derived fake-level/scene renderer classes as needed

## Existing code to reuse or replace

Reuse/keep:

- `DocExportHelper` as the startup orchestration point.
- `ExportPaths` as the central output path registry.
- `DocExportHelper.executor` only for file writing or post-processing that does not touch Minecraft render state.

Reuse during migration, then simplify:

- `AnimatedTextureExporter`: keep initially so existing `output/animated_textures/**/*.gif` output remains stable, then remove once rendered WebP output is trusted.
- `GifGenerator`: keep unchanged for the legacy GIF exporter during migration; do not extend it for new rendered outputs.
- `TextureReloader`: keep only for the legacy texture-strip exporter; remove when that exporter is retired.
- Sprite/animation accessor mixins and access transformers: keep only the subset needed to detect animated sprites and frame timing for render-based animation capture.

## Implementation checklist

- [ ] Update repository/mod licensing metadata to LGPL-compatible terms and add GuideME attribution for copied/adapted files.
- [ ] Copy/adapt GuideME `OffScreenRenderer` for Minecraft 1.21.1 framebuffer capture.
- [ ] Copy/adapt the minimum GuideME fake-level and scene-rendering classes needed for one-block scenes.
- [ ] Implement `BlockRenderExporter`:
  - [ ] scan every loaded block/item registry entry rather than filtering to Ars namespaces,
  - [ ] create a one-block scene for each block state,
  - [ ] export static renders to PNG,
  - [ ] export animated renders to WebP.
- [ ] Implement `ItemRenderExporter` using the same `OffScreenRenderer` and Minecraft item renderer.
- [ ] Reuse/adapt GuideME’s `OffScreenRenderer.captureAsWebp` for rendered animated block/item outputs.
- [ ] Add output paths: recommended `output/renders/block/{namespace}/{path}.png|.webp` and `output/renders/item/{namespace}/{path}.png|.webp`.
- [ ] Integrate new exporters into `DocExportHelper.postTick` and ensure render work stays on the render/client thread.
- [ ] Keep old GIF/CPU texture extraction code during migration without expanding it; remove it only after rendered WebP outputs cover the same use cases.
- [ ] Update mixin config/access transformers to remove no-longer-needed animation accessors after legacy GIF removal.
- [ ] Update CI cleanup/artifact steps to include the new rendered image output folders while preserving existing GIF artifacts during migration.

## Animated WebP strategy

Preferred direction: use GuideME’s animated WebP strategy directly for newly rendered animated assets. This preserves lighting, block geometry, transparency, fluids, block entities, and model transformations better than extracting raw texture frames.

GuideME’s `OffScreenRenderer.captureAsWebp` already does the needed work: detect animated sprites, compute the animation loop length, advance sprite tickers, render each frame, and write the captured `NativeImage` sequence as animated WebP. We should copy/adapt that method rather than building a separate GIF/APNG exporter.

The GuideME-derived animated capture path should:

- detect whether a block/item uses animated sprites,
- compute an animation loop length from the referenced sprites’ frame timings,
- tick/upload the relevant sprite frames by reusing GuideME’s `OffScreenRenderer.captureAsWebp` structure,
- render and capture each frame through the real block/item renderer inside `OffScreenRenderer`,
- write a WebP to the same rendered asset location where a static PNG would otherwise be written.

## Cleanup checklist

- [ ] Keep generated `output/animated_textures/**/*.gif` during migration; remove in a later change only after rendered WebP parity is confirmed.
- [ ] Keep GIF-specific classes unchanged during migration; remove them when the legacy GIF exporter is retired.
- [ ] Remove animation texture accessors that only support CPU texture-strip generation once no longer needed.
- [ ] Remove `ImageIO` texture-strip loading utilities once the legacy animated texture exporter is retired.
- [ ] Ensure `doc_exporter.mixins.json` only lists still-used mixins.
- [ ] Ensure `accesstransformer.cfg` only contains still-needed accesses.

## Verification

- [ ] Run the doc exporter mod locally and confirm it exits normally.
- [ ] Confirm static block PNGs render with transparency and correct isometric camera.
- [ ] Confirm representative block entities render: e.g. source jars, scribes table, ritual brazier if applicable.
- [ ] Confirm animated blocks produce WebPs: e.g. archwood logs or other known animated block textures.
- [ ] Confirm rendered animated item WebP output is produced alongside existing legacy GIF output during migration.
- [ ] Confirm generated docs/language/glyph JSON remain unchanged except intended render asset references/outputs.
- [ ] Confirm CI still cleans and publishes the expected output folders.

## Open questions

None currently. Recommended rendered output layout is `output/renders/block/{namespace}/{path}.png|.webp` and `output/renders/item/{namespace}/{path}.png|.webp`.
