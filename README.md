# Ars Addon Builder

Ars Addon Builder downloads pre-built mod JARs from CurseForge and runs a headless Minecraft client to export documentation, renders, recipes, tags, and language files for Ars Nouveau and its addons.

The workflow runs daily and can also be triggered manually.

## Generated assets

- Manifest: https://assets.ars.guide/manifest.json
- Book: https://assets.ars.guide/manifests/book.json
- Language: https://assets.ars.guide/manifests/lang.json
- Projects: https://assets.ars.guide/manifests/projects.json
- Recipes: https://assets.ars.guide/manifests/recipes.json
- Render: https://assets.ars.guide/manifests/render.json
- Tags: https://assets.ars.guide/manifests/tags.json
- Tomes: https://assets.ars.guide/manifests/tomes.json

## Adding a new project

- Create a file in `./projects` matching your project name. Example: `ars_artifice.json`
- Fill out the following JSON:
```json
{
  "mod_id": "ars_artifice",
  "display_name": "Ars Artifice",
  "disabled": false,
  "cf_id": 123456,
  "dependencies": []
}
```
- `mod_id` -- the Minecraft mod ID (from `neoforge.mods.toml`)
- `display_name` -- human-readable name
- `cf_id` -- CurseForge project ID
- `disabled` -- set to `true` to skip this project
- `dependencies` -- array of CurseForge dependencies not already covered by another project, e.g. `[{ "cf_id": 328085, "name": "create" }]`
- The mod must have direct downloads enabled on CurseForge.
- Create a PR for the new file.

## Requirements

The repository requires a `CURSEFORGE_API_KEY` secret (from https://console.curseforge.com/).

## License

This repository is licensed under LGPL-3.0-or-later. Some rendering code is derived from [GuideME](https://github.com/AppliedEnergistics/GuideME), which is published under LGPL-compatible terms.
