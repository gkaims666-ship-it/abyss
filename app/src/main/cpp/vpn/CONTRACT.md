# Native VPN engine contract (`libabyss_vpn.so`)

The Kotlin side (`feature/vpn/VpnBridge.kt`) loads `libabyss_vpn` and calls three
JNI symbols. Any native engine you build must export exactly these:

```c
// returns true on success
jboolean Java_com_abyss_app_feature_vpn_VpnBridge_nativeStart(
    JNIEnv*, jobject, jstring configPath, jint tunFd, jint socksPort, jint mtu);

void Java_com_abyss_app_feature_vpn_VpnBridge_nativeStop(JNIEnv*, jobject);
```

`nativeStart` must:
1. Start **Xray-core** with the JSON at `configPath` (it defines a SOCKS inbound
   on `127.0.0.1:socksPort` and the chosen outbound).
2. Start a **tun2socks** forwarder (e.g. hev-socks5-tunnel) bridging the VPN
   `tunFd` to that SOCKS inbound, using `mtu`.
3. Protect every outbound socket it opens from the VPN loop by calling back into
   Kotlin: `com.abyss.app.feature.vpn.AbyssVpnService.protectFd(int) : boolean`
   (a static method). From C/Go, call it via JNI on that class.

## How to build it (outline)

- **Xray-core → Android lib:** use [XTLS/libXray](https://github.com/XTLS/libXray)
  with `gomobile bind -target=android/arm64` to produce an `.aar`. Drop the
  `.aar` into `app/libs/`.
- **tun2socks:** build [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
  for `arm64-v8a` with the NDK.
- Write a small JNI shim exporting the two symbols above, linking both, and
  package its `.so` as `libabyss_vpn.so` under `src/main/jniLibs/arm64-v8a/`.

See `.github/workflows/build-vpn.yml` for a CI starting point. This is the one
piece that needs a Go + NDK toolchain and may need adapter tuning to the exact
libXray API version — it is intentionally opt-in so the default APK always builds.
