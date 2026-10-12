package com.skillexchange.app.presentation.friend

import com.skillexchange.app.domain.model.friend.Friend

enum class FriendTab {
    FRIENDS,
    INVITATIONS
}

data class FriendListState(
    val selectedTab: FriendTab = FriendTab.FRIENDS,
    val friends: List<Friend> = emptyList(),
    val invitations: List<Friend> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class FriendListIntent {
    data class SelectTab(val tab: FriendTab) : FriendListIntent()
    object LoadData : FriendListIntent()
    data class AcceptInvitation(val friendshipId: String) : FriendListIntent()
    data class RejectInvitation(val friendshipId: String) : FriendListIntent()
    data class RemoveFriend(val friendshipId: String) : FriendListIntent()
    object Retry : FriendListIntent()
}

sealed class FriendListEffect {
    data class ShowSnackbar(val message: String) : FriendListEffect()
    data class NavigateToProfile(val userId: String) : FriendListEffect()
}
