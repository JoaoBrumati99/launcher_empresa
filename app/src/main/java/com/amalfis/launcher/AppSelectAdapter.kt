package com.amalfis.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppSelectAdapter(
    private val apps: List<AppInfo>,
    private val onToggleAllowed: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgIcon: ImageView = itemView.findViewById(R.id.imgAppIcon)
        val txtName: TextView = itemView.findViewById(R.id.txtAppName)
        val txtPackage: TextView = itemView.findViewById(R.id.txtPackageName)
        val chkAllowed: CheckBox = itemView.findViewById(R.id.chkAllowed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_checkbox, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.txtName.text = app.appName
        holder.txtPackage.text = app.packageName
        holder.imgIcon.setImageDrawable(app.icon)

        holder.chkAllowed.setOnCheckedChangeListener(null)
        holder.chkAllowed.isChecked = app.isAllowed

        holder.chkAllowed.setOnCheckedChangeListener { _, isChecked ->
            app.isAllowed = isChecked
            onToggleAllowed(app, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.chkAllowed.isChecked = !holder.chkAllowed.isChecked
        }
    }

    override fun getItemCount(): Int = apps.size
}
