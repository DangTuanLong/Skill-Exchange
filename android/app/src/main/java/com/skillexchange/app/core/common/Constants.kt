package com.skillexchange.app.core.common

/**
 * Constants toàn cục của app.
 */
object Constants {
    // ─── API Server (Ktor) ──────────────────────────────────────────
    // Local dev: chạy Ktor trên máy tính, emulator dùng 10.0.2.2 để trỏ về localhost
    const val BASE_URL_LOCAL = "http://10.0.2.2:8080"
    const val BASE_URL_PROD  = "https://skillexchange-api.onrender.com" // TODO: thay URL Render thật

    // Đổi sang BASE_URL_PROD khi deploy
    const val BASE_URL = BASE_URL_LOCAL

    // ─── Supabase ───────────────────────────────────────────────────
    const val SUPABASE_URL      = "https://nleafbmggblqggttnfoa.supabase.co"
    // Publishable key (anon key) — an toàn để để trong code client
    const val SUPABASE_ANON_KEY = "sb_publishable_jkwnAPPq27Xb_zRLd3pMEQ_pZdCzXkT"

    // ─── Timeouts (milliseconds) ────────────────────────────────────
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val REQUEST_TIMEOUT_MS = 30_000L
    const val SOCKET_TIMEOUT_MS  = 30_000L

    // ─── Auth Tokens ────────────────────────────────────────────────
    const val PREF_ACCESS_TOKEN          = "access_token"
    const val PREF_REFRESH_TOKEN         = "refresh_token"
    const val ACCESS_TOKEN_EXPIRY_MS     = 15 * 60 * 1000L           // 15 phút
    const val REFRESH_TOKEN_EXPIRY_MS    = 7 * 24 * 60 * 60 * 1000L  // 7 ngày

    // ─── Pagination ─────────────────────────────────────────────────
    const val DEFAULT_PAGE_SIZE = 20
}
