# 实验七 基于 Service 的音乐播放应用

使用 Java 和 Android 原生控件实现本地音乐播放。应用从系统媒体库读取音频，支持播放、暂停、停止、切歌、进度拖动和后台播放通知。

## 构建与试用

1. 用 Android Studio 打开仓库根目录，安装 Android SDK 37，并为 Gradle 选择 JDK 17 或更高版本。
2. 通过 Android Studio 运行 `app`，或在命令行执行 `gradlew.bat :app:assembleDebug`（macOS/Linux 使用 `./gradlew :app:assembleDebug`）。
3. 在 Android 8.0（API 26）或以上的设备上，将几首 MP3 或 WAV 文件复制到 `Music` 文件夹。文件进入系统媒体库后，启动应用并允许访问音频。Android 13 及以上还可以授权显示播放通知。
4. 点击列表选择歌曲。拖动进度条后松手即可跳转。按 Home 返回桌面，音乐会继续播放。点击播放通知可以返回应用。

## 代码结构

- `Songs.java` 通过 `ContentResolver` 查询 `MediaStore.Audio.Media`，生成供列表显示和播放使用的 `content://` URI。
- `MusicService.java` 持有 `MediaPlayer`，通过本地 Binder 提供播放控制和状态查询。播放开始时服务进入前台，切歌时更新通知，停止时释放播放器并移除通知。
- `MainActivity.java` 负责权限请求和界面显示。工作线程查询媒体库并定时调度进度刷新。主线程从 Binder 读取状态并更新界面，拖动时暂缓进度回写。

项目在 Pixel 7 模拟器（Android 14，API 34）上验证。
