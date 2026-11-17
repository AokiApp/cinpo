# Migrating to CINPO 0.5.0

CINPO 0.5.0 splits the framework artifact into several Maven modules so that a consumer does not pull in the entire runtime transitively. This note documents what changed and what a consumer needs to do.

## What changed at the publisher side

| Artifact | Role |
|---------|------|
| `app.aoki.cinpo:cinpo:0.5.0` | Framework core (CLI, orchestrator, manifest, GP / SCP crypto, APDU I/O, task loader). |
| `app.aoki.cinpo:cinpo-runtime-jcresim:0.5.0` | Oracle JCDK simulator runtime. |
| `app.aoki.cinpo:cinpo-runtime-jcardengine:0.5.0` | In-process Java Card engine runtime. |
| `app.aoki.cinpo:cinpo-runtime-pcsc:0.5.0` | PC/SC runtime (no native dependencies). |

All four artifacts are published under `groupId=app.aoki.cinpo` and have their own `maven-metadata.xml`, `pom`, and Gradle Module Metadata. They share the same `version` with the plugin (`app.aoki.cinpo:cinpo-gradle-plugin:0.5.0`).

## Consumer impact

Nothing about the source-level API changed: `app.aoki.cinpo.apdu.*`, `app.aoki.cinpo.config.*`, `app.aoki.cinpo.orchestrator.*`, `app.aoki.cinpo.task.*`, and `app.aoki.cinpo.gp.*` all continue to be the packages your code imports. No class moved packages.

What changed is the *dependency declaration*. If your project is based on the consumer template, the template built for 0.5.0 already declares:

```groovy
implementation 'app.aoki.cinpo:cinpo:0.5.0'
implementation 'app.aoki.cinpo:cinpo-runtime-jcresim:0.5.0'
implementation 'app.aoki.cinpo:cinpo-runtime-jcardengine:0.5.0'
implementation 'app.aoki.cinpo:cinpo-runtime-pcsc:0.5.0'
```

If you are hand-editing an existing project rather than regenerating from the template, add whichever runtime modules you use instead of one fat artifact. In particular:

- If you run only `cinpoRun` against the simulator, your project only needs `cinpo` and `cinpo-runtime-jcresim`.
- If you are distributing a binary that provisions physical cards, you only need `cinpo` and `cinpo-runtime-pcsc`.
- If you use `CinpoJarTask` to build a self-contained JAR, its output contains only what you declare on `runtimeClasspath` — nothing else is pulled in silently.

See [Modules](../README.md#modules) for the complete table.
