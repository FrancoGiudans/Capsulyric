# 🎯 总体目标 (Objective)
将 Capsulyric / IslandLyrics 的 Android targetSdk 从 36 升级到 37，并完成 Android 17/API 37 行为变更审计、适配和验证。

# 📚 收集到的背景信息 (Context)
- 官方资料：Android Developers《Set up the Android 17 SDK》要求使用 Android 17 API 时 compileSdk = 37，准备启用 Android 17 运行时行为时 targetSdk = 37；页面最后更新时间为 2026-07-14。https://developer.android.com/about/versions/17/setup-sdk
- 官方资料：Android Developers《Behavior changes: Apps targeting Android 17 or higher》说明 target Android 17/API 37 后需处理仅针对 target 37+ 应用的行为变更；页面最后更新时间为 2026-07-14。https://developer.android.com/about/versions/17/behavior-changes-17
- 官方资料：同一页面提示还必须同时审阅《Behavior changes: all apps》，因为这些变更影响所有运行在 Android 17 上的应用，不论 targetSdkVersion。https://developer.android.com/about/versions/17/behavior-changes-all
- 当前项目配置位于 gradle/scripts/android-app.gradle：compileSdk = 37、minSdk = 35、targetSdk = 36；本任务核心配置改动预计是 targetSdk 36 -> 37。
- 当前顶层 build.gradle 使用 com.android.application 9.2.1；gradle/libs.versions.toml 也记录 agp = 9.2.1，高于官方 Android 17 SDK 页面要求的 AGP 8.9.0-rc01 起点。
- 应用启动阶段 app/src/main/java/com/example/islandlyrics/app/IslandLyricsApp.kt 调用 HiddenApiBypass.addHiddenApiExemptions("")；Android 17 target 37 变更包含 MessageQueue 私有字段/方法反射风险，以及 static final 字段反射/JNI 修改限制。
- Manifest 声明 SYSTEM_ALERT_WINDOW、POST_NOTIFICATIONS、POST_PROMOTED_NOTIFICATIONS、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE、REQUEST_IGNORE_BATTERY_OPTIMIZATIONS、Shizuku 权限；这些权限流需要在 Android 17 真机/模拟器上重新验证。
- Manifest 中 LyricService 使用 foregroundServiceType="specialUse"；Android 17 target 37 的背景音频加固要求后台音频交互必须具备符合要求的前台服务等条件，因此媒体/前台服务路径是重点验证对象。
- 源码大量使用 MediaSessionManager、PlaybackState、NotificationListenerService，并通过 LyricService、MediaMonitorService、ProgressSyncController、MediaActionController 等实现歌词、媒体状态和控制逻辑。
- 官方 Android 17 target 37 变更还包括 ECH/TLS 行为、ACCESS_LOCAL_NETWORK 运行时权限、外接键盘密码显示策略、Contacts Provider 严格 SQL、Large screen orientation/resizability/aspect ratio 限制忽略、BluetoothSocket read() 返回 -1 等；本项目初步搜索未发现 LAN socket/Bluetooth/Contacts 直接使用，但仍需在审计步骤中确认。
- Android 17 all-apps 变更包含应用内存限制，项目已有 onTrimMemory 和 FairMemoryManager，应把长时间歌词渲染、专辑图缓存、后台服务保活纳入内存回归。
- Last.fm 凭据存储使用 AndroidKeyStore，日志分享使用 FileProvider + ACTION_SEND；两条属于升级后需跑通的安全/分享回归路径。

# 🛠️ 约束与规范 (Constraints)
- 优先引用 Android Developers 官方文档和项目本地代码；第三方资料只作为补充，不作为行为变更依据。
- 保持变更范围收敛：先只改 targetSdk，除非编译、lint 或 Android 17 运行时验证暴露问题，再做针对性适配。
- 不得回退或覆盖用户已有改动；修改前检查 git status，遇到无关脏文件只记录不处理。
- 隐藏 API、Shizuku、前台服务和悬浮窗属于高风险路径，必须有失败降级、日志或用户可恢复提示，避免启动期崩溃。
- 所有 Android 17 专属适配优先用 Build.VERSION.SDK_INT >= 37 或能力检测进行版本门控，避免影响 Android 15/16。
- 如果需要新增权限，例如 ACCESS_LOCAL_NETWORK，必须先证明项目确实访问 LAN；不能为了保险而声明无用敏感权限。
- 不得引入大规模依赖升级或架构重构来完成 targetSdk 升级；依赖升级只用于解决明确的不兼容。
- 验收必须包含编译验证和 Android 17 设备/模拟器上的核心功能验证；仅编译通过不算完成。
- 如果 Play 发布是目标，还需检查 Google Play target API 政策窗口，但本任务本身以代码兼容和本地验证为准。

# 📋 执行计划 (Execution Plan)
- [ ] 步骤 1: 建立升级基线：记录 git status，确认当前分支、未提交改动、Gradle/AGP/Kotlin/SDK 配置，并保存当前 targetSdk=36 的构建状态。
- [ ] 步骤 2: 跑升级前编译基线：执行 .\gradlew :app:compileDebugKotlin 和 .\gradlew :app:compilePrereleaseKotlin；若失败，先区分既有问题和 target 37 任务无关问题。
- [ ] 步骤 3: 核对 Android 17 SDK 环境：确认本机已安装 Android SDK Platform 37 和 Build-Tools 37.x；若缺失，通过 Android Studio SDK Manager 或 sdkmanager 安装。
- [ ] 步骤 4: 执行最小配置改动：在 gradle/scripts/android-app.gradle 将 defaultConfig.targetSdk 从 36 改为 37，保留 compileSdk=37、minSdk=35 和现有 AGP 9.2.1。
- [ ] 步骤 5: 重新编译并记录差异：运行 debug/prerelease Kotlin 编译；如出现 manifest merge、permission、API 或依赖错误，按错误最小修复。
- [ ] 步骤 6: 审计隐藏 API 和反射路径：重点检查 IslandLyricsApp.kt 的 HiddenApiBypass.addHiddenApiExemptions("")、hidden-api 模块、FirewallCompat 反射调用，以及是否有 MessageQueue 私有成员或 static final 字段修改行为；必要时加 try/catch、日志和降级策略。
- [ ] 步骤 7: 审计后台音频和前台服务路径：确认 LyricService startForegroundTracked、foregroundServiceType=specialUse、MediaMonitorService、MediaActionController 在后台播放/暂停/切歌/音量相关场景下符合 Android 17 背景音频加固要求。
- [ ] 步骤 8: 审计权限与用户恢复路径：验证 SYSTEM_ALERT_WINDOW、POST_NOTIFICATIONS、POST_PROMOTED_NOTIFICATIONS、Notification Listener、Shizuku、忽略电池优化权限的申请、拒绝、重新授权和设置页跳转。
- [ ] 步骤 9: 审计网络与数据路径：确认 OkHttp/Last.fm/社区歌词源在 Android 17 ECH/TLS 行为下可用；搜索并确认无 LAN、Bluetooth RFCOMM、ContactsContract、SMS OTP 等需要额外 Android 17 适配的代码路径。
- [ ] 步骤 10: 审计大屏和窗口行为：在 sw>=600dp 设备/模拟器验证 MainActivity、Settings、媒体控制透明 Activity、悬浮歌词 overlay 的方向、可调整大小、透明窗口和 taskAffinity 行为。
- [ ] 步骤 11: 执行 Android 17 设备回归：安装 debug/prerelease 包，跑首次启动、权限引导、悬浮歌词显示隐藏、媒体会话监听、切歌进度同步、媒体控制弹窗、Shizuku 授权/未授权、Last.fm 登录、日志导出分享、缓存清理和长时间运行内存观察。
- [ ] 步骤 12: 执行兼容回归：至少在 Android 15/16 设备或模拟器验证核心路径，确保 target 37 版本门控没有破坏 minSdk=35 范围内行为。
- [ ] 步骤 13: 补齐测试或诊断：为风险较高的纯 Kotlin 逻辑补单元测试；对设备专属行为补手工测试记录、日志标签或诊断页验证项。
- [ ] 步骤 14: 收尾验收：再次运行编译命令，整理 Android 17 行为变更审计结论、已修改文件、未解决风险和发布前人工验证清单。
