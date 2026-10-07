package com.skillexchange.api.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DemoDataGeneratorTest {

    @Test
    fun testGeneratesExact40UsersWithDeterministicSeed() {
        val users1 = DemoDataGenerator.generateUsers(seed = 1337L)
        val users2 = DemoDataGenerator.generateUsers(seed = 1337L)

        assertEquals(40, users1.size, "Phải sinh ra chính xác 40 users (5 scenario + 35 random)")
        assertEquals(users1.size, users2.size)

        // Tính tái lập (Reproducibility / Determinism)
        for (i in users1.indices) {
            val u1 = users1[i]
            val u2 = users2[i]
            assertEquals(u1.email, u2.email)
            assertEquals(u1.fullName, u2.fullName)
            assertEquals(u1.city, u2.city)
            assertEquals(u1.bio, u2.bio)
            assertEquals(u1.availability, u2.availability)
            assertEquals(u1.skills, u2.skills)
        }
    }

    @Test
    fun testScenarioUsersHaveExpectedEmailsAndRoles() {
        val users = DemoDataGenerator.generateUsers()
        val scenarioUsers = users.filter { it.isScenarioUser }

        assertEquals(5, scenarioUsers.size, "Phải có đúng 5 user kịch bản cố định")

        val expectedEmails = setOf(
            "sender@seed.skillexchange.test",
            "receiver@seed.skillexchange.test",
            "uiux@seed.skillexchange.test",
            "lang@seed.skillexchange.test",
            "music@seed.skillexchange.test"
        )
        val actualEmails = scenarioUsers.map { it.email }.toSet()
        assertEquals(expectedEmails, actualEmails, "Emails của 5 kịch bản phải khớp danh sách yêu cầu")
    }

    @Test
    fun testSenderAndReceiverAvailabilityOverlap() {
        val users = DemoDataGenerator.generateUsers()
        val sender = users.first { it.email == "sender@seed.skillexchange.test" }
        val receiver = users.first { it.email == "receiver@seed.skillexchange.test" }

        // Tìm các khung giờ trùng ngày và giao nhau về thời gian
        var hasOverlap = false
        for (sWindow in sender.availability) {
            for (rWindow in receiver.availability) {
                if (sWindow.day == rWindow.day) {
                    val overlapStart = maxOf(sWindow.from, rWindow.from)
                    val overlapEnd = minOf(sWindow.to, rWindow.to)
                    if (overlapStart < overlapEnd) {
                        hasOverlap = true
                    }
                }
            }
        }

        assertTrue(hasOverlap, "Bắt buộc sender và receiver phải có availability chồng nhau để test đặt lịch")
    }

    @Test
    fun testAllUsersComplyWithDatabaseConstraints() {
        val users = DemoDataGenerator.generateUsers()

        val allEmails = users.map { it.email }
        assertEquals(users.size, allEmails.distinct().size, "Tất cả emails phải là duy nhất")

        for (user in users) {
            assertTrue(user.email.endsWith("@seed.skillexchange.test"), "Email phải thuộc domain demo")
            assertTrue(user.fullName.isNotBlank(), "Tên không được để trống")
            assertTrue(user.bio.length <= 500, "Bio phải <= 500 ký tự theo ràng buộc CHECK của DB")
            assertTrue(user.city in DemoDataGenerator.CITIES, "City phải thuộc danh sách thành phố")
            assertTrue(user.skills.isNotEmpty(), "User phải có ít nhất 1 kỹ năng")

            // Kiểm tra ràng buộc kỹ năng
            val skillTypePairs = user.skills.map { Pair(it.name, it.type) }
            assertEquals(
                skillTypePairs.size,
                skillTypePairs.distinct().size,
                "Không được có kỹ năng trùng lặp cùng type (HAVE hoặc WANT) cho user ${user.email}"
            )

            for (skill in user.skills) {
                assertTrue(
                    skill.level in 1..5,
                    "Proficiency level phải nằm trong khoảng 1..5 (CHECK user_skills_proficiency_range)"
                )
                assertTrue(
                    skill.type in listOf("HAVE", "WANT"),
                    "Type phải là HAVE hoặc WANT (CHECK user_skills_type_check)"
                )
                assertTrue(
                    skill.name in DemoDataGenerator.AVAILABLE_SKILLS,
                    "Kỹ năng '${skill.name}' phải nằm trong từ điển kỹ năng chuẩn"
                )
            }

            // Kiểm tra ràng buộc availability
            for (window in user.availability) {
                assertTrue(
                    window.day in listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
                    "Ngày phải thuộc MON..SUN"
                )
                assertTrue(
                    window.from < window.to,
                    "Giờ bắt đầu phải trước giờ kết thúc (from < to)"
                )
                assertTrue(window.from.matches(Regex("^\\d{2}:\\d{2}$")), "Format giờ bắt đầu phải là HH:mm")
                assertTrue(window.to.matches(Regex("^\\d{2}:\\d{2}$")), "Format giờ kết thúc phải là HH:mm")
            }
        }
    }
}
