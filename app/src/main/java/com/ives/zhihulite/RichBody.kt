package com.ives.zhihulite

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.net.URL

sealed class BodyBlock {
 data class Text(val value: AnnotatedString, val heading: Boolean=false, val quote: Boolean=false): BodyBlock()
 data class Image(val url: String): BodyBlock()
 data class Code(val value: String): BodyBlock()
 data class Table(val rows: List<List<String>>): BodyBlock()
}
fun richText(node: Node): AnnotatedString = buildAnnotatedString {
 fun visit(n: Node) {
  if(n is TextNode) { append(n.text()); return }
  if(n !is Element) return
  when(n.tagName()) {
   "script", "style", "iframe" -> return
   "br" -> { append("\n"); return }
   "img" -> return
  }
  val style = when(n.tagName()) {
   "strong", "b" -> SpanStyle(fontWeight=FontWeight.Bold)
   "em", "i" -> SpanStyle(fontStyle=FontStyle.Italic)
   "code" -> SpanStyle(fontFamily=FontFamily.Monospace)
   "a" -> SpanStyle(color=Color(0xFF0084FF))
   else -> SpanStyle()
  }
  if(n.tagName()=="a") pushStringAnnotation("URL", n.absUrl("href"))
  pushStyle(style); n.childNodes().forEach { visit(it) }; pop()
  if(n.tagName()=="a") pop()
 }
 visit(node)
}
fun bodyBlocks(html: String): List<BodyBlock> {
 val body = Jsoup.parse(html,"https://www.zhihu.com").body()
 val out = mutableListOf<BodyBlock>()
 fun image(e: Element) {
  val u = sequenceOf(e.attr("data-original"),e.attr("data-actualsrc"),e.attr("src")).firstOrNull { it.startsWith("https://") || it.startsWith("//") }.orEmpty()
  if(u.isNotEmpty()) out.add(BodyBlock.Image(if(u.startsWith("//")) "https:$u" else u))
 }
 fun visit(e: Element) {
  when(e.tagName()) {
   "script","style","iframe" -> return
   "img" -> image(e)
   "pre" -> out.add(BodyBlock.Code(e.wholeText()))
   "table" -> out.add(BodyBlock.Table(e.select("tr").map { row -> row.children().map {it.text()} }))
   "ul","ol" -> e.children().forEachIndexed { index, li ->
    out.add(BodyBlock.Text(buildAnnotatedString { append(if(e.tagName()=="ol") "${index+1}. " else "• "); append(richText(li)) })); li.select("img").forEach { image(it) }
   }
   "p","h1","h2","h3","h4","blockquote" -> {
    val text=richText(e)
    if(text.isNotBlank()) out.add(BodyBlock.Text(text,e.tagName().startsWith("h"),e.tagName()=="blockquote"))
    e.select("img").forEach { image(it) }
   }
   else -> if(e.children().isEmpty()) { val text=richText(e); if(text.isNotBlank()) out.add(BodyBlock.Text(text)) } else e.children().forEach { visit(it) }
  }
 }
 body.children().forEach { visit(it) }
 if(out.isEmpty() && body.text().isNotBlank()) out.add(BodyBlock.Text(AnnotatedString(body.text())))
 return out
}
@Composable fun BodyItem(block: BodyBlock, size: Int, onLink: (String)->Unit, onImage: (String)->Unit) {
 when(block) {
  is BodyBlock.Text -> {
   val color=MaterialTheme.colorScheme.onSurface
   ClickableText(block.value, modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=7.dp).then(if(block.quote) Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp) else Modifier),
    style=TextStyle(color=color,fontSize=(if(block.heading) size+4 else size).sp,lineHeight=(size*1.65).sp,fontWeight=if(block.heading) FontWeight.Bold else FontWeight.Normal),
    onClick={ pos -> block.value.getStringAnnotations("URL",pos,pos).firstOrNull()?.let { onLink(it.item) } })
  }
  is BodyBlock.Image -> AsyncImage(block.url,"回答图片，点击查看", modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).heightIn(min=120.dp,max=420.dp).clickable { onImage(block.url) })
  is BodyBlock.Table -> Column(Modifier.padding(18.dp).horizontalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceVariant)) {block.rows.forEach {row -> Row {row.forEach {cell->Text(cell,Modifier.width(180.dp).padding(10.dp),fontSize=14.sp,lineHeight=21.sp)}};HorizontalDivider(thickness=.5.dp)}}
  is BodyBlock.Code -> {
   val clipboard=LocalClipboardManager.current
   Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
    TextButton({ clipboard.setText(AnnotatedString(block.value)) },Modifier.align(Alignment.End)) { Text("复制代码") }
    Text(block.value,Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),fontFamily=FontFamily.Monospace,fontSize=14.sp,lineHeight=21.sp,softWrap=false)
   }
  }
 }
}
@Composable fun ImageViewer(urls: List<String>, initial: String, close: ()->Unit) {
 var index by remember { mutableIntStateOf(urls.indexOf(initial).coerceAtLeast(0)) }
 var zoom by remember(index) { mutableFloatStateOf(1f) }
 var x by remember(index) { mutableFloatStateOf(0f) }; var y by remember(index) { mutableFloatStateOf(0f) }
 var message by remember { mutableStateOf<String?>(null) }
 val context=LocalContext.current; val scope=rememberCoroutineScope()
 Dialog(close,DialogProperties(usePlatformDefaultWidth=false)) {
  Box(Modifier.fillMaxSize().background(Color.Black)) {
   AsyncImage(urls[index],"图片 ${index+1}",Modifier.fillMaxSize().pointerInput(index) { detectTransformGestures { _,pan,z,_ -> zoom=(zoom*z).coerceIn(1f,5f);x+=pan.x;y+=pan.y } }.pointerInput(index) { detectTapGestures(onDoubleTap={zoom=if(zoom>1f) 1f else 2.5f;x=0f;y=0f}) }.graphicsLayer { scaleX=zoom;scaleY=zoom;translationX=x;translationY=y })
   Row(Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top=20.dp),horizontalArrangement=Arrangement.SpaceBetween) {
    TextButton(close) { Text("关闭",color=Color.White) }; Text("${index+1} / ${urls.size}",Modifier.padding(16.dp),color=Color.White)
    TextButton({ scope.launch {
     message=withContext(Dispatchers.IO) {
      runCatching {
       val host=URL(urls[index]).host; require(host.endsWith(".zhimg.com"))
       val values=ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"ZhihuLite-${System.currentTimeMillis()}.jpg");put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/ZhihuLite");put(MediaStore.Images.Media.IS_PENDING,1) }
       if(android.os.Build.VERSION.SDK_INT < 29) return@runCatching "请使用系统分享保存图片"
       val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("无法保存")
       try { URL(urls[index]).openConnection().apply{connectTimeout=12000;readTimeout=16000}.getInputStream().use { input -> context.contentResolver.openOutputStream(uri)!!.use { input.copyTo(it) } }; values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.contentResolver.update(uri,values,null,null) }
       catch(e: Exception) { context.contentResolver.delete(uri,null,null);throw e }
       "已保存到相册"
      }.getOrDefault("保存失败，请检查网络")
     }
    } }) { Text("保存",color=Color.White) }
   }
   Row(Modifier.align(Alignment.BottomCenter).padding(bottom=30.dp),verticalAlignment=Alignment.CenterVertically) {
    TextButton({if(index>0) index--},enabled=index>0) { Text("上一张",color=Color.White) }
    Text(message ?: "双指缩放 · 双击放大",color=Color.White,fontSize=12.sp)
    TextButton({if(index<urls.lastIndex) index++},enabled=index<urls.lastIndex) { Text("下一张",color=Color.White) }
   }
  }
 }
}
