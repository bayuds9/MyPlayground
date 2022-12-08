package com.flowerencee9.myplayground.dynamicinput

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.flowerencee9.myplayground.databinding.ActivityDynamicInputBinding

class DynamicInputActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context) = Intent(context, DynamicInputActivity::class.java)
        private val TAG = DynamicInputActivity::class.java.simpleName
    }

    private var listString = ArrayList<String>()

    private lateinit var binding: ActivityDynamicInputBinding

    private fun onItemDeleted(deletedIndex: Int) {
        Log.d(TAG, "item removed from index $deletedIndex")
        listString.removeAt(deletedIndex)
        setupRv()
    }

    private fun onItemListener(stringValue: String, indexPosition: Int) {
        Log.d(TAG, "item on index $indexPosition is $stringValue")
        listString[indexPosition] = stringValue
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDynamicInputBinding.inflate(layoutInflater)
        setContentView(binding.root)
        listString.add("")
        listString.add("")
        setupView()
    }

    private val textListener : (String, Int) -> Unit = { string, index ->
        onItemListener(string, index)
    }

    private val deleteListener : (Int) -> Unit = { index ->
        onItemDeleted(index)
    }

    private fun setupView() {
        setupRv()
        with(binding){
            btnAdd.setOnClickListener { addContent() }
            btnSave.setOnClickListener { saveContent() }
        }
    }

    private fun setupRv() {
        val rvAdapter = DynamicAdapter(listString, textListener, deleteListener)
        binding.rvInput.apply {
            layoutManager = LinearLayoutManager(this@DynamicInputActivity)
            adapter = rvAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(listString.size)
        }
    }

    private fun addContent() {
        listString.add("")
        setupRv()
    }

    private fun saveContent() {
        Log.d(TAG, "all list value $listString")
    }
}