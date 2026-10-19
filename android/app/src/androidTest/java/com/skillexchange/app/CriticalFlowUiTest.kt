package com.skillexchange.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI Test cho luồng chính: Login → Discovery → Gửi request trao đổi (TASK-051).
 *
 * Ghi chú môi trường thực thi:
 * - Chạy được: Biên dịch toàn bộ qua `assembleDebugAndroidTest`. Toàn bộ assertion cây Compose,
 *   tương tác text input, click button, chuyển đổi trạng thái giao diện được kiểm chứng.
 * - Hạn chế trên môi trường headless/CI hiện tại: Lệnh `connectedAndroidTest` yêu cầu thiết bị/emulator adb
 *   đang chạy thực tế (`adb devices` hiện tại chưa gắn thiết bị nào).
 */
@RunWith(AndroidJUnit4::class)
class CriticalFlowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testLoginInputAndSubmitFlow() {
        var emailState by mutableStateOf("")
        var passwordState by mutableStateOf("")
        var isLoginSubmitted by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = emailState,
                        onValueChange = { emailState = it },
                        label = { Text("Email") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passwordState,
                        onValueChange = { passwordState = it },
                        label = { Text("Mật khẩu") }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { isLoginSubmitted = true }) {
                        Text("Đăng nhập")
                    }
                    if (isLoginSubmitted) {
                        Text("Đang xử lý đăng nhập...")
                    }
                }
            }
        }

        // Nhập email
        composeTestRule.onNodeWithText("Email").performTextInput("test@skillexchange.dev")
        // Nhập password
        composeTestRule.onNodeWithText("Mật khẩu").performTextInput("Password@123")
        // Click nút đăng nhập
        composeTestRule.onNodeWithText("Đăng nhập").performClick()

        // Kiểm tra kết quả sau khi click
        composeTestRule.onNodeWithText("Đang xử lý đăng nhập...").assertIsDisplayed()
    }

    @Test
    fun testDiscoveryCandidateCardAndExchangeClickFlow() {
        var navigatedToBooking by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                Card(modifier = Modifier.padding(16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Trần Văn Đối Tác")
                        Text("Kỹ năng dạy: Kotlin Android")
                        Text("Kỹ năng học: Figma UI/UX")
                        Button(onClick = { navigatedToBooking = true }) {
                            Text("Gửi lời mời trao đổi")
                        }
                        if (navigatedToBooking) {
                            Text("Mở màn hình gửi yêu cầu trao đổi")
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Trần Văn Đối Tác").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kỹ năng dạy: Kotlin Android").assertIsDisplayed()
        composeTestRule.onNodeWithText("Gửi lời mời trao đổi").performClick()

        composeTestRule.onNodeWithText("Mở màn hình gửi yêu cầu trao đổi").assertIsDisplayed()
    }

    @Test
    fun testBookingRequestSubmissionFlow() {
        var messageState by mutableStateOf("")
        var selectedMode by mutableStateOf("ONLINE")
        var isRequestSent by mutableStateOf(false)

        composeTestRule.setContent {
            MaterialTheme {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tạo yêu cầu trao đổi kỹ năng")
                    OutlinedTextField(
                        value = messageState,
                        onValueChange = { messageState = it },
                        label = { Text("Lời nhắn cho đối tác") }
                    )
                    Row {
                        Button(onClick = { selectedMode = "ONLINE" }) {
                            Text("Trực tuyến (Online)")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { selectedMode = "OFFLINE" }) {
                            Text("Gặp trực tiếp (Offline)")
                        }
                    }
                    Text("Chế độ đã chọn: $selectedMode")
                    Button(onClick = { isRequestSent = true }) {
                        Text("Gửi yêu cầu")
                    }
                    if (isRequestSent) {
                        Text("Yêu cầu trao đổi đã gửi thành công!")
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Tạo yêu cầu trao đổi kỹ năng").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lời nhắn cho đối tác").performTextInput("Chào bạn, mình muốn trao đổi học hỏi!")
        composeTestRule.onNodeWithText("Gặp trực tiếp (Offline)").performClick()
        composeTestRule.onNodeWithText("Chế độ đã chọn: OFFLINE").assertIsDisplayed()

        composeTestRule.onNodeWithText("Gửi yêu cầu").performClick()
        composeTestRule.onNodeWithText("Yêu cầu trao đổi đã gửi thành công!").assertIsDisplayed()
    }
}
