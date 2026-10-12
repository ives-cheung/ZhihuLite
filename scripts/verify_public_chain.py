"""Live, unauthenticated Zhihu contract check. No fixtures or fabricated records."""
import urllib.request,json,datetime,sys
HEAD={'User-Agent':'com.zhihu.android/Futureve/11.2.0','x-api-version':'3.0.93','x-page-id':'172'}
def get(url):
 assert url.startswith(('https://api.zhihu.com/','https://www.zhihu.com/'))
 with urllib.request.urlopen(urllib.request.Request(url,headers=HEAD),timeout=20) as r:
  assert r.status==200
  return json.load(r)
def paging(j):
 p=j.get('paging',{})
 return json.loads(p) if isinstance(p,str) else p
def next_url(j):
 p=paging(j)
 return None if p.get('is_end',True) else p.get('next','').replace('\\u0026','&')
hot=get('https://api.zhihu.com/topstory/hot-lists/total?limit=50')
guest=get('https://www.zhihu.com/api/v3/explore/guest/feeds?limit=15&ws_qiangzhisafe=0')
assert hot.get('data') and guest.get('data')
question=hot['data'][0]['target'];qid=question['link']['url'].rsplit('/',1)[-1]
seed=next(x['target'] for x in guest['data'] if x.get('target',{}).get('type')=='answer' and str(x['target'].get('question',{}).get('id'))!=qid)
url=f'https://api.zhihu.com/next-render?id={seed["id"]}&type=answer&context_expand=1&is_native=0&scenes=question_feed&collection_id={qid}&collection_type=question&question_feed_session_id=&question_feed_cursor='
answers=get(url)
a=[x for x in answers.get('data',[]) if x.get('type')=='answer']
assert a and all(str(x.get('question',{}).get('id'))==qid for x in a),'Question identity mismatch'
ids=[str(x['id']) for x in a]
assert len(ids)==len(set(ids))
result={'checked_at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'hot_count':len(hot['data']),'guest_count':len(guest['data']),'question_id':qid,'question_title':question['title_area']['text'],'answer_count_first_page':len(a),'answer_paging_available':bool(next_url(answers)),'bodies':[]}
# Check two actual answer bodies, preserving server continuation URLs.
for answer in a[:2]:
 aid=str(answer['id']);body=get(f'https://api.zhihu.com/next-content-render?offset=0&url_token={aid}&content_type=answer')
 segments=body.get('segments',[]);pages=1;seen=set();n=next_url(body)
 while n and pages<10:
  assert n not in seen,'Repeated body paging URL';seen.add(n)
  body=get(n);segments+=body.get('segments',[]);pages+=1;n=next_url(body)
 assert segments,'Empty body'
 entry={'answer_id':aid,'body_pages':pages,'segments':len(segments),'reached_end':n is None,'images':sum(s.get('type')=='image' for s in segments)}
 img=next((s['image']['urls'][0] for s in segments if s.get('type')=='image' and s.get('image',{}).get('urls')),None)
 if img:
  assert urllib.parse.urlparse(img).hostname.endswith('.zhimg.com')
  with urllib.request.urlopen(img,timeout=20) as r:entry['image_http_status']=r.status;assert r.status==200;assert r.headers.get('Content-Type','').startswith('image/');r.read(1024)
 result['bodies'].append(entry)
if next_url(answers):
 page2=get(next_url(answers));second=[x for x in page2.get('data',[]) if x.get('type')=='answer']
 assert all(str(x.get('question',{}).get('id'))==qid for x in second)
 result['answer_count_second_page']=len(second)
print(json.dumps(result,ensure_ascii=False,indent=2))
