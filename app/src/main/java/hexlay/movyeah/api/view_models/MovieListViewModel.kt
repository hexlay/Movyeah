package hexlay.movyeah.api.view_models

import androidx.lifecycle.MutableLiveData
import hexlay.movyeah.api.view_models.base.AbsAdjaraViewModel
import hexlay.movyeah.models.events.NetworkErrorEvent
import hexlay.movyeah.models.movie.Movie
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus

class MovieListViewModel : AbsAdjaraViewModel() {

    fun fetchMovies(
            page: Int = 1,
            filtersSort: String = "-upload_date",
            filtersType: String = "movie",
            filtersLanguage: String? = null,
            filtersGenres: String? = null,
            filtersYears: String
    ): MutableLiveData<List<Movie>> {
        val movies = MutableLiveData<List<Movie>>()
        scope.launch {
            try {
                movies.postValue(repository.getMainMovies(page, filtersType, filtersLanguage, filtersGenres, filtersYears, filtersSort))
            } catch (t: Throwable) {
                EventBus.getDefault().post(NetworkErrorEvent())
            }
        }
        return movies
    }

    fun fetchSearchMovie(
            page: Int = 1,
            keywords: String
    ): MutableLiveData<List<Movie>> {
        val movies = MutableLiveData<List<Movie>>()
        scope.launch {
            try {
                movies.postValue(repository.searchMovie(page, keywords))
            } catch (t: Throwable) {
                EventBus.getDefault().post(NetworkErrorEvent())
            }
        }
        return movies
    }

}