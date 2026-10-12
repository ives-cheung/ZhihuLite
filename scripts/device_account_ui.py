import os
"""Validate native account routes; emit no usernames, message text, or cookies."""
import subprocess,xml.etree.ElementTree as ET,re,json
S=os.environ.get("ANDROID_SERIAL", "")
assert S, "Set ANDROID_SERIAL to your adb device serial"
def adb(*a):return subprocess.check_output(['adb','-s',S,*a])
def nodes():
 adb('shell','uiautomator','dump','/sdcard/account-ui.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/account-ui.xml')).iter('node'))
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def tap(n):
 x,y,z,w=bounds(n);adb('shell','input','tap',str((x+z)//2),str((y+w)//2))
def text(n):return n.get('text','')
def find(ns,t):return next((n for n in ns if text(n)==t),None)
def scroll_find(t):
 adb('shell','input','swipe','600','900','600','2250','300')
 ns=nodes()
 for _ in range(4):
  n=find(ns,t)
  if n is not None:return n
  adb('shell','input','swipe','600','2150','600','1000','300');ns=nodes()
 raise AssertionError('Account menu missing: '+t)
def settled():
 ns=nodes()
 for _ in range(3):
  if not any('Progress' in n.get('class','') for n in ns):break
  ns=nodes()
 return ns
adb('shell','am','force-stop','com.ives.zhihulite');adb('shell','am','start','-n','com.ives.zhihulite/.MainActivity');ns=nodes()
tap([n for n in ns if text(n)=='我的'][-1]);ns=settled()
assert any(n.get('content-desc')=='我的头像' for n in ns),'Account avatar not shown'
assert not any('资料同步失败' in text(n) for n in ns),'Account sync error'
result={'profile_restored_in_ui':True}
for label,expected in [('我的赞同','likes'),('评论与回复','comments'),('私信','inbox')]:
 tap(scroll_find(label));ns=settled();assert find(ns,label) is not None
 assert not any(text(n)=='暂时无法加载' for n in ns),'Account endpoint error'
 if expected=='likes':assert any(re.search(r'\d.*赞同',text(n)) for n in ns),'Liked records not rendered'
 if expected=='comments':assert any('评论了你的' in text(n) or '回复了' in text(n) for n in ns),'Reply notification not rendered'
 if expected=='inbox':
  rows=[n for n in ns if n.get('clickable')=='true' and bounds(n)[2]-bounds(n)[0]>800 and bounds(n)[3]-bounds(n)[1]>150]
  assert rows,'Inbox not rendered';tap(rows[0]);ns=settled()
  assert find(ns,'最近消息 · 只读') is not None,'Chat did not open'
  assert not any(text(n)=='暂时无法加载' for n in ns),'Chat load error'
  result['chat_route_rendered']=True;adb('shell','input','keyevent','4');ns=nodes()
 result[expected+'_route_rendered']=True
 adb('shell','input','keyevent','4');ns=nodes()
adb('shell','rm','/sdcard/account-ui.xml')
print(json.dumps(result,indent=2))
