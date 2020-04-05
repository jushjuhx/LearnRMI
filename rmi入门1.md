标题: Java RMI入门

创建: 2020-02-22 10:00
更新: 2020-04-01 16:50
链接: http://scz.617.cn:8/network/202002221000.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ Java RMI
        1) HelloRMIInterface.java
        2) HelloRMIInterfaceImpl.java
        3) HelloRMIServer.java
        4) HelloRMIClient.java
        5) HelloRMIServer/HelloRMIClient不在同一台主机上时的幺蛾子
            5.0) 转储"com.sun.proxy.$Proxy0"
            5.1) Java RMI与DCE/MS RPC、ONC/Sun RPC
            5.2) HelloRMIServer2.java
        6) 侦听指定IP、指定PORT
            6.1) HelloRMIServerSocketFactoryImpl.java
            6.2) HelloRMIInterfaceImpl3.java
            6.3) HelloRMIServer3.java
            6.4) 另一种方案
                6.4.1) HelloRMIInterfaceImpl8.java
                6.4.2) HelloRMIDynamicServer8.java
        7) java.rmi.Naming
            7.1) HelloRMIServer4.java
            7.2) HelloRMIClient4.java
            7.3) Naming.rebind
            7.4) Naming.lookup
        8) 分离周知端口与动态端口
            8.1) HelloRMIWellknownServer.java
            8.2) HelloRMIDynamicServer.java
            8.3) 周知端口与动态端口不在同一台主机上时的幺蛾子
            8.4) 周知端口与动态端口不在同一台主机上时的网络通信报文
            8.5) HelloRMIDynamicServer2.java
        9) JDK自带RMI相关工具
            9.1) rmiregistry
                9.1.1) inside rmiregistry
                9.1.2) 扫描识别rmiregistry
       10) 从周知端口获取所有动态端口信息
           10.1) rmiinfo.java
           10.2) rmi-dumpregistry.nse
               10.2.1) HelloRMI_6.cap部分报文解码
           10.3) rmiregistry_detect.nasl
               10.3.1) HelloRMI_7.cap部分报文解码
       11) JNDI
           11.1) HelloRMIDynamicServer5.java (JNDI+RMI)
           11.2) HelloRMIClient5.java
           11.3) HelloRMIDynamicServer6.java
           11.4) HelloRMIClient6.java
       12) RMI-IIOP
           12.1) HelloRMIInterfaceImpl7.java
               12.1.1) rmic
           12.2) HelloRMIDynamicServer7.java (JNDI+CORBA)
           12.3) HelloRMIClient7.java
           12.4) orbd
               12.4.1) inside orbd
           12.5) 测试RMI-IIOP
               12.5.1) HelloRMIDynamicServer7/HelloRMIClient7不在同一台主机上时的幺蛾子
           12.6) RMI-IIOP vs RMI
    ☆ JNDI+LDAP
        1) 简版LDAP Server
        2) jndi.ldif
        3) HelloRMIInterface.java
        4) HelloRMIInterfaceImpl.java
        5) JNDILDAPServer.java
        6) JNDILDAPClient.java
        7) 编译
        8) 测试
            8.1) 为何有个GET请求404时客户端仍然正常结束
        9) HelloRMIInterfaceImpl8.java
       10) JNDILDAPServer2.java
    ☆ 后记

--------------------------------------------------------------------------

# ☆ 前言

参看

《Java RMI入门(2)》
http://scz.617.cn:8/network/202003081810.txt

《Java RMI入门(3)》
http://scz.617.cn:8/network/202003121717.txt

《Java RMI入门(4)》
http://scz.617.cn:8/network/202003191728.txt

《Java RMI入门(5)》
http://scz.617.cn:8/network/202003241127.txt

《Java RMI入门(6)》
http://scz.617.cn:8/network/202004011650.txt

自从99年放弃Java，再没有主动学习过Java的正经面，一直到2019.11。这一拨学习
源自试图理解Java漏洞所涉及的若干方面，RMI正是其中之一。

本文是我学习RMI之后的笔记。不打算用一些看上去玄之又玄的概念来开场，做为程
序员，一个提纲挈领的"Hello World"足以入门。

任何有过DCE/MS RPC、ONC/Sun RPC编程、协议分析、漏洞挖掘经历的读者很容易理
解本篇笔记，假设本文面向的读者是这一类的，只不过没有接触过Java RMI。

# ☆ Java RMI

RMI是"Remote Method Invocation"的缩写。

## 1) HelloRMIInterface.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterface.java
 */
import java.rmi.*;

/*
 * The Interface must always be public and extend Remote.
 *
 * All methods described in the Remote interface must list RemoteException
 * in their throws clause.
 */
public interface HelloRMIInterface extends Remote
{
    public String Echo ( String sth ) throws RemoteException;
}
```

## 2) HelloRMIInterfaceImpl.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterfaceImpl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class HelloRMIInterfaceImpl extends UnicastRemoteObject implements HelloRMIInterface
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    protected HelloRMIInterfaceImpl () throws RemoteException
    {
        super();
    }

    @Override
    public String Echo ( String sth ) throws RemoteException
    {
        /*
         * 故意加一对[]，将来抓包时便于识别请求、响应
         */
        return( "[" + sth + "]" );
    }
}
```

## 3) HelloRMIServer.java

```java
/*
 * javac -encoding GBK -g HelloRMIServer.java
 * java HelloRMIServer 1099 HelloRMIInterface
 */
import java.rmi.registry.*;

public class HelloRMIServer
{
    public static void main ( String[] argv ) throws Exception
    {
        int                 port    = Integer.parseInt( argv[0] );
        String              name    = argv[1];
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html
         *
         * port默认使用1099/TCP，addr默认使用"0.0.0.0"
         */
        Registry            r       = LocateRegistry.createRegistry( port );
        HelloRMIInterface   hello   = new HelloRMIInterfaceImpl();
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/Registry.html
         *
         * 第一形参内容任意，起唯一标识作用
         */
        r.rebind( name, hello );
    }
}
```

## 4) HelloRMIClient.java

```java
/*
 * javac -encoding GBK -g HelloRMIClient.java
 * java HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World"
 */
import java.rmi.registry.*;

public class HelloRMIClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr    = argv[0];
        int                 port    = Integer.parseInt( argv[1] );
        String              name    = argv[2];
        String              sth     = argv[3];
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html
         */
        Registry            r       = LocateRegistry.getRegistry( addr, port );
        HelloRMIInterface   hello   = ( HelloRMIInterface )r.lookup( name );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

启动服务端:

$ java HelloRMIServer 1099 HelloRMIInterface

测试客户端:

$ java HelloRMIClient 127.0.0.1 1099 HelloRMIInterface "Hello World"
[Hello World]

## 5) HelloRMIServer/HelloRMIClient不在同一台主机上时的幺蛾子

假设Linux是192.168.65.23，Windows是192.168.68.1。

在Linux中启动HelloRMIServer:

$ java HelloRMIServer 1099 HelloRMIInterface

用netstat、lsof确认服务端侦听"0.0.0.0:1099/TCP"。在客户端用nc确认远程可达
服务端的1099/TCP。

在Windows中放两个类:

HelloRMIClient.class
HelloRMIInterface.class

在Windows中运行HelloRMIClient:

$ java.exe HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"
Exception in thread "main" java.rmi.ConnectException: Connection refused to host: 127.0.0.1; nested exception is:
        java.net.ConnectException: Connection refused: connect
        at sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:619)
        at sun.rmi.transport.tcp.TCPChannel.createConnection(TCPChannel.java:216)
        at sun.rmi.transport.tcp.TCPChannel.newConnection(TCPChannel.java:202)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:129)
        at java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:227)
        at java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:179)
        at com.sun.proxy.$Proxy0.Echo(Unknown Source)
        at HelloRMIClient.main(HelloRMIClient.java:20)
Caused by: java.net.ConnectException: Connection refused: connect
        at java.net.DualStackPlainSocketImpl.connect0(Native Method)
        at java.net.DualStackPlainSocketImpl.socketConnect(DualStackPlainSocketImpl.java:79)
        at java.net.AbstractPlainSocketImpl.doConnect(AbstractPlainSocketImpl.java:350)
        at java.net.AbstractPlainSocketImpl.connectToAddress(AbstractPlainSocketImpl.java:206)
        at java.net.AbstractPlainSocketImpl.connect(AbstractPlainSocketImpl.java:188)
        at java.net.PlainSocketImpl.connect(PlainSocketImpl.java:172)
        at java.net.SocksSocketImpl.connect(SocksSocketImpl.java:392)
        at java.net.Socket.connect(Socket.java:589)
        at java.net.Socket.connect(Socket.java:538)
        at java.net.Socket.<init>(Socket.java:434)
        at java.net.Socket.<init>(Socket.java:211)
        at sun.rmi.transport.proxy.RMIDirectSocketFactory.createSocket(RMIDirectSocketFactory.java:40)
        at sun.rmi.transport.proxy.RMIMasterSocketFactory.createSocket(RMIMasterSocketFactory.java:148)
        at sun.rmi.transport.tcp.TCPEndpoint.newSocket(TCPEndpoint.java:613)
        ... 7 more

居然抛出异常，后面我会剖析发生了什么。

### 5.0) 转储"com.sun.proxy.$Proxy0"

调用栈回溯中出现"com.sun.proxy.$Proxy0"，这是动态代理机制。有办法把这个动
态生成的类从内存中转储出来，其中一个办法是:

$ mkdir com\sun\proxy
$ java.exe -Dsun.misc.ProxyGenerator.saveGeneratedFiles=true HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"
$ dir com\sun\proxy\$Proxy0.class

用JD-GUI看$Proxy0.class，没什么可看的，这都是固定套路式的代码，真正起作用
的是调用栈回溯中的sun.rmi.server.UnicastRef.invoke()。

### 5.1) Java RMI与DCE/MS RPC、ONC/Sun RPC

开始以为什么报文都没有发往192.168.65.23，以为客户端直接尝试连接127.0.0.1。
用Wireshark抓包，发现"192.168.65.1"已经与"192.168.65.23:1099"有交互，抓包
观察到"JRMI Call"和"JRMI ReturnData"。在后者的hexdump中看到"127.0.0.1"，
估计客户端按此指示尝试连接127.0.0.1的某个端口。

搜索前面那个异常信息，发现官方有段解释。参看:

https://docs.oracle.com/javase/8/docs/technotes/guides/rmi/faq.html

A.1 Why do I get an exception for an unexpected hostname and/or port number when I call Naming.lookup?

它这个标题不直接区配HelloRMIClient.java，但回答的内容是匹配的。

下面是一种解决方案，启动HelloRMIServer时指定一个JVM参数:

$ java -Djava.rmi.server.hostname=192.168.65.23 HelloRMIServer 1099 HelloRMIInterface

重新在Windows中测试HelloRMIClient，这次成功:

$ java.exe HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"
[Hello World From Windows]

抓包，发现"-Djava.rmi.server.hostname="会改变"JRMI ReturnData"中的
"127.0.0.1"，这次变成"192.168.65.23"。客户端收到"JRMI ReturnData"之后，
新建了一条到"192.168.65.23:38070"的TCP连接，端口号38070(0x94b6)也在
"JRMI ReturnData"中指明。

正常情况下可以翻看Java RMI相关文档、JDK源码，或者逆一下rt.jar，以搞清楚其
中的代码逻辑。不过此刻没心情这么折腾，我用其他办法来试图理解发生了什么。

很多年前对Windows平台的DCE/MS RPC和*nix平台的ONC/Sun RPC有过深入研究，2002
年我写过它们之间的简单对比:

--------------------------------------------------------------------------
DCE/MS RPC                          ONC/Sun RPC
--------------------------------------------------------------------------
.idl                                .x
MIDL编译器                          rpcgen
NDR                                 XDR
endpoint mapper(135/TCP、135/UDP)   RPCBIND/PORTMAPPER(111/TCP、111/UDP)
--------------------------------------------------------------------------

上面第四行的东西侦听固定周知端口，那些侦听动态端口的RPC服务将自己所侦听的
动态端口注册(汇报)给第四行。RPC客户端首先向第四行查询，以获取动态端口号，
继而访问动态端口。

Java RMI既然也是RPC的一种，想必1099/TCP地位相当于前述第四行，38070/TCP是动
态端口，每次重启HelloRMIServer，动态端口会变。HelloRMIClient访问1099/TCP获
取动态端口，对于Java RMI来说，可能还有一个动态IP的概念；HelloRMI_1.cap中的
"JRMI Call"和"JRMI ReturnData"对应这个过程；源码中r.lookup()对应这个过程。
HelloRMIClient访问"动态IP+动态端口"进行真正的RPC调用，HelloRMI_1.cap中第二
条TCP连接(38070/TCP)对应这个过程，源码中hello.Echo()对应这个过程。JVM参数
"-Djava.rmi.server.hostname="指定的就是动态IP。你可能看过一些其他手段，比
如/etc/hosts、域名解析之类的，其本质是让HelloRMIClient找到动态IP或其等价物。
最常见的手段是先用hosname取服务端主机名，再用ifconfig取服务端IP，在
/etc/hosts中增加一条"服务端IP 服务端主机名"，重启HelloRMIServer；我不推荐
这种方案。

前面这些内容完全是基于历史经验从架构上猜测而写，非官方描述，切勿当真。没动
力翻文档，RPC就这么点事，换汤不换药，猫叫咪咪、咪咪叫猫罢了。

在服务端查看HelloRMIServer侦听的端口:

$ lsof -lnPR +c0 +f g -o1 -c /java/ | grep IPv4
java    53597 2151     1000   12u  IPv4      RW,ND             696864       0t0       TCP *:1099 (LISTEN)
java    53597 2151     1000   13u  IPv4      RW,ND             696865       0t0       TCP *:38070 (LISTEN)

$ netstat -natp | grep java
tcp        0      0 0.0.0.0:1099            0.0.0.0:*               LISTEN      53597/java
tcp        0      0 0.0.0.0:38070           0.0.0.0:*               LISTEN      53597/java

### 5.2) HelloRMIServer2.java

这个例子不用JVM参数来指定动态IP，而是在源码中设置它。

```java
/*
 * javac -encoding GBK -g HelloRMIServer2.java
 * java HelloRMIServer2 192.168.65.23 1099 HelloRMIInterface
 */
import java.rmi.registry.*;

public class HelloRMIServer2
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr    = argv[0];
        int                 port    = Integer.parseInt( argv[1] );
        String              name    = argv[2];
        /*
         * 指定动态IP，而不是默认的"127.0.0.1"。这句必须在createRegistry()
         * 之前，而不是rebind()之前。
         */
        System.setProperty( "java.rmi.server.hostname", addr );
        Registry            r       = LocateRegistry.createRegistry( port );
        HelloRMIInterface   hello   = new HelloRMIInterfaceImpl();
        r.rebind( name, hello );
    }
}
```

$ java HelloRMIServer2 192.168.65.23 1099 HelloRMIInterface
$ java HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World"
$ java.exe HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"

## 6) 侦听指定IP、指定PORT

从前面netstat的输出可以看到，HelloRMIServer会侦听两个端口，一个是周知端口，
另一个是动态端口，这两个端口均侦听在"0.0.0.0"上。即使指定那个JVM参数或等价
操作，仅仅影响"JRMI ReturnData"中的动态IP字段，HelloRMIServer的动态端口实
际仍然侦听在"0.0.0.0"上。

现在想让周知端口、动态端口分别侦听在指定IP上，比如"192.168.65.23"、
"127.0.0.1"。此外，不想让系统随机指定动态端口，想自己指定动态端口。这是可
以做到的。

### 6.1) HelloRMIServerSocketFactoryImpl.java

```java
/*
 * javac -encoding GBK -g HelloRMIServerSocketFactoryImpl.java
 */
import java.io.*;
import java.net.*;
import java.rmi.server.RMIServerSocketFactory;

public class HelloRMIServerSocketFactoryImpl implements RMIServerSocketFactory
{
    /*
     * https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html
     */
    private InetAddress bindAddr;

    public HelloRMIServerSocketFactoryImpl ( InetAddress bindAddr )
    {
        this.bindAddr   = bindAddr;
    }

    /*
     * https://docs.oracle.com/javase/8/docs/api/java/rmi/server/RMIServerSocketFactory.html
     */
    @Override
    public ServerSocket createServerSocket ( int port ) throws IOException
    {
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/net/ServerSocket.html
         */
        return new ServerSocket( port, 0, bindAddr );
    }

    /*
     * https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html
     *
     * An implementation of this interface should implement Object.equals(java.lang.Object)
     * to return true when passed an instance that represents the same
     * (functionally equivalent) server socket factory, and false otherwise
     * (and it should also implement Object.hashCode() consistently with
     * its Object.equals implementation).
     */
    @Override
    public boolean equals ( Object obj )
    {
        return obj != null && this.getClass() == obj.getClass() && this.bindAddr.equals( ((HelloRMIServerSocketFactoryImpl)obj).bindAddr );
    }
}
```

### 6.2) HelloRMIInterfaceImpl3.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterfaceImpl3.java
 */
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class HelloRMIInterfaceImpl3 extends UnicastRemoteObject implements HelloRMIInterface
{
    private static final long   serialVersionUID    = 0x5120131473637a01L;

    protected HelloRMIInterfaceImpl3 ( int port, InetAddress bindAddr ) throws RemoteException
    {
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/server/UnicastRemoteObject.html
         *
         * if port is zero, an anonymous port is chosen
         */
        super( port, null, new HelloRMIServerSocketFactoryImpl( bindAddr ) );
    }

    @Override
    public String Echo ( String sth ) throws RemoteException
    {
        return( "[" + sth + "]" );
    }
}
```

6.3) HelloRMIServer3.java

```java
/*
 * javac -encoding GBK -g HelloRMIServer3.java
 * java HelloRMIServer3 192.168.65.23 1099 127.0.0.1 0 HelloRMIInterface
 */
import java.net.InetAddress;
import java.rmi.registry.*;

public class HelloRMIServer3
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        int                 port_1      = Integer.parseInt( argv[3] );
        String              name        = argv[4];
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/net/InetAddress.html
         */
        InetAddress         bindAddr_0  = InetAddress.getByName( addr_0 );
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        System.setProperty( "java.rmi.server.hostname", addr_1 );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/registry/LocateRegistry.html
         */
        Registry            r           = LocateRegistry.createRegistry( port_0, null, new HelloRMIServerSocketFactoryImpl( bindAddr_0 ) );
        /*
         * if port is zero, an anonymous port is chosen
         */
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        r.rebind( name, hello );
    }
}
```

让系统随机指定动态端口:

$ java HelloRMIServer3 192.168.65.23 1099 127.0.0.1 0 HelloRMIInterface

$ netstat -natp | grep java
tcp        0      0 127.0.0.1:33949         0.0.0.0:*               LISTEN      66878/java
tcp        0      0 192.168.65.23:1099      0.0.0.0:*               LISTEN      66878/java

$ java HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World"

服务端显式指定两个IP、两个端口:

$ java HelloRMIServer3 192.168.65.23 1098 192.168.65.23 1100 HelloRMIInterface

$ netstat -natp | grep java
tcp        0      0 192.168.65.23:1098      0.0.0.0:*               LISTEN      67510/java
tcp        0      0 192.168.65.23:1100      0.0.0.0:*               LISTEN      67510/java

在Windows中测试HelloRMIClient，注意服务端周知端口被人为设置成1098/TCP，客
户端需要同步改变:

$ java.exe HelloRMIClient 192.168.65.23 1098 HelloRMIInterface "Hello World From Windows"

尽管可以明确指定HelloRMIServer3侦听的动态端口，比如前述1100/TCP，但
HelloRMIClient不需要关心这种变化，HelloRMIClient始终会通过周知端口或者说主
端口去隐式获取动态端口并发起RPC调用。

### 6.4) 另一种方案

本小节是后补的，提前引入了"分离周知端口与动态端口"的设定，如果感到困惑，可
以回头再来看本小节。

#### 6.4.1) HelloRMIInterfaceImpl8.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterfaceImpl8.java
 */
import java.rmi.RemoteException;

/*
 * 故意不继承java.rmi.server.UnicastRemoteObject，以演示另一种用法
 */
public class HelloRMIInterfaceImpl8 implements HelloRMIInterface
{
    @Override
    public String Echo ( String sth ) throws RemoteException
    {
        return( "[" + sth + "]" );
    }
}
```

#### 6.4.2) HelloRMIDynamicServer8.java

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer8.java
 * java HelloRMIDynamicServer8 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;

public class HelloRMIDynamicServer8
{
    public static void main ( String[] argv ) throws Exception
    {
        String                  addr_0      = argv[0];
        int                     port_0      = Integer.parseInt( argv[1] );
        String                  addr_1      = argv[2];
        int                     port_1      = Integer.parseInt( argv[3] );
        String                  name        = argv[4];
        InetAddress             bindAddr_1  = InetAddress.getByName( addr_1 );
        Registry                r           = LocateRegistry.getRegistry( addr_0, port_0 );
        /*
         * HelloRMIInterfaceImpl8没有继承UnicastRemoteObject，这次演示另一
         * 种用法。
         */
        HelloRMIInterface       obj         = new HelloRMIInterfaceImpl8();
        /*
         * if port is zero, an anonymous port is chosen
         */
        HelloRMIInterface       hello       = ( HelloRMIInterface )UnicastRemoteObject.exportObject
        (
            /*
             * 如果直接将"new HelloRMIInterfaceImpl8()"置于此处，后面的
             * r.rebind()无法形成阻塞，进程退出，动态端口关闭。
             */
            obj,
            port_1,
            null,
            new HelloRMIServerSocketFactoryImpl( bindAddr_1 )
        );
        r.rebind( name, hello );
    }
}
```

侦听周知端口、动态端口，其中动态端口显式指定成1314/TCP:

$ rmiregistry 1099
$ java HelloRMIDynamicServer8 192.168.65.23 1099 192.168.65.23 1314 HelloRMIInterface

检查服务端侦听的端口:

$ netstat -nltp | egrep "rmiregistry|java"
tcp        0      0 192.168.65.23:1314      0.0.0.0:*               LISTEN      10475/java
tcp        0      0 0.0.0.0:1099            0.0.0.0:*               LISTEN      5676/rmiregistry

执行客户端:

$ java HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World"
$ java.exe HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"

## 7) java.rmi.Naming

### 7.1) HelloRMIServer4.java

```java
/*
 * javac -encoding GBK -g HelloRMIServer4.java
 * java HelloRMIServer4 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import java.rmi.registry.*;
import java.rmi.Naming;

public class HelloRMIServer4
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        int                 port_1      = Integer.parseInt( argv[3] );
        String              name        = argv[4];
        String              url         = String.format( "rmi://%s:%d/%s", addr_0, port_0, name );
        InetAddress         bindAddr_0  = InetAddress.getByName( addr_0 );
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        System.setProperty( "java.rmi.server.hostname", addr_1 );
        Registry            r           = LocateRegistry.createRegistry( port_0, null, new HelloRMIServerSocketFactoryImpl( bindAddr_0 ) );
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/Naming.html
         *
         * 这一步过去用的是
         *
         * r.rebind( name, hello );
         *
         * 第一形参URL指定PORTMAPPER等价物所在，形如:
         *
         * rmi://127.0.0.1:1099/HelloRMIInterface
         */
        Naming.rebind( url, hello );
    }
}
```

### 7.2) HelloRMIClient4.java

```java
/*
 * javac -encoding GBK -g HelloRMIClient4.java
 * java HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World"
 */
import java.rmi.Naming;

public class HelloRMIClient4
{
    public static void main ( String[] argv ) throws Exception
    {
        String              url     = argv[0];
        String              sth     = argv[1];
        /*
         * 这一步过去用的是
         *
         * r = LocateRegistry.getRegistry( addr, port )
         * r.lookup( name )
         */
        HelloRMIInterface   hello   = ( HelloRMIInterface )Naming.lookup( url );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

$ java HelloRMIServer4 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
$ java HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World"
$ java.exe HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World From Windows"

java.rmi.Naming用"rmi://..."这种形式的url指定周知IP、周知端口等信息。

### 7.3) Naming.rebind

java.rmi.Naming是对java.rmi.registry的封装使用，没有本质区别。

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/rmi/Naming.java

```java
public static void rebind ( String name, Remote obj )
    throws RemoteException, MalformedURLException
{
    ParsedNamingURL parsed      = parseURL( name );
    Registry        registry    = getRegistry( parsed );
    if ( obj == null )
    {
        throw new NullPointerException( "cannot bind to null" );
    }
    registry.rebind( parsed.name, obj );
}
```

### 7.4) Naming.lookup

```java
public static Remote lookup ( String name )
    throws NotBoundException, MalformedURLException, RemoteException
{
    ParsedNamingURL parsed      = parseURL( name );
    Registry        registry    = getRegistry( parsed );
    if ( parsed.name == null )
    {
        return registry;
    }
    return registry.lookup( parsed.name );
}
```

## 8) 分离周知端口与动态端口

就RPC架构来说，周知端口提供的服务与动态端口提供的服务完全两码事。前面的
HelloRMIServer为了演示便捷，将这两种端口放在同一个main()侦听，可以分离它们
到不同进程中去，但没法分离它们到不同主机中去，Java RMI对此有安全限制。

这种分离是一种自然而然的需求，周知端口只有一个，动态端口可以有很多，对应不
同的远程服务。

### 8.1) HelloRMIWellknownServer.java

```java
/*
 * javac -encoding GBK -g HelloRMIWellknownServer.java
 * java HelloRMIWellknownServer 192.168.65.23 1099 192.168.65.23
 */
import java.net.InetAddress;
import java.rmi.registry.*;

public class HelloRMIWellknownServer
{
    public static void main ( String[] argv ) throws Exception
    {
        /*
         * 变量命名故意如此，以与HelloRMIServer4.java产生更直观的对比
         */
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        InetAddress         bindAddr_0  = InetAddress.getByName( addr_0 );
        /*
         * 这个设置只影响"JRMI ReturnData"中的动态IP字段，不影响动态端口实
         * 际侦听的地址。
         */
        System.setProperty( "java.rmi.server.hostname", addr_1 );
        /*
         * 这会侦听周知端口，应该是有个异步机制在背后，不需要单开一个线程
         * 放这句代码。
         */
        Registry            r           = LocateRegistry.createRegistry( port_0, null, new HelloRMIServerSocketFactoryImpl( bindAddr_0 ) );
        /*
         * 类似C语言的getchar()，最简单的阻塞。否则本进程结束，周知端口关
         * 闭。这个阻塞不影响对周知端口的访问。老年程序员的脑洞就是大。
         */
        System.in.read();
    }
}
```

### 8.2) HelloRMIDynamicServer.java

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer.java
 * java HelloRMIDynamicServer 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import java.rmi.registry.*;

public class HelloRMIDynamicServer
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        int                 port_1      = Integer.parseInt( argv[3] );
        String              name        = argv[4];
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        /*
         * getRegistry()并不会发起到周知端口的TCP连接
         */
        Registry            r           = LocateRegistry.getRegistry( addr_0, port_0 );
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        /*
         * 向周知端口注册(汇报)动态端口，等待客户端前来访问。rebind()会发
         * 起到周知端口的TCP连接。
         */
        r.rebind( name, hello );
    }
}
```

先侦听周知端口:

$ java HelloRMIWellknownServer 192.168.65.23 1099 192.168.65.23

再侦听动态端口:

$ java HelloRMIDynamicServer 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface

在Windows中执行客户端:

$ java.exe HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World From Windows"

### 8.3) 周知端口与动态端口不在同一台主机上时的幺蛾子

试图让周知端口跑在192.168.65.23上，让动态端口跑在192.168.65.20上，失败。

在192.168.65.23上:

$ java HelloRMIWellknownServer 192.168.65.23 1099 192.168.65.20

在192.168.65.20上:

$ ls -1
HelloRMIDynamicServer.class
HelloRMIInterface.class
HelloRMIInterfaceImpl3.class
HelloRMIServerSocketFactoryImpl.class

$ java_8_232 HelloRMIDynamicServer 192.168.65.23 1099 192.168.65.20 0 HelloRMIInterface
Exception in thread "main" java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.AccessException: Registry.rebind disallowed; origin /192.168.65.20 is non-local host
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:389)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:573)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:834)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:688)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:687)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
        at java.lang.Thread.run(Thread.java:748)
        at sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:303)
        at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:279)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:375)
        at sun.rmi.registry.RegistryImpl_Stub.rebind(RegistryImpl_Stub.java:158)
        at HelloRMIDynamicServer.main(HelloRMIDynamicServer.java:27)
Caused by: java.rmi.AccessException: Registry.rebind disallowed; origin /192.168.65.20 is non-local host
        at sun.rmi.registry.RegistryImpl.checkAccess(RegistryImpl.java:350)
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(RegistryImpl_Skel.java:142)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:469)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:301)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:573)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:834)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0(TCPTransport.java:688)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:687)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
        at java.lang.Thread.run(Thread.java:748)

HelloRMIDynamicServer抛出异常。从调用栈回溯中注意到:

sun.rmi.registry.RegistryImpl.checkAccess()

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/registry/RegistryImpl.java

```java
/**
 * Check that the caller has access to perform indicated operation.
 * The client must be on same the same host as this server.
 */
public static void checkAccess(String op) throws AccessException
```

checkAccess()会检查rebind()的源IP与目标IP是否位于同一主机，不是则抛出异常
java.rmi.AccessException。从TCP层看没有限制，前述检查是Java RMI自己加的，
出于安全考虑？这大大限制了Java RMI的分布式应用。搜了一下，没有官方绕过方案。
自己Patch rt.jar就比较扯了，不考虑这种Hacking方案，无论静态还是动态Patch。

### 8.4) 周知端口与动态端口不在同一台主机上时的网络通信报文

可以在192.168.65.23上用tcpdump抓包:

$ tcpdump -i ens33 -s 68 -ntpq "tcp port 1099"
$ tcpdump -i ens33 -s 4096 -ntpqX "tcp port 1099"
$ tcpdump -i ens33 -s 4096 -ntpq -w HelloRMI_2.cap "tcp port 1099"

也可以直接在VMnet8上用Wireshark抓两台虚拟机之间的通信。这两种方案不等价，
后者MTU是1500，较大的"JRMI ReturnData"分散到两个TCP报文中，Wireshark没有重
组它们。而HelloRMI_2.cap中"JRMI ReturnData"是单个TCP报文。

观察HelloRMI_2.cap中"JRMI ReturnData"，发现192.168.65.23已经在抛异常:

java.rmi.AccessException: Registry.rebind disallowed; origin /192.168.65.20 is non-local host

正是checkAccess()做的检查，只不过周知端口将异常通过"JRMI ReturnData"送至动
态端口，没有在Console上直接显示异常。可以调试周知端口，通过断点确认流程经
过checkAccess()。

### 8.5) HelloRMIDynamicServer2.java

本例使用java.rmi.Naming。

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer2.java
 * java HelloRMIDynamicServer2 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import java.rmi.Naming;

public class HelloRMIDynamicServer2
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        int                 port_1      = Integer.parseInt( argv[3] );
        String              name        = argv[4];
        String              url         = String.format( "rmi://%s:%d/%s", addr_0, port_0, name );
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        Naming.rebind( url, hello );
    }
}
```

在Linux中启动两个服务端:

$ java HelloRMIWellknownServer 192.168.65.23 1099 192.168.65.23
$ java HelloRMIDynamicServer2 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface

在Windows中执行客户端:

$ java.exe HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World From Windows"

## 9) JDK自带RMI相关工具

### 9.1) rmiregistry

JDK自带rmiregistry用来单独提供周知端口服务，可以指定端口号。rmiregistry的
地位相当于ONC/Sun RPC的rpcbind。

$ rmiregistry 1099

$ netstat -natp | grep 1099
tcp        0      0 0.0.0.0:1099            0.0.0.0:*               LISTEN      55074/rmiregistry

上面这条命令侦听周知端口，相当于:

$ java HelloRMIWellknownServer 0.0.0.0 1099 192.168.65.23

rmiregistry虽然没有参数用于指定"JRMI ReturnData"中的动态IP字段，但它没有用
hostname(一般是localhost.localdomain)对应的IP(一般是127.0.0.1)，而是用
ifconfig看到的那个IP做动态IP，这倒是避免了不少麻烦。我抓包确认的。

rmiregistry不像HelloRMIWellknownServer，后者可以指定周知端口侦听什么IP，前
者只能让周知端口侦听0.0.0.0。

测试rmiregistry是否可用:

$ java HelloRMIDynamicServer2 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface

$ netstat -natp | grep java
tcp        0      0 192.168.65.23:38063     0.0.0.0:*               LISTEN      56281/java

$ java HelloRMIClient4 "rmi://127.0.0.1:1099/HelloRMIInterface" "Hello World"
$ java.exe HelloRMIClient4 "rmi://192.168.65.23:1099/HelloRMIInterface" "Hello World From Windows"

#### 9.1.1) inside rmiregistry

不知上哪找rmiregistry的源码，用IDA逆一下，main()中在调JLI_Launch()。

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/tip/src/share/bin/java.c

```C
/*
 * Entry point.
 */
int
JLI_Launch(int argc, char ** argv,              /* main argc, argc */
        int jargc, const char** jargv,          /* java args */
        int appclassc, const char** appclassv,  /* app classpath */
        const char* fullversion,                /* full version defined */
        const char* dotversion,                 /* dot version defined */
        const char* pname,                      /* program name */
        const char* lname,                      /* launcher name */
        jboolean javaargs,                      /* JAVA_ARGS */
        jboolean cpwildcard,                    /* classpath wildcard*/
        jboolean javaw,                         /* windows-only javaw */
        jint ergo                               /* ergonomics class policy */
)
```

可以用jinfo查看rmiregistry进程:

$ jinfo 55074
...
sun.java.command = sun.rmi.registry.RegistryImpl 1099
...

"rmiregistry 1099"相当于:

$ java sun.rmi.registry.RegistryImpl 1099

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/registry/RegistryImpl.java

createRegistry()调的就是RegistryImpl()。RegistryImpl.java中有main():

```java
/**
 * Main program to start a registry. <br>
 * The port number can be specified on the command line.
 */
public static void main(String args[])
{
...
        final int regPort = (args.length >= 1) ? Integer.parseInt(args[0])
                                               : Registry.REGISTRY_PORT;
        try {
            registry = AccessController.doPrivileged(
                new PrivilegedExceptionAction<RegistryImpl>() {
                    public RegistryImpl run() throws RemoteException {
                        return new RegistryImpl(regPort);
                    }
                }, getAccessControlContext(regPort));
        } catch (PrivilegedActionException ex) {
            throw (RemoteException) ex.getException();
        }

        // prevent registry from exiting
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException e) {
            }
        }
    } catch (NumberFormatException e) {
        System.err.println(MessageFormat.format(
            getTextResource("rmiregistry.port.badnumber"),
            args[0] ));
        System.err.println(MessageFormat.format(
            getTextResource("rmiregistry.usage"),
            "rmiregistry" ));
    } catch (Exception e) {
        e.printStackTrace();
    }
    System.exit(1);
}
```

从源码看出，这个main()只能指定端口，不能指定IP。

#### 9.1.2) 扫描识别rmiregistry

Nessus有个插件rmi_remote_object_detect.nasl，核心操作对应rmi_connect()，可
以抓包看看它触发的通信报文。
$ vi rmi_remote_object_detect_mini.nasl
```C
--------------------------------------------------------------------------
#
# (C) Tenable Network Security, Inc.
#

include("compat.inc");

if ( description )
{
    script_id( 22363 );
    exit( 0 );
}

include("byte_func.inc");
include("global_settings.inc");
include("misc_func.inc");
include("audit.inc");
include("rmi.inc");

port    = 1099;
#
# verify we can connect to this port using RMI
#
soc     = rmi_connect( port:port );
close( soc );
--------------------------------------------------------------------------
```

侦听周知端口:

$ rmiregistry 1099

在Windows上运行Nessus插件:

$ nasl -t 192.168.65.23 rmi_remote_object_detect_mini.nasl

这个没有输出，只是来触发通信的，抓包(HelloRMI_8.cap)。

--------------------------------------------------------------------------
No.     len   Protocol src                   dst                   sport  dport  Info
      4 61    RMI      192.168.65.1          192.168.65.23         58334  1099   JRMI, Version: 2, StreamProtocol

Internet Protocol Version 4, Src: 192.168.65.1, Dst: 192.168.65.23
Transmission Control Protocol, Src Port: 58334, Dst Port: 1099, Seq: 1, Ack: 1, Len: 7
Java RMI
    Magic: 0x4a524d49
    Version: 2
    Protocol: StreamProtocol (0x4b)

0030                    4a 52 4d 49 00 02 4b                  JRMI..K
--------------------------------------------------------------------------
No.     len   Protocol src                   dst                   sport  dport  Info
      6 73    RMI      192.168.65.23         192.168.65.1          1099   58334  JRMI, ProtocolAck

Internet Protocol Version 4, Src: 192.168.65.23, Dst: 192.168.65.1
Transmission Control Protocol, Src Port: 1099, Dst Port: 58334, Seq: 1, Ack: 8, Len: 19
Java RMI
    Input Stream Message: ProtocolAck (0x4e)
    EndPointIdentifier
        Length: 12
        Hostname: 192.168.65.1
        Port: 58334

0030                    4e 00 0c 31 39 32 2e 31 36 38         N..192.168
0040  2e 36 35 2e 31 00 00 e3 de                        .65.1....
--------------------------------------------------------------------------

这部分有官方文档，参看:

### 9.2 RMI Transport Protocol
https://docs.oracle.com/javase/8/docs/platform/rmi/spec/rmi-protocol3.html

一种可行的扫描方案，向1099/TCP发送"4a 52 4d 49 00 02 4b"，启用读超时的情况
下尝试读取23或更多字节的响应数据。如果响应数据长度不在[14,22]闭区间，服务
端不是rmiregistry或等价服务。检查响应数据前两字节是否是"4e 00"；0x4e表示
ProtocolAck，接下来的0其实是另一个2字节长度字段的高字节；如果服务端确为
rmiregistry或等价服务，响应数据buf[1:3]是个长度字段，指明后面的IP串长度，
结尾没有NUL字符；这个长度最大15、最小7，其高字节必是0。[14,22]是这么来的:

14=1+2+7+4
22=1+2+15+4

有人可能会想，为什么不检查响应数据中的IP是否等于请求包源IP？考虑NAT情形，
不建议这样做。

快速扫描方案，向1099/TCP发送"4a 52 4d 49 00 02 4b"，启用读超时的情况下尝试
读取2字节的响应数据，检查响应数据是否等于"4e 00"。

## 10) 从周知端口获取所有动态端口信息

### 10.1) rmiinfo.java

ONC/Sun RPC有个rpcinfo，可以列出向rpcbind注册过的所有动态端口。

DCE/MS RPC当年没有官方工具干类似的事，但有相应API。我写过135dump.c，还写过
NASL版本。

Java RMI有相应API干类似的事。

```java
/*
 * javac -encoding GBK -g rmiinfo.java
 * java rmiinfo 192.168.65.23 1099
 */
import java.rmi.registry.*;

