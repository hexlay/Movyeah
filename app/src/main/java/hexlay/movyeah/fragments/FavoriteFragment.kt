package hexlay.movyeah.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.afollestad.recyclical.datasource.emptyDataSource
import com.afollestad.recyclical.setup
import com.afollestad.recyclical.withItem
import hexlay.movyeah.R
import hexlay.movyeah.adapters.view_holders.MovieViewHolder
import hexlay.movyeah.database.view_models.DbMovieViewModel
import hexlay.movyeah.helpers.dpOf
import hexlay.movyeah.helpers.getActionBarSize
import hexlay.movyeah.helpers.getStatusBarHeight
import hexlay.movyeah.models.movie.Movie
import kotlinx.android.synthetic.main.fragment_movies.*

class FavoriteFragment : Fragment() {

    private val dbMovies by viewModels<DbMovieViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_movies, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
    }

    private fun initView() {
        val recyclerPaddingTop = getStatusBarHeight() + getActionBarSize() + dpOf(10)
        movies_holder.setPadding(0, recyclerPaddingTop, 0, getActionBarSize())
    }

    private fun init() {
        initView()
        initReloader()
        loadMovies()
    }

    private fun initReloader() {
        movies_reloader.setProgressViewOffset(false, 0, getStatusBarHeight() + getActionBarSize() + dpOf(5))
        movies_reloader.setOnRefreshListener {
            loadMovies()
            movies_reloader.isRefreshing = false
        }
    }

    private fun loadMovies() {
        val source = emptyDataSource()
        loading_movies.isGone = false
        dbMovies.getMovies()?.observe(viewLifecycleOwner, Observer {
            loading_movies.isGone = true
            source.clear()
            if (it.isNotEmpty()) {
                source.addAll(it)
                warning_holder.isVisible = false
            } else {
                warning_holder.text = getString(R.string.loading_favs_fail)
                warning_holder.isVisible = true
            }
        })
        movies_holder.setup {
            withDataSource(source)
            withLayoutManager(GridLayoutManager(requireContext(), 2))
            withItem<Movie, MovieViewHolder>(R.layout.list_items_extended) {
                onBind(::MovieViewHolder) { _, item ->
                    this.bind(item, requireActivity())
                }
            }
        }
    }

}
