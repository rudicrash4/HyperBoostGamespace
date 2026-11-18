package io.github.hyperboost.gamespace

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.hyperboost.gamespace.databinding.ItemGameBinding

class GameAdapter(private val onPlay: (GameInfo) -> Unit) :
    RecyclerView.Adapter<GameAdapter.VH>() {

    private var items: List<GameInfo> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<GameInfo>) {
        items = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemGameBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val g = items[position]
        holder.b.imgIcon.setImageDrawable(g.icon)
        holder.b.tvName.text = g.label
        holder.b.tvPkg.text = g.packageName
        holder.b.btnPlay.setOnClickListener { onPlay(g) }
    }

    override fun getItemCount() = items.size
}
