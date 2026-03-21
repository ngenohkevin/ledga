package com.ledga.app.data.repository

import com.ledga.app.data.db.dao.CategoryDao
import com.ledga.app.data.db.dao.CategoryRuleDao
import com.ledga.app.data.db.entity.Category
import com.ledga.app.data.db.entity.CategoryRule
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val categoryRuleDao: CategoryRuleDao
) {
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    fun getAllRules(): Flow<List<CategoryRule>> = categoryRuleDao.getAllRules()

    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)
}
