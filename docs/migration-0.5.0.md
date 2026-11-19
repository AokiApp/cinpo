# Migrating to CINPO 0.5.0

CINPO 0.5.0 splits the framework artifact into several Maven modules so that you only pull in the runtimes you use, and changes the Gradle plugin ID. This note documents what changed and what you need to do.

## What changed at the publisher side

| Artifact | Role |
|---------|------|
| `app.aoki.cinpo:cinpo:0.5.0` | Framework core (CLI, orchestrator, manifest, GP / SCP crypto, APDU I/O, task loader). |
| `app.aoki.cinpo:cinpo-runtime-jcresim:0.5.0` | Oracle JCDK simulator runtime. |
| `app.aoki.cinpo:cinpo-runtime-jcardengine:0.5.0` | In-process Java Card engine runtime. |
| `app.aoki.cinpo:cinpo-runtime-pcsc:0.5.0` | PC/SC runtime (no native dependencies). |
| `app.aoki.cinpo:cinpo-gradle-plugin:0.5.0` | Gradle plugin implementation. |
| `app.aoki.cinpo:app.aoki.cinpo.gradle.plugin:0.5.0` | Gradle plugin marker (POM only). |

All six packages use `groupId=app.aoki.cinpo` and version `0.5.0`. The marker lets `plugins { id 'app.aoki.cinpo' version '0.5.0' }` resolve the implementation.

## Consumer impact

The source-level API your project uses did not change. The packages `app.aoki.cinpo.apdu.*`, `app.aoki.cinpo.config.*`, `app.aoki.cinpo.orchestrator.*`, `app.aoki.cinpo.task.*`, `app.aoki.cinpo.gp.*`, and `app.aoki.cinpo.util.*` still contain the classes imported by the bundled templates.

### Gradle plugin ID

The Gradle plugin ID changed from `app.aoki.cinpo.gradle` to `app.aoki.cinpo`. Update the `plugins` block:

Before:

```groovy
plugins {
    id 'app.aoki.cinpo.gradle' version '0.5.0'
}
```

After:

```groovy
plugins {
    id 'app.aoki.cinpo' version '0.5.0'
}
```

The implementation artifact is `app.aoki.cinpo:cinpo-gradle-plugin:0.5.0`. The old `app.aoki.cinpo.gradle` marker is not published for 0.5.0. The internal `app.aoki.cinpo.prepare-java-card-tools` helper is applied from the implementation jar; its marker is no longer published, so do not request it from a repository.

What changed is the *dependency declaration* and the *Gradle plugin ID*. Upgrading only the `cinpo` version is not enough: the 0.5.0 artifact is the core only, so you must add the runtime modules you use. The Gradle plugin must also move to 0.5.0 so it can add the annotation processor from the framework artifact. Without a runtime module on `runtimeClasspath`, adapter lookup fails with `Unsupported runtime`. If your project is based on the consumer template, it already declares both:

```groovy
plugins {
    id 'app.aoki.cinpo' version '0.5.0'
}

dependencies {
    implementation 'app.aoki.cinpo:cinpo:0.5.0'
    implementation 'app.aoki.cinpo:cinpo-runtime-jcresim:0.5.0'
    implementation 'app.aoki.cinpo:cinpo-runtime-jcardengine:0.5.0'
    implementation 'app.aoki.cinpo:cinpo-runtime-pcsc:0.5.0'
}
```

If you are hand-editing an existing project rather than regenerating from the template, add the runtime modules your project uses. In particular:

- If you run only `cinpoRun` against the simulator, your project only needs `cinpo` and `cinpo-runtime-jcresim`.
- If you are distributing a binary that provisions physical cards, you only need `cinpo` and `cinpo-runtime-pcsc`.
- If you use `CinpoJarTask` to build a self-contained JAR, its output contains only what you declare on `runtimeClasspath` — nothing else is pulled in silently.
- The Simulator vendor archive is prepared only while you declare `cinpo-runtime-jcresim` in `build.gradle`. If you only use PC/SC, you can omit it — but first remove the `cinpo-runtime-jcresim` line from the starter template's dependencies; keeping it makes the build ask for the Simulator archive even when your active profile uses `pcsc`.
- Profiles are plain YAML files; the framework has no builtin profiles. The CLI defaults `--profile` to `jcdksim`, resolved from `profile/jcdksim.yaml` in the project or on the classpath. The appliance JAR carries the project's profiles, so a bare `write` works without a project directory. If you don't use the simulator, name your own profile — leaving it in place fails at profile or adapter lookup.

See [Modules](../README.md#modules) for the runtime module details.
