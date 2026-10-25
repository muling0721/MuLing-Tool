package com.ncmdump.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import kotlin.concurrent.thread
import java.net.URL

class VideoFragment : Fragment() {

    private lateinit var etBvid: EditText
    private lateinit var btnParse: Button
    private lateinit var btnGetPlayUrl: Button
    private lateinit var btnPlayInline: Button
    private lateinit var tvBiliStatus: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvOwner: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvPages: TextView
    private lateinit var ivCover: ImageView
    private lateinit var cardVideoInfo: CardView
    private lateinit var cardPages: CardView

    private var currentVideoInfo: BiliParser.VideoInfo? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etBvid = view.findViewById(R.id.etBvid)
        btnParse = view.findViewById(R.id.btnParse)
        btnGetPlayUrl = view.findViewById(R.id.btnGetPlayUrl)
        btnPlayInline = view.findViewById(R.id.btnPlayInline)
        tvBiliStatus = view.findViewById(R.id.tvBiliStatus)
        tvTitle = view.findViewById(R.id.tvTitle)
        tvOwner = view.findViewById(R.id.tvOwner)
        tvDuration = view.findViewById(R.id.tvDuration)
        tvDesc = view.findViewById(R.id.tvDesc)
        tvPages = view.findViewById(R.id.tvPages)
        ivCover = view.findViewById(R.id.ivCover)
        cardVideoInfo = view.findViewById(R.id.cardVideoInfo)
        cardPages = view.findViewById(R.id.cardPages)

        btnParse.setOnClickListener {
            val input = etBvid.text.toString().trim()
            if (input.isEmpty()) {
                Toast.makeText(requireContext(), "请输入BV号或链接", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            parseVideo(input)
        }

        btnGetPlayUrl.setOnClickListener {
            currentVideoInfo?.let { info -> downloadVideo(info) }
        }

        btnPlayInline.setOnClickListener {
            currentVideoInfo?.let { info -> playInline(info.bvid, info.cid, info.title) }
        }

    }

    private fun parseVideo(input: String) {
        tvBiliStatus.text = "正在解析..."
        btnParse.isEnabled = false
        cardVideoInfo.visibility = CardView.GONE
        cardPages.visibility = CardView.GONE

        thread {
            val info = BiliParser.getVideoInfo(input)
            requireActivity().runOnUiThread {
                btnParse.isEnabled = true
                if (info != null) {
                    currentVideoInfo = info
                    showVideoInfo(info)
                    tvBiliStatus.text = "解析成功"
                } else {
                    val err = BiliParser.lastError
                    tvBiliStatus.text = "解析失败: ${err.ifEmpty { "请检查BV号或网络" }}"
                }
            }
        }
    }

    private fun showVideoInfo(info: BiliParser.VideoInfo) {
        cardVideoInfo.visibility = CardView.VISIBLE
        tvTitle.text = info.title
        tvOwner.text = "UP主: ${info.owner}"
        tvDuration.text = "时长: ${BiliParser.formatDuration(info.duration)}  |  BV: ${info.bvid}"
        tvDesc.text = info.desc.ifEmpty { "暂无简介" }

        thread {
            try {
                val url = URL(info.cover)
                val bitmap = BitmapFactory.decodeStream(url.openStream())
                requireActivity().runOnUiThread {
                    if (bitmap != null) ivCover.setImageBitmap(bitmap)
                }
            } catch (_: Exception) {}
        }

        if (info.pages.size > 1) {
            cardPages.visibility = CardView.VISIBLE
            val sb = StringBuilder()
            info.pages.forEach { page ->
                sb.append("P${page.page}: ${page.part} (${BiliParser.formatDuration(page.duration)})\n")
            }
            tvPages.text = sb.toString().trimEnd()
        }

    }

    private fun downloadVideo(info: BiliParser.VideoInfo) {
        tvBiliStatus.text = "正在获取下载地址..."
        btnGetPlayUrl.isEnabled = false
        thread {
            val playUrl = BiliParser.getDirectPlayUrl(info.bvid, info.cid, qn = 80)
            activity?.runOnUiThread {
                btnGetPlayUrl.isEnabled = true
                if (playUrl == null || playUrl.videoUrl.isEmpty()) {
                    tvBiliStatus.text = "下载失败：无法获取视频地址（可能仅支持 DASH）"
                    return@runOnUiThread
                }
                val name = info.title.ifBlank { info.bvid }
                    .replace(Regex("[\\/:*?\"<>|]"), "_")
                    .take(60) + ".mp4"
                DownloadHelper.download(
                    requireContext(),
                    playUrl.videoUrl,
                    OutputDir.file(requireContext(), name),
                    "https://www.bilibili.com/",
                    info.title
                )
                tvBiliStatus.text = "已开始下载：$name"
            }
        }
    }

    private fun playInline(bvid: String, cid: Long, title: String) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("bvid", bvid)
            putExtra("cid", cid)
            putExtra("video_title", title)
        }
        startActivity(intent)
    }

}
