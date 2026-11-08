import argparse
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

NATIVE_DEPENDENCIES = [
    "libc6 (>= 2.39)", "libstdc++6", "libgcc-s1", "libasound2t64", "libpulse0",
    "libgl1", "libegl1", "libopengl0", "libglew2.2", "libx11-6", "libxv1",
    "libxext6", "libxi6", "libxrender1", "libxrandr2", "libxtst6",
    "libfontconfig1", "libfreetype6", "libglib2.0-0t64", "libgtk-3-0t64",
    "pkexec", "xdg-utils", "desktop-file-utils"
]


def update_control(control, version):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Expected a stable numeric QTalk version")
    fields = {}
    for block in re.split(r"\n(?=[A-Za-z][A-Za-z0-9-]*:)", control.strip()):
        key, value = block.split(":", 1)
        fields[key] = value.strip()
    if fields.get("Package") != "qtalk" or fields.get("Architecture") != "amd64":
        raise ValueError("Expected the qtalk amd64 package")
    dependencies = [x.strip() for x in fields.get("Depends", "").split(",") if x.strip()]
    dependencies = [x for x in dependencies if re.split(r"[\s(]", x)[0] != "libc6"]
    names = {re.split(r"[\s(]", x)[0] for x in dependencies}
    for dependency in NATIVE_DEPENDENCIES:
        name = re.split(r"[\s(]", dependency)[0]
        if name not in names:
            dependencies.append(dependency)
            names.add(name)
    fields["Version"] = version + "-1"
    fields["Depends"] = ", ".join(dependencies)
    fields["Recommends"] = ", ".join(filter(None, [fields.get("Recommends", ""), "xwayland"]))
    fields["Maintainer"] = "Qvoste <qvostedev@gmail.com>"
    return "\n".join(key + ": " + value for key, value in fields.items()) + "\n"


def configure_desktop_integration(stage):
    control_directory = stage / "DEBIAN"
    for name in ["preinst", "postinst", "prerm", "postrm"]:
        (control_directory / name).unlink(missing_ok=True)
    script = """#!/bin/sh
set -e
if [ -d /usr/share/applications ] && command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications
fi
exit 0
"""
    for name in ["postinst", "postrm"]:
        path = control_directory / name
        path.write_text(script)
        path.chmod(0o755)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("deb", type=Path)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    args.output.mkdir(parents=True, exist_ok=True)
    output = args.output / f"QTalk_{args.version}-1_amd64.deb"
    with tempfile.TemporaryDirectory(prefix="qtalk-deb-") as temp:
        stage = Path(temp) / "package"
        subprocess.run(["dpkg-deb", "--raw-extract", str(args.deb), str(stage)], check=True)
        launcher = stage / "opt/qtalk/bin/QTalk"
        if not launcher.is_file():
            raise RuntimeError("QTalk launcher missing from /opt/qtalk/bin/QTalk")
        control = stage / "DEBIAN/control"
        control.write_text(update_control(control.read_text(), args.version))
        for relative, source in [("usr/bin/qtalk", "qtalk"),
                                 ("usr/share/applications/qtalk.desktop", "qtalk.desktop")]:
            target = stage / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(root / "installer/linux" / source, target)
            target.chmod(0o755 if relative.startswith("usr/bin/") else 0o644)
        icon = stage / "usr/share/pixmaps/qtalk.png"
        icon.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / "branding/logo.png", icon)
        icon.chmod(0o644)
        subprocess.run(["desktop-file-validate", str(stage / "usr/share/applications/qtalk.desktop")], check=True)
        configure_desktop_integration(stage)
        checksums = []
        for path in sorted(stage.rglob("*")):
            relative = path.relative_to(stage)
            if path.is_file() and not path.is_symlink() and relative.parts[0] != "DEBIAN":
                digest = hashlib.md5(path.read_bytes(), usedforsecurity=False).hexdigest()
                checksums.append(digest + "  " + relative.as_posix())
        (stage / "DEBIAN/md5sums").write_text(chr(10).join(checksums) + chr(10))
        subprocess.run(["dpkg-deb", "--build", "--root-owner-group", str(stage), str(output)], check=True)
    with output.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    report = {"file": output.name, "version": args.version, "architecture": "amd64",
              "sha256": digest, "bytes": output.stat().st_size,
              "source_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()}
    (args.output / "linux-verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
