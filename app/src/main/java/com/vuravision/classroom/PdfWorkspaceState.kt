package com.vuravision.classroom

/** Coordinates remain in PDF display points, independently of divider and zoom. */
data class PdfSheet(var width: Float = 595f, var height: Float = 842f, var page: Page = Page(background="plain")) {
    fun deepCopy(): PdfSheet = copy(page=Lesson(pages=mutableListOf(page)).copyDeep().pages.first())
}
data class PdfWorkspaceState(
    var asset: String = "", var sourceUri: String = "", var title: String = "PDF",
    var sheets: MutableList<PdfSheet> = mutableListOf(),
    var ratio: Float = .5f, var onRight: Boolean = true, var fullscreen: Boolean = false,
    var scroll: Int = 0, var zoom: Float = 1f, var current: Int = 0,
    var revision: Long = 0, var savedRevision: Long = 0,
    var originalHash: String = "",
) {
    val unsaved get() = revision != savedRevision
    fun copyForSave(): PdfWorkspaceState = copy(sheets=sheets.map{it.copy(page=Lesson(pages=mutableListOf(it.page)).copyForSave().pages.first())}.toMutableList())
    fun deepCopy(): PdfWorkspaceState = copy(sheets=sheets.map{it.deepCopy()}.toMutableList())
    fun validate() {
        require(asset.matches(Regex("[a-zA-Z0-9._-]+")))
        require(title.length<=1000 && sourceUri.length<=10000)
        require(sheets.size in 1..1000 && current in sheets.indices)
        require(ratio.isFinite() && ratio in .2f.. .8f && zoom.isFinite() && zoom in 1f..3f && scroll>=0)
        require(sheets.sumOf { it.page.items.size }<=20000)
        require(sheets.sumOf { s->s.page.items.sumOf{it.points.size} }<=1000000)
        sheets.forEachIndexed { i,s ->
            require(s.width.isFinite() && s.height.isFinite() && s.width in 1f..100000f && s.height in 1f..100000f)
            Lesson(pages=mutableListOf(s.page)).validate()
            require(s.page.panes.size==1)
            val base=s.page.items.firstOrNull()
            require(base!=null && base.kind=="pdf" && base.asset==asset && base.pdfPage==i && base.pageCount==sheets.size && base.locked)
        }
    }
}
fun Lesson.allAssetItems(): List<Item> = pages.flatMap{it.validationCanvases().flatMap{c->c.items}} + pdf?.sheets.orEmpty().flatMap{it.page.items}
