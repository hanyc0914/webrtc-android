1.安装jdk1.8.0_144

2.手动配置gradle
先在Android studio中open项目，这时会下载某个版本的gradle，强制关闭Android studio。进入目录~/.gradle/wrapper/dists/查看下载的gradle版本，把自己下载好的gradle-x.x.x-x.zip拷贝到目标目录（每台机器的一长串字符可能不一样），再次重启Android studio，open项目等待gradle编译
eg.2-14-1-all.zip(其他版本同理)
cp /Users/hanyachao/Downloads/gradle-2.14.1-all.zip ~/.gradle/wrapper/dists/gradle-2.14.1-all/8bnwg5hd3w55iofp58khbp6yv/

3.运行时，pc端输入网址  https://appr.tc  ,输入房间号，进入聊天房间；在android端也输入相同房间号进入聊天房间（有的安卓手机需要在设置中给应用开权限）
