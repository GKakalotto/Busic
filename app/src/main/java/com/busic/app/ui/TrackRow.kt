package com.busic.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.busic.app.data.Track
import com.busic.app.data.formatMmSs
import com.busic.app.ui.theme.BgHighlight
import com.busic.app.ui.theme.Brand
import com.busic.app.ui.theme.TextSecondary
import com.busic.app.ui.theme.TextTertiary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgHighlight)
                } else {
                    Modifier
                },
            )
            .quietClick(onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = track.title,
                color = if (active) Brand else Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
                overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (active) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (track.author.isNotBlank()) {
                    Text(
                        text = "UP",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brand)
                            .padding(horizontal = 4.dp, vertical = 0.dp),
                    )
                }
                Text(
                    text = track.author,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = if (track.author.isNotBlank()) 4.dp else 0.dp)
                        .weight(1f)
                        .then(if (active) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier),
                )
            }
        }
        Text(
            text = formatMmSs(track.durationSec),
            color = TextTertiary,
            fontSize = 12.sp,
        )
    }
}
