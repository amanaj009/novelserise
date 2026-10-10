package com.writer_hub.novelserise

import com.engine.core.models.WoSubcategory
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.writer_hub.novelserise.adapter.CategoryNovelAdapter
import com.writer_hub.novelserise.adapter.CategorySubcategoryAdapter
import com.writer_hub.novelserise.databinding.ActivityCategoryNovelBinding
import com.engine.core.viewmodel.AppProvider
import com.engine.core.viewmodel.NovelViewModel
import kotlinx.coroutines.launch

class CategoryNovelActivity : BaseActivity() {

    private lateinit var binding: ActivityCategoryNovelBinding
    private lateinit var novelViewModel: NovelViewModel
    private lateinit var subcategoryAdapter: CategorySubcategoryAdapter
    private lateinit var novelsAdapter: CategoryNovelAdapter

    private var categoryId: String? = null
    private var selectedSubcategoryId: String? = "all"

    // LiveData to hold subcategories specific to this activity's category
    private val _subcategoriesForCategory = MutableLiveData<List<WoSubcategory>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCategoryNovelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        categoryId = intent.getStringExtra(CATEGORY_ID_EXTRA)
        if (categoryId == null) {
            finish()
            return
        }

        novelViewModel = AppProvider.getViewModel(application, NovelViewModel::class.java)

        setupSubcategoryRecyclerView()
        setupNovelsRecyclerView()
        setupListeners()
        observeViewModel()

        // Fetch all categories to then filter for subcategories
        novelViewModel.fetchCategories()
    }

    private fun setupSubcategoryRecyclerView() {
        subcategoryAdapter = CategorySubcategoryAdapter(emptyList()) { subcategory ->
            selectedSubcategoryId = subcategory?.id ?: "all"
            loadNovels()
        }
        binding.rvSubcategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvSubcategories.adapter = subcategoryAdapter
    }

    private fun setupNovelsRecyclerView() {
        novelsAdapter = CategoryNovelAdapter(emptyList()) { novel ->
            NovelDetailActivity.start(this, novel.id ?: "")
        }
        binding.rvNovels.layoutManager = LinearLayoutManager(this)
        binding.rvNovels.adapter = novelsAdapter
    }

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadNovels() {
        categoryId?.let { catId ->
            lifecycleScope.launch {
                novelViewModel.setNovelLoading(true)
                val novels = novelViewModel.fetchNovelsByCategoryAndSubcategory(
                    categoryId = catId,
                    subcategoryId = selectedSubcategoryId ?: "all",
                    type = "get_novel",
                    offset = 0,
                    limit = 20,
                    timeSlot = "0",
                    filter = null,
                    resourceId = "0",
                    userId = null,
                    NovelId = null,
                    competitionId = null
                )
                novelViewModel.setNovels(novels)
                novelViewModel.setNovelLoading(false)
            }
        }
    }

    private fun observeViewModel() {
        // Observe all categories from NovelViewModel
        novelViewModel.categories.observe(this) { allCategories ->
            val targetCategory = allCategories.find { it.id == categoryId }
            val subcategories = targetCategory?.subcategories ?: emptyList()
            _subcategoriesForCategory.postValue(subcategories)
        }

        // Observe the filtered subcategories for this activity
        _subcategoriesForCategory.observe(this) { subcategories ->
            // Add an "All" option at the beginning
            val allSubcategory = WoSubcategory(id = "all", name = "All", novels = emptyList())
            val updatedSubcategories = mutableListOf(allSubcategory)
            updatedSubcategories.addAll(subcategories)
            subcategoryAdapter.updateData(updatedSubcategories)

            // Select "All" by default and load novels
            if (selectedSubcategoryId == "all") {
                subcategoryAdapter.selectSubcategory("all")
                loadNovels()
            }
        }

        // Observe novels from NovelViewModel
        novelViewModel.novels.observe(this) { novels ->
            novelsAdapter.updateData(novels)
            val isLoading = novelViewModel.novelLoading.value ?: false
            if (!isLoading) {
                binding.layoutEmpty.visibility = if (novels.isEmpty()) View.VISIBLE else View.GONE
                binding.rvNovels.visibility = if (novels.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        // Observe loading state from NovelViewModel
        novelViewModel.novelLoading.observe(this) { isLoading ->
            if (isLoading) {
                binding.shimmerViewContainer.visibility = View.VISIBLE
                binding.shimmerViewContainer.startShimmer()
                binding.rvNovels.visibility = View.GONE
                binding.layoutEmpty.visibility = View.GONE
            } else {
                binding.shimmerViewContainer.stopShimmer()
                binding.shimmerViewContainer.visibility = View.GONE
                val novels = novelViewModel.novels.value ?: emptyList()
                binding.rvNovels.visibility = if (novels.isEmpty()) View.GONE else View.VISIBLE
                binding.layoutEmpty.visibility = if (novels.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    companion object {
        const val CATEGORY_ID_EXTRA = "category_id_extra"

        fun start(context: Context, categoryId: String) {
            val intent = Intent(context, CategoryNovelActivity::class.java)
            intent.putExtra(CATEGORY_ID_EXTRA, categoryId)
            context.startActivity(intent)
        }
    }
}
