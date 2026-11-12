# Validation

Checked on 2026-11-12. These are completed development checks, not certification or proof of venue acceptance.

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

Lint warnings concern the SDK-generated manifest's missing application icon and an optional Kotlin bitmap helper. Optimized compilation passed for 0.4.1 with the SDK metadata warnings described in [KNOWN_ISSUES.md](KNOWN_ISSUES.md). Optimized 0.4.2 compilation and runtime testing are pending.

Sanitized records are under [docs/verification](docs/verification/). The included screenshots show only the dummy practice ticket. The app and tests make no claim to validate an issuer's ticket or an event's admission policy.
