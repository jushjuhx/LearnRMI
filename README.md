# 一、x01_demo
[Java RMI入门](http://scz.617.cn:8/network/202002221000.txt)
1. 服务端（运行在`111.231.190.16`上）
    1. 通过`LocateRegistry.createRegistry(int port)`创建一个注册表，绑定到端口`1099`上。
    2. 创建一个实现`Remote`接口并继承`UnicastRemoteObject`的对象，该对象创建后，实际上就产生了动态端口。
    3. 最后指定一个名字，将该对象`rebind`到注册表中。
        1. 除了使用注册表绑定一个名字外，还可以通过`static java.rmi.Naming.rebind`绑定`rmi://ip:port/name`形式的名字
           使用Naming绑定时，查找也是使用`static java.rmi.Naming.lookup`进行查找
        2. `rebind`可以是其他进程进行`rebind`，先`getRegistry`，再绑定就行。
           但是由于安全原因，其他主机获取到该注册表后，是不能进行绑定的(通过`sun.rmi.registry.RegistryImpl.checkAccess()`限制)。
           checkAccess()会检查rebind()的源IP与目标IP是否位于同一主机，不是则抛出异常java.rmi.AccessException。
           从TCP层看没有限制，前述检查是Java RMI自己加的，出于安全考虑？这大大限制了Java RMI的分布式应用。
           搜了一下，没有官方绕过方案。自己Patch rt.jar就比较扯了，不考虑这种Hacking方案，无论静态还是动态Patch。
    
2. 客户端
    1. 通过`LocateRegistry.getRegistry(String host, int port)`获取注册表
    2. 通过注册表的`lookup(String name)`获取特定名字的对象
    3. 执行该对象的某个方法
    
3. 底层细节
    1. 服务端创建注册表后，监听在`0.0.0.0:1099`端口(对应1.1)
    2. 服务端创建`Remote`对象的子类同时，会监听在`0.0.0.0:随机端口`上，如果再创建新的`Remote`子类对象，监听的端口`不变`(对应1.2)
        1. 这个随机端口是可以指定为固定端口的：
           HelloRMIInterfaceImpl继承自UnicastRemoteObject，
           而UnicastRemoteObject有个构造函数可以指定 RMIServerSocketFactory，
           所以可以实现一个类，继承RMIServerSocketFactory并重写的createServerSocket方法，进而实现监听指定端口的功能
    3. 客户端执行`LocateRegistry.getRegistry`时，并没有网络连接操作，也就是说这时候如果对应的`host:port`上没有相关注册表，也不会报错。
        1. 如果执行后续操作，比如`r.list`获取所有的名字，那么则会产生网络连接。
    4. 客户端执行`lookup`操作，网络交互如下

        a. 客户端发起RMI请求报文
        ```text
        Java RMI
            Magic: 0x4a524d49
            Version: 2
            Protocol: StreamProtocol (0x4b)
        ```
        b. 服务端返回RMI信息，其中客户端需要连接的hostname和port(该hostname是服务端的IP，port是客户端发起TCP连接时使用的port)
        ```text
        Java RMI
            Input Stream Message: ProtocolAck (0x4e)
            EndPointIdentifier
                Length: 14
                Hostname: 111.231.190.16
                Port: 64117
        ```
        c. 客户端发送请求的对象名字`HelloRMIInterfaceTest001`
        d. 服务端返回远程对象的地址`127.0.0.1`（并不是`111.231.190.16`）、(抓包未看到明显信息，但是猜测也有3.2中的)`随机端口`，和一些其他信息(Reflect Proxy等)。
           注意客户端会去连接`127.0.0.1`，但是因为服务端实际上在`111.231.190.16`上，所以会抛出异常。
           解决该问题的方式是服务端启动的时候，指定RMI server：添加启动参数`-Djava.rmi.server.hostname=111.231.190.16`即可
        e. 客户端往远程服务器发送信息
        f. 远程服务器返回执行结果
        
