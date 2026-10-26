# QTalk Windows installer

The branded Windows installer is built by the Gradle task:

```text
:desktopApp:packageQTalkInstaller
```

In IntelliJ IDEA, open the Gradle tool window and run:

```text
QTalk → desktopApp → Tasks → distribution → packageQTalkInstaller
```

The resulting installer is written to:

```text
desktopApp/build/compose/binaries/main/exe/QTalk-Setup-1.1.1.exe
```

The task requires Inno Setup 6. It detects a standard system or per-user
installation automatically. A custom compiler location can be supplied through
the `INNO_SETUP_COMPILER` environment variable pointing to `ISCC.exe`.

## GitHub Actions

Run the **Windows installer** workflow on the release branch or tag. It builds and tests the Windows installer, checks native libraries, and uploads the EXE as the `QTalk-Windows-installer` artifact. Pushing a version tag (`v*`) runs the same checks and publishes the installer with release notes from `docs/releases/<version>.md`. Manual workflow runs only build the artifact.
