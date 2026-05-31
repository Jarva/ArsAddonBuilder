# Ars Addon Builder

Ars Addon Builder is a project to build generated resources and publish all of them in one easy to retrieve project.

The repository will check whether the projects are up to date each day at midnight and if there is a new git commit the repository will kick off a build for that specific project.

To add a new project:
- Create a file in `./projects` to match your project name. Example: `ars_artifice.json`
- Fill out the following JSON object with the correct information:
```json
{
  "repo": "Jarva/Ars-Artifice",
  "branch": "1.20.x",
  "disabled": false
}
```
- Set `disabled` to `true` to keep a project in the repository without syncing or building it.
- Create a PR for the new file. Do not add the hash key, the script will fetch the latest hash when running at midnight and kick off the build as required.

## License

This repository is licensed under LGPL-3.0-or-later. Some rendering code is derived from [GuideME](https://github.com/AppliedEnergistics/GuideME), which is published under LGPL-compatible terms.
