package com.busic.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.busic.app.BusicApp
import com.busic.app.data.BiliUser
import com.busic.app.data.FavFolder
import com.busic.app.ui.AppIcons
import com.busic.app.ui.ConfirmDialog
import com.busic.app.ui.FlatIconButton
import com.busic.app.ui.quietClick
import com.busic.app.ui.theme.BgBase
import com.busic.app.ui.theme.BgElevated
import com.busic.app.ui.theme.BgHighlight
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary

@Composable
fun MeScreen(
    ready: Boolean,
    user: BiliUser?,
    folders: List<FavFolder>,
    loading: Boolean,
    error: String?,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onFolder: (FavFolder) -> Unit,
) {
    var confirmLogout by remember { mutableStateOf(false) }
    val recent by BusicApp.get().local.recent.collectAsState()
    val cards = buildList {
        add(BusicApp.get().local.recentFolder(recent))
        if (user != null) addAll(folders)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "我的",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            FlatIconButton(onClick = onSearch) {
                Icon(AppIcons.Search, contentDescription = "搜索", tint = Color.White)
            }
            FlatIconButton(onClick = onSettings) {
                Icon(AppIcons.Settings, contentDescription = "设置", tint = Color.White)
            }
        }
        when {
            !ready || (loading && user == null) -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Brand)
            }
            else -> Column(Modifier.weight(1f).fillMaxWidth()) {
                if (user == null) {
                    AccountRow(onClick = onLogin) {
                        FacePlaceholder()
                        Text(
                            text = "点击登录",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                } else {
                    UserRow(user) { confirmLogout = true }
                }
                if (error != null && folders.isEmpty()) {
                    Text(
                        text = error,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(cards, key = { it.id }) { folder ->
                        FolderCard(folder, onClick = { onFolder(folder) })
                    }
                }
            }
        }
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "退出登录",
            body = "确定退出当前账号？",
            confirm = "退出",
            onConfirm = {
                confirmLogout = false
                onLogout()
            },
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun AccountRow(
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .then(if (onClick != null) Modifier.quietClick(onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

@Composable
private fun FacePlaceholder() {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(BgHighlight),
    )
}

@Composable
private fun UserRow(user: BiliUser, onLogout: () -> Unit) {
    AccountRow {
        if (user.face.isBlank()) {
            FacePlaceholder()
        } else {
            AsyncImage(
                model = user.face,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = user.name,
            color = Color.White,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = "退出",
            color = TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BgHighlight)
                .quietClick(onLogout)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun FolderCard(folder: FavFolder, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgElevated)
            .quietClick(onClick),
    ) {
        if (folder.cover.isBlank()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(BgHighlight),
            )
        } else {
            AsyncImage(
                model = folder.cover,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = folder.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = folder.count.toString(),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
