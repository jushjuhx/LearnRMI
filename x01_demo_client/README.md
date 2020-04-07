# HelloRMIClientWithUnknownClass说明
RMI调用时，服务端返回了一个客户端不存在的类的对象`Messagge`，导致代码最终不会正常执行。
`java.io.ObjectInputStream.readOrdinaryObject`最终没有获取到类，调用堆栈如下：
```text
java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1795)
java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1347)
java.io.ObjectInputStream.readObject(ObjectInputStream.java:369)
sun.rmi.server.UnicastRef.unmarshalValue(UnicastRef.java:324)
sun.rmi.server.UnicastRef.invoke(UnicastRef.java:173)
java.rmi.server.RemoteObjectInvocationHandler.invokeRemoteMethod(RemoteObjectInvocationHandler.java:194)
java.rmi.server.RemoteObjectInvocationHandler.invoke(RemoteObjectInvocationHandler.java:148)
sun.proxy.$Proxy0.EchoObject(Unknown Source)
HelloRMIClientWithUnknownClass.main(HelloRMIClientWithUnknownClass.java:24)
```
