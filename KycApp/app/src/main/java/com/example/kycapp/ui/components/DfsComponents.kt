package com.example.kycapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kycapp.ui.theme.DfsColors
import kotlin.math.max

private val CardShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun DfsBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DfsColors.Background)
            .drawBehind {
                // Soft ambient washes — top-left blue, mid teal, bottom glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(DfsColors.Primary.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 0.06f),
                        radius = max(size.width, size.height) * 0.7f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(DfsColors.TealAccent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 0.55f),
                        radius = max(size.width, size.height) * 0.55f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(DfsColors.PrimaryStrong.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 1.05f),
                        radius = max(size.width, size.height) * 0.6f
                    )
                )
            }
    ) {
        content()
    }
}

/**
 * Mobile-first screen shell.
 * @param scrollable when true (default), content scrolls so buttons stay reachable.
 *   Use false for screens that own their own LazyColumn (e.g. records list).
 */
@Composable
fun DfsScreen(
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    DfsBackground {
        val base = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
        Column(
            modifier = if (scrollable) {
                base
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            } else {
                base.padding(horizontal = 22.dp, vertical = 16.dp)
            },
            content = content
        )
    }
}

@Composable
fun DfsAnimatedSection(
    visible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    // AnimatedVisibility stacks bare children in one box — always wrap in Column
    // so brand/title/subtitle never overlap on device.
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 12 },
        exit = fadeOut(tween(180))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content
        )
    }
}

/** Circular blue→teal mark + DFS CORPORATE wordmark. */
@Composable
fun DfsBrandMark(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val markSize = if (compact) 40.dp else 48.dp
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(markSize)
                .shadow(12.dp, CircleShape, ambientColor = DfsColors.GlowBlue, spotColor = DfsColors.Primary)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(DfsColors.Primary, DfsColors.TealAccent)
                    )
                )
                .border(1.dp, DfsColors.BorderStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "DFS",
                color = DfsColors.OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 10.sp else 12.sp,
                letterSpacing = 0.6.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "DFS CORPORATE",
                color = DfsColors.OnBackground,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                letterSpacing = 0.4.sp,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Partner identity platform",
                color = DfsColors.MutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun DfsStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    dotColor: Color = DfsColors.TealAccent
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(DfsColors.SurfaceTranslucent)
            .border(1.dp, DfsColors.BorderStrong, PillShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = DfsColors.OnBackground,
            letterSpacing = 0.8.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DfsCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = CardShape
    Column(
        modifier = modifier
            .then(
                if (glow) {
                    Modifier.shadow(
                        10.dp,
                        shape,
                        ambientColor = DfsColors.GlowBlue,
                        spotColor = DfsColors.Primary
                    )
                } else Modifier
            )
            .clip(shape)
            .background(DfsColors.SurfaceTranslucent)
            .border(1.dp, DfsColors.Border, shape)
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun DfsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(100),
        label = "dfsBtnScale"
    )

    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(DfsColors.Primary, DfsColors.PrimaryStrong))
    } else {
        Brush.horizontalGradient(
            listOf(DfsColors.MutedText.copy(alpha = 0.35f), DfsColors.MutedText.copy(alpha = 0.25f))
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .scale(scale)
            .shadow(
                elevation = if (enabled) 6.dp else 0.dp,
                shape = PillShape,
                ambientColor = DfsColors.GlowBlue,
                spotColor = DfsColors.Primary
            )
            .clip(PillShape)
            .background(brush)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = DfsColors.OnBackground,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun DfsSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(PillShape)
            .border(1.dp, DfsColors.BorderStrong, PillShape)
            .background(DfsColors.SurfaceElevated.copy(alpha = 0.65f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) DfsColors.OnBackground else DfsColors.MutedText,
            maxLines = 1
        )
    }
}

@Composable
fun DfsTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = DfsColors.Primary
) {
    Text(
        text = text,
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = color
    )
}

@Composable
fun DfsProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = DfsColors.Primary,
        trackColor = DfsColors.TealAccent.copy(alpha = 0.25f),
        strokeWidth = 3.dp
    )
}

@Composable
fun DfsScreenHeader(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    onBack: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (onBack != null) {
            DfsTextButton(text = "← Back", onClick = onBack)
            Spacer(modifier = Modifier.height(4.dp))
        }
        if (badge != null) {
            DfsStatusBadge(label = badge)
            Spacer(modifier = Modifier.height(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = DfsColors.OnBackground
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = DfsColors.MutedText,
                textAlign = TextAlign.Start
            )
        }
    }
}

@Composable
fun DfsMenuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(100),
        label = "menuPress"
    )

    DfsCard(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        glow = false,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = DfsColors.OnBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "›",
                color = DfsColors.Primary,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 24.sp
            )
        }
    }
}
