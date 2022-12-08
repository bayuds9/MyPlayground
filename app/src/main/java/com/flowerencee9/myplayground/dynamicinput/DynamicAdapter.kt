package com.flowerencee9.myplayground.dynamicinput

import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowerencee9.myplayground.R
import com.flowerencee9.myplayground.databinding.LayoutItemDinamicInputBinding

class DynamicAdapter(
    private val data: ArrayList<String>,
    private val listener: (String, Int) -> Unit,
    private val deleteListener : (Int) -> Unit
) : RecyclerView.Adapter<DynamicAdapter.ViewHolder>() {

    private val listData = ArrayList<String>()

    init {
        listData.clear()
        listData.addAll(data)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: String, position: Int) = with(itemView) {
            val binding = LayoutItemDinamicInputBinding.bind(itemView)
            with(binding){
                if (position > 1) btnDelete.visibility = View.VISIBLE
                else btnDelete.visibility = View.GONE

                etInput.apply {
                    addTextChangedListener(object : TextWatcher{
                        override fun beforeTextChanged(
                            p0: CharSequence?,
                            p1: Int,
                            p2: Int,
                            p3: Int
                        ) {}

                        override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

                        override fun afterTextChanged(p0: Editable?) {
                            Log.d("haha", "position $position")
                            listData[position] = p0.toString()
                            listener(p0.toString(), position)
                        }

                    })

                    if (item.isNotEmpty()) setText(item)
                    else text.clear()
                }
                btnDelete.setOnClickListener { deleteListener(position) }
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            (
                    LayoutInflater.from(parent.context)
                        .inflate(R.layout.layout_item_dinamic_input, parent, false)
                    )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listData[position], position)
    }

    override fun getItemCount(): Int = listData.size
}