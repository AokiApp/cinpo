# Changelog

## [0.5.3](https://github.com/AokiApp/cinpo/compare/v0.5.2...v0.5.3) (2026-11-21)


### Bug Fixes

* **gradle-plugin:** scope signature exclusion to META-INF ([ef95b71](https://github.com/AokiApp/cinpo/commit/ef95b71ac2e266fc18d4aa89589cdf4567e2d358))
* **gradle-plugin:** scope signature metadata exclusion to META-INF ([53e66cd](https://github.com/AokiApp/cinpo/commit/53e66cdcedff5c634fadc91c7fd16903ded2cd3f))

## [0.5.2](https://github.com/AokiApp/cinpo/compare/v0.5.1...v0.5.2) (2026-11-21)


### Features

* **templates:** download template archives in bootstrap ([2430031](https://github.com/AokiApp/cinpo/commit/24300311503c377180656216a86103b0091290d4))
* **templates:** download template archives in bootstrap ([5c13aed](https://github.com/AokiApp/cinpo/commit/5c13aed48afacc27ce177f886bdcbcd2285fde43))


### Bug Fixes

* **gradle-plugin:** drop EC signature files from the appliance ([394e266](https://github.com/AokiApp/cinpo/commit/394e2669d2108e37b403f188171f6e1f53b62817))
* **gradle-plugin:** drop EC signature files from the appliance ([8c2a9eb](https://github.com/AokiApp/cinpo/commit/8c2a9ebe3d60a88f92ad57a7f1b6bc1ff37cb0ad))
* **gradle-plugin:** isolate Java Card tool loading and clean up generated outputs ([3ef9dc9](https://github.com/AokiApp/cinpo/commit/3ef9dc9ead870e90c1ee355f0411ecd1b30f3677))
* **gradle-plugin:** isolate Java Card tools and declare generated outputs ([cd9f2f8](https://github.com/AokiApp/cinpo/commit/cd9f2f85a3e0d413717c545d1f20227ae2cb4f14))
* **gradle-plugin:** keep applet task metadata and resources out of the appliance ([a335f60](https://github.com/AokiApp/cinpo/commit/a335f605b5334d4aabe77fd586180b12c32531a4))
* **gradle-plugin:** keep service names and source-set runtime classpath entries ([7098087](https://github.com/AokiApp/cinpo/commit/7098087c2b5adbee527551caaf2b6f71e1eedeff))
* **gradle-plugin:** make simulator preparation configuration-cache compatible ([eb65036](https://github.com/AokiApp/cinpo/commit/eb65036a36f96ba359f115498707a506c20e5241))
* **gradle-plugin:** read the cinpoRun working directory from a property ([b128d36](https://github.com/AokiApp/cinpo/commit/b128d36697980ec19f2995dde98d30733e8929a1))
* **gradle-plugin:** read the cinpoRun working directory from a property ([df9f115](https://github.com/AokiApp/cinpo/commit/df9f115333bc727af9fe3d5afdd29218ee559f12))
* **gradle-plugin:** support the configuration cache ([18bda1f](https://github.com/AokiApp/cinpo/commit/18bda1fae23adc17bc6d578b738b096225dbc948))
* **gradle-plugin:** validate service entries and restore classpath coverage ([47e5fa9](https://github.com/AokiApp/cinpo/commit/47e5fa9348cc4d1fb9465a94dd76ae1e40f715ec))
* **probe-template:** run cinpoTest with the probe task kind ([81e966d](https://github.com/AokiApp/cinpo/commit/81e966d99026ed1ad6805c32a1ee4cde5e08752b))
* **probe-template:** run cinpoTest with the probe task kind ([31c5d55](https://github.com/AokiApp/cinpo/commit/31c5d55f3acd95d1a8237548e85f84617e6525d1))


### Reverts

* keep the probe template unchanged in this PR ([102fc38](https://github.com/AokiApp/cinpo/commit/102fc38dbcedb03c6f527bc7a8543acd18605080))


### Code Refactoring

* unify the Oracle simulator runtime on jcdksim ([58abdcf](https://github.com/AokiApp/cinpo/commit/58abdcfac4a8ccc3c0e3d03761c9f68bd4c98788))

## [0.5.1](https://github.com/AokiApp/cinpo/compare/v0.5.0...v0.5.1) (2026-11-19)


### Bug Fixes

* keep Release Please out of tagging ([e462291](https://github.com/AokiApp/cinpo/commit/e462291646e7b7b05a0722855881e0b7c8035a15))
* keep Release Please out of tagging ([bbaeb63](https://github.com/AokiApp/cinpo/commit/bbaeb63e57803784197add70c94c8987c1d06df4))
* scope template repositories to CINPO artifacts ([072c076](https://github.com/AokiApp/cinpo/commit/072c076b5e14fe6fe1ec7301ca21318758a56147))
