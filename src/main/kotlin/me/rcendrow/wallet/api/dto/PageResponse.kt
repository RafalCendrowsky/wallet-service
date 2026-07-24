package me.rcendrow.wallet.api.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

fun <T : Any> Page<T>.toResponse() = PageResponse(
    items = content,
    page = number,
    size = size,
    totalElements = totalElements,
    totalPages = totalPages
)
