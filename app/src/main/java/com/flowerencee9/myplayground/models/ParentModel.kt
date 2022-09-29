package com.flowerencee9.myplayground.models

data class ParentModel(
    val label: String,
    val child: ArrayList<ChildModel>,
    val expanded: Boolean
)
