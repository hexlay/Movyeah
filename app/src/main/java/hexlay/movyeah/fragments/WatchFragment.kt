package hexlay.movyeah.fragments

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Rational
import android.util.SparseArray
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.transition.TransitionManager
import androidx.viewpager.widget.ViewPager
import com.afollestad.assent.Permission
import com.afollestad.assent.runWithPermissions
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.afollestad.recyclical.datasource.dataSourceOf
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.SingleSampleMediaSource
import com.google.android.exoplayer2.source.TrackGroupArray
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionArray
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.Util
import hexlay.movyeah.R
import hexlay.movyeah.activities.ActorActivity
import hexlay.movyeah.activities.CommentsActivity
import hexlay.movyeah.activities.base.AbsWatchModeActivity
import hexlay.movyeah.adapters.SeasonPageAdapter
import hexlay.movyeah.adapters.view_holders.CastViewHolder
import hexlay.movyeah.api.view_models.WatchViewModel
import hexlay.movyeah.database.view_models.DbEpisodeViewModel
import hexlay.movyeah.database.view_models.DbMovieViewModel
import hexlay.movyeah.helpers.*
import hexlay.movyeah.models.events.ChooseEpisodeEvent
import hexlay.movyeah.models.movie.Movie
import hexlay.movyeah.models.movie.attributes.Actor
import hexlay.movyeah.models.movie.attributes.Subtitle
import hexlay.movyeah.models.movie.attributes.show.Episode
import hexlay.movyeah.models.movie.attributes.show.EpisodeCache
import hexlay.movyeah.models.movie.attributes.show.EpisodeFileData
import kotlinx.android.synthetic.main.exo_playback_control_view.*
import kotlinx.android.synthetic.main.fragment_watch.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.noAnimation
import org.jetbrains.anko.support.v4.browse
import org.jetbrains.anko.support.v4.intentFor
import org.jetbrains.anko.support.v4.share
import smartdevelop.ir.eram.showcaseviewlib.GuideView
import smartdevelop.ir.eram.showcaseviewlib.config.DismissType
import smartdevelop.ir.eram.showcaseviewlib.config.Gravity
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

class WatchFragment : Fragment() {

    private lateinit var reference: WeakReference<AbsWatchModeActivity>
    private lateinit var preferenceHelper: PreferenceHelper

    private val watchViewModel by viewModels<WatchViewModel>()
    private val dbMovie by viewModels<DbMovieViewModel>()
    private val dbEpisodes by viewModels<DbEpisodeViewModel>()

    // Movie attributes
    private lateinit var movie: Movie
    private lateinit var qualityKey: String
    private lateinit var languageKey: String
    private var subtitleKey = "NONE"
    private var currentSeason = 1
    private var genres = ""

    private lateinit var fileData: Map<String, List<EpisodeFileData>>
    private lateinit var subtitleData: Map<String, List<Subtitle>>
    private lateinit var tvShowSeasons: SparseArray<List<Episode>>

    private var pipBuild: PictureInPictureParams.Builder? = null
    var isInPIP = false

    private var playerInitialLaunch = false
    private var stillInApp = false
    var superExit = false

    // ExoPlayer
    var isFullscreen = false
    private var canResume = false
    private var exoPlayer: ExoPlayer? = null
    private var controlsShown = true
    private var isPreparing = true
    private var isSeeking = false
    private var isPlaying = false
    private var isCritical = false
    private var onPause = false
    private var seekSaved: Long = 0
    private var seekSecForward: Long = 0
    private var seekSecBackward: Long = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        reference = WeakReference(activity as AbsWatchModeActivity)
        preferenceHelper = PreferenceHelper(reference.get()!!)
        EventBus.getDefault().register(this)
        return inflater.inflate(R.layout.fragment_watch, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (Constants.isAndroidO) {
            pipBuild = PictureInPictureParams.Builder()
        }
        if (savedInstanceState != null && savedInstanceState.containsKey("movie")) {
            movie = savedInstanceState.getParcelable("movie")!!
        }
        safeCheckMovie()
        initLayout()
        initFavorite()
        initPlayer()
        initLayoutActions()
        initActivityProperties()
    }

