# Known issues and release status

Version 0.4.3 is an SDK preview. It updates the submission to SDK 0.2.7 and adopts the owner-aligned application ID; ticket handling code is unchanged. The following points remain open for a production submission:

1. **Backup exclusion:** the standalone prototype explicitly disables Android backup. The SDK-generated manifest does not expose an equivalent option in the tested release. Saved preview tickets must not be described as excluded from all backups or device transfer.
2. **Transfer encryption:** the current paired, short-lived Wi-Fi upload is plain HTTP and is disclosed in both interfaces. Light has not reviewed whether to accept this flow or prefer its Tool Manager or another encrypted method.
3. **Brightness:** keeping a ticket awake is implemented. Temporarily increasing and restoring screen brightness is not.
4. **USB import:** the standalone prototype's USB inbox is not implemented in this SDK preview. Wireless import is available.
5. **Release toolkit warnings:** optimized builds of 0.4.0 and 0.4.1 completed with Kotlin-metadata parsing warnings from the SDK-pinned R8 version. No SDK restrictions or dependency pins were modified to hide the warnings. The optimized 0.4.2 build has not been run.
6. **Venue acceptance:** the Light-built 0.4.3 release APK passed 25 phone checks and a physical iPhone scan of its practice QR while unplugged. These checks do not prove admission at an event. No venue acceptance test has been completed.
7. **Publication decisions:** the final public listing remains subject to Light review; the owner-aligned permanent package identifier has been selected. Build and approval status should be checked in the Light dashboard.

The preview's removal bug was fixed in 0.4.2 and retested on the phone: confirmation removed the practice ticket, returned to the shelf, and allowed the practice ticket to be restored and opened.

On November 14, 2026, Light's dashboard still showed version 4 as pending approval and the tool as unpublished. The GitHub beta is the exact Light-built APK tested on the phone; Light's build and signature do not imply manual approval. See [VALIDATION.md](VALIDATION.md) for its provenance and completed checks.
