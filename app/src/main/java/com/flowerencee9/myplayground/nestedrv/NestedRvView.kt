package com.flowerencee9.myplayground.nestedrv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowerencee9.myplayground.databinding.ActivityMainBinding
import com.flowerencee9.myplayground.models.ChildModel
import com.flowerencee9.myplayground.models.ParentModel

class NestedRvView : AppCompatActivity() {
    companion object {
        fun newIntent(context: Context) = Intent(context, NestedRvView::class.java)
        private val TAG = NestedRvView::class.java.simpleName
    }

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        val itemList = generateList(20)
        val parentAdapter = ParentAdapter(itemList)
        binding.rv.apply {
            adapter = parentAdapter
            layoutManager = LinearLayoutManager(this@NestedRvView)
            setItemViewCacheSize(50)
            setHasFixedSize(true)
        }
        Log.d("list", "$itemList")
    }

    private fun generateList(s: Int): ArrayList<ParentModel> {
        val list = ArrayList<ParentModel>()
        repeat(s) { i ->
            val child = ArrayList<ChildModel>()
            if (i != 2) {
                repeat(s * 2) {j ->
                    child.add(
                        ChildModel(
                            "parent $i child $j",
                            j
                        )
                    )
                }
            }
            list.add(ParentModel("label $i", child, false))
        }
        return list
    }
}