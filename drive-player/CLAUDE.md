# Drive Player — ghi chú cho Claude

App nghe nhạc YouTube (chỉ audio) cho Android Auto và Apple CarPlay, **chỉ dùng cá nhân** (sideload, không lên
store, vì phát YouTube ngầm vi phạm điều khoản YouTube). Chủ project nói tiếng Việt và dùng Windows.
Hãy trả lời bằng tiếng Việt.

## Kiến trúc
- Đây là **media app**. App không tự vẽ bản đồ: Android Auto (chia màn hình Coolwalk) và CarPlay (Dashboard)
  tự hiện Google Maps / VietMap bên cạnh thẻ nhạc của app.
- Trên xe không được hiện video, nên app chỉ phát audio.
- Nguồn nhạc nằm sau interface `AudioSource` (có ở cả Android và iOS). Muốn đổi nguồn chỉ cần viết implementation mới.

## Cấu trúc
```
android/  Kotlin, Media3 MediaLibraryService, NewPipeExtractor, Compose
  app/src/main/java/com/driveplayer/
    data/BrowseTree.kt         cây duyệt nhạc trên xe: root → playlist | recents | queries → q:<từ khóa>
                               mediaId = "<parentId>|<videoId>". Thuần Kotlin, có unit test.
    data/LibraryStore.kt       interface + InMemoryLibraryStore
    data/PrefsLibraryStore.kt  lưu bằng SharedPreferences (JSON)
    playback/PlaybackService.kt  MediaLibraryService: browse, search, voice play (searchQuery), hàng chờ
                               URI "yt:<videoId>" được đổi sang link stream thật lúc tải (ResolvingDataSource)
    source/YouTubeSource.kt    tìm kiếm + lấy stream qua NewPipeExtractor (không cần API key)
    ui/MainActivity.kt         màn hình điện thoại (Compose), nối vào service qua MediaController
  app/src/test/.../BrowseTreeTest.kt
ios/      Swift, CarPlay templates, AVPlayer, YouTubeKit (SPM); project.yml dùng cho XcodeGen
  DrivePlayer/CarPlay/CarPlaySceneDelegate.swift  tab Playlist / Gần đây / Tìm nhanh + CPNowPlayingTemplate
  DrivePlayer/Playback/PlayerManager.swift        hàng chờ, MPRemoteCommandCenter, Now Playing
  DrivePlayer/Source/YouTubeSource.swift          tìm kiếm qua YouTube Data API v3 (cần key), stream qua YouTubeKit
```

## Build Android trên Windows
- Cần JDK 17+ và Android SDK (không cần Android Studio). Cài các gói SDK sau:
  `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`.
- Tạo file `android/local.properties` với nội dung: `sdk.dir=C:\\Users\\<user>\\AppData\\Local\\Android\\Sdk`
- `cd android` rồi chạy `build.bat`. Script build, chạy test và copy APK ra `android\DrivePlayer.apk`.
- Muốn chạy từng lệnh riêng: `gradlew.bat assembleDebug`, `gradlew.bat testDebugUnitTest`, `gradlew.bat installDebug`
  (lệnh cuối cài APK lên điện thoại đang cắm USB).

## Test không cần xe
- Dùng DHU (`extras;google;auto`): `adb forward tcp:5277 tcp:5277` rồi chạy
  `%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe`.
- Trên điện thoại, mở cài đặt Android Auto, bấm "Phiên bản" 10 lần để bật Developer settings.
  Sau đó tick "Unknown sources" và bấm "Start head unit server".
- iOS: cần Mac. Chạy `brew install xcodegen && xcodegen generate`, rồi dùng Simulator: I/O → External Displays → CarPlay.

## Hiện trạng (2026-11)
- Android: `assembleDebug` build được, 7/7 unit test qua. **Chưa ai chạy trên máy thật hay DHU**, cũng chưa thử
  tìm kiếm/phát YouTube thật (môi trường cloud đã build app thì bị chặn YouTube).
- iOS: chưa compile lần nào.

## Việc tiếp theo
1. Cài APK, thử tìm và phát nhạc trên điện thoại, sau đó thử trên DHU cùng với Maps.
2. Nếu NewPipe lỗi (YouTube đổi cấu trúc): nâng `newpipe` trong `android/gradle/libs.versions.toml`
   lên bản mới nhất tại github.com/TeamNewPipe/NewPipeExtractor/releases.
3. Build iOS trên Mac và sửa lỗi compile nếu có.
