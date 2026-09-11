package com.example.islandlyrics.core.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityFeedItemTest {
    @Test
    fun primaryActionProjectionPrefersPrimaryStyle() {
        val item = CommunityFeedItem(
            id = "item",
            title = "Title",
            summary = "",
            body = "Body",
            actions = listOf(
                action("secondary", "https://example.com/secondary", CommunityFeedActionStyle.SECONDARY),
                action("primary", "https://example.com/primary", CommunityFeedActionStyle.PRIMARY)
            )
        )

        assertEquals("https://example.com/primary", item.url)
        assertEquals("primary", item.actionText)
        assertTrue(item.hasUrl)
    }

    @Test
    fun emptyActionsHaveNoLegacyUrlProjection() {
        val item = CommunityFeedItem("item", "Title", "", "Body", emptyList())

        assertEquals("", item.url)
        assertEquals("", item.actionText)
        assertTrue(!item.hasUrl)
    }

    @Test
    fun actionsKeepFeedOrderForMultiButtonRendering() {
        val item = CommunityFeedItem(
            id = "item",
            title = "Title",
            summary = "",
            body = "Body",
            actions = listOf(
                action("first", "https://example.com/first", CommunityFeedActionStyle.SECONDARY),
                action("second", "https://example.com/second", CommunityFeedActionStyle.SECONDARY),
                action("third", "https://example.com/third", CommunityFeedActionStyle.PRIMARY)
            )
        )

        assertEquals(listOf("first", "second", "third"), item.actions.map { it.id })
    }

    private fun action(
        id: String,
        url: String,
        style: CommunityFeedActionStyle
    ) = CommunityFeedAction(
        id = id,
        type = CommunityFeedActionType.OPEN_URL,
        text = id,
        url = url,
        style = style
    )
}