public class rmiinfo
{
    public static void main ( String[] argv ) throws Exception
    {
        String      addr    = argv[0];
        int         port    = Integer.parseInt( argv[1] );
        Registry    r       = LocateRegistry.getRegistry( addr, port );
        String[]    names   = r.list();
        // for ( int i = 0; i < names.length; i++ )
        // {
        //     System.out.println( names[i] );
        // }
        for ( String name : names )
        {
            System.out.println( name );
        }
    }
}
```

侦听周知端口、动态端口:

$ rmiregistry 1099
$ java HelloRMIDynamicServer2 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface

用rmiinfo向周知端口查询所有注册过来的name:

$ java.exe rmiinfo 192.168.65.23 1099
HelloRMIInterface

本例只有一个动态端口向周知端口注册过，rmiinfo只返回一个name，现实世界中可
能返回很多name。

r.list()这个API太弱了，只返回name，不返回与之对应的动态端口号。如果用标准
Java API进行RPC调用，有name就够了。如果想绕过周知端口直接访问动态端口，只
有name是不行的。

抓包看r.list()的通信报文(HelloRMI_3.cap)。起初我以为底层返回了动态端口，只
是上层API只返回name，结果"JRMI ReturnData"中确实只有name信息。

r.lookup()对应的"JRMI ReturnData"中包含name对应的动态端口，但API没有显式返
回这个信息。总的来说，Java RMI就不想让你知道动态端口这回事，想跟你玩点玄之
又玄的其他概念。一个可行的办法是自己写Java RMI客户端，做协议封装、解码。

### 10.2) rmi-dumpregistry.nse

nmap提供了一个脚本，可以从RMI周知端口转储那些向之注册过的动态端口信息。

https://nmap.org/nsedoc/scripts/rmi-dumpregistry.html
https://svn.nmap.org/nmap/scripts/rmi-dumpregistry.nse

这是官方说明:

--------------------------------------------------------------------------
Connects to a remote RMI registry and attempts to dump all of its objects.

First it tries to determine the names of all objects bound in the registry,
and then it tries to determine information about the objects, such as the
the class names of the superclasses and interfaces. This may, depending on
what the registry is used for, give valuable information about the service.
E.g, if the app uses JMX (Java Management eXtensions), you should see an
object called "jmxconnector" on it.

It also gives information about where the objects are located, (marked
with @<ip>:port in the output).

Some apps give away the classpath, which this scripts catches in so-called
"Custom data".
--------------------------------------------------------------------------

rmi-dumpregistry.nse可以扫rmiregistry，不能扫orbd。

启动两个服务端:

$ rmiregistry 1099
$ java HelloRMIDynamicServer8 192.168.65.23 1099 192.168.65.23 1314 HelloRMIInterface

在Windows上运行nmap脚本:

$ nmap -n -Pn -p 1099 --script rmi-dumpregistry.nse 192.168.65.23

PORT     STATE SERVICE
1099/tcp open  java-rmi
| rmi-dumpregistry:
|   HelloRMIInterface
|      implements HelloRMIInterface,
|     extends
|       java.lang.reflect.Proxy
|       fields
|           Ljava/lang/reflect/InvocationHandler; h
|             java.rmi.server.RemoteObjectInvocationHandler
|             @192.168.65.23:1314
|             extends
|_              java.rmi.server.RemoteObject

这个输出比rmiinfo强出十八条长安街。抓包(HelloRMI_6.cap)。

马慧培说nmap的"-sC"或"--script=default"默认会调用上述脚本。

#### 10.2.1) HelloRMI_6.cap部分报文解码

HelloRMI_6.cap中有多条TCP连接。第1条是SYN扫描之类的。第2条是r.list()。第3
条是r.lookup()。rmi-dumpregistry.nse的代码逻辑很直白，先r.list()弄一堆name
回来，然后针对每个name调用r.lookup()。前面这些操作每次都在一条新TCP连接上
进行，并未复用单条TCP连接，如果r.list()返回N个name，后面就有N条TCP连接对应
N个r.lookup()。

前面说rmiinfo.java很弱，提到自己写Java RMI客户端，做协议封装、解码。现在看
来，rmi-dumpregistry.nse已经这样干了。得亏我多看了些文档，不然又重新造轮子。

从HelloRMI_6.cap的11、13号报文析取"Serialization Data"，分别保存至
HelloRMI_6_11.bin、HelloRMI_6_13.bin。这是r.list()的请求与响应。本次测试中
r.list()只返回了一个name。

$ xxd -g 1 HelloRMI_6_11.bin
0000000: ac ed 00 05 77 22 00 00 00 00 00 00 00 00 00 00  ....w"..........
0000010: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01  ................
0000020: 44 15 4d c9 d4 e6 3b df                          D.M...;.

$ java -jar SerializationDumper.jar -r HelloRMI_6_11.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 34 - 0x22
    Contents - 0x000000000000000000000000000000000000000000000000000144154dc9d4e63bdf

上述Contents字段应该是"ObjectIdentifier Operation Hash"

0001                // opnum = 1
44154dc9d4e63bdf    // 64-bits hash of something

r.list()的opnum是1。

$ xxd -g 1 HelloRMI_6_13.bin
0000000: ac ed 00 05 77 0f 01 30 c5 4f de 00 00 01 70 91  ....w..0.O....p.
0000010: 4e 54 a9 80 0c 75 72 00 13 5b 4c 6a 61 76 61 2e  NT...ur..[Ljava.
0000020: 6c 61 6e 67 2e 53 74 72 69 6e 67 3b ad d2 56 e7  lang.String;..V.
0000030: e9 1d 7b 47 02 00 00 70 78 70 00 00 00 01 74 00  ..{G...pxp....t.
0000040: 11 48 65 6c 6c 6f 52 4d 49 49 6e 74 65 72 66 61  .HelloRMIInterfa
0000050: 63 65                                            ce

$ java -jar SerializationDumper.jar -r HelloRMI_6_13.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 15 - 0x0f
    Contents - 0x0130c54fde00000170914e54a9800c
  TC_ARRAY - 0x75
    TC_CLASSDESC - 0x72
      className
        Length - 19 - 0x00 13
        Value - [Ljava.lang.String; - 0x5b4c6a6176612e6c616e672e537472696e673b
      serialVersionUID - 0xad d2 56 e7 e9 1d 7b 47
      newHandle 0x00 7e 00 00
      classDescFlags - 0x02 - SC_SERIALIZABLE
      fieldCount - 0 - 0x00 00
      classAnnotations
        TC_NULL - 0x70
        TC_ENDBLOCKDATA - 0x78
      superClassDesc
        TC_NULL - 0x70
    newHandle 0x00 7e 00 01
    Array size - 1 - 0x00 00 00 01
    Values
      Index 0:
        (object)
          TC_STRING - 0x74
            newHandle 0x00 7e 00 02
            Length - 17 - 0x00 11
            Value - HelloRMIInterface - 0x48656c6c6f524d49496e74657266616365

从HelloRMI_6.cap的22、24号报文析取"Serialization Data"，分别保存至
HelloRMI_6_22.bin、HelloRMI_6_24.bin。这是单次r.lookup()的请求与响应。

$ xxd -g 1 HelloRMI_6_22.bin
0000000: ac ed 00 05 77 22 00 00 00 00 00 00 00 00 00 00  ....w"..........
0000010: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 02  ................
0000020: 44 15 4d c9 d4 e6 3b df 74 00 11 48 65 6c 6c 6f  D.M...;.t..Hello
0000030: 52 4d 49 49 6e 74 65 72 66 61 63 65              RMIInterface

$ java -jar SerializationDumper.jar -r HelloRMI_6_22.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 34 - 0x22
    Contents - 0x000000000000000000000000000000000000000000000000000244154dc9d4e63bdf
  TC_STRING - 0x74
    newHandle 0x00 7e 00 00
    Length - 17 - 0x00 11
    Value - HelloRMIInterface - 0x48656c6c6f524d49496e74657266616365

上述Contents字段应该是"ObjectIdentifier Operation Hash"

0002                // opnum = 2
44154dc9d4e63bdf    // 64-bits hash of something

r.lookup()的opnum是2。

$ xxd -g 1 HelloRMI_6_24.bin
0000000: ac ed 00 05 77 0f 01 30 c5 4f de 00 00 01 70 91  ....w..0.O....p.
0000010: 4e 54 a9 80 0d 73 7d 00 00 00 01 00 11 48 65 6c  NT...s}......Hel
0000020: 6c 6f 52 4d 49 49 6e 74 65 72 66 61 63 65 70 78  loRMIInterfacepx
0000030: 72 00 17 6a 61 76 61 2e 6c 61 6e 67 2e 72 65 66  r..java.lang.ref
0000040: 6c 65 63 74 2e 50 72 6f 78 79 e1 27 da 20 cc 10  lect.Proxy.'. ..
0000050: 43 cb 02 00 01 4c 00 01 68 74 00 25 4c 6a 61 76  C....L..ht.%Ljav
0000060: 61 2f 6c 61 6e 67 2f 72 65 66 6c 65 63 74 2f 49  a/lang/reflect/I
0000070: 6e 76 6f 63 61 74 69 6f 6e 48 61 6e 64 6c 65 72  nvocationHandler
0000080: 3b 70 78 70 73 72 00 2d 6a 61 76 61 2e 72 6d 69  ;pxpsr.-java.rmi
0000090: 2e 73 65 72 76 65 72 2e 52 65 6d 6f 74 65 4f 62  .server.RemoteOb
00000a0: 6a 65 63 74 49 6e 76 6f 63 61 74 69 6f 6e 48 61  jectInvocationHa
00000b0: 6e 64 6c 65 72 00 00 00 00 00 00 00 02 02 00 00  ndler...........
00000c0: 70 78 72 00 1c 6a 61 76 61 2e 72 6d 69 2e 73 65  pxr..java.rmi.se
00000d0: 72 76 65 72 2e 52 65 6d 6f 74 65 4f 62 6a 65 63  rver.RemoteObjec
00000e0: 74 d3 61 b4 91 0c 61 33 1e 03 00 00 70 78 70 77  t.a...a3....pxpw
00000f0: 38 00 0b 55 6e 69 63 61 73 74 52 65 66 32 00 00  8..UnicastRef2..
0000100: 0d 31 39 32 2e 31 36 38 2e 36 35 2e 32 33 00 00  .192.168.65.23..
0000110: 05 22 5f 30 bc 6c dd 61 cc 80 e4 a5 d8 2e 00 00  ."_0.l.a........
0000120: 01 70 91 4e 6c c2 80 01 01 78                    .p.Nl....x

动态IP、动态端口在其中。

$ java -jar SerializationDumper.jar -r HelloRMI_6_24.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 15 - 0x0f
    Contents - 0x0130c54fde00000170914e54a9800d
  TC_OBJECT - 0x73
    TC_PROXYCLASSDESC - 0x7d
      newHandle 0x00 7e 00 00
      Interface count - 1 - 0x00 00 00 01
      proxyInterfaceNames
        0:
          Length - 17 - 0x00 11
          Value - HelloRMIInterface - 0x48656c6c6f524d49496e74657266616365
      classAnnotations
        TC_NULL - 0x70
        TC_ENDBLOCKDATA - 0x78
      superClassDesc
        TC_CLASSDESC - 0x72
          className
            Length - 23 - 0x00 17
            Value - java.lang.reflect.Proxy - 0x6a6176612e6c616e672e7265666c6563742e50726f7879
          serialVersionUID - 0xe1 27 da 20 cc 10 43 cb
          newHandle 0x00 7e 00 01
          classDescFlags - 0x02 - SC_SERIALIZABLE
          fieldCount - 1 - 0x00 01
          Fields
            0:
              Object - L - 0x4c
              fieldName
                Length - 1 - 0x00 01
                Value - h - 0x68
              className1
                TC_STRING - 0x74
                  newHandle 0x00 7e 00 02
                  Length - 37 - 0x00 25
                  Value - Ljava/lang/reflect/InvocationHandler; - 0x4c6a6176612f6c616e672f7265666c6563742f496e766f636174696f6e48616e646c65723b
          classAnnotations
            TC_NULL - 0x70
            TC_ENDBLOCKDATA - 0x78
          superClassDesc
            TC_NULL - 0x70
    newHandle 0x00 7e 00 03
    classdata
      java.lang.reflect.Proxy
        values
          h
            (object)
              TC_OBJECT - 0x73
                TC_CLASSDESC - 0x72
                  className
                    Length - 45 - 0x00 2d
                    Value - java.rmi.server.RemoteObjectInvocationHandler - 0x6a6176612e726d692e7365727665722e52656d6f74654f626a656374496e766f636174696f6e48616e646c6572
                  serialVersionUID - 0x00 00 00 00 00 00 00 02
                  newHandle 0x00 7e 00 04
                  classDescFlags - 0x02 - SC_SERIALIZABLE
                  fieldCount - 0 - 0x00 00
                  classAnnotations
                    TC_NULL - 0x70
                    TC_ENDBLOCKDATA - 0x78
                  superClassDesc
                    TC_CLASSDESC - 0x72
                      className
                        Length - 28 - 0x00 1c
                        Value - java.rmi.server.RemoteObject - 0x6a6176612e726d692e7365727665722e52656d6f74654f626a656374
                      serialVersionUID - 0xd3 61 b4 91 0c 61 33 1e
                      newHandle 0x00 7e 00 05
                      classDescFlags - 0x03 - SC_WRITE_METHOD | SC_SERIALIZABLE
                      fieldCount - 0 - 0x00 00
                      classAnnotations
                        TC_NULL - 0x70
                        TC_ENDBLOCKDATA - 0x78
                      superClassDesc
                        TC_NULL - 0x70
                newHandle 0x00 7e 00 06
                classdata
                  java.rmi.server.RemoteObject
                    values
                    objectAnnotation
                      TC_BLOCKDATA - 0x77
                        Length - 56 - 0x38
                        Contents - 0x000b556e69636173745265663200000d3139322e3136382e36352e3233000005225f30bc6cdd61cc80e4a5d82e00000170914e6cc2800101
                      TC_ENDBLOCKDATA - 0x78
                  java.rmi.server.RemoteObjectInvocationHandler
                    values

#### 10.3) rmiregistry_detect.nasl

Nessus提供了一个插件，可以从RMI周知端口转储那些向之注册过的动态端口信息。

启动三个服务端:

$ rmiregistry 1099
$ java HelloRMIDynamicServer8 192.168.65.23 1099 192.168.65.23 1314 HelloRMIInterface
$ java HelloRMIDynamicServer8 192.168.65.23 1099 192.168.65.23 1315 "HelloRMIInterface No 2"

在Windows上运行Nessus插件:

$ nasl -t 192.168.65.23 rmiregistry_detect.nasl

Valid response recieved for port 1099:
0x00:  51 AC ED 00 05 77 0F 01 5D BD E4 1D 00 00 01 70    Q....w..]......p
0x10:  95 D9 61 9F 80 0A 75 72 00 13 5B 4C 6A 61 76 61    ..a...ur..[Ljava
0x20:  2E 6C 61 6E 67 2E 53 74 72 69 6E 67 3B AD D2 56    .lang.String;..V
0x30:  E7 E9 1D 7B 47 02 00 00 70 78 70 00 00 00 02 74    ...{G...pxp....t
0x40:  00 11 48 65 6C 6C 6F 52 4D 49 49 6E 74 65 72 66    ..HelloRMIInterf
0x50:  61 63 65 74 00 16 48 65 6C 6C 6F 52 4D 49 49 6E    acet..HelloRMIIn
0x60:  74 65 72 66 61 63 65 20 4E 6F 20 32                terface No 2


Here is a list of objects the remote RMI registry is currently
aware of :

  rmi://192.168.65.23:1314/HelloRMIInterface
  rmi://192.168.65.23:1315/HelloRMIInterface No 2

单扫rmiregistry_detect.nasl有点慢，可以精简一下。话说我曾经是资深NASL程序
员，你信吗？关于这点，那些年逆向过极光扫描器的的友商兄弟们可以作证，我绝对
不是吹牛。谁曾想十几年后还会来风格化一个NASL插件，真是"再回首，背影已远走"。

NASL语法直白，很容易改写成Python版本。

抓包(HelloRMI_7.cap)。

rmiregistry_detect.nasl的解码能力比nmap脚本rmi-dumpregistry.nse差远了，但
最关键的name与动态端口之间的映射关系被解码后显示出来。

$ nmap -n -Pn -p 1099 --script rmi-dumpregistry.nse 192.168.65.23

PORT     STATE SERVICE
1099/tcp open  java-rmi
| rmi-dumpregistry:
|   HelloRMIInterface
|      implements HelloRMIInterface,
|     extends
|       java.lang.reflect.Proxy
|       fields
|           Ljava/lang/reflect/InvocationHandler; h
|             java.rmi.server.RemoteObjectInvocationHandler
|             @192.168.65.23:1314
|             extends
|               java.rmi.server.RemoteObject
|   HelloRMIInterface No 2
|      implements HelloRMIInterface,
|     extends
|       java.lang.reflect.Proxy
|       fields
|           Ljava/lang/reflect/InvocationHandler; h
|             java.rmi.server.RemoteObjectInvocationHandler
|             @192.168.65.23:1315
|             extends
|_              java.rmi.server.RemoteObject

#### 10.3.1) HelloRMI_7.cap部分报文解码

从HelloRMI_7.cap的7号报文人工析取"Serialization Data"，在hexdump中寻找
"50 ac ed"特征字节流，从"ac ed"开始的数据即"Serialization Data"，保存至
HelloRMI_7_7.bin，这是r.list()的请求报文。Nessus插件向服务端发送数据时把逻
辑上分属不同块的两组数据打包在一起发送，Wireshark未能正常切割它们，只好人
工析取。

$ xxd -g 1 HelloRMI_7_7.bin
0000000: ac ed 00 05 77 22 00 00 00 00 00 00 00 00 00 00  ....w"..........
0000010: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01  ................
0000020: 44 15 4d c9 d4 e6 3b df                          D.M...;.

$ java -jar SerializationDumper.jar -r HelloRMI_7_7.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 34 - 0x22
    Contents - 0x000000000000000000000000000000000000000000000000000144154dc9d4e63bdf

从HelloRMI_7.cap的8号报文析取"Serialization Data"，保存至HelloRMI_7_8.bin，
这是r.list()的响应报文。

$ xxd -g 1 HelloRMI_7_8.bin
0000000: ac ed 00 05 77 0f 01 5d bd e4 1d 00 00 01 70 95  ....w..]......p.
0000010: d9 61 9f 80 10 75 72 00 13 5b 4c 6a 61 76 61 2e  .a...ur..[Ljava.
0000020: 6c 61 6e 67 2e 53 74 72 69 6e 67 3b ad d2 56 e7  lang.String;..V.
0000030: e9 1d 7b 47 02 00 00 70 78 70 00 00 00 02 74 00  ..{G...pxp....t.
0000040: 11 48 65 6c 6c 6f 52 4d 49 49 6e 74 65 72 66 61  .HelloRMIInterfa
0000050: 63 65 74 00 16 48 65 6c 6c 6f 52 4d 49 49 6e 74  cet..HelloRMIInt
0000060: 65 72 66 61 63 65 20 4e 6f 20 32                 erface No 2

$ java -jar SerializationDumper.jar -r HelloRMI_7_8.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 15 - 0x0f
    Contents - 0x015dbde41d0000017095d9619f8010
  TC_ARRAY - 0x75
    TC_CLASSDESC - 0x72
      className
        Length - 19 - 0x00 13
        Value - [Ljava.lang.String; - 0x5b4c6a6176612e6c616e672e537472696e673b
      serialVersionUID - 0xad d2 56 e7 e9 1d 7b 47
      newHandle 0x00 7e 00 00
      classDescFlags - 0x02 - SC_SERIALIZABLE
      fieldCount - 0 - 0x00 00
      classAnnotations
        TC_NULL - 0x70
        TC_ENDBLOCKDATA - 0x78
      superClassDesc
        TC_NULL - 0x70
    newHandle 0x00 7e 00 01
    Array size - 2 - 0x00 00 00 02
    Values
      Index 0:
        (object)
          TC_STRING - 0x74
            newHandle 0x00 7e 00 02
            Length - 17 - 0x00 11
            Value - HelloRMIInterface - 0x48656c6c6f524d49496e74657266616365
      Index 1:
        (object)
          TC_STRING - 0x74
            newHandle 0x00 7e 00 03
            Length - 22 - 0x00 16
            Value - HelloRMIInterface No 2 - 0x48656c6c6f524d49496e74657266616365204e6f2032

从HelloRMI_7.cap的16号报文人工析取"Serialization Data"，在hexdump中寻找
"50 ac ed"特征字节流，从"ac ed"开始的数据即"Serialization Data"，保存至
HelloRMI_7_16.bin，这是第一个r.lookup()的请求报文。

$ xxd -g 1 HelloRMI_7_16.bin
0000000: ac ed 00 05 77 22 00 00 00 00 00 00 00 00 00 00  ....w"..........
0000010: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 02  ................
0000020: 44 15 4d c9 d4 e6 3b df 74 00 11 48 65 6c 6c 6f  D.M...;.t..Hello
0000030: 52 4d 49 49 6e 74 65 72 66 61 63 65              RMIInterface

$ java -jar SerializationDumper.jar -r HelloRMI_7_16.bin

STREAM_MAGIC - 0xac ed
STREAM_VERSION - 0x00 05
Contents
  TC_BLOCKDATA - 0x77
    Length - 34 - 0x22
    Contents - 0x000000000000000000000000000000000000000000000000000244154dc9d4e63bdf
  TC_STRING - 0x74
    newHandle 0x00 7e 00 00
    Length - 17 - 0x00 11
    Value - HelloRMIInterface - 0x48656c6c6f524d49496e74657266616365

HelloRMI_7_16.bin中没有TC_REFERENCE(0x71)，也就不需要newHandle字段。但
newHandle有默认初值0x7e0000，依次递增，SerializationDumper将之显示出来。

## 11) JNDI

JNDI是"Java Naming and Directory Interface"的缩写。它对诸如LDAP、DNS、NIS、
NDS、RMI、CORBA之类的东西进行封装。

### 11.1) HelloRMIDynamicServer5.java (JNDI+RMI)

本着循序渐进的原则，演示JNDI对RMI的封装使用。

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer5.java
 * java HelloRMIDynamicServer5 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import javax.naming.*;

public class HelloRMIDynamicServer5
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_0      = argv[0];
        int                 port_0      = Integer.parseInt( argv[1] );
        String              addr_1      = argv[2];
        int                 port_1      = Integer.parseInt( argv[3] );
        String              name        = argv[4];
        String              url         = String.format( "rmi://%s:%d", addr_0, port_0 );
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        /*
         * 参看javax.naming.Context，Context.INITIAL_CONTEXT_FACTORY的值即
         * 第一形参。第二形参指明此次JNDI对RMI进行封装使用，如果使用其他封
         * 装，需要将第二形参改成其他值。可以不在这里写代码，换用JVM参数指
         * 定。
         */
        System.setProperty( "java.naming.factory.initial", "com.sun.jndi.rmi.registry.RegistryContextFactory" );
        /*
         * 第一形参即Context.PROVIDER_URL。第二形参指定周知端口所在。
         */
        System.setProperty( "java.naming.provider.url", url );
        /*
         * 这次没有显式调用LocateRegistry.getRegistry()
         */
        Context             ctx         = new InitialContext();
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        /*
         * 过去是r.rebind()或Naming.rebind()
         */
        ctx.rebind( name, hello );
    }
}
```

### 11.2) HelloRMIClient5.java

```java
/*
 * javac -encoding GBK -g HelloRMIClient5.java
 * java HelloRMIClient5 192.168.65.23 1099 HelloRMIInterface "Hello World"
 */
import java.util.Properties;
import javax.naming.*;

public class HelloRMIClient5
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr    = argv[0];
        int                 port    = Integer.parseInt( argv[1] );
        String              name    = argv[2];
        String              sth     = argv[3];
        String              url     = String.format( "rmi://%s:%d", addr, port );
        Properties          p       = new Properties();
        /*
         * 演示另一种方案，这次不调System.setProperty()
         */
        p.put( "java.naming.factory.initial", "com.sun.jndi.rmi.registry.RegistryContextFactory" );
        p.put( "java.naming.provider.url", url );
        Context             ctx     = new InitialContext( p );
        /*
         * 过去是r.lookup()或Naming.lookup()
         */
        HelloRMIInterface   hello   = ( HelloRMIInterface )ctx.lookup( name );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

有个构造函数javax.naming.InitialContext(Hashtable)，java.util.Properties继
承自Hashtable，上面的p变量类型直接是java.util.Hashtable也可以。但如果直接
用Hashtable，编译时有警告，"uses unchecked or unsafe operations"，不推荐。

启动两个服务端:

$ rmiregistry 1099
$ java HelloRMIDynamicServer5 192.168.65.23 1099 192.168.65.23 0 HelloRMIInterface

执行客户端:

$ java HelloRMIClient5 192.168.65.23 1099 HelloRMIInterface "Hello World"

本例中JNDI对RMI进行封装使用。假设服务端用了JNDI，客户端并不是必须要用JNDI，
反之亦然。在Windows上测试非JNDI版本的旧版客户端:

$ D:\Java\jdk1.8.0_221\bin\java HelloRMIClient 192.168.65.23 1099 HelloRMIInterface "Hello World From Windows"

看完上面这组示例，就该明白JNDI对RMI的封装是啥概念了，没啥新鲜东西。

### 11.3) HelloRMIDynamicServer6.java

编写HelloRMIDynamicServer6.java，改用JVM参数。

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer6.java
 * java -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://192.168.65.23:1099 HelloRMIDynamicServer6 192.168.65.23 0 HelloRMIInterface
 */
import java.net.InetAddress;
import javax.naming.*;

public class HelloRMIDynamicServer6
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr_1      = argv[0];
        int                 port_1      = Integer.parseInt( argv[1] );
        String              name        = argv[2];
        InetAddress         bindAddr_1  = InetAddress.getByName( addr_1 );
        Context             ctx         = new InitialContext();
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl3( port_1, bindAddr_1 );
        ctx.rebind( name, hello );
    }
}
```

本想用HelloRMIDynamicServer6测试RMI-IIOP，但其中所用HelloRMIInterfaceImpl3
继承的是UnicastRemoteObject，最终未能成功。

### 11.4) HelloRMIClient6.java

编写HelloRMIClient6.java，改用JVM参数。

```java
/*
 * javac -encoding GBK -g HelloRMIClient6.java
 * java -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://192.168.65.23:1099 HelloRMIClient6 HelloRMIInterface "Hello World"
 */
import javax.naming.*;

public class HelloRMIClient6
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name    = argv[0];
        String              sth     = argv[1];
        Context             ctx     = new InitialContext();
        HelloRMIInterface   hello   = ( HelloRMIInterface )ctx.lookup( name );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

启动两个服务端:

$ rmiregistry 1099
$ java -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://192.168.65.23:1099 HelloRMIDynamicServer6 192.168.65.23 0 HelloRMIInterface

执行客户端:

$ java -Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory -Djava.naming.provider.url=rmi://192.168.65.23:1099 HelloRMIClient6 HelloRMIInterface "Hello World"

## 12) RMI-IIOP

据说前面那种Java RMI底层用的协议是JRMP(Java Remote Method Protocol)。如果
底层协议换成IIOP(Internet Inter-ORB Protocol)，就是Java RMI Over IIOP。号
称RMI-IIOP为RMI增加了CORBA(Common Object Request Broker Architecture)的啥
能力。

并不完全理解前面这段话，没有系统翻看过官方文档。关于JRMP、IIOP，回头抓包看
了再说。我不是来学习开发的，是来搞事的。

### 12.1) HelloRMIInterfaceImpl7.java

```java
/*
 * javac -encoding GBK -g HelloRMIInterfaceImpl7.java
 * rmic -iiop HelloRMIInterfaceImpl7
 */
import java.rmi.RemoteException;
import javax.rmi.PortableRemoteObject;

/*
 * By extending PortableRemoteObject, the HelloRMIInterfaceImpl7 class can
 * be used to create a remote object that uses IIOP-based transport for
 * communication.
 *
 * HelloRMIInterfaceImpl3继承的是UnicastRemoteObject
 */
public class HelloRMIInterfaceImpl7 extends PortableRemoteObject implements HelloRMIInterface
{
    protected HelloRMIInterfaceImpl7 () throws RemoteException
    {
        /*
         * https://docs.oracle.com/javase/8/docs/api/javax/rmi/PortableRemoteObject.html
         */
        super();
    }

    @Override
    public String Echo ( String sth ) throws RemoteException
    {
        return( "[" + sth + "]" );
    }
}
```

#### 12.1.1) rmic

rmic是JDK自带工具，有man手册。

$ javac -encoding GBK -g HelloRMIInterfaceImpl7.java

在已经生成HelloRMIInterfaceImpl7.class的情况下执行rmic命令:

$ rmic -iiop HelloRMIInterfaceImpl7
$ ls -l _*.class
-rw-rw-r--. 1 scz scz 2588 Feb 27 17:12 _HelloRMIInterfaceImpl7_Tie.class
-rw-rw-r--. 1 scz scz 2855 Feb 27 17:12 _HelloRMIInterface_Stub.class

前述rmic命令会生成两个文件:

_HelloRMIInterfaceImpl7_Tie.class   // the server skeleton
_HelloRMIInterface_Stub.class       // the client stub

执行HelloRMIDynamicServer7时需要二者同时在场，只有前者不够。执行
HelloRMIClient7时只需要后者在场。编译HelloRMIDynamicServer7.java、
HelloRMIClient7.java时不需要它们。

$ rmic -iiop HelloRMIInterfaceImpl3
error: java.rmi.server.RemoteServer is not a valid remote implementation: has no remote interfaces.

rmic不能用于HelloRMIInterfaceImpl3，因为HelloRMIInterfaceImpl3继承自
UnicastRemoteObject，而不是PortableRemoteObject。

### 12.2) HelloRMIDynamicServer7.java (JNDI+CORBA)

```java
/*
 * javac -encoding GBK -g HelloRMIDynamicServer7.java
 * java HelloRMIDynamicServer7 192.168.65.23 1050 HelloRMIInterface
 */
import java.util.Properties;
import javax.naming.*;

