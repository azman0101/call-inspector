package com.example.data.model

data class ArcepOperator(
    val code: String,
    val name: String,
    val siret: String? = null,
    val rcs: String? = null,
    val address: String? = null,
    val declarationDate: String? = null
)

data class ArcepNumberRange(
    val id: Long = 0,
    val ezabpqm: String,
    val trancheDebut: String,
    val trancheFin: String,
    val operatorCode: String,
    val operatorName: String,
    val territory: String? = null,
    val attributionDate: String? = null
)
