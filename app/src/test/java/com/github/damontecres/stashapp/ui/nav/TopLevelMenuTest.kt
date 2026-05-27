package com.github.damontecres.stashapp.ui.nav

import com.github.damontecres.stashapp.data.DataType
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelMenuTest {
    @Test
    fun topLevelMenuDataTypesExcludeSecondaryMetadataPages() {
        assertEquals(
            listOf(
                DataType.SCENE,
                DataType.MARKER,
                DataType.TAG,
                DataType.IMAGE,
                DataType.GALLERY,
            ),
            DataType.entries.filter(::isTopLevelMenuDataType),
        )
    }
}
