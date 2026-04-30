package com.vantage.contracts

import com.vantage.models.FilterType

interface IFilterEngine {
    fun setFilter(filter: FilterType)
    fun getCurrentFilter(): FilterType
}
