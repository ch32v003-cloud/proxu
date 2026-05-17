package com.proxu.app.dto

import android.content.Context
import com.proxu.app.dto.entities.ProfileItem
import com.proxu.app.enums.CoreResolvedType

/**
 * Runtime context produced by the builder and consumed by CoreConfigManager.
 */
data class CoreConfigContext(
    val context: Context,
    val guid: String,
    val selectedProfile: ProfileItem,
    val resolvedProfiles: List<ProfileItem>,
    val resolvedType: CoreResolvedType,
    val customOutboundProfiles: Map<String, ProfileItem> = emptyMap(),
)