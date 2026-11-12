# Known issues and release status

Version 0.4.2 is an SDK preview. The following points remain open for a production submission:

1. **Backup exclusion:** the standalone prototype explicitly disables Android backup. The SDK-generated manifest does not expose an equivalent option in the tested release. Saved preview tickets must not be described as excluded from all backups or device transfer.
2. **Transfer encryption:** the current paired, short-lived Wi-Fi upload is plain HTTP and is disclosed in both interfaces. Light has not reviewed whether to accept this flow or prefer its Tool Manager or another encrypted method.
3. **Brightness:** keeping a ticket awake is implemented. Temporarily increasing and restoring screen brightness is not.
4. **USB import:** the standalone prototype's USB inbox is not implemented in this SDK preview. Wireless import is available.
5. **Release toolkit warnings:** optimized builds of 0.4.0 and 0.4.1 completed with Kotlin-metadata parsing warnings from the SDK-pinned R8 version. No SDK restrictions or dependency pins were modified to hide the warnings. The optimized 0.4.2 build has not been run.
6. **Final release tests:** the newest full-screen view has visual and navigation checks, but a fresh optical scan of that view and venue acceptance have not been tested. An optimized release APK has not been run on the phone.
7. **Publication decisions:** the permanent package identifier and final public listing need review before submission. No build has been submitted to Light.

The preview's removal bug was fixed in 0.4.2 and retested on the phone: confirmation removed the practice ticket, returned to the shelf, and allowed the practice ticket to be restored and opened.
