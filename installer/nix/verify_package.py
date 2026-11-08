import argparse
import io
import json
import shutil
import subprocess
import tarfile
import zipfile
from pathlib import Path


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def build(root, archive, scheme="git+file"):
    expression = ('let flake = builtins.getFlake ' + json.dumps(("git+file://" if scheme == "git+file" else "path:") + root.as_posix()) + '; in '
                  'flake.packages.x86_64-linux.qtalk.override { appArchive = builtins.path { path = builtins.toPath '
                  + json.dumps(archive.as_posix()) + '; name = "qtalk-app.tar.xz"; }; }')
    return command('nix', 'build', '--impure', '--no-link', '--print-out-paths', '--expr', expression)


def closure(path):
    result = json.loads(command('nix', 'path-info', '--json', '--recursive', '--size', path))
    return {x['path']: x for x in result} if isinstance(result, list) else result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('archive', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    archive = args.archive.resolve()
    package = build(root, archive)
    before = closure(package)
    probe = archive.with_name('app-only-update-probe.tar.xz')
    with tarfile.open(archive) as original, tarfile.open(probe, 'w:xz') as output:
        for info in original.getmembers():
            entry = original.extractfile(info)
            if entry is None:
                raise RuntimeError("Unexpected directory in application bundle")
            data = entry.read()
            if info.name == 'desktopApp.jar':
                stream = io.BytesIO(data)
                with zipfile.ZipFile(stream, 'a') as jar:
                    jar.writestr('META-INF/qtalk-update-probe.txt', 'Packaging cache-reuse test only')
                data = stream.getvalue()
            info.size = len(data)
            output.addfile(info, io.BytesIO(data))
    probe_root = root / 'build/nix-update-probe-source'
    if probe_root.exists():
        shutil.rmtree(probe_root)
    tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
    for name in filter(None, tracked):
        source = root / name
        if source.is_file():
            target = probe_root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)
    application_file = probe_root / 'nix/application.json'
    application = json.loads(application_file.read_text())
    original_version = application['version']
    application['version'] += '-update-probe'
    application_file.write_text(json.dumps(application))
    properties = probe_root / 'gradle.properties'
    properties.write_text(properties.read_text().replace('qtalkVersion=' + original_version,
                                                         'qtalkVersion=' + application['version']))
    updated = build(probe_root, probe, scheme='path')
    after = closure(updated)
    added = set(after) - set(before)
    if added != {updated}:
        raise RuntimeError('An app-only update unexpectedly rebuilt runtime dependencies: ' + repr(added))
    added_bytes = sum(after[path]['narSize'] for path in added)
    if archive.stat().st_size >= 10_000_000 or added_bytes >= 10_000_000:
        raise RuntimeError('App-only update payload unexpectedly exceeds 10 MB')
    report = {'package': package, 'application_archive_bytes': archive.stat().st_size,
              'initial_closure_nar_bytes': sum(x['narSize'] for x in before.values()),
              'app_only_update_new_nar_bytes': added_bytes,
              'unchanged_store_paths_reused': len(set(after) & set(before)),
              'update_probe': 'Application JAR, package version and flake source changed; no runtime dependency paths changed'}
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
