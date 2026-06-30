package com.qnh.helper

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView

    private val fragments = listOf(
        { HomeFragment() },
        { HouerjiaFragment() },
        { LogFragment() }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        viewPager = ViewPager2(this).apply {
            id = View.generateViewId()
            adapter = ViewPagerAdapter(this@MainActivity)
            offscreenPageLimit = 2
        }
        root.addView(viewPager, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        bottomNav = BottomNavigationView(this).apply {
            id = View.generateViewId()
            setBackgroundColor(0xFFFFFFFF.toInt())
            itemIconTintList = android.content.res.ColorStateList.valueOf(0xFF94A3B8.toInt())
            itemTextColor = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(0xFF3B82F6.toInt(), 0xFF94A3B8.toInt())
            )
            labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
        }

        val menu = bottomNav.menu
        menu.add(0, 0, 0, "首页").apply {
            icon = getDrawable(R.drawable.ic_home)
        }
        menu.add(0, 1, 1, "猴儿家").apply {
            icon = getDrawable(R.drawable.ic_clock)
        }
        menu.add(0, 2, 2, "日志").apply {
            icon = getDrawable(R.drawable.ic_log)
        }

        bottomNav.setOnItemSelectedListener { item ->
            viewPager.currentItem = item.itemId
            true
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = position
            }
        })

        root.addView(bottomNav, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
    }

    private inner class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = fragments.size
        override fun createFragment(position: Int): Fragment = fragments[position]()
    }
}
