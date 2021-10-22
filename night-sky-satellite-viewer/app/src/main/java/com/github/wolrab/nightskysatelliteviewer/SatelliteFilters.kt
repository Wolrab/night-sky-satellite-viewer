interface Filter {
    fun filter(sat: Satellite): Boolean
}

object FavoritesFilter: Filter {
    private var enabled = false

    override fun filter(sat: Satellite): Boolean {
        var pass = true
        if (enabled) {
            pass = sat.isFavorite
        }
        return pass
    }

    // TODO: Mutex
    fun toggle() {
        enabled = !enabled
    }
}

object SearchFilter: Filter {
    // TODO: Mutex on setter
    var cmp: String? = null

    override fun filter(sat: Satellite): Boolean {
        var pass = true
        if (cmp != null && cmp != "") {
            pass = cmp.toString().commonPrefixWith(sat.name) == cmp.toString()
        }
        return pass
    }
}