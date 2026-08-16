# Privacy Policy (隐私说明)

> Full version of the privacy summary in the README. / README 中隐私摘要的完整版本。

## English

This app needs to read notifications to get lyrics and playback information.

We only read media playback notification content, including: album art, artist, song title, album name, and the package name of the app currently playing media.

The above information is used solely for:
- Reading and displaying playback information
- Extracting lyrics from media notifications
- Matching online lyrics when Online Lyrics is enabled
- Sending now playing and scrobble records to Last.fm when Last.fm scrobbling is enabled and connected
- App self-logging for diagnostics

We do NOT read chat messages, verification codes, emails, or any non-media notifications. Processing stays on your device by default. Network requests are only made for features you explicitly enable, such as Online Lyrics or Last.fm.

For Last.fm, Capsulyric uses API credentials supplied by the user. When enabled, it may send track title, artist, album, duration, and playback timestamp to Last.fm for now-playing updates and scrobbles. Last.fm API credentials and session keys are encrypted locally with Android Keystore-backed AES-GCM storage and excluded from Android backup/device-transfer rules. Normal Capsulyric setting exports omit them; users can explicitly include them only in a password-encrypted sensitive-data backup entry.

## 中文

本应用需要读取通知以获取歌词与播放信息。

我们仅会读取媒体播放通知的内容，包括：专辑图片、歌手、歌名、专辑名，以及正在播放媒体的应用包名。

上述信息仅用于以下用途：
- 播放信息的读取和显示
- 媒体通知歌词的提取
- 开启在线歌词后用以匹配在线歌词
- 开启并连接 Last.fm 后向 Last.fm 发送正在播放与 scrobble 记录
- 应用记录自身日志

我们不会读取您的聊天消息、验证码、邮件等非媒体类通知。默认情况下数据在本机处理；只有在您明确开启在线歌词或 Last.fm 等功能时，才会发起相应网络请求。

Last.fm 使用由用户自行提供的 API 凭据。开启后，Capsulyric 可能会将歌名、歌手、专辑、时长和播放时间发送给 Last.fm，用于正在播放状态和 scrobble 记录。Last.fm API 凭据与 session key 会使用 Android Keystore 支持的 AES-GCM 存储在本机，并排除在 Android 备份/设备迁移规则之外。Capsulyric 常规配置导出不会包含这些数据；只有用户主动选择敏感数据并设置备份口令时，才会写入加密备份项。

---

Back to README / 返回 README: [README.md](../README.md)
