package com.example.gzingapp.ui.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.gzingapp.ui.auth.LoginFragment
import com.example.gzingapp.ui.auth.SignupFragment

class AuthViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> LoginFragment()
            1 -> SignupFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
} 