package com.glassstudy.filament

/** Shared built-in model IDs, display names and levels of detail. */
data class ModelSelection(val modelId:String,val lod:String) {
    init {
        require(ModelCatalog.ids.contains(modelId)) { "Unsupported model: "+modelId }
        require(ModelCatalog.qualities.contains(lod)) { "Unsupported quality: "+lod }
    }
}

object ModelCatalog {
    val ids=listOf("faceted","tumbler","wine","mug","pitcher","bowl","vase",
        "sphere","cube","cylinder","cone")
    val names=listOf("Гранёный стакан","Стакан","Бокал","Чашка","Кувшин",
        "Пиала","Ваза","Сфера","Куб","Цилиндр","Конус")
    val qualities=listOf("low","medium","high")
    val defaultSelection=ModelSelection("tumbler","medium")
    fun idAt(index:Int)=ids[index.coerceIn(0,ids.lastIndex)]
    fun qualityAt(index:Int)=qualities[index.coerceIn(0,qualities.lastIndex)]
    fun displayName(modelId:String)=names[ids.indexOf(modelId).coerceAtLeast(0)]
    fun indexOfId(modelId:String)=ids.indexOf(modelId).coerceAtLeast(0)
    fun indexOfQuality(lod:String)=qualities.indexOf(lod).coerceAtLeast(0)
}
