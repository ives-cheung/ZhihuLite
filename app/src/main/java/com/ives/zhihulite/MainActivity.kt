package com.ives.zhihulite

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity: ComponentActivity() {
 private val vm: ReaderViewModel by viewModels()
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { ReaderApp(vm) } }
}
class Route(val name: String, val content: Content?=null, val value: String="") { val key="${name}:${content?.type}:${content?.id}:$value" }
internal val Blue=Color(0xFF0084FF)
@Composable fun ReaderApp(vm: ReaderViewModel) {
 val context=LocalContext.current
 val prefs=remember { context.getSharedPreferences("settings",0) }
 var theme by rememberSaveable { mutableStateOf(prefs.getString("theme","system") ?: "system") }
 var font by rememberSaveable { mutableIntStateOf(prefs.getInt("font",17)) }
 val dark=theme=="dark" || (theme=="system" && isSystemInDarkTheme())
 val scheme=if(dark) darkColorScheme(primary=Blue,background=Color(0xFF0D1420),surface=Color(0xFF182230),surfaceVariant=Color(0xFF232A32),onSurface=Color(0xFFE5EAF0),onSurfaceVariant=Color(0xFF9AA6B5)) else lightColorScheme(primary=Blue,background=Color(0xFFF0F3F9),surface=Color.White,surfaceVariant=Color(0xFFF2F5F8),onSurface=Color(0xFF202124),onSurfaceVariant=Color(0xFF737B86))
 SideEffect {val activity=context as? ComponentActivity;activity?.window?.let{window->androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightStatusBars=!dark;androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).isAppearanceLightNavigationBars=!dark}}
 MaterialTheme(colorScheme=scheme) {
  val stack=remember { mutableStateListOf(Route("root")) }
  var tab by rememberSaveable { mutableIntStateOf(0) }
  var sessionVersion by remember { mutableIntStateOf(0) }
  val route=stack.last(); val holder=rememberSaveableStateHolder()
  fun push(r: Route) { stack.add(r); r.content?.let { vm.record(it) } }
  fun back() { if(stack.size>1) {if(stack.last().name=="web"){vm.repo.session.capture();sessionVersion++;vm.syncAccount()};stack.removeAt(stack.lastIndex)} }
  fun web(url: String) {
   val uri=Uri.parse(url);val host=uri.host.orEmpty()
   if(uri.scheme=="https" && (host=="zhihu.com" || host.endsWith(".zhihu.com")))push(Route("web",value=url))
   else if(uri.scheme=="https" || uri.scheme=="http")runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,uri))}
  }
  fun open(c: Content) { push(Route(if(c.type=="question") "question" else "reader",c)) }
  BackHandler(stack.size>1) { back() }
  val backdrop=rememberGraphicsLayer()
  val background=Brush.verticalGradient(listOf(scheme.background,scheme.surfaceVariant,scheme.background))
  CompositionLocalProvider(LocalBackdrop provides backdrop, LocalContentColor provides scheme.onSurface) {
  Box(Modifier.fillMaxSize()) {
  Scaffold(containerColor=Color.Transparent,
   topBar={if(route.name!="web") GlassPanel(Modifier.statusBarsPadding().padding(horizontal=12.dp,vertical=8.dp).fillMaxWidth(),radius=26) { Header(when(route.name) { "root"->listOf("知乎Lite","热榜","关注","我的")[tab];"search"->"搜索";"question"->"问题";"reader"->"阅读";"comments"->"评论";"replies"->"全部回复";"user"->"作者";"account"->accountTitle(route.value);"chat"->route.value.substringAfter("\n","私信");"saved"->route.value;"settings"->"设置";else->"知乎Lite" },stack.size>1,{back()},onSearch={push(Route("search"))},onRefresh=if(route.name=="root" && tab<2) {{vm.refresh(tab==1,true)}} else null) }},
   bottomBar={if(route.name=="root") BottomTabs(tab) {tab=it}}) { padding ->
   CompositionLocalProvider(LocalReaderInsets provides padding) {
   Box(Modifier.fillMaxSize().drawWithContent {backdrop.record {this@drawWithContent.drawContent()};drawLayer(backdrop)}.background(if(route.name=="reader") Brush.verticalGradient(listOf(scheme.surface,scheme.surface)) else background)) {
    holder.SaveableStateProvider(if(route.name=="root") "tab:$tab" else route.key) {
     when(route.name) {
      "root" -> when(tab) {
       0 -> { val state by vm.home.collectAsState(); FeedScreen(state,false,{vm.refresh(false,true)},{vm.refresh(false,more=true)},::open,::web) }
       1 -> { val state by vm.hot.collectAsState(); FeedScreen(state,true,{vm.refresh(true,true)},{},::open,::web) }
       2 -> Following(vm,sessionVersion,::open,::web)
       3 -> MyScreen(vm,sessionVersion,{push(Route("saved",value=it))},{push(Route("settings"))},::web,{push(Route("account",value=it))})
      }
      "question" -> QuestionScreen(vm,route.content!!,::open,::web)
      "reader" -> ReadingScreen(vm,route.content!!,font,::web,{push(Route("comments",it))},{push(Route("user",value=it))})
      "comments" -> CommentsScreen(vm,route.content!!,::web,{push(Route("replies",route.content,value=it.toString()))})
      "replies" -> key(route.value){CommentsScreen(vm,route.content!!,::web,{},JSONObject(route.value))}
      "search" -> SearchScreen(vm,::open,::web,{push(Route("user",value=it))})
      "user" -> UserScreen(vm,route.value,::web)
      "saved" -> SavedScreen(vm,route.value,::open)
      "account" -> AccountScreen(vm,route.value,::open,::web,{push(Route("chat",value=it.peer+"\n"+it.title))})
      "chat" -> AccountScreen(vm,"chat",::open,::web,{},peer=route.value.substringBefore("\n"))
      "settings" -> SettingsScreen(vm,theme,font,{theme=it;prefs.edit().putString("theme",it).apply()},{font=it;prefs.edit().putInt("font",it).apply()},{sessionVersion++;vm.syncAccount()},::web)
      "web" -> WebScreen(route.value,{back()},{back()},{sessionVersion++;vm.syncAccount()})
     }
    }
   }
  }
 }
  Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scheme.background.copy(alpha=.92f)))
  Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(scheme.background.copy(alpha=.92f)))
  }
  }
 }
}

