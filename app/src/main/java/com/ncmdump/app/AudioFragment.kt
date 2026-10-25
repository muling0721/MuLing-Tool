package com.ncmdump.app

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

class AudioFragment : Fragment() {

    private var currentChild: Fragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_audio, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.audioContent, MenuFragment())
                .commit()
        }
        childFragmentManager.addOnBackStackChangedListener {
            view?.postDelayed({
                refreshToolbar()
                (activity as? MainActivity)?.applyCardOpacity()
            }, 320)
        }
        view.post { refreshToolbar() }
    }

    fun openChild(fragment: Fragment) {
        currentChild = fragment
        (activity as? MainActivity)?.showAudioBack(true)
        childFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.nav_enter,
                R.anim.nav_exit,
                R.anim.nav_pop_enter,
                R.anim.nav_pop_exit
            )
            .replace(R.id.audioContent, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun refreshToolbar() {
        val hasChild = childFragmentManager.backStackEntryCount > 0
        (activity as? MainActivity)?.showAudioBack(hasChild)
    }

    fun goBack(): Boolean {
        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStack()
            currentChild = null
            return true
        }
        return false
    }

    // 获取当前可见的子Fragment
    private fun getVisibleChild(): Fragment? {
        return childFragmentManager.fragments.find { it.isVisible && it !is MenuFragment }
    }

    // 宿主Activity回调：文件选择结果
    fun onFilesSelected(uris: List<Uri>) {
        val child = getVisibleChild() ?: currentChild
        when (child) {
            is AudioParseFragment -> child.onFilesSelected(uris)
            is AudioConvertFragment -> uris.firstOrNull()?.let { child.onFileSelected(it) }
        }
    }

    // 菜单Fragment
    class MenuFragment : Fragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(R.layout.fragment_audio_menu, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            view.findViewById<LinearLayout>(R.id.menuParse).setOnClickListener {
                (parentFragment as AudioFragment).openChild(AudioParseFragment())
            }
            view.findViewById<LinearLayout>(R.id.menuConvert).setOnClickListener {
                (parentFragment as AudioFragment).openChild(AudioConvertFragment())
            }
            view.findViewById<LinearLayout>(R.id.menuNetease).setOnClickListener {
                (parentFragment as AudioFragment).openChild(NeteaseFragment())
            }
        }
    }
}
