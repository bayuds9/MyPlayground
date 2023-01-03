package com.flowerencee9.myplayground

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowerencee9.myplayground.databinding.ActivityMainBinding
import com.flowerencee9.myplayground.dynamicinput.DynamicInputActivity
import com.flowerencee9.myplayground.fr.FRActivity
import com.flowerencee9.myplayground.nestedrv.NestedRvView
import com.flowerencee9.myplayground.pinactivity.PinActivity
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        val menuList = generateMenu()
        val menuListener = object : MainAdapter.MenuListener{
            override fun onClick(item: String, position: Int) {
                when(position){
                    0 -> startActivity(NestedRvView.newIntent(this@MainActivity))
                    1 -> startActivity(PinActivity.newIntent(this@MainActivity))
                    2 -> startActivity(DynamicInputActivity.newIntent(this@MainActivity))
                    3 -> startActivity(FRActivity.myIntent(this@MainActivity))
                }
            }

        }
        val menuAdapter = MainAdapter(menuList, menuListener)
        binding.rv.apply {
            adapter = menuAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun generateMenu(): ArrayList<String> {
        return arrayListOf("Nested RV", "PIN", "DYNAMIC INPUT", "FACE RECOGNITION by TFLITE - FACENET")
    }
}