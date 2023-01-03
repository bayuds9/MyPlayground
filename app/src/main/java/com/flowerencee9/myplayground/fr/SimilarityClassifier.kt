package com.flowerencee9.myplayground.fr

interface SimilarityClassifier {
    class Recognition(
        /**
         * A unique identifier for what has been recognized. Specific to the class, not the instance of
         * the object.
         */
        private val id: String?,
        /** Display name for the recognition.  */
        private val title: String?, private val distance: Float?
    ) {
        var extra: Any? = null

        override fun toString(): String {
            var resultString = ""
            if (id != null) {
                resultString += "[$id] "
            }
            if (title != null) {
                resultString += "$title "
            }
            if (distance != null) {
                resultString += String.format("(%.1f%%) ", distance * 100.0f)
            }
            return resultString.trim { it <= ' ' }
        }
    }
}