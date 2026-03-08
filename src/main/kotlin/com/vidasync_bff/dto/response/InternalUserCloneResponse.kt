package com.vidasync_bff.dto.response

data class InternalUserCloneResponse(
    val sourceUserId: String,
    val clonedUserId: String?,
    val clonedUsername: String?,
    val dryRun: Boolean,
    val copied: InternalUserCloneCopied,
    val audit: InternalUserCloneAudit,
    val security: InternalUserCloneSecurity
)

data class InternalUserCloneCopied(
    val profile: Int,
    val meals: Int,
    val favorites: Int
)

data class InternalUserCloneAudit(
    val clonedFrom: String,
    val clonedBy: String,
    val whenAt: String
)

data class InternalUserCloneSecurity(
    val passwordCopied: Boolean,
    val sessionsCopied: Boolean
)
