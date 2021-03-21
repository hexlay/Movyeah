package hexlay.movyeah.activities

import android.os.Bundle
import android.util.SparseArray
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.assent.Permission
import com.afollestad.assent.runWithPermissions
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.jude.swipbackhelper.SwipeBackHelper
import hexlay.movyeah.R
import hexlay.movyeah.adapters.view_holders.CastViewHolder
import hexlay.movyeah.api.database.view_models.DbDownloadMovieViewModel
import hexlay.movyeah.api.database.view_models.DbEpisodeViewModel
import hexlay.movyeah.api.database.view_models.DbMovieViewModel
import hexlay.movyeah.api.helpers.isNetworkAvailable
import hexlay.movyeah.api.models.DownloadMovie
import hexlay.movyeah.api.models.Movie
import hexlay.movyeah.api.models.attributes.Actor
import hexlay.movyeah.api.models.attributes.Subtitle
import hexlay.movyeah.api.models.attributes.show.Episode
import hexlay.movyeah.api.models.attributes.show.EpisodeCache
import hexlay.movyeah.api.models.attributes.show.EpisodeFileData
import hexlay.movyeah.api.network.view_models.WatchViewModel
import hexlay.movyeah.helpers.*
import hexlay.movyeah.models.events.ChooseEpisodeEvent
import kotlinx.android.synthetic.main.activity_movie.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.share
import org.jetbrains.anko.toast
import java.util.*
import kotlin.collections.HashMap

class MovieActivity : AppCompatActivity() {

    private val watchViewModel by viewModels<WatchViewModel>()
    private val dbMovie by viewModels<DbMovieViewModel>()
    private val dbDownloadMovie by viewModels<DbDownloadMovieViewModel>()
    private val dbEpisodes by viewModels<DbEpisodeViewModel>()

    // Movie attributes
    private lateinit var movie: Movie
    private var qualityKey = "NONE"
    private var languageKey = "NONE"
    private var subtitleKey = "NONE"
    private var currentSeason = 1
    private var currentEpisode = 1
    private var genres = ""

    private var fileData: Map<String, List<EpisodeFileData>> = HashMap()
    private var subtitleData: Map<String, List<Subtitle>> = HashMap()
    private var tvShowSeasons: SparseArray<List<Episode>> = SparseArray()

    private var isNetworkAvailable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie)
        initDarkMode()
        initStarterData()
        makeFullscreen()
        initLayout()
        initFavorite()
        initLayoutActions()
        loadData()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        SwipeBackHelper.onPostCreate(this)
    }

    private fun initStarterData() {
        EventBus.getDefault().register(this)
        SwipeBackHelper.onCreate(this)
        isNetworkAvailable = isNetworkAvailable()
        movie = intent!!.extras!!.getParcelable("movie")!!
    }

    private fun initLayout() {
        toolbar.setPadding(0, getStatusBarHeight(), 0, 0)
        toolbar.setSize(height = getStatusBarHeight() + getActionBarSize())
    }

    private fun initFavorite() {
        dbMovie.getMovie(movie.id)?.observe(this, {
            if (it == null) {
                button_favorite.showAvdSecond()
                button_favorite.setOnClickListener {
                    dbMovie.insertMovie(movie)
                    button_favorite.morph()
                }
            } else {
                button_favorite.showAvdFirst()
                button_favorite.setOnClickListener {
                    dbMovie.deleteMovie(movie)
                    button_favorite.morph()
                }
            }
            button_favorite.isVisible = true
        })
    }

    private fun loadData() {
        loadIndependentData()
        if (!isNetworkAvailable) {
            showMovieError(R.string.full_error_movie)
            button_watch.hide()
            button_episodes.hide()
        } else {
            if (movie.isTvShow) {
                watchViewModel.fetchSingleMovie(movie.adjaraId).observeOnce(this, { movieExtend ->
                    if (movieExtend?.seasons != null) {
                        movie.seasons = movieExtend.seasons
                        watchViewModel.fetchTvShowEpisodes(movie.id, movieExtend.seasons!!.data.size)
                                .observeOnce(this, { seasons ->
                                    if (seasons != null && seasons.isNotEmpty()) {
                                        tvShowSeasons = seasons
                                        setupTvShow()
                                    } else {
                                        showMovieError(R.string.full_error_show)
                                    }
                                })
                    } else {
                        showMovieError(R.string.full_error_show)
                    }
                })
                button_watch.hide()
            } else {
                button_episodes.hide()
                watchViewModel.fetchMovieFileData(movie.id).observeOnce(this, { episode ->
                    if (episode != null) {
                        fileData = episode.files.map { it.lang!! to it.files }.toMap()
                        subtitleData = episode.files.map { it.lang!! to it.subtitles }.toMap()
                        setupMovie()
                    } else {
                        showMovieError(R.string.full_error_movie)
                    }
                })
            }
        }
    }

    private fun showMovieError(textId: Int) {
        MaterialDialog(this).show {
            message(textId)
            cancelable(false)
            positiveButton(R.string.full_error_button) {
                finish()
            }
        }
    }

    private fun loadIndependentData() {
        watchViewModel.fetchMovieActors(movie.adjaraId).observeOnce(this, { cast ->
            if (cast != null) {
                setupCast(cast)
            } else {
                cast_holder.isGone = true
            }
        })
        genres = movie.getGenresString()
        setupMovieInformation()
    }

    private fun setupLanguageAndQuality() {
        languageKey = when {
            fileData.containsKey(PreferenceHelper.lang) -> {
                PreferenceHelper.lang
            }
            fileData.containsKey("GEO") -> {
                "GEO"
            }
            fileData.containsKey("ENG") -> {
                "ENG"
            }
            else -> {
                fileData.keys.first()
            }
        }
        val qualities = fileData[languageKey]?.map { it.quality }
        qualityKey = when {
            qualities?.contains(PreferenceHelper.quality)!! -> {
                PreferenceHelper.quality
            }
            qualities.contains("HIGH") -> {
                "HIGH"
            }
            else -> {
                fileData[languageKey]?.first()?.quality!!
            }
        }
        button_lang.isEnabled = true
        button_quality.isEnabled = true
    }

    private fun setupMovie() {
        setupLanguageAndQuality()
        button_lang.text = languageKey.translateLanguage(this)
        button_quality.text = qualityKey.translateQuality(this)
        if (isNetworkAvailable) {
            button_download.isVisible = true
        }
        setupMovieSubtitles()
    }

    private fun setupTvShow() {
        dbEpisodes.getEpisode(movie.id)?.observeOnce(this, {
            if (it != null) {
                currentSeason = it.season
                setupTvShowEpisode(it.episode)
                //episode_holder.currentItem = currentSeason - 1
            } else {
                setupTvShowEpisode(0)
            }
        })
        //setupTvShowSeasons()
    }

    private fun setupTvShowEpisode(episode: Int) {
        if (tvShowSeasons.isEmpty() || tvShowSeasons[currentSeason].isEmpty()) {
            showMovieError(R.string.full_error_show)
            return
        }
        currentEpisode = episode
        dbEpisodes.insertEpisode(EpisodeCache(movie.id, episode, currentSeason))
        fileData = tvShowSeasons[currentSeason][episode].files.map { it.lang!! to it.files }.toMap()
        subtitleData = tvShowSeasons[currentSeason][episode].files.map { it.lang!! to it.subtitles }.toMap()
        setupLanguageAndQuality()
        button_lang.text = languageKey.translateLanguage(this)
        button_quality.text = qualityKey.translateQuality(this)
        setupMovieSubtitles()
    }

    /*private fun setupTvShowSeasons() {
        episode_holder.adapter = SeasonPageAdapter(supportFragmentManager, movie, tvShowSeasons)
        season_tabs.setupWithViewPager(episode_holder)
        episode_holder.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                currentSeason = position + 1
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        episodes.isEnabled = true
    }*/

    private fun setupMovieSubtitles() {
        if (subtitleData[languageKey]?.isNotEmpty()!!) {
            val list = subtitleData[languageKey]?.map { it.lang?.toUpperCase(Locale.ENGLISH)!! }
            subtitleKey = if (subtitleKey == "NONE")
                if (list?.contains(languageKey)!!) {
                    list[list.indexOf(languageKey)]
                } else {
                    list.first()
                }
            else
                subtitleKey
            button_subtitles.text = subtitleKey.translateLanguage(this)
            button_subtitles.setOnClickListener {
                MaterialDialog(this).show {
                    title(R.string.full_chsubtitles)
                    listItems(items = list) { _, _, text ->
                        subtitleKey = text.toString()
                        setSubtitlesText(subtitleKey)
                    }
                }
            }
            button_subtitles.isGone = false
            button_subtitles.isEnabled = true
        } else {
            subtitleKey = "NONE"
            button_subtitles.isGone = true
        }
    }

    private fun setupCast(actors: List<Actor>) {
        if (actors.isNotEmpty()) {
            cast_holder.setup {
                withDataSource(dataSourceOf(actors))
                withLayoutManager(LinearLayoutManager(this@MovieActivity, RecyclerView.HORIZONTAL, false))
                withItem<Actor, CastViewHolder>(R.layout.list_items_cast) {
                    onBind(::CastViewHolder) { _, item ->
                        name.text = item.getTitle()
                        item.poster?.let { image.setUrl(it) }
                    }
                    onClick {
                        startActivity(intentFor<ActorActivity>("actor" to item))
                    }
                }
            }
        } else {
            cast_holder.isGone = true
        }
    }

    private fun setupMovieInformation() {
        movie_title.text = movie.getTitle()
        /*description_date.text = getString(R.string.news_year).format(movie.year)
        description_imdb.text = getString(R.string.news_imdb).format(movie.getRating("imdb"))
        description_duration.text = getString(R.string.news_duration).format(movie.duration.toHumanDuration())
        description_imdb.setOnClickListener {
            movie.imdbUrl?.let { it1 -> browse(it1, true) }
        }
        watch_text.text = getString(R.string.news_watch).format(movie.getWatchString())*/
        movie_categories.text = if (genres.isEmpty())
            getString(R.string.full_cats_not_found)
        else
            genres
        movie_title.isSelected = true
        movie_categories.isSelected = true
        /*description_duration.isSelected = true
        description_imdb.isSelected = true
        watch_text.isSelected = true*/
        movie.getTruePoster()?.let { poster.setUrl(it) }
        movie.getCover()?.let { cover.setUrl(it) }
        movie_text.text = movie.getDescription()
    }

    private fun initLayoutActions() {
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        button_share.setOnClickListener {
            share("https://www.adjaranet.com/movies/${movie.getRealId()}")
        }
        button_download.setOnClickListener {
            val dialogItems = ArrayList<String>()
            val dataDownload = ArrayList<DownloadMovie>()
            for ((langKey, langValue) in fileData) {
                val qualities = langValue.map { it.quality to it }.toMap()
                for ((qualKey, qualValue) in qualities) {
                    val downloadTitle = "${movie.id}_${qualKey}_${langKey}"
                    if (!downloadExists(downloadTitle)) {
                        val download = DownloadMovie(
                                currentEpisode = currentEpisode,
                                currentSeason = currentSeason,
                                language = langKey,
                                quality = qualKey,
                                movie = movie,
                                url = qualValue.src,
                                identifier = downloadTitle
                        )
                        dataDownload.add(download)
                        val rQuality = qualKey?.translateQuality(this)
                        val rLang = langKey.translateLanguage(this)
                        dialogItems.add("$rQuality (${rLang})")
                    }
                }
            }
            if (dialogItems.isNotEmpty()) {
                MaterialDialog(this).show {
                    title(text = movie.getTitle())
                    listItems(items = dialogItems) { _, index, _ ->
                        runWithPermissions(Permission.WRITE_EXTERNAL_STORAGE) {
                            val download = dataDownload[index]
                            val downloadId = downloadMovie(download.url!!, download.identifier)
                            download.downloadId = downloadId
                            dbDownloadMovie.insertMovie(download)
                            showAlert(text = getString(R.string.download_start))
                        }
                    }
                }
            }
        }
        button_quality.setOnClickListener {
            MaterialDialog(this).show {
                title(R.string.full_chquality)
                listItems(items = fileData[languageKey]?.map { it.quality!! }) { _, _, text ->
                    qualityKey = text.toString()
                    setQualityText(qualityKey)
                    setupMovieSubtitles()
                }
            }
        }
        button_lang.setOnClickListener {
            MaterialDialog(this).show {
                title(R.string.full_chlang)
                listItems(items = fileData.keys.toList()) { _, _, text ->
                    languageKey = text.toString()
                    setLanguageText(languageKey)
                    setupMovieSubtitles()
                }
            }
        }
        button_watch.setOnClickListener {
            toast(generatePlayerUrl())
        }
    }

    private fun generatePlayerUrl(): String {
        return fileData[languageKey]?.first { it.quality == qualityKey }?.src.toString()
    }

    private fun generateSubtitleUrl(): String {
        return subtitleData[languageKey]?.first { it.lang == subtitleKey.toLowerCase(Locale.ENGLISH) }?.url.toString()
    }

    private fun setLanguageText(text: String) {
        button_lang.text = text.translateLanguage(this)
    }

    private fun setQualityText(text: String) {
        button_quality.text = text.translateQuality(this)
    }

    private fun setSubtitlesText(text: String) {
        button_subtitles.text = text.translateLanguage(this)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(R.style.SlideRightAnimation, R.style.SlideRightAnimation)
    }

    @Subscribe
    fun listenEpisodeChange(event: ChooseEpisodeEvent) {
        setupTvShowEpisode(event.position)
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        SwipeBackHelper.onDestroy(this)
    }
}