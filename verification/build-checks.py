"""Run host checks and build a separate, locally signed native-check APK.

No installation is performed. Requires a completed SDK :tool:assembleDebug build.
The signing key stays outside the source tree and is supplied by the caller.
"""
import os
import subprocess
import zipfile
from pathlib import Path

source = Path(__file__).resolve().parent
sdk_project = Path(os.environ.get("LIGHT_TICKETS_SDK_PROJECT", source.parent)).resolve()
java = Path(os.environ["JAVA_HOME"]) / "bin"
android = Path(os.environ["ANDROID_HOME"])
cache = Path(os.environ["GRADLE_USER_HOME"])
key = Path(os.environ["LIGHT_TICKETS_KEY_FILE"])
key_alias = os.environ["LIGHT_TICKETS_KEY_ALIAS"]
for name in ("LIGHT_TICKETS_KEYSTORE_PASSWORD", "LIGHT_TICKETS_KEY_PASSWORD"):
    if not os.environ.get(name):
        raise SystemExit(f"Set {name}; signing passwords are never stored in this script")
build = Path(os.environ["LIGHT_TICKETS_CHECK_DIR"])
if build.exists() and any(build.iterdir()):
    raise SystemExit("Choose an empty, disposable LIGHT_TICKETS_CHECK_DIR")
stdlib = list((cache / "caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.3.20").rglob("*.jar"))
assert len(stdlib) == 1, "Build the pinned SDK first to resolve its Kotlin runtime"
classes = sdk_project / "tool/build/tmp/kotlin-classes/debug"
assert (classes / "dev/steelthread/lighttickets/TicketStore.class").is_file()
platform = android / "platforms/android-36/android.jar"
tools = android / "build-tools/36.0.0"
checks = build / "classes"
dex = build / "dex"
checks.mkdir(parents=True, exist_ok=True)
dex.mkdir(parents=True, exist_ok=True)

def run(*args):
    subprocess.run([str(arg) for arg in args], check=True)

classpath = os.pathsep.join(map(str, [platform, classes, stdlib[0]]))
run(java / "javac", "--release", "17", "-cp", classpath, "-d", checks,
    *sorted(source.glob("*.java")))
runtime = os.pathsep.join(map(str, [classes, checks, stdlib[0]]))
for test in ("ImportLimitsTest", "LocalTransferServerTest"):
    run(java / "java", "-cp", runtime, "dev.steelthread.lighttickets." + test)
jar = build / "checks.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for path in checks.rglob("*.class"):
        archive.write(path, path.relative_to(checks))
run(java / "java", "-cp", android / "cmdline-tools/latest/lib/r8.jar", "com.android.tools.r8.D8",
    "--min-api", "34", "--lib", platform, "--classpath", stdlib[0], "--classpath", classes,
    "--output", dex, jar)
resources = build / "resources.apk"
run(tools / "aapt2", "link", "-I", platform, "--manifest", source / "AndroidManifest.xml", "-o", resources)
with zipfile.ZipFile(resources, "a") as archive:
    for path in dex.glob("*.dex"):
        archive.write(path, path.name)
aligned = build / "aligned.apk"
run(tools / "zipalign", "-f", "4", resources, aligned)
run(tools / "apksigner", "sign", "--ks", key, "--ks-key-alias", key_alias,
    "--ks-pass", "env:LIGHT_TICKETS_KEYSTORE_PASSWORD", "--key-pass", "env:LIGHT_TICKETS_KEY_PASSWORD",
    "--out", build / "checks.apk", aligned)
print("Native-check APK ready; not installed.")
