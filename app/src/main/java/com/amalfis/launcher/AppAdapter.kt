package com.amalfis.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private var apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: ((AppInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgIcon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        val txtName: TextView = itemView.findViewById(R.id.txtAppName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_oneui, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.txtName.text = app.appName
        holder.imgIcon.setImageDrawable(app.icon)
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
        if (onAppLongClick != null) {
            holder.itemView.setOnLongClickListener {
                onAppLongClick.invoke(app)
                true
            }
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }
}
