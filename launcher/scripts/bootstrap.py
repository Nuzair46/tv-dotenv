#!/usr/bin/env python3
"""Fetch pinned official Linux x86-64 build tools into this project only."""
import hashlib, pathlib, platform, subprocess, tarfile, zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
TOOLCHAIN=ROOT/'toolchain'
SOURCES=[
    ('jdk.tar.gz','https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz','ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94','jdk-21.0.12.1+1/bin/java',''),
    ('platform.zip','https://dl.google.com/android/repository/platform-34-ext12_r01.zip','b8349ff89d0bc40d26c712dcc5eb36ee60f40e203d7589bd4599b9237e98329a','android-34-ext12/android.jar',''),
    ('build-tools.zip','https://dl.google.com/android/repository/build-tools_r36_linux.zip','5d9ac77fb6ff43d9da518a337b4fcf8f9097113df531d99ccefe80ef7ce8250b','build-tools/android-16/aapt2','build-tools'),
]
def main():
    assert platform.system()=='Linux' and platform.machine()=='x86_64','Pinned tools are Linux x86-64 only'
    TOOLCHAIN.mkdir(exist_ok=True);cache=TOOLCHAIN/'downloads';cache.mkdir(exist_ok=True)
    for name,url,digest,marker,folder in SOURCES:
        if (TOOLCHAIN/marker).exists():print('Present:',marker);continue
        path=cache/name
        if not path.exists():subprocess.run(['curl','--fail','--location','--retry','2','--proto','=https','--output',str(path),url],check=True)
        assert hashlib.sha256(path.read_bytes()).hexdigest()==digest,'Hash mismatch: '+name
        dest=TOOLCHAIN/folder;dest.mkdir(exist_ok=True)
        if name.endswith('.tar.gz'):
            with tarfile.open(path) as archive:archive.extractall(dest,filter='data')
        else:
            with zipfile.ZipFile(path) as archive:
                for entry in archive.infolist():
                    assert (dest/entry.filename).resolve().is_relative_to(dest.resolve()),'Unsafe archive path'
                archive.extractall(dest)
                for entry in archive.infolist():
                    if entry.external_attr>>16 & 0o111:(dest/entry.filename).chmod(0o755)
        assert (TOOLCHAIN/marker).exists(),'Unexpected archive layout: '+name
        print('Verified and installed:',marker)
if __name__=='__main__':main()
