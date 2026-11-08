import argparse
import base64
import hashlib
import json
import tarfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--export', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--metadata', type=Path, required=True)
    args = parser.parse_args()
    runtime = json.loads((args.export / 'runtime-lock.json').read_text())
    version = runtime['version']
    args.output.mkdir(parents=True, exist_ok=True)
    args.metadata.mkdir(parents=True, exist_ok=True)
    archive = args.output / f'QTalk-Nix-{version}.tar.xz'
    jars = sorted((args.export / 'app').glob('*.jar'))
    if {p.name for p in jars} != {'desktopApp.jar', 'shared-jvm.jar'}:
        raise RuntimeError('Expected exactly the two application JARs, without runtime dependencies')
    with tarfile.open(archive, 'w:xz', preset=9) as tar:
        for path in jars:
            info = tar.gettarinfo(str(path), arcname=path.name)
            info.uid = info.gid = info.mtime = 0
            info.uname = info.gname = ''
            info.mode = 0o644
            with path.open('rb') as stream:
                tar.addfile(info, stream)
    digest = hashlib.file_digest(archive.open('rb'), 'sha256').digest()
    application = {'version': version, 'name': archive.name,
                   'url': f'https://github.com/qvostedev/QTalk/releases/download/v{version}/{archive.name}',
                   'hash': 'sha256-' + base64.b64encode(digest).decode(),
                   'bytes': archive.stat().st_size}
    (args.metadata / 'application.json').write_text(json.dumps(application, indent=2) + '\n')
    (args.metadata / 'runtime-lock.json').write_text(json.dumps({'dependencies': runtime['dependencies']}, indent=2) + '\n')
    print(json.dumps(application, indent=2))


if __name__ == '__main__':
    main()
