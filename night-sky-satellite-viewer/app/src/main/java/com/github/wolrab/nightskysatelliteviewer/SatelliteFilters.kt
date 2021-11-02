package com.github.wolrab.nightskysatelliteviewer


interface Filter {
    fun filter(sat: Satellite): Boolean
}

class FavoritesFilter: Filter {
    var enabled = false

    override fun filter(sat: Satellite): Boolean {
        var pass = true
        if (enabled) {
            pass = sat.isFavorite
        }
        return pass
    }
}

class SearchFilter: Filter {
    // TODO: Mutex on setter
    var cmp: String? = null

    override fun filter(sat: Satellite): Boolean {
        var pass = true
        if (cmp != null && cmp != "") {
            pass = cmp!!.commonPrefixWith(sat.name) == cmp
        }
        return pass
    }
}