/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParserRuleBackupTest {

    @Test
    fun validatedParserRulesJson_rejectsEmptyMalformedAndInvalidArrays() {
        assertNull(SettingsBackupManager.validatedParserRulesJson("[]"))
        assertNull(SettingsBackupManager.validatedParserRulesJson("not-json"))
        assertNull(SettingsBackupManager.validatedParserRulesJson("[{}]"))
        assertNull(SettingsBackupManager.validatedParserRulesJson("[null]"))
    }

    @Test
    fun parserRuleSelection_supportsPartialSelectionAndUnderscorePackages() {
        val underscorePackage = "com.example.music_player"
        val dottedPackage = "com.example.music.player"
        val underscoreId = BackupCategories.parserSubGroupId(underscorePackage)
        val dottedId = BackupCategories.parserSubGroupId(dottedPackage)

        assertNotEquals(underscoreId, dottedId)
        assertEquals(
            setOf(underscorePackage),
            BackupCategories.selectedParserPackages(setOf(underscoreId))
        )
        assertEquals(underscorePackage, BackupCategories.packageNameFromParserSubGroupId(underscoreId))
        assertEquals(dottedPackage, BackupCategories.packageNameFromParserSubGroupId(dottedId))
        assertEquals(emptySet<String>(), BackupCategories.selectedParserPackages(setOf("parser_pkg_")))
    }

    @Test
    fun parserRuleSelection_keepsLegacyIdsReadable() {
        assertEquals(
            "com.example.player",
            BackupCategories.packageNameFromParserSubGroupId("parser_com_example_player")
        )
    }
}
