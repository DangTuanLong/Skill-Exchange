package com.skillexchange.api.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import java.net.URI
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.principal
import java.net.URL
import java.security.interfaces.RSAPublicKey
import com.auth0.jwk.JwkProviderBuilder
import java.util.concurrent.TimeUnit

fun Application.configureAuth() {
    val supabaseUrl  = System.getenv("SUPABASE_URL")
        ?: "https://nleafbmggblqggttnfoa.supabase.co"
    val jwksUrl      = System.getenv("SUPABASE_JWKS_URL")
        ?: "$supabaseUrl/auth/v1/.well-known/jwks.json"
    val jwtIssuer    = System.getenv("JWT_ISSUER")
        ?: "$supabaseUrl/auth/v1"
    val jwtAudience  = System.getenv("JWT_AUDIENCE") ?: "authenticated"

    // JWK Provider — cache 10 keys, refresh mỗi 24h
    val jwkProvider = JwkProviderBuilder(URI(jwksUrl).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "SkillExchange API"
            verifier(jwkProvider, jwtIssuer) {
                acceptLeeway(3)          // 3 giây tolerance cho clock skew
                withAudience(jwtAudience)
            }
            validate { credential ->
                val userId = credential.payload.subject  // Supabase JWT dùng `sub` cho userId
                if (!userId.isNullOrEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Lấy Supabase User ID từ JWT `sub` claim.
 * Dùng trong route handlers: val userId = call.getUserId()
 */
fun ApplicationCall.getUserId(): String {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.subject
        ?: throw IllegalStateException("User ID not found in JWT")
}

/**
 * Lấy Supabase User ID từ JWT nếu có xác thực, hoặc null nếu không có token.
 * Dùng cho các route công khai nhưng cá nhân hóa (như tìm kiếm loại trừ bản thân).
 */
fun ApplicationCall.getUserIdOrNull(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.subject
}

/**
 * Lấy email từ JWT claims.
 */
fun ApplicationCall.getUserEmail(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("email")?.asString()
}
