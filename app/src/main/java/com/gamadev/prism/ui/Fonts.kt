package com.gamadev.prism.ui

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.gamadev.prism.R

/** Inter (SIL OFL, docs/Inter-OFL.txt) as a free substitute for SF Pro. Variable font, axis wght. */
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.inter_variable,
            weight = FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    }
)
