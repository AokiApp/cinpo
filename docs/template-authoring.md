# Authoring and maintaining templates

A template is a compilable, testable starter project plus the documentation that ships with it. `scripts/bootstrap.sh` copies the whole directory into a new project, so every path and example in `README.md` and `AGENTS.md` must make sense from the generated project's root — not from the CINPO repository.

## What each template proves

| Template | Contract | Specialty |
|---|---|---|
| `basic` | An applet stores an X.509 certificate; `provision` writes it, `test` reads it back and parses it. | Development loop, certificate coupling, first real change. |
| `authenticated` | The applet uses the GlobalPlatform on-card API and secure messaging; `test` authenticates an SCP session and checks both the secure and unsecure paths. | Companion bundle setup, SCP03, negative security test. |
| `probe` | A host task selects an application and reads card data without writing to the card. | PC/SC profiles, `TaskArguments`, read-only safety. |

A template is not a framework manual. Framework details belong in [`tasks.md`](tasks.md), [`manifest.md`](manifest.md), [`profiles.md`](profiles.md), and [`vendor.md`](vendor.md); templates link to them.

## Required files

```
template/<name>/
├── applet/                 # Java Card sources; compiled by the CINPO plugin
├── host/                   # host-side tasks; ordinary Java 21
├── manifest.yaml           # package, AIDs, SDK versions, companion bundles
├── profile/jcdksim.yaml    # simulator profile with development-only keys
├── build.gradle            # CINPO plugin, dependencies, source sets
├── settings.gradle         # plugin repositories and rootProject.name
├── README.md               # first run and change map
└── AGENTS.md               # invariants and decision procedure for AI agents
```

`scripts/bootstrap.sh` adds the Gradle wrapper and rewrites `rootProject.name`. Do not document wrapper paths that assume a monorepo checkout: the generated project runs `./gradlew` from its own root.

Template docs must tell users how to obtain vendor files from the vendor's site. Do not make a generator rely on a repository-internal `vendor.zip`, a password, or a relative `../scripts/...` path; a generated project does not have them.

## README skeleton

Keep the order. A reader should be able to stop after "Run it" and still have a working project.

```markdown
# <Project name>

What this project is, what CINPO is, and a link to the CINPO repository and docs.

## What you get
The files that matter and the flow they implement.

## Requirements
JDK 21, git, Oracle JCDK archives, companion bundles if any.

## Layout
A short tree.

## Run it
The three commands a new user needs.

## Development loop
Full run vs host-only run, `--debug`, and the failure ladder.

## Where to change what
Goal → files → command → test.

## Testing and debugging
One positive and one negative test shape; how to read a status word.

## Make it yours
Sample identifiers, rename checklist, AID rules, certificate flow, real-card profile.

## Documentation
Links to the CINPO docs.
```

Code blocks in the README must be copy-pasteable from the generated project root. Use `./gradlew` and `.\gradlew.bat`, not `../gradlew`.

## AGENTS skeleton

AGENTS is a contract, not a notebook. Keep it short enough that an agent reads all of it.

```markdown
# AGENTS.md

Rules for agents (and humans) working in this project. Follow them literally.

## What this project is
## The two worlds
## Before you change
## Commands and how a run works
## The applet at a glance
## How to change things
## The host tasks at a glance
## Hard rules
## Do not
## Definition of done
## Where new knowledge goes
## Project-specific notes
```

The last section is deliberately empty in the template. Generated projects add local rules there instead of editing the template sections above. This keeps template updates mergeable and stops project-specific incidents from becoming universal rules.

## Sample identifiers

Keep placeholders in one table and use generic wording ("the applet", "the sample task") elsewhere. Concrete names belong in code blocks and in the table, not in every paragraph.

| Placeholder | Where it appears |
|---|---|
| `com.example.cinpo.hello` | applet package, `manifest.yaml` |
| `HelloApplet` | applet class, host comments, README |
| `F00000000101` | `manifest.yaml` AIDs |
| `CN=CINPO Template Leaf` | issuer and test assertion |
| `CN=CINPO Template Test CA` | issuer |

After renaming, run:

```bash
grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
  -e 'HelloApplet' -e 'com.example.cinpo.hello' -e 'F00000000101' .
```

Every remaining hit is either a file that still needs the rename or the sample-identifier table.

## Verification

Run these from the CINPO repository root. Use a temporary directory so an existing project is never touched.

```bash
tmp="$(mktemp -d)"
./scripts/bootstrap.sh --template basic "$tmp/project"
cp -a vendor "$tmp/project/vendor"          # after ./scripts/extract_vendor.sh
cd "$tmp/project"
./gradlew --no-daemon cinpoRun --args='--debug --test'
./gradlew --no-daemon cinpoJar
java -jar build/libs/cinpo-appliance.jar write --test
```

For `authenticated`, stage the companion bundle as described in the template's README before `cinpoRun`.

For `probe`, the default kind is `provision`, which the template does not define; run the probe kind explicitly:

```bash
./gradlew cinpoRun --args='--task=probe'
```

Also run the Windows path (`scripts/bootstrap.ps1`, `.\gradlew.bat`) when the template changes. The simulator executable is OS-specific (`jcsl` on Linux, `jcsw.exe` on Windows); a Linux-only check does not prove the Windows flow.

### Documentation checks

At minimum, check that template docs contain no repository-internal or absolute paths:

```bash
grep -RIn --exclude-dir=.git --exclude-dir=build --exclude-dir=vendor \
  -e '/home/' -e '\.\./gradlew' -e 'template/README' template/
```

Then check every relative link and every command against a freshly bootstrapped project. A command that was never run is a hypothesis, not documentation.

## Adding a template

1. Copy `template/basic` to `template/<name>`.
2. Change the applet, host tasks, `manifest.yaml`, and `profile/`. Keep the sample flow runnable.
3. Write `README.md` and `AGENTS.md` from the skeletons above. Keep the sample identifiers isolated.
4. If the applet needs a companion bundle, document a project-local staging step. A script is optional; do not make the project depend on repository-internal files or on `vendor.zip`.
5. Add the template to the table in the root [`README.md`](../README.md).
6. Add it to `.github/workflows/verify-template.yml`.
7. Run the verification checklist on Linux and Windows.
8. Run the rename smoke test: bootstrap, rename the sample package/class/AIDs, and confirm the old names are gone.

## Changing an existing template

- Change code and documentation in the same pull request.
- When a contract changes (CLA, INS, AID, certificate subject, chunk size), change the test first and let the failing test point at the producer.
- When a command or path changes, grep for the old one across `template/`.
- When a doc claim cannot be verified, say so; do not write it as fact.
- Remove the statement the new one replaces. Do not add a second warning next to the old one.

## Where new knowledge goes

| Kind of knowledge | Home | Shape |
|---|---|---|
| Invariant for every template project | `AGENTS.md` | one rule, linked to a check |
| Procedure or workflow | `README.md` | numbered steps with commands |
| API or specification detail | `docs/` | reference plus example |
| Fact specific to one generated project | `## Project-specific notes` in that project's `AGENTS.md` | names, paths, local setup |
| A mistake that can be reproduced | a test or CI check | assertion and failure message |
| A one-off incident | commit message or issue | not a rule |

Promotion order: reproduce it, encode it as a check, write the smallest example, and only then add a rule if it is universal. When you add a rule, remove or merge one. `must` means enforced by code or a test; `conventionally` means recommended, not enforced.

## Review checklist

- [ ] A fresh bootstrap reaches `--test` with only the documented setup.
- [ ] The same commands work on Windows.
- [ ] `cinpoJar` produces a runnable appliance.
- [ ] README commands are copy-pasteable from the project root.
- [ ] AGENTS contains no stale sample names or broken links.
- [ ] Every relative link resolves.
- [ ] No secrets, absolute paths, or repository-internal paths.
- [ ] Companion bundles are fetched by a documented, project-local step.
- [ ] The root README template table and CI matrix include the template.
- [ ] The sample identifiers are isolated and a rename leaves no stale names.
