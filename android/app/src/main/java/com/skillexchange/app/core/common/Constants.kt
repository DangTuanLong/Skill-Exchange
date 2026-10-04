package com.skillexchange.app.core.common

import com.skillexchange.app.BuildConfig

/**
 * Constants toàn cục của app.
 */
object Constants {
    // ─── API Server (Ktor) ──────────────────────────────────────────
    val BASE_URL: String = BuildConfig.BASE_URL

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
