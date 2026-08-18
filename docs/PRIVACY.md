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

### Online Lyrics Sources

When Online Lyrics is enabled, Capsulyric sends the current song title and artist to the online lyric providers you enable (in order: QQ Music, Netease, Kugou, SodaMusic, LrcApi, LRCLIB, Apple Music, Musixmatch) to match and fetch lyrics.

- **Apple Music**: Apple only returns lyrics when the request carries a login token (`media-user-token`) from a signed-in Apple Music web session; anonymous mode can search songs but cannot fetch lyrics. You can sign in inside the app (an in-app WebView) or paste the token manually. The in-app WebView stores cookies in the app's private session only and does not affect your system browser. If you import a `media-user-token`, Capsulyric uses it solely to request lyrics from Apple and to resolve your account region, stores it encrypted on-device with Android Keystore-backed AES-GCM, excludes it from Android backup/device-transfer rules and normal setting exports, and includes it only in a password-encrypted sensitive-data backup entry when you explicitly choose so. The token is short-lived (expires within a few hours); lyrics stop working until you sign in again.
- **Musixmatch**: requests are sent to Musixmatch's desktop lyric endpoint with browser-like headers. Musixmatch may challenge automated requests (captcha); matching depends on song title and artist.

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

### 在线歌词源

开启在线歌词后，Capsulyric 会把当前歌曲的歌名与歌手发送给按顺序启用的在线歌词源（QQ 音乐、网易云、酷狗、SodaMusic、LrcApi、LRCLIB、Apple Music、Musixmatch），用于匹配并获取歌词。

- **Apple Music**：Apple 只有在请求携带已登录 Apple Music 网页会话的登录凭据（media-user-token）时才返回歌词，匿名模式只能搜索到歌曲、无法获取歌词。您可以在应用内通过网页登录（应用内 WebView），也可以手动粘贴该值。应用内 WebView 的 Cookie 仅保存在应用私有会话中，不影响系统浏览器。若您导入 media-user-token，Capsulyric 仅用它向 Apple 请求歌词并解析账号地区，并使用 Android Keystore 支持的 AES-GCM 加密存储在本机；该值排除在 Android 备份/设备迁移规则与常规设置导出之外，仅在您明确选择时写入密码加密的敏感数据备份项。该凭据时效很短（几小时内过期），歌词失效后请重新登录获取。
- **Musixmatch**：请求以浏览器 UA 形式发送到 Musixmatch 桌面歌词接口。Musixmatch 可能会对自动化请求进行验证码挑战；匹配结果取决于歌名与歌手。

---

Back to README / 返回 README: [README.md](../README.md)
