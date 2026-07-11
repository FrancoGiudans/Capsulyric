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

package com.example.islandlyrics.feature.parserrule

import com.example.islandlyrics.rules.FieldOrder
import com.example.islandlyrics.rules.ParserRule
import com.example.islandlyrics.lyrics.online.provider.OnlineLyricProvider

enum class ParserRuleSourceConfigType {
    NOTIFICATION,
    ONLINE,
    LYRICON
}

data class ParserRuleEditorState(
    val packageName: String,
    val customName: String,
    val usesCarProtocol: Boolean,
    val separator: String,
    val fieldOrder: FieldOrder,
    val useLocalLyrics: Boolean,
    val useOnlineLyrics: Boolean,
    val useSmartOnlineLyricSelection: Boolean,
    val useRawMetadataForOnlineMatching: Boolean,
    val receiveOnlineTranslation: Boolean,
    val receiveOnlineRomanization: Boolean,
    val onlineLyricProviderOrder: List<OnlineLyricProvider>,
    val useSuperLyricApi: Boolean,
    val useLyricGetterApi: Boolean,
    val useLyriconApi: Boolean,
    val receiveLyriconTranslation: Boolean,
    val receiveLyriconRomanization: Boolean,
    val useLastFmScrobble: Boolean
)

fun ParserRule.toEditorState(): ParserRuleEditorState = ParserRuleEditorState(
    packageName = packageName,
    customName = customName.orEmpty(),
    usesCarProtocol = usesCarProtocol,
    separator = separatorPattern,
    fieldOrder = fieldOrder,
    useLocalLyrics = useLocalLyrics,
    useOnlineLyrics = useOnlineLyrics,
    useSmartOnlineLyricSelection = useSmartOnlineLyricSelection,
    useRawMetadataForOnlineMatching = useRawMetadataForOnlineMatching,
    receiveOnlineTranslation = receiveOnlineTranslation,
    receiveOnlineRomanization = receiveOnlineRomanization,
    onlineLyricProviderOrder = OnlineLyricProvider.normalizeOrder(onlineLyricProviderOrder),
    useSuperLyricApi = useSuperLyricApi,
    useLyricGetterApi = useLyricGetterApi,
    useLyriconApi = useLyriconApi,
    receiveLyriconTranslation = receiveLyriconTranslation,
    receiveLyriconRomanization = receiveLyriconRomanization,
    useLastFmScrobble = useLastFmScrobble
)

fun ParserRuleEditorState.withSourceSettingsFrom(source: ParserRuleEditorState): ParserRuleEditorState = copy(
    usesCarProtocol = source.usesCarProtocol,
    separator = source.separator,
    fieldOrder = source.fieldOrder,
    useLocalLyrics = source.useLocalLyrics,
    useOnlineLyrics = source.useOnlineLyrics,
    useSmartOnlineLyricSelection = source.useSmartOnlineLyricSelection,
    useRawMetadataForOnlineMatching = source.useRawMetadataForOnlineMatching,
    receiveOnlineTranslation = source.receiveOnlineTranslation,
    receiveOnlineRomanization = source.receiveOnlineRomanization,
    onlineLyricProviderOrder = source.onlineLyricProviderOrder,
    useSuperLyricApi = source.useSuperLyricApi,
    useLyricGetterApi = source.useLyricGetterApi,
    useLyriconApi = source.useLyriconApi,
    receiveLyriconTranslation = source.receiveLyriconTranslation,
    receiveLyriconRomanization = source.receiveLyriconRomanization,
    useLastFmScrobble = source.useLastFmScrobble
)

fun ParserRuleEditorState.toRule(previousRule: ParserRule?): ParserRule = ParserRule(
    packageName = packageName.trim(),
    customName = customName.trim().ifEmpty { null },
    enabled = previousRule?.enabled ?: true,
    usesCarProtocol = usesCarProtocol,
    separatorPattern = separator,
    fieldOrder = fieldOrder,
    useLocalLyrics = useLocalLyrics,
    useOnlineLyrics = useOnlineLyrics,
    useSmartOnlineLyricSelection = useSmartOnlineLyricSelection,
    useRawMetadataForOnlineMatching = useRawMetadataForOnlineMatching,
    receiveOnlineTranslation = receiveOnlineTranslation,
    receiveOnlineRomanization = receiveOnlineRomanization,
    onlineLyricProviderOrder = onlineLyricProviderOrder.map { it.id },
    useSuperLyricApi = useSuperLyricApi,
    useLyricGetterApi = useLyricGetterApi,
    useLyriconApi = useLyriconApi,
    receiveLyriconTranslation = receiveLyriconTranslation,
    receiveLyriconRomanization = receiveLyriconRomanization,
    useLastFmScrobble = useLastFmScrobble
)

