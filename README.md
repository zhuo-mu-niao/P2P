# P2P
基于P2P的局域网通讯软件
某些学校的计算机网络课程设计，可供参考

启动方法：
将命令行切换到...\Course work-p2p\out\production\Course work-p2p路径下
CMD的切换路径指令为cd <路径>
先启服务器：java Server
再启动客户端：java P2PClient
客户端可运行多个，启动服务器与客户端需要多个命令行

使用方法：
在最上方输入框中输入用户名后点击log in即可连接至服务器，并在服务器中存贮登录信息
然后点击log out即可登出并清除服务器中的登录信息
点击refresh即可通过服务器刷新当前在线用户列表
点击在线用户列表中的用户名并点击connect即可连接至目标用户，然后即可发送文字消息/文件
点击exit退出

test.txt是测试发送文件用的