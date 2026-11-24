package com.driveplayer.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowseTreeTest {

    private val a = Track("aaaaaaaaaaa", "Song A", "X")
    private val b = Track("bbbbbbbbbbb", "Song B", "Y")
    private val c = Track("ccccccccccc", "Song C", "Z")

    private var searchCalls = 0
    private val store = InMemoryLibraryStore(playlist = listOf(a, b), recents = listOf(c), queries = listOf("lofi"))
    private val tree = BrowseTree(store) { q -> searchCalls++; if (q == "lofi") listOf(b, c) else emptyList() }

    @Test fun rootHasThreeFolders() = runTest {
        assertEquals(listOf("playlist", "recents", "queries"), tree.children(BrowseTree.ROOT).map { it.id })
    }

    @Test fun playlistSongsHaveParentPrefixedIds() = runTest {
        val ids = tree.children(BrowseTree.PLAYLIST).map { it.id }
        assertEquals(listOf("playlist|aaaaaaaaaaa", "playlist|bbbbbbbbbbb"), ids)
    }

    @Test fun queryFolderRunsSearchOnceAndCaches() = runTest {
        val folders = tree.children(BrowseTree.QUERIES)
        assertEquals(listOf("q:lofi"), folders.map { it.id })
        tree.children("q:lofi")
        val songs = tree.children("q:lofi")
        assertEquals(listOf("q:lofi|bbbbbbbbbbb", "q:lofi|ccccccccccc"), songs.map { it.id })
        assertEquals(1, searchCalls)
    }

    @Test fun siblingsReturnWholeFolderForQueue() = runTest {
        tree.children(BrowseTree.PLAYLIST)
        val sibs = tree.siblings("playlist|bbbbbbbbbbb")
        assertEquals(listOf(a, b), sibs.map { (it as BrowseTree.Node.Song).track })
    }

    @Test fun trackLookupFallsBackToStore() {
        assertEquals(c, tree.track("recents|ccccccccccc"))
        assertNull(tree.track("nothing"))
    }

    @Test fun parseVideoId() {
        assertEquals("aaaaaaaaaaa", BrowseTree.parseVideoId("q:a|b|aaaaaaaaaaa"))
        assertEquals("aaaaaaaaaaa", BrowseTree.parseVideoId("aaaaaaaaaaa"))
        assertNull(BrowseTree.parseVideoId("playlist"))
    }

    @Test fun recentsAreDedupedAndMostRecentFirst() {
        store.markPlayed(a); store.markPlayed(c)
        assertEquals(listOf(c, a), store.recents.value)
    }
}
