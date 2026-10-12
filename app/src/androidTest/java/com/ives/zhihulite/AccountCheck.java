package com.ives.zhihulite;
import android.app.Instrumentation;
import android.app.Activity;
import android.os.Bundle;
import org.json.JSONObject;
/** Uses the actual device session; reports only booleans/status, never credentials. */
public class AccountCheck extends Instrumentation {
 private void probe(ZhihuRepository repo,String key,String url,Bundle result) {
  try {
   JSONObject j=repo.getApi().get(url);result.putInt(key+"_http_status",200);
   org.json.JSONArray a=j.optJSONArray("data");if(a==null && key.equals("chat") && j.optJSONObject("data")!=null)a=j.optJSONObject("data").optJSONArray("messages");result.putInt(key+"_count",a==null?0:a.length());
   if(a!=null && a.length()>0) {
    JSONObject first=a.optJSONObject(0);result.putString(key+"_keys",first.names().toString());
    if(key.equals("activities")) {java.util.Set<String> types=new java.util.HashSet<>();for(int i=0;i<a.length();i++){JSONObject event=a.optJSONObject(i);types.add(event.optString("verb"));}result.putString("activity_types",String.join(",",types));}
    if(key.equals("notifications")){JSONObject content=first.optJSONObject("content");result.putString("notification_content_type",first.opt("content").getClass().getSimpleName());if(content!=null)result.putString("notification_content_keys",content.names().toString());JSONObject extra=content==null?null:content.optJSONObject("extend");if(extra!=null)result.putString("notification_extend_keys",extra.names().toString());java.util.Set<String> types=new java.util.HashSet<>();for(int i=0;i<a.length();i++)types.add(a.optJSONObject(i).optJSONObject("content").optString("verb"));result.putString("notification_types",String.join(",",types));JSONObject target=first.optJSONObject("target");if(target!=null)result.putString("notification_target_keys",target.names().toString());}
    if(key.equals("inbox")){JSONObject peer=first.optJSONObject("participant");if(peer!=null)probe(repo,"chat","https://www.zhihu.com/api/v4/chat?sender_id="+peer.optString("id")+"&limit=20",result);}
   }
  } catch(Exception e){if(e instanceof ApiError)result.putInt(key+"_http_status",((ApiError)e).getStatus());else result.putString(key+"_error_type",e.getClass().getSimpleName());}
 }
 private boolean repliesOnly;private boolean feedOnly;
 @Override public void onCreate(Bundle args) { super.onCreate(args);feedOnly=args!=null && args.getString("feed_only", "false").equals("true");repliesOnly=args!=null && args.getString("replies_only", "false").equals("true");start(); }
 private void checkReplies(Bundle result) throws Exception {
  ZhihuRepository repo=new ZhihuRepository(getTargetContext());
  result.putString("probe_stage","feed");
  java.util.List<Content> seeds=repo.feed(null,false).getItems();
  for(Content c:seeds) {
   if(!c.getType().equals("answer"))continue;
   result.putString("probe_stage","root_comments");JSONObject roots;try{roots=repo.comments(c,null);}catch(Exception e){continue;}org.json.JSONArray list=roots.getJSONArray("data");
   for(int i=0;i<list.length();i++) {
    JSONObject root=list.getJSONObject(i);if(root.optInt("child_comment_count")<=2)continue;
    result.putString("probe_stage","reply_first_page");JSONObject first=repo.replies(root.getString("id"),null);
    org.json.JSONArray replies=first.getJSONArray("data");
    if(replies.length()<=1)continue;
    result.putInt("root_preview_count",root.optJSONArray("child_comments")==null?0:root.getJSONArray("child_comments").length());
    result.putInt("reply_first_page_count",replies.length());
    String next=StructuredContentKt.pagingNext(first);
    if(next==null){first=repo.replies(root.getString("id"),"https://www.zhihu.com/api/v4/comment_v5/comment/"+root.getString("id")+"/child_comment?order_by=ts&limit=2");replies=first.getJSONArray("data");next=StructuredContentKt.pagingNext(first);result.putInt("pagination_page_size",replies.length());}
    if(next==null)continue;
    result.putString("probe_stage","reply_second_page");JSONObject second=repo.replies(root.getString("id"),next);org.json.JSONArray later=second.getJSONArray("data");
    java.util.Set<String> unique=new java.util.HashSet<>();for(int k=0;k<replies.length();k++)unique.add(replies.getJSONObject(k).getString("id"));int previous=unique.size();for(int k=0;k<later.length();k++)unique.add(later.getJSONObject(k).getString("id"));
    if(unique.size()<=previous)throw new AssertionError("Reply pagination repeated");
    result.putInt("reply_second_page_count",later.length());result.putInt("unique_replies_loaded",unique.size());result.putBoolean("reply_pagination_passed",true);return;
   }
  }
  throw new AssertionError("No multi-page public reply sample found");
 }
 @Override public void onStart() {
  Bundle result=new Bundle();
  try {
   if(feedOnly){ZhihuRepository repo=new ZhihuRepository(getTargetContext());
    result.putBoolean("authenticated_session",repo.getSession().loggedIn());
    Page page=repo.feed(null,true);result.putInt("account_feed_parsed",page.getItems().size());result.putBoolean("account_feed_labeled",page.getNotice().equals("账号推荐"));
    if(page.getItems().isEmpty())throw new AssertionError("No readable account feed items");
    result.putBoolean("account_feed_has_next",page.getNext()!=null);
    if(page.getNext()!=null){Page more=repo.feed(page.getNext(),true);result.putInt("account_feed_next_count",more.getItems().size());java.util.Set<String> ids=new java.util.HashSet<>();for(Content c:page.getItems())ids.add(c.getType()+":"+c.getId());int previous=ids.size();for(Content c:more.getItems())ids.add(c.getType()+":"+c.getId());result.putBoolean("account_feed_next_has_new_items",ids.size()>previous);}

    probe(repo,"recommend_web","https://www.zhihu.com/api/v3/feed/topstory/recommend?desktop=true&limit=20",result);
    probe(repo,"recommend_app","https://api.zhihu.com/topstory/recommend?limit=20",result);
    finish(Activity.RESULT_OK,result);return;}
   if(repliesOnly){checkReplies(result);finish(Activity.RESULT_OK,result);return;}
   if(!SessionStoreKt.hasAuthenticationCookie("d_c0=guest; z_c0=example") ||
      !SessionStoreKt.hasAuthenticationCookie("z_c0=example; d_c0=guest") ||
      SessionStoreKt.hasAuthenticationCookie("d_c0=guest; z_c0=") ||
      SessionStoreKt.hasAuthenticationCookie("z_c0=deleted")) throw new AssertionError("Cookie parsing regression");
   result.putBoolean("cookie_parser_passed",true);
   SessionStore store=new SessionStore(getTargetContext());
   runOnMainSync(()->store.capture());
   result.putBoolean("authenticated_cookie_saved",store.loggedIn());
   if(store.loggedIn()) {
    try {ZhihuRepository repo=new ZhihuRepository(getTargetContext());AccountProfile profile=repo.me();result.putBoolean("profile_synced",!profile.getName().isEmpty());result.putBoolean("avatar_available",!profile.getAvatar().isEmpty());
     result.putInt("liked_items_parsed",AccountScreensKt.accountPage(repo,"likes",null).getItems().size());
     result.putInt("reply_items_parsed",AccountScreensKt.accountPage(repo,"comments",null).getItems().size());
     result.putInt("inbox_items_parsed",AccountScreensKt.accountPage(repo,"inbox",null).getItems().size());
     probe(repo,"inbox","https://www.zhihu.com/api/v4/inbox",result);
     probe(repo,"activities","https://www.zhihu.com/api/v3/moments/"+profile.getId()+"/activities?limit=20&offset=0",result);
     probe(repo,"comments","https://www.zhihu.com/api/v4/notifications/v2/recent?limit=20&entry_name=default",result);
     probe(repo,"notifications","https://www.zhihu.com/api/v4/notifications/v2/recent?limit=20&entry_name=all",result);}
    catch(Exception e){if(e instanceof ApiError)result.putInt("profile_http_status",((ApiError)e).getStatus());else result.putString("profile_error_type",e.getClass().getSimpleName());}
    SessionStore reopened=new SessionStore(getTargetContext());result.putBoolean("session_restored",reopened.loggedIn());result.putBoolean("profile_cached",reopened.profile()!=null);
   }
   finish(Activity.RESULT_OK,result);
  } catch(Throwable e) {result.putString("failure_type",e.getClass().getSimpleName());finish(Activity.RESULT_CANCELED,result);}
 }
}
