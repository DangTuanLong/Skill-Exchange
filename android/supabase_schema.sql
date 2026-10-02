-- ============================================================
-- SkillExchange — Supabase Database Schema
-- Chạy file này trong: Supabase Dashboard > SQL Editor
-- ============================================================

-- Bật extension cần thiết
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";      -- Full-text search tiếng Việt

-- ============================================================
-- 1. SKILL CATEGORIES
-- ============================================================
CREATE TABLE skill_categories (
    id          SERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    icon        VARCHAR(50),                    -- Tên icon Material (e.g. "code", "music_note")
    color_hex   VARCHAR(7) DEFAULT '#6366F1',   -- Brand color per category
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

-- Seed dữ liệu danh mục kỹ năng
INSERT INTO skill_categories (name, icon, color_hex) VALUES
    ('Lập trình & Công nghệ',   'code',           '#6366F1'),
    ('Ngoại ngữ',               'translate',      '#10B981'),
    ('Thiết kế & Sáng tạo',     'palette',        '#F59E0B'),
    ('Âm nhạc & Nghệ thuật',    'music_note',     '#EC4899'),
    ('Kinh doanh & Marketing',  'trending_up',    '#3B82F6'),
    ('Sức khỏe & Thể thao',     'fitness_center', '#22C55E'),
    ('Nấu ăn & Ẩm thực',        'restaurant',     '#F97316'),
    ('Học thuật & Nghiên cứu',  'school',         '#8B5CF6'),
    ('Kỹ năng mềm',             'psychology',     '#06B6D4'),
    ('Thủ công & DIY',          'build',          '#84CC16');

-- ============================================================
-- 2. SKILLS
-- ============================================================
CREATE TABLE skills (
    id          SERIAL PRIMARY KEY,
    category_id INT NOT NULL REFERENCES skill_categories(id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    name_search TSVECTOR GENERATED ALWAYS AS (to_tsvector('simple', name)) STORED,
    description TEXT,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(category_id, name)
);

CREATE INDEX idx_skills_category   ON skills(category_id);
CREATE INDEX idx_skills_name_fts   ON skills USING GIN(name_search);
CREATE INDEX idx_skills_name_trgm  ON skills USING GIN(name gin_trgm_ops); -- Fuzzy search

-- Seed kỹ năng mẫu
INSERT INTO skills (category_id, name, description) VALUES
    -- Lập trình & Công nghệ
    (1, 'Kotlin Android',       'Phát triển ứng dụng Android với Kotlin & Jetpack Compose'),
    (1, 'Python',               'Lập trình Python cho data science, automation, backend'),
    (1, 'React / Next.js',      'Frontend web development'),
    (1, 'Machine Learning',     'ML cơ bản đến nâng cao với scikit-learn, TensorFlow'),
    (1, 'SQL & Database',       'Thiết kế database, viết query tối ưu'),
    (1, 'UI/UX Design',         'Thiết kế giao diện với Figma'),
    -- Ngoại ngữ
    (2, 'Tiếng Anh giao tiếp',  'Giao tiếp hàng ngày, phỏng vấn, thuyết trình'),
    (2, 'Tiếng Nhật',           'N5 đến N2, giao tiếp và đọc hiểu'),
    (2, 'Tiếng Hàn',            'TOPIK cơ bản đến trung cấp'),
    (2, 'Tiếng Trung',          'HSK 1-6, giao tiếp thực tế'),
    -- Thiết kế
    (3, 'Figma',                'Thiết kế UI/UX, prototype, design system'),
    (3, 'Photoshop',            'Chỉnh sửa ảnh, thiết kế đồ họa'),
    (3, 'Illustrator',          'Vector graphics, logo design'),
    -- Âm nhạc
    (4, 'Guitar',               'Guitar acoustic và electric, từ cơ bản đến nâng cao'),
    (4, 'Piano / Keyboard',     'Piano cổ điển và hiện đại'),
    (4, 'Hát / Vocal',          'Kỹ thuật hát, điều tiết giọng'),
    -- Kinh doanh
    (5, 'Digital Marketing',    'SEO, Google Ads, Facebook Ads, Content Marketing'),
    (5, 'Excel & Data Analysis','Phân tích dữ liệu với Excel, Power BI'),
    (5, 'Public Speaking',      'Kỹ năng thuyết trình, diễn thuyết'),
    -- Kỹ năng mềm
    (9, 'Quản lý thời gian',    'Time management, productivity systems'),
    (9, 'Tư duy phản biện',     'Critical thinking, problem solving'),
    (9, 'Lãnh đạo nhóm',        'Team leadership, conflict resolution');

-- ============================================================
-- 3. USERS (Extends Supabase Auth)
-- ============================================================
CREATE TABLE users (
    id               UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email            VARCHAR(255) NOT NULL UNIQUE,
    reputation_score DECIMAL(3,2) DEFAULT 0.00 CHECK (reputation_score >= 0 AND reputation_score <= 5),
    total_exchanges  INT DEFAULT 0,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    last_login       TIMESTAMPTZ DEFAULT NOW()
);

-- ============================================================
-- 4. PROFILES
-- ============================================================
CREATE TABLE profiles (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id      UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    full_name    VARCHAR(100) NOT NULL,
    avatar_url   TEXT,
    bio          TEXT,
    city         VARCHAR(100),
    -- Availability: mảng các slot thời gian rảnh
    -- Format: [{"day": "MON", "start": "18:00", "end": "21:00"}, ...]
    availability JSONB DEFAULT '[]'::JSONB,
    is_public    BOOLEAN DEFAULT TRUE,
    updated_at   TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_profiles_user_id ON profiles(user_id);
CREATE INDEX idx_profiles_city    ON profiles(city);

-- ============================================================
-- 5. USER SKILLS
-- ============================================================
CREATE TYPE skill_type AS ENUM ('HAVE', 'WANT');

CREATE TABLE user_skills (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id          INT NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    type              skill_type NOT NULL,
    proficiency_level INT CHECK (proficiency_level BETWEEN 1 AND 5),  -- 1=Mới, 5=Chuyên gia
    note              TEXT,
    created_at        TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, skill_id, type)   -- Mỗi user chỉ đăng ký 1 lần HAVE/WANT cho mỗi skill
);

CREATE INDEX idx_user_skills_user    ON user_skills(user_id);
CREATE INDEX idx_user_skills_skill   ON user_skills(skill_id);
CREATE INDEX idx_user_skills_type    ON user_skills(type);

-- ============================================================
-- 6. EXCHANGE REQUESTS
-- ============================================================
CREATE TYPE exchange_status AS ENUM (
    'PENDING',
    'ACCEPTED',
    'REJECTED',
    'COMPLETED',
    'CANCELLED'
);

CREATE TABLE exchange_requests (
    id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    sender_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    receiver_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_offered_id INT REFERENCES skills(id),    -- Kỹ năng sender đề nghị dạy
    skill_wanted_id  INT REFERENCES skills(id),    -- Kỹ năng sender muốn học
    status           exchange_status DEFAULT 'PENDING',
    message          TEXT,
    scheduled_at     TIMESTAMPTZ,                  -- Thời gian hẹn gặp
    completed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ DEFAULT NOW(),
    updated_at       TIMESTAMPTZ DEFAULT NOW(),
    CHECK (sender_id != receiver_id)               -- Không thể tự gửi cho mình
);

CREATE INDEX idx_exchange_sender   ON exchange_requests(sender_id);
CREATE INDEX idx_exchange_receiver ON exchange_requests(receiver_id);
CREATE INDEX idx_exchange_status   ON exchange_requests(status);

-- ============================================================
-- 7. RATINGS
-- ============================================================
CREATE TABLE ratings (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    exchange_id  UUID NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,
    reviewer_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reviewee_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    score        INT NOT NULL CHECK (score BETWEEN 1 AND 5),
    comment      TEXT,
    created_at   TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(exchange_id, reviewer_id)               -- Mỗi người chỉ rate 1 lần/phiên
);

CREATE INDEX idx_ratings_exchange ON ratings(exchange_id);
CREATE INDEX idx_ratings_reviewee ON ratings(reviewee_id);

-- ============================================================
-- 8. FRIENDSHIPS
-- ============================================================
CREATE TYPE friendship_status AS ENUM ('PENDING', 'ACCEPTED', 'BLOCKED');

CREATE TABLE friendships (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    friend_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status     friendship_status DEFAULT 'PENDING',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    UNIQUE(user_id, friend_id),
    CHECK (user_id != friend_id)
);

CREATE INDEX idx_friendships_user   ON friendships(user_id);
CREATE INDEX idx_friendships_friend ON friendships(friend_id);

-- ============================================================
-- 9. NOTIFICATIONS
-- ============================================================
CREATE TYPE notification_type AS ENUM (
    'EXCHANGE_REQUEST',
    'EXCHANGE_ACCEPTED',
    'EXCHANGE_REJECTED',
    'EXCHANGE_COMPLETED',
    'NEW_MESSAGE',
    'NEW_RATING',
    'FRIEND_REQUEST',
    'FRIEND_ACCEPTED'
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       notification_type NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       TEXT,
    data       JSONB DEFAULT '{}'::JSONB,   -- Extra payload (exchange_id, user_id, etc.)
    is_read    BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_notifications_user    ON notifications(user_id);
CREATE INDEX idx_notifications_unread  ON notifications(user_id, is_read) WHERE is_read = FALSE;

-- ============================================================
-- 10. FCM TOKENS (Push Notification)
-- ============================================================
CREATE TABLE fcm_tokens (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       TEXT NOT NULL UNIQUE,
    device_info VARCHAR(200),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_fcm_tokens_user ON fcm_tokens(user_id);

-- ============================================================
-- 11. TRIGGERS — Tự động tạo user record khi đăng ký Auth
-- ============================================================
CREATE OR REPLACE FUNCTION handle_new_auth_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.users (id, email)
    VALUES (NEW.id, NEW.email)
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION handle_new_auth_user();

-- Trigger tự động cập nhật updated_at cho profiles
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

CREATE TRIGGER set_exchange_updated_at
    BEFORE UPDATE ON exchange_requests
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ============================================================
-- 12. ROW LEVEL SECURITY (RLS)
-- ============================================================
ALTER TABLE users             ENABLE ROW LEVEL SECURITY;
ALTER TABLE profiles          ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_skills       ENABLE ROW LEVEL SECURITY;
ALTER TABLE exchange_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE ratings           ENABLE ROW LEVEL SECURITY;
ALTER TABLE friendships       ENABLE ROW LEVEL SECURITY;
ALTER TABLE notifications     ENABLE ROW LEVEL SECURITY;
ALTER TABLE fcm_tokens        ENABLE ROW LEVEL SECURITY;

-- Users: Chỉ đọc được thông tin cơ bản của người khác
CREATE POLICY "Users can view all users" ON users FOR SELECT USING (TRUE);
CREATE POLICY "Users can update own record" ON users FOR UPDATE USING (auth.uid() = id);

-- Profiles: Public profile có thể xem, chỉ tự sửa của mình
CREATE POLICY "Public profiles are viewable" ON profiles FOR SELECT USING (is_public = TRUE OR auth.uid() = user_id);
CREATE POLICY "Users can insert own profile" ON profiles FOR INSERT WITH CHECK (auth.uid() = user_id);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = user_id);

-- User Skills: Ai cũng xem được, chỉ tự sửa của mình
CREATE POLICY "User skills are viewable" ON user_skills FOR SELECT USING (TRUE);
CREATE POLICY "Users can manage own skills" ON user_skills FOR ALL USING (auth.uid() = user_id);

-- Exchange Requests: Chỉ sender và receiver mới xem được
CREATE POLICY "Parties can view their exchanges" ON exchange_requests
    FOR SELECT USING (auth.uid() = sender_id OR auth.uid() = receiver_id);
CREATE POLICY "Users can create exchanges" ON exchange_requests
    FOR INSERT WITH CHECK (auth.uid() = sender_id);
CREATE POLICY "Receiver can update status" ON exchange_requests
    FOR UPDATE USING (auth.uid() = receiver_id OR auth.uid() = sender_id);

-- Ratings
CREATE POLICY "Ratings are public" ON ratings FOR SELECT USING (TRUE);
CREATE POLICY "Users can submit ratings" ON ratings FOR INSERT WITH CHECK (auth.uid() = reviewer_id);

-- Notifications: Chỉ xem của mình
CREATE POLICY "Own notifications only" ON notifications FOR ALL USING (auth.uid() = user_id);

-- FCM Tokens: Chỉ quản lý của mình
CREATE POLICY "Own FCM tokens only" ON fcm_tokens FOR ALL USING (auth.uid() = user_id);

-- ============================================================
-- DONE — Schema hoàn chỉnh cho Phase 1
-- ============================================================
