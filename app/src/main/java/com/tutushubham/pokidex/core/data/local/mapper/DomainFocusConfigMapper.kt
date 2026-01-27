package com.tutushubham.pokidex.core.data.local.mapper

import com.tutushubham.pokidex.core.data.DomainFocusConfigEntity
import com.tutushubham.pokidex.core.domain.entity.DomainFocusConfig
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
private data class RotationData(val order: List<String>)

@Serializable
private data class WeightedData(val weights: Map<String, Int>)

fun DomainFocusConfigEntity.toDomain(): DomainFocusConfig {
    val strategy = when (strategyType) {
        "MANUAL" -> FocusStrategy.Manual
        "ROTATION" -> {
            val data = strategyData?.let { Json.decodeFromString<RotationData>(it) }
            FocusStrategy.Rotation(data?.order ?: emptyList())
        }
        "WEIGHTED" -> {
            val data = strategyData?.let { Json.decodeFromString<WeightedData>(it) }
            FocusStrategy.Weighted(data?.weights ?: emptyMap())
        }
        "DEADLINE" -> FocusStrategy.DeadlineDriven
        else -> FocusStrategy.Manual
    }

    return DomainFocusConfig(
        domain = domain,
        strategy = strategy,
        manualOverrideFocusId = manualOverrideFocusId,
        createdAt = createdAt
    )
}

fun DomainFocusConfig.toEntity(): DomainFocusConfigEntity {
    val (strategyType, strategyData) = when (val s = strategy) {
        is FocusStrategy.Manual -> "MANUAL" to null
        is FocusStrategy.Rotation -> {
            val data = Json.encodeToString(RotationData(s.order))
            "ROTATION" to data
        }
        is FocusStrategy.Weighted -> {
            val data = Json.encodeToString(WeightedData(s.weights))
            "WEIGHTED" to data
        }
        is FocusStrategy.DeadlineDriven -> "DEADLINE" to null
    }

    return DomainFocusConfigEntity(
        domain = domain,
        strategyType = strategyType,
        strategyData = strategyData,
        manualOverrideFocusId = manualOverrideFocusId,
        createdAt = createdAt
    )
}
