# Third-party notices

The MIT license for Light Tickets does not replace these components' licenses.

## Light SDK

- Source: <https://github.com/lightphone/light-sdk>
- Submission SDK: 0.2.7, commit `e81295e6bc29f987c44c656fdf5081117bbee833`. Earlier phone tests used SDK 0.2.3.
- License: MIT, copyright © 2026 The Light Phone.
- Notice: [LIGHT-SDK-LICENSE.txt](LIGHT-SDK-LICENSE.txt).

The tool build configuration is adapted from the SDK scaffold. SDK binaries and source modules are fetched into an ignored build workspace; they are not vendored in this small repository. The SDK's dependencies retain their respective licenses.

## ZXing Core

- Maven coordinate: `com.google.zxing:core:3.5.4`.
- Source: <https://github.com/zxing/zxing>.
- License: Apache License 2.0.
- Included license: [ZXING-LICENSE.txt](ZXING-LICENSE.txt).

The app uses ZXing Core to generate pairing QR codes. It is fetched as a declared Maven dependency; no JAR is committed here.

## Practice content

The event ticket image and screenshots are generated demonstration content. Their QR payload is `LIGHT-TICKETS-DEMO-7F3A`; they are not valid for admission and contain no personal ticket data.
