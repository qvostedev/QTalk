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
desktopApp/build/compose/binaries/main/exe/QTalk-Setup-1.0.3.exe
```

The task requires Inno Setup 6. It detects a standard system or per-user
installation automatically. A custom compiler location can be supplied through
the `INNO_SETUP_COMPILER` environment variable pointing to `ISCC.exe`.