    override fun onCreateAnimation(transit: Int, enter: Boolean, nextAnim: Int): Animation? {
        return if (nextAnim > 0) {
            val animation = AnimationUtils.loadAnimation(activity, nextAnim)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    loadData(movie.id)
                }

                override fun onAnimationStart(animation: Animation?) {}
            })
            animation
        } else {
            super.onCreateAnimation(transit, enter, nextAnim)
        }
    }

    private fun initActivityProperties() {
        if (!isInNightMode())
            removeLightStatusBar()
        getWindow()?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (preferenceHelper.maxBrightness) {
            val layoutParams = getWindow()?.attributes
            layoutParams?.screenBrightness = 100 / 100.0f
            getWindow()?.attributes = layoutParams
        }
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, DefaultTrackSelector())
        exo_fullscreen.isEnabled = false
        player_src.useController = true
        player_src.requestFocus()
        player_src.player = exoPlayer
        player_src.controllerShowTimeoutMs = 2000
        player_src.setControllerVisibilityListener { visibility ->
            controlsShown = visibility == View.VISIBLE
            toolbar.fade(if (visibility == View.VISIBLE) 1 else 0, 150)
        }
        if (!preferenceHelper.watchHint) {
            GuideView.Builder(context)
                    .setContentText("სრულ ეკრანზე ჩვენებისას შეგიძლიათ ორჯერ მარჯვნივ/მარცხნივ დაჭერით გადაახვიოთ ფილმი.\nგადახვევის დრო შეგიძლიათ შეცვალოთ პარამეტრებში")
                    .setGravity(Gravity.auto)
                    .setDismissType(DismissType.anywhere)
                    .setTargetView(player_src)
                    .setGuideListener {
                        preferenceHelper.watchHint = true
                        GuideView.Builder(context)
                                .setContentText("დაჭერისას შეგიძლიათ ნახოთ ფილმის/სერიალის IMDB გვერდი")
                                .setGravity(Gravity.auto)
                                .setDismissType(DismissType.anywhere)
                                .setTargetView(description_imdb)
                                .build()
                                .show()
                    }
                    .build()
                    .show()
        }
        exoPlayer!!.playWhenReady = preferenceHelper.autoStart
        exoPlayer!!.addListener(object : Player.EventListener {

            override fun onTimelineChanged(timeline: Timeline?, manifest: Any?, reason: Int) {}

            override fun onTracksChanged(trackGroups: TrackGroupArray?, trackSelections: TrackSelectionArray?) {}

            override fun onLoadingChanged(isLoading: Boolean) {}

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) {
                    exo_loading.isVisible = true
                    action_holder.isVisible = false
                } else {
                    exo_loading.isVisible = false
                    action_holder.isVisible = true
                    seekSecForward = 0
                    seekSecBackward = 0
                }
                if (isPreparing && playbackState == Player.STATE_READY) {
                    requestSensorForever()
                    button_lang.isEnabled = true
                    button_quality.isEnabled = true
                    if (subtitleData.isNotEmpty())
                        button_subtitles.isEnabled = true
                    button_comment.isEnabled = true
                    exo_fullscreen.isEnabled = true
                    isPreparing = false
                    enableDoubleTapSeek()
                }
                if (playWhenReady && playbackState == Player.STATE_READY) {
                    if (isInPIP)
                        setPipActions(Constants.CONTROL_TYPE_PAUSE)
                    isPlaying = true
                    if (!playerInitialLaunch)
                        playerInitialLaunch = true
                    exoPlayer!!.playWhenReady = true
                    //requestAudioFocus()
                    if (isSeeking) {
                        exoPlayer!!.seekTo(seekSaved)
                        isSeeking = false
                        seekSaved = 0
                    }
                    if (!isInPIP && onPause)
                        pausePlayer()
                } else {
                    //abandonAudioFocus()
                    if (isInPIP)
                        setPipActions(Constants.CONTROL_TYPE_PLAY)
                    isPlaying = false
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {}

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {}

            override fun onPlayerError(error: ExoPlaybackException) {
                isCritical = true
                action_error.isVisible = true
                action_holder.isVisible = false
                exo_loading.isVisible = false
                button_lang.isEnabled = false
                button_quality.isEnabled = false
                button_subtitles.isEnabled = false
                button_comment.isEnabled = false
            }

            override fun onPositionDiscontinuity(reason: Int) {}

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {}

            override fun onSeekProcessed() {}
        })
        exo_fullscreen.setOnClickListener {
            if (isFullscreen) {
                setFullscreenDrawable(R.drawable.action_fs_enter)
                modePortrait()
                requestPortrait()
            } else {
                setFullscreenDrawable(R.drawable.action_fs_exit)
                modeLandscape()
                requestLandscape()
            }
        }
    }

    private fun enableDoubleTapSeek() {
        player_src.setOnTouchListener(object : View.OnTouchListener {

            private val screenWidth = getScreenWidth()

            private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(motionEvent: MotionEvent): Boolean {
                    player_src.showController()
                    if (motionEvent.x > screenWidth * 0.5f) {
                        seekSecForward += (preferenceHelper.seek / 1000).toLong()
                        view_forward.text = getString(R.string.settings_main_seek_seconds).format(seekSecForward.toString())
                        view_forward.fade(1, 150)
                        exoPlayer!!.seekTo(exoPlayer!!.currentPosition + preferenceHelper.seek)
                        Handler().postDelayed({ view_forward.fade(0, 150) }, 500)
                    } else {
                        seekSecBackward += (preferenceHelper.seek / 1000).toLong()
                        view_backward.text = getString(R.string.settings_main_seek_seconds).format(seekSecBackward.toString())
                        view_backward.fade(1, 150)
                        exoPlayer!!.seekTo(exoPlayer!!.currentPosition - preferenceHelper.seek)
                        Handler().postDelayed({ view_backward.fade(0, 150) }, 500)
                    }
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    controlsShown = if (controlsShown) {
                        player_src.hideController()
                        false
                    } else {
                        player_src.showController()
                        true
                    }
                    return true
                }
            })

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(motionEvent)
                return true
            }
        })
    }

    private fun loadData(id: Int) {
        if (movie.isTvShow) {
            watchViewModel.fetchMovie(movie.adjaraId)
            watchViewModel.movie.observeOnce(viewLifecycleOwner, Observer { movieExtend ->
                if (movieExtend.seasons != null) {
                    watchViewModel.fetchTvShowEpisodes(movie.id, movieExtend.seasons!!.data.size)
                    watchViewModel.tvShowEpisodes.observeOnce(viewLifecycleOwner, Observer { seasons ->
                        if (seasons.isNotEmpty()) {
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
        } else {
            navigation.menu.removeItem(R.id.episodes)
            watchViewModel.fetchMovieFileData(id)
            watchViewModel.movieData.observeOnce(viewLifecycleOwner, Observer { episode ->
                if (episode != null) {
                    fileData = episode.files.map { it.lang!! to it.files }.toMap()
                    subtitleData = episode.files.map { it.lang!! to it.subtitles }.toMap()
                    setupMovie()
                } else {
                    showMovieError(R.string.full_error_movie)
                }
            })
        }
        loadIndependentData()
    }

    private fun showMovieError(textId: Int) {
        MaterialDialog(requireContext()).show {
            message(textId)
            cancelable(false)
            positiveButton(R.string.full_error_button) {
                this@WatchFragment.onBackPressed()
            }
        }
    }

    private fun loadIndependentData() {
        watchViewModel.fetchActors(movie.adjaraId)
        watchViewModel.actorList.observeOnce(viewLifecycleOwner, Observer {
            setupCast(it)
        })
        genres = movie.genres?.data?.map { it.primaryName }?.toCommaList().toString()
        setupMovieInformation()
    }

    private fun setupMovie() {
        languageKey = when {
            fileData.containsKey(preferenceHelper.lang) -> {
                preferenceHelper.lang
            }
            fileData.containsKey("ENG") -> {
                "ENG"
            }
            else -> {
                fileData.keys.first()
            }
        }
        qualityKey = if (fileData[languageKey]?.map { it.quality }?.contains(preferenceHelper.quality)!!) {
            preferenceHelper.quality
        } else {
            fileData[languageKey]?.get(0)?.quality!!
        }
        button_lang.text = languageKey.translateLanguage(requireContext())
        button_quality.text = qualityKey.translateQuality(requireContext())
        button_download.isVisible = true
        if (isInLandscape())
            modeLandscape()
        setupMovieSubtitles()
        setupSource()
    }

    private fun setupTvShow() {
        if (isInLandscape())
            modeLandscape()
        dbEpisodes.getEpisode(movie.id)?.observeOnce(viewLifecycleOwner, Observer {
            if (it != null) {
                currentSeason = it.season
                setupTvShowEpisode(it.episode)
                episode_holder.currentItem = currentSeason - 1
            } else {
                setupTvShowEpisode(0)
            }
        })
        setupTvShowSeasons()
    }

    private fun setupTvShowEpisode(episode: Int) {
        dbEpisodes.insertEpisode(EpisodeCache(movie.id, episode, currentSeason))
        fileData = tvShowSeasons[currentSeason][episode].files.map { it.lang!! to it.files }.toMap()
        subtitleData = tvShowSeasons[currentSeason][episode].files.map { it.lang!! to it.subtitles }.toMap()
        languageKey = when {
            fileData.containsKey(preferenceHelper.lang) -> {
                preferenceHelper.lang
            }
            fileData.containsKey("ENG") -> {
                "ENG"
            }
            else -> {
                fileData.keys.first()
            }
        }
        qualityKey = if (fileData[languageKey]?.map { it.quality }?.contains(preferenceHelper.quality)!!) {
            preferenceHelper.quality
        } else {
            fileData[languageKey]?.get(0)?.quality!!
        }
        button_lang.text = languageKey.translateLanguage(requireContext())
        button_quality.text = qualityKey.translateQuality(requireContext())
        button_download.isVisible = true
        setupMovieSubtitles()
        setupSource()
    }

    private fun setupTvShowSeasons() {
        episode_holder.adapter = SeasonPageAdapter(childFragmentManager, movie.id, tvShowSeasons)
        season_tabs.setupWithViewPager(episode_holder)
        episode_holder.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                currentSeason = position + 1
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        navigation.menu.findItem(R.id.episodes).isEnabled = true
    }

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
            button_subtitles.text = subtitleKey.translateLanguage(requireContext())
            button_subtitles.setOnClickListener {
                MaterialDialog(requireContext()).show {
                    title(R.string.full_chsubtitles)
                    listItems(items = list) { _, _, text ->
                        isSeeking = exoPlayer != null
                        subtitleKey = text.toString()
                        if (isSeeking)
                            seekSaved = exoPlayer!!.currentPosition
                        setSubtitlesText(subtitleKey)
                        restartPlayer()
                    }
                }
            }
            button_subtitles.isVisible = true
        } else {
            subtitleKey = "NONE"
            button_subtitles.isVisible = false
        }
    }

    private fun setupCast(actors: List<Actor>) {
        if (actors.isNotEmpty()) {
            navigation.menu.findItem(R.id.cast).isEnabled = true
            cast_holder.setup {
                withDataSource(dataSourceOf(actors))
                withLayoutManager(GridLayoutManager(context, 3))
                withItem<Actor, CastViewHolder>(R.layout.list_items_cast) {
                    onBind(::CastViewHolder) { _, item ->
                        name.text = item.getTitle()
                        item.poster?.let { image.setUrl(it) }
                    }
                    onClick {
                        stillInApp = true
                        startActivity(intentFor<ActorActivity>("actor" to item))
                    }
                }
            }
        } else {
            navigation.menu.removeItem(R.id.cast)
        }
    }

    private fun setupMovieInformation() {
        title_text.text = movie.getTitle()
        description_date.text = getString(R.string.news_year).format(movie.year)
        description_imdb.text = getString(R.string.news_imdb).format(movie.getRating("imdb").score)
        description_duration.text = getString(R.string.news_duration).format(movie.duration.toHumanDuration())
        description_imdb.setOnClickListener {
            movie.imdbUrl?.let { it1 -> browse(it1, true) }
        }
        watch_text.text = getString(R.string.news_watch).format(movie.watchCount)
        category_text.text = if (genres.isEmpty())
            getString(R.string.full_cats_not_found)
        else
            genres
        title_text.isSelected = true
        category_text.isSelected = true
        description_duration.isSelected = true
        description_imdb.isSelected = true
        watch_text.isSelected = true
        movie.getTruePoster()?.let { description_poster.setUrl(it) }
        description_text.text = movie.getDescription()
    }

    private fun initLayout() {
        toolbar.title = ""
        toolbar.setPadding(0, getStatusBarHeight(), 0, 0)
        toolbar.setSize(null, getStatusBarHeight() + getActionBarSize())
        TransitionManager.beginDelayedTransition(main_frame)
    }

    private fun initFavorite() {
        dbMovie.getMovie(movie.id)?.observe(viewLifecycleOwner, Observer {
            if (it == null) {
                button_favorite.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite_empty))
                button_favorite.setOnClickListener {
                    dbMovie.insertMovie(movie)
                }
            } else {
                button_favorite.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_favorite))
                button_favorite.setOnClickListener {
                    dbMovie.deleteMovie(movie)
                }
            }
            button_favorite.isVisible = true
        })
    }

    private fun generatePlayerUrl(): String {
        return fileData[languageKey]?.first { it.quality == qualityKey }?.src!!
    }

    private fun generateSubtitleUrl(): String {
        return subtitleData[languageKey]?.first { it.lang == subtitleKey.toLowerCase(Locale.ENGLISH) }?.url!!
    }

    private fun initLayoutActions() {
        toolbar.setNavigationIcon(R.drawable.ic_down)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        button_share.setOnClickListener {
            val id = if (movie.isTvShow) movie.adjaraId else movie.id
            share("https://www.adjaranet.com/movies/${id}")
            stillInApp = true
        }
        button_download.setOnClickListener {
            runWithPermissions(Permission.WRITE_EXTERNAL_STORAGE) {
                downloadMovie(generatePlayerUrl(), "${title_text.text} ($languageKey, $qualityKey)")
            }
        }
        button_quality.setOnClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.full_chquality)
                listItems(items = fileData[languageKey]?.map { it.quality!! }) { _, _, text ->
                    isSeeking = exoPlayer != null
                    qualityKey = text.toString()
                    if (isSeeking)
                        seekSaved = exoPlayer!!.currentPosition
                    setQualityText(qualityKey)
                    restartPlayer()
                }
            }
        }
        button_lang.setOnClickListener {
            MaterialDialog(requireContext()).show {
                title(R.string.full_chlang)
                listItems(items = fileData.keys.toList()) { _, _, text ->
                    isSeeking = exoPlayer != null
                    languageKey = text.toString()
                    if (isSeeking)
                        seekSaved = exoPlayer!!.currentPosition
                    setLanguageText(languageKey)
                    restartPlayer()
                }
            }
        }
        button_comment.setOnClickListener {
            stillInApp = true
            startActivity(intentFor<CommentsActivity>("id" to movie.id).noAnimation())
        }
        navigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.info -> {
                    sup_cast.isVisible = false
                    sup_episode.isVisible = false
                    sup_info.isVisible = true
                }
                R.id.episodes -> {
                    sup_cast.isVisible = false
                    sup_info.isVisible = false
                    sup_episode.isVisible = true
                }
                R.id.cast -> {
                    sup_info.isVisible = false
                    sup_episode.isVisible = false
                    sup_cast.isVisible = true
                }
            }
            true
        }
    }

    private fun setLanguageText(text: String) {
        button_lang.text = text.translateLanguage(requireContext())
    }

    private fun setQualityText(text: String) {
        button_quality.text = text.translateQuality(requireContext())
    }

    private fun setSubtitlesText(text: String) {
        button_subtitles.text = text.translateLanguage(requireContext())
    }

    private fun decideSubtitles() {
        if (subtitleData.isNotEmpty() && subtitleKey != "NONE")
            setupSourceSubtitles()
        else
            setupSource()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        when (newConfig.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> modePortrait()
            Configuration.ORIENTATION_LANDSCAPE -> modeLandscape()
        }
    }

    private fun setFullscreenDrawable(drawableId: Int) {
        exo_fullscreen.setImageDrawable(ContextCompat.getDrawable(requireContext(), drawableId))
    }

    private fun modeLandscape() {
        isFullscreen = true
        navigation.visibility = View.GONE
        val params = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
        player_holder.layoutParams = params
        layoutFullScreen()
        toolbar.navigationIcon = null
        setFullscreenDrawable(R.drawable.action_fs_exit)
        player_src.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT)
    }

    private fun modePortrait() {
        isFullscreen = false
        navigation.isVisible = true
        val params = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
        player_holder.layoutParams = params
        layoutStable()
        toolbar.setNavigationIcon(R.drawable.ic_down)
        setFullscreenDrawable(R.drawable.action_fs_enter)
        player_src.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
    }

    private fun layoutFullScreen() {
        getDecorView()?.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun layoutStable() {
        getDecorView()?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        getDecorView()?.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    private fun setupSource() {
        if (exoPlayer != null) {
            val httpDataSourceFactory = DefaultHttpDataSourceFactory(
                    Util.getUserAgent(context, "Movyeah"), null,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true
            )
            val dataSourceFactory = DefaultDataSourceFactory(context, null, httpDataSourceFactory)
            val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(Uri.parse(generatePlayerUrl()))
            exoPlayer?.prepare(mediaSource)
        }
    }

    private fun setupSourceSubtitles() {
        if (exoPlayer != null) {
            val httpDataSourceFactory = DefaultHttpDataSourceFactory(
                    Util.getUserAgent(context, "Movyeah"), null,
                    DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                    DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                    true
            )
            val dataSourceFactory = DefaultDataSourceFactory(context, null, httpDataSourceFactory)
            val textFormat = Format.createTextSampleFormat(null, MimeTypes.TEXT_VTT, null, Format.NO_VALUE, C.SELECTION_FLAG_DEFAULT, null, null, 0)
            val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(generateSubtitleUrl()), textFormat, C.TIME_UNSET)
            val mediaSource = ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(generatePlayerUrl()))
            exoPlayer?.prepare(MergingMediaSource(mediaSource, subtitleSource))
        }
    }

    @Subscribe
    fun listenEpisodeChange(event: ChooseEpisodeEvent) {
        setupTvShowEpisode(event.position)
    }

    private fun pausePlayer() {
        if (exoPlayer != null) {
            exoPlayer!!.playWhenReady = false
            exoPlayer!!.playbackState
        }
    }

    private fun startPlayer() {
        if (exoPlayer != null) {
            exoPlayer!!.playWhenReady = true
            exoPlayer!!.playbackState
        }
    }

    private fun restartPlayer() {
        if (exoPlayer != null) {
            if (subtitleData.isNotEmpty()) {
                setupMovieSubtitles()
            }
            pausePlayer()
            exoPlayer!!.stop()
            exoPlayer!!.seekTo(0)
            decideSubtitles()
            startPlayer()
            isPreparing = true
        }
    }

    private fun releaseVideo() {
        if (exoPlayer != null) {
            pausePlayer()
            exoPlayer!!.stop()
            exoPlayer!!.release()
            exoPlayer = null
        }
    }

    private fun releaseActivityProperties() {
        requestPortraitForever()
        getWindow()?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!isInNightMode())
            setLightStatusBar()
        if (preferenceHelper.maxBrightness) {
            val layoutParams = getWindow()?.attributes
            layoutParams?.screenBrightness = -1f
            getWindow()?.attributes = layoutParams
        }
    }

    override fun onDestroyView() {
        releaseVideo()
        releaseActivityProperties()
        EventBus.getDefault().unregister(this)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        stillInApp = false
        onPause = false
        if (!superExit && canResume && playerInitialLaunch) {
            if (!isPlaying)
                startPlayer()
            canResume = false
        }
    }

    override fun onPause() {
        if (!superExit) {
            onPause = true
            canResume = true
            if (isPlaying && !Constants.isAndroidO) {
                pausePlayer()
            } else if (isPlaying && preferenceHelper.pictureInPicture && !stillInApp && !isFullscreen) {
                enterPIP()
            } else if (isPlaying) {
                pausePlayer()
            }
        }
        super.onPause()
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun setPipActions(controlType: Int) {
        if (pipBuild != null) {
            val pipActions = ArrayList<RemoteAction>()
            val intent = PendingIntent.getBroadcast(context, controlType, Intent("media_control").putExtra("control_type", controlType), 0)
            val icon = Icon.createWithResource(context, if (controlType == 1) R.drawable.action_play else R.drawable.action_pause)
            val remoteAction = RemoteAction(icon, movie.getTitle(), movie.getTitle(), intent)
            pipActions.add(remoteAction)
            pipBuild!!.setActions(pipActions)
            reference.get()!!.setPictureInPictureParams(pipBuild!!.build())
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    fun enterPIP() {
        if (pipBuild != null) {
            player_src.hideController()
            player_src.useController = false
            toolbar.isVisible = false
            modeLandscape()
            val aspectRatio = Rational(player_src.width, player_src.height)
            pipBuild!!.setAspectRatio(aspectRatio)
            pipBuild!!.build()?.let { reference.get()?.enterPictureInPictureMode(it) }
            setPipActions(Constants.CONTROL_TYPE_PAUSE)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        safeCheckMovie()
        outState.putParcelable("movie", movie)
    }

    private fun safeCheckMovie() {
        if (!::movie.isInitialized)
            onDetach()
    }

    fun pipStartPlayer() {
        startPlayer()
        canResume = true
    }

    fun pipStopPlayer() {
        pausePlayer()
        canResume = false
    }

    fun pipRevert() {
        player_src.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
        player_src.useController = true
        player_src.showController()
        pausePlayer()
    }

    companion object {

        fun newInstance(movie: Movie): WatchFragment {
            val watchFragment = WatchFragment()
            watchFragment.movie = movie
            return watchFragment
        }

    }

}
