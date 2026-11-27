## Tính năng chính

- **Quét hộp thư TikTok tự động:** Tự cuộn và quét danh sách đoạn chat cá nhân & nhóm chat kèm tên hiển thị và ảnh đại diện.
- **Lên lịch chạy ngầm bền bỉ:** Hỗ trợ hẹn giờ cố định hằng ngày hoặc lặp theo chu kỳ giờ, tự khởi chạy lại sau khi khởi động lại máy, tự động bỏ qua các mục đã có chuỗi hôm nay và tùy chọn gửi sớm khi pin yếu.
- **Tùy chỉnh tin nhắn linh hoạt:** Gửi tin nhắn cố định (mặc định `🔥🔥🔥`) hoặc bật chế độ xáo trộn 50 mẫu tin nhắn tự nhiên.
- **Quản lý danh sách & lịch sử:** Nhập/xuất danh sách dạng JSON, thử lại riêng các mục gửi lỗi trong ngày, lưu 50 phiên chạy gần nhất và xuất log chẩn đoán.
- **Giao diện Material 3 Expressive:** Hỗ trợ Dynamic Color (Material You) và bảng màu Flame-Rose & Honey-Ember hài hòa cho cả chế độ Sáng/Tối, tương thích cạnh-tới-cạnh và cử chỉ Predictive Back.
- **100% xử lý cục bộ:** Không máy chủ trung gian, không theo dõi; cookie phiên chỉ lưu trong WebView hệ thống của thiết bị.

## Tải về

Tải file cài đặt **`StreakKeeper-arm64.apk`** (standalone APK, kiến trúc `arm64-v8a`, Android 8.0+) trong mục [Releases](https://github.com/tnyfishy/StreakKeeper/releases).

## Build từ mã nguồn

Yêu cầu **JDK 21** và **Android SDK** (`compileSdk 37`, `build-tools 36.0.0`).

1. Tạo file `local.properties` trỏ tới Android SDK:
   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```
2. *(Tùy chọn)* Tạo file `keystore.properties` (không theo dõi bởi Git) để ký bản release:
   ```properties
   storeFile=/path/to/release.jks
   storePassword=your_store_password
   keyAlias=your_alias
   keyPassword=your_key_password
   ```
3. Build bản release tối ưu R8 (`arm64-v8a`):
   ```bash
   ./gradlew :app:assembleRelease
   ```
   APK xuất ra tại `app/build/outputs/apk/release/app-release.apk`.
