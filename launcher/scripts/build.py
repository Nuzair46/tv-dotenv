#!/usr/bin/env python3
"""Build a dependency-free Android APK with the official Android SDK + Temurin JDK."""
from pathlib import Path
import hashlib, subprocess, zipfile, secrets
ROOT=Path(__file__).resolve().parents[1]
SDK=ROOT/'toolchain/android-34-ext12/android.jar'
BT=ROOT/'toolchain/build-tools/android-16'
JAVA=ROOT/'toolchain/jdk-21.0.12.1+1/bin'
BUILD=ROOT/'build'
def run(args):
    subprocess.run([str(a) for a in args],check=True,cwd=ROOT)
def main():
    for d in ['classes','generated','dex']: (BUILD/d).mkdir(parents=True,exist_ok=True)
    # A stale class can survive a renamed source file; rebuild classes and dex from scratch.
    for directory in ['classes','generated','dex']:
        for path in (BUILD/directory).rglob('*'):
            if path.is_file():path.unlink()
    video=ROOT/'app/src/main/res/raw/neural.mp4'
    assert hashlib.sha256(video.read_bytes()).hexdigest()=='8076dc4b05856ae138b4b1b201ec5aeca83d1641c9e0c47838207c70a2cc9e03'
    run([BT/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip'])
    run([BT/'aapt2','link','-o',BUILD/'unsigned.apk','--manifest',ROOT/'app/src/main/AndroidManifest.xml','-I',SDK,'--java',BUILD/'generated','--auto-add-overlay','-0','mp4',BUILD/'resources.zip'])
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted((BUILD/'generated').rglob('*.java'))
    run([JAVA/'javac','-source','8','-target','8','-Xlint:-options','-encoding','UTF-8','-classpath',SDK,'-d',BUILD/'classes',*sources])
    run([JAVA/'java','-cp',BT/'lib/d8.jar','com.android.tools.r8.D8','--release','--lib',SDK,'--min-api','28','--output',BUILD/'dex',*sorted((BUILD/'classes').rglob('*.class'))])
    with zipfile.ZipFile(BUILD/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as apk:
        for file in (BUILD/'dex').glob('*.dex'):apk.write(file,file.name)
    key=ROOT/'nuzair-home.jks';password=ROOT/'.signing-password'
    if not key.exists():
        password.write_text(secrets.token_hex(24));password.chmod(0o600)
        run([JAVA/'keytool','-genkeypair','-keystore',key,'-storepass:file',password,'-keypass:file',password,'-alias','nuzair-home','-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Nuzair Home, OU=Local TV Launcher'])
        key.chmod(0o600)
    run([BT/'zipalign','-f','-p','4',BUILD/'unsigned.apk',BUILD/'aligned.apk'])
    run([JAVA/'java','-jar',BT/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','file:'+str(password),'--out',BUILD/'nuzair-home.apk',BUILD/'aligned.apk'])
    run([JAVA/'java','-jar',BT/'lib/apksigner.jar','verify','--verbose',BUILD/'nuzair-home.apk'])
    final=BUILD/'nuzair-home.apk';digest=hashlib.sha256(final.read_bytes()).hexdigest();(BUILD/'nuzair-home.apk.sha256').write_text(digest+'  nuzair-home.apk\n')
    print(f'Built {final}: {final.stat().st_size:,} bytes; SHA256 {digest}')
if __name__=='__main__':main()
