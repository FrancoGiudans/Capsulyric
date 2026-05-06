package com.example.islandlyrics.feature.parserrule

import com.example.islandlyrics.data.FieldOrder
import com.example.islandlyrics.data.ParserRule
import com.example.islandlyrics.data.lyric.OnlineLyricProvider

data class ParserRuleEditorState(
    val packageName: String,
    val customName: String,
    val usesCarProtocol: Boolean,
    val separator: String,
    val fieldOrder: FieldOrder,
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
    val receiveLyriconRomanization: Boolean
)

fun ParserRule.toEditorState(): ParserRuleEditorState = ParserRuleEditorState(
    packageName = packageName,
    customName = customName.orEmpty(),
    usesCarProtocol = usesCarProtocol,
    separator = separatorPattern,
    fieldOrder = fieldOrder,
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
    receiveLyriconRomanization = receiveLyriconRomanization
)

fun ParserRuleEditorState.toRule(previousRule: ParserRule?, isNewRule: Boolean): ParserRule = ParserRule(
    packageName = packageName.trim(),
    customName = customName.trim().ifEmpty { null },
    enabled = previousRule?.enabled ?: true,
    usesCarProtocol = usesCarProtocol,
    separatorPattern = separator,
    fieldOrder = fieldOrder,
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
    receiveLyriconRomanization = receiveLyriconRomanization
)
