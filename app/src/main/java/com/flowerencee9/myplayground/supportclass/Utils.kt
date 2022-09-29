package com.flowerencee9.myplayground.supportclass

import android.animation.Animator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator

fun View.animateVisibility(setVisible: Boolean, animDuration: Long) {
    if (setVisible) expand(this, animDuration) else collapse(this, animDuration)
}

private fun expand(view: View, animDuration: Long) {
    view.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    val initialHeight = 0
    val targetHeight = view.measuredHeight

    // Older versions of Android (pre API 21) cancel animations for views with a height of 0.
    //v.getLayoutParams().height = 1;
    view.layoutParams.height = 0
    view.visibility = View.VISIBLE

    animateView(view, initialHeight, targetHeight, animDuration)
}

private fun collapse(view: View, animDuration: Long) {
    val initialHeight = view.measuredHeight
    val targetHeight = 0
    view.visibility = View.INVISIBLE

    animateView(view, initialHeight, targetHeight, animDuration)
}

private fun animateView(v: View, initialHeight: Int, targetHeight: Int, animDuration: Long) {
    val valueAnimator = ValueAnimator.ofInt(initialHeight, targetHeight)
    valueAnimator.addUpdateListener { animation ->
        v.layoutParams.height = animation.animatedValue as Int
        v.requestLayout()
    }
    valueAnimator.addListener(object : Animator.AnimatorListener {
        override fun onAnimationEnd(animation: Animator) {
            v.layoutParams.height = targetHeight
        }

        override fun onAnimationStart(animation: Animator) {}
        override fun onAnimationCancel(animation: Animator) {}
        override fun onAnimationRepeat(animation: Animator) {}
    })
    valueAnimator.duration = animDuration
    valueAnimator.interpolator = DecelerateInterpolator()
    valueAnimator.start()
}