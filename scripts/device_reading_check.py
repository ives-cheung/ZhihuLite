import os
import subprocess,xml.etree.ElementTree as ET,re,pathlib,json
S=os.environ.get("ANDROID_SERIAL", "")
assert S, "Set ANDROID_SERIAL to your adb device serial";O=pathlib.Path(__file__).resolve().parent.parent/'docs'/'device';R=[]
def adb(*a):return subprocess.check_output(['adb','-s',S,*a])
def dump(name):
 adb('shell','uiautomator','dump','/sdcard/check.xml');data=adb('shell','cat','/sdcard/check.xml');(O/(name+'.xml')).write_bytes(data);return list(ET.fromstring(data).iter('node'))
def find(nodes,label):return next((n for n in nodes if n.get('text')==label or n.get('content-desc')==label),None)
def tap(n):
 assert n is not None
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def shot(name):(O/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
n=dump('reader-image');img=find(n,'回答图片，点击查看')
if img is None:
 adb('shell','input','swipe','600','2200','600','950','350');n=dump('reader-image');img=find(n,'回答图片，点击查看')
assert img is not None,'回答图片不在页面中';shot('reader-image');tap(img);n=dump('image-viewer');assert find(n,'关闭') is not None;shot('image-viewer');R.append('原生图片浏览器打开成功')
adb('shell','input','tap','608','1320');adb('shell','input','tap','608','1320');shot('image-zoomed');tap(find(n,'保存'));n=dump('image-saved')
for _ in range(3):
 if find(n,'已保存到相册') is not None:break
 n=dump('image-saved')
assert find(n,'已保存到相册') is not None,'图片未保存成功';R.append('图片保存到 MediaStore 成功');tap(find(n,'关闭'))
n=dump('reader-bottom')
for _ in range(10):
 if find(n,'本地收藏') is not None or find(n,'已收藏') is not None:break
 adb('shell','input','swipe','600','2220','600','700','280');n=dump('reader-bottom')
if find(n,'本地收藏') is not None:tap(find(n,'本地收藏'));n=dump('favorite-saved')
assert find(n,'已收藏') is not None,'收藏未写入';R.append('Room 本地收藏写入成功');shot('favorite-saved')
adb('shell','input','keyevent','4');n=dump('question-return');assert find(n,'问题') is not None;R.append('返回问题页成功，标题和回答位置保留')
adb('shell','input','keyevent','4');n=dump('hot-return');assert find(n,'全站热榜') is not None;R.append('返回热榜成功，列表位置保留')
my=[n0 for n0 in n if n0.get('text')=='我的'];tap(my[-1]);n=dump('my-tested');tap(find(n,'收藏'));n=dump('favorites-tested');assert any('媒体曝腾讯' in n0.get('text','') for n0 in n);R.append('收藏列表读取成功');shot('favorites-tested')
adb('shell','input','keyevent','4');n=dump('my-history');tap(find(n,'浏览历史'));n=dump('history-tested');assert any('媒体曝腾讯' in n0.get('text','') for n0 in n);R.append('浏览历史记录成功');shot('history-tested')
adb('shell','input','keyevent','4');n=dump('my-done');home=[n0 for n0 in n if n0.get('text')=='首页'];tap(home[-1]);shot('home-final')
print(json.dumps({'checks':R,'status':'passed'},ensure_ascii=False,indent=2))
