package com.marketap.android

import java.io.Serializable

data class Product(
    val id: String,
    val name: String,
    val category: String,
    val manufacturer: String,
    val price: Double,
    val description: String
) : Serializable