# Building Light Tickets

The public source tree is deliberately small. Light builds the `tool/` module against its own SDK; `gradle.properties` selects SDK **0.2.7**.

The tested SDK source commit is:

```
e81295e6bc29f987c44c656fdf5081117bbee833
```

## Local build

Prerequisites: Git, Python 3.10 or newer, Java 17, and Android SDK platform 36 and build-tools 36.0.0. Set `JAVA_HOME` and `ANDROID_HOME` to your local installations. The SDK downloads its declared dependencies during the first build.

From this repository:

```sh
python3 scripts/prepare-build.py
cd .local/light-sdk
./gradlew --no-daemon -DlightSdk.toolOnly=true :tool:assembleDebug :tool:lintDebug
```

The helper clones the official SDK at the pinned commit into an ignored local directory and uses Light's own extractor to copy the tool files into it. It does not download or copy user signing keys, modify a phone, or change the SDK's source or restrictions. It refuses to overwrite an existing destination. For another clean build workspace, supply an empty destination with `--destination`.

Debug APK output is under `.local/light-sdk/tool/build/outputs/apk/debug/`. The SDK scaffold uses its publicly supplied development signing key. This is a development build, not a Light-signed release, and generated APKs and SDK keys are excluded from this repository.

The optional optimized build is:

```sh
./gradlew --no-daemon -DlightSdk.toolOnly=true :tool:assembleRelease
```

See [KNOWN_ISSUES.md](KNOWN_ISSUES.md) for the SDK's release metadata warnings. The local optimized APK is not evidence of official review or approval.

## Local verification helper

`verification/build-checks.py` runs real HTTP/socket and file-limit checks, then builds a separate Android instrumentation APK. It never installs anything.

Supply these environment variables:

- `LIGHT_TICKETS_SDK_PROJECT`: the prepared SDK checkout.
- `JAVA_HOME`, `ANDROID_HOME`, and `GRADLE_USER_HOME`: the build tool locations.
- `LIGHT_TICKETS_CHECK_DIR`: an empty, disposable output directory outside tracked source.
- `LIGHT_TICKETS_KEY_FILE`: a local signing key outside the repository.
- `LIGHT_TICKETS_KEY_ALIAS`, `LIGHT_TICKETS_KEYSTORE_PASSWORD`, and `LIGHT_TICKETS_KEY_PASSWORD`: the matching key settings. Set the last two to the same value if the key uses one password.

The check APK and target APK must have the same signer. Native checks use generated practice files in a temporary directory and remove that directory afterward. Running native instrumentation restarts the target process; run it on a test device with the owner's knowledge. Do not publish signing material or real tickets.

## Tool Library submission

Light's builder reads root `gradle.properties`, `tool/lighttool.toml`, `tool/build.gradle.kts`, and `tool/src/main/`. It supplies the SDK and build environment. The app has Kotlin production sources and no handwritten production Android manifest. The Java and manifest files under `verification/` are separate test helpers.

Before registering a tool, review its permanent package identifier; Light does not allow changing it afterward. The submission ID is `io.github.izzystst.lighttickets`, aligned with the owner’s GitHub username. The on-phone development preview uses a separate earlier identifier.

Official references:

- [Required repository files](https://github.com/lightphone/light-sdk/blob/main/docs/repo/README.md#required-files)
- [Submission instructions](https://github.com/lightphone/light-sdk#submitting-your-tool)
- [Tool guidelines](https://github.com/lightphone/light-sdk/blob/main/TOOL_GUIDELINES.md)
