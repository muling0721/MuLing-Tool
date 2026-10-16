package com.ncmdump.app

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

class SongAdapter(
    private val onPlay: (NeteaseApi.Song) -> Unit,
    private val onMore: (NeteaseApi.Song) -> Unit
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private val items = mutableListOf<NeteaseApi.Song>()

    fun submit(list: List<NeteaseApi.Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = items[position]
        holder.name.text = song.name
        holder.info.text = listOf(song.artist, song.album)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        holder.duration.text = if (song.durationMs > 0) NeteaseApi.formatDuration(song.durationMs) else ""
        loadCover(holder.cover, song.cover)
        holder.itemView.setOnClickListener { onPlay(song) }
        holder.itemView.setOnLongClickListener { onMore(song); true }
    }

    private fun loadCover(iv: ImageView, url: String) {
        iv.tag = url
        iv.setImageDrawable(null)
        if (url.isBlank()) return
        Thread {
            val bmp = try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (_: Exception) {
                null
            }
            if (bmp != null) {
                iv.post { if (iv.tag == url) iv.setImageBitmap(bmp) }
            }
        }.start()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.ivSongCover)
        val name: TextView = view.findViewById(R.id.tvSongName)
        val info: TextView = view.findViewById(R.id.tvSongInfo)
        val duration: TextView = view.findViewById(R.id.tvSongDuration)
    }
}
