import os
import subprocess, xml.etree.ElementTree as ET,re,json,pathlib
serial=os.environ.get("ANDROID_SERIAL", "")
assert serial, "Set ANDROID_SERIAL to your adb device serial"
root=pathlib.Path(__file__).resolve().parent.parent
out=root/'docs'/'device';out.mkdir(parents=True,exist_ok=True)
def adb(*args):return subprocess.check_output(['adb','-s',serial,*args])
def dump(name):
 adb('shell','uiautomator','dump','/sdcard/zhihu-check.xml')
 xml=adb('shell','cat','/sdcard/zhihu-check.xml');(out/(name+'.xml')).write_bytes(xml)
 return list(ET.fromstring(xml).iter('node'))
def tap(node):
 a,b,c,d=map(int,re.findall(r'\d+',node.attrib['bounds']));adb('shell','input','tap',str((a+c)//2),str((b+d)//2))
def shot(name): (out/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def texts(nodes): return [n.get('text','') for n in nodes if n.get('text')]
adb('shell','am','start','-n','com.ives.zhihulite/.MainActivity')
nodes=dump('start')
for _ in range(5):
 if any(n.get('text')=='首页' for n in nodes):break
 adb('shell','input','keyevent','4');nodes=dump('back-to-root')
nav=[n for n in nodes if n.get('text')=='热榜' and int(re.findall(r'\d+',n.get('bounds'))[1])>2000]
assert nav,'未找到底部热榜'
tap(nav[-1]);nodes=dump('hot-tested');shot('hot-tested')
assert '全站热榜' in texts(nodes),'页面在测试期间发生变化'
question=next(n for n in nodes if n.get('text','').endswith('？') and int(re.findall(r'\d+',n.get('bounds'))[1])>450)
qtitle=question.get('text');tap(question)
for _ in range(5):
 nodes=dump('question-tested')
 if any('赞同' in t for t in texts(nodes)):break
shot('question-tested')
assert '问题' in texts(nodes),'未进入问题页面'
# answer list may sit below the question description
for _ in range(5):
 author=next((n for n in nodes if '赞同' in n.get('text','')),None)
 if author is not None:break
 adb('shell','input','swipe','600','2150','600','700','350');nodes=dump('question-answer-list')
assert author is not None,'未找到真实回答列表'
tap(author)
for _ in range(5):
 nodes=dump('reader-tested')
 if any(len(t)>60 for t in texts(nodes)) and '阅读' in texts(nodes):break
shot('reader-tested')
assert '阅读' in texts(nodes),'未进入原生阅读页'
print(json.dumps({'question':qtitle,'reader_texts':texts(nodes)[:12],'status':'native hot -> question -> answer navigation passed'},ensure_ascii=False,indent=2))
