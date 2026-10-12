# 引用、参考与致谢

核对日期：2026-10-12。以下区分接口/设计调研、同作者项目复用、实际编译依赖与图像资源。致谢不代表原作者参与或为本项目背书。

| 来源 / 作者 | 核对版本与许可证 | 知乎Lite使用或参考了什么 |
| --- | --- | --- |
| [Zhihu++ / zly2006 与贡献者](https://github.com/zly2006/zhihu-plus-plus) | `464a6f5e7c3920a0e36e3fec6d1b7150156614cf`；[AGPL-3.0](https://github.com/zly2006/zhihu-plus-plus/blob/464a6f5e7c3920a0e36e3fec6d1b7150156614cf/LICENSE) | 调研热榜、问题回答分页、身份请求头、账号推荐、楼中楼 child_comment 接口及数据结构。对应本项目 Data.kt、MainActivity.kt 的网络与阅读流程；实现独立编写，未作为依赖或 fork 引入，未随仓库发布其源码。 |
| [Hydrogen / zhihulite 与贡献者](https://github.com/zhihulite/Hydrogen) | `ce586963f92ec19b5036dacc297799a0861c8e6e`；[MIT](https://github.com/zhihulite/Hydrogen/blob/ce586963f92ec19b5036dacc297799a0861c8e6e/LICENSE) | 参考 hot_model、question_model、回答与账户模型中的热榜、问题/回答接口和会话更新思路；本项目 Kotlin 网络层独立实现，未引入其 Lua/Android 源码。 |
| [知乎-- / huamurui 与贡献者](https://github.com/huamurui/zhihu-minus-minus) | `05a8a963a21d2bd59be44fe40829bbac7b8bbbf5`；[GPL-3.0](https://github.com/huamurui/zhihu-minus-minus/blob/05a8a963a21d2bd59be44fe40829bbac7b8bbbf5/LICENSE) | 参考 appApi 请求字段（x-api-version、x-page-id）、游客 explore/guest/feeds 和登录/游客接口区分；未引入或打包其客户端实现。 |
| [Telegram Android / Nikolai Kudashov（DrKLO）与贡献者](https://github.com/DrKLO/Telegram) | [仓库 GPL v2](https://github.com/DrKLO/Telegram/blob/master/LICENSE)；具体 Java 文件头可能为 v2 or later；本次未固定所阅读 Theme.java 的提交 | 仅研究 Theme.java 的背景、副文字、分隔线、顶栏配色和紧凑列表层级；未复制 Telegram Java 实现或品牌素材。 |
| [Weibo Lite / ives-cheung 与贡献者](https://github.com/ives-cheung/Weibo_Lite) | 同作者项目；[非商业学习使用许可](https://github.com/ives-cheung/Weibo_Lite/blob/main/LICENSE) | 0.2.0 复用并适配其玻璃界面方案：GlassEffects.kt 的 GPU 模糊/AGSL 边缘折射，以及导航背景采样、弹性选中气泡与安全边距处理；知乎端在 LiquidGlass.kt、GlassEffects.kt 集成。 |
| [Material Design Icons / Google 与贡献者](https://github.com/google/material-design-icons) | [Apache-2.0](https://github.com/google/material-design-icons/blob/master/LICENSE) | Compose material-icons-core 提供导航、搜索、刷新等图标；drawable/ic_like.xml 的拇指路径来自 Material thumb_up，ic_reply.xml 为项目绘制的对话图标。保留 Apache-2.0 许可。 |

## 实际依赖

AndroidX、Kotlin、Coil、jsoup 等实际运行时依赖见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [解析清单](docs/dependency-licenses.json)。这些组件由 Gradle 下载，其原始许可证不被本项目非商业许可替代。

## 图标与内容

- 当前 Z 对话气泡图标由 OpenAI 图像生成工具辅助生成，源图在 assets/icon-generated.png，缩放后的 APK 资源在 app/src/main/res/drawable-nodpi/app_icon.png；不是知乎官方标志。不承诺生成图像具有独占性或可注册性。
- 用户最初提供的图标只保留在本机，不随公开仓库发布。
- 知乎返回的回答、评论、头像、图片等属于原作者或相应权利人；本项目许可不授予这些内容的版权或商标权。
- 调研项目源码仅保留在本机 research/，没有纳入公开仓库。接口知识或设计参考不等于对第三方代码的重新许可。后续若引入第三方代码，应保留原始声明并重新核对许可证兼容性。
