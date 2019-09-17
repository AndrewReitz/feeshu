import org.w3c.dom.Window
import org.w3c.dom.asList
import kotlin.js.Promise

private const val HTML_TEMPLATE = """<!DOCTYPE html><html lang="en"><head> <meta charset="UTF-8"> <meta name="viewport" content="width=device-width, initial-scale=1.0"> <meta http-equiv="X-UA-Compatible" content="ie=edge"> <title>feeshu</title> <style>html{line-height:1.15;-webkit-text-size-adjust:100%}body{margin:0}main{display:block}h1{font-size:2em;margin:.67em 0}hr{box-sizing:content-box;height:0;overflow:visible}pre{font-family:monospace,monospace;font-size:1em}a{background-color:transparent}abbr[title]{border-bottom:none;text-decoration:underline;text-decoration:underline dotted}b,strong{font-weight:bolder}code,kbd,samp{font-family:monospace,monospace;font-size:1em}small{font-size:80%}sub,sup{font-size:75%;line-height:0;position:relative;vertical-align:baseline}sub{bottom:-.25em}sup{top:-.5em}img{border-style:none}button,input,optgroup,select,textarea{font-family:inherit;font-size:100%;line-height:1.15;margin:0}button,input{overflow:visible}button,select{text-transform:none}button,[type="button"],[type="reset"],[type="submit"]{-webkit-appearance:button}button::-moz-focus-inner,[type="button"]::-moz-focus-inner,[type="reset"]::-moz-focus-inner,[type="submit"]::-moz-focus-inner{border-style:none;padding:0}button:-moz-focusring,[type="button"]:-moz-focusring,[type="reset"]:-moz-focusring,[type="submit"]:-moz-focusring{outline:1px dotted ButtonText}fieldset{padding:.35em .75em .625em}legend{box-sizing:border-box;color:inherit;display:table;max-width:100%;padding:0;white-space:normal}progress{vertical-align:baseline}textarea{overflow:auto}[type="checkbox"],[type="radio"]{box-sizing:border-box;padding:0}[type="number"]::-webkit-inner-spin-button,[type="number"]::-webkit-outer-spin-button{height:auto}[type="search"]{-webkit-appearance:textfield;outline-offset:-2px}[type="search"]::-webkit-search-decoration{-webkit-appearance:none}::-webkit-file-upload-button{-webkit-appearance:button;font:inherit}details{display:block}summary{display:list-item}template{display:none}[hidden]{display:none}body{color:#c7cae2;background-color:#272b2a}header{text-align:center}h2,ul{text-align:center}li{list-style-type:none;padding-top:8px;text-align:center}</style></head><body> <header> <h1>feeshu</h1> </header> ${"\${shows}"}</body></html>"""

@JsModule("jsdom")
external class jsdom {
    class JSDOM constructor(html: String) {
        constructor()
        val window: Window
    }
}

@JsModule("node-fetch")
external fun fetch(url: String): Promise<org.w3c.fetch.Response>

@JsName("feeshu")
fun feeshu(req: Request, res: Response) {
    val sets = req.query.sets?.toString()?.toInt() ?: 10

    fetch("https://api.phish.net/v3/setlists/recent?apikey=FFC6B556891FFF7A4198&limit=${sets}")
        .then { r -> r.json().asDynamic().unsafeCast<PhishJson>() }
        .then { json -> json.response }
        .then { response: PhishResponse -> response.data.toList() }
        .then { data: List<SetListData> ->
            val linksToLastShows = data.asSequence()
                .take(sets)
                .map { it.url }
                .mapIndexed { index, url -> """<li><a href="$url">Show ${index + 1}</a></li>""" }
                .joinToString(separator = "", prefix = "<h2>Last Show Links<h2><ul>", postfix = "</ul>")

            val lastShow = data.asSequence()
                .take(1)
                .map { setlist -> setlist.setlistdata }
                .map { setlistdata -> jsdom.JSDOM(setlistdata) }
                .flatMap { dom -> dom.window.document.getElementsByTagName("a").asList().asSequence() }
                .map { selector -> PhishSong(selector.textContent, selector.getAttribute("href")) }
                .map { (title, link) -> """<li><a href="$link">$title</a></li>""" }
                .joinToString(prefix = "<h2>Last Show</h2><ul>", postfix = "</ul>", separator = "")

            val playCount = data.asSequence()
                .take(sets)
                .map { setlist -> setlist.setlistdata }
                .map { setlistdata -> jsdom.JSDOM(setlistdata) }
                .flatMap { dom -> dom.window.document.getElementsByTagName("a").asList().asSequence() }
                .map { selector -> PhishSong(selector.textContent, selector.getAttribute("href")) }
                .groupBy { it.title }
                .toList()
                .map { it.second }
                .map { list -> list.first().let { SongCount(it.title, it.linkToPhishNet, list.size) } }
                .filter { it.count > 1 }
                .sortedByDescending { it.count }
                .map { (song, link, playCount) -> "<li><a href=\"$link\">$song</a> has been played $playCount times</li>" }
                .joinToString(separator = "", prefix = "<h2>In the last $sets shows</h2><ul>", postfix = "</ul>")


            HTML_TEMPLATE.replaceFirst("\${shows}", playCount + lastShow + linksToLastShows)
        }
        .then { data -> res.send(data) }

}

data class SongCount(val title: String?, val linkToPhishNet: String?, val count: Int)
data class PhishSong(val title: String?, val linkToPhishNet: String?)

external interface Request {
    val query: dynamic
}

external interface Response {
    fun send(any: Any)
    fun append(any: Any)
}

external interface SetListData {
    val showid: Int
    val showdate: String
    val short_date: String
    val long_date: String
    val relative_date: String
    val url: String
    val gapchart: String
    val artist: String
    val artistid: Int
    val venueid: Int
    val venue: String
    val location: String
    val setlistdata: String
    val setlistnotes: String
    val rating: String
}

external interface PhishResponse {
    val count: Int
    val data: Array<SetListData>
}

external interface PhishJson {
    val response: PhishResponse
}

