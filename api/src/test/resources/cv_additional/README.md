# Thư mục CV Bổ sung (Additional CV Benchmark Dataset)

Thư mục này dùng để chứa các tệp CV hư cấu bổ sung phục vụ việc đánh giá mở rộng độ chính xác trích xuất kỹ năng độc lập với bộ 24 CV ban đầu.

## Cách tạo tệp CV mới:
Tạo tệp văn bản có đuôi `.txt` hoặc `.md` (ví dụ: `CV_ADD_01.txt`, `CV_ADD_02.md`).

### Cấu trúc định dạng hỗ trợ:
Bạn có thể định nghĩa nhãn Ground Truth ở đầu tệp theo 1 trong 2 cách sau:

#### Cách 1: Frontmatter (khuyến nghị)
```yaml
---
id: CV-ADD-01
title: Kỹ sư Dữ liệu & AI
have: Python, SQL, Tiếng Anh
want: AI/ML
negative: Java, Flutter
org: Học viện Ngân hàng
---
HỌ TÊN: NGUYỄN VĂN A
Vị trí: Data Engineer
KỸ NĂNG:
- Lập trình thành thạo Python và xử lý truy vấn SQL.
- Tiếng Anh giao tiếp tốt trong công việc.
MỤC TIÊU:
- Muốn học thêm về AI/ML.
LƯU Ý:
- Chưa từng làm việc với Java và Flutter.
```

#### Cách 2: Dòng chú thích bắt đầu bằng `#`
```text
# ID: CV-ADD-01
# TITLE: Kỹ sư Dữ liệu & AI
# HAVE: Python, SQL, Tiếng Anh
# WANT: AI/ML
# NEGATIVE: Java, Flutter
# ORG: Học viện Ngân hàng

HỌ TÊN: NGUYỄN VĂN A
...
```

### Các trường nhãn Ground Truth:
- `id`: Mã định danh CV (ví dụ `CV-ADD-01`).
- `title`: Tiêu đề mô tả ứng viên/bối cảnh.
- `have` (hoặc `expectedHave`): Danh sách kỹ năng ứng viên THỰC SỰ CÓ (ngăn cách bởi dấu phẩy `,`).
- `want` (hoặc `expectedWant`): Danh sách kỹ năng ứng viên muốn học (tùy chọn).
- `negative` (hoặc `negativeSkills`): Danh sách kỹ năng bị phủ định trong văn bản (tùy chọn).
- `org` (hoặc `orgSkills`): Kỹ năng xuất hiện trong tên trường, cơ quan, công ty, câu lạc bộ (tùy chọn).

