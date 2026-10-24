package com.example.data

import com.example.util.DownloadMetadata

/**
 * Single source of truth for the built-in Starter Model Catalog.
 * To add more starter models in the future, add an entry to [StarterModelCatalog.MODELS].
 */
data class StarterCatalogModel(
    val id: String,
    val name: String,
    val fileName: String,
    val tier: String,
    val approximateSize: String,
    val approximateSizeBytes: Long,
    val minRamGb: Double,
    val recommendedRamGb: Double,
    val task: String,
    val license: String,
    val downloadUrl: String,
    val description: String,
    val requiresLicenseAcceptance: Boolean = false,
    val requiresHfToken: Boolean = false
) {
    fun toDownloadMetadata(): DownloadMetadata = DownloadMetadata(
        displayName = name,
        tier = tier,
        minRamGb = minRamGb,
        recommendedRamGb = recommendedRamGb,
        task = task,
        licenseNote = license,
        requiresLicenseAcceptance = requiresLicenseAcceptance,
        isCurated = true
    )
}

object StarterModelCatalog {

    val MODELS: List<StarterCatalogModel> = listOf(
        StarterCatalogModel(
            id = "qwen3_0_6b",
            name = "Qwen3 0.6B",
            fileName = "Qwen3-0.6B.litertlm",
            tier = "Tiny",
            approximateSize = "about 614 MB",
            approximateSizeBytes = 643_825_664L,
            minRamGb = 3.0,
            recommendedRamGb = 4.0,
            task = "chat",
            license = "Apache-2.0",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            description = "Ultra-lightweight Qwen3 0.6B model for fast everyday chat with low memory footprint."
        ),
        StarterCatalogModel(
            id = "qwen2_5_1_5b_instruct",
            name = "Qwen2.5 1.5B Instruct",
            fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            tier = "Small",
            approximateSize = "about 1.6 GB",
            approximateSizeBytes = 1_717_986_918L,
            minRamGb = 4.0,
            recommendedRamGb = 6.0,
            task = "chat",
            license = "Apache-2.0",
            downloadUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
            description = "Balanced 1.5B instruction-tuned model ideal for 6 GB RAM phones."
        ),
        StarterCatalogModel(
            id = "gemma_4_e2b",
            name = "Gemma 4 E2B",
            fileName = "gemma-4-E2B-it.litertlm",
            tier = "Medium",
            approximateSize = "about 2.6 GB",
            approximateSizeBytes = 2_791_728_742L,
            minRamGb = 6.0,
            recommendedRamGb = 8.0,
            task = "chat and code",
            license = "Apache-2.0",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            description = "Capable medium-tier model for both conversational chat and code generation."
        )
    )
}
