package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudItemSortingTest {
    private val oldest = CloudItem(
        name = "old.jpg",
        type = "image",
        size = 100,
        mtime = 100
    )
    private val newest = CloudItem(
        name = "new.jpg",
        type = "image",
        size = 10,
        mtime = 300
    )
    private val largest = CloudItem(
        name = "large.mp4",
        type = "video",
        size = 1_000,
        mtime = 200,
        durationSeconds = 45
    )
    private val shortest = CloudItem(
        name = "short.mp4",
        type = "video",
        size = 500,
        mtime = 200,
        durationSeconds = 12
    )
    private val durationUnknown = CloudItem(
        name = "unknown.mp4",
        type = "video",
        size = 700,
        mtime = 200
    )

    @Test
    fun dateSortPlacesNewestFirst() {
        assertEquals(
            listOf("new.jpg", "large.mp4", "old.jpg"),
            listOf(oldest, largest, newest).sortedBy(CloudItemSortOrder.DATE_NEWEST).map { it.name }
        )
    }

    @Test
    fun sizeSortPlacesLargestFirst() {
        assertEquals(
            listOf("large.mp4", "unknown.mp4", "short.mp4", "old.jpg", "new.jpg"),
            listOf(oldest, newest, largest, shortest, durationUnknown)
                .sortedBy(CloudItemSortOrder.SIZE_LARGEST)
                .map { it.name }
        )
    }

    @Test
    fun durationSortPlacesLongestKnownVideoFirstAndUnknownLast() {
        assertEquals(
            listOf("large.mp4", "short.mp4", "new.jpg", "old.jpg", "unknown.mp4"),
            listOf(oldest, newest, shortest, durationUnknown, largest)
                .sortedBy(CloudItemSortOrder.DURATION_LONGEST)
                .map { it.name }
        )
    }

    @Test
    fun durationMillisecondsAreConvertedToSeconds() {
        val item = CloudItem(name = "clip.mp4", type = "video", durationMilliseconds = 91_000)
        assertEquals(91L, item.mediaDurationSeconds)
    }
}
