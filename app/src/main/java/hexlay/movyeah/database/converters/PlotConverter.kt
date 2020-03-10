package hexlay.movyeah.database.converters

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import hexlay.movyeah.models.movie.attributes.Plot
import hexlay.movyeah.models.movie.attributes.Rating

object PlotConverter {

    @TypeConverter
    @JvmStatic
    fun fromString(value: String): List<Plot> {
        val mapType = object : TypeToken<List<Plot>>() {}.type
        return Gson().fromJson(value, mapType)
    }

    @TypeConverter
    @JvmStatic
    fun fromStringMap(map: List<Plot>): String {
        return Gson().toJson(map)
    }

}