package hexlay.movyeah.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import hexlay.movyeah.R
import hexlay.movyeah.adapters.view_holders.LibraryViewHolder
import hexlay.movyeah.models.Library
import kotlinx.android.synthetic.main.activity_about.*
import org.jetbrains.anko.browse

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        initToolbar()
        initResources()
        initVersion()
    }

    private fun initToolbar() {
        toolbar.title = ""
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_w)
        toolbar.setNavigationOnClickListener {
            finish()
        }
        version_holder.setOnClickListener {
            browse("https://github.com/hexlay/MovYeah", true)
        }
        author_holder.setOnClickListener {
            browse("https://github.com/hexlay", true)
        }
    }

    private fun initVersion() {
        val gradleVersionName = packageManager.getPackageInfo(packageName, 0).versionName
        version.text = gradleVersionName
    }

    private fun initResources() {
        val libraries = dataSourceOf(
                Library("Adjaranet",
                        "Adjaranet's team",
                        "http://net.adjara.com/"),
                Library("Retrofit",
                        "Square",
                        "https://github.com/square/retrofit"),
                Library("OkHttp",
                        "Square",
                        "https://github.com/square/okhttp"),
                Library("Glide",
                        "Bumptech",
                        "https://github.com/bumptech/glide"),
                Library("Material Dialogs",
                        "Aidan Follestad",
                        "https://github.com/afollestad/material-dialogs"),
                Library("Recyclical",
                        "Aidan Follestad",
                        "https://github.com/afollestad/recyclical"),
                Library("Assent",
                        "Aidan Follestad",
                        "https://github.com/afollestad/assent"),
                Library("ExoPlayer 2",
                        "Google",
                        "https://github.com/google/ExoPlayer"),
                Library("CircleImageView",
                        "Henning Dodenhof",
                        "https://github.com/hdodenhof/CircleImageView"),
                Library("Android Room",
                        "Google",
                        "https://developer.android.com/topic/libraries/architecture/room"),
                Library("EventBus",
                        "Markus Junginger",
                        "https://github.com/greenrobot/EventBus")
        )
        resource_holder.setup {
            withDataSource(libraries)
            withItem<Library, LibraryViewHolder>(R.layout.list_about) {
                onBind(::LibraryViewHolder) { _, item ->
                    resource.text = item.libraryName
                    summary.text = item.libraryAuthor
                }
                onClick {
                    browse(item.libraryLink, true)
                }
            }
        }
    }

}
