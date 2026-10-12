#!/usr/bin/env python3
"""Check ELF LOAD alignment and RELRO rounding for packaged 64-bit libraries.

Run after apksigner and zipalign; those checks do not inspect ELF segments.
"""
import json
import struct
import sys
from zipfile import ZipFile


def inspect(path):
    libraries = []
    with ZipFile(path) as archive:
        for name in sorted(archive.namelist()):
            if not name.startswith("lib/") or not name.endswith(".so"):
                continue
            data = archive.read(name)
            if len(data) < 64 or data[:4] != b"\x7fELF":
                raise ValueError(f"Invalid ELF: {name}")
            bits = {1: 32, 2: 64}[data[4]]
            order = {1: "<", 2: ">"}[data[5]]
            if bits == 64:
                offset = struct.unpack_from(order + "Q", data, 32)[0]
                stride, count = struct.unpack_from(order + "HH", data, 54)
                alignment_offset, alignment_format = 48, "Q"
            else:
                offset = struct.unpack_from(order + "I", data, 28)[0]
                stride, count = struct.unpack_from(order + "HH", data, 42)
                alignment_offset, alignment_format = 28, "I"
            alignments, writable, relro = [], [], []
            for index in range(count):
                start = offset + index * stride
                kind = struct.unpack_from(order + "I", data, start)[0]
                if kind == 1:
                    alignments.append(struct.unpack_from(order + alignment_format, data, start + alignment_offset)[0])
                if bits == 64:
                    flags = struct.unpack_from(order + "I", data, start + 4)[0]
                    address = struct.unpack_from(order + "Q", data, start + 16)[0]
                    size = struct.unpack_from(order + "Q", data, start + 40)[0]
                    if kind == 1 and flags & 2:
                        writable.append((address, address + size))
                    if kind == 0x6474E552:
                        relro.append((address, address + size))
            if not alignments:
                raise ValueError(f"Missing LOAD segments: {name}")
            if bits == 64 and any(n < 16384 or n % 16384 for n in alignments):
                raise ValueError(f"64-bit library is not 16 KiB aligned: {name}: {alignments}")
            # Android protects every page touched by RELRO. A nonaligned end is
            # safe when it ends a LOAD segment and the padding contains no RW
            # data. Check actual ranges rather than rejecting all such suffixes.
            # See platform/bionic linker/linker_phdr.cpp, GNU RELRO protection.
            for begin, end in relro:
                rounded = (begin // 16384 * 16384, (end + 16383) // 16384 * 16384)
                for left, right in writable:
                    cursor, stop = max(left, rounded[0]), min(right, rounded[1])
                    for rel_begin, rel_end in sorted(relro):
                        if rel_begin <= cursor:
                            cursor = max(cursor, rel_end)
                    if cursor < stop:
                        raise ValueError(f"RELRO rounding protects writable data in {name}")
            libraries.append({"file": name, "bits": bits, "load_alignment": alignments,
                              "relro_ranges": relro, "relro_rounding": "pass" if bits == 64 else "not applicable"})
    return {"status": "pass", "libraries": libraries}


if __name__ == "__main__":
    print(json.dumps(inspect(sys.argv[1]), indent=2))
