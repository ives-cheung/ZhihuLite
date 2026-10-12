import os
"""Reply navigation verification; UI content stays in memory, only booleans are emitted."""
import subprocess,xml.etree.ElementTree as E,re,json
S=os.environ.get("ANDROID_SERIAL", "")
assert S, "Set ANDROID_SERIAL to your adb device serial"
def adb(*a):return subprocess.check_output(['adb','-s',S,*a])
def nodes():
 adb('shell','uiautomator','dump','/sdcard/replies-ui.xml')
 ns=list(E.fromstring(adb('shell','cat','/sdcard/replies-ui.xml')).iter('node'))
 assert any(n.get('package')=='com.ives.zhihulite' for n in ns),'Device switched away from ZhihuLite'
 return ns
def txt(n):return n.get('text','')
def tap(n):
 a=list(map(int,re.findall(r'\d+',n.get('bounds'))));adb('shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2))
def find(ns,p):return next((n for n in ns if p(txt(n))),None)
def swipe():adb('shell','input','swipe','600','2240','600','700','200')
adb('shell','am','force-stop','com.ives.zhihulite')
adb('shell','am','start','-n','com.ives.zhihulite/.MainActivity');ns=nodes()
for _ in range(5):
 rows=[n for n in ns if n.get('clickable')=='true' and len(txt(n))>12]
 if rows:break
 ns=nodes()
# Compose groups can expose clickable ancestors without their own text.
if not rows:
 rows=[n for n in ns if n.get('clickable')=='true' and len(list(n))>=2 and int(re.findall(r'\d+',n.get('bounds'))[1])>250]
assert rows,'Home content absent';tap(rows[0]);ns=nodes()
for _ in range(50):
 button=find(ns,lambda t:re.fullmatch(r'查看 [\d.万]+ 条评论',t)!=None)
 if button:break
 swipe();ns=nodes()
assert button,'Comment button absent';tap(button);ns=nodes()
for _ in range(12):
 button=find(ns,lambda t:re.fullmatch(r'查看全部 \d+ 条回复',t)!=None)
 if button:break
 swipe();ns=nodes()
assert button,'Reply thread absent';tap(button);ns=nodes()
for _ in range(5):
 if find(ns,lambda t:re.fullmatch(r'已加载 [2-9]\d* 条回复',t)!=None or re.fullmatch(r'已加载 1\d+ 条回复',t)!=None):break
 ns=nodes()
assert find(ns,lambda t:t=='全部回复'),'Reply screen absent'
assert find(ns,lambda t:re.fullmatch(r'已加载 \d+ 条回复',t)!=None),'Reply count absent'
assert not find(ns,lambda t:t=='暂时无法加载'),'Reply load failed'
result={'reply_screen_opened':True,'native_reply_count_shown':True}
previous=None;unchanged=0
for _ in range(20):
 fingerprint=[(txt(n),n.get('bounds')) for n in ns]
 unchanged=unchanged+1 if fingerprint==previous else 0
 previous=fingerprint
 if unchanged>=2:break
 more=find(ns,lambda t:t=='加载更多回复')
 if more:break
 swipe();ns=nodes()
if more:
 tap(more);ns=nodes();result['load_more_button_worked']=not bool(find(ns,lambda t:t=='暂时无法加载'))
adb('shell','rm','/sdcard/replies-ui.xml')
print(json.dumps(result,indent=2))
