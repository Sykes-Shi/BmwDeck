# CarDeck-BMW

CarDeck-BMW 基于开源项目 [CarDeck](https://github.com/shaonialife/cardeck)，针对宝马 iDrive 7 车机的无线 CarPlay 连接与使用体验进行优化。项目运行在 Android 手机上，通过 CarPlay 将手机应用界面投送至车机。

CarDeck-BMW is an experimental Android project based on [CarDeck](https://github.com/shaonialife/cardeck), optimized for BMW iDrive 7 head units. It builds on the upstream project's wireless CarPlay sender and adds BMW-specific compatibility and interface work.

> 本分支是基于 V46 的小米 / iDrive 7 蓝牙兼容测试分支，测试包默认保留系统蓝牙连接，尚待实车验证。测试步骤见 [小米 / iDrive 7 兼容测试](docs/XIAOMI-ID7-TEST.md)；下面的 V46 下载链接仍是原公开试用版。

## 新手入口

- [安装与首次连接教程](docs/QUICKSTART.md)：安装到安卓手机 → 启动 Shizuku → 配对宝马 → 启动连接。
- [版本更新记录](CHANGELOG.md) · [反馈问题](https://github.com/Sykes-Shi/CarDeck-BMW/issues)
- [下载 V46 中控公开试用版 APK](https://github.com/Sykes-Shi/CarDeck-BMW/releases/download/v46-public-preview/CarDeck-BMW-v46-public.apk) · [发布说明与校验值](https://github.com/Sykes-Shi/CarDeck-BMW/releases/tag/v46-public-preview)
- **旧调试版升级注意：**本次首次使用独立正式签名，不能覆盖旧的 Android Debug 签名包。请先保存需要的设置和日志，再卸载旧中控试用版并重新安装、授权；卸载会清除应用本地数据。新用户直接安装。

## ID6 社区反馈（2026 年 11 月）

有一位社区用户反馈：其 **原车带无线 CarPlay 的 ID6** 已实现安卓手机直连。该反馈来自项目抖音视频评论；车型、手机型号、软件版本及各项功能尚未完整核实，**不是所有 ID6 均兼容的保证**。本项目仍主要围绕 iDrive 7 优化，欢迎在 Issues 补充具体测试配置与结果；没有无线 CarPlay 的车机不能据此视为可用。

## 项目状态

当前仓库源码对应 **V46 中控公开试用版**，在 V39 中控版本基础上继续优化，仍在开发和实车验证中。宝马车型、车机版本和手机系统存在差异，连接、声音、网络、导航和按键功能的表现可能不同。请在停车时完成配置和操作，并保留原车导航等备用方式。完整说明见 [免责声明](DISCLAIMER.md)。

仪表导航测试版作为独立版本开发，使用不同的应用 ID；其源码见 [cluster-test 分支](https://github.com/Sykes-Shi/CarDeck-BMW/tree/cluster-test)，相关功能仍在实验与验证中。两版应分别安装授权，使用时一次只运行并连接其中一版。

各诊断版本的主要变化见 [版本更新记录](CHANGELOG.md)。新手先安装上面的中控公开试用版；反馈问题请使用 [GitHub Issues](https://github.com/Sykes-Shi/CarDeck-BMW/issues)。正式签名只标识本项目的发布来源，不代表宝马/Apple 认证或生产级稳定性。

## 主要功能与优化

- 针对宝马车机的无线连接、会话和音频兼容性进行调整。
- 优化横屏桌面、地图和媒体界面的显示；支持车机地图与手机导航切换，并提供百度地图巡航入口。
- 增加旋钮、方向盘及车机快捷键相关的输入处理、诊断和校准功能；具体可用按键取决于车机上报的信号。
- 支持媒体信息及歌词显示，可选择使用在线歌词查询。
- 提供网络连接、声音和输入等诊断信息，便于排查兼容问题。

## 使用条件

- 推荐 Android 14 或更新版本的手机。项目的最低构建版本为 Android 12（API 31），部分功能依赖较新系统。
- 安装并启动 [Shizuku](https://shizuku.rikka.app/)，按其说明授权 CarDeck-BMW；虚拟显示、输入注入等功能依赖 Shizuku。
- 支持无线 CarPlay 的宝马 iDrive 7 车机；实际可用性取决于手机、车机和系统配置。

在线歌词是可选功能。开启后，在需要查询时会向 LRCLIB、网易云音乐公开曲库发送歌名和歌手；专辑与时长用于本地匹配。网络重试选项可在应用中设置。

## 从源码构建

需要 JDK 17、Android SDK 及 API 34 平台。项目提供 Gradle Wrapper（8.10.2），首次构建需要下载 Gradle 和依赖。

```bash
# macOS / Linux
./gradlew :app:assembleDebug

# Windows
gradlew.bat :app:assembleDebug
```

构建完成后，debug APK 位于 `app/build/outputs/apk/debug/`。开发构建的应用 ID 为 `io.github.shaonialife.cardeck.bmwprobe.dev`，与公开试用版和上游版分开。

公开试用版使用 `release` 构建，应用 ID 为 `io.github.shaonialife.cardeck.bmwprobe`，版本为 `0.1.13-v46-public`（versionCode 160）。构建时通过环境变量 `CARDECK_RELEASE_STORE`、`CARDECK_RELEASE_PASSWORD` 提供仓库外的 PKCS12 签名文件与口令，alias 为 `cardeck-bmw`。未提供签名时产物为 unsigned，不允许直接公开发布。密钥、加密口令及解密后的口令均不得提交到仓库。公开证书指纹与 APK SHA-256 见发布说明。

## 致谢与许可

本项目基于 [shaonialife/cardeck](https://github.com/shaonialife/cardeck) 开发，源码基线为上游提交 [`09eebb0`](https://github.com/shaonialife/cardeck/commit/09eebb0f2a51c961c101e8fb49134ca7769b2d94)，感谢原作者及贡献者。上游版权声明为 **Copyright (c) 2026 lizhihua**，项目依照 [MIT License](LICENSE) 开源。

日间默认壁纸由图像生成工具制作。

本项目是独立的非官方实验项目，与宝马、Apple 及相关品牌无关联或认证。CarPlay、AirPlay 等名称及商标归各自权利人所有。
