import argparse
import re
import subprocess
import tempfile
import zipfile
from pathlib import Path


def exported_symbols(path):
    result = subprocess.check_output(['nm', '-D', '--defined-only', '--format=posix', str(path)], text=True)
    return sorted(line.split()[0] for line in result.splitlines() if line.split())


def strip_sdk(source, destination):
    count = 0
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as output:
        if any(re.fullmatch(r'META-INF/[^/]+\.(SF|RSA|DSA|EC)', n, re.IGNORECASE) for n in original.namelist()):
            raise RuntimeError('Refusing to invalidate a signed SDK JAR')
        with tempfile.TemporaryDirectory(prefix='qtalk-strip-') as temporary:
            library = Path(temporary) / 'library.so'
            for entry in original.infolist():
                data = original.read(entry.filename)
                if not entry.is_dir() and '.so' in Path(entry.filename).name and data.startswith(b'\x7fELF'):
                    library.write_bytes(data)
                    before = exported_symbols(library)
                    subprocess.run(['strip', '--strip-debug', str(library)], check=True)
                    if exported_symbols(library) != before:
                        raise RuntimeError('Stripping changed exported symbols: ' + entry.filename)
                    data = library.read_bytes()
                    count += 1
                output.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    if count == 0:
        raise RuntimeError('No Linux native libraries found in SDK JAR')
    with zipfile.ZipFile(destination) as archive:
        if archive.testzip() is not None:
            raise RuntimeError('Stripped SDK JAR failed CRC verification')
    print(f'SDK JAR: {source.stat().st_size} -> {destination.stat().st_size} bytes; {count} native libraries; exported symbols preserved')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    strip_sdk(args.source, args.destination)
