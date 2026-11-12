"""Prepare a fresh ignored SDK workspace; never install or publish anything."""
import argparse
import importlib
import subprocess
import sys
from pathlib import Path

SDK_URL = "https://github.com/lightphone/light-sdk.git"
SDK_COMMIT = "e81295e6bc29f987c44c656fdf5081117bbee833"
ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", type=Path, default=ROOT / ".local/light-sdk")
    args = parser.parse_args()
    destination = args.destination.expanduser().absolute()
    if destination.exists() or destination.is_symlink():
        parser.error("Destination already exists; choose a new empty path. No files were changed.")
    destination.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["git", "clone", "--no-checkout", SDK_URL, str(destination)], check=True)
    subprocess.run(["git", "-C", str(destination), "checkout", "--detach", SDK_COMMIT], check=True)
    sys.path.insert(0, str(destination / "builder"))
    extractor = importlib.import_module("lightbuilder.extract")
    report = extractor.extract(ROOT, destination / "tool")
    print(f"Prepared {len(report.files_copied)} tool files against SDK commit {SDK_COMMIT}.")
    print(f"SDK workspace: {destination}")
    print("Next: run ./gradlew --no-daemon -DlightSdk.toolOnly=true :tool:assembleDebug :tool:lintDebug there.")


if __name__ == "__main__":
    main()
