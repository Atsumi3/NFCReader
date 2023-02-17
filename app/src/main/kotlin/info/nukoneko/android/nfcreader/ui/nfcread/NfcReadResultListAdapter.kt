package info.nukoneko.android.nfcreader.ui.nfcread

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import info.nukoneko.android.nfcreader.R
import info.nukoneko.android.nfcreader.model.entity.NfcEntity

class NfcReadResultListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data: List<NfcEntity> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return ContentViewHolder(
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_nfc_list_content,
                    parent,
                    false
                )
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ContentViewHolder) {
            holder.bind(data[position])
        }
    }

    override fun getItemCount(): Int = data.size

    class ContentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.title)
        private val data: TextView = view.findViewById(R.id.data)
        private val description: TextView = view.findViewById(R.id.description)

        fun bind(entity: NfcEntity) {
            val tagName = entity.techName
            data.text = entity.data
            title.text = tagName.split(".").last()
            description.text = tagName
        }
    }
}
