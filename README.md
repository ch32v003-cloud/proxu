# proxu

A V2Ray client for Android based on [v2rayNG](https://github.com/2dust/v2rayNG), support [Xray core](https://github.com/XTLS/Xray-core) and [v2fly core](https://github.com/v2fly/v2ray-core).

This is a fork of the original [v2rayNG](https://github.com/2dust/v2rayNG) project with the following modifications:
- Renamed project from **v2rayNG** to **proxu**
- Renamed package from `com.v2ray.ang` to `com.proxu.app`
- Renamed all branding and resources accordingly

[![API](https://img.shields.io/badge/API-24%2B-yellow.svg?style=flat)](https://developer.android.com/about/versions/lollipop)
[![Kotlin Version](https://img.shields.io/badge/Kotlin-2.3.10-blue.svg)](https://kotlinlang.org)
[![GitHub commit activity](https://img.shields.io/github/commit-activity/m/ch32v003-cloud/proxu)](https://github.com/ch32v003-cloud/proxu/commits/master)
[![GitHub Releases](https://img.shields.io/github/downloads/ch32v003-cloud/proxu/latest/total?logo=github)](https://github.com/ch32v003-cloud/proxu/releases)

### Upstream

This project tracks the original [v2rayNG](https://github.com/2dust/v2rayNG) repository.
To manually pull updates from upstream:

```bash
# Fetch upstream changes
git fetch upstream

# View new commits
git log --oneline HEAD..upstream/master

# Cherry-pick specific commits
git cherry-pick <commit-hash>
```

See [UPSTREAM.md](UPSTREAM.md) for detailed update workflow.

### Usage

#### Geoip and Geosite
- geoip.dat and geosite.dat files are in `Android/data/com.proxu.app/files/assets` (path may differ on some Android device)
- download feature will get enhanced version in this [repo](https://github.com/Loyalsoldier/v2ray-rules-dat) (Note it need a working proxy)
- latest official [domain list](https://github.com/Loyalsoldier/v2ray-rules-dat) and [ip list](https://github.com/Loyalsoldier/geoip) can be imported manually
- possible to use third party dat file in the same folder, like [h2y](https://guide.v2fly.org/routing/sitedata.html#%E5%A4%96%E7%BD%AE%E7%9A%84%E5%9F%9F%E5%90%8D%E6%96%87%E4%BB%B6)

### Development guide

Android project under `proxu/` folder can be compiled directly in Android Studio, or using Gradle wrapper. But the v2ray core inside the aar is (probably) outdated.

The aar can be compiled from the Golang project [AndroidLibV2rayLite](https://github.com/2dust/AndroidLibV2rayLite) or [AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite).

For a quick start, read guide for [Go Mobile](https://github.com/golang/go/wiki/Mobile) and [Makefiles for Go Developers](https://tutorialedge.net/golang/makefiles-for-go-developers/)

proxu can run on Android Emulators. For WSA, VPN permission need to be granted via
`appops set com.proxu.app ACTIVATE_VPN allow`

### Build

```bash
cd proxu
./gradlew assembleRelease
```

APK files will be generated in `proxu/app/build/outputs/apk/`.

### Credits

- Original project: [v2rayNG](https://github.com/2dust/v2rayNG) by [2dust](https://github.com/2dust)
- Xray core: [XTLS/Xray-core](https://github.com/XTLS/Xray-core)
- v2fly core: [v2fly/v2ray-core](https://github.com/v2fly/v2ray-core)

### License

[GPLv3](LICENSE)
