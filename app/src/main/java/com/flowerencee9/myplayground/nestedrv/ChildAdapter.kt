package com.flowerencee9.myplayground.nestedrv

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowerencee9.myplayground.R
import com.flowerencee9.myplayground.databinding.LayoutItemChildBinding
import com.flowerencee9.myplayground.models.ChildModel

class ChildAdapter(
    data: ArrayList<ChildModel>
) : RecyclerView.Adapter<ChildAdapter.ViewHolder>() {
    private var listChild = ArrayList<ChildModel>()

    init {
        listChild.addAll(data)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ChildModel) = with(itemView){
            val binding = LayoutItemChildBinding.bind(itemView)
            with(binding){
                textView.text = item.a
                textView2.text = item.b.toString()
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vh = ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.layout_item_child, parent, false))
        return vh
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(listChild[position])
    }

    override fun getItemCount(): Int = listChild.size
}