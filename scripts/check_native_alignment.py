"""Check every packaged 64-bit ELF LOAD segment for 16 KB compatibility.
Usage: python scripts/check_native_alignment.py path/to/app.aab (or .apk).
This static check does not replace testing on a 16 KB device.
"""
import struct
import sys
import zipfile


def check(path):
    failed = []
    checked = 0
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith('.so') or not any('/' + abi + '/' in name for abi in ('arm64-v8a', 'x86_64')):
                continue
            data = archive.read(name)
            if data[:5] != b'\x7fELF\x02':
                failed.append(name + ': expected ELF64')
                continue
            endian = '<' if data[5] == 1 else '>'
            offset = struct.unpack_from(endian + 'Q', data, 32)[0]
            entry_size, count = struct.unpack_from(endian + 'HH', data, 54)
            for index in range(count):
                kind, flags, file_offset, virtual, physical, file_size, memory_size, alignment = struct.unpack_from(endian + 'IIQQQQQQ', data, offset + index * entry_size)
                if kind == 1 and (alignment < 16384 or (virtual - file_offset) % 16384):
                    failed.append(name + ': LOAD segment not 16 KB aligned')
            checked += 1
    if not checked:
        raise SystemExit('No 64-bit native libraries found; inspect package contents.')
    if failed:
        raise SystemExit('\n'.join(sorted(set(failed))))
    print(f'PASS: {checked} packaged 64-bit native libraries have 16 KB-compatible LOAD segments.')


if __name__ == '__main__':
    check(sys.argv[1])
