# BmwDeck

BmwDeck（原 CarDeck-BMW）基于开源项目 [CarDeck](https://github.com/shaonialife/cardeck)，针对宝马 iDrive 7 车机的无线 CarPlay 连接与使用体验进行优化。项目运行在 Android 手机上，通过 CarPlay 将手机应用界面投送至车机。

BmwDeck is an experimental Android project based on [CarDeck](https://github.com/shaonialife/cardeck), optimized for BMW iDrive 7 head units. It builds on the upstream project's wireless CarPlay sender and adds BMW-specific compatibility and interface work.

## 新手入口

- [V58 中控版源码](https://github.com/Sykes-Shi/BmwDeck/tree/v58-source) · [V58 发布说明](https://github.com/Sykes-Shi/BmwDeck/releases/tag/v58-source) · [V47 至 V58 更新记录](CHANGELOG.md)
- 初次体验可按下方「从源码构建」生成手机端应用。已安装中控版的用户可直接看[首次连接教程](docs/QUICKSTART.md)。
- GitHub 现提供源码和版本说明。V46、V47 的历史安装包已撤下，仍可查阅 [V47 版本说明](https://github.com/Sykes-Shi/BmwDeck/releases/tag/v47-public-preview)和 [V55 手机界面更新说明](https://github.com/Sykes-Shi/BmwDeck/releases/tag/v55-update-notes)。

## 车机兼容性：社区试用反馈（2026 年 11 月）

- 据抖音群友反馈，iDrive 6（ID6）和 iDrive 8.5 各有成功连接的个例；这不代表同代车机或全部功能均已验证兼容。
- **ID6 补充**：另有一位抖音视频评论区用户反馈，其原车带无线 CarPlay 的 ID6 已实现安卓手机直连。

这些是用户反馈的连接个例；车机固件、手机型号、系统版本及其他功能尚未全面验证，不代表对应代际的所有车型均可用，也不代表原车不带无线 CarPlay 的车机可用。本项目仍主要围绕 iDrive 7 优化，更多车型与手机组合正在探索。欢迎在 [Issues](https://github.com/Sykes-Shi/BmwDeck/issues) 补充配置与测试结果。

## 项目状态

当前仓库源码对应 **V58 中控试用版**（`0.1.13-v58-center`，versionCode 178）。宝马车型、车机版本和手机系统存在差异，连接、声音、网络、导航和按键功能的表现可能不同。请在停车时完成配置和操作，并保留原车导航等备用方式。完整说明见[免责声明](DISCLAIMER.md)。

仪表导航测试版作为独立版本开发，使用不同的应用 ID；其源码见 [cluster-test 分支](https://github.com/Sykes-Shi/BmwDeck/tree/cluster-test)，相关功能仍在实验与验证中。两版应分别安装授权，使用时一次只运行并连接其中一版。

各版本的主要变化见[版本更新记录](CHANGELOG.md)。反馈问题时请注明手机型号、车机系统和 BmwDeck 版本。[GitHub Issues](https://github.com/Sykes-Shi/BmwDeck/issues)

## 主要功能与优化

- 针对宝马车机的无线连接、会话和音频兼容性进行调整。
- V58 的中控导航固定使用运行 BmwDeck 的 Android 手机上的百度地图汽车版。首页地图、「回家」「去公司」和已映射的 MAP / NAV 按键都打开汽车版。手机上的百度地图手机版可独立导航。
- 增加旋钮、方向盘及车机快捷键相关的输入处理、诊断和校准功能；具体可用按键取决于车机上报的信号。
- 支持媒体信息及歌词显示，可选择使用在线歌词查询。
- 提供网络连接、声音和输入等诊断信息，便于排查兼容问题。

## 使用条件

- 推荐 Android 14 或更新版本的手机。项目的最低构建版本为 Android 12（API 31），部分功能依赖较新系统。
- 安装并启动 Shizuku，按应用提示授权 BmwDeck。虚拟显示和触控依赖该服务。[Shizuku 启动指南](https://shizuku.rikka.app/)
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

构建完成后，可将本机生成的 `app/build/outputs/apk/debug/app-debug.apk` 安装到 Android 手机。开发构建的版本名为 `0.1.13-v58-center-dev`，应用 ID 为 `io.github.shaonialife.cardeck.bmwprobe.dev`，可与原中控试用版并存。首次使用需单独授予 Shizuku 权限，一次只连接其中一个应用。

`release` 构建的应用 ID 为 `io.github.shaonialife.cardeck.bmwprobe`，版本为 `0.1.13-v58-center`（versionCode 178）。Gradle 通过环境变量 `CARDECK_RELEASE_STORE`、`CARDECK_RELEASE_PASSWORD` 读取仓库外的 PKCS12 签名文件与口令，alias 为 `cardeck-bmw`。省略这些配置会生成未签名产物。要覆盖手机上已有的同包名应用，签名须与原安装一致。

## 致谢与许可

本项目基于 [shaonialife/cardeck](https://github.com/shaonialife/cardeck) 开发，源码基线为上游提交 [`09eebb0`](https://github.com/shaonialife/cardeck/commit/09eebb0f2a51c961c101e8fb49134ca7769b2d94)，感谢原作者及贡献者。上游版权声明为 **Copyright (c) 2026 lizhihua**，项目依照 [MIT License](LICENSE) 开源。

日间默认壁纸由图像生成工具制作。

本项目是独立的非官方实验项目，与宝马、Apple 及相关品牌无关联或认证。CarPlay、AirPlay 等名称及商标归各自权利人所有。
