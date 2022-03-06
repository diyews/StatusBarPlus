package ws.diye.statusbarplus

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView

import android.content.pm.PackageInfo

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import java.util.*
import kotlin.collections.ArrayList


class AllInstalledAppActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var installedAppsList: ArrayList<AppModel>
    private lateinit var installedAppAdapter: AppAdapter

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_installed_app)
        recyclerView = findViewById(R.id.recycler_view)
        val loadingDialog = Dialog(this)
        loadingDialog.setContentView(R.layout.loading)
        loadingDialog.window!!.setLayout(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        loadingDialog.setCancelable(false)
        installedAppsList = ArrayList()
        loadingDialog.show()
        Handler(Looper.getMainLooper()).postDelayed({
            getInstalledApps()
            loadingDialog.dismiss()
            findViewById<TextView>(R.id.totalInstalledApp).text =
                "${getString(R.string.total_installed_apps)} ${installedAppsList.size}"
            installedAppAdapter = AppAdapter(this, installedAppsList)
            recyclerView.adapter = installedAppAdapter
        }, 500)

    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getInstalledApps(): ArrayList<AppModel> {
        installedAppsList.clear()
        val packs = packageManager.getInstalledPackages(0)
        for (i in packs.indices) {
            val p = packs[i]
            if (!isSystemPackage(p)) {
                val appName = p.applicationInfo.loadLabel(packageManager).toString()
                val icon = p.applicationInfo.loadIcon(packageManager)
                val packages = p.applicationInfo.packageName
                installedAppsList.add(AppModel(appName, icon, packages))
            }
        }
        installedAppsList.sortBy { it.getName().capitalized() }
        return installedAppsList
    }
    private fun String.capitalized(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase())
                it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }
    private fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        val search = menu.findItem(R.id.app_bar_search)

        val searchView = search.actionView as SearchView
        searchView.maxWidth = android.R.attr.width
        searchView.queryHint = "Search app name or package"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                val appModelArrayList: ArrayList<AppModel> = ArrayList()

                for (i in installedAppsList) {
                    if (i.getName().lowercase(Locale.getDefault()).contains(
                            newText!!.lowercase(
                                Locale.getDefault()
                            )
                        )
                        ||
                        i.getPackages().lowercase(Locale.getDefault()).contains(
                            newText.lowercase(
                                Locale.getDefault()
                            )
                        )
                    ) {
                        appModelArrayList.add(i)
                    }
                }
                installedAppAdapter =
                    AppAdapter(this@AllInstalledAppActivity, appModelArrayList)

                recyclerView.adapter = installedAppAdapter
                installedAppAdapter.notifyDataSetChanged()
                return true
            }
        })

        return super.onCreateOptionsMenu(menu)
    }
}

class AppModel(private var name:String, private var icon: Drawable, private var packages:String) {
    fun getName(): String {
        return name
    }

    fun getIcon(): Drawable {
        return icon
    }

    fun getPackages(): String {
        return packages
    }
}

class AppAdapter(private val context: Context, private var appModelList: ArrayList<AppModel>) :
    RecyclerView.Adapter<AppAdapter.ViewHolder>() {


    class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {
        val appNameTxt: TextView = itemView.findViewById(R.id.list_app_name)
        val appPackageNameTxt: TextView = itemView.findViewById(R.id.app_package)
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View =
            LayoutInflater.from(parent.context)
                .inflate(R.layout.installed_app_layout, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.appNameTxt.text = appModelList[position].getName()
        holder.appIcon.setImageDrawable(appModelList[position].getIcon())
        holder.appPackageNameTxt.text = appModelList[position].getPackages()

        holder.itemView.setOnClickListener {
            val dialogListTitle = arrayOf("Open App", "App Info")
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            builder.setTitle("Choose Action")
                .setItems(
                    dialogListTitle
                ) { _, which ->
                    when (which) {
                        0 -> {
                            val intent =
                                context.packageManager.getLaunchIntentForPackage(appModelList[position].getPackages())
                            if (intent != null) {
                                context.startActivity(intent)
                            }else{
                                Toast.makeText(context,"System app is not open for any reason.",Toast.LENGTH_LONG).show()
                            }
                        }
                        1 -> {
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            intent.data =
                                Uri.parse("package:${appModelList[position].getPackages()}")
                            context.startActivity(intent)
                        }
                    }
                }
            builder.show()
        }

    }

    override fun getItemCount(): Int {
        return appModelList.size
    }
}