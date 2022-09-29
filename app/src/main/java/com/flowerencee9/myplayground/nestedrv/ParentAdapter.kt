package com.flowerencee9.myplayground.nestedrv

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.postDelayed
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowerencee9.myplayground.R
import com.flowerencee9.myplayground.databinding.LayoutItemParentBinding
import com.flowerencee9.myplayground.models.ParentModel
import com.flowerencee9.myplayground.supportclass.animateVisibility
import java.lang.Long

class ParentAdapter(
    data: ArrayList<ParentModel>
) : RecyclerView.Adapter<ParentAdapter.VH>() {
    private var listParent = ArrayList<ParentModel>()
    init {
        listParent.addAll(data)
    }

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: ParentModel) = with(itemView) {
            val binding = LayoutItemParentBinding.bind(itemView)
            binding.textView3.text = item.label
            val cAdapter = ChildAdapter(item.child)
            binding.rvChild.apply {
                adapter = cAdapter
                layoutManager = LinearLayoutManager(context)
            }
            if (item.child.isNotEmpty()){
                val duration = 500
                binding.root.setOnClickListener {
                    if (binding.rvChild.visibility == View.VISIBLE){
                        binding.indicator.setImageResource(R.color.teal_200)
                        binding.rvChild.animateVisibility(false, duration.toLong())
                    }
                    else {
                        binding.indicator.setImageResource(R.color.purple_200)
                        binding.rvChild.animateVisibility(true, duration.toLong())
                    }
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val vh = VH(LayoutInflater.from(parent.context).inflate(R.layout.layout_item_parent, parent, false))
        return vh
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(listParent[position])
    }

    override fun getItemCount(): Int = listParent.size

}