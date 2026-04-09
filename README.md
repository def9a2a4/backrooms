# PluginTemplate

A template repository for Paper plugins.

## File Map

| Path                                          | Description                                                                                                                      |
| --------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------- |
| `build.gradle.kts`                            | Gradle build config: dependencies (Paper API, bStats), shadow JAR, version interpolation, Java 21 toolchain                      |
| `settings.gradle.kts`                         | Gradle project name                                                                                                              |
| `Makefile`                                    | Build and test automation — the primary interface for building, running a dev server, and CI                                     |
| `src/main/java/.../PluginTemplatePlugin.java` | Main plugin class (extends `JavaPlugin`)                                                                                         |
| `src/main/resources/plugin.yml`               | Plugin manifest — name, commands, permissions. Version is injected at build time from `build.gradle.kts`                         |
| `src/main/resources/config.yml`               | Default plugin config, copied to the server's plugin data folder on first run                                                    |
| `.github/workflows/build.yml`                 | CI: builds the plugin on push/PR to main                                                                                         |
| `.github/workflows/server-test.yml`           | CI: downloads a Paper server, loads the plugin, and verifies it starts without errors. Manual trigger only (`workflow_dispatch`) |
| `.gitignore`                                  | Ignores build artifacts, server directories, IDE files                                                                           |

## Customizing the Template

When creating a new plugin from this template, update the following:

### Plugin name

Replace `PluginTemplate` / `plugintemplate` with your plugin name in these files:

| File                            | What to change                                                          |
| ------------------------------- | ----------------------------------------------------------------------- |
| `build.gradle.kts`              | `archiveBaseName.set(...)` (appears twice: `jar` and `shadowJar` tasks) |
| `settings.gradle.kts`           | `rootProject.name`                                                      |
| `Makefile`                      | `PLUGIN_NAME` variable at the top                                       |
| `src/main/resources/plugin.yml` | `name`, `main` class path, command name, permission prefixes            |
| `.github/workflows/build.yml`   | Artifact name and path in the commented-out upload step                 |
| `PluginTemplatePlugin.java`     | Class name and filename                                                 |

### Package name

The default package is `name.d3420b8b7fe04.def9a2a4.plugintemplate`.

1. Rename the directory tree under `src/main/java/` to match your new package
2. Update the `package` declaration in your main class
3. Update the `main` field in `plugin.yml`
4. Update the `group` in `build.gradle.kts` if your base group changes
5. Update the bStats `relocate(...)` target in `build.gradle.kts` if your base group changes

### Other fields

| Field       | File                        | Notes                                                                        |
| ----------- | --------------------------- | ---------------------------------------------------------------------------- |
| Version     | `build.gradle.kts`          | `version = "0.1.0"` — automatically injected into `plugin.yml` at build time |
| bStats ID   | `PluginTemplatePlugin.java` | Replace `00000` with your plugin's ID from [bstats.org](https://bstats.org)  |
| Author      | `plugin.yml`                |                                                                              |
| Description | `plugin.yml`                |                                                                              |
| Commands    | `plugin.yml`                | Add/remove commands and their permissions                                    |
| Config      | `config.yml`                | Your plugin's default configuration                                          |

## Building

```sh
make build       # compile and produce bin/PluginTemplate-<version>.jar
make clean       # remove build artifacts
```

## Running a Dev Server

Set up a local Paper server in `server/` with the server JAR, then:

```sh
make server      # build, copy JAR to server/plugins/, start the server
```

Or step by step:

```sh
make build
make server-plugin-copy
make server-start
```

## Test Server (CI)

The Makefile can download a Paper (or Purpur) server and run the plugin in an automated test environment:

```sh
make build
make test-server-download-all                  # downloads Paper JAR (cached in .download-cache/)
make test-server-setup                         # configures eula, server.properties, copies plugin
make test-server-local                         # starts the test server (1GB heap)
```

To use a different server or Minecraft version:

```sh
make test-server-download-all SERVER_VARIANT=purpur MINECRAFT_VERSION=1.21.4
```

The `test-server-ci` target runs the server headlessly, waits for startup, verifies the plugin loaded, checks for errors, and shuts down — used by the `server-test.yml` GitHub Action.

## CI Workflows

- **Build** (`build.yml`): Runs on every push/PR to `main`. Compiles the plugin with `make build`.
- **Server Integration Test** (`server-test.yml`): Manual trigger only. Downloads a Paper server, loads the plugin, and verifies it starts without errors. Expand the `matrix` to test multiple Minecraft versions or server types.
