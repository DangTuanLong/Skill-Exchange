package com.skillexchange.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skillexchange.app.core.ui.theme.*
import com.skillexchange.app.domain.model.PotentialMatch
import com.skillexchange.app.domain.model.PotentialMatchSkill
import com.skillexchange.app.domain.model.SkillType
import com.skillexchange.app.domain.model.UserDiscovery

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UserCardItem(
    user: UserDiscovery,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRelationshipChip: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // User Top Info Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!user.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = user.avatarUrl,
                            contentDescription = user.fullName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = user.fullName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand500
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = user.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    // Chips row: Match percentage, Available pairs, Relationship chip
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (user.matchPercentage != null && user.matchPercentage > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFEF3C7),
                                contentColor = Color(0xFFD97706)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${user.matchPercentage}%",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (user.availablePairsCount != null && user.availablePairsCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = "Còn ${user.availablePairsCount} cặp trao đổi",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .semantics { contentDescription = "Còn ${user.availablePairsCount} cặp trao đổi" }
                                )
                            }
                        }

                        if (showRelationshipChip) {
                            if (user.relationshipStatus == "ACTIVE") {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Text(
                                        text = "Đang trao đổi",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .semantics { contentDescription = "Trạng thái: Đang trao đổi" }
                                    )
                                }
                            } else if (user.relationshipStatus == "COMPLETED" || user.completedExchangeCount > 0) {
                                val count = if (user.completedExchangeCount > 0) user.completedExchangeCount else 1
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ) {
                                    Text(
                                        text = "Đã trao đổi $count lần",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .semantics { contentDescription = "Trạng thái: Đã trao đổi $count lần" }
                                    )
                                }
                            }
                        }
                    }

                    if (!user.city.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = user.city,
                                fontSize = 12.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }
            }

            if (!user.bio.isNullOrBlank()) {
                Text(
                    text = user.bio,
                    fontSize = 13.sp,
                    color = TextSecondaryLight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Skills breakdown chips
            val haveSkills = remember(user.skills) { user.skills.filter { it.type == SkillType.HAVE } }
            val wantSkills = remember(user.skills) { user.skills.filter { it.type == SkillType.WANT } }

            if (haveSkills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Có thể chia sẻ:", fontSize = 11.sp, color = Brand600, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        haveSkills.take(4).forEach { s ->
                            SkillBadgeChip(s.skillName, s.proficiencyLevel, isHave = true)
                        }
                    }
                }
            }

            if (wantSkills.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Muốn học hỏi:", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        wantSkills.take(4).forEach { s ->
                            SkillBadgeChip(s.skillName, s.proficiencyLevel, isHave = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PotentialMatchCard(
    match: PotentialMatch,
    onAddWantSkill: (PotentialMatchSkill) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Avatar, Name, City, Potential Score Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEFF6FF)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!match.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = match.avatarUrl,
                            contentDescription = match.fullName,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = match.fullName.take(1).uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Brand500
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = match.fullName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryLight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Text(
                                text = "${match.potentialScore}% tiềm năng",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!match.city.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = Brand500,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = match.city,
                                fontSize = 12.sp,
                                color = TextSecondaryLight
                            )
                        }
                    }
                }
            }

            // Breakdown box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Bạn có thể dạy:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand600
                        )
                        Text(
                            text = "${match.canTeachSkill.skillName} (Cấp độ ${match.canTeachSkill.proficiencyLevel}/5)",
                            fontSize = 12.sp,
                            color = TextPrimaryLight
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Đối tác có thể dạy:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF16A34A)
                        )
                        Text(
                            text = "${match.suggestedWantSkill.skillName} (Cấp độ ${match.suggestedWantSkill.proficiencyLevel}/5)",
                            fontSize = 12.sp,
                            color = TextPrimaryLight
                        )
                    }
                }
            }

            // Action button: "Thêm [kỹ năng] vào mong muốn"
            Button(
                onClick = { onAddWantSkill(match.suggestedWantSkill) },
                colors = ButtonDefaults.buttonColors(containerColor = Brand500),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thêm ${match.suggestedWantSkill.skillName} vào mong muốn",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SkillBadgeChip(name: String, level: Int, isHave: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHave) Color(0xFFEFF6FF) else Color(0xFFF0FDF4))
            .border(0.5.dp, if (isHave) Brand500 else Color(0xFF16A34A), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isHave) Brand600 else Color(0xFF15803D)
            )
            if (isHave) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Lvl $level",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Brand500
                )
            }
        }
    }
}

fun formatCategoryLabel(icon: String, name: String): String {
    return name
}
