package com.ives.zhihulite

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

fun plain(html: String) = Jsoup.parse(html).text()
fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
fun count(n: Long): String = if (n >= 10000) "%.1f万".format(n / 10000.0) else n.toString()
data class Content(val id: String, val type: String, val title: String, val excerpt: String = "", val author: String = "", val authorId: String = "", val avatar: String = "", val votes: Long = 0, val comments: Long = 0, val image: String = "", val html: String = "", val questionId: String = "", val heat: String = "", val truncated: Boolean = false, val time: Long = 0, val bodyNext: String? = null, val answerCount: Long = 0, val followers: Long = 0) {
    val url get() = when(type) { "question" -> "https://www.zhihu.com/question/$id"; "article" -> "https://zhuanlan.zhihu.com/p/$id"; else -> "https://www.zhihu.com/question/$questionId/answer/$id" }
}
data class Page(val items: List<Content>, val next: String? = null, val notice: String = "")
data class LoadState(val loading: Boolean = false, val items: List<Content> = emptyList(), val error: String? = null, val next: String? = null, val notice: String = "")
data class AccountProfile(val id: String, val name: String, val avatar: String, val headline: String, val followers: Long, val following: Long) {
    companion object {
        fun from(j: JSONObject): AccountProfile {
            require(j.optString("id").isNotBlank() && j.optString("name").isNotBlank()) {"知乎未返回有效账户资料"}
            return AccountProfile(j.getString("id"),j.getString("name"),j.optString("avatar_url"),j.optString("headline"),j.optLong("follower_count"),j.optLong("following_count"))
        }
    }
}
data class AccountState(val hasSession: Boolean=false,val profile: AccountProfile?=null,val loading: Boolean=false,val error: String?=null)
class ApiError(val status: Int, message: String): Exception(message)

