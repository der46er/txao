package com.txao.files

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutRulesTest {
    private val roots = listOf("/storage/emulated/0", "/storage/ABCD-1234")

    @Test fun storageRootsDefaultToGrid() {
        assertEquals(FolderLayout.Grid, LayoutRules.default("/storage/emulated/0", roots))
        assertEquals(FolderLayout.Grid, LayoutRules.default("/storage/emulated/0/", roots))
        assertEquals(FolderLayout.Grid, LayoutRules.default("/storage/ABCD-1234", roots))
    }

    @Test fun subfoldersDefaultToList() {
        assertEquals(FolderLayout.List, LayoutRules.default("/storage/emulated/0/Download", roots))
        assertEquals(FolderLayout.List, LayoutRules.default("/storage/emulated/0/Download/Quick Share", roots))
    }

    @Test fun storedChoiceWinsOverDefault() {
        assertEquals(FolderLayout.Grid, LayoutRules.resolve("Grid", "/storage/emulated/0/DCIM", roots))
        assertEquals(FolderLayout.List, LayoutRules.resolve("List", "/storage/emulated/0", roots))
    }

    @Test fun unknownStoredValueFallsBackToDefault() {
        assertEquals(FolderLayout.List, LayoutRules.resolve("nonsense", "/storage/emulated/0/Music", roots))
        assertEquals(FolderLayout.Grid, LayoutRules.resolve(null, "/storage/emulated/0", roots))
    }

    @Test fun keysAreIndependentPerFolder() {
        assert(LayoutRules.key("/a/b") != LayoutRules.key("/a/c"))
        assertEquals(LayoutRules.key("/a/b"), LayoutRules.key("/a/b/"))
    }
}
