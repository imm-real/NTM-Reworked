# HBM NTM for NeoForge 1.21.1

This is me trying to drag HBM's Nuclear Tech Mod from Forge 1.7.10 into
NeoForge 1.21.1, without turning it into a whole different mod on the way.

Its not finished and its definitely not stable yet. Dont put it in a world you
care about unless you got backups, seriously.

Alot works already though, ores and materials, radiation, HE power, fluids,
conveyors, a pile of production machines, several reactors, the DFC, FEnSU,
foundry stuff, bombs, guns and enough nuclear explosions to make testing
terrain temporary. Theres still plenty of dependency chains and late game
machines missing. Also if something shows up in the creative menu that doesnt
automatically mean all of it works yet.

## Building it

You need JDK 21, the gradle wrapper handles the rest.

```sh
./gradlew clean build
```

Jar will be in `build/libs/`.

If you changed datagen stuff then run this first:

```sh
./gradlew runData
./gradlew clean build
```

For running it in dev:

```sh
./gradlew runClient
./gradlew runServer
./gradlew runGameTestServer
```

GameTest uses the `hbm` namespace and exits when its done. Client, server and
GameTest also keep their mess in seperate directories under `run/`.

## About the one giant commit

The old commit history was broken and pretty useless if you wanted to follow
what happened, so I wiped it before putting this up. This repo starts from the
current working tree and commits after this should be normal again.

## The actual HBM NTM

The actual 1.7.10 project is here:
[HbmMods/Hbm-s-Nuclear-Tech-GIT](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT).
That code is what im using as the reference for behavior, models and general
machine nonsense. All credit for the original mod goes to HbMinecraft and the
HBM NTM contributors.

This isnt an official upstream build. If this port breaks then report it here,
not on the 1.7.10 issue tracker. They got enough fires of their own.

## License

The mod metadata says GPL-3.0-or-later. The GPL and LGPL texts included with the
project are in [LICENSE](LICENSE) and [LICENSE.LESSER](LICENSE.LESSER).
