package com.glassstudy.filament

/** Curated realistic wine glass and four quad-mesh LOD densities. */
data class ModelSelection(val modelId:String,val lod:String) {
    init {
        require(modelId in ModelCatalog.ids) { "Unsupported model: $modelId" }
        require(lod in ModelCatalog.qualities) { "Unsupported quality: $lod" }
    }
}
object ModelCatalog {
    val ids=listOf("wine")
    val names=listOf("Бокал")
    val qualities=listOf("low","medium","high","ultra")
    val qualityNames=listOf("Low","Medium","High","Ultra")
    val defaultSelection=ModelSelection("wine","medium")
    fun idAt(index:Int)=ids[index.coerceIn(0,ids.lastIndex)]
    fun qualityAt(index:Int)=qualities[index.coerceIn(0,qualities.lastIndex)]
    fun displayName(modelId:String)=names[ids.indexOf(modelId).coerceAtLeast(0)]
    fun indexOfId(modelId:String)=ids.indexOf(modelId).coerceAtLeast(0)
    fun indexOfQuality(lod:String)=qualities.indexOf(lod).coerceAtLeast(0)
}
