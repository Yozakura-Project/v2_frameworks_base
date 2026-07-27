/*
 * SPDX-FileCopyrightText: The YozakuraOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.axion.quicklook

/**
 * YozakuraOS DynamicBar Stage A2 compatibility shim.
 *
 * Infinity's DynamicBar consumes live "QuickLook" sports data from the Axion
 * platform service (com.android.axion.quicklook), which does not exist on
 * LineageOS. This shim provides only the SportsData shape that
 * SmartspaceIslandManager reads, so the ported DynamicBar files stay verbatim.
 * The paired no-op QuickLookClient never emits any SportsData, so this type is
 * effectively unused at runtime; it exists purely so the port compiles.
 */
data class SportsData(
    val team1Name: String = "",
    val team2Name: String = "",
    val score1: String = "",
    val score2: String = "",
    val team1IconBytes: ByteArray? = null,
    val team2IconBytes: ByteArray? = null,
    val status: String = "",
    val statusDetail: String = "",
    val league: String = "",
)
