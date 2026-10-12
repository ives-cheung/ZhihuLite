package com.ives.zhihulite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AccountItem(val id: String,val title: String,val body: String,val avatar: String="",val url: String="",val peer: String="",val content: Content?=null,val time: Long=0,val unread: Int=0)
data class AccountPage(val items: List<AccountItem>,val next: String?=null)
fun accountTitle(kind: String)=when(kind){"likes"->"我的赞同";"inbox"->"私信";else->"评论与回复"}
fun accountPage(repo: ZhihuRepository,kind: String,next: String?=null): AccountPage {
 require(repo.session.loggedIn()) {"登录知乎后查看账户内容。"}
 val profile=repo.session.profile() ?: repo.me()
 val url=next ?: when(kind) {
  "likes"->"https://www.zhihu.com/api/v3/moments/${profile.id}/activities?limit=20&offset=0"
  "inbox"->"https://www.zhihu.com/api/v4/inbox?limit=20"
  else->"https://www.zhihu.com/api/v4/notifications/v2/recent?limit=20&entry_name=all"
 }
 val j=repo.api.get(url);val a=j.optJSONArray("data") ?: throw ApiError(200,"知乎未返回有效的账户列表")
 val items=(0 until a.length()).mapNotNull { i->
  val t=a.optJSONObject(i) ?: return@mapNotNull null
  when(kind) {
   "likes"->{
    val source=t.optJSONObject("source")
    val verb=t.optString("verb",source?.optString("action_type").orEmpty()).uppercase()
    val action=t.optString("action_text",source?.optString("action_text").orEmpty())
    if(!verb.contains("VOTE_UP") && !verb.contains("UPVOTE") && !verb.contains("VOTEUP") && !action.contains("赞同"))return@mapNotNull null
    val c=repo.parse(t) ?: return@mapNotNull null
    AccountItem(t.optString("id",c.id),c.title,c.excerpt,c.avatar,c.url,content=c,time=t.optLong("created_time"))
   }
   "inbox"->{val peer=t.optJSONObject("participant") ?: return@mapNotNull null
    AccountItem(t.optString("id"),peer.optString("name"),plain(t.optString("snippet")),peer.optString("avatar_url"),url="https://www.zhihu.com/inbox/${peer.optString("id")}",peer=peer.optString("id"),time=t.optLong("updated_time"),unread=t.optInt("unread_count"))
   }
   else->{
    val notice=t.optJSONObject("content")
    val verb=notice?.optString("verb").orEmpty()
    val html=if(notice==null)t.optString("content") else notice.optJSONObject("extend")?.optString("text").orEmpty()
    val target=t.optJSONObject("target") ?: notice?.optJSONObject("target")
    val isReply=verb.contains("回复") || verb.contains("reply",ignoreCase=true)
    val isComment=(verb.contains("评论") || verb.contains("comment",ignoreCase=true)) && !verb.contains("喜欢") && !verb.contains("赞同")
    if(!isReply && !isComment)return@mapNotNull null
    val actors=notice?.optJSONArray("actors") ?: JSONArray()
    val names=(0 until actors.length()).mapNotNull {actors.optJSONObject(it)?.optString("name")?.takeIf{it.isNotBlank()}}.take(2).joinToString("、")
    val c=target?.let{repo.parse(it)}
    val text=plain(html)
    val body=(names+" "+verb).trim()+(if(text.isNotBlank()) "\n$text" else "")
    val link=Jsoup.parse(html).select("a[href]").map{it.attr("href")}.firstOrNull{it.startsWith("https://www.zhihu.com/question/") || it.startsWith("https://zhuanlan.zhihu.com/p/")}.orEmpty()
    AccountItem(t.optString("id"),c?.title?.takeIf{it.isNotBlank()} ?: "评论与回复",body,url=link.ifBlank{c?.url ?: "https://www.zhihu.com/notifications"},content=c,time=t.optLong("create_time"))
   }
  }
 }
 return AccountPage(items,pagingNext(j))
}
fun chatPage(repo: ZhihuRepository,peer: String,next: String?=null): AccountPage {
 require(repo.session.loggedIn()) {"登录知乎后查看私信。"}
 val j=repo.api.get(next ?: "https://www.zhihu.com/api/v4/chat?sender_id=${enc(peer)}&limit=20")
 val data=j.optJSONObject("data") ?: throw ApiError(200,"知乎未返回有效的会话数据")
 val a=data.optJSONArray("messages") ?: JSONArray()
 val sender=data.optJSONObject("sender")
 val receiver=data.optJSONObject("receiver")
 val entries=(0 until a.length()).mapNotNull {i->
  val message=a.optJSONObject(i) ?: return@mapNotNull null
  val person=if(message.optString("user_type")=="sender") sender else receiver
  AccountItem(message.optString("id"),person?.optString("name").orEmpty(),if(message.optBoolean("is_canceled")) "消息已撤回" else plain(message.optString("text")).ifBlank{"图片或其他媒体消息，可在知乎网页查看"},person?.optString("avatar_url").orEmpty(),time=message.optLong("created_time"))
 }
 return AccountPage(entries,pagingNext(j))
}
@Composable fun AccountScreen(vm: ReaderViewModel,kind: String,open: (Content)->Unit,web: (String)->Unit,chat: (AccountItem)->Unit,peer: String="") {
 var entries by remember {mutableStateOf<List<AccountItem>>(emptyList())}
 var next by remember{mutableStateOf<String?>(null)};var loading by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)};var reload by remember{mutableIntStateOf(0)}
 val scope=rememberCoroutineScope();val logged=vm.repo.session.loggedIn()
 suspend fun load(more: Boolean=false) {
  loading=true;error=null
  try {val p=withContext(Dispatchers.IO){if(kind=="chat")chatPage(vm.repo,peer,if(more)next else null) else accountPage(vm.repo,kind,if(more)next else null)}
   entries=if(more)(entries+p.items).distinctBy{it.id} else p.items;next=p.next
  }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message ?: "账户内容加载失败"}
  finally{loading=false}
 }
 LaunchedEffect(reload){if(logged)load()}
 LazyColumn(state=rememberLazyListState(),modifier=Modifier.fillMaxSize(),contentPadding=LocalReaderInsets.current) {
  if(!logged)item{Notice("登录知乎后查看账户内容。");TextButton({web("https://www.zhihu.com/signin")}){Text("登录知乎")}}
  if(kind=="likes")item{Notice("从你的知乎动态读取赞同记录。知乎可能只提供部分历史；可以继续加载较早的动态。")} 
  if(kind=="comments")item{Notice("收到的评论与回复 · 点击记录在知乎中查看完整上下文。")} 
  if(kind=="chat")item{Notice("最近消息 · 只读");TextButton({web("https://www.zhihu.com/inbox/$peer")}){Text("在知乎中打开完整会话")}}
  if(error!=null)item{ErrorView(error!!,{reload++},{web(if(kind=="inbox" || kind=="chat") "https://www.zhihu.com/inbox" else "https://www.zhihu.com/notifications")})}
  if(loading)item{Loading()}
  if(logged && !loading && error==null && entries.isEmpty())item{Notice(if(next!=null) "这一页没有相应记录，可继续加载较早内容。" else "知乎本次返回的列表中没有相应记录。")}
  items(entries,key={it.id}){item->
   if(kind=="likes" && item.content!=null)ContentRow(item.content){open(item.content)}
   else {
    Column(Modifier.fillMaxWidth().clickable(enabled=kind!="chat"){
     if(kind=="inbox")chat(item) else if(item.url.isNotEmpty())web(item.url) else item.content?.let{open(it)}
    }.padding(16.dp)) {
     Row(verticalAlignment=Alignment.CenterVertically){
      if(item.avatar.isNotEmpty())AsyncImage(item.avatar,"账户内容头像",Modifier.padding(end=10.dp).size(36.dp).clip(CircleShape))
      Text(item.title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold)
      if(item.unread>0)Text("${item.unread} 未读",fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
     }
     Spacer(Modifier.height(8.dp));Text(item.body,fontSize=15.sp,lineHeight=24.sp)
     if(item.time>0)Text(SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date(item.time*1000)),Modifier.padding(top=8.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(Modifier.padding(start=16.dp),thickness=.5.dp)
   }
  }
  if(logged)item{Row{TextButton({reload++},enabled=!loading){Text("刷新")};if(next!=null)TextButton({scope.launch{load(true)}},enabled=!loading){Text("加载更多")}}}
 }
}