@Composable fun Header(title: String, back: Boolean=false, onBack: ()->Unit={},onSearch: ()->Unit={},onRefresh: (()->Unit)?=null) {
 Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
  if(back) IconButton(onBack) { Icon(Icons.Default.ArrowBack,"返回") } else Spacer(Modifier.width(8.dp))
  Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
  if(onRefresh!=null) IconButton(onRefresh) { Icon(Icons.Default.Refresh,"刷新") }
  if(!back) IconButton(onSearch) { Icon(Icons.Default.Search,"搜索") }
 }
}
@Composable fun BottomTabs(selected: Int, choose: (Int)->Unit) {
 GlassPanel(Modifier.navigationBarsPadding().padding(start=20.dp,end=20.dp,top=8.dp,bottom=12.dp).fillMaxWidth(),radius=34,frosted=true) {BubbleNavigation(selected,choose)}
}
@Composable fun Notice(text: String) { Text(text,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
@Composable fun ErrorView(text: String, retry: ()->Unit, verify: (()->Unit)?=null) {
 Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
  Text("暂时无法加载",fontSize=17.sp,fontWeight=FontWeight.Medium)
  Spacer(Modifier.height(8.dp));Text(text,fontSize=13.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row { TextButton(retry) {Text("重新加载")}; if(verify!=null) TextButton(verify) {Text("打开知乎验证") } }
 }
}
@Composable fun Loading() { Box(Modifier.fillMaxWidth().padding(32.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(25.dp),strokeWidth=2.dp) } }
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FeedScreen(state: LoadState, hot: Boolean, refresh: ()->Unit, more: ()->Unit, open: (Content)->Unit, web: (String)->Unit) {
 val list=rememberLazyListState()
 PullToRefreshBox(state.loading,refresh,Modifier.fillMaxSize()) {
  LazyColumn(state=list,modifier=Modifier.fillMaxSize(),contentPadding=LocalReaderInsets.current) {
   item { Row(Modifier.padding(horizontal=16.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically) { Text(if(hot) "全站热榜" else "为你推荐",fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.weight(1f));Text(if(hot) "实时 · 知乎" else state.notice.ifBlank{"正在获取推荐"},modifier=Modifier.clip(RoundedCornerShape(6.dp)).background(Blue.copy(alpha=.07f)).padding(horizontal=8.dp,vertical=4.dp),fontSize=10.sp,color=Blue) } }
   if(state.error!=null) item { ErrorView(state.error,refresh,{web("https://www.zhihu.com/")}) }
   if(state.items.isEmpty() && !state.loading && state.error==null) item { Notice("暂无内容，下拉刷新试试。") }
   itemsIndexed(state.items,key={_,c->"${c.type}:${c.id}"}) { index,c -> if(hot) HotRow(index,c) {open(c)} else ContentRow(c) {open(c)} }
   if(state.loading && state.items.isEmpty()) item {Loading()}
   if(state.next!=null) item { TextButton(more,Modifier.fillMaxWidth(),enabled=!state.loading) {Text(if(state.loading) "加载中…" else "加载更多")} }
   if(state.items.isNotEmpty() && state.next==null) item {Text(if(hot) "已展示当前热榜" else "暂时没有更多内容",Modifier.fillMaxWidth().padding(20.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  }
 }
 if(!hot) {
  val end by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= state.items.size-3 && state.items.isNotEmpty() } }
  LaunchedEffect(end,state.items.size) { if(end && state.next!=null && !state.loading) more() }
 }
}
@Composable fun HotRow(index: Int,c: Content,click: ()->Unit) {
 Row(Modifier.padding(horizontal=12.dp,vertical=4.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=click).padding(16.dp)) {
  Text("%02d".format(index+1),Modifier.width(36.dp),color=if(index<3) Blue else MaterialTheme.colorScheme.onSurfaceVariant,fontSize=19.sp,fontWeight=FontWeight.Bold)
  Column(Modifier.weight(1f)) {Text(c.title,fontSize=16.sp,lineHeight=24.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(7.dp));Text(c.heat.ifEmpty {"知乎热榜"},fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
  if(c.image.isNotEmpty()) AsyncImage(c.image,null,Modifier.padding(start=12.dp).size(68.dp).clip(RoundedCornerShape(12.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
 }
}
@Composable fun ContentRow(c: Content,showTitle: Boolean=true,click: ()->Unit) {
 val muted=MaterialTheme.colorScheme.onSurfaceVariant
 Column(Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth().shadow(2.dp,RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=click).padding(horizontal=18.dp,vertical=17.dp)) {
  Text(if(showTitle)c.title.ifEmpty{c.author} else c.author,fontSize=(if(showTitle)16 else 15).sp,lineHeight=23.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
  val summary=c.excerpt.ifEmpty{plain(c.html)}.replace(Regex("\\s+")," ").trim()
  if(summary.isNotBlank() || c.image.isNotBlank()) {
   Spacer(Modifier.height(8.dp))
   Row(verticalAlignment=Alignment.Top) {
    if(summary.isNotBlank())Text(summary,Modifier.weight(1f),fontSize=13.sp,lineHeight=20.sp,maxLines=2,overflow=TextOverflow.Ellipsis,color=muted)
    else Spacer(Modifier.weight(1f))
    if(c.image.isNotBlank())AsyncImage(c.image,null,Modifier.padding(start=12.dp).size(width=76.dp,height=56.dp).clip(RoundedCornerShape(8.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)
   }
  }
  Spacer(Modifier.height(12.dp))
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   if(c.avatar.isNotBlank())AsyncImage(c.avatar,null,Modifier.size(20.dp).clip(CircleShape))
   Text(c.author.ifBlank{if(c.type=="article")"文章" else if(c.type=="question")"问题" else "回答"},Modifier.weight(1f),fontSize=11.sp,color=muted,maxLines=1,overflow=TextOverflow.Ellipsis)
   StatBadge(R.drawable.ic_like,count(c.votes))
   StatBadge(R.drawable.ic_reply,count(c.comments))
  }
 }
}
@Composable fun QuestionScreen(vm: ReaderViewModel,c: Content,open: (Content)->Unit,web: (String)->Unit) {
 var order by rememberSaveable {mutableStateOf("default")};val stateKey="${c.id}:$order";var state by remember(stateKey) {mutableStateOf(vm.questionStates[stateKey] ?: LoadState(loading=true))};var reload by remember {mutableIntStateOf(0)};val scope=rememberCoroutineScope();var expanded by rememberSaveable{mutableStateOf(false)}
 LaunchedEffect(state) {if(!state.loading)vm.questionStates[stateKey]=state}
 LaunchedEffect(reload,order) {if(reload==0 && vm.questionStates[stateKey]!=null)return@LaunchedEffect;state=state.copy(loading=true,error=null);try {val p=withContext(Dispatchers.IO){vm.repo.question(c,order=order)};state=LoadState(items=p.items,next=p.next,notice=p.notice)}catch(e: Exception){state=state.copy(loading=false,error=e.message)}}
 LazyColumn(state=rememberLazyListState(),contentPadding=LocalReaderInsets.current) {
  item {Column(Modifier.padding(18.dp)) {Text(c.title,fontSize=23.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold);if(c.excerpt.isNotEmpty()){Spacer(Modifier.height(14.dp));Text(c.excerpt,fontSize=15.sp,lineHeight=24.sp,maxLines=if(expanded) Int.MAX_VALUE else 5,overflow=TextOverflow.Ellipsis);if(c.excerpt.length>180)TextButton({expanded=!expanded}){Text(if(expanded) "收起问题描述" else "展开问题描述")}};Spacer(Modifier.height(12.dp));Text(c.heat.ifEmpty {"知乎问题"} + (if((state.items.firstOrNull()?.answerCount ?: c.answerCount)>0) " · ${count(state.items.firstOrNull()?.answerCount ?: c.answerCount)} 回答" else "") + (if((state.items.firstOrNull()?.followers ?: 0)>0) " · ${count(state.items.first().followers)} 关注" else ""),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);SaveActions(vm,c)}}
  item {Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {Icon(painterResource(R.drawable.ic_reply),null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("回答",Modifier.weight(1f),fontWeight=FontWeight.SemiBold);TextButton({order="default"}) {Text("默认",color=if(order=="default") Blue else MaterialTheme.colorScheme.onSurfaceVariant)};TextButton({order="updated"}) {Text("最新",color=if(order=="updated") Blue else MaterialTheme.colorScheme.onSurfaceVariant)}}}
  if(state.notice.isNotEmpty()) item {Notice(state.notice)}
  if(state.error!=null) item {ErrorView(state.error!!,{reload++},{web(c.url)})}
  if(state.loading) item {Loading()}
  items(state.items,key={it.id}) {answer->ContentRow(answer,showTitle=false) {open(answer)}}
  if(!state.loading && state.error==null && state.items.isEmpty()) item {Notice("此问题暂无可显示的回答。")}
  if(state.next!=null) item {TextButton({scope.launch {state=state.copy(loading=true);try{val p=withContext(Dispatchers.IO){vm.repo.question(c,state.next,order)};state=LoadState(items=(state.items+p.items).distinctBy{it.id},next=p.next)}catch(e:Exception){state=state.copy(loading=false,error=e.message)}}},Modifier.fillMaxWidth(),enabled=!state.loading){Text("加载更多回答")}}
 }
}
@Composable fun SaveActions(vm: ReaderViewModel,c: Content) {
 val scope=rememberCoroutineScope();val context=LocalContext.current
 var saved by remember {mutableStateOf(false)};var later by remember {mutableStateOf(false)}
 LaunchedEffect(c.id){withContext(Dispatchers.IO){saved=vm.repo.db.saved().exists("favorite:${c.type}:${c.id}")>0;later=vm.repo.db.saved().exists("later:${c.type}:${c.id}")>0}}
 Row {
  TextButton({scope.launch(Dispatchers.IO){if(saved) vm.repo.db.saved().remove("favorite:${c.type}:${c.id}") else vm.repo.save(c,"favorite");saved=!saved}}){Text(if(saved) "已收藏" else "本地收藏")}
  TextButton({scope.launch(Dispatchers.IO){if(later) vm.repo.db.saved().remove("later:${c.type}:${c.id}") else vm.repo.save(c,"later");later=!later}}){Text(if(later) "已加入稍后阅读" else "稍后阅读")}
  IconButton({context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"${c.title}\n${c.url}")},"分享内容"))}) {Icon(Icons.Default.Share,"分享")}
 }
}
@Composable fun ReadingScreen(vm: ReaderViewModel,seed: Content,size: Int,web: (String)->Unit,comments: (Content)->Unit,user: (String)->Unit) {
 var content by remember {mutableStateOf(vm.readStates[seed.id] ?: seed)};var loading by remember {mutableStateOf(true)};var error by remember {mutableStateOf<String?>(null)};var retry by remember {mutableIntStateOf(0)};var image by remember {mutableStateOf<String?>(null)};val scope=rememberCoroutineScope();var loadingMore by remember{mutableStateOf(false)}
 LaunchedEffect(content,loading) {if(!loading && content.html.isNotEmpty())vm.readStates[seed.id]=content}
 LaunchedEffect(retry){if(retry==0 && vm.readStates[seed.id]!=null){loading=false;return@LaunchedEffect};loading=true;error=null;try{content=withContext(Dispatchers.IO){vm.repo.detail(seed)}}catch(e:Exception){error=e.message};loading=false}
 val blocks=remember(content.html){bodyBlocks(content.html)};val readingList=rememberLazyListState();val nearEnd by remember(blocks.size){derivedStateOf{(readingList.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0)>=blocks.size-2 && blocks.isNotEmpty()}};val urls=remember(blocks){blocks.filterIsInstance<BodyBlock.Image>().map{it.url}.distinct()}
 LaunchedEffect(nearEnd,content.bodyNext) {if(nearEnd && content.bodyNext!=null && !loading && !loadingMore && error==null){loadingMore=true;try{content=withContext(Dispatchers.IO){vm.repo.bodyPage(content)}}catch(e:Exception){error=e.message};loadingMore=false}}
 LazyColumn(state=readingList,contentPadding=LocalReaderInsets.current) {
  item {Column(Modifier.padding(18.dp)) {Text(content.title,fontSize=23.sp,lineHeight=32.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(20.dp));Row(Modifier.fillMaxWidth().clickable{if(content.authorId.isNotEmpty()) user(content.authorId)},verticalAlignment=Alignment.CenterVertically){AsyncImage(content.avatar,"作者头像",Modifier.size(38.dp).clip(CircleShape));Column(Modifier.padding(start=10.dp)){Text(content.author,fontWeight=FontWeight.SemiBold,fontSize=15.sp);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){StatBadge(R.drawable.ic_like,"${count(content.votes)} 赞同");StatBadge(R.drawable.ic_reply,"${count(content.comments)} 评论")}}}}}
  if(loading) item{Loading()}
  if(error!=null) item{ErrorView(error!!,{retry++},{web(content.url)})}
  if(content.truncated) item{Column{Notice("知乎向游客提供的正文可能不完整。完成知乎验证或登录后，返回重试以读取完整内容。");TextButton({web(content.url)},Modifier.padding(horizontal=12.dp)){Text("在知乎中继续阅读 / 验证")}}}
  itemsIndexed(blocks,key={i,_->"body:$i"}){_,block->BodyItem(block,size,web,{image=it})}
  if(!loading && blocks.isEmpty() && error==null) item{Notice("正文尚未返回，请打开知乎验证后重试。");TextButton({web(content.url)}){Text("打开原文")}}
  if(content.bodyNext!=null) item {TextButton({scope.launch {loadingMore=true;error=null;try{content=withContext(Dispatchers.IO){vm.repo.bodyPage(content)}}catch(e:Exception){error=e.message};loadingMore=false}},Modifier.fillMaxWidth(),enabled=!loadingMore){Text(if(loadingMore) "正在加载后续正文…" else "继续加载正文")}}
  item{Column(Modifier.padding(18.dp)){if(content.time>0) Text("更新于 ${SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(Date(content.time*1000))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);SaveActions(vm,content);TextButton({comments(content)},Modifier.fillMaxWidth()){Text("查看 ${count(content.comments)} 条评论")};Spacer(Modifier.height(20.dp))}}
 }
 if(image!=null && urls.isNotEmpty()) ImageViewer(urls,image!!){image=null}
}
@Composable fun MyScreen(vm: ReaderViewModel,version: Int,saved: (String)->Unit,settings: ()->Unit,web: (String)->Unit,accounts: (String)->Unit) {
 val account by vm.account.collectAsState()
 LaunchedEffect(version){vm.syncAccount()}
 LazyColumn(contentPadding=LocalReaderInsets.current) {
  item{Column(Modifier.fillMaxWidth().padding(24.dp)){
   val profile=account.profile
   Row(verticalAlignment=Alignment.CenterVertically) {
    if(profile!=null)AsyncImage(profile.avatar,"我的头像",Modifier.size(60.dp).clip(CircleShape))
    Column(Modifier.padding(start=if(profile!=null)16.dp else 0.dp)) {
     Text(profile?.name ?: if(account.hasSession) "知乎会话已保存" else "访客模式",fontSize=25.sp,fontWeight=FontWeight.Bold)
     Spacer(Modifier.height(8.dp))
     Text(profile?.headline?.takeIf{it.isNotBlank()} ?: if(account.loading) "正在同步账户资料…" else if(account.hasSession) "登录凭据保存在此设备" else "安静地阅读，留住有价值的内容",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
   }
   if(profile!=null){Spacer(Modifier.height(12.dp));Text("${count(profile.following)} 关注 · ${count(profile.followers)} 粉丝",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   if(account.error!=null){Spacer(Modifier.height(8.dp));Text(account.error!!,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Spacer(Modifier.height(12.dp));Row{TextButton({web("https://www.zhihu.com/signin")}){Text(if(account.hasSession) "管理知乎登录" else "登录知乎")};if(account.hasSession)TextButton({vm.syncAccount()}){Text("同步资料")}}
  }}
  item{Notice("知乎账户")}
  item{SettingRow("我的赞同","赞同过的回答与文章"){accounts("likes")}}
  item{SettingRow("评论与回复","收到的评论和回复通知"){accounts("comments")}}
  item{SettingRow("私信","会话与消息，只读"){accounts("inbox")}}
  item{Notice("本地收藏、历史与稍后阅读仅保存在此设备。")}
  items(listOf("收藏","浏览历史","稍后阅读")){label->SettingRow(label,"保存在本机"){saved(label)}}
  item{Spacer(Modifier.height(16.dp));SettingRow("外观与阅读设置","主题、字号、缓存、隐私",settings)}
  item{Column(Modifier.padding(20.dp)){Text("知乎Lite 0.1.8",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(5.dp));Text("第三方阅读客户端 · 无广告 · 无后台服务",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
 }
}
@Composable fun SettingRow(label: String,subtitle: String="",click: ()->Unit) {
 Column(Modifier.padding(horizontal=12.dp,vertical=4.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick=click).padding(horizontal=20.dp,vertical=15.dp)){Text(label,fontSize=16.sp);if(subtitle.isNotEmpty()){Spacer(Modifier.height(5.dp));Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
}
@Composable fun SavedScreen(vm: ReaderViewModel,label: String,open: (Content)->Unit) {
 val kind=when(label){"收藏"->"favorite";"稍后阅读"->"later";else->"history"};var list by remember{mutableStateOf<List<SavedContent>>(emptyList())};var reload by remember{mutableIntStateOf(0)};var confirm by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 LaunchedEffect(reload){list=withContext(Dispatchers.IO){vm.repo.db.saved().list(kind)}}
 LazyColumn(state=rememberLazyListState(),contentPadding=LocalReaderInsets.current) {
  item{Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("${list.size} 条内容",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton({confirm=true},enabled=list.isNotEmpty()){Text("清除全部")}}}
  if(list.isEmpty()) item{Notice("还没有${label}。阅读时可以收藏或加入稍后阅读。")}
  items(list,key={it.key}){s->Column{ContentRow(Content(s.contentId,s.contentType,s.title,author=s.author,questionId=s.url.substringAfter("question/","").substringBefore('/'))){open(Content(s.contentId,s.contentType,s.title,author=s.author,questionId=s.url.substringAfter("question/","").substringBefore('/')))};TextButton({scope.launch{withContext(Dispatchers.IO){vm.repo.db.saved().remove(s.key)};reload++}},Modifier.padding(start=8.dp)){Text("移除",fontSize=12.sp)}}}
 }
 if(confirm) AlertDialog({confirm=false},title={Text("清除${label}？")},text={Text("此操作将移除此设备中的全部${label}记录。")},confirmButton={TextButton({scope.launch{withContext(Dispatchers.IO){vm.repo.db.saved().clear(kind)};reload++;confirm=false}}){Text("清除")}},dismissButton={TextButton({confirm=false}){Text("取消")}})
}
@Composable fun Following(vm: ReaderViewModel,version: Int,open: (Content)->Unit,web: (String)->Unit) {
 val logged=remember(version){vm.repo.session.loggedIn()};var state by remember{mutableStateOf(LoadState())};var retry by remember{mutableIntStateOf(0)};val scope=rememberCoroutineScope()
 LaunchedEffect(version,retry){if(logged){state=state.copy(loading=true,error=null);try{val p=withContext(Dispatchers.IO){vm.repo.following()};state=LoadState(items=p.items,next=p.next)}catch(e:Exception){state=LoadState(error=e.message)}}}
 if(!logged) Column(Modifier.padding(LocalReaderInsets.current).fillMaxWidth().padding(28.dp)){Text("你关注的知识，在这里",fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Text("登录知乎后查看关注内容。首页、热榜和本地收藏可在访客模式使用。",lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(16.dp));Button({web("https://www.zhihu.com/signin")}){Text("登录知乎")}}
 else FeedScreen(state,false,{retry++},{scope.launch{state=state.copy(loading=true);try{val p=withContext(Dispatchers.IO){vm.repo.following(state.next)};state=LoadState(items=(state.items+p.items).distinctBy{it.id},next=p.next)}catch(e:Exception){state=state.copy(loading=false,error=e.message)}}},open,web)
}
@Composable fun SettingsScreen(vm: ReaderViewModel,theme: String,font: Int,setTheme: (String)->Unit,setFont: (Int)->Unit,sessionChanged: ()->Unit,web: (String)->Unit) {
 val scope=rememberCoroutineScope();var size by remember{mutableLongStateOf(0L)};var message by remember{mutableStateOf("")}
 LaunchedEffect(Unit){size=withContext(Dispatchers.IO){vm.repo.cacheSize()}}
 LazyColumn(contentPadding=LocalReaderInsets.current) {
  item{Notice("外观")}
  item{Row(Modifier.padding(12.dp)){listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach{(id,label)->TextButton({setTheme(id)}){Text(if(theme==id) "✓ $label" else label)}}}}
  item{Notice("阅读字号")}
  item{Row(Modifier.padding(12.dp)){listOf(15 to "小",17 to "标准",19 to "大",21 to "特大").forEach{(n,label)->TextButton({setFont(n)}){Text(if(font==n) "✓ $label" else label)}}};Text("知识值得被安静地阅读。",Modifier.padding(18.dp),fontSize=font.sp,lineHeight=(font*1.65).sp)}
  item{Notice("缓存与隐私")}
  item{SettingRow("清除内容缓存","%.2f MB · 保留收藏与历史".format(size/1048576.0)){scope.launch{withContext(Dispatchers.IO){vm.repo.clearCache()};size=0;message="内容缓存已清除"}}}
  item{SettingRow("退出知乎账户","清除本机 Cookie 与登录状态"){vm.repo.session.clear();sessionChanged();scope.launch(Dispatchers.IO){vm.repo.clearCache()};message="已退出知乎"}}
  if(message.isNotEmpty()) item{Notice(message)}
  item{Notice("Cookie 使用 Android Keystore 加密，仅保存在本机；不记录账号密码，不收集统计信息。此版本无法保证知乎限制接口在游客模式始终可用。")}
  item{SettingRow("关于知乎Lite","0.2.0 · 独立开发的第三方知乎阅读器"){web("https://www.zhihu.com/")}}
 }
}
@Composable fun SearchScreen(vm: ReaderViewModel,open: (Content)->Unit,web: (String)->Unit,user: (String)->Unit) {
 val context=LocalContext.current;val prefs=remember{context.getSharedPreferences("search",0)};val focus=remember{FocusRequester()}
 var query by rememberSaveable{mutableStateOf("")};var submitted by rememberSaveable{mutableStateOf("")};var type by rememberSaveable{mutableStateOf("general")};var state by remember{mutableStateOf(LoadState())};var retry by remember{mutableIntStateOf(0)};var history by remember{mutableStateOf(prefs.getString("history","")!!.split('\n').filter{it.isNotEmpty()})};val scope=rememberCoroutineScope()
 fun submit(q: String){if(q.isBlank())return;query=q.trim();submitted=query;history=(listOf(query)+history).distinct().take(20);prefs.edit().putString("history",history.joinToString("\n")).apply();retry++}
 LaunchedEffect(Unit){focus.requestFocus()}
 LaunchedEffect(submitted,type,retry){if(submitted.isNotEmpty()){state=LoadState(loading=true);try{val p=withContext(Dispatchers.IO){vm.repo.search(submitted,type)};state=LoadState(items=p.items,next=p.next)}catch(e:Exception){state=LoadState(error=e.message)}}}
 val insets=LocalReaderInsets.current
 Column(Modifier.padding(top=insets.calculateTopPadding())) {
  CompositionLocalProvider(LocalReaderInsets provides PaddingValues(bottom=insets.calculateBottomPadding())) {
  OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),singleLine=true,shape=RoundedCornerShape(26.dp),colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Blue.copy(alpha=.35f),unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedContainerColor=MaterialTheme.colorScheme.surface),placeholder={Text("搜索问题、回答、文章")},keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={submit(query)}),trailingIcon={IconButton({submit(query)}){Icon(Icons.Default.Search,"提交搜索")}})
  Row(Modifier.padding(horizontal=8.dp)){listOf("general" to "综合","question" to "问题","article" to "文章").forEach{(id,label)->TextButton({type=id}){Text(if(type==id) "✓ $label" else label)}}}
  LazyColumn(state=rememberLazyListState(),contentPadding=LocalReaderInsets.current) {
   if(submitted.isEmpty()) {
    item{Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){Text("搜索历史",Modifier.weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton({history=emptyList();prefs.edit().remove("history").apply()}){Text("清除")}}}
    items(history){q->SettingRow(q){submit(q)}}
   }else{
    if(state.loading) item{Loading()}
    if(state.error!=null) item{ErrorView(state.error!!,{retry++},{web("https://www.zhihu.com/search?q=${enc(submitted)}")})}
    items(state.items,key={"${it.type}:${it.id}"}){c->ContentRow(c){open(c)}}
    if(!state.loading && state.error==null && state.items.isEmpty()) item{Notice("没有找到相关内容。")}
    if(state.next!=null) item{TextButton({scope.launch{state=state.copy(loading=true);try{val p=withContext(Dispatchers.IO){vm.repo.search(submitted,type,state.next)};state=LoadState(items=(state.items+p.items).distinctBy{it.id},next=p.next)}catch(e:Exception){state=state.copy(loading=false,error=e.message)}}},Modifier.fillMaxWidth(),enabled=!state.loading){Text("更多结果")}}
   }
  }
 }
}
}
@Composable fun UserScreen(vm: ReaderViewModel,id: String,web: (String)->Unit) {
 var json by remember{mutableStateOf<JSONObject?>(null)};var error by remember{mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)};var loading by remember{mutableStateOf(true)}
 LaunchedEffect(retry){loading=true;error=null;try{json=withContext(Dispatchers.IO){vm.repo.user(id)}}catch(e:Exception){error=e.message};loading=false}
 LazyColumn(contentPadding=LocalReaderInsets.current) {
  if(loading) item{Loading()}
  if(error!=null) item{ErrorView(error!!,{retry++},{web("https://www.zhihu.com/people/$id")})}
  json?.let {j->item{Column(Modifier.fillMaxWidth().padding(24.dp)){AsyncImage(j.optString("avatar_url"),"作者头像",Modifier.size(72.dp).clip(CircleShape));Spacer(Modifier.height(16.dp));Text(j.optString("name"),fontSize=25.sp,fontWeight=FontWeight.Bold);Text(j.optString("headline"),Modifier.padding(top=10.dp),lineHeight=23.sp);Spacer(Modifier.height(20.dp));Text("${count(j.optLong("follower_count"))} 粉丝   ${count(j.optLong("voteup_count"))} 获赞");Text("${j.optInt("answer_count")} 回答   ${j.optInt("articles_count")} 文章",Modifier.padding(top=8.dp));TextButton({web("https://www.zhihu.com/people/$id/answers")}){Text("查看作者的回答与文章")}}}}
 }
}
@Composable fun StatBadge(icon: Int,label: String) {
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
  Icon(painterResource(icon),null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  Text(label,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 }
}
@Composable fun CommentBody(j: JSONObject) {
 val author=j.optJSONObject("author")
 Row(verticalAlignment=Alignment.CenterVertically) {
  val avatar=author?.optString("avatar_url").orEmpty()
  if(avatar.isNotBlank())AsyncImage(avatar,"评论用户头像",Modifier.size(36.dp).clip(CircleShape))
  Column(Modifier.weight(1f).padding(start=if(avatar.isNotBlank())10.dp else 0.dp,end=8.dp)) {
   Text(author?.optString("name").orEmpty(),fontSize=14.sp,fontWeight=FontWeight.SemiBold)
   val replyTo=j.optJSONObject("reply_to_author")?.optString("name").orEmpty()
   if(replyTo.isNotBlank())Text("回复 $replyTo",Modifier.padding(top=4.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
  }
  StatBadge(R.drawable.ic_like,"${count(j.optLong("like_count"))} 赞")
 }
 Text(plain(j.optString("content")),Modifier.padding(top=6.dp),fontSize=15.sp,lineHeight=24.sp)
}
@Composable fun CommentsScreen(vm: ReaderViewModel,c: Content,web: (String)->Unit,replies: (JSONObject)->Unit,root: JSONObject?=null) {
 var entries by remember{mutableStateOf<List<JSONObject>>(emptyList())}
 var next by remember{mutableStateOf<String?>(null)};var error by remember{mutableStateOf<String?>(null)}
 var retry by remember{mutableIntStateOf(0)};var loading by remember{mutableStateOf(true)};val scope=rememberCoroutineScope()
 suspend fun load(more: Boolean=false) {
  loading=true;error=null
  val requested=if(more)next else null
  try {
   val j=withContext(Dispatchers.IO){if(root==null)vm.repo.comments(c,requested) else vm.repo.replies(root.getString("id"),requested)}
   val a=j.optJSONArray("data") ?: throw ApiError(200,"知乎没有返回有效的评论列表")
   val page=(0 until a.length()).mapNotNull{a.optJSONObject(it)}
   entries=(if(more)entries+page else page).distinctBy{it.optString("id")}
   val following=pagingNext(j)
   next=following?.takeUnless{it==requested}
   if(following!=null && following==requested)error="知乎返回了重复的分页，请稍后重试。"
  }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}
  finally{loading=false}
 }
 LaunchedEffect(retry){load()}
 LazyColumn(state=rememberLazyListState(),contentPadding=LocalReaderInsets.current) {
  if(root!=null)item{Column(Modifier.fillMaxWidth().padding(16.dp)){Text("原评论",fontSize=12.sp,color=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(8.dp));CommentBody(root)};HorizontalDivider();Notice("已加载 ${entries.size} 条回复")}
  itemsIndexed(entries,key={_,it->it.optString("id")}){index,j->
   val shape=RoundedCornerShape(topStart=if(index==0)24.dp else 0.dp,topEnd=if(index==0)24.dp else 0.dp,bottomStart=if(index==entries.lastIndex)24.dp else 0.dp,bottomEnd=if(index==entries.lastIndex)24.dp else 0.dp)
   Column(Modifier.padding(horizontal=12.dp).fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)) {
   Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=16.dp)){
    CommentBody(j)
    if(root==null){
     val children=j.optJSONArray("child_comments")
     if(children!=null)for(i in 0 until children.length()){
      val child=children.optJSONObject(i) ?: continue
      Column(Modifier.padding(start=12.dp,top=12.dp,bottom=4.dp)){CommentBody(child)}
     }
     val total=maxOf(j.optInt("child_comment_count"),children?.length() ?: 0)
     if(total>0)TextButton({replies(j)},contentPadding=PaddingValues(horizontal=0.dp,vertical=4.dp)){Icon(painterResource(R.drawable.ic_reply),null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text("查看全部 $total 条回复")}
    }
   };if(index<entries.lastIndex)HorizontalDivider(Modifier.padding(start=16.dp,end=16.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.7f))
   }
  }
  if(error!=null)item{ErrorView(error!!,{if(entries.isEmpty())retry++ else scope.launch{load(next!=null)}},{web(c.url)})}
  if(loading)item{Loading()}
  if(!loading && error==null && entries.isEmpty())item{Notice(if(root==null) "暂无评论。" else "知乎暂未返回可查看的回复。")}
  if(next!=null)item{TextButton({scope.launch{load(true)}},Modifier.fillMaxWidth(),enabled=!loading){Text(if(root==null) "更多评论" else "加载更多回复")}}
 }
}
@Suppress("SetJavaScriptEnabled")
@Composable fun WebScreen(url: String,done: ()->Unit,back: ()->Unit,onSessionChanged: ()->Unit) {
 val context=LocalContext.current;var view by remember{mutableStateOf<WebView?>(null)};var loading by remember{mutableStateOf(true)};var title by remember{mutableStateOf("知乎网页")};var failure by remember{mutableStateOf<String?>(null)};val session=remember{SessionStore(context)};var notifiedCookie by remember{mutableStateOf(session.read())}
 DisposableEffect(Unit){onDispose{session.capture();view?.stopLoading();view?.destroy();view=null;CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush()}}
 Column(Modifier.fillMaxSize().padding(LocalReaderInsets.current)) {
  Row(Modifier.fillMaxWidth().height(56.dp),verticalAlignment=Alignment.CenterVertically){IconButton({if(view?.canGoBack()==true)view?.goBack() else back()}){Icon(Icons.Default.ArrowBack,"返回")};Text(title,Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=16.sp);TextButton(done){Text("完成验证")}}
  Notice("知乎网页验证 / 原文。账号密码由知乎页面处理；完成后返回原生阅读页。")
  if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
  if(failure!=null) Notice(failure!!)
  AndroidView(factory={WebView(context).apply{
   view=this;settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;CookieManager.getInstance().setAcceptThirdPartyCookies(this,false)
   webChromeClient=object:WebChromeClient(){override fun onReceivedTitle(v: WebView?,t: String?){title=t ?: "知乎网页"}}
   webViewClient=object:WebViewClient(){
    override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {val uri=r.url;val host=uri.host.orEmpty();if(uri.scheme=="https" && (host=="zhihu.com" || host.endsWith(".zhihu.com"))) return false;if(uri.scheme=="http")return true;try{context.startActivity(Intent(Intent.ACTION_VIEW,uri))}catch(_:Exception){failure="此链接无法打开"};return true}
    override fun onPageStarted(v:WebView?,u:String?,icon:android.graphics.Bitmap?){loading=true;failure=null}
    override fun onPageFinished(v:WebView?,u:String?){loading=false;if(session.capture() && session.read()!=notifiedCookie){notifiedCookie=session.read();onSessionChanged()}}
    override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){if(r.isForMainFrame){loading=false;failure="网页加载失败：${e.description}"}}
   }
   val savedCookie=session.read();if(savedCookie.isNotEmpty())CookieManager.getInstance().setCookie("https://www.zhihu.com/",savedCookie){loadUrl(url)} else loadUrl(url)
  }},modifier=Modifier.weight(1f).fillMaxWidth())
 }
}