class ZhihuApi(private val session: SessionStore) {
    fun get(url: String): JSONObject {
        val host = URL(url).host
        require(host in setOf("api.zhihu.com", "www.zhihu.com", "zhuanlan.zhihu.com")) { "请求域名不受支持" }
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12000; connection.readTimeout = 16000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", if (host == "api.zhihu.com") "com.zhihu.android/Futureve/11.2.0" else "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36")
        connection.setRequestProperty("x-api-version", if(url.contains("/feeds?")) "3.0.89" else "3.0.93")
        connection.setRequestProperty("x-page-id", "172")
        if(host=="www.zhihu.com") {
            connection.setRequestProperty("x-requested-with","fetch")
            session.read().split(';').map {it.trim().split('=',limit=2)}.firstOrNull {it.size==2 && it[0]=="_xsrf"}?.let {connection.setRequestProperty("x-xsrftoken",it[1])}
        }
        connection.setRequestProperty("Referer", "https://www.zhihu.com/")
        session.read().takeIf { it.isNotEmpty() }?.let { connection.setRequestProperty("Cookie", it) }
        try {
            val status = connection.responseCode
            val body = (if(status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
                throw ApiError(status, if(status == 401 || status == 403) "知乎要求验证或登录（HTTP $status）。${detail.take(120)}" else "请求失败（HTTP $status），请稍后重试。")
            }
            return try { JSONObject(body) } catch(e: Exception) { throw ApiError(status, "知乎返回了非数据页面，请完成验证后重试。") }
        } finally { connection.disconnect() }
    }
}
class ZhihuRepository(context: Context) {
    val session = SessionStore(context)
    val api = ZhihuApi(session)
    val db = Room.databaseBuilder(context, LiteDatabase::class.java, "reader.db").build()
    private val cache = File(context.cacheDir, "responses").apply { mkdirs() }
    private val known = java.util.concurrent.ConcurrentHashMap<String, Content>()
    private fun file(url: String) = File(cache, MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")
    fun cached(url: String): JSONObject? = runCatching { JSONObject(file(url).readText()) }.getOrNull()
    private fun fetch(url: String, force: Boolean = false, ttl: Long = 300000): JSONObject {
        val f = file(url)
        if (!force && f.exists() && System.currentTimeMillis()-f.lastModified() < ttl) cached(url)?.let { return it }
        return api.get(url).also { f.writeText(it.toString()) }
    }
    fun parse(obj: JSONObject): Content? {
        val t = obj.optJSONObject("target") ?: obj.optJSONObject("object") ?: obj
        val type = t.optString("type", "answer")
        val titleArea = t.optJSONObject("title_area")
        if (titleArea != null) {
            val url = t.optJSONObject("link")?.optString("url").orEmpty()
            return Content(url.substringAfterLast('/'), "question", titleArea.optString("text"), t.optJSONObject("excerpt_area")?.optString("text").orEmpty(), image=t.optJSONObject("image_area")?.optString("url").orEmpty(), heat=t.optJSONObject("metrics_area")?.optString("text").orEmpty(),answerCount=obj.optJSONObject("feed_specific")?.optLong("answer_count") ?: 0)
        }
        val id = t.optString("id"); if (id.isEmpty()) return null
        val q = t.optJSONObject("question"); val author = t.optJSONObject("author")
        val title = t.optString("title").ifEmpty { q?.optString("title").orEmpty() }
        val structured=t.optJSONObject("structured_content")
        if(structured!=null) {
            val stats=t.optJSONObject("reaction")?.optJSONObject("statistics")
            val segments=structured.optJSONArray("segments") ?: JSONArray()
            val html=segmentsHtml(segments)
            return Content(id,type,title,plain(t.optString("excerpt")),author?.optString("fullname").orEmpty(),author?.optString("url_token").orEmpty(),author?.optJSONObject("avatar")?.optJSONObject("avatar_image")?.optString("day").orEmpty(),stats?.optLong("up_vote_count") ?: 0,stats?.optLong("comment_count") ?: 0,html=html,questionId=q?.optString("id").orEmpty(),bodyNext=pagingNext(structured),answerCount=q?.optLong("answer_count") ?: 0,followers=q?.optLong("followers_count") ?: 0).also {known["$type:$id"]=it}
        }
        if (type !in setOf("answer", "question", "article")) return null
        return Content(id, type, title, plain(t.optString("excerpt")), author?.optString("name").orEmpty(), author?.optString("url_token").orEmpty(), author?.optString("avatar_url").orEmpty(), t.optLong("voteup_count"), t.optLong("comment_count"), t.optString("thumbnail"), t.optString("content"), q?.optString("id").orEmpty(), obj.optString("detail_text"), t.optBoolean("content_need_truncated") || t.optBoolean("force_login_when_click_read_more"), t.optLong("updated_time")).also { known["$type:$id"] = it }
    }
    private fun page(j: JSONObject): Page {
        val a = j.optJSONArray("data") ?: JSONArray()
        val items = (0 until a.length()).mapNotNull { runCatching { parse(a.getJSONObject(it)) }.getOrNull() }.distinctBy { "${it.type}:${it.id}" }
        return Page(items, pagingNext(j))
    }
    val hotUrl = "https://api.zhihu.com/topstory/hot-lists/total?limit=50"
    val feedUrl = "https://www.zhihu.com/api/v3/explore/guest/feeds?limit=15&ws_qiangzhisafe=0"
    fun guestFeed(next: String?=null,force: Boolean=false) = page(fetch(next ?: feedUrl,force)).copy(notice="游客推荐")
    fun feed(next: String? = null, force: Boolean = false): Page {
        if(!session.loggedIn())return guestFeed(next,force)
        val result=page(api.get(next ?: "https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=20"))
        if(result.items.isEmpty() && result.next==null)throw ApiError(200,"知乎暂未返回可阅读的账号推荐，请重试。")
        return result.copy(notice="账号推荐")
    }
    fun hot(force: Boolean = false) = page(fetch(hotUrl, force))
    fun cachedHot() = cached(hotUrl)?.let { page(it) }
    fun cachedFeed() = if(session.loggedIn()) null else cached(feedUrl)?.let { page(it).copy(notice="游客推荐") }
    fun question(c: Content, next: String? = null, order: String = "default"): Page {
        if(next != null) return page(fetch(next)).let { it.copy(items=it.items.filter {answer -> answer.questionId == c.id}) }
        val legacy="https://api.zhihu.com/questions/${c.id}/feeds?include=big_card_summary,media_detail,reaction_instruction,is_author,is_thanked,voting,is_favorited,label_info,content_text_length,reactions&order=$order&show_detail=1&limit=10&offset=0"
        try { val result=page(fetch(legacy));if(result.items.isNotEmpty())return result } catch(_:Exception) {}
        val seeds=guestFeed().items
        val seed=seeds.firstOrNull {it.type=="answer" && it.questionId!=c.id} ?: seeds.firstOrNull {it.type=="answer"} ?: throw ApiError(403,"知乎暂未提供可用的游客回答入口，请验证后重试。")
        val url="https://api.zhihu.com/next-render?id=${seed.id}&type=answer&context_expand=1&is_native=0&scenes=question_feed&collection_id=${c.id}&collection_type=question&question_feed_session_id=&question_feed_cursor="
        val j=fetch(url);val result=page(j)
        val entries=result.items.filter {it.questionId==c.id}
        val a=j.optJSONArray("data") ?: JSONArray()
        val login=(0 until a.length()).any {a.optJSONObject(it)?.optString("type")=="login_prompt"}
        if(entries.isEmpty() && login)throw ApiError(403,"知乎要求登录后查看更多回答。")
        return result.copy(items=entries,notice=if(login) "知乎提供了游客可读回答；更多回答可能需要登录。" else if(order!="default") "当前游客接口采用知乎默认排序，最新排序需登录后使用。" else "")
    }
    fun detail(c: Content): Content {
        if(c.type=="answer") {
            val knownContent=known["answer:${c.id}"] ?: c
            val body=fetch("https://api.zhihu.com/next-content-render?offset=0&url_token=${c.id}&content_type=answer")
            val segments=body.optJSONArray("segments") ?: throw ApiError(200,"知乎没有返回正文")
            val html=segmentsHtml(segments)
            if(html.isBlank()) throw ApiError(403,"此回答的正文暂时不可用，请打开知乎验证。")
            val login=(0 until segments.length()).any {segments.optJSONObject(it)?.optString("type")=="login_prompt"}
            return knownContent.copy(html=html,truncated=login,bodyNext=pagingNext(body)).also {known["answer:${c.id}"]=it}
        }
        return parse(fetch("https://www.zhihu.com/api/v4/articles/${c.id}?include=content,author")) ?: throw ApiError(200,"正文结构无法解析")
    }
    fun bodyPage(c: Content): Content {
        val next=c.bodyNext ?: return c
        val j=fetch(next)
        val segments=j.optJSONArray("segments") ?: throw ApiError(200,"知乎没有返回后续正文")
        val login=(0 until segments.length()).any {segments.optJSONObject(it)?.optString("type")=="login_prompt"}
        val addition=segmentsHtml(segments);val following=pagingNext(j)
        if(following==next)throw ApiError(200,"知乎返回了重复的正文分页，请稍后重试。")
        if(addition.isBlank() && login)throw ApiError(403,"后续正文需要知乎登录，请完成验证。")
        return c.copy(html=c.html+addition,bodyNext=following,truncated=c.truncated || login).also {known["${c.type}:${c.id}"]=it}
    }
    fun comments(c: Content, next: String? = null): JSONObject {
        val kind=when(c.type){"question"->"questions";"article"->"articles";"pin"->"pins";else->"answers"}
        return api.get(next ?: "https://www.zhihu.com/api/v4/comment_v5/$kind/${enc(c.id)}/root_comment?order_by=score&limit=20")
    }
    fun replies(rootId: String,next: String?=null) = api.get(next ?: "https://www.zhihu.com/api/v4/comment_v5/comment/${enc(rootId)}/child_comment?order_by=ts&limit=20")
    fun me(): AccountProfile {
        require(session.loggedIn()) {"尚未保存知乎登录会话"}
        val before=session.read()
        val j=api.get("https://www.zhihu.com/api/v4/me?include=following_count,follower_count,headline,avatar_url")
        if(session.read()!=before)throw CancellationException("账户会话已变化")
        val profile=AccountProfile.from(j);session.storeProfile(j);return profile
    }
    fun user(id: String) = fetch("https://www.zhihu.com/api/v4/members/${enc(id)}?include=answer_count,articles_count,follower_count,voteup_count")
    fun search(query: String, type: String, next: String? = null) = page(fetch(next ?: "https://www.zhihu.com/api/v4/search_v3?t=$type&q=${enc(query)}&correction=1&offset=0&limit=20", ttl=60000))
    fun following(next: String? = null) = page(fetch(next ?: "https://www.zhihu.com/api/v3/moments?limit=20", ttl=60000))
    fun save(c: Content, kind: String) {
        val s = SavedContent(); s.key="$kind:${c.type}:${c.id}"; s.kind=kind; s.contentType=c.type; s.contentId=c.id; s.title=c.title; s.author=c.author; s.url=c.url; s.readTime=System.currentTimeMillis(); db.saved().put(s)
    }
    fun clearCache() { cache.listFiles()?.forEach { it.delete() } }
    fun cacheSize() = (cache.listFiles()?.sumOf { it.length() } ?: 0L)
}

class ReaderViewModel(app: Application): AndroidViewModel(app) {
    val repo = ZhihuRepository(app)
    private val _home = MutableStateFlow(LoadState())
    private val _hot = MutableStateFlow(LoadState())
    val home = _home.asStateFlow(); val hot = _hot.asStateFlow()
    private val _account=MutableStateFlow(AccountState(hasSession=repo.session.loggedIn(),profile=repo.session.profile()))
    val account=_account.asStateFlow()
    private var accountJob: Job?=null
    private var homeJob: Job?=null
    private fun sessionKey()=repo.session.read().split(';').map{it.trim()}.firstOrNull{it.startsWith("z_c0=")}.orEmpty()
    private var homeSession=sessionKey()
    fun syncAccount() {
        val current=sessionKey()
        if(current!=homeSession){homeSession=current;homeJob?.cancel();_home.value=LoadState();refresh(false,true)}
        accountJob?.cancel()
        val authenticated=repo.session.loggedIn()
        if(!authenticated){_account.value=AccountState();return}
        accountJob=viewModelScope.launch {
            _account.value=AccountState(true,repo.session.profile(),loading=true)
            try {val profile=withContext(Dispatchers.IO){repo.me()};_account.value=AccountState(true,profile)}
            catch(e: CancellationException){throw e}
            catch(e: Exception){_account.value=_account.value.copy(loading=false,error=if(e is ApiError) "资料同步失败（HTTP ${e.status}），可重新验证后重试。" else "账户资料暂未同步，请重试。")}
        }
    }
    val questionStates = mutableMapOf<String,LoadState>()
    val readStates = mutableMapOf<String,Content>()
    init { repo.session.capture();syncAccount();refresh(false);refresh(true) }
    fun refresh(hot: Boolean, force: Boolean = false, more: Boolean = false) {
        val target = if(hot) _hot else _home
        if(target.value.loading) return
        if(more && target.value.next == null) return
        val requestSession=sessionKey()
        val job=viewModelScope.launch {
            if(target.value.items.isEmpty()) {
                val cached = withContext(Dispatchers.IO) { if(hot) repo.cachedHot() else repo.cachedFeed() }
                if(cached!=null) target.value = LoadState(items=cached.items, next=cached.next)
            }
            target.value = target.value.copy(loading=true,error=null)
            try {
                val p = withContext(Dispatchers.IO) { if(hot) repo.hot(force) else repo.feed(if(more) target.value.next else null, force) }
                if(!hot && requestSession!=sessionKey())throw CancellationException("账户会话已变化")
                target.value = LoadState(items=if(more) (target.value.items+p.items).distinctBy { "${it.type}:${it.id}" } else p.items, next=p.next, notice=p.notice)
            } catch(e: CancellationException){throw e}
            catch(e: Exception) { target.value = target.value.copy(loading=false,error=e.message ?: "网络连接失败") }
        }
        if(!hot)homeJob=job
    }
    fun record(c: Content) { viewModelScope.launch(Dispatchers.IO) { repo.save(c,"history") } }
}
