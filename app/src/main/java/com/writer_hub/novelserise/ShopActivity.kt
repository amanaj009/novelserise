package com.writer_hub.novelserise

import android.os.Bundle
import com.writer_hub.novelserise.databinding.ActivityShopBinding
import com.writer_hub.novelserise.fragment.ShopFragment

class ShopActivity : BaseActivity() {

    private lateinit var binding: ActivityShopBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ShopFragment())
                .commit()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
