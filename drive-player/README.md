# Drive Player

App nghe nhạc YouTube (chỉ audio) cho **Android Auto** và **Apple CarPlay**. App chạy song song với
**Google Maps / VietMap Live**: bản đồ ở một bên, thẻ nhạc ở bên còn lại (Android Auto: chia màn hình "Coolwalk",
CarPlay: Dashboard). Bạn không cần tự làm bản đồ trong app.

> ⚠️ **Chỉ dùng cá nhân.** Phát YouTube ngầm và lấy stream trực tiếp vi phạm điều khoản của YouTube.
> Đừng đưa app lên Google Play / App Store. Nếu bạn có YouTube Premium thì YouTube Music đã làm được
> việc này một cách hợp lệ.

```
drive-player/
  android/   Kotlin + Media3 (MediaLibraryService) + NewPipeExtractor
  ios/       Swift + CarPlay templates + AVPlayer + YouTubeKit
```

## Tính năng

| | Android Auto | CarPlay |
|---|---|---|
| Playlist / Gần đây | ✅ | ✅ |
| "Tìm nhanh" (từ khóa lưu sẵn từ điện thoại) | ✅ | ✅ |
| Tìm kiếm bằng bàn phím / giọng nói trên xe | ✅ ("Play … on Drive Player") | ❌ (Apple không cho app nhạc dùng bàn phím) |
| Next / Prev / Pause trên vô lăng | ✅ | ✅ |
| Tự giảm tiếng khi Maps đọc chỉ đường | ✅ | ✅ |
| Cần API key | Không | YouTube Data API v3 (miễn phí) để tìm kiếm |

---

## Android Auto

### Build và cài lên điện thoại
1. Cài [Android Studio](https://developer.android.com/studio), chọn **Open** thư mục `drive-player/android`.
2. Cắm điện thoại (đã bật *USB debugging*), bấm **Run ▶**.
   Hoặc dùng dòng lệnh: `./gradlew installDebug`.

### Bật app "lạ" trên Android Auto (bắt buộc vì app không tải từ Play Store)
1. Trên điện thoại vào **Cài đặt → Android Auto** (hoặc mở app Android Auto).
2. Kéo xuống cuối, bấm **Phiên bản (Version)** khoảng 10 lần → đồng ý bật **Developer settings**.
3. Bấm ⋮ → **Developer settings** → tick **Unknown sources**.

### Test khi không có xe: Desktop Head Unit (DHU)
1. Android Studio → **Tools → SDK Manager → SDK Tools** → tick **Android Auto Desktop Head Unit Emulator** → Apply.
2. Trên điện thoại: Android Auto → ⋮ → **Start head unit server**.
3. Trên máy tính:
   ```bash
   adb forward tcp:5277 tcp:5277
   cd $ANDROID_SDK_ROOT/extras/google/auto   # Windows: %LOCALAPPDATA%\Android\Sdk\extras\google\auto
   ./desktop-head-unit                        # Windows: desktop-head-unit.exe
   ```
4. Cửa sổ màn hình xe giả lập hiện lên. Mở **Google Maps** (hoặc VietMap nếu bản của bạn hỗ trợ Android Auto),
   rồi mở **Drive Player** → chọn một bài. Thẻ nhạc sẽ hiện cạnh bản đồ.

---

## CarPlay (cần máy Mac)

### Build
```bash
brew install xcodegen
cd drive-player/ios
xcodegen generate
open DrivePlayer.xcodeproj
```
- Trong Xcode → target **DrivePlayer → Signing & Capabilities**, chọn Team (Apple ID của bạn) và đổi Bundle ID nếu bị trùng.
- Mở app → ⚙️ **Cài đặt** → dán **YouTube Data API key**. Cách lấy key: [console.cloud.google.com](https://console.cloud.google.com)
  → tạo project → *APIs & Services → Library* → bật **YouTube Data API v3** → *Credentials → Create API key*.

### Test khi không có xe: CarPlay Simulator
- **Cách 1 (dễ nhất):** chạy app trên iOS Simulator → menu **I/O → External Displays → CarPlay**.
  Cửa sổ CarPlay hiện ra. Mở Maps + Drive Player để xem Dashboard.
- **Cách 2:** tải **Additional Tools for Xcode** tại developer.apple.com/download → mở app **CarPlay Simulator**
  → cắm iPhone thật vào Mac.

### Chạy trên iPhone thật với xe/màn hình thật
- Bạn cần entitlement `com.apple.developer.carplay-audio` do Apple cấp
  (gửi form "CarPlay entitlement request" trên developer.apple.com, cần tài khoản Developer trả phí). Trên Simulator thì không cần xin.
- Apple ID miễn phí: app hết hạn sau **7 ngày**, phải build lại. Tài khoản Developer giá $99/năm: app dùng được 1 năm.

---

## Test thực tế không cần xe
Mua một **màn hình CarPlay/Android Auto gắn ngoài** (loại gắn taplo, khoảng 1–2 triệu trên Shopee/Lazada),
cắm nguồn 12V hoặc adapter là test được giống hệt trên xe.

## Khi app bị hỏng
YouTube thường xuyên thay đổi, làm các thư viện lấy stream bị lỗi:
- **Android:** sửa dòng `newpipe = "..."` trong `android/gradle/libs.versions.toml` thành bản mới nhất
  ([releases](https://github.com/TeamNewPipe/NewPipeExtractor/releases)), rồi build lại.
- **iOS:** Xcode → **File → Packages → Update to Latest Package Versions**.

## Cấu trúc code
| File | Vai trò |
|---|---|
| `android/.../data/BrowseTree.kt` | Cây duyệt nhạc hiện trên Android Auto (có unit test) |
| `android/.../playback/PlaybackService.kt` | Media3 `MediaLibraryService`: phát ngầm, tìm kiếm, hàng chờ |
| `android/.../source/YouTubeSource.kt` | Tìm kiếm và lấy stream audio qua NewPipeExtractor |
| `android/.../ui/MainActivity.kt` | Màn hình điện thoại (Compose) |
| `ios/DrivePlayer/CarPlay/CarPlaySceneDelegate.swift` | Giao diện CarPlay (tab Playlist / Gần đây / Tìm nhanh) |
| `ios/DrivePlayer/Playback/PlayerManager.swift` | AVPlayer, hàng chờ, Now Playing, nút điều khiển |
| `ios/DrivePlayer/Source/YouTubeSource.swift` | Tìm kiếm qua Data API, lấy stream qua YouTubeKit |
| `ios/DrivePlayer/UI/ContentView.swift` | Màn hình điện thoại (SwiftUI) |

Muốn đổi nguồn nhạc (file MP3 trên máy, server riêng...) chỉ cần viết một class mới implement
`AudioSource` (Android) hoặc protocol `AudioSource` (iOS).
