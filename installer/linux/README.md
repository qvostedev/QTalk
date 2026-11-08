# Linux DEB

Supported package targets: **Ubuntu 24.04 and Debian 13, amd64**. The bundled Linphone SDK targets Ubuntu 24.04; older glibc-based distributions are not supported by this package. Other Linux distributions can use the source/Nix build.

## Install

Download the `QTalk_<version>-1_amd64.deb` asset from the release, then install the downloaded file with APT:

```bash
sudo apt install ./QTalk_*_amd64.deb
qtalk
```

Java is bundled. APT resolves native graphics/audio libraries and `pkexec`, which the existing in-app updater uses for Debian-package installation. A normal desktop session with a PolicyKit authentication agent is needed for graphical update authorization.

The package adds an application-menu entry, icon, the `qtalk` command and the `qtalk:` URI handler. On Wayland the launcher sets the existing XWayland compatibility hint. It does not modify SIP credentials, application preferences or user audio settings.

## Build

On Ubuntu 24.04 amd64, install JDK 21, Python 3, `fakeroot`, `desktop-file-utils` and the native dependencies listed in `package_deb.py`, then run:

```bash
./gradlew :desktopApp:packageQTalkDeb --no-configuration-cache
```

The final package is in `desktopApp/build/compose/binaries/main/deb-release/`. Its public asset name keeps the `QTalk_` prefix expected by existing Linux updaters.

The Linux DEB GitHub Actions workflow runs JVM tests under Xvfb, creates the DEB, installs it in clean Ubuntu 24.04 and Debian 13 containers, and checks that the packaged application opens a window. Tag builds attach only the DEB to the matching release after the Windows publishing workflow creates it. A manual build may attach the package to an existing same-version release only when production Kotlin sources match that tag.

Container/native tests do not verify physical microphones, speakers, a user's Wayland compositor or real multi-PC PBX calls.
