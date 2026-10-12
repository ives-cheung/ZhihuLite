# 第三方组件与许可证

项目原创部分 © 2026 ives-cheung 与贡献者。第三方组件的版权属于各自权利人，按原有许可使用，不附加本项目的非商业限制。

完整运行时坐标、解析版本及许可来源见 [docs/dependency-licenses.json](docs/dependency-licenses.json)，包含传递依赖。

| 组件 / 权利人 | 许可与用途 |
| --- | --- |
| AndroidX Activity、Core、Lifecycle、Compose、Material3、Room 等 / Android Open Source Project 贡献者 | Apache-2.0；Android UI、生命周期、数据库与系统适配 |
| Kotlin、kotlinx.coroutines、annotations / JetBrains 与贡献者 | Apache-2.0；语言运行库、协程、注解 |
| Coil / Coil 贡献者 | Apache-2.0；图像加载与 Compose 集成 |
| OkHttp、Okio / Square 与贡献者 | Apache-2.0；Coil 等引入的传递依赖 |
| Accompanist DrawablePainter / Google 与贡献者 | Apache-2.0；图像加载的传递依赖 |
| Guava listenablefuture / Google 与贡献者 | Apache-2.0；AndroidX 传递依赖 |
| jsoup 1.18.3 / Jonathan Hedley 与贡献者 | [MIT 原文](licenses/jsoup-MIT.txt)；HTML 解析 |
| Material Design Icons / Google 与贡献者 | Apache-2.0；导航、动作与赞同图标 |
| Gradle Wrapper 8.9 / Gradle 与贡献者 | [原文](licenses/Gradle-LICENSE.txt)，Apache-2.0；构建启动器 |
| AGP 8.7.2 / Android Open Source Project；Kotlin Gradle Plugin 2.0.0 / JetBrains | Apache-2.0；构建时下载，不作为项目原创代码重新许可 |

[Apache-2.0 全文](licenses/Apache-2.0.txt)及[依赖归档内原始声明](licenses/runtime-artifact-notices.txt)随仓库保留。调研参考不属于编译依赖，详见 [引用与致谢](ACKNOWLEDGEMENTS.md)。

源码与开发预览 APK 分别通过仓库及 Releases 提供。APK 随附许可包（ZhihuLite-0.2.0-Licenses.zip），不上传签名密钥或用户数据。自行分发二进制时，应同时保留适用的许可和声明，并核对各组件的分发义务。
