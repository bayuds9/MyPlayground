package com.flowerencee9.myplayground

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.flowerencee9.myplayground.databinding.LayoutMenuBinding

class MainAdapter(
    var data: ArrayList<String>,
    private val listener: MenuListener
) : RecyclerView.Adapter<MainAdapter.ViewHolder>() {
    var dataMenu = ArrayList<String>()

    init {
        dataMenu.addAll(data)
    }

    interface MenuListener {
        fun onClick(item: String, position: Int)
    }

    inner class ViewHolder (itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(item: String, position: Int, listener: MenuListener) = with(itemView) {
            val binding = LayoutMenuBinding.bind(itemView)
            binding.textView.text = item
            binding.root.setOnClickListener {
                listener.onClick(item, position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder = ViewHolder(
        (
                LayoutInflater.from(parent.context).inflate(R.layout.layout_menu, parent, false)
                )
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(dataMenu[position], position, listener)
    }

    override fun getItemCount(): Int = dataMenu.size

}