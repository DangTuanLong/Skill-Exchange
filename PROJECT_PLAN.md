# 📋 Kế Hoạch Dự Án — SkillExchange (Khóa Luận Tốt Nghiệp)

> **Mạng Xã Hội Trao Đổi Kỹ Năng** — Nền tảng kết nối người dạy & người học, cho phép trao đổi kỹ năng lẫn nhau dựa trên thuật toán matching thông minh, NLP tiếng Việt, và Knowledge Graph.

---

## 1. Tổng Quan Dự Án

| Hạng mục | Chi tiết |
|---|---|
| **Loại dự án** | Khóa luận tốt nghiệp |
| **Developer** | Solo (1 người) |
| **Nền tảng** | Android Native (Kotlin/Compose), KMP-ready |
| **Thị trường** | Việt Nam |
| **Ngân sách hosting** | $0 — 100% Free Tier |
| **Timeline** | **16 tuần** (~4 tháng) |

### Đối tượng sử dụng
- **Người dạy:** Người có kỹ năng muốn chia sẻ & trao đổi
- **Người học:** Người muốn học kỹ năng mới từ cộng đồng

### 3 Điểm nhấn kỹ thuật cho khóa luận
1. **Matching Algorithm** — Thuật toán ghép cặp đa chiều (skill, proficiency, location, availability)
2. **NLP Pipeline** — Tự động phát hiện kỹ năng từ text tiếng Việt (underthesea + PhoBERT)
3. **Knowledge Graph** — Tìm kiếm trao đổi gián tiếp qua đồ thị kỹ năng (Neo4j)

---

## 2. Tính Năng

### ✅ Tính năng triển khai

| # | Tính năng | Mô tả | Phase |
|---|---|---|---|
| 1 | **Đăng ký & Đăng nhập** | Email + OTP, JWT + Refresh Token | Phase 1 |
| 2 | **Quản lý hồ sơ** | Tạo/sửa/xóa profile, avatar, bio, thành phố | Phase 1 |
| 3 | **Danh mục kỹ năng** | Kỹ năng tôi có (HAVE) & Kỹ năng muốn học (WANT), mức độ 1-5 | Phase 1 |
| 4 | **Tìm kiếm & Bộ lọc** | Search theo danh mục, thành phố, mức độ, thời gian rảnh | Phase 2 |
| 5 | **Thuật toán Matching** | Scoring đa chiều: skill match + proficiency gap + location + availability | Phase 2 |
| 6 | **Booking Flow** | Gửi yêu cầu → Chấp nhận/Từ chối → Đặt lịch hẹn | Phase 2 |
| 7 | **Chat Real-time** | Chat 1-1 qua Firebase Firestore, gửi ảnh/tài liệu | Phase 3 |
| 8 | **Push Notification** | FCM thông báo tin nhắn, yêu cầu kết nối, lịch hẹn | Phase 3 |
| 9 | **Đánh giá & Uy tín** | Rating 1-5 sao, Reputation Score tự động tính | Phase 3 |
| 10 | **Kết bạn** | Follow/Unfollow, danh sách bạn bè | Phase 3 |
| 11 | **NLP Kỹ năng** | Phân tích text → Tự động detect kỹ năng (tiếng Việt) | Phase 4 |
| 12 | **Data Import** | Upload file export Facebook/LinkedIn → parse → gợi ý kỹ năng | Phase 4 |
| 13 | **Knowledge Graph** | Đồ thị quan hệ user–skill, tìm skill loop (A→B→C→A) | Phase 4 |
| 14 | **Dashboard thống kê** | Biểu đồ radar kỹ năng, lịch sử hoạt động, leaderboard | Phase 5 |

### ❌ Tính năng bỏ

| Tính năng | Lý do |
|---|---|
| Web Scraping MXH | Vi phạm ToS Facebook/LinkedIn, rủi ro pháp lý |
| Web App Admin Portal | Quá tải cho solo dev — quản lý qua Supabase Dashboard |
| Dự đoán kỹ năng tương lai | Cần dataset lớn, không đủ thời gian train model |
| Dự báo xu hướng thị trường | Ngoài scope khóa luận |

### ⏸️ Tính năng hoãn (nếu còn thời gian)

| Tính năng | Ghi chú |
|---|---|
| iOS app (KMP) | Architecture đã KMP-ready, chuyển đổi sau tốt nghiệp |
| Gợi ý kết bạn xung quanh (GPS) | Cần location permission + phức tạp UX |
| Video call tích hợp | Quá phức tạp, dùng link Google Meet thay thế |
| Gamification (badges, levels) | Nice-to-have, không critical |

---

## 3. Ngăn Xếp Công Nghệ

### 3.1 Mobile Client — Android Native (KMP-Ready)

| Thành phần | Công nghệ | Phiên bản | Ghi chú |
|---|---|---|---|
| Ngôn ngữ | Kotlin (K2 compiler) | 2.2.10 | |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 | |
| Min SDK | API 26 (Android 8.0) | — | |
| Target SDK | API 37 | — | |
| Java Compatibility | **JDK 17** | — | ⚠️ Cần nâng từ 11 → 17 |
| Networking | Ktor Client | 3.1.+ | KMP-compatible |
| DI | Koin | 4.0.+ | KMP-compatible |
| Image Loading | Coil 3 | 3.1.+ | Compose Multiplatform ready |
| Local DB | Room KMP | 2.7.+ | Offline cache |
| Serialization | kotlinx.serialization | 1.7.+ | |
| Navigation | Compose Navigation | 2.9.+ | KMP-compatible từ 2.8+ |
| State Pattern | **MVI** (Model-View-Intent) | — | Dễ test, explicit state |

### 3.2 Core Backend — Ktor Server

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Framework | Ktor Server | 3.1.+ |
| Async | Kotlin Coroutines | 1.10.+ |
| Database ORM | Exposed | 0.58.+ |
| Auth | JWT + Supabase Auth | — |
| Build | Gradle + Shadow JAR | — |
| Container | Docker | — |

### 3.3 AI & Data Service — Python

| Thành phần | Công nghệ | Phiên bản |
|---|---|---|
| Framework | FastAPI | 0.115.+ |
| Server | Uvicorn | — |
| NLP tiếng Việt | **underthesea** | 6.8.+ |
| NLP nâng cao | **PhoBERT** (via transformers) | — |
| HTML/JSON parsing | BeautifulSoup4 + lxml | — |
| Sentence Embeddings | sentence-transformers (multilingual) | — |

> **Tại sao underthesea + PhoBERT thay vì spaCy?**
> - `spaCy` không có model tiếng Việt chính thức
> - `underthesea` là thư viện NLP tiếng Việt tốt nhất: word segmentation, NER, POS tagging
> - `PhoBERT` (VinAI) là BERT pre-trained cho tiếng Việt, phù hợp cho embedding kỹ năng

### 3.4 Hạ Tầng — 100% Free Tier

| Dịch vụ | Provider | Free Tier | Dùng cho |
|---|---|---|---|
| PostgreSQL | **Supabase** | 500MB DB, Auth miễn phí | Database chính, Auth |
| File Storage | **Cloudflare R2** | 10GB, 0đ egress | CV, tài liệu, avatar |
| Push Notification | **Firebase (FCM)** | Không giới hạn | Thông báo đẩy |
| Chat Real-time | **Firebase Firestore** | 1GB stored, 50K reads/day | Chat 1-1 |
| Backend Ktor | **Render** | 750h/month, 512MB RAM | API Server |
| Backend Python | **Render** | 750h/month, 512MB RAM | AI Service |
| Crash Reporting | **Firebase Crashlytics** | Miễn phí | Monitoring |
| CI/CD | **GitHub Actions** | 2000 min/month | Build + Health ping |
| Graph DB | **Neo4j AuraDB** | 200K nodes | Knowledge Graph (Phase 4) |

> **Anti-sleep strategy cho Render Free Tier:**
> GitHub Actions cron job ping healthcheck mỗi 14 phút, chỉ trong giờ 8:00-23:00 VN. Chấp nhận cold start ~30s ngoài giờ.

---

## 4. Kiến Trúc Hệ Thống

### 4.1 Tổng Quan

```
┌──────────────────┐      REST API       ┌──────────────────┐    gRPC/REST    ┌──────────────────┐
│                  │ ──────────────────▶  │                  │ ──────────────▶ │                  │
│   Android App    │                      │   Ktor API       │                 │  Python AI       │
│   (Kotlin +      │ ◀──────────────────  │   Server         │ ◀────────────── │  Service         │
│    Compose)      │                      │   (Render)       │                 │  (Render)        │
│                  │                      │                  │                 │                  │
└───────┬──────────┘                      └───────┬──────────┘                 └──────────────────┘
        │                                         │
        │  Real-time Chat     ┌───────────┐       │  ORM (Exposed)
        ├────────────────────▶│ Firestore │       │
        │                     └───────────┘       ▼
        │  Push               ┌───────────┐     ┌──────────────────┐
        ├────────────────────▶│ FCM       │     │  PostgreSQL      │
        │                     └───────────┘     │  (Supabase)      │
        │  Auth               ┌───────────┐     └──────────────────┘
        ├────────────────────▶│ Supabase  │
        │                     │ Auth      │     ┌──────────────────┐
        │  File Upload        └───────────┘     │  Neo4j AuraDB    │
        ├────────────────────▶┌───────────┐     │  (Phase 4)       │
        │                     │ Cloudflare│     └──────────────────┘
        │                     │ R2        │
        │                     └───────────┘
```

### 4.2 Cấu Trúc 3 Repositories

#### Repo 1: `skillexchange-android`
```
skillexchange-android/
├── app/                              # Application module (DI wiring, NavHost)
│   └── src/main/java/.../app/
│       ├── SkillExchangeApp.kt
│       ├── MainActivity.kt
│       ├── navigation/               # NavGraph, Route definitions
│       └── di/                       # Koin root modules
├── core/
│   ├── core-network/                 # Ktor Client, API interfaces, DTOs
│   ├── core-database/                # Room DB, DAOs, Entities
│   ├── core-common/                  # Result wrapper, extensions, constants
│   └── core-ui/                      # Theme, Design tokens, shared Composables
├── feature/
│   ├── feature-auth/                 # Login, Register, OTP verification
│   ├── feature-profile/              # Profile management
│   ├── feature-discovery/            # Search, filter, matching results
│   ├── feature-booking/              # Exchange requests, scheduling
│   ├── feature-chat/                 # Messaging UI (Firestore)
│   ├── feature-rating/               # Rating & review
│   └── feature-dashboard/            # Statistics & charts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

> **Lưu ý:** Ban đầu có thể giữ single module nhưng **BẮT BUỘC** tuân thủ package structure trên. Khi codebase > 50 files thì refactor thành multi-module. Domain layer **KHÔNG ĐƯỢC** import `android.*`.

#### Repo 2: `skillexchange-api`
```
skillexchange-api/
├── src/main/kotlin/com/skillexchange/api/
│   ├── Application.kt                # Ktor entry point
│   ├── plugins/                      # Routing, Serialization, Auth, CORS
│   ├── routes/
│   │   ├── AuthRoutes.kt
│   │   ├── ProfileRoutes.kt
│   │   ├── SkillRoutes.kt
│   │   ├── MatchingRoutes.kt
│   │   ├── BookingRoutes.kt
│   │   └── RatingRoutes.kt
│   ├── models/                       # Database table definitions (Exposed)
│   ├── services/                     # Business logic
│   ├── utils/                        # JWT helper, validation, error handling
│   └── config/                       # Environment, DB connection
├── Dockerfile
├── build.gradle.kts
└── render.yaml
```

#### Repo 3: `skillexchange-ai`
```
skillexchange-ai/
├── app/
│   ├── main.py                       # FastAPI entry
│   ├── routers/
│   │   ├── nlp_router.py             # Skill extraction endpoints
│   │   └── matching_router.py        # Compatibility scoring
│   ├── services/
│   │   ├── skill_extractor.py        # underthesea + PhoBERT pipeline
│   │   ├── pii_anonymizer.py         # PII removal
│   │   └── compatibility_scorer.py   # Multi-dimensional matching
│   ├── models/                       # Pydantic schemas
│   └── config.py
├── Dockerfile
├── requirements.txt
└── render.yaml
```

### 4.3 Database Schema (PostgreSQL — Supabase)

```
┌─────────────────────┐       ┌─────────────────────┐
│       users          │       │   skill_categories   │
├─────────────────────┤       ├─────────────────────┤
│ id          UUID PK │       │ id          INT  PK  │
│ email       TEXT UQ │       │ name        TEXT     │
│ password_hash TEXT  │       │ icon        TEXT     │
│ created_at  TIMESTAMP│      └──────────┬──────────┘
│ last_login  TIMESTAMP│                 │ 1:N
│ reputation_score FLOAT│     ┌──────────▼──────────┐
└──────┬──────────────┘       │       skills         │
       │                      ├─────────────────────┤
       │ 1:1                  │ id          INT  PK  │
┌──────▼──────────────┐       │ category_id INT  FK  │
│      profiles        │       │ name        TEXT     │
├─────────────────────┤       │ description TEXT     │
│ id          UUID PK │       └──────────┬──────────┘
│ user_id     UUID FK │                  │
│ full_name   TEXT    │                  │
│ avatar_url  TEXT    │       ┌──────────▼──────────┐
│ bio         TEXT    │       │     user_skills      │
│ city        TEXT    │       ├─────────────────────┤
│ availability JSONB  │       │ id          UUID PK  │
│ updated_at  TIMESTAMP│      │ user_id     UUID FK  │◀── users.id
└─────────────────────┘       │ skill_id    INT  FK  │
                              │ type     ENUM(HAVE/WANT)│
       ┌─────────────────┐    │ proficiency_level INT│
       │  friendships     │    │ note        TEXT     │
       ├─────────────────┤    └─────────────────────┘
       │ id       UUID PK│
       │ user_id  UUID FK│
       │ friend_id UUID FK│   ┌─────────────────────┐
       │ status ENUM     │    │  exchange_requests   │
       │ created_at TS   │    ├─────────────────────┤
       └─────────────────┘    │ id            UUID PK│
                              │ sender_id     UUID FK│
       ┌─────────────────┐    │ receiver_id   UUID FK│
       │    ratings       │    │ skill_offered_id INT FK│
       ├─────────────────┤    │ skill_wanted_id  INT FK│
       │ id       UUID PK│    │ status ENUM          │
       │ exchange_id UUID FK│◀─┤  (PENDING/ACCEPTED/  │
       │ reviewer_id UUID FK│  │   REJECTED/COMPLETED/│
       │ reviewee_id UUID FK│  │   CANCELLED)         │
       │ score    INT(1-5)│   │ scheduled_at  TS     │
       │ comment  TEXT    │    │ message       TEXT    │
       │ created_at TS   │    │ created_at    TS     │
       └─────────────────┘    └─────────────────────┘
```

**Quan hệ chính:**
- `users` 1:1 `profiles`
- `users` 1:N `user_skills`
- `users` 1:N `exchange_requests` (cả sender & receiver)
- `exchange_requests` 1:0..1 `ratings`
- `skill_categories` 1:N `skills`
- `users` N:N `friendships`

---

## 5. Lộ Trình Triển Khai — 16 Tuần

```
Tuần:  1    2    3    4    5    6    7    8    9    10   11   12   13   14   15   16
       ├─────────────────┤─────────────────┤─────────────────┤─────────────────┤──────────────────┤
       │   PHASE 1       │   PHASE 2       │   PHASE 3       │   PHASE 4       │   PHASE 5        │
       │ Foundation &    │ Discovery &     │ Communication   │ AI &            │ Polish &         │
       │ Authentication  │ Matching        │ & Rating        │ Intelligence    │ Release          │
       ├─────────────────┤─────────────────┤─────────────────┤─────────────────┤──────────────────┤
```

> **Kế hoạch backup:** Phase 1-3 (10 tuần) tạo thành MVP hoàn chỉnh đủ để bảo vệ khóa luận. Phase 4-5 là bonus để ghi điểm thêm.

---

### Phase 1: Foundation & Authentication (Tuần 1-4)

**Mục tiêu:** Người dùng có thể đăng ký, đăng nhập, tạo hồ sơ kỹ năng.

#### Tuần 1 — Project Setup
- [ ] Nâng `compileOptions` từ Java 11 → **Java 17**
- [ ] Thiết lập package structure Clean Architecture (`core/`, `domain/`, `data/`, `presentation/`)
- [ ] Cấu hình Koin DI, Ktor Client, Compose Navigation
- [ ] Design System: Material 3 theme, color palette, typography
- [ ] Tạo Supabase project, bật Auth, tạo database schema
- [ ] Tạo repo `skillexchange-api`, scaffold Ktor Server project
- [ ] Health check endpoint + Dockerfile

#### Tuần 2 — Authentication Backend
- [ ] Ktor routes: `/auth/register`, `/auth/login`, `/auth/verify-otp`, `/auth/refresh`
- [ ] JWT access token (15 min) + Refresh token (7 days)
- [ ] Supabase Auth integration (Email OTP)
- [ ] Password hashing (bcrypt)
- [ ] Input validation & error responses
- [ ] Unit tests cho auth service

#### Tuần 3 — Authentication Mobile
- [ ] Onboarding screens (3 slides giới thiệu app)
- [ ] Login screen (Email + Password)
- [ ] Register screen (Email + OTP verification)
- [ ] Token storage (EncryptedSharedPreferences)
- [ ] Auto-refresh token interceptor (Ktor Client plugin)
- [ ] Navigation guard (redirect to login if unauthenticated)

#### Tuần 4 — Profile & Skills
- [ ] Backend: CRUD endpoints cho Profile, Skills, UserSkills
- [ ] Mobile: Tạo hồ sơ cá nhân (tên, avatar, bio, thành phố)
- [ ] Mobile: Chọn "Kỹ năng tôi có" + "Kỹ năng tôi muốn học" từ danh mục
- [ ] Mobile: Slider chọn mức độ thành thạo (1-5)
- [ ] Seed data: Danh mục kỹ năng (Lập trình, Ngoại ngữ, Âm nhạc, Thiết kế, v.v.)
- [ ] Room cache cho Profile offline
- [ ] Unit tests cho Profile use cases

**✅ Deliverable:** App chạy được → Đăng ký → Đăng nhập → Tạo hồ sơ kỹ năng

---

### Phase 2: Discovery & Matching (Tuần 5-7)

**Mục tiêu:** Người dùng tìm kiếm, xem profile người khác, gửi yêu cầu trao đổi.

#### Tuần 5 — Search & Filter
- [ ] Backend: `/skills/search` API với filter (category, city, proficiency, availability)
- [ ] Backend: Full-text search PostgreSQL (`tsvector` + `tsquery` cho tiếng Việt)
- [ ] Mobile: Màn hình Discovery — danh sách cards người dùng
- [ ] Mobile: Bộ lọc (danh mục, thành phố, mức độ)
- [ ] Mobile: Profile Detail screen (xem kỹ năng, bio, rating)
- [ ] Pagination (cursor-based)

#### Tuần 6 — Matching Algorithm
- [ ] Backend: Thuật toán Compatibility Score
  - **Skill Match:** A có kỹ năng mà B muốn học VÀ ngược lại → score cao
  - **Proficiency Gap:** Chênh lệch 1-2 level là tốt nhất
  - **Location:** Cùng thành phố → bonus
  - **Availability Overlap:** Thời gian rảnh trùng nhau → bonus
  - **Công thức:** `Score = w1*SkillMatch + w2*ProficiencyFit + w3*LocationBonus + w4*AvailabilityOverlap`
- [ ] Backend: `/matching/suggestions` endpoint — trả top-N người phù hợp nhất
- [ ] Mobile: Tab "Gợi ý cho bạn" hiển thị sorted by compatibility score
- [ ] Unit tests cho matching algorithm

#### Tuần 7 — Booking Flow
- [ ] Backend: CRUD cho `exchange_requests`
- [ ] Backend: State machine (PENDING → ACCEPTED/REJECTED → COMPLETED/CANCELLED)
- [ ] Mobile: Nút "Gửi yêu cầu trao đổi" trên Profile Detail
- [ ] Mobile: Chọn kỹ năng muốn trao đổi + thời gian đề xuất
- [ ] Mobile: Tab "Yêu cầu" — danh sách incoming/outgoing requests
- [ ] Mobile: Accept/Reject flow
- [ ] FCM Push Notification khi có request mới
- [ ] Unit tests cho booking state machine

**✅ Deliverable:** Tìm kiếm → Xem profile → Gợi ý matching → Gửi yêu cầu → Chấp nhận/Từ chối

---

### Phase 3: Communication & Rating (Tuần 8-10)

**Mục tiêu:** Chat real-time, đánh giá sau phiên trao đổi.

#### Tuần 8 — Chat (Firebase Firestore)
- [ ] Firestore schema: `chats/{chatId}/messages/{messageId}`
- [ ] Backend: Tạo chat room khi exchange request được ACCEPTED
- [ ] Mobile: Chat list screen (danh sách cuộc hội thoại)
- [ ] Mobile: Chat detail screen (real-time messages via Firestore listener)
- [ ] Firestore Security Rules (chỉ 2 người trong chat mới đọc/ghi)
- [ ] Offline support (Firestore built-in offline persistence)

#### Tuần 9 — Chat Nâng Cao + Notifications
- [ ] Message types: Text, Image (upload → R2 → share URL)
- [ ] Read receipts (last read timestamp per user)
- [ ] FCM push notification khi có tin nhắn mới
- [ ] Typing indicator (Firestore presence)
- [ ] Mobile: Chia sẻ tài liệu (PDF/image upload → Cloudflare R2)
- [ ] Notification center screen (tổng hợp tất cả notifications)

#### Tuần 10 — Rating & Reputation
- [ ] Backend: Rating endpoints (submit rating after COMPLETED exchange)
- [ ] Backend: Reputation Score algorithm
  - `ReputationScore = AVG(ratings) * 0.6 + CompletionRate * 0.3 + ActivityBonus * 0.1`
  - CompletionRate = Completed / (Completed + Cancelled)
- [ ] Mobile: Rating dialog (1-5 sao + comment) sau khi đánh dấu phiên hoàn thành
- [ ] Mobile: Hiển thị reputation badge trên profile (Mới, Đáng tin cậy, Xuất sắc)
- [ ] Friend system: Follow/Unfollow, danh sách bạn bè
- [ ] Unit tests cho reputation algorithm

**✅ Deliverable:** Chat real-time + Đánh giá + Hệ thống uy tín

---

### Phase 4: AI & Intelligence (Tuần 11-13)

**Mục tiêu:** NLP tự động phân tích kỹ năng, Knowledge Graph.

> ⭐ **Đây là phần tạo điểm nhấn cho khóa luận.** Hội đồng sẽ quan tâm thuật toán, kiến trúc AI pipeline, và kết quả demo.

#### Tuần 11 — Python NLP Service
- [ ] Tạo repo `skillexchange-ai`, scaffold FastAPI project
- [ ] Endpoint `/nlp/extract-skills`: Nhận text bio → trả danh sách kỹ năng được detect
  - `underthesea` word segmentation cho tiếng Việt
  - Keyword extraction + Named Entity Recognition
  - Mapping từ khóa → skill categories trong DB
- [ ] Endpoint `/nlp/embed-text`: Tạo vector embedding cho text (sentence-transformers multilingual)
- [ ] Dockerfile + deploy lên Render Free
- [ ] Unit tests cho NLP pipeline
- [ ] API documentation (FastAPI auto-generates Swagger)

#### Tuần 12 — Data Import & Auto Profile
- [ ] Mobile: Upload file JSON/ZIP export từ Facebook/LinkedIn
- [ ] Upload flow: File → Cloudflare R2 → Backend trigger → Python service parse
- [ ] Python: Parser cho Facebook data export (posts, groups, education, work)
- [ ] Python: PII Anonymization (loại bỏ email, SĐT, địa chỉ cụ thể)
- [ ] Python: Trích xuất kỹ năng từ parsed data → gợi ý cho user
- [ ] Mobile: Màn hình "Xác nhận kỹ năng được phát hiện" — user duyệt 1 chạm
- [ ] Viết tài liệu pipeline cho khóa luận

#### Tuần 13 — Knowledge Graph (Neo4j)
- [ ] Setup Neo4j AuraDB Free
- [ ] Sync data: PostgreSQL → Neo4j (users, skills, relationships)
- [ ] Cypher queries:
  - Tìm đường đi ngắn nhất giữa 2 user qua skill graph
  - Tìm skill loop (A→B→C→A: trao đổi vòng tròn)
  - Gợi ý kỹ năng liên quan dựa trên graph patterns
- [ ] Backend: `/graph/suggest-path` endpoint
- [ ] Mobile: Visualize skill graph đơn giản (Canvas trong Compose)
- [ ] Tài liệu graph algorithms cho khóa luận

**✅ Deliverable:** NLP tự động detect kỹ năng + Knowledge Graph tìm matching gián tiếp

---

### Phase 5: Polish & Release (Tuần 14-16)

**Mục tiêu:** Dashboard, testing, tối ưu, đóng gói, tài liệu khóa luận.

#### Tuần 14 — Dashboard & Statistics
- [ ] Mobile: Dashboard cá nhân
  - Biểu đồ radar: Mức độ thành thạo các kỹ năng
  - Biểu đồ cột: Số phiên trao đổi theo tháng
  - Timeline: Lịch sử hoạt động
- [ ] Mobile: Leaderboard (top users by reputation)
- [ ] Gợi ý lộ trình học tập dựa trên skill gap analysis
- [ ] Dark mode support

#### Tuần 15 — Testing & Security
- [ ] Unit tests: Cover tối thiểu 70% domain + data layer
- [ ] Integration tests: API endpoints chính
- [ ] UI tests: Critical flows (Login → Search → Book → Chat → Rate)
- [ ] Security:
  - Input sanitization (SQL injection, XSS)
  - Rate limiting trên API
  - HTTPS enforcement
  - Sensitive data encryption
- [ ] Firebase Crashlytics integration
- [ ] Performance profiling (Compose recomposition, memory leaks)

#### Tuần 16 — Release & Documentation
- [ ] Build APK/AAB (Release signed)
- [ ] Viết README cho cả 3 repos
- [ ] API documentation hoàn chỉnh (Swagger/OpenAPI)
- [ ] Tài liệu khóa luận:
  - Kiến trúc hệ thống (diagrams)
  - Thuật toán matching (công thức + evaluation)
  - NLP pipeline (flow chart + accuracy metrics)
  - Knowledge Graph (use cases + Cypher examples)
  - Screenshots + User flow
- [ ] Chuẩn bị demo scenario (seed data, fake users for demonstration)
- [ ] GitHub Actions: CI pipeline (build + test + lint)
- [ ] Anti-sleep cron job cho Render

**✅ Deliverable:** App hoàn chỉnh + APK + Tài liệu khóa luận

---

## 6. Thuật Toán Chính

### 6.1 Compatibility Score (Matching)

```
Score = w1 * SkillMatch + w2 * ProficiencyFit + w3 * LocationBonus + w4 * AvailabilityOverlap

Trong đó:
  w1 = 0.4  (Trọng số skill match — quan trọng nhất)
  w2 = 0.25 (Trọng số mức độ phù hợp)
  w3 = 0.15 (Trọng số vị trí)
  w4 = 0.20 (Trọng số thời gian rảnh)

SkillMatch:
  - A có skill mà B muốn HỌC → +0.5
  - B có skill mà A muốn HỌC → +0.5
  - Cả hai chiều đều match  → 1.0 (trao đổi lý tưởng)

ProficiencyFit:
  - |LevelA - LevelB| = 1-2  → 1.0 (chênh lệch vừa phải, lý tưởng)
  - |LevelA - LevelB| = 0    → 0.6 (ngang nhau, ít lợi ích)
  - |LevelA - LevelB| = 3    → 0.4
  - |LevelA - LevelB| >= 4   → 0.2 (quá xa)

LocationBonus:
  - Cùng thành phố → 1.0
  - Khác thành phố → 0.3

AvailabilityOverlap:
  - Tính dựa trên intersection của availability JSONB arrays
  - overlap_hours / max_possible_hours → [0, 1]
```

### 6.2 Reputation Score

```
ReputationScore = AVG(ratings) * 0.6 + CompletionRate * 0.3 + ActivityBonus * 0.1

CompletionRate = Completed / (Completed + Cancelled)
ActivityBonus  = min(1.0, total_exchanges / 20)  // Cap tại 20 phiên

Badges:
  - Score < 2.0 hoặc < 3 phiên  → "Thành viên mới"
  - Score >= 3.5 và >= 5 phiên   → "Đáng tin cậy"
  - Score >= 4.5 và >= 15 phiên  → "Xuất sắc"
```

### 6.3 NLP Skill Extraction Pipeline

```
Input Text (tiếng Việt)
       │
       ▼
┌─────────────────┐
│  Word Segmentation  │  ← underthesea.word_tokenize()
│  "lập trình web"    │
└───────┬─────────┘
        │
        ▼
┌─────────────────┐
│  POS Tagging    │  ← underthesea.pos_tag()
│  Filter NOUN/VERB│
└───────┬─────────┘
        │
        ▼
┌─────────────────┐
│  NER             │  ← Detect SKILL, ORG, TOOL entities
└───────┬─────────┘
        │
        ▼
┌─────────────────┐
│  Skill Mapping   │  ← Match against skill dictionary DB
│  + Embedding     │  ← PhoBERT cosine similarity for fuzzy match
└───────┬─────────┘
        │
        ▼
Output: [{skill_id, skill_name, confidence}]
```

---

## 7. Chiến Lược Demo Cho Khóa Luận

### Trước ngày demo
- [ ] Seed **30-50 fake users** với skills đa dạng để demo matching algorithm
- [ ] Chuẩn bị **2 điện thoại / emulator** để demo chat real-time
- [ ] Ping Render services **5 phút trước demo** (tránh cold start)
- [ ] Chuẩn bị **video backup** phòng trường hợp mất mạng
- [ ] Chuẩn bị **1 file Facebook export mẫu** để demo auto-skill detection

### Flow demo đề xuất (10-15 phút)

| Bước | Nội dung | Thời gian | Điểm nhấn |
|---|---|---|---|
| 1 | Đăng ký → OTP → Tạo profile | 2 phút | |
| 2 | Import Facebook data → NLP detect skills → Confirm 1 chạm | 2 phút | ⭐ AI Pipeline |
| 3 | Search + Filter → Xem gợi ý matching → Gửi request | 2 phút | ⭐ Matching Algorithm |
| 4 | Accept request (device 2) → Chat real-time | 2 phút | |
| 5 | Hoàn thành phiên → Rating → Reputation update | 1 phút | |
| 6 | Knowledge Graph → Tìm skill loop → Visualize | 2 phút | ⭐ Graph Algorithm |
| 7 | Dashboard → Biểu đồ radar → Thống kê | 1 phút | |

---

## 8. Rủi Ro & Giải Pháp

| # | Rủi ro | Xác suất | Tác động | Giải pháp |
|---|---|---|---|---|
| 1 | Render cold start khi demo | Cao | Cao | Ping 5 phút trước, video backup |
| 2 | Supabase free tier hết bandwidth | Thấp | Cao | 5GB/month đủ cho dev + demo |
| 3 | NLP accuracy thấp cho tiếng Việt | Trung bình | Trung bình | Kết hợp rule-based + ML, chuẩn bị từ điển kỹ năng |
| 4 | Neo4j AuraDB free bị ngưng | Thấp | Trung bình | Fallback: PostgreSQL recursive CTE |
| 5 | Không kịp deadline | Trung bình | Cao | Phase 1-3 là MVP đủ bảo vệ |
| 6 | Firebase Firestore hết quota | Thấp | Trung bình | 50K reads/day đủ cho demo |

---

## 9. Tóm Tắt

```
Dự án:     SkillExchange — Mạng Xã Hội Trao Đổi Kỹ Năng
Loại:      Khóa luận tốt nghiệp (Solo developer)
Timeline:  16 tuần (5 phases)
Tech:      Kotlin/Compose → Ktor Server → Python FastAPI
DB:        PostgreSQL (Supabase) + Neo4j (Phase 4) + Firestore (Chat)
Hosting:   100% Free Tier (Render + Supabase + Firebase + Cloudflare R2)
MVP:       Phase 1-3 (10 tuần) — đủ để bảo vệ khóa luận
Full:      Phase 1-5 (16 tuần) — bao gồm AI + Knowledge Graph
```

---

*Tài liệu này được tạo ngày 21/09/2026. Cập nhật khi có thay đổi kế hoạch.*
