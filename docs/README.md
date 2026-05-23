# KGraphQL Documentation

The documentation is built with [Starlight](https://starlight.astro.build/), a documentation theme for [Astro](https://astro.build/).

## Prerequisites

An installation of [Bun](https://bun.sh/) is required to run the commands below.
The version of Bun used to build the documentation in the GitHub workflow is defined in [.bun-version](.bun-version).

If you want to use bun locally to the project, you can use tools like [proto](https://moonrepo.dev/docs/proto/) to
manage your bun installation.

## Commands

All commands are run from the root of the documentation folder:

| Command                   | Action                                           |
|:--------------------------| :----------------------------------------------- |
| `bun install`             | Installs dependencies                            |
| `bun dev`                 | Starts local dev server at `localhost:4321`      |
| `bun run build`           | Build your production site to `./dist/`          |
| `bun preview`             | Preview your build locally, before deploying     |
| `bun run astro ...`       | Run CLI commands like `astro add`, `astro check` |
| `bun run astro -- --help` | Get help using the Astro CLI                     |

## Want to learn more?

Check out [Starlight’s docs](https://starlight.astro.build/), read [the Astro documentation](https://docs.astro.build), or jump into the [Astro Discord server](https://astro.build/chat).
