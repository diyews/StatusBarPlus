package ws.diye.statusbarplus

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView

import android.content.pm.PackageInfo

import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import java.util.*
import kotlin.collections.ArrayList


class AllInstalledAppActivity : AppCompatActivity() {
    private val allAppsList: ArrayList<AppModel> = ArrayList()
    private val appsToSearchList: ArrayList<AppModel> = ArrayList()
    private lateinit var recyclerView: RecyclerView
    private lateinit var installedAppAdapter: AppAdapter

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
        loadingDialog.show()
        Handler(Looper.getMainLooper()).postDelayed({
            getAllApps()
            setAppsToSearch()
            loadingDialog.dismiss()
            setRecyclerViewDataSet(appsToSearchList)
        }, 500)

    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun getAllApps(): ArrayList<AppModel> {
        val packs = packageManager.getInstalledPackages(0)
        for (i in packs.indices) {
            val p = packs[i]
            val isSystem = isSystemPackage(p)
            val checkFlagsPass: Boolean = (p.applicationInfo.flags and ApplicationInfo.FLAG_SUPPORTS_RTL != 0 && p.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA != 0)
                .let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        it && p.applicationInfo.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS != 0
                    } else {
                        it
                    }
                }
            if (!isSystem || checkFlagsPass) {
                val appName = p.applicationInfo.loadLabel(packageManager).toString()
                val icon = p.applicationInfo.loadIcon(packageManager)
                val packages = p.applicationInfo.packageName
                allAppsList.add(AppModel(appName, icon, packages, isSystem))
            }
        }
        allAppsList.sortBy { it.name.capitalized() }
        return allAppsList
    }
    private fun String.capitalized(): String {
        return this.replaceFirstChar {
            if (it.isLowerCase())
                it.titlecase(Locale.getDefault())
            else it.toString()
        }
    }
    private fun setAppsToSearch(show: Boolean = false) {
        appsToSearchList.clear()
        if (show) {
            appsToSearchList.addAll(allAppsList)
        } else {
            appsToSearchList.addAll(
                allAppsList.filter { !it.isSystemPackage })
        }
    }
    private fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
        return pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        val checkBoxAllApps = menu.findItem(R.id.app_bar_checkbox_all_apps).actionView as CheckBox
        val searchView = menu.findItem(R.id.app_bar_search).actionView as SearchView

        searchView.maxWidth = android.R.attr.width
        searchView.queryHint = "Search app name or package"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                val appModelArrayList: ArrayList<AppModel> = ArrayList()

                for (i in appsToSearchList) {
                    if (i.name.lowercase(Locale.getDefault()).contains(
                            newText!!.lowercase(
                                Locale.getDefault()
                            )
                        )
                        ||
                        i.packages.lowercase(Locale.getDefault()).contains(
                            newText.lowercase(
                                Locale.getDefault()
                            )
                        )
                    ) {
                        appModelArrayList.add(i)
                    }
                }
                setRecyclerViewDataSet(appModelArrayList, needNotify = true)
                return true
            }
        })
        searchView.setOnSearchClickListener {
            checkBoxAllApps.visibility = View.GONE
        }
        searchView.setOnCloseListener {
            checkBoxAllApps.visibility = View.VISIBLE
            false
        }

        checkBoxAllApps.setOnCheckedChangeListener { _, checked ->
            setAppsToSearch(checked)
            setRecyclerViewDataSet(appsToSearchList, needNotify = true)
        }

        return super.onCreateOptionsMenu(menu)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun setRecyclerViewDataSet(dataList: ArrayList<AppModel>, needNotify: Boolean = false) {
        findViewById<TextView>(R.id.totalInstalledApp).text =
            getString(R.string.total_installed_apps, dataList.size)

        installedAppAdapter =
            AppAdapter(this, dataList)

        recyclerView.adapter = installedAppAdapter
        if (needNotify) {
            installedAppAdapter.notifyDataSetChanged()
        }
    }
}

private data class AppModel(val name: String, val icon: Drawable, val packages: String, val isSystemPackage: Boolean)

private class AppAdapter(private val context: AllInstalledAppActivity, private var appModelList: ArrayList<AppModel>) :
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
        holder.appNameTxt.text = appModelList[position].name
        holder.appIcon.setImageDrawable(appModelList[position].icon)
        holder.appPackageNameTxt.text = appModelList[position].packages

        holder.itemView.setOnClickListener {
            val dialogListTitle = arrayOf("Set to open", "App Info")
            val builder: AlertDialog.Builder = AlertDialog.Builder(context)
            builder.setTitle("Choose Action")
                .setItems(
                    dialogListTitle
                ) { _, which ->
                    when (which) {
                        0 -> {
                            val intent =
                                context.packageManager.getLaunchIntentForPackage(appModelList[position].packages)
                            if (intent != null) {
                                context.intent.extras?.getString("action_type_preference_key")
                                    .also { preferenceKey ->
                                        if (preferenceKey == null) return@also

                                        val gson = Gson()
                                        val sharedPreferences =
                                            PreferenceManager.getDefaultSharedPreferences(context)

                                        val actionData: ActionData = sharedPreferences.getString(preferenceKey, null)
                                            .let { jsonStr ->
                                                if (jsonStr ==null) return@let ActionData(lastModifiedTimeMs = System.currentTimeMillis())
                                                else return@let gson.fromJson(jsonStr, ActionData::class.java)
                                            }

                                        actionData.apply {
                                            type = ActionExecuteType.APP
                                            packageId = appModelList[position].packages
                                            lastModifiedTimeMs = System.currentTimeMillis()
                                        }

                                        sharedPreferences.edit {
                                            putString(preferenceKey, gson.toJson(actionData))
                                        }

                                        context.finish()
                                    }
                            }else{
                                Toast.makeText(context,"System app is not open for any reason.",Toast.LENGTH_LONG).show()
                            }
                        }
                        1 -> {
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            intent.data =
                                Uri.parse("package:${appModelList[position].packages}")
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