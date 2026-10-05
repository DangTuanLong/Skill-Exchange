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

-- ============================================================
-- 5. EXCHANGE REQUESTS [PLANNED — TASK-021]
-- Q2: sender_completed_at + receiver_completed_at thay cho completed_at đơn.
--     COMPLETED do application logic, không dùng trigger.
-- ============================================================
CREATE TYPE exchange_status AS ENUM (
    'PENDING', 'ACCEPTED', 'REJECTED', 'COMPLETED', 'CANCELLED'
);

CREATE TABLE exchange_requests (
    id                    UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id             UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    receiver_id           UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    skill_offered_id      INT  REFERENCES skills(id),
    skill_wanted_id       INT  REFERENCES skills(id),
    status                exchange_status DEFAULT 'PENDING',
    message               TEXT,
    scheduled_at          TIMESTAMPTZ,
    sender_completed_at   TIMESTAMPTZ,     -- sender xác nhận hoàn thành
    receiver_completed_at TIMESTAMPTZ,     -- receiver xác nhận hoàn thành
    created_at            TIMESTAMPTZ DEFAULT NOW(),
    updated_at            TIMESTAMPTZ DEFAULT NOW(),
    CHECK (sender_id <> receiver_id)
);

CREATE INDEX idx_exchange_sender   ON exchange_requests(sender_id, status);
CREATE INDEX idx_exchange_receiver ON exchange_requests(receiver_id, status);

CREATE TRIGGER set_exchange_updated_at
    BEFORE UPDATE ON exchange_requests
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 6. RATINGS [PLANNED — TASK-033]
-- ============================================================
CREATE TABLE ratings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exchange_id  UUID NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,
    reviewer_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reviewee_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    score        INT  NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment      TEXT CHECK (char_length(comment) <= 200),
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(exchange_id, reviewer_id),
    CHECK (reviewer_id <> reviewee_id)
);

CREATE INDEX idx_ratings_exchange ON ratings(exchange_id);
CREATE INDEX idx_ratings_reviewee ON ratings(reviewee_id);

-- ============================================================
-- 7. FRIENDSHIPS [PLANNED — TASK-034]
-- UNIQUE(user_id, friend_id): cặp có hướng. Application tự enforce không trùng chiều ngược.
-- ============================================================
CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'BLOCKED');

CREATE TABLE friendships (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    friend_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status     friendship_status DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, friend_id),
    CHECK (user_id <> friend_id)
);

CREATE INDEX idx_friendships_user   ON friendships(user_id);
CREATE INDEX idx_friendships_friend ON friendships(friend_id);

-- ============================================================
-- 8. NOTIFICATIONS [PLANNED — TASK-032]
-- ============================================================
CREATE TYPE notification_type AS ENUM (
    'EXCHANGE_REQUEST', 'EXCHANGE_ACCEPTED', 'EXCHANGE_REJECTED',
    'EXCHANGE_COMPLETED', 'NEW_MESSAGE', 'NEW_RATING',
    'FRIEND_REQUEST', 'FRIEND_ACCEPTED'
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type       notification_type NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    data       JSONB DEFAULT '{}'::JSONB,
    is_read    BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user   ON notifications(user_id);
CREATE INDEX idx_notifications_unread ON notifications(user_id, is_read) WHERE is_read = FALSE;

-- ============================================================
-- 9. FCM TOKENS [PLANNED — TASK-023]
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
ALTER TABLE ratings           ENABLE ROW LEVEL SECURITY;
ALTER TABLE friendships       ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications     ENABLE ROW LEVEL SECURITY;
ALTER TABLE fcm_tokens        ENABLE ROW LEVEL SECURITY;

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

-- Ratings: public read, own write
CREATE POLICY "ratings_select" ON ratings FOR SELECT USING (TRUE);
CREATE POLICY "ratings_insert" ON ratings FOR INSERT WITH CHECK (auth.uid() = reviewer_id);

-- Notifications: own only
CREATE POLICY "notifications_all" ON notifications FOR ALL USING (auth.uid() = user_id);

-- FCM tokens: own only
CREATE POLICY "fcm_tokens_all" ON fcm_tokens FOR ALL USING (auth.uid() = user_id);

-- ============================================================
-- DONE — Cập nhật khi thay đổi schema (DEC-007)
-- ============================================================
