# QTalk on NixOS

Native package for **x86_64 Linux**, including NixOS 26.11. It uses a Nix-provided JDK 21 and pinned native libraries, not a bundled Debian filesystem or `apt`.

## Install and run

```sh
nix profile install github:qvostedev/QTalk#qtalk --refresh
qtalk
```

The profile also provides a desktop launcher and `qtalk:` URI handler. Close an older source-built QTalk instance before starting the installed package. Existing application preferences stay in your user profile.

To try it without installing:

```sh
nix run github:qvostedev/QTalk#qtalk
```

These commands require the `nix-command` and `flakes` experimental features. If they are not enabled, add `--extra-experimental-features 'nix-command flakes'` immediately after `nix`.

## Updates

```sh
nix profile upgrade qtalk --refresh
```

Only the QTalk profile entry is upgraded. The application never invokes `apt`, `pkexec`, `nixos-rebuild`, or an upgrade of every profile package. Its update button explains how to update through Nix instead of downloading an incompatible DEB.

For a declarative NixOS configuration, add this repository as a flake input and include `qtalk.packages.${pkgs.stdenv.hostPlatform.system}.qtalk` in `environment.systemPackages`. Update that input and rebuild your own configuration when upgrading; do not use the profile command for a system-managed installation.

The first installation can still download substantial dependencies. Subsequent application-only updates fetch the small `QTalk-Nix-<version>.tar.xz` application archive; the JDK, Maven artifacts and stripped SDK remain separate store paths. A Nixpkgs, SDK or other library upgrade can require additional downloads. Do not update `flake.lock` merely to change the application version.

## Building a release

On Linux with JDK 21:

```sh
./gradlew -I installer/nix/export-runtime.gradle :desktopApp:exportNixRuntime
python3 installer/nix/prepare_bundle.py --export build/nix-export --output build/nix-release --metadata nix
```

Commit the generated `nix/application.json` and `nix/runtime-lock.json` together with the corresponding release. Upload only `QTalk-Nix-<version>.tar.xz` as the Nix application asset. The flake verifies its hash and the hashes of every separately fetched runtime artifact. The archive is fetched automatically by Nix; it is not a standalone DEB or executable installer.

`Nix package` CI builds the package, checks that changing the application **and the flake source** reuses runtime paths, installs an isolated profile, and opens the GUI under Xvfb. This does not test a real NixOS Wayland session, audio hardware or registered PBX calls.

## По-русски

Установка: `nix profile install github:qvostedev/QTalk#qtalk --refresh`, запуск: `qtalk`.

Обновление: `nix profile upgrade qtalk --refresh`. DEB для NixOS не нужен. Java и библиотеки скачиваются отдельно и повторно используются; первая установка может быть большой, а обновление только приложения — маленьким. Для установки через конфигурацию NixOS обновляй соответствующий flake input и свою конфигурацию.
