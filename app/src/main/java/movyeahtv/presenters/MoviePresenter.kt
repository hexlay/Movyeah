package movyeahtv.presenters

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import hexlay.movyeah.R
import hexlay.movyeah.helpers.setUrl
import hexlay.movyeah.models.movie.Movie
import movyeahtv.activities.TvWatchActivity
import org.jetbrains.anko.startActivity


class MoviePresenter(private val context: Context) : Presenter() {

    inner class ViewHolder(view: View) : Presenter.ViewHolder(view) {

        val imageCardView = view as ImageCardView

    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val imageCardView = ImageCardView(context)
        imageCardView.isFocusable = true
        imageCardView.isFocusableInTouchMode = true
        imageCardView.setMainImageDimensions(300, 400)
        imageCardView.setBackgroundColor(ContextCompat.getColor(context, R.color.default_background))
        return ViewHolder(imageCardView)
    }

    override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any) {
        val movie = item as Movie
        val holder = viewHolder as ViewHolder
        holder.imageCardView.titleText = movie.getTitle()
        holder.imageCardView.contentText = "IMDB: ${movie.getRating("imdb").score}, წელი: ${movie.year}"
        movie.getTruePoster()?.let { holder.imageCardView.mainImageView.setUrl(it) }
        holder.imageCardView.setOnClickListener {
            context.startActivity<TvWatchActivity>("movie" to movie)
        }
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder?) {}

    override fun onViewAttachedToWindow(viewHolder: Presenter.ViewHolder?) {}

}