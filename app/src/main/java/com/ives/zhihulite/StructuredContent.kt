package com.ives.zhihulite

import org.json.JSONArray
import org.json.JSONObject

private fun escaped(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
fun pagingObject(value: Any?): JSONObject? = when(value) { is JSONObject -> value; is String -> runCatching {JSONObject(value)}.getOrNull();else->null }
fun pagingNext(j: JSONObject): String? = pagingObject(j.opt("paging"))?.takeIf { !it.optBoolean("is_end",true) }?.optString("next")?.replace("\\u0026","&")?.takeIf {it.startsWith("https://api.zhihu.com/") || it.startsWith("https://www.zhihu.com/")}
/** Render only response content. Entity tracking metadata and advertising segments are ignored. */
fun segmentsHtml(segments: JSONArray): String = buildString {
 fun text(node: JSONObject): String {
  val value=node.optString("text");val marks=node.optJSONArray("marks") ?: JSONArray()
  val spans=(0 until marks.length()).mapNotNull {marks.optJSONObject(it)}
  val boundaries=(listOf(0,value.length)+spans.flatMap{listOf(it.optInt("start_index").coerceIn(0,value.length),it.optInt("end_index").coerceIn(0,value.length))}).distinct().sorted()
  return buildString {
   for(i in 0 until boundaries.lastIndex) {
    val start=boundaries[i];val end=boundaries[i+1];if(start==end)continue
    val active=spans.filter{it.optInt("start_index")<=start && it.optInt("end_index")>=end}
    var part=escaped(value.substring(start,end)).replace("\n","<br>")
    for(mark in active) when(mark.optString("type")) {
     "bold"->part="<strong>$part</strong>";"italic"->part="<em>$part</em>";"code"->part="<code>$part</code>"
     "link","entity_word"->{val url=mark.optJSONObject(mark.optString("type"))?.optString("url").orEmpty();if(url.startsWith("https://"))part="<a href=\"${escaped(url)}\">$part</a>"}
    }
    append(part)
   }
  }
 }
 for(i in 0 until segments.length()) {
  val s=segments.optJSONObject(i) ?: continue;val type=s.optString("type");val node=s.optJSONObject(type) ?: continue
  when(type) {
   "paragraph"->append("<p>${text(node)}</p>")
   "heading"->append("<h2>${text(node)}</h2>")
   "blockquote"->append("<blockquote>${text(node)}</blockquote>")
   "image"->{val url=(node.optJSONArray("urls") ?: node.optJSONArray("original_urls"))?.optString(0).orEmpty();if(url.startsWith("https://"))append("<img src=\"${escaped(url)}\">")}
   "code","code_block"->append("<pre>${escaped(node.optString("text",node.optString("code")))}</pre>")
   "list_node"->{val items=node.optJSONArray("items") ?: JSONArray();val tag=if(node.optString("type")=="ordered") "ol" else "ul";append("<$tag>");for(n in 0 until items.length())append("<li>${text(items.optJSONObject(n) ?: JSONObject())}</li>");append("</$tag>")}
   "table"->{val cells=node.optJSONArray("cells") ?: JSONArray();val cols=node.optInt("column_count",1).coerceAtLeast(1);append("<table>");for(n in 0 until cells.length()){if(n%cols==0)append("<tr>");append("<td>${escaped(cells.optString(n))}</td>");if(n%cols==cols-1)append("</tr>")};append("</table>")}
   "card"->{val url=node.optString("url");if(url.startsWith("https://"))append("<p><a href=\"${escaped(url)}\">${escaped(node.optString("title"))}</a></p>")}
   "hr"->append("<p>────────</p>")
   else->{if(node.has("text"))append("<p>${text(node)}</p>")}
  }
 }
}
