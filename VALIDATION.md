# Validation

Updated on 2026-11-14. These are completed development checks, not certification or proof of venue acceptance.

| Check | Result | Scope |
| --- | --- | --- |
| SDK source-file extraction | Pass | Tool source and assets fit Light's extractor |
| SDK debug compilation and Android lint | Pass | 0.4.2; zero errors and two warnings |
| File-limit host checks | 16 passed | Empty/oversized files, copy boundaries, and filename cleanup |
| Real HTTP/socket checks | 34 passed | Pairing, one-upload limit, boundaries, origins, expiry, fresh secrets, stable address |
| Native Android ticket checks | 16 passed | Generated PDFs/images, page counts, bounds, reopening, deletion, rejected inputs |
| Actual Wi-Fi upload | Pass | Practice PDF, phone Save approval, sender confirmation, shelf entry |
| Leaving receiver with Back | Pass | A previously listening socket became unreachable |
| Full-screen viewer | Pass | User visual confirmation; 1080 × 1094 ticket viewport on a 1080 × 1240 display; Back returns to ticket view |
| Confirmed removal returns to shelf | Pass | 0.4.2; practice entry disappeared and was restored afterward |

The host and native data-path checks were run during the 0.4.0 migration; those core implementations remain unchanged in 0.4.2. The full-screen layout was checked in 0.4.1, and the removal fix was retested in 0.4.2.

Lint warnings concern the SDK-generated manifest's missing application icon and an optional Kotlin bitmap helper. Optimized compilation passed for 0.4.1 with the SDK metadata warnings described in [KNOWN_ISSUES.md](KNOWN_ISSUES.md). An optimized local 0.4.2 build was not run; the subsequent Light-built 0.4.3 APK was installed and tested as described below.

Sanitized records are under [docs/verification](docs/verification/). The included screenshots show only the dummy practice ticket. The app and tests make no claim to validate an issuer's ticket or an event's admission policy.

Submission candidate 0.4.3 updates to SDK 0.2.7 and application ID `io.github.izzystst.lighttickets`. Ticket handling and display code are unchanged from the tested preview; the instrumentation target has been aligned to the new ID. Candidate 0.4.3 debug compilation, current SDK source/dependency validation, and Android lint passed on SDK 0.2.7 (zero errors, two warnings).

## Light-built 0.4.3 APK

Light's build succeeded from source commit `a483b25135834b582ee5e07b61b365c3e9e392ce`. The GitHub `v0.4.3` tag identifies that exact build source; later documentation updates live on `main`.

- Package: `io.github.izzystst.lighttickets`; version name `0.4.3`, version code `4`.
- APK size: 10,462,690 bytes.
- APK SHA-256: `110759f7e4cc2930d4e28fb55f19fb926aabba04a94bf44738bca2c077bf9ebd`.
- Android signature and Light source stamp verified. Signer: `O=Light, CN=io.github.izzystst.lighttickets`. The APK is not debuggable.
- Installed and run on a physical Light Phone III: **25 checks passed**, covering practice-image display, full-screen Back navigation, confirmed removal returning to the shelf, browser pairing and PDF upload, phone approval, sender confirmation, receiver shutdown on Back, fresh session credentials, stable bookmark address, two PDF pages, protected ticket display, and persistence after process restart.
- A separate desktop decode of the phone screenshot passed. The user also scanned the displayed practice QR with an iPhone Code Scanner **with USB unplugged** and confirmed `LIGHT-TICKETS-DEMO-7F3A`.
- Only dummy files were used. Older app installations and their real tickets were not modified. This beta does not migrate those tickets.

The installed APK allows Android backup; backup exclusion remains unresolved. Local transfer remains unencrypted HTTP. Venue admission, rotating barcodes, and NFC ticketing are outside these checks. A sanitized result record is available at [docs/verification/light-built-0.4.3.json](docs/verification/light-built-0.4.3.json).
