# Viettel Checker

Ứng dụng Android đơn giản: bấm nút để soạn tin nhắn **"TK"** gửi tới đầu số **191**
(dịch vụ tra cứu tài khoản chính thức của Viettel), sau đó lắng nghe tin nhắn phản hồi
từ 191 và hiển thị nội dung ngay trong ứng dụng.

Ứng dụng chỉ thao tác trên SIM đang lắp trong máy đang chạy app — không truy cập,
không đăng nhập, không kiểm tra tài khoản của người khác.

## Build

```
./gradlew assembleDebug
```

APK debug xuất ra tại `app/build/outputs/apk/debug/app-debug.apk`.

## Quyền sử dụng

- `SEND_SMS` — để gửi tin nhắn "TK" tới 191.
- `RECEIVE_SMS` — để nhận và đọc tin nhắn phản hồi từ 191.

Ứng dụng yêu cầu các quyền này khi người dùng bấm nút kiểm tra lần đầu.
