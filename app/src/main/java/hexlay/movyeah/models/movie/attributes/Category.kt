package hexlay.movyeah.models.movie.attributes

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity
data class Category(
        @PrimaryKey @SerializedName("id") var id: Int = 0,
        @SerializedName("primaryName") var primaryName: String?
) : Parcelable