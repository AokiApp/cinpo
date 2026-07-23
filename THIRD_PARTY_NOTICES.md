# Third-Party Notices

CINPO distributions and CINPO appliance fat JARs may contain third-party software. Third-party
components are licensed by their respective copyright holders under their own terms and are not
covered by the AokiApp Normative Application License - Tight.

## Gradle Wrapper

The Gradle Wrapper scripts and wrapper JAR are provided by the Gradle project under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). Copyright © the original Gradle
authors and contributors.

## GlobalPlatformPro

- Component: GlobalPlatformPro library
- Maven coordinate: `com.github.martinpaljak:globalplatformpro`
- Source and build instructions: <https://github.com/martinpaljak/GlobalPlatformPro>
- Source artifact: `com.github.martinpaljak:globalplatformpro:26.06.05-SNAPSHOT:sources`
- Source artifact repository: <https://mvn.javacard.pro/maven/SNAPSHOTS/>
- License: `LGPL-3.0-or-later` unless an individual source file states another compatible license
- Copyright holders include Martin Paljak, Wojciech Mostowski, Francois Kooman, and other
  contributors identified by the upstream source files.

GlobalPlatformPro is currently resolved transitively through JCardEngine. CINPO appliance fat JARs
contain a flattened copy of the resolved GlobalPlatformPro classes and the GNU GPL version 3 and
GNU LGPL version 3 license texts below `META-INF/cinpo/`. When conveying an appliance fat JAR,
obtain and provide the source artifact corresponding to the packaged GlobalPlatformPro binary.
Also provide the appliance project source and Gradle build files in a form that permits the
recipient to replace GlobalPlatformPro with an interface-compatible modified build and run
`./gradlew cinpoJar`. GlobalPlatformPro remains governed by its upstream license.

## Limited relinking permission

For an LGPL-covered component only, AokiApp grants lawful recipients the additional permission to
modify that component, reverse engineer the combined work solely to debug such modifications, and
modify CINPO only as needed to relink it with an interface-compatible modified component. This does
not otherwise modify the ANAL-Tight terms or license CINPO under the GNU GPL or GNU LGPL.
