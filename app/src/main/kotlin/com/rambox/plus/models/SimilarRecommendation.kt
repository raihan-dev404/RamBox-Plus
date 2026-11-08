// SimilarRecommendation.kt

package com.rambox.plus.models

import com.music.innertube.models.YTItem
import com.rambox.plus.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
