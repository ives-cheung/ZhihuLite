# 知乎Lite

轻量的 Kotlin / Jetpack Compose 第三方知乎 Android 阅读客户端。当前版本 **0.2.0**，支持蓝白/深色主题和玻璃导航界面，无广告 SDK、统计 SDK、推送或后台服务。

> **仅供非商业学习、交流与研究。** 本项目为公开源码的学习项目，与知乎官方无隶属、合作或授权关系。

## 版权与使用范围

- 原创代码与文档 © 2026 **ives-cheung 与项目贡献者**，适用 [非商业学习使用许可](LICENSE)。请保留版权、许可及引用声明；商业使用需另行取得相关权利人授权。
- 本许可含非商业限制，不是 OSI 认可的开源许可证。公开仓库不代表放弃版权。
- 第三方库、素材及平台内容仍适用各自许可证与权利；本许可不重新授权知乎内容、商标、账户或接口。
- 请遵守平台服务条款，不绕过验证、访问控制或付费限制。“学习研究”声明不构成第三方授权或免责保证。
- 如有权利或来源问题，请通过 [Issues](https://github.com/ives-cheung/ZhihuLite/issues) 提供具体文件与依据，以便核实、修正或移除。

## 功能

- 游客推荐、登录后的账号推荐、实时热榜、问题及回答分页。
- 原生结构化阅读：段落、链接、引用、列表、表格、代码复制和图片浏览/保存。
- 评论及完整楼中楼回复、头像与赞数；账户资料、赞同动态、收到的评论/回复、只读私信。
- Room 本地收藏、历史和稍后阅读；阅读字号、浅色/深色主题及缓存设置。
- Android Keystore 加密保存会话；不保存账号密码，禁用系统备份与账户数据迁移。
- Android 12+ GPU 玻璃模糊、Android 13+ AGSL 边缘折射，旧系统提供透明层回退。

知乎接口可能变更、限流或要求登录/验证；功能可用性以真实返回为准。赞同动态不保证覆盖全部历史；私信只读。知乎账户收藏尚未实现，关注 Feed 尚未完成全面验收。验证码与登录由用户亲自操作。

## 构建

需要 JDK 17 或 21、Android SDK 35。配置本机 local.properties（不入库）：

```properties
sdk.dir=/path/to/Android/sdk
```

```sh
./gradlew assembleRelease lintDebug
adb -s <设备序列号> install -r app/build/outputs/apk/release/app-release.apk
```

发布构建启用 R8 与资源压缩；当前使用构建机器的 Android debug keystore 供调试，自行分发应配置自己的发布签名。签名文件、APK、本机设置、调试日志和真实设备截图不在公开仓库中。

设备验证脚本需要设置 `ANDROID_SERIAL`；执行会操作设备页面，部分脚本会写入本地收藏或历史。网络测试使用真实接口，无硬编码假数据。历史验证记录反映当时结果，不保证当前接口始终可用。

## 引用与致谢

具体“引用了谁、哪些内容、是否实际复用代码”见 [ACKNOWLEDGEMENTS.md](ACKNOWLEDGEMENTS.md)：

- **zly2006 / Zhihu++**：推荐、热榜、分页、身份请求与楼中楼接口调研。
- **zhihulite / Hydrogen**：问题、回答、热榜及会话模型调研。
- **huamurui / 知乎--**：请求字段、游客推荐及登录/游客接口区分调研。
- **Nikolai Kudashov / Telegram Android**：主题配色、紧凑列表与信息层次参考。
- **ives-cheung / Weibo Lite**：玻璃模糊、折射、导航背景采样及弹性气泡方案复用与适配。
- **Google / Material Design Icons**：导航与赞同等图标。

实际编译组件及许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 和 [依赖清单](docs/dependency-licenses.json)。当前应用图标为 AI 辅助生成的 Z 对话气泡，不是知乎官方标志。

## 技术与验证

Kotlin 2.0.0、Compose 1.7.6、Material3 1.3.1、Coil 2.7.0、jsoup 1.18.3、Room 2.6.1。只有 INTERNET 权限，图片保存使用 MediaStore。

- [技术方案与接口调研](docs/方案与接口验证.md)
- [玻璃界面验证记录](docs/glass-ui-validation.md)
- [公共接口验证脚本](scripts/verify_public_chain.py)

感谢所有参考项目、依赖库的作者和维护者；致谢不代表他们为本项目背书。