public class HelloRMIDynamicServer7
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr    = argv[0];
        int                 port    = Integer.parseInt( argv[1] );
        String              name    = argv[2];
        String              url     = String.format( "iiop://%s:%d", addr, port );
        Properties          p       = new Properties();
        /*
         * 第二形参不再是"com.sun.jndi.rmi.registry.RegistryContextFactory"
         */
        p.put( "java.naming.factory.initial", "com.sun.jndi.cosnaming.CNCtxFactory" );
        p.put( "java.naming.provider.url", url );
        Context             ctx     = new InitialContext( p );
        /*
         * 必须用HelloRMIInterfaceImpl7，而不是HelloRMIInterfaceImpl3
         */
        HelloRMIInterface   hello   = new HelloRMIInterfaceImpl7();
        ctx.rebind( name, hello );
    }
}
```

### 12.3) HelloRMIClient7.java

```java
/*
 * javac -encoding GBK -g HelloRMIClient7.java
 * java HelloRMIClient7 192.168.65.23 1050 HelloRMIInterface "Hello World"
 */
import java.util.Properties;
import javax.naming.*;

public class HelloRMIClient7
{
    public static void main ( String[] argv ) throws Exception
    {
        String              addr    = argv[0];
        int                 port    = Integer.parseInt( argv[1] );
        String              name    = argv[2];
        String              sth     = argv[3];
        String              url     = String.format( "iiop://%s:%d", addr, port );
        Properties          p       = new Properties();
        p.put( "java.naming.factory.initial", "com.sun.jndi.cosnaming.CNCtxFactory" );
        p.put( "java.naming.provider.url", url );
        Context             ctx     = new InitialContext( p );
        HelloRMIInterface   hello   = ( HelloRMIInterface )ctx.lookup( name );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

上例中ctx.lookup()返回值被强制类型转换。看一些旧文档，提到此处不能强制类型
转换，必须调用javax.rmi.PortableRemoteObject.narrow()，可能那是过去的限制
吧，反正我没这么干。

### 12.4) orbd

orbd是"Object Request Broker Daemon"的缩写。

orbd是JDK自带的，有man手册。这是CORBA相关的，地位相当于rmiregistry。

侦听周知端口:

$ orbd -ORBInitialPort 1050

相当于:

$ orbd -ORBInitialPort 1050 -port 1049 -defaultdb ./orb.db

上例中的1050、1049/TCP合一起，其地位相当于rmiregistry的1099/TCP，后面抓包
时再细说。

--------------------------------------------------------------------------
-ORBInitialPort nameserverport

    Required. Specifies the port on which the name server should be
    started. After it is started, orbd listens for incoming requests on
    this port.

    这个没有缺省值。但一般指定成1050/TCP，此时Wireshark可以对其解码，否则
    Wireshark不对其解码。

 -port port

    Specifies the activation port where ORBD should be started, and where
    ORBD will be accepting requests for persistent objects. The default
    value for this port is 1049. This port number is added to the port
    field of the persistent Interoperable Object References (IOR).

    这个有缺省值1049/TCP。Wireshark对1049/TCP上的通信做了解码。

-defaultdb directory

    Specifies the base where the ORBD persistent storage directory. If
    this option is not specified, then the default value is ./orb.db.

    "./orb.db"是目录名，不是文件名，会自动创建之。
--------------------------------------------------------------------------

#### 12.4.1) inside orbd

orbd跟rmiregistry一样调用了JLI_Launch()。

$ jinfo <pid>
...
sun.java.command = com.sun.corba.se.impl.activation.ORBD -ORBInitialPort 1050
...

$ jps -mlv

$ orbd -ORBInitialPort 1050

相当于:

java \
-Dcom.sun.CORBA.activation.DbDir=./orb.db \
-Dcom.sun.CORBA.activation.Port=1049 \
-Dcom.sun.CORBA.POA.ORBServerId=1 \
com.sun.corba.se.impl.activation.ORBD -ORBInitialPort 1050

com.sun.corba.se.impl.activation.ORBD中有main()。

### 12.5) 测试RMI-IIOP

假设rmic已经执行过。启动两个服务端:

$ orbd -ORBInitialPort 1050
$ java HelloRMIDynamicServer7 192.168.65.23 1050 HelloRMIInterface

共有3个端口在侦听中:

$ netstat -nltp | egrep "orbd|java"
tcp        0      0 0.0.0.0:1049            0.0.0.0:*               LISTEN      24423/orbd
tcp        0      0 0.0.0.0:1050            0.0.0.0:*               LISTEN      24423/orbd
tcp        0      0 0.0.0.0:32910           0.0.0.0:*               LISTEN      24446/java

32910/TCP是动态端口。

执行客户端:

$ java HelloRMIClient7 192.168.65.23 1050 HelloRMIInterface "Hello World"

HelloRMIClient6可以用于测试RMI-IIOP:

$ java -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory -Djava.naming.provider.url=iiop://192.168.65.23:1050 HelloRMIClient6 HelloRMIInterface "Hello World"

#### 12.5.1) HelloRMIDynamicServer7/HelloRMIClient7不在同一台主机上时的幺蛾子

在Windows中放三个类:

HelloRMIClient7.class
HelloRMIInterface.class
_HelloRMIInterface_Stub.class

在Windows中执行客户端，失败:

$ java.exe HelloRMIClient7 192.168.65.23 1050 HelloRMIInterface "Hello World From Windows"
二月 27, 2020 9:33:01 下午 com.sun.corba.se.impl.transport.SocketOrChannelConnectionImpl <init>
警告: "IOP00410201: (COMM_FAILURE) Connection failure: socketType: IIOP_CLEAR_TEXT; hostname: 127.0.0.1; port: 1049"
org.omg.CORBA.COMM_FAILURE:   vmcid: SUN  minor code: 201  completed: No
        at com.sun.corba.se.impl.logging.ORBUtilSystemException.connectFailure(ORBUtilSystemException.java:2200)
        at com.sun.corba.se.impl.logging.ORBUtilSystemException.connectFailure(ORBUtilSystemException.java:2221)
        at com.sun.corba.se.impl.transport.SocketOrChannelConnectionImpl.<init>(SocketOrChannelConnectionImpl.java:223)
        at com.sun.corba.se.impl.transport.SocketOrChannelConnectionImpl.<init>(SocketOrChannelConnectionImpl.java:236)
        at com.sun.corba.se.impl.transport.SocketOrChannelContactInfoImpl.createConnection(SocketOrChannelContactInfoImpl.java:119)
        at com.sun.corba.se.impl.protocol.CorbaClientRequestDispatcherImpl.beginRequest(CorbaClientRequestDispatcherImpl.java:187)
        at com.sun.corba.se.impl.protocol.CorbaClientDelegateImpl.request(CorbaClientDelegateImpl.java:137)
        at com.sun.corba.se.impl.protocol.CorbaClientDelegateImpl.is_a(CorbaClientDelegateImpl.java:229)
        at com.sun.corba.se.impl.protocol.CorbaClientDelegateImpl.is_a(CorbaClientDelegateImpl.java:239)
        at org.omg.CORBA.portable.ObjectImpl._is_a(ObjectImpl.java:130)
        at org.omg.CosNaming.NamingContextHelper.narrow(NamingContextHelper.java:69)
        at com.sun.jndi.cosnaming.CNCtx.setOrbAndRootContext(CNCtx.java:434)
        at com.sun.jndi.cosnaming.CNCtx.initUsingIiopUrl(CNCtx.java:329)
        at com.sun.jndi.cosnaming.CNCtx.initUsingUrl(CNCtx.java:298)
        at com.sun.jndi.cosnaming.CNCtx.initOrbAndRootContext(CNCtx.java:266)
        at com.sun.jndi.cosnaming.CNCtx.<init>(CNCtx.java:120)
        at com.sun.jndi.cosnaming.CNCtxFactory.getInitialContext(CNCtxFactory.java:49)
        at javax.naming.spi.NamingManager.getInitialContext(NamingManager.java:684)
        at javax.naming.InitialContext.getDefaultInitCtx(InitialContext.java:313)
        at javax.naming.InitialContext.init(InitialContext.java:244)
        at javax.naming.InitialContext.<init>(InitialContext.java:216)
        at HelloRMIClient7.main(HelloRMIClient7.java:20)
Caused by: java.net.ConnectException: Connection refused: connect
        at sun.nio.ch.Net.connect0(Native Method)
        at sun.nio.ch.Net.connect(Net.java:454)
        at sun.nio.ch.Net.connect(Net.java:446)
        at sun.nio.ch.SocketChannelImpl.connect(SocketChannelImpl.java:648)
        at java.nio.channels.SocketChannel.open(SocketChannel.java:189)
        at com.sun.corba.se.impl.transport.DefaultSocketFactoryImpl.createSocket(DefaultSocketFactoryImpl.java:95)
        at com.sun.corba.se.impl.transport.SocketOrChannelConnectionImpl.<init>(SocketOrChannelConnectionImpl.java:207)
        ... 19 more

抓包(HelloRMI_4.cap)。首先会访问192.168.65.23:1050/TCP，响应报文中指明
127.0.0.1:1049/TCP。猜测此处存在HelloRMIServer曾经面临的类似问题，响应报文
中的动态IP字段问题。实测表明指定"java.rmi.server.hostname"不能解决orbd的问
题。曾经测试过:

$ orbd -J-Djava.rmi.server.hostname=192.168.65.23 -ORBInitialPort 1050

用jinfo确认"java.rmi.server.hostname"设置成功，但没能解决问题。只好用最蠢
的办法:

$ vi /etc/hosts

192.168.65.23   RedHat

$ hostname RedHat

多说一句，hostname有两个相关文件:

/etc/hostname
/proc/sys/kernel/hostname

前者是静态设置，重启生效；后者是hostname命令实际修改的文件，热生效。

重新启动两个服务端:

$ orbd -ORBInitialPort 1050
$ java HelloRMIDynamicServer7 192.168.65.23 1050 HelloRMIInterface

检查服务端侦听的端口:

$ netstat -nltp | egrep "orbd|java"
tcp        0      0 0.0.0.0:1049            0.0.0.0:*               LISTEN      24700/orbd
tcp        0      0 0.0.0.0:1050            0.0.0.0:*               LISTEN      24700/orbd
tcp        0      0 0.0.0.0:39704           0.0.0.0:*               LISTEN      24715/java

在Windows中执行客户端:

$ java.exe HelloRMIClient7 192.168.65.23 1050 HelloRMIInterface "Hello World From Windows"

抓包(HelloRMI_5.cap)。首先访问1050/TCP，从响应报文中析取IP、PORT，比如
192.168.65.23:1049/TCP。其次访问1049/TCP，提交name，获取name对应的IP和动态
端口，比如192.168.65.23:39704/TCP。最后访问39704/TCP，进行RPC调用，值得一
提的是，Wireshark对动态端口上的通信也做了解码。

如果客户端缺少_HelloRMIInterface_Stub.class，会报错:

Exception in thread "main" java.lang.ClassCastException: com.sun.corba.se.impl.corba.CORBAObjectImpl cannot be cast to HelloRMIInterface
        at HelloRMIClient7.main(HelloRMIClient7.java:21)

在Windows中放三个类:

HelloRMIClient6.class
HelloRMIInterface.class
_HelloRMIInterface_Stub.class

在Windows中用HelloRMIClient6测试RMI-IIOP:

$ java.exe -Djava.naming.factory.initial=com.sun.jndi.cosnaming.CNCtxFactory -Djava.naming.provider.url=iiop://192.168.65.23:1050 HelloRMIClient6 HelloRMIInterface "Hello World"

### 12.6) RMI-IIOP vs RMI

orbd比rmiregistry重，为了获取动态端口，前者居然有两个周知端口介入，后者只
有一个周知端口介入。

当服务端、客户端位于不同主机时，orbd只能用/etc/hosts之类的手段解决麻烦，而
rmiregistry则自动处理了麻烦。

orbd的两个周知端口都只能侦听在0.0.0.0上，没法侦听在指定IP上。rmiregistry也
一样。但HelloRMIWellknownServer可以让周知端口侦听在指定IP上。

HelloRMIDynamicServer7没法指定动态端口侦听在哪个IP上，只能侦听在0.0.0.0上。
HelloRMIDynamicServer可以让动态端口侦听在指定IP上。

RMI-IIOP比RMI还要古老。在2020年这个时间点上，如果不是为了演示JNDI对不同组
件的封装使用，我也不会关注RMI-IIOP。

# ☆ JNDI+LDAP

## 1) 简版LDAP Server

Simple all-in-one LDAP server (wrapped ApacheDS)
https://github.com/kwart/ldap-server

## 2) jndi.ldif

--------------------------------------------------------------------------
dn: o=anything,dc=evil,dc=com
objectclass: top
objectclass: organization
o: anything
--------------------------------------------------------------------------

这是我瞎写的，不懂LDAP，不知道该怎么弄一个最简.ldif文件，至少这个能用。

$ java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

## 3) HelloRMIInterface.java

同前

## 4) HelloRMIInterfaceImpl.java

同前

## 5) JNDILDAPServer.java

```java
/*
 * javac -encoding GBK -g JNDILDAPServer.java
 */
import javax.naming.directory.*;

public class JNDILDAPServer
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name        = argv[0];
        String              codebase    = argv[1];
        /*
         * InitialDirContext的rebind()才能指定Attributes。不能用
         * InitialContext。
         */
        DirContext          ctx         = new InitialDirContext();
        HelloRMIInterface   hello       = new HelloRMIInterfaceImpl();
        /*
         * LDAP好像没有类似java.rmi.server.codebase这种JVM参数，为了指定
         * codebase，只能用这种办法。
         */
        ctx.rebind( name, hello, new BasicAttributes( "javaCodebase", codebase ) );
    }
}
```

## 6) JNDILDAPClient.java

```java
/*
 * javac -encoding GBK -g JNDILDAPClient.java
 */
import javax.naming.directory.*;

public class JNDILDAPClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name    = argv[0];
        String              sth     = argv[1];
        DirContext          ctx     = new InitialDirContext();
        HelloRMIInterface   hello   = ( HelloRMIInterface )ctx.lookup( name );
        String              resp    = hello.Echo( sth );
        System.out.println( resp );
    }
}
```

这个.java没必要，可以用HelloRMIClient6.java做客户端。

## 7) 编译

javac -encoding GBK -g HelloRMIInterface.java
javac -encoding GBK -g HelloRMIInterfaceImpl.java
javac -encoding GBK -g JNDILDAPServer.java
javac -encoding GBK -g JNDILDAPClient.java

## 8) 测试

假设目录结构是:

.
|
+---test0
|       jndi.ldif
|       ldap-server.jar
|
+---test1
|       HelloRMIInterface.class
|       HelloRMIInterfaceImpl.class
|       JNDILDAPServer.class
|
+---test2
|       HelloRMIInterface.class
|       JNDILDAPClient.class
|
\---testserverbase
        HelloRMIInterfaceImpl.class

各目录下有哪些.class是精心设计过的，不要多也不要少。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPServer cn=any http://192.168.65.23:8080/testserverbase/

LDAP好像没有类似java.rmi.server.codebase这种JVM参数，为了指定codebase，只
能在JNDILDAPServer.java中用"javaCodebase"属性指定。

在test2目录执行:

java \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPClient cn=any "msg from client"

KINGX指出，2018年10月Java 8u191开始，trustURLCodebase默认为false，此时客户
端忽略远程codebase。太遗憾了，以前没关注过Java漏洞，这么大的洞等我关注时已
经默认被堵。

JNDILDAPClient不需要SecurityManager和.policy文件。LDAP在这点上与RMI不同，
后者只要使用远程codebase，必须启用SecurityManager。

测试正常，客户端输出:

[msg from client]

HTTP Server收到两个GET请求:

"GET /testserverbase/HelloRMIInterfaceImpl.class HTTP/1.1" 200
"GET /testserverbase/HelloRMIInterfaceImpl_Stub.class HTTP/1.1" 404

尽管第2个GET请求404了，JNDILDAPClient还是正常结束。这里有点蹊跷，回头调调。
这次没有缓存机制在其中，每次执行客户端都会触发GET请求。

服务端为了指定codebase必须用InitialDirContext()，客户端可以继续用
InitialContext()。比如:

java \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/ \
HelloRMIClient6 cn=any,o=anything,dc=evil,dc=com "msg from client"

### 8.1) 为何有个GET请求404时客户端仍然正常结束

在test2目录以调试方式启动客户端:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPClient cn=any "msg from client"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

或者用Eclipse跟踪。通过断点发现HelloRMIInterfaceImpl.Echo()实际在客户端执
行，而不是在服务端被执行后远程返回。

"GET /testserverbase/HelloRMIInterfaceImpl.class HTTP/1.1" 200

客户端的ctx.lookup()会触发这个GET请求，但不会自动调用HelloRMIInterfaceImpl
的构造函数，事实上通过设断发现HelloRMIInterfaceImpl.<init>从未在客户端被调
用过。

在客户端用Eclipse的条件断点断下来，查看调用栈回溯:

(new String(b)).startsWith("GET /testserverbase/HelloRMIInterfaceImpl.class")

Thread [main] (Suspended (breakpoint at line 101 in java.net.SocketOutputStream))
    java.net.SocketOutputStream.socketWrite(byte[], int, int) line: 101
    java.net.SocketOutputStream.write(byte[], int, int) line: 155
    java.io.BufferedOutputStream.flushBuffer() line: 82
    java.io.BufferedOutputStream.flush() line: 140
    java.io.PrintStream.flush() line: 338
    sun.net.www.MessageHeader.print(java.io.PrintStream) line: 301
    sun.net.www.http.HttpClient.writeRequests(sun.net.www.MessageHeader, sun.net.www.http.PosterOutputStream) line: 644
    sun.net.www.http.HttpClient.writeRequests(sun.net.www.MessageHeader, sun.net.www.http.PosterOutputStream, boolean) line: 655
    sun.net.www.protocol.http.HttpURLConnection.writeRequests() line: 694
    sun.net.www.protocol.http.HttpURLConnection.getInputStream0() line: 1591
    sun.net.www.protocol.http.HttpURLConnection.getInputStream() line: 1498
    sun.misc.URLClassPath$Loader.getResource(java.lang.String, boolean) line: 747
    sun.misc.URLClassPath.getResource(java.lang.String, boolean) line: 249
    java.net.URLClassLoader$1.run() line: 366
    java.net.URLClassLoader$1.run() line: 363
    java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
    java.net.FactoryURLClassLoader(java.net.URLClassLoader).findClass(java.lang.String) line: 362
    java.net.FactoryURLClassLoader(java.lang.ClassLoader).loadClass(java.lang.String, boolean) line: 418
    java.net.FactoryURLClassLoader.loadClass(java.lang.String, boolean) line: 817
    java.net.FactoryURLClassLoader(java.lang.ClassLoader).loadClass(java.lang.String) line: 351
    com.sun.jndi.ldap.Obj$LoaderInputStream.resolveClass(java.io.ObjectStreamClass) line: 619
    com.sun.jndi.ldap.Obj$LoaderInputStream(java.io.ObjectInputStream).readNonProxyDesc(boolean) line: 1867
    com.sun.jndi.ldap.Obj$LoaderInputStream(java.io.ObjectInputStream).readClassDesc(boolean) line: 1750
    com.sun.jndi.ldap.Obj$LoaderInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2041
    com.sun.jndi.ldap.Obj$LoaderInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
    com.sun.jndi.ldap.Obj$LoaderInputStream(java.io.ObjectInputStream).readObject() line: 430
    com.sun.jndi.ldap.Obj.deserializeObject(byte[], java.lang.ClassLoader) line: 531
    com.sun.jndi.ldap.Obj.decodeObject(javax.naming.directory.Attributes) line: 239
    com.sun.jndi.ldap.LdapCtx.c_lookup(javax.naming.Name, com.sun.jndi.toolkit.ctx.Continuation) line: 1051
    com.sun.jndi.ldap.LdapCtx(com.sun.jndi.toolkit.ctx.ComponentContext).p_lookup(javax.naming.Name, com.sun.jndi.toolkit.ctx.Continuation) line: 542
    com.sun.jndi.ldap.LdapCtx(com.sun.jndi.toolkit.ctx.PartialCompositeContext).lookup(javax.naming.Name) line: 177
    com.sun.jndi.ldap.LdapCtx(com.sun.jndi.toolkit.ctx.PartialCompositeContext).lookup(java.lang.String) line: 166
    javax.naming.InitialContext.lookup(java.lang.String) line: 417
    HelloRMIClient6.main(java.lang.String[]) line: 14

为了进一步确认，在test1目录以调试方式启动服务端:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPServer cn=any http://192.168.65.23:8080/testserverbase/

在服务端对HelloRMIInterfaceImpl.Echo()设断，流程根本未经过。

感觉是这样的，JNDILDAPClient直接在本地执行临时下载的HelloRMIInterfaceImpl.Echo()。
这真是骚操作，如果没看HTTP Server日志，就这么被它糊弄过去了。

## 9) HelloRMIInterfaceImpl8.java

同前

## 10) JNDILDAPServer2.java

```java
/*
 * javac -encoding GBK -g JNDILDAPServer2.java
 */
import javax.naming.directory.*;
import java.rmi.server.UnicastRemoteObject;

public class JNDILDAPServer2
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name        = argv[0];
        String              codebase    = null;
        if ( argv.length > 1 )
        {
            codebase    = argv[1];
        }
        DirContext          ctx         = new InitialDirContext();
        /*
         * HelloRMIInterfaceImpl8没有继承UnicastRemoteObject
         */
        HelloRMIInterface   obj         = new HelloRMIInterfaceImpl8();
        HelloRMIInterface   hello       = ( HelloRMIInterface )UnicastRemoteObject.exportObject( obj, 0 );
        if ( codebase != null )
        {
            ctx.rebind( name, hello, new BasicAttributes( "javaCodebase", codebase ) );
        }
        else
        {
            ctx.rebind( name, hello, null );
        }
    }
}
```

编译:

javac -encoding GBK -g HelloRMIInterfaceImpl8.java
javac -encoding GBK -g JNDILDAPServer2.java

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPServer2 cn=any

没有指定codebase。

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
JNDILDAPClient cn=any "msg from client"

不涉及trustURLCodebase。

HTTP Server没有收到任何请求，说明UnicastRemoteObject.exportObject()这种搞
法下，用了非codebase的机制，更透明，比"extends UnicastRemoteObject"效果理
想。这是LdapCtxFactory的测试结论，RegistryContextFactory没有这个结论。

# ☆ 后记

后来我看了一些Java程序员写的关于RMI的文章，他们可能使用了官方术语。如果你
是前言中假设的那类读者，完全没必要理会那些官方术语，让我们回归最本质的放之
四海而皆准的RPC架构。

对于DCE/MS RPC、ONC/Sun RPC，当年它们流行的时候，底层是C语言，在序列化、反
序列化过程中出现过很多严重安全漏洞，Windows、Solaris曾被打成筛子一般。这种
类型的漏洞与如今的Java反序列化漏洞不是一类，更接近底层，与协议解码强相关。

另一点要提一下，RPC架构所涉及的动态端口才是RPC真正进行的通道，周知端口存在
的目的是方便客户端查询获取动态端口。如果客户端用其他手段得知动态端口，理论
上可以不访问周知端口而直接访问动态端口发起RPC调用。对于DCE/MS RPC、ONC/Sun
RPC，这不新鲜，对于Java RMI，有心人可以琢磨一下。

不展开安全相关的内容了，毕竟本文标题是"Java RMI入门"。