-- ============================================================
-- SkillExchange — Supabase Database Schema (v2)
-- Phiên bản đã đối chiếu với Exposed runtime + các quyết định đã chốt
-- Cập nhật: 2026-10-05 (TASK-005, TASK-010, TASK-007)
--
-- Quyết định đã ghi nhận:
--   DEC-006: Không lưu password trong app. Auth = Supabase.
--   DEC-007: Schema quản lý tay trên Supabase Dashboard.
--   DEC-013: Tìm kiếm dùng unaccent + pg_trgm, KHÔNG dùng tsvector.
--   Q1: Không có bảng users. FK trỏ auth.users(id) ON DELETE CASCADE.
--   Q2: exchange_requests dùng sender_completed_at / receiver_completed_at.
--       Không có completed_at denormalized. COMPLETED do application logic.
--   Q3: full_name VARCHAR(255).
--   Q4: File nằm tại api/supabase_schema.sql.
--
-- File này là bản ghi schema đích trên Supabase (DEC-007), cập nhật mỗi khi thay đổi schema.
-- ============================================================

-- ============================================================
-- 0. EXTENSIONS
-- ============================================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- uuid_generate_v4()
CREATE EXTENSION IF NOT EXISTS "pgcrypto";   -- crypt functions
CREATE EXTENSION IF NOT EXISTS "pg_trgm";    -- trigram fuzzy search (DEC-013)
CREATE EXTENSION IF NOT EXISTS "unaccent" WITH SCHEMA extensions;   -- accent-insensitive search (DEC-013)

-- Wrapper function IMMUTABLE bọc extensions.unaccent (chỉ định rõ dictionary 'extensions.unaccent'::regdictionary
-- để tránh phụ thuộc vào search_path, đảm bảo an toàn tuyệt đối khi đánh dấu IMMUTABLE cho GIN expression index)
CREATE OR REPLACE FUNCTION public.f_unaccent(text)
RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$
    SELECT extensions.unaccent('extensions.unaccent'::regdictionary, $1)
$$;

-- ============================================================
-- 1. SKILL CATEGORIES
-- ============================================================
CREATE TABLE skill_categories (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL UNIQUE,
    icon        VARCHAR(50)   NOT NULL DEFAULT '',
    description VARCHAR(300),                          -- Exposed có cột này
    color_hex   VARCHAR(7)    DEFAULT '#6366F1',
    created_at  TIMESTAMPTZ   DEFAULT NOW()
);

CREATE INDEX idx_skill_categories_name_trgm
    ON skill_categories USING GIN (name gin_trgm_ops);

-- ============================================================
-- 2. SKILLS
-- ============================================================
-- Không dùng tsvector (vi phạm DEC-013). Dùng pg_trgm GIN index với public.f_unaccent.
CREATE TABLE skills (
    id          SERIAL PRIMARY KEY,
    category_id INT          NOT NULL REFERENCES skill_categories(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  DEFAULT NOW(),
    UNIQUE(category_id, name)
);

CREATE INDEX idx_skills_category  ON skills(category_id);
CREATE INDEX idx_skills_name_unaccent_trgm ON skills USING GIN (public.f_unaccent(name) gin_trgm_ops);

-- ============================================================
-- 3. PROFILES
-- Không có bảng users. user_id tham chiếu auth.users(id) trực tiếp.
-- ============================================================
CREATE TABLE profiles (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID         NOT NULL UNIQUE REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name    VARCHAR(255) NOT NULL CONSTRAINT profiles_full_name_not_blank CHECK (char_length(trim(full_name)) > 0),
    bio          TEXT         CONSTRAINT profiles_bio_length_check CHECK (char_length(bio) <= 500),
    city         VARCHAR(100),
    avatar_url   VARCHAR(500),
    is_public    BOOLEAN      DEFAULT TRUE,            -- Cột legacy do Exposed SchemaUtils tạo trên DB, hiện chưa dùng
    -- Availability: [{\"day\":\"MON\",\"from\":\"18:00\",\"to\":\"21:00\"}]
    -- day ∈ {MON,TUE,WED,THU,FRI,SAT,SUN}; from < to; HH:mm UTC+7
    availability JSONB        DEFAULT '[]'::JSONB,
    updated_at   TIMESTAMPTZ  DEFAULT NOW()
);

CREATE INDEX idx_profiles_user_id ON profiles(user_id);
CREATE INDEX idx_profiles_city    ON profiles(city);
CREATE INDEX idx_profiles_city_unaccent_trgm
    ON profiles USING GIN (public.f_unaccent(city) gin_trgm_ops);
CREATE INDEX idx_profiles_full_name_trgm
    ON profiles USING GIN (public.f_unaccent(full_name) gin_trgm_ops);

-- Trigger tự động cập nhật updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER set_profiles_updated_at
    BEFORE UPDATE ON profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 4. USER SKILLS
-- Kiểu VARCHAR(10) để tương thích với Exposed. ENUM tránh dùng vì
-- Exposed khai báo varchar("type", 10) — ENUM gây lỗi SchemaUtils.
-- ============================================================
CREATE TABLE user_skills (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID    NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    skill_id          INT     NOT NULL CONSTRAINT fk_user_skills_skill_id__id REFERENCES skills(id) ON DELETE RESTRICT,
    type              VARCHAR(10) NOT NULL CONSTRAINT user_skills_type_check CHECK (type IN ('HAVE', 'WANT')),
    proficiency_level INT     DEFAULT 1 CONSTRAINT user_skills_proficiency_range CHECK (proficiency_level BETWEEN 1 AND 5),
    note              TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, skill_id, type)   -- Mỗi user chỉ HAVE/WANT 1 lần mỗi skill
);

CREATE INDEX idx_user_skills_user       ON user_skills(user_id);
CREATE INDEX idx_user_skills_skill      ON user_skills(skill_id, type);
CREATE INDEX idx_user_skills_type_skill ON user_skills(type, skill_id, proficiency_level); -- TASK-020 (matching filter)
CREATE INDEX idx_user_skills_user_type  ON user_skills(user_id, type);                      -- TASK-020 (matching profile)
CREATE INDEX IF NOT EXISTS idx_user_skills_graph_cte ON user_skills(type, skill_id, proficiency_level, user_id); -- TASK-042 (graph cycle CTE)

-- ============================================================
-- 5. EXCHANGE REQUESTS [IMPLEMENTED — TASK-021]
-- Q2: sender_completed_at + receiver_completed_at thay cho completed_at đơn.
--     COMPLETED do application logic, không dùng trigger.
-- ============================================================
DO $$ BEGIN
    CREATE TYPE exchange_status AS ENUM (
        'PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'CANCELLED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE meeting_mode AS ENUM (
        'ONLINE', 'IN_PERSON', 'UNDECIDED'
    );
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

CREATE TABLE IF NOT EXISTS exchange_requests (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    receiver_id           UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    skill_offered_id      INT  NOT NULL REFERENCES skills(id),
    skill_wanted_id       INT  NOT NULL REFERENCES skills(id),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING' CONSTRAINT exchange_requests_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'CANCELLED')),
    duration_minutes      INT NOT NULL DEFAULT 60 CONSTRAINT exchange_requests_duration_check CHECK (duration_minutes IN (30, 60, 90, 120)),
    meeting_mode          VARCHAR(20) NOT NULL DEFAULT 'UNDECIDED' CONSTRAINT exchange_requests_meeting_mode_check CHECK (meeting_mode IN ('ONLINE', 'IN_PERSON', 'UNDECIDED')),
    message               TEXT,
    cancellation_reason   TEXT,
    scheduled_at          TIMESTAMPTZ NOT NULL,
    accepted_at           TIMESTAMPTZ,     -- thời điểm chấp nhận yêu cầu (TASK-033)
    sender_completed_at   TIMESTAMPTZ,     -- sender xác nhận hoàn thành
    receiver_completed_at TIMESTAMPTZ,     -- receiver xác nhận hoàn thành
    resolved_at           TIMESTAMPTZ,     -- thời điểm kết thúc REJECTED, CANCELLED, COMPLETED (DEC-028)
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT exchange_requests_diff_users CHECK (sender_id <> receiver_id)
);

ALTER TABLE exchange_requests ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_exchange_sender   ON exchange_requests(sender_id, status);
CREATE INDEX IF NOT EXISTS idx_exchange_receiver ON exchange_requests(receiver_id, status);
CREATE INDEX IF NOT EXISTS idx_exchange_scheduled ON exchange_requests(scheduled_at);

-- DEC-028: Index tối ưu hóa truy vấn NOT EXISTS / anti-join loại trừ đối tác trên Feed Gợi ý
CREATE INDEX IF NOT EXISTS idx_exchange_requests_exclusion_lookup
    ON exchange_requests (sender_id, receiver_id, status, COALESCE(resolved_at, updated_at));

-- DEC-028: Chặn trùng lặp cặp kỹ năng đối xứng hai chiều đang mở (PENDING / ACCEPTED)
CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_requests_active_pair ON exchange_requests (
    LEAST(sender_id, receiver_id),
    GREATEST(sender_id, receiver_id),
    (CASE WHEN sender_id < receiver_id THEN skill_offered_id ELSE skill_wanted_id END),
    (CASE WHEN sender_id < receiver_id THEN skill_wanted_id ELSE skill_offered_id END)
) WHERE status IN ('PENDING', 'ACCEPTED');

CREATE OR REPLACE TRIGGER set_exchange_updated_at
    BEFORE UPDATE ON exchange_requests
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- DEC-028: Tự động gắn resolved_at khi chuyển sang trạng thái kết thúc (REJECTED, CANCELLED, COMPLETED)
CREATE OR REPLACE FUNCTION set_exchange_resolved_at()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status IN ('REJECTED', 'CANCELLED', 'COMPLETED') AND (OLD.status IS DISTINCT FROM NEW.status) THEN
        NEW.resolved_at = NOW();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_set_exchange_resolved_at ON exchange_requests;
CREATE TRIGGER trigger_set_exchange_resolved_at
    BEFORE UPDATE ON exchange_requests
    FOR EACH ROW
    EXECUTE FUNCTION set_exchange_resolved_at();

-- ============================================================
-- 5.1 RESCHEDULE PROPOSALS [IMPLEMENTED — DEC-025, TASK-067]
-- ============================================================
CREATE TABLE IF NOT EXISTS reschedule_proposals (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exchange_id       UUID NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,
    proposer_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    new_scheduled_at  TIMESTAMPTZ NOT NULL,
    reason            VARCHAR(200),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' CONSTRAINT reschedule_proposals_status_check CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED')),
    -- proposal_number (1..3) tính trên TẤT CẢ các bản ghi đề xuất từng tạo của exchange đó (COUNT(*) + 1),
    -- bao gồm cả các bản ghi CANCELLED, REJECTED, EXPIRED để chống spam hủy rồi đề xuất lại.
    proposal_number   INT NOT NULL CONSTRAINT reschedule_proposals_number_check CHECK (proposal_number BETWEEN 1 AND 3),
    responded_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Chỉ cho phép tối đa 1 proposal PENDING cho mỗi exchange
CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_pending_reschedule
    ON reschedule_proposals (exchange_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_reschedule_proposals_exchange
    ON reschedule_proposals(exchange_id, created_at DESC);

CREATE OR REPLACE TRIGGER set_reschedule_proposals_updated_at
    BEFORE UPDATE ON reschedule_proposals
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 6. RATINGS [IMPLEMENTED — TASK-033]
-- Không bật RLS (API là cổng duy nhất).
-- ============================================================
CREATE TABLE IF NOT EXISTS ratings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exchange_id  UUID NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,
    reviewer_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reviewee_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    score        INT  NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment      VARCHAR(200) CHECK (char_length(comment) <= 200),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ratings_exchange_reviewer UNIQUE(exchange_id, reviewer_id),
    CONSTRAINT chk_ratings_different_users CHECK (reviewer_id <> reviewee_id)
);

CREATE INDEX IF NOT EXISTS idx_ratings_exchange ON ratings(exchange_id);
CREATE INDEX IF NOT EXISTS idx_ratings_reviewee ON ratings(reviewee_id);
CREATE INDEX IF NOT EXISTS idx_ratings_reviewer ON ratings(reviewer_id);

-- ============================================================
-- 7. NOTIFICATIONS [IMPLEMENTED — TASK-032]
-- ============================================================
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'notification_type') THEN
        CREATE TYPE notification_type AS ENUM (
            'NEW_MESSAGE',
            'NEW_REQUEST',
            'REQUEST_ACCEPTED',
            'REQUEST_REJECTED',
            'APPOINTMENT_REMINDER',
            'RATING_REMINDER',
            'RESCHEDULE_PROPOSED',
            'RESCHEDULE_ACCEPTED',
            'RESCHEDULE_REJECTED'
        );
    END IF;
END$$;

-- LƯU Ý POSTGRESQL: ALTER TYPE ... ADD VALUE không thể chạy trong transaction block.
-- Chạy riêng rẽ từng câu lệnh dưới đây trong Supabase SQL Editor:
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'RESCHEDULE_PROPOSED';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'RESCHEDULE_ACCEPTED';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'RESCHEDULE_REJECTED';
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'MISSED_CALL';

CREATE TABLE IF NOT EXISTS notifications (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type       notification_type NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    entity_id  VARCHAR(100),
    data       JSONB DEFAULT '{}'::JSONB,
    is_read    BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user   ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;

-- ============================================================
-- 8.1 CHỐNG TRÙNG LẶP NHẮC NHỞ (DEC-018, CẬP NHẬT THEO DEC-025 - PHƯƠNG ÁN 1)
-- Thay thế idx_notifications_unique_reminders cũ bằng 2 partial unique indexes:
-- 1. idx_notifications_unique_appointment: cho phép gửi nhắc nhở cho mốc giờ mới khi dời lịch
--    Biểu thức (data->>'scheduledAt') là IMMUTABLE trong PostgreSQL (hàm jsonb_extract_path_text).
--    Quy tắc định dạng: data->>'scheduledAt' bắt buộc là chuỗi ISO-8601 UTC cố định (uuuu-MM-dd'T'HH:mm:ss'Z').
-- 2. idx_notifications_unique_rating: giữ nguyên quy tắc mỗi user/exchange chỉ nhắc 1 lần.
--
-- HƯỚNG DẪN THỨ TỰ CHẠY MIGRATION AN TOÀN CHO CHỦ DỰ ÁN:
--
-- [Bước 1: Chạy câu kiểm tra dữ liệu trùng trước (Pre-check)]
-- SELECT user_id, type, entity_id, (data->>'scheduledAt') AS scheduled_at, COUNT(*)
-- FROM notifications
-- WHERE type = 'APPOINTMENT_REMINDER'
-- GROUP BY user_id, type, entity_id, (data->>'scheduledAt')
-- HAVING COUNT(*) > 1;
--
-- SELECT user_id, type, entity_id, COUNT(*)
-- FROM notifications
-- WHERE type = 'RATING_REMINDER'
-- GROUP BY user_id, type, entity_id
-- HAVING COUNT(*) > 1;
--
-- [Bước 2: Tạo 2 index mới TRƯỚC để bảo đảm không bị khoảng trống thiếu ràng buộc]
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_unique_appointment
-- ON notifications (user_id, type, entity_id, (data->>'scheduledAt'))
-- WHERE type = 'APPOINTMENT_REMINDER';
--
-- CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_unique_rating
-- ON notifications (user_id, type, entity_id)
-- WHERE type = 'RATING_REMINDER';
--
-- [Bước 3: DROP index cũ sau khi các index mới đã sẵn sàng]
-- DROP INDEX IF EXISTS idx_notifications_unique_reminders;
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_unique_appointment
ON notifications (user_id, type, entity_id, (data->>'scheduledAt'))
WHERE type = 'APPOINTMENT_REMINDER';

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_unique_rating
ON notifications (user_id, type, entity_id)
WHERE type = 'RATING_REMINDER';

-- ============================================================
-- 9. FCM TOKENS [IMPLEMENTED — TASK-023]
-- ============================================================
CREATE TABLE fcm_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL UNIQUE,
    device_info VARCHAR(200),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_fcm_tokens_user ON fcm_tokens(user_id);

CREATE TRIGGER set_fcm_tokens_updated_at
    BEFORE UPDATE ON fcm_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 10. ROW LEVEL SECURITY (defense in depth — API dùng service role)
-- ============================================================
ALTER TABLE profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_skills       ENABLE ROW LEVEL SECURITY;
ALTER TABLE exchange_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE friendships       ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications     ENABLE ROW LEVEL SECURITY;
ALTER TABLE fcm_tokens        ENABLE ROW LEVEL SECURITY;
ALTER TABLE reschedule_proposals ENABLE ROW LEVEL SECURITY;
ALTER TABLE call_sessions     ENABLE ROW LEVEL SECURITY;

-- Profiles: public read, own write
CREATE POLICY "profiles_select" ON profiles FOR SELECT USING (TRUE);
CREATE POLICY "profiles_insert" ON profiles FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "profiles_update" ON profiles FOR UPDATE USING (auth.uid() = user_id);

-- User skills: public read, own write
CREATE POLICY "user_skills_select" ON user_skills FOR SELECT USING (TRUE);
CREATE POLICY "user_skills_write"  ON user_skills FOR ALL   USING (auth.uid() = user_id);

-- Exchange requests: parties only
CREATE POLICY "exchanges_select" ON exchange_requests
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);
CREATE POLICY "exchanges_insert" ON exchange_requests
    FOR INSERT WITH CHECK (auth.uid() = sender_id);
CREATE POLICY "exchanges_update" ON exchange_requests
    FOR UPDATE USING (auth.uid() = sender_id OR auth.uid() = receiver_id);

-- Ratings: Không dùng RLS (API là cổng duy nhất, kiểm soát qua code — TASK-033)

-- Notifications: own only
CREATE POLICY "notifications_all" ON notifications FOR ALL USING (auth.uid() = user_id);

-- FCM tokens: own only
CREATE POLICY "fcm_tokens_all" ON fcm_tokens FOR ALL USING (auth.uid() = user_id);

-- Reschedule proposals & Call sessions: Bật RLS nhưng KHÔNG tạo policy cho anon/authenticated (defense in depth).
-- Toàn bộ truy vấn trực tiếp từ Supabase client SDK (PostgREST) bị chặn hoàn toàn.
-- API Ktor kết nối qua JDBC connection pool bằng user postgres (superuser/service role có BYPASSRLS) nên vẫn truy cập bình thường.

-- ============================================================
-- 5.2 CALL SESSIONS [PLANNED — DEC-026, TASK-073]
-- ============================================================
CREATE TABLE IF NOT EXISTS call_sessions (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exchange_id       UUID NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,
    caller_id         UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    receiver_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    room_name         VARCHAR(100) NOT NULL UNIQUE,
    call_type         VARCHAR(10) NOT NULL CONSTRAINT call_sessions_type_check CHECK (call_type IN ('VOICE', 'VIDEO')),
    status            VARCHAR(20) NOT NULL DEFAULT 'DIALING' CONSTRAINT call_sessions_status_check CHECK (status IN ('DIALING', 'RINGING', 'CONNECTED', 'ENDED', 'REJECTED', 'BUSY', 'MISSED', 'FAILED')),
    started_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    connected_at      TIMESTAMPTZ,
    ended_at          TIMESTAMPTZ,
    duration_seconds  INT NOT NULL DEFAULT 0 CONSTRAINT call_sessions_duration_check CHECK (duration_seconds >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_call_sessions_different_users CHECK (caller_id <> receiver_id)
);

-- Partial Unique Indexes bảo vệ Concurrency Guard (mỗi người tối đa 1 cuộc gọi active)
CREATE UNIQUE INDEX IF NOT EXISTS idx_call_sessions_active_caller
    ON call_sessions (caller_id)
    WHERE status IN ('DIALING', 'RINGING', 'CONNECTED');

CREATE UNIQUE INDEX IF NOT EXISTS idx_call_sessions_active_receiver
    ON call_sessions (receiver_id)
    WHERE status IN ('DIALING', 'RINGING', 'CONNECTED');

-- Các index hỗ trợ dọn dẹp timeout và tra cứu
CREATE INDEX IF NOT EXISTS idx_call_sessions_exchange ON call_sessions (exchange_id);
CREATE INDEX IF NOT EXISTS idx_call_sessions_timeout ON call_sessions (status, created_at) WHERE status IN ('DIALING', 'RINGING', 'CONNECTED');

CREATE OR REPLACE TRIGGER set_call_sessions_updated_at
    BEFORE UPDATE ON call_sessions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 14. BÁO CÁO VI PHẠM & CHẶN NGƯỜI DÙNG 2 CHIỀU (DEC-031)
-- ============================================================

CREATE TABLE IF NOT EXISTS public.user_reports (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reporter_id       UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reported_user_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    exchange_id       UUID NULL REFERENCES public.exchange_requests(id) ON DELETE SET NULL,
    reason            VARCHAR(50) NOT NULL,
    description       VARCHAR(500) NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at       TIMESTAMPTZ NULL,
    CONSTRAINT chk_user_reports_different_users CHECK (reporter_id <> reported_user_id),
    CONSTRAINT chk_user_reports_reason CHECK (
        reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE_CONTENT', 'NO_SHOW', 'FAKE_PROFILE', 'OTHER')
    ),
    CONSTRAINT chk_user_reports_status CHECK (
        status IN ('PENDING', 'REVIEWED', 'DISMISSED')
    ),
    CONSTRAINT chk_user_reports_desc_len CHECK (
        description IS NULL OR char_length(description) <= 500
    )
);

-- Chống spam báo cáo trùng lặp khi báo cáo trước đó còn đang PENDING
CREATE UNIQUE INDEX IF NOT EXISTS uq_user_reports_open
    ON public.user_reports (reporter_id, reported_user_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_user_reports_reported
    ON public.user_reports (reported_user_id, status, created_at DESC);

ALTER TABLE public.user_reports ENABLE ROW LEVEL SECURITY;
-- Không tạo public policy cho anon/authenticated: chỉ Ktor Backend truy cập qua postgres role.

CREATE TABLE IF NOT EXISTS public.user_blocks (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    blocker_id        UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    blocked_user_id   UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_user_blocks_different_users CHECK (blocker_id <> blocked_user_id),
    CONSTRAINT uq_user_blocks_pair UNIQUE (blocker_id, blocked_user_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker
    ON public.user_blocks (blocker_id, blocked_user_id);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked
    ON public.user_blocks (blocked_user_id, blocker_id);

ALTER TABLE public.user_blocks ENABLE ROW LEVEL SECURITY;
-- Không tạo public policy cho anon/authenticated: chỉ Ktor Backend truy cập qua postgres role.

-- ============================================================
-- 15. THẺ ĐÁNH GIÁ NHANH TRONG RATINGS (DEC-032)
-- ============================================================

ALTER TABLE public.ratings
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_ratings_tags_array'
    ) THEN
        ALTER TABLE public.ratings
            ADD CONSTRAINT chk_ratings_tags_array
            CHECK (jsonb_typeof(tags) = 'array' AND jsonb_array_length(tags) <= 5);
    END IF;
END $$;

-- ============================================================
-- 16. TRỌNG SỐ GỢI Ý CÁ NHÂN HÓA TRONG PROFILES (DEC-034)
-- ============================================================

ALTER TABLE public.profiles
    ADD COLUMN IF NOT EXISTS matching_weights JSONB NULL,
    ADD COLUMN IF NOT EXISTS matching_feedback_count INT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_profiles_feedback_count_nonneg'
    ) THEN
        ALTER TABLE public.profiles
            ADD CONSTRAINT chk_profiles_feedback_count_nonneg
            CHECK (matching_feedback_count >= 0);
    END IF;
END $$;

-- ============================================================
-- 17. CHUỖI KỸ NĂNG / LỘ TRÌNH HỌC TẬP DAG (DEC-035)
-- ============================================================

CREATE TABLE IF NOT EXISTS public.skill_prerequisites (
    id                      SERIAL PRIMARY KEY,
    skill_id                INT NOT NULL REFERENCES public.skills(id) ON DELETE CASCADE,
    prerequisite_skill_id   INT NOT NULL REFERENCES public.skills(id) ON DELETE CASCADE,
    step_order              INT NOT NULL DEFAULT 1,
    rationale               VARCHAR(255) NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_skill_prereq_no_self_loop CHECK (skill_id <> prerequisite_skill_id),
    CONSTRAINT chk_skill_prereq_step_order CHECK (step_order >= 1),
    CONSTRAINT uq_skill_prerequisite_pair UNIQUE (skill_id, prerequisite_skill_id)
);

CREATE INDEX IF NOT EXISTS idx_skill_prerequisites_target
    ON public.skill_prerequisites (skill_id, step_order);

CREATE INDEX IF NOT EXISTS idx_skill_prerequisites_prereq
    ON public.skill_prerequisites (prerequisite_skill_id);

ALTER TABLE public.skill_prerequisites ENABLE ROW LEVEL SECURITY;
-- Không tạo write policy cho anon/authenticated; dữ liệu đọc qua Ktor Backend API.

-- Seed dữ liệu lộ trình học tập chuẩn (DAG không chu trình) theo tên kỹ năng
INSERT INTO public.skill_prerequisites (skill_id, prerequisite_skill_id, step_order, rationale)
SELECT s_target.id, s_prereq.id, v.step_order, v.rationale
FROM (
    VALUES
        ('Kotlin', 'Java', 1, 'Nắm vững OOP và JVM cơ bản từ Java giúp tiếp cận Kotlin và Android nhanh hơn.'),
        ('Flutter', 'Kotlin', 2, 'Hiểu kiến trúc ứng dụng di động gốc giúp phát triển đa nền tảng với Flutter hiệu quả.'),
        ('React', 'JavaScript', 1, 'JavaScript ES6+ là nền tảng cốt lõi trước khi học thư viện giao diện React.'),
        ('Python', 'SQL', 1, 'Tư duy truy vấn dữ liệu SQL bổ trợ trực tiếp cho phân tích dữ liệu và backend với Python.'),
        ('UI/UX', 'Figma', 1, 'Sử dụng thành thạo công cụ Figma giúp hiện thực hóa quy trình thiết kế UI/UX.'),
        ('After Effects', 'Illustrator', 1, 'Thiết kế vector trên Illustrator là bước chuẩn bị quan trọng cho chuyển động đồ họa After Effects.'),
        ('Chỉnh sửa ảnh', 'Photoshop', 1, 'Kỹ thuật xử lý lớp và màu sắc trong Photoshop là nền tảng cho chỉnh sửa ảnh chuyên sâu.'),
        ('Ukulele', 'Guitar', 1, 'Cảm âm và bấm hợp âm Guitar giúp chuyển sang chơi Ukulele nhanh chóng.'),
        ('Quay phim', 'Chụp chân dung', 1, 'Nắm vững bố cục và ánh sáng trong nhiếp ảnh tĩnh trước khi quay video chuyển động.'),
        ('Đàm phán', 'Thuyết trình', 1, 'Kỹ năng trình bày rõ ràng và tự tin là tiền đề cho đàm phán thuyết phục.'),
        ('Lãnh đạo', 'Đàm phán', 2, 'Khả năng lắng nghe và đàm phán giúp điều phối nhóm và lãnh đạo hiệu quả.'),
        ('Lãnh đạo', 'Quản lý thời gian', 1, 'Làm chủ thời gian cá nhân trước khi quản lý tiến độ của tập thể.')
) AS v(target_name, prereq_name, step_order, rationale)
JOIN public.skills s_target ON s_target.name = v.target_name
JOIN public.skills s_prereq ON s_prereq.name = v.prereq_name
ON CONFLICT (skill_id, prerequisite_skill_id) DO NOTHING;

-- ============================================================
-- DONE — Cập nhật khi thay đổi schema (DEC-007)
-- ============================================================
