package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandNavyDark
import com.example.ui.theme.BrandRed
import com.example.ui.theme.DisplayFontFamily

/**
 * Faithful vector rendering of the Muebles América brand mark:
 * "Muebles" + glossy blue globe with bright yellow Americas inside a red ring + "América" MR.
 */
@Composable
fun MueblesAmericaLogo(
    modifier: Modifier = Modifier,
    globeSize: Dp = 44.dp,
    fontSize: TextUnit = 26.sp,
    showTrademark: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        DoubleOutlinedBrandWord(text = "Muebles", fontSize = fontSize)
        Spacer(modifier = Modifier.width(4.dp))
        MueblesAmericaGlobe(modifier = Modifier.size(globeSize))
        Spacer(modifier = Modifier.width(3.dp))
        DoubleOutlinedBrandWord(text = "América", fontSize = fontSize)
        if (showTrademark) {
            Text(
                text = "MR",
                color = BrandNavy,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (fontSize.value * 0.34f).sp,
                modifier = Modifier
                    .align(Alignment.Bottom)
                    .padding(start = 2.dp, bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun DoubleOutlinedBrandWord(
    text: String,
    fontSize: TextUnit
) {
    val baseStyle = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontStyle = FontStyle.Italic,
        fontSize = fontSize,
        letterSpacing = (-0.6).sp
    )
    Box(contentAlignment = Alignment.Center) {
        // Outer bold red outline
        Text(
            text = text,
            style = baseStyle.copy(
                color = BrandRed,
                drawStyle = Stroke(width = fontSize.value * 0.42f, join = StrokeJoin.Round)
            )
        )
        // Middle navy outline
        Text(
            text = text,
            style = baseStyle.copy(
                color = BrandNavyDark,
                drawStyle = Stroke(width = fontSize.value * 0.22f, join = StrokeJoin.Round)
            )
        )
        // Crisp white foreground fill
        Text(
            text = text,
            style = baseStyle.copy(color = Color.White)
        )
    }
}

@Composable
fun MueblesAmericaGlobe(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = size.minDimension / 2f
        val ringStroke = outerRadius * 0.16f
        val globeRadius = outerRadius - ringStroke * 0.55f

        // Glossy blue ocean sphere
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFF5D92E8),
                    Color(0xFF1442A6),
                    Color(0xFF061B5E)
                ),
                center = Offset(center.x + globeRadius * 0.28f, center.y - globeRadius * 0.25f),
                radius = globeRadius * 1.45f
            ),
            radius = globeRadius,
            center = center
        )

        // Bright yellow Americas continent silhouette
        val americasColor = Color(0xFFFFF021)
        val w = globeRadius * 2f
        val left = center.x - globeRadius
        val top = center.y - globeRadius

        val northAmerica = Path().apply {
            moveTo(left + w * 0.34f, top + w * 0.14f)
            lineTo(left + w * 0.58f, top + w * 0.12f)
            lineTo(left + w * 0.65f, top + w * 0.24f)
            lineTo(left + w * 0.52f, top + w * 0.34f)
            lineTo(left + w * 0.44f, top + w * 0.42f)
            lineTo(left + w * 0.49f, top + w * 0.51f)
            lineTo(left + w * 0.41f, top + w * 0.52f)
            lineTo(left + w * 0.32f, top + w * 0.40f)
            lineTo(left + w * 0.26f, top + w * 0.26f)
            close()
        }
        drawPath(path = northAmerica, color = americasColor)

        val southAmerica = Path().apply {
            moveTo(left + w * 0.48f, top + w * 0.53f)
            lineTo(left + w * 0.64f, top + w * 0.58f)
            lineTo(left + w * 0.72f, top + w * 0.70f)
            lineTo(left + w * 0.61f, top + w * 0.88f)
            lineTo(left + w * 0.50f, top + w * 0.93f)
            lineTo(left + w * 0.45f, top + w * 0.76f)
            lineTo(left + w * 0.42f, top + w * 0.62f)
            close()
        }
        drawPath(path = southAmerica, color = americasColor)

        // Outer bold red ring
        drawCircle(
            color = BrandRed,
            radius = outerRadius - ringStroke / 2f,
            center = center,
            style = Stroke(width = ringStroke)
        )
    }
}

@Composable
fun SplashLaunchScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5FC), Color(0xFFE4ECF9))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            MueblesAmericaLogo(
                globeSize = 54.dp,
                fontSize = 30.sp,
                showTrademark = true
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.brand_subtitle),
                style = MaterialTheme.typography.titleSmall,
                color = BrandNavy
            )
        }
    }
}
