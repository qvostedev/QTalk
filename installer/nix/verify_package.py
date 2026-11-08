import argparse
import io
import json
import subprocess
import tarfile
import zipfile
from pathlib import Path


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def build(root, archive):
    expression = ('let flake = builtins.getFlake ' + json.dumps('git+file://' + root.as_posix()) + '; in '
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
    updated = build(root, probe)
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
              'update_probe': 'Only application JAR content changed; no runtime dependency paths changed'}
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
