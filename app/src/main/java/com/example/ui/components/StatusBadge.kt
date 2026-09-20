package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyncStatus
import com.example.ui.theme.StatusFailedRed
import com.example.ui.theme.StatusPendingAmber
import com.example.ui.theme.StatusSyncedGreen

@Composable
fun SyncStatusBadge(
    status: SyncStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, text, icon) = when (status) {
        SyncStatus.SYNCED -> Quad(
            Color(0xFFE8F5E9),
            StatusSyncedGreen,
            "Cloud Synced",
            Icons.Default.CloudDone
        )
        SyncStatus.PENDING_SYNC -> Quad(
            Color(0xFFFFF7ED),
            StatusPendingAmber,
            "Pending Sync",
            Icons.Default.CloudUpload
        )
        SyncStatus.SYNCING -> Quad(
            Color(0xFFE0F2FE),
            Color(0xFF0284C7),
            "Syncing...",
            Icons.Default.Sync
        )
        SyncStatus.FAILED -> Quad(
            Color(0xFFFEF2F2),
            StatusFailedRed,
            "Sync Failed",
            Icons.Default.ErrorOutline
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == SyncStatus.SYNCING) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = textColor
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = textColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun NetworkStatusPill(
    isOnline: Boolean,
    isSimulatingOffline: Boolean,
    modifier: Modifier = Modifier
) {
    val (bgColor, dotColor, label) = when {
        isSimulatingOffline -> Triple(Color(0xFFFEF3C7), Color(0xFFD97706), "Field Offline (Sim)")
        isOnline -> Triple(Color(0xFFDCFCE7), Color(0xFF16A34A), "Cloud Connected")
        else -> Triple(Color(0xFFFEE2E2), Color(0xFFDC2626), "No Connectivity")
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = dotColor
        )
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
