# Migrating to CINPO 0.5

CINPO 0.5 splits the framework artifact into several Maven modules so that you only pull in the runtimes you use, changes the Gradle plugin ID, and names the Oracle JCDK simulator runtime `jcdksim`. This note documents what changed and what you need to do.

## Simulator runtime: `jcdksim`

The Oracle JCDK simulator uses the name `jcdksim` across the profile, the runtime identifier, the Maven artifact, the Java package, and the Gradle task.

| Part | Value |
|------|-------|
| Maven artifact | `app.aoki.cinpo:cinpo-runtime-jcdksim` |
| Runtime identifier | `jcdksim` |
| Java package | `app.aoki.cinpo.runtime.jcdksim` |
| Runtime adapter | `JcdkSimRuntimeAdapter` |
| Gradle task | `prepareJcdksimSimulator` |

The profile file `profile/jcdksim.yaml`, its `name: jcdksim`, and the CLI default `--profile jcdksim` use the same name.

## What changed at the publisher side

The artifacts use `groupId=app.aoki.cinpo` and the same version.

| Artifact | Role |
|---------|------|
| `app.aoki.cinpo:cinpo:<version>` | Framework core (CLI, orchestrator, manifest, GP / SCP crypto, APDU I/O, task loader). |
| `app.aoki.cinpo:cinpo-runtime-jcdksim:<version>` | Oracle JCDK simulator runtime. |
| `app.aoki.cinpo:cinpo-runtime-jcardengine:<version>` | In-process Java Card engine runtime. |
| `app.aoki.cinpo:cinpo-runtime-pcsc:<version>` | PC/SC runtime (no native dependencies). |
| `app.aoki.cinpo:cinpo-gradle-plugin:<version>` | Gradle plugin implementation. |
| `app.aoki.cinpo:app.aoki.cinpo.gradle.plugin:<version>` | Gradle plugin marker (POM only). |

The marker lets `plugins { id 'app.aoki.cinpo' version '<version>' }` resolve the implementation.

## Consumer impact

The source-level API your project uses in the core packages did not change. The packages `app.aoki.cinpo.apdu.*`, `app.aoki.cinpo.config.*`, `app.aoki.cinpo.orchestrator.*`, `app.aoki.cinpo.task.*`, `app.aoki.cinpo.gp.*`, and `app.aoki.cinpo.util.*` still contain the classes imported by the bundled templates.

### Gradle plugin ID

The Gradle plugin ID changed from `app.aoki.cinpo.gradle` to `app.aoki.cinpo`. Update the `plugins` block:

Before:

```groovy
plugins {
    id 'app.aoki.cinpo.gradle' version '<version>'
}
```

After:

```groovy
plugins {
    id 'app.aoki.cinpo' version '<version>'
}
```

The implementation artifact is `app.aoki.cinpo:cinpo-gradle-plugin:<version>`. The old `app.aoki.cinpo.gradle` marker and the internal `app.aoki.cinpo.prepare-java-card-tools` marker are not published; the helper is applied from the implementation jar.

What changed is the *dependency declaration* and the *Gradle plugin ID*. Upgrading only the `cinpo` version is not enough: the `cinpo` artifact is the core only, so you must add the runtime modules you use. The Gradle plugin must also move to the same version so it can add the annotation processor from the framework artifact.

Without a runtime module on `runtimeClasspath`, adapter lookup fails with `Unsupported runtime`. If your project is based on the consumer template, it already declares the plugin and the runtime modules:

```groovy
dependencies {
    implementation 'app.aoki.cinpo:cinpo:<version>'
    implementation 'app.aoki.cinpo:cinpo-runtime-jcdksim:<version>'
    implementation 'app.aoki.cinpo:cinpo-runtime-jcardengine:<version>'
    implementation 'app.aoki.cinpo:cinpo-runtime-pcsc:<version>'
}
```

If you are hand-editing an existing project rather than regenerating from the template, add the runtime modules your project uses. In particular:

- If you run only `cinpoRun` against the simulator, your project only needs `cinpo` and `cinpo-runtime-jcdksim`.
- If you are distributing a binary that provisions physical cards, you only need `cinpo` and `cinpo-runtime-pcsc`.
- If you use `CinpoJarTask` to build a self-contained JAR, its output contains only what you declare on `runtimeClasspath` — nothing else is pulled in silently.
- The Simulator vendor archive is prepared only while you declare `cinpo-runtime-jcdksim` in `build.gradle`. If you only use PC/SC, you can omit it — but first remove the `cinpo-runtime-jcdksim` line from the starter template's dependencies; keeping it makes the build ask for the Simulator archive even when your active profile uses `pcsc`.
- Profiles are plain YAML files; the framework has no builtin profiles. The CLI defaults `--profile` to `jcdksim`, resolved from `profile/jcdksim.yaml` in the project or on the classpath. The appliance JAR carries the project's profiles, so a bare `write` works without a project directory. If you don't use the simulator, name your own profile — leaving it in place fails at profile or adapter lookup.

See the artifact table above for the runtime module details.
