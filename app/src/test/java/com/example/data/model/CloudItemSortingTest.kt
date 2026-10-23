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
            listOf(oldest, largest, newest).sortedByOrder(CloudItemSortOrder.DATE_NEWEST).map { it.name }
        )
    }

    @Test
    fun sizeSortPlacesLargestFirst() {
        assertEquals(
            listOf("large.mp4", "unknown.mp4", "short.mp4", "old.jpg", "new.jpg"),
            listOf(oldest, newest, largest, shortest, durationUnknown)
                .sortedByOrder(CloudItemSortOrder.SIZE_LARGEST)
                .map { it.name }
        )
    }

    @Test
    fun durationSortPlacesLongestKnownVideoFirstAndUnknownLast() {
        assertEquals(
            listOf("large.mp4", "short.mp4", "new.jpg", "old.jpg", "unknown.mp4"),
            listOf(oldest, newest, shortest, durationUnknown, largest)
                .sortedByOrder(CloudItemSortOrder.DURATION_LONGEST)
                .map { it.name }
        )
    }

    @Test
    fun durationMillisecondsAreConvertedToSeconds() {
        val item = CloudItem(name = "clip.mp4", type = "video", durationMilliseconds = 91_000)
        assertEquals(91L, item.mediaDurationSeconds)
    }

    private val folder = CloudItem(name = "zzz folder", kind = "folder", mtime = 999)
    private val banana = CloudItem(name = "banana.txt", type = "document", mtime = 100)
    private val apple = CloudItem(name = "apple.txt", type = "document", mtime = 200)
    private val cherry = CloudItem(name = "cherry.txt", type = "document", mtime = 300)

    @Test
    fun foldersComeFirstInEveryOrder() {
        val result = listOf(apple, folder, cherry).sortedByOrder(CloudItemSortOrder.NAME_AZ)
        assertEquals("zzz folder", result.first().name)
    }

    @Test
    fun nameSortAzAndZa() {
        assertEquals(
            listOf("apple.txt", "banana.txt", "cherry.txt"),
            listOf(cherry, apple, banana).sortedByOrder(CloudItemSortOrder.NAME_AZ).map { it.name }
        )
        assertEquals(
            listOf("cherry.txt", "banana.txt", "apple.txt"),
            listOf(apple, cherry, banana).sortedByOrder(CloudItemSortOrder.NAME_ZA).map { it.name }
        )
    }

    @Test
    fun dateSortOldestFirstAndUnknownMtimeLast() {
        val noMtime = CloudItem(name = "no-time.bin", type = "document", mtime = 0)
        assertEquals(
            listOf("banana.txt", "apple.txt", "cherry.txt", "no-time.bin"),
            listOf(noMtime, cherry, apple, banana)
                .sortedByOrder(CloudItemSortOrder.DATE_OLDEST)
                .map { it.name }
        )
    }

    @Test
    fun sizeSortSmallestFirstAndUnknownSizeLast() {
        val unknown = CloudItem(name = "unknown.bin", type = "document", size = 0)
        assertEquals(
            listOf("new.jpg", "old.jpg", "short.mp4", "large.mp4", "unknown.bin"),
            listOf(unknown, largest, shortest, newest, oldest)
                .sortedByOrder(CloudItemSortOrder.SIZE_SMALLEST)
                .map { it.name }
        )
    }
}
