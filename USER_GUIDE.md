# Pigeon开发指南

Pigeon是一个分布式服务通信框架（RPC），在美团点评内部广泛使用，是美团点评最基础的底层框架之一。

## 主要特色

除了支持spring schema等配置方式，也支持代码annotation方式发布服务、引用远程服务，并提供原生api接口的用法。

支持http协议，方便非java应用调用pigeon的服务。

序列化方式除了hessian，还支持thrift等。

提供了服务器单机控制台pigeon-console，包含单机服务测试工具。

创新的客户端路由策略，提供服务预热功能，解决线上流量大的service重启时大量超时的问题。

记录每个请求的对象大小、返回对象大小等监控信息。

服务端可对方法设置单独的线程池进行服务隔离，可配置客户端应用的最大并发数进行限流。


## 依赖

Pigeon依赖JDK1.7+

pom依赖定义：
```xml
<dependency>
    <groupId>com.dianping</groupId>
	<artifactId>pigeon</artifactId>
	<version>${pigeon.version}</version>
</dependency>
```

pom里加入以下仓库依赖：
```xml
<repositories>
    <repository>
        <id>github-dianping-maven-repo</id>
        <url>https://raw.githubusercontent.com/dianping/maven-repo/master</url>
        <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>
    </snapshots>
    </repository>
</repositories>
```

pigeon在运行时可能会依赖以下jar包，如果有必要，需要应用自行加上以下jar(版本建议高于或等于以下基础版本)：
```xml
<!-- 监控框架依赖，下面的cat依赖是可选的，如果不依赖cat则默认不会有监控功能，如果想接入美团点评的监控框架cat（已经开源），需增加以下依赖（pigeon-monitor-cat代码在https://github.com/wu-xiang/pigeon-monitor-cat） -->
<dependency>
    <groupId>com.dianping</groupId>
    <artifactId>pigeon-monitor-cat</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.dianping.cat</groupId>
    <artifactId>cat-core</artifactId>
    <version>1.3.6-SNAPSHOT</version>
</dependency>

<!-- 配置框架依赖，下面的lion依赖是可选的，如果不依赖lion则会默认通过本地文件加载配置，如果想接入美团点评的配置框架lion(尚未开源)，需增加以下依赖（pigeon-config-lion代码在https://github.com/wu-xiang/pigeon-config-lion） -->
<dependency>
    <groupId>com.dianping</groupId>
    <artifactId>pigeon-config-lion</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.dianping.lion</groupId>
    <artifactId>lion-client</artifactId>
    <version>0.5.3</version>
</dependency>

<!-- 加入spring，版本根据自身需要设置，spring.version可以是大多数spring版本如3.2.9.RELEASE -->
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-context</artifactId>
    <version>${spring.version}</version>
</dependency>

<!-- 如果是非tomcat项目需要自行加入servlet-api的jar，servlet.version可以是2.5-20081211 -->
<dependency>
    <groupId>org.mortbay.jetty</groupId>
    <artifactId>servlet-api</artifactId>
    <version>${servlet.version}</version>
</dependency>

<!-- 需要自行加入swift的jar，swift.version可以是0.17.0或更高版本 -->
<dependency>
	<groupId>com.facebook.swift</groupId>
	<artifactId>swift-annotations</artifactId>
	<version>${swift.version}</version>
</dependency>
<dependency>
	<groupId>com.facebook.swift</groupId>
	<artifactId>swift-codec</artifactId>
	<version>${swift.version}</version>
</dependency>
<dependency>
	<groupId>com.facebook.swift</groupId>
	<artifactId>swift-generator</artifactId>
	<version>${swift.version}</version>
</dependency>
```
## 准备工作

如果是在外部公司使用开源版本pigeon，需要关注此章节，进行一些准备工作：

### 通过maven构建项目
```bash
git clone https://github.com/dianping/pigeon.git pigeon-parent

cd pigeon-parent

mvn clean install -DskipTests
```
### 准备环境

#### ZooKeeper安装
pigeon内部使用zookeeper作为注册中心，需要安装好zookeeper集群。

#### 配置ZooKeeper集群地址
如未使用美团点评配置框架Lion，需在应用代码resources/config/pigeon.properties里（也可以在绝对路径/data/webapps/config/pigeon.properties里）设置注册中心zookeeper地址：
```
pigeon.registry.address=10.1.1.1:2181,10.1.1.2:2181,10.1.1.3:2181,10.1.1.4:2181,10.1.1.5:2181
```

### 配置服务摘除脚本

由于pigeon内部是在zookeeper里使用持久化节点，如果非正常关闭JVM，不会从ZooKeeper集群里摘除相应的本机服务的ip、port，需要在关闭JVM脚本里（比如tomcat的shutdown.sh脚本）加入以下调用：
```bash
/usr/bin/curl -s --connect-timeout 5  --speed-time 6 --speed-limit 1 "http://127.0.0.1:4080/services.unpublish"
```
该脚本内部会等待3秒，如果成功会返回ok，等该脚本执行成功再关闭JVM

### 配置应用名称

在应用代码resources/META-INF/app.properties文件里设置
```
app.name=xxx
```
代表此应用名称为xxx，定义应用名称是基于规范应用的考虑


## 配置管理
pigeon内部有一系列配置，用于不同应用进行定制，应用级的配置是独立的config模块来支撑，配置的管理可以有以下3个选择：

### 本地配置
本地配置是在机器级别配置properties文件里存储，开源版本如果没有依赖其他扩展的配置中心，默认会用本地配置。

本地配置是在应用的classpath下放config/pigeon.properties文件（该文件的配置是所有环境都生效，包括关闭线上自动注册，请谨慎使用）

如果是只设置某个环境的配置，如开发环境可以用pigeon_dev.properties，测试环境可以用pigeon_qa.properties，环境定义如dev、qa可自行定义

如果是全局配置，这个配置文件也可以放在绝对路径/data/webapps/config/pigeon/pigeon.properties文件里

例如pigeon内部有一个全局默认配置pigeon.provider.applimit.enable，值为false，如果某个应用想修改这个默认配置，可以在properties文件里增加一行：

pigeon.provider.applimit.enable=true

### lion配置
如果使用了点评内部的lion配置中心，相比本地配置管理上更加方便，在lion管理端进行配置的统一管理，无需在每台机器上的properties文件里进行配置

lion配置需要按前面依赖里提到的引入以下依赖：
```xml
<dependency>
    <groupId>com.dianping</groupId>
    <artifactId>pigeon-config-lion</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```
如果要设置某个应用级的pigeon配置，需要在lion里增加相应的pigeon配置，如pigeon内部有一个全局默认配置pigeon.provider.applimit.enable，值为false

如果某个应用xxx-service（这个应用名就是app.properties里的app.name）想修改这个默认配置，那么可以在lion里增加一个key：xxx-service.pigeon.provider.applimit.enable，设置为true

本文档里提到的所有配置，如果要在应用级修改配置，都需要按这个规则在lion进行配置。

应用级的配置优先级高于pigeon内部的默认配置，比如xxx-service.pigeon.provider.applimit.enable的优先级高于pigeon.provider.applimit.enable

### 扩展pigeon-config模块
如果外部公司想集成自己公司内部的配置中心，可以扩展pigeon的pigeon-config模块，需要继承AbstractConfigManager：
```java
public class XXXConfigManager extends AbstractConfigManager
```
然后在自己的classpath下放META-INF/services/com.dianping.pigeon.config.ConfigManager文件，文件内容是扩展类的类名：

com.xxx....XXXConfigManager

完成以上步骤后，需要将这个扩展项目打成jar包，引入项目里，pigeon就会自动使用用户自己扩展的配置模块

## 快速入门

本文档相关代码示例也可以参考[pigeon-demo](https://github.com/dianping/pigeon-demo)项目

### 定义服务

定义服务接口: (该接口需单独打包，在服务提供方和调用方共享)

> EchoService.java

```java
package com.dianping.pigeon.demo;

public interface EchoService {

	public String echo(String name);
	
}
```
在服务提供方实现接口：(对服务调用方隐藏实现)

> EchoServiceImpl.java

```
package com.dianping.pigeon.demo.provider;

import com.dianping.pigeon.demo.EchoService;

public class EchoServiceImpl implements EchoService {

	public String echo(String name) {
		return "Hello " + name;
	}
	
}
```
### 服务提供者

这里先介绍传统spring方式，后边章节会介绍annotation方式、spring schema定义方式、api方式。

Spring配置声明暴露服务：

> provider.xml

services属性下的key是服务全局唯一的标识url（如果一个远程服务未特别设置，url默认是服务接口类名），value是引用的服务bean
port属性可不指定
```xml
<bean class="com.dianping.pigeon.remoting.provider.config.spring.ServiceBean" init-method="init">
    <property name="services">
        <map>
            <entry key="http://service.dianping.com/demoService/echoService_1.0.0" value-ref="echoServiceImpl" />
        </map>
    </property>
    <property name="port">
        <value>5008</value>
    </property>
</bean>

<bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl" />
```	
加载Spring配置：

> Provider.java

```java
import org.springframework.context.support.ClassPathXmlApplicationContext;
public class Provider {

    public static void main(String[] args) throws Exception {
    	ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {"provider.xml"});
    	context.start();
    	System.in.read(); // 按任意键退出
    }
    
}
```

### 服务调用者

这里先介绍传统spring方式，后边章节会介绍annotation方式、spring schema定义方式、api方式。

通过Spring配置引用远程服务：

> invoker.xml

```xml
<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
    <!-- 服务全局唯一的标识url，默认是服务接口类名，必须设置 -->
	<property name="url" value="http://service.dianping.com/demoService/echoService_1.0.0" />
	<!-- 接口名称，必须设置 -->
	<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
	<!-- 超时时间，毫秒，默认5000，建议自己设置 -->
	<property name="timeout" value="2000" />
	<!-- 序列化，hessian/fst/protostuff，默认hessian，可不设置-->
	<property name="serialize" value="hessian" />
	<!-- 调用方式，sync/future/callback/oneway，默认sync，可不设置 -->
	<property name="callType" value="sync" />
	<!-- 失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast，可不设置 -->
	<property name="cluster" value="failfast" />
	<!-- 是否超时重试，默认false，可不设置 -->
	<property name="timeoutRetry" value="false" />
	<!-- 重试次数，默认1，可不设置 -->
	<property name="retries" value="1" />
</bean>
```		
加载Spring配置，并调用远程服务：

> Invoker.java

```
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.dianping.pigeon.demo.EchoService;

public class Invoker {

	public static void main(String[] args) throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {“invoker.xml"});
	    context.start();
		EchoService echoService = (EchoService)context.getBean(“echoService"); // 获取远程服务代理
		String hello = echoService.echo("world");
		System.out.println( hello );
	}
	
}
```

## Annotation编程方式

Annotation方式的编程无需在Spring里定义每个bean，但仍需依赖spring，具体使用方式如下：

### 服务提供者
EchoService是一个远程服务的接口：
```java
public interface EchoService {

	String echo(String input);
	
}
```
在服务端需要实现这个服务接口，服务实现类上需要加上@Service（com.dianping.pigeon.remoting.provider.config.annotation.Service）：
```java
@Service
public class EchoServiceAnnotationImpl implements EchoService {

    @Override
    public String echo(String input) {
    	return "annotation service echo:" + input;
    }

}
```
除此之外，只需要在spring配置里加上pigeon:annotation配置：
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
		xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" 
		xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd 
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd 
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd 
		http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
		
		<!-- 默认只扫描com.dianping包，如果非此包下的服务需要自定义package属性，多个package以逗号,分隔-->
        <pigeon:annotation />
		
</beans>
```
@Service在pigeon内部的定义如下：
```java
public @interface Service {

	Class<?> interfaceClass() default void.class;
	String url() default "";
	String version() default "";
	String group() default "";
	int port() default 4040;
	boolean autoSelectPort() default true;
	boolean useSharedPool() default true;
	int actives() default 0;
	
}
```
### 服务调用者
假设在客户端有一个AnnotationTestService，需要引用远程的EchoService服务，只需要在field或method上加上@Reference：
```java
public class AnnotationTestService {

    @Reference(timeout = 1000)
    private EchoService echoService;
    
    public String testEcho(String input) {
    	return echoService.echo(input);
    }
    
}
```		
只需要在spring配置里加上pigeon:annotation配置：
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
		xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
		xmlns:context="http://www.springframework.org/schema/context"
		xmlns:tx="http://www.springframework.org/schema/tx" 
		xmlns:pigeon="http://code.dianping.com/schema/pigeon"
		xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd 
		http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd 
		http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd 
		http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">

	<!-- 默认只扫描com.dianping包，如果非此包下的服务需要自定义package属性，多个package以逗号,分隔-->
	<pigeon:annotation />
	
	<bean id="annotationTestService" class="com.dianping.pigeon.demo.invoker.annotation.AnnotationTestService" />
	
</beans>
```
@Reference定义：
```java
public @interface Reference {

	Class<?> interfaceClass() default void.class;
	String url() default "";
	String protocol() default "default";
	String serialize() default "hessian";
	String callType() default "sync";
	int timeout() default 5000;
	String callback() default "";
	String loadbalance() default "weightedAutoaware";
	String cluster() default "failfast";
	int retries() default 1;
	boolean timeoutRetry() default false;
	String version() default "";
	String group() default "";
	
}
```
## Spring Schema配置方式

### 服务端Spring配置
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:context="http://www.springframework.org/schema/context"
        xmlns:tx="http://www.springframework.org/schema/tx"
        xmlns:pigeon="http://code.dianping.com/schema/pigeon"
        xsi:schemaLocation="http://www.springframework.org/schema/beans
http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
        http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
        http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd"
        default-autowire="byName">
        
    <bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl"/>
    <pigeon:service id="echoService" interface="com.dianping.pigeon.demo.EchoService" ref="echoServiceImpl" />
      
</beans>
```
也可以指定服务url（代表这个服务的唯一性标识，默认是接口类名）和port等属性：
```xml
<bean id="echoServiceImpl" class="com.dianping.pigeon.demo.provider.EchoServiceImpl" />

<pigeon:service id="echoService" url="http://service.dianping.com/demoService/echoService_1.0.0" interface="com.dianping.pigeon.demo.EchoService" port="4040" ref="echoServiceImpl" />
```
### 客户端Spring配置
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:context="http://www.springframework.org/schema/context"
        xmlns:tx="http://www.springframework.org/schema/tx"
        xmlns:pigeon="http://code.dianping.com/schema/pigeon"
        xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
        http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
        http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
        http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">

	<pigeon:reference id="echoService" timeout="1000" protocol="http" serialize="hessian" callType="sync" interface="com.dianping.pigeon.demo.EchoService" />
	<!-- timeout-超时时间，毫秒-->
	<!-- callType-调用方式，sync/future/callback/oneway，默认sync -->
	<!-- protocol-协议，default/http，默认default -->
	<!-- serialize-序列化，hessian/thrift/fst/protostuff，默认hessian -->
	<!-- cluser调用失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast  -->
	<!-- timeoutRetry是否超时重试，在cluster为failover时有效，默认false  -->
	<!-- retries超时重试次数，在cluster为failover时有效  -->
	<!-- interface-服务接口名称 -->
	<!-- url-服务全局唯一的标识url -->
	<!-- callback-服务回调对象 -->
	<!-- loadBalance-负载均衡类型，autoaware/roundRobin/random，默认autoaware -->
	<bean id="echoServiceCallback" class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />
	
	<pigeon:reference id="echoServiceWithCallback" timeout="1000" protocol="http" serialize="hessian" callType="sync" interface="com.dianping.pigeon.demo.EchoService" callback="echoServiceCallback" />
	
</beans>
```
也可以指定服务url（代表这个服务的唯一性标识，默认是接口类名）属性：
```xml
<pigeon:reference id="echoService" url="http://service.dianping.com/demoService/echoService_1.0.0"  timeout=”1000” interface="com.dianping.pigeon.demo.EchoService" />

<bean id="echoServiceCallback" class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />

<pigeon:reference id="echoServiceWithCallback" url="http://service.dianping.com/demoService/echoService_1.0.0" timeout=”1000” interface="com.dianping.pigeon.demo.EchoService" callType="callback" callback="echoServiceCallback" />
```
## API编程方式

### 服务提供者

> Provider.java

```java
public class Provider {

    public static void main(String[] args) throws Exception {
    	ServiceFactory.addService(EchoService.class, new EchoServiceImpl());
    	System.in.read(); // 按任意键退出
    }

}
```
如需自定义服务url（代表这个服务的唯一性标识，默认是接口类名）或端口等参数，可以参考以下代码：
```java
ServiceFactory.publishService("http://service.dianping.com/demoService/echoService_1.0.0", EchoService.class, new EchoServiceImpl(), 4040);
```
更详细的API接口可以参考ServiceFactory类的api详细说明。

### 服务调用者

> Invoker.java

```java
public class Invoker {

	public static void main(String[] args) throws Exception {
		EchoService echoService = ServiceFactory.getService(EchoService.class); // 获取远程服务代理
		String hello = echoService.echo("world");
		System.out.println( hello );
	}
	
}
```		
如果要调用的服务定义了特定的url（代表这个服务的唯一性标识，默认是接口类名），需要客户端指定服务url，可以参考如下代码：
```java
EchoService echoService = ServiceFactory.getService("http://service.dianping.com/demoService/echoService_1.0.0", EchoService.class, 2000); // 获取远程服务代理
String hello = echoService.echo("world");
System.out.println( hello );
```
如果要程序指定序列化方式或协议类型，可以参考如下代码：
```java
InvokerConfig<EchoService> config = new InvokerConfig<EchoService>(EchoService.class);
config.setProtocol(InvokerConfig.PROTOCOL_DEFAULT);
config.setSerialize(InvokerConfig.SERIALIZE_HESSIAN);
EchoService service = ServiceFactory.getService(config);
String hello = service.echo("world");
System.out.println( hello );
```
更详细的api接口可以参考ServiceFactory类的api详细说明。

### ServiceFactory接口
```java
		public static <T> T getService(Class<T> serviceInterface) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, int timeout) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, ServiceCallback callback) throws RpcException
		public static <T> T getService(Class<T> serviceInterface, ServiceCallback callback, int timeout)
		throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, int timeout) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, InvocationCallback callback) throws RpcException
		public static <T> T getService(String url, Class<T> serviceInterface, InvocationCallback callback, int timeout)
		throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param serviceInterface
		* @param service
		* @throws RpcException
		*/
		public static <T> void addService(Class<T> serviceInterface, T service) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param url
		* @param serviceInterface
		* @param service
		* @throws RpcException
		*/
		public static <T> void addService(String url, Class<T> serviceInterface, T service) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param url
		* @param serviceInterface
		* @param service
		* @param port
		* @throws RpcException
		*/
		public static <T> void addService(String url, Class<T> serviceInterface, T service, int port) throws RpcException
		/**
		* add the service to pigeon and publish the service to registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void addService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* add the services to pigeon and publish these services to registry
		*
		* @param providerConfigList
		* @throws RpcException
		*/
		public static void addServices(List<ProviderConfig<?>> providerConfigList) throws RpcException
		/**
		* publish the service to registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void publishService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* publish the service to registry
		*
		* @param url
		* @throws RpcException
		*/
		public static <T> void publishService(String url) throws RpcException
		/**
		* unpublish the service from registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void unpublishService(ProviderConfig<T> providerConfig) throws RpcException
		/**
		* unpublish the service from registry
		*
		* @param url
		* @throws RpcException
		*/
		public static <T> void unpublishService(String url) throws RpcException
		/**
		* unpublish all pigeon services from registry
		*
		* @throws RpcException
		*/
		public static void unpublishAllServices() throws RpcException
		/**
		* publish all pigeon services to registry
		*
		* @throws RpcException
		*/
		public static void publishAllServices() throws RpcException
		/**
		* remove all pigeon services, including unregister these services from
		* registry
		*
		* @throws RpcException
		*/
		public static void removeAllServices() throws RpcException
		/**
		* remove the service from pigeon, including unregister this service from
		* registry
		*
		* @param url
		* @throws RpcException
		*/
		public static void removeService(String url) throws RpcException
		/**
		* remove the service from pigeon, including unregister this service from
		* registry
		*
		* @param providerConfig
		* @throws RpcException
		*/
		public static <T> void removeService(ProviderConfig<T> providerConfig) throws RpcException
		public static void setServerWeight(int weight) throws RegistryException
		public static void online() throws RegistryException
		public static void offline() throws RegistryException
```
## 序列化支持

pigeon支持多种序列化方式，序列化方式只需要在客户端调用时通过serialize属性指定，一般情况推荐兼容性最好的hessian。
如果需要自行设计序列化方式，可以继承
```java
com.dianping.pigeon.remoting.common.codec.DefaultAbstractSerializer
```
类来定义自己的序列化类，并通过
```java
SerializerFactory.registerSerializer(byte serializerType, Serializer serializer)
```
接口将自定义的序列化类注册进来。

### protobuf3序列化支持

客户端配置将序列化方式指定为protobuf3，如下：

```xml
<bean id="testService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
    <property name="url" value="com.dianping.midas.query.api.message.TestService" />
    <property name="interfaceName" value="com.dianping.midas.query.api.message.TestService" />
    <property name="serialize" value="protobuf3" />
</bean>
```
需要注意的是：方法的参数和返回值的类型，必须都为protobuf生成的类。
具体可参考官方文档：[protobuf-java](https://developers.google.com/protocol-buffers/docs/javatutorial)

## HTTP协议支持

pigeon目前支持2种协议：default和http。

default是pigeon默认的基于tcp方式的调用方式。

pigeon也支持http调用，这样可以允许非java的应用调用pigeon的服务。对于http调用，服务端不需要任何修改，任何一个pigeon的服务启动之后，都会同时支持目前默认的基于tcp的和基于http的服务调用，只需要客户端修改配置即可实现http方式的调用。

http协议的默认端口是4080，目前不可配置，如果被占用，会自动选择其他端口。

如果想通过http调用pigeon服务，可以通过http发送post请求调用pigeon服务，可以采用json或hessian的序列化格式:

a) 可以将请求内容post到http://ip:4080/service, 并且在header里设置serialize参数为7或2。

b) 如果是json序列化可以post到http://ip:4080/service?serialize=7, 如果是hessian序列化请post到http://ip:4080/service?serialize=2.

### POST方式
POST地址：
```
http://ip:4080/service?serialize=7
```
JSON请求：
```javascript
{  
    "seq":-985,
    "serialize":7,
    "callType":1,
    "timeout":1000,
    "methodName":"echo",
    "parameters":[  
        "echoService_492"
    ],
    "messageType":2,
    "url":"com.dianping.pigeon.demo.EchoService"
}
```
返回：
```javascript
{  
    "seq":-985,
    "messageType":2,
    "context":null,
    "exception":null,
    "response":"echo:echoService_492"
}
```
如果参数List<T>类型：
JSON请求：
```javascript
{
    "seq": -146,
    "serialize": 7,
    "callType": 1,
    "timeout": 2000,
    "methodName": "getUserDetail",
    "parameters": [
        ["java.util.List", [{
            "@class": "com.dianping.pigeon.demo.UserService$User",
            "username": "user_73"
        }, {
            "@class": "com.dianping.pigeon.demo.UserService$User",
            "username": "user_74"
        }]], false
    ],
    "messageType": 2,
    "url": "com.dianping.pigeon.demo.UserService"
}
```
返回：
```javascript
{
    "seq": -146,
    "messageType": 2,
    "context": null,
    "exception": null,
    "response": ["[Lcom.dianping.pigeon.demo.UserService$User;", [{
        "username": "user_73",
        "email": null,
        "password": null
    }, {
        "username": "user_74",
        "email": null,
        "password": null
    }]]
}
```
### GET方式
GET URL：
```
http://ip:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=abc
```
* url：服务地址
* method：服务方法
* parameterTypes：服务方法method的参数类型，如果是多个参数就写多个parameterTypes
* parameters：参数值，多个参数值就写多个parameters

如果参数类型是enum类型，参数值要传某个enum值，请传递该值在enum里的定义顺序，如enum的第1个值就传0，第2个值就传1。

如果是多个参数，比如某个方法：
```java
String echo2(String input, int size);
```
URL示例：
```
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.lang.String&parameters=wux&parameterTypes=int&parameters=2
```
如果服务方法参数类型是Collection泛型，如List<User>，需要在参数值指定@class类型，比如这个方法：
```java
getUserDetail(List<User> userList, boolean flag)
```
的userList变量，需要传参数：
```javascritp
[{
    "@class": "com.dianping.pigeon.demo.UserService$User",
    "username": "user_73"
}, {
    "@class": "com.dianping.pigeon.demo.UserService$User",
    "username": "user_74"
}]
```

以上json格式需符合jackson的json规范，如果不清楚一个对象对应的json字符串，pigeon提供接口可以得到对象转换后的json字符串。
```java
public static void main(String[] args) {
	User user = new User();
	user.setUsername("scott");
	List<User> users = new ArrayList<User>();
	users.add(user);
	JacksonSerializer serializer = new JacksonSerializer();
	String str = serializer.serializeObject(users);
	System.out.println(str);
}
```
## 服务测试工具

pigeon提供了服务测试的工具，测试工具基于pigeon的http协议(默认在4080端口)，可以访问每一台服务器的url：
```
http://ip:4080/services
```
页面上会列出该服务器上所有的pigeon服务列表，对于每一个服务方法，可以在右侧输入json格式的参数，进行服务调用，获取json格式的返回结果。

如果不清楚一个对象对应的json字符串，可以参考前面一节，pigeon提供接口可以得到对象转换后的json字符串。

在线上环境进行测试时，需要输入验证码，验证码可以从该ip的pigeon日志文件中获取，请务必谨慎使用该测试工具，以免人为失误影响线上数据。

如果服务方法参数类型是Collection泛型，如`List<User>`，需要在参数值指定@class类型，比如这个方法：
```
getUserDetail(List<User> userList, boolean flag)
```
的userList变量，需要传参数：
```javascript
[{
    "@class": "com.dianping.pigeon.demo.UserService$User",
    "username": "wux",
    "email": "scott@dianping.com"
}]
```
如果服务方法参数类型是Map泛型，如`Map<User, Double>`，需要符合这种格式：
```javascript
{
    "{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"w\",\"email\":null,\"password\":null}": 4.5,
    "{\"username\":\"x\",\"email\":null,\"password\":null}": 3.5
}
```
访问`http://ip:4080/services.json`可以返回该服务器所有服务列表的json内容。

访问`http://ip:4080/invoke.json`可以通过get方式测试服务，例如：
```
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=abc
```
* url参数是服务地址
* method是服务方法
* parameterTypes是服务方法method的参数类型，如果是多个参数就写多个parameterTypes
* parameters是参数值，多个参数值就写多个parameters
 
如果参数类型是enum类型，参数值要传某个enum值，请传递该值在enum里的定义顺序，如enum的第1个值就传0，第2个值就传1。

如果是多个参数，比如某个方法：
```java
String echo2(String input, int size);
```
url示例：
```
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.lang.String&parameters=wux&parameterTypes=int&parameters=2
```
方法：
```java
echo2(Map<User, Double> userMap, int count)
```
url示例：
```
http://localhost:4080/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo2&parameterTypes=java.util.Map&parameters={"{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"w\",\"email\":null,\"password\":null}":4.5,"{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"x\"}":3.5}&parameterTypes=int&parameters=3&direct=false
```
如果需要每次调用都记录cat日志，需要带上direct=false参数

http://ip:4080/services.status 可以测试服务健康状况

## 配置负载均衡策略

### 配置客户端的loadBalance属性

目前可以是random/roundRobin/weightedAutoware这几种类型，默认是weightedAutoware策略，一般场景不建议修改。

```xml
<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"	init-method="init">
	<property name="url" value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
	<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
	<property name="callType" value="sync" />
	<property name="timeout" value="1000" />
	<property name="loadBalance" value="weightedAutoware" />
</bean>
```

### 运行中动态配置客户端的loadBalance属性

在客户端应用xxx的lion管理端，添加配置项xxx.pigeon.loadbalance.dynamictype，内容为：

```java
{
    "com.dianping.arch.test.benchmark.service.EchoService#echo" : "random",
    "com.dianping.arch.test.benchmark.service.TestService" : "autoaware"
}
```

优先级是method级别的loadbalance，然后到service级别的loadbalance，最后是应用静态配置的loadbalance。


## 客户端配置某个方法的超时时间

pigeon支持客户端调用某个服务接口时，对整个服务的超时时间进行设置，也可以对该服务接口的某个方法设置单独的超时时间，没有配置超时的方法会以服务级别的超时时间为准。
```xml
<beans xmlns="http://www.springframework.org/schema/beans"
    	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
    	xmlns:context="http://www.springframework.org/schema/context"
	    xmlns:tx="http://www.springframework.org/schema/tx" 
	    xmlns:pigeon="http://code.dianping.com/schema/pigeon"
	    xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-2.5.xsd
	    http://www.springframework.org/schema/context http://www.springframework.org/schema/context/spring-context-2.5.xsd
	    http://www.springframework.org/schema/tx http://www.springframework.org/schema/tx/spring-tx-2.5.xsd
	    http://code.dianping.com/schema/pigeon http://code.dianping.com/schema/pigeon/pigeon-service-2.0.xsd">
	    
	<pigeon:reference id="echoService" timeout="1000" url="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" interface="com.dianping.pigeon.demo.EchoService">
	    <pigeon:method name="echo" timeout="2000" />
	</pigeon:reference>
	
</beans>
```
如果想设置当前线程下一个pigeon方法调用的超时时间，可以调用
```java
		InvokerHelper.setTimeout(200);
```

## 服务隔离与限流

### 配置服务方法级别的最大并发数

pigeon支持服务端对某个服务接口的方法的最大并发数进行配置，这样可以隔离每个服务方法的访问，防止某些方法执行太慢导致服务端线程池全部卡住的问题。

1、客户端spring配置

只需要设置useSharedPool为false，pigeon就会为每个方法设置独立的线程池执行请求。

如果并发超过设置的最大并发数，服务端会抛出

```java
com.dianping.pigeon.remoting.common.exception.RejectedException
```
异常，客户端也会收到这个异常。
```xml
<!-- 定义pool -->
<pigeon:pool id="poolS"
corePoolSize="${tena.test.core.size}"
maxPoolSize="${tena.test.max.size}"
workQueueSize="${tena.test.queue.size}" />
<pigeon:pool id="poolM"
corePoolSize="${pigeon-benchmark.isolation.core.size}"
maxPoolSize="${pigeon-benchmark.isolation.max.size}"
workQueueSize="${pigeon-benchmark.isolation.queue.size}" />
 
<!-- 引用pool -->
<pigeon:service interface="com.dianping.pigeon.benchmark.service.HelloService"
url="com.dianping.pigeon.benchmark.service.HelloService"
pool="poolS" useSharedPool="false" ref="helloService">
    <pigeon:method name="sayHello" pool="poolM" />
</pigeon:service>
<pigeon:service interface="com.dianping.pigeon.benchmark.service.TestService"
url="com.dianping.pigeon.benchmark.service.TestService"
pool="poolM" useSharedPool="false" ref="testService">
    <pigeon:method name="sendLong" pool="poolS" />
</pigeon:service>
```
需要设置useSharedPool为false，pool定义中的corePoolSize、maxPoolSize、workQueueSize均可动态改变生效

2、配置中心统一配置

a、首先需要在应用lion里配置开关打开，例如xxx-service项目要配置以下lion配置：

xxx-service.pigeon.provider.pool.config.switch=true

b、配置应用的自定义pool(与方法1中的pool无关)，添加配置项：

xxx-service.pigeon.provider.pool.config

内容为json格式的pool对象数组，如下：
```
[ {
 "poolName" : "pool1",
 "corePoolSize" : 50,
 "maxPoolSize" : 100,
 "workQueueSize" : 101
}, {
 "poolName" : "pool2",
 "corePoolSize" : 1,
 "maxPoolSize" : 2,
 "workQueueSize" : 33
}, {
 "poolName" : "pool3",
 "corePoolSize" : 0,
 "maxPoolSize" : 1,
 "workQueueSize" : 1
} ]
```
c、配置应用的接口与使用的自定义pool的映射，支持服务或方法级别，添加而配置项：

pigeon-benchmark.pigeon.provider.pool.api.config

内容为json格式的映射对象，如下：
```
{
 "com.dianping.pigeon.benchmark.service.HelloService#statistics" : "pool1",
 "com.dianping.pigeon.benchmark.service.EchoService" : "pool2",
 "com.dianping.pigeon.benchmark.service.TestService" : "pool3"
}
```
d、以上a、b、c的配置项都可以动态生效。

3、管理端配置

服务隔离的配置也可通过管理端进行

### 限制某个客户端应用的最大并发数

1、应用级限流

pigeon支持在服务端配置某个客户端应用的最大请求QPS

首先需要在应用lion里配置开关打开，例如deal-service项目要配置以下lion配置： deal-service.pigeon.provider.applimit.enable=true

配置客户端应用对应的最大QPS： pigeon.provider.applimit=tuangou-web:100,xxx:50,yyy:100 如果客户端请求QPS超过了设置的阀值，服务端会返回com.dianping.pigeon.remoting.common.exception.RejectedException给客户端，客户端会收到RejectedException

2、配置某个接口方法对应的客户端应用的最大QPS:

首先打开开关：xxx-service.pigeon.provider.methodapplimit.active=true

增加配置项：xxx-service.pigeon.provider.methodapplimit

配置内容为json格式：{ "api#method" : { "app1": 100, "app2": 50} }

例如：
```
{ "http://service.dianping.com/com.dianping.pigeon.demo.EchoService#echo": { "account-service": 2000, "deal-server": 5000} }
```

3、配置服务端的总体最大QPS：

首先打开开关：xxx-service.pigeon.provider.globallimit.enable=true

增加配置项：xxx-service.pigeon.provider.globallimit=5000

4、其他:

以上配置第一次配置了之后，均可以通过lion动态在线设置实时生效

## 服务降级

pigeon在调用端提供了服务降级功能支持

应用调用远端的服务接口如果在最近一段时间内出现连续的调用失败，失败率超过一定阀值，可以自动触发或手工触发降级，调用端直接返回默认对象或抛出异常，不会将调用请求发到服务提供方，如果服务提供方恢复可用，客户端可以自动或手工解除降级

### 配置接口的降级策略

例如xxx-service项目，有http://service.dianping.com/com.dianping.pigeon.demo.EchoService这个服务，包含3个方法：
```
String echo(String input);
User getUserDetail(String userName);
User[] getUserDetailArray(String[] usernames);
```
### 配置可降级的方法

要配置以下lion配置：

a、增加lion配置：xxx-service.pigeon.invoker.degrade.methods配置为：
```
http://service.dianping.com/com.dianping.pigeon.demo.EchoService#echo=a,http://service.dianping.com/com.dianping.pigeon.demo.EchoService#getUserDetail=b,http://service.dianping.com/com.dianping.pigeon.demo.EchoService#getUserDetailArray=c
```
上述配置内容包含多个方法的降级策略a、b、c。如果某此调用需要降级，而降级策略没有配置则不降级，进行正常调用流程。

b、增加lion配置：pigeon-test.pigeon.invoker.degrade.method.return.a对应echo方法的默认返回，配置为：
```
{"returnClass":"java.lang.String","content":"echo,input"}
```
如果不想返回默认值，而是抛出一个降级异常（pigeon默认会抛出com.dianping.pigeon.remoting.invoker.exception.ServiceDegradedException），配置为：
```
{"throwException":"true"}
```
c、增加lion配置：pigeon-test.pigeon.invoker.degrade.method.return.b对应getUserDetail方法的默认返回，配置为：
```
{"returnClass":"com.dianping.pigeon.demo.User","content":"{\"username\":\"user-1\"}"}
```
d、增加lion配置：pigeon-test.pigeon.invoker.degrade.method.return.c对应getUserDetailArray方法的默认返回，配置为：
```
{"returnClass":"[Lcom.dianping.pigeon.demo.UserService$User;","content":"[{\"username\":\"array-1\"},{\"username\":\"array-2\"}]"}
```
这里返回对象是数组，如果是返回集合，也类似，例如返回一个LinkedList：
```
{"returnClass":"java.util.LinkedList","content":"[{\"@class\":\"com.dianping.pigeon.demo.UserService$User\",\"username\":\"list-1\"},{\"username\":\"list-2\"}]"}
```

e、使用groovy脚本的方式，增加lion配置:pigeon-test.pigeon.invoker.degrade.method.return.a，其中content对应echo方法的默认groovy脚本，配置为：
可以执行任意脚本，例如抛出异常：
```
{"useGroovyScript":"true", "content":"throw new RuntimeException('test groovy degrade');"}
```
或者返回对象：
```
{"useGroovyScript":"true", "content":"return new com.dianping.pigeon.remoting.test.Person(name:'zhangsan',age:1);"}
```
注意！脚本的最后一条执行语句，必须返回方法的返回值类型或抛出异常。

f、除了上述几种使用lion配置降级策略的方式，pigeon还提供了一种使用mock类的降级配置方式。

例如我们想修改pigeon-test.pigeon.invoker.degrade.method.return.a的降级策略方式为mock方式，只需修改配置为：

{"useMockClass":"true"}

打开mock开关，然后在spring的xml配置中添加mock类的引用对象：

```xml
<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
    <property name="url" value="com.dianping.pigeon.benchmark.service.EchoService" />
    <property name="interfaceName" value="com.dianping.pigeon.benchmark.service.EchoService" />
    <property name="mock" ref="echoServiceMock" /><!-- 添加mock类的引用 -->
</bean>

<!-- 必须实现EchoService接口 -->
<bean id="echoServiceMock" class="com.dianping.pigeon.benchmark.service.EchoServiceMock"/>
```
g、若想在开启了降级总开关的基础上，开启或关闭部分接口的降级开关，可在json配置中添加配置项"enable":"false"或"enable":"true"，不填写则缺省为true
例如

```
{"enable":"false", "useGroovyScript":"true", "content":"if (new Random().nextInt(2) < 1) { return 'normal'; } else { throw new RuntimeException('test groovy degrade'); }"}
```

h、降级配置方式的优先级为：mock > groovy script > json exception > json default value

### 强制降级开关
强制降级开关只是在远程服务大量超时或其他不可用情况时，紧急时候进行设置，开启后，调用端会根据上述降级策略直接返回默认值或抛出降级异常，当远程服务恢复后，建议关闭此开关

提供了pigeon.invoker.degrade.force配置开关，例如xxx-service项目要配置以下lion配置：
```
xxx-service.pigeon.invoker.degrade.force=true，默认为false
```

### 失败降级开关


失败降级开关便于客户端在服务端出现非业务异常(比如网络失败，超时，无可用节点等)时进行降级容错，而在出现业务异常(比如登录用户名密码错误)时不需要降级。

提供了pigeon.invoker.degrade.failure配置开关，例如xxx-service项目要配置以下lion配置：
```
xxx-service.pigeon.invoker.degrade.failure=true，默认为false
```

### 自动降级开关

自动降级开关是在调用端设置，开启自动降级后，调用端如果调用某个服务出现连续的超时或不可用，当一段时间内（10秒内）失败率超过一定阀值（默认1%）会触发自动降级，调用端会根据上述降级策略直接返回默认值或抛出降级异常

当服务端恢复后，调用端会自动解除降级模式，再次发起请求到远程服务

提供了pigeon.invoker.degrade.auto配置开关，例如xxx-service项目要配置以下lion配置：
```
xxx-service.pigeon.invoker.degrade.auto=true，默认为false
```

### 降级开关的优先级(在同时打开的时候的有效性)

强制降级 > 自动降级 > 失败降级

其中自动降级包含失败降级策略

### 自动降级添加指定业务异常到失败统计并支持降级

在一些业务场景中，调用端希望被调用端的一些常见业务异常，实际上是程序bug或底层逻辑或数据故障导致的异常，也可以被降级，例如空指针异常，数据库访问异常等等。
在这样的情况下，可以在调用端应用，如：app1应用，想要添加指定异常java.lang.NullPointerException和org.springframework.dao.DataAccessException
请在lion管理端添加key：app1.pigeon.invoker.degrade.customized.exception
内容为：java.lang.NullPointerException,org.springframework.dao.DataAccessException
多个异常之间用逗号隔开，以上配置可以动态修改

### 业务自行降级
前面提到的降级是pigeon内置的服务降级功能，通常pigeon提供的服务降级功能足够满足业务需求，但如果业务想自行进行服务降级，可以catch pigeon的RpcException，这个RpcException包括了超时等所有pigeon异常：
```java
try {
	return xxxService.echo("hello");
} catch (RpcException e) {
	return "default value";
}
```

### 降级相关异常统计、日志及监控输出
如果使用pigeon内部的服务降级，服务降级返回默认对象的请求在CAT上PigeonCall不会统计作为失败，而是认为是成功，如果降级策略是抛出异常那么还是会统计作为失败请求。

每次降级的请求都会在CAT event里记录PigeonCall.degrade事件，如果每次降级想作为异常输出到CAT，可以配置pigeon.invoker.degrade.log.enable参数为true，这样每次降级后pigeon会在CAT problem输出com.dianping.pigeon.remoting.invoker.exception.ServiceDegradedException

如果业务有自己的服务降级策略，优先调远程pigeon服务，如果调用失败可以返回默认值，但又不希望pigeon把异常记录到CAT，可以在调用远程服务前按如下例子进行设置：
```java
InvokerHelper.setLogCallException(false);
try {
	return xxxService.echo("hello");
} catch (RpcException e) {
	return "default value";
}
```

## 配置客户端调用模式

在pigeon内部，客户端调用远程服务有4种模式（sync/future/callback/oneway），例如spring编程方式下只需要配置callType属性：
```xml
<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
	<property name="url" value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
	<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
	<property name="callType" value="sync" />
	<property name="timeout" value="1000" />
</bean>
```
### sync
同步调用，客户端线程会阻塞等待返回结果，默认设置是sync模式。

### oneway
客户端只是将请求传递给pigeon，pigeon提交给服务端，客户端也不等待立即返回，服务端也不会返回结果给客户端，这种方式一般都是没有返回结果的接口调用。​

### future
客户端将请求提交给pigeon后立即返回，不等待返回结果，由pigeon负责等待返回结果，客户端可以自行决定何时何地来取返回结果，代码示例：
```java
//调用ServiceA的method1
serviceA.method1("aaa");
//获取ServiceA的method1调用future状态
Future future1OfServiceA = InvokerHelper.getFuture();
//调用ServiceA的method2
serviceA.method2("bbb");
//获取ServiceA的method2调用future状态
Future future2OfServiceA = InvokerHelper.getFuture();
//调用ServiceB的method1
serviceB.method1("ccc");
//获取ServiceB的method1调用future状态
Future future1OfServiceB = InvokerHelper.getFuture();
//获取ServiceA的method2调用结果
Object result2OfServiceA = future2OfServiceA.get();
//获取ServiceA的method1调用结果
Object result1OfServiceA = future1OfServiceA.get();
//获取ServiceB的method1调用结果
Object result1OfServiceB = future1OfServiceB.get();
```
最后的get()调用顺序由业务自行决定。操作总共花费的时间，大致等于耗时最长的服务方法执行时间。
除了get()接口，也可以使用get(timeout, TimeUnit.MILLISECONDS)指定超时时间。

### callback
回调方式，客户端将请求提交给pigeon后立即返回，也不等待返回结果，它与future方式的区别是，callback必须提供一个实现了pigeon提供的InvocationCallback接口的回调对象给pigeon，pigeon负责接收返回结果并传递回给这个回调对象，代码示例：
> spring配置文件：

```xml
<bean id="echoServiceWithCallback" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
    <property name="url" value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
    <property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
    <property name="callType" value="callback" />
    <property name="timeout" value="1000" />
    <property name="callback" ref="echoServiceCallback" />
</bean>

<bean id="echoServiceCallback" class="com.dianping.pigeon.demo.invoker.EchoServiceCallback" />
```		
调用代码：
```java
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.dianping.pigeon.demo.EchoService;

public class Invoker {

	public static void main(String[] args) throws Exception {
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext(new String[] {“invoker.xml"});
		context.start();
		// 获取远程服务代理
		EchoService echoServiceWithCallback = (EchoService)context.getBean(“echoServiceWithCallback");
		String hello = echoServiceWithCallback.echo("world");
		System.out.println( hello );
	}
	
}
```
> Callback类：

```java
import com.dianping.pigeon.remoting.invoker.concurrent.InvocationCallback;

public class EchoServiceCallback implements InvocationCallback {
	private static final Logger logger =  LoggerLoader.getLogger(EchoServiceCallback.class);
	
	@Override
	public void onSuccess(Object result) {
		System.out.println("callback:" + result);
	}
	
	@Override
	public void onFailure(Throwable exception) {
		logger.error("", exception);
	}
	
}
```

如果需要动态设置callback，比如在一个线程里发起多次服务调用请求，每次使用不同的callback，可以按照以下代码：
```java
InvokerHelper.setCallback(new InvocationCallback(){...});
```

## 配置客户端集群策略模式

客户端配置cluster属性：
```xml
<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean" init-method="init">
	<property name="url" value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
	<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
	<property name="callType" value="sync" />
	<property name="timeout" value="1000" />
	<!-- 失败策略，快速失败failfast/失败转移failover/失败忽略failsafe/并发取最快返回forking，默认failfast -->
	<property name="cluster" value="failfast" />
	<!-- 是否超时重试，默认false -->
	<property name="timeoutRetry" value="false" />
	<!-- 重试次数，默认1 -->
	<property name="retries" value="1" />
</bean>
```
* failfast：调用服务的一个节点失败后抛出异常返回，可以同时配置重试timeoutRetry和retries属性

* failover：调用服务的一个节点失败后会尝试调用另外的一个节点，可以同时配置重试timeoutRetry和retries属性

* failsafe：调用服务的一个节点失败后不会抛出异常，返回null，后续版本会考虑按配置默认值返回

* forking：同时调用服务的所有可用节点，返回调用最快的节点结果数据

### 客户端多连接

pigeon 客户端默认使用的单连接，2.9.0及以后的版本支持多连接配置，多连接对性能有一定的提升，目前多连接配置是应用级别。

配置方式：增加pigeon.channel.pool.normal.size配置，默认值为1，最大可配置到5。一般如果有需要配置到2即可。

如果是lion配置，需要设置xxx-service.pigeon.channel.pool.normal.size配置

### 异步编程

如果要追求最好的单机性能，需要通过pigeon进行异步编程。

1、客户端调用方式选择future或callback方式。 可以参考前面的“配置客户端调用模式”说明

2、服务端一般业务场景都采用多线程实现并发，如果要实现异步编程，需要使用事件驱动callback模式。一般可以在IO调用的callback里回写服务调用结果，服务端需要加lion配置xxx.pigeon.provider.reply.manual为true（xxx为应用app name）

pigeon服务里如果有任何IO操作，需要该IO操作支持callback编程，IO操作常见的有缓存访问（支持callback调用）、数据库访问（正在开发callback调用支持）、pigeon服务调用（支持callback调用） 

例如在一个pigeon服务里调用了cache操作，需要在cache框架也支持callback模式，然后在callback里调用pigeon的api去回写最终返回客户端的结果

```java
@Service
public class XXXDefaultService implements XXXService {
 
    public XXXDefaultService() {
    }
 
    @Autowired
    private CacheService cacheService;
 
    @Override
    public String get(CacheKey cacheKey) {
        cacheService.asyncGet(cacheKey, new CacheCallback<String>() {
 
            private ProviderContext providerContext = ProviderHelper.getContext();
 
            @Override
            public void onSuccess(String result) {
                ProviderHelper.writeSuccessResponse(providerContext, result);
            }
 
            @Override
            public void onFailure(String msg, Throwable e) {
                ProviderHelper.writeFailureResponse(providerContext, new RuntimeException(msg));
            }
 
        });
        return null;
    }
 
    @Override
    public Map<CacheKey, String> batchGet(List<CacheKey> cacheKeys) {
        cacheService.asyncBatchGet(cacheKeys, new CacheCallback<Map<CacheKey, String>>() {
 
            private ProviderContext providerContext = ProviderHelper.getContext();
 
            @Override
            public void onSuccess(Map<CacheKey, String> result) {
                ProviderHelper.writeSuccessResponse(providerContext, result);
            }
 
            @Override
            public void onFailure(String msg, Throwable e) {
                ProviderHelper.writeFailureResponse(providerContext, new RuntimeException(msg));
            }
 
        });
        return null;
    }
 
}
```

3、改进后的基于服务级别的异步化方式
不需要再去配置pigeon.provider.reply.manual，否则会影响整个应用，只需要在需要异步化的服务实现当中调用ProviderHelper.startAsync()获取ProviderContext后即可进行异步编程。参考代码如下：

```java
@Service
public class XXXDefaultService implements XXXService {

    public XXXDefaultService() {
    }

    @Autowired
    private CacheService cacheService;

    @Override
    public String get(CacheKey cacheKey) {
        cacheService.asyncGet(cacheKey, new CacheCallback<String>() {

            private ProviderContext providerContext = ProviderHelper.startAsync();

            @Override
            public void onSuccess(String result) {
                ProviderHelper.writeSuccessResponse(providerContext, result);
            }

            @Override
            public void onFailure(String msg, Throwable e) {
                ProviderHelper.writeFailureResponse(providerContext, new RuntimeException(msg));
            }

        });
        return null;
    }

    @Override
    public Map<CacheKey, String> batchGet(List<CacheKey> cacheKeys) {
        cacheService.asyncBatchGet(cacheKeys, new CacheCallback<Map<CacheKey, String>>() {

            private ProviderContext providerContext = ProviderHelper.startAsync();

            @Override
            public void onSuccess(Map<CacheKey, String> result) {
                ProviderHelper.writeSuccessResponse(providerContext, result);
            }

            @Override
            public void onFailure(String msg, Throwable e) {
                ProviderHelper.writeFailureResponse(providerContext, new RuntimeException(msg));
            }

        });
        return null;
    }

}
```

### ZooKeeper协议格式
1、服务地址配置：

每个服务都有一个全局唯一的url代表服务名称，比如我们有一个服务： http://service.dianping.com/com.dianping.pigeon.demo.EchoService 服务名称url格式不固定，只要求是字符串在公司内部唯一即可。 Pigeon服务端每次启动后会将自身ip:port注册到ZooKeeper集群中。 在ZooKeeper中pigeon服务具体格式是这样的： pigeon服务都会写到/DP/SERVER节点下： /DP/SERVER/http:^^service.dianping.com^com.dianping.pigeon.demo.EchoService 的值为：192.168.93.1:4088,192.168.93.2:4088 多台服务器就是逗号分隔，客户端只需要拿到这个值就能知道这个服务的服务器地址列表。

2、服务权重配置：

pigeon服务还会写到/DP/WEIGHT/192.168.93.1:4088这个节点，值为1代表权重，如果为0代表这台机器暂时不提供服务，目前只有1和0两种值。

3、服务所属应用配置：

pigeon服务还会写到/DP/APP/192.168.93.1:4088这个节点，值为这个服务所属的应用名，这个应用名是读取本地classpath下META-INF/app.properties里的app.name值。

客户端需要拿到服务对应的地址列表、每个地址对应的权重weight，就可以自己实现负载均衡策略去调其中一台服务器。

### 安全性

1、基于token的认证 pigeon支持基于token的认证方式，token认证在pigeon的http和tcp协议层面都同时支持，如果开启token认证，客户端请求中必须设置pigeon规范的token，否则请求将被拒绝

对于服务端：

a、打开token认证开关，token认证开关默认是关闭的，需要服务提供方自行打开，在lion里配置key，如xxx-service这个应用：

配置xxx-service.pigeon.provider.token.enable，内容为true

b、需要定义每个客户端的密钥，在配置中心lion里配置key：xxx-service.pigeon.provider.token.app.secrets，内容如： 
```
xxx-web:r3wzPd4azsHEhgDI69jubmV,yyy-service:45etwFsfFsHEdrg9ju3 
```
分别代表xxx-web和yyy-service的密钥，针对每个应用配置不同的密钥，密钥需要严格管理，不能泄露，目前限定密钥长度必须不少于16个字符

c、如果服务提供方希望客户端在http header里设置token，可以在lion里配置xxx-service.pigeon.console.token.header为true，否则默认可以是url里带上token

d、客户端需要带上timestamp到服务端，在服务端会对timestamp进行校验，默认只接受时差2分钟以内的请求，如果要调整可以设置： 

xxx-service.pigeon.provider.token.timestamp.diff，默认为120（单位秒）

e、如果服务提供方只希望http客户端进行认证，而不希望默认的tcp客户端做认证（老业务），需要配置

xxx-service.pigeon.provider.token.protocol.default.enable为false

f、如果服务提供方希望部分服务或方法开启或不开启认证，需要配置

xxx-service.pigeon.provider.token.switches，内容类似com.dianping.pigeon.demo.EchoService=false,com.dianping.pigeon.demo.EchoService#echo2=true

这里边com.dianping.pigeon.demo.EchoService服务下所有方法是默认不开启认证，但echo2方法需要开启认证


对于客户端：

a、对于使用pigeon java客户端的应用，只需要配置所依赖的服务的密钥，在配置中心lion里配置key，如xxx-web这个应用：

配置xxx-web.pigeon.invoker.token.app.secrets，内容如： 
```
xxx-service:r3wzPd4azsHEhgDI69jubmV,yyy-service:45etwFsfFsHEdrg9ju3 
```
分别代表访问xxx-service和yyy-service的密钥，针对每个服务端配置不同的密钥，密钥需要严格管理，不能泄露，这个配置不要跟服务端配置共享，应严格独立管理

b、对于未使用pigeon java客户端的应用，如果通过HTTP GET方式请求，需要根据服务提供方提供的密钥，生成token，具体规则如下： 
如果服务提供方允许url带token传递，可以按以下url格式来发出请求 
```
http://pigeon.dper.com/xxx-service/invoke.json?app=xxx-web&token=v5cg4EUS4c8wIjOC70VwvvgxZzg&timestamp=1458447031&url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=scott 
```
url里必须再带上timestamp，timestamp=1458447031 url里也必须带上app=xxx-web，以便在服务端进行认证

其中token生成规则是： 
```
String token = SecurityUtils.encrypt(data, secret) 
```
data字符串组成：服务名url + "#" + 服务方法名 + "#" + timestamp（目前为简单起见未加入请求参数等），例如调用http://service.dianping.com/com.dianping.pigeon.demo.EchoService这个服务的echo方法： 
```
http://service.dianping.com/com.dianping.pigeon.demo.EchoService#echo#1458442458 
```
timestamp是System.currentTimeMillis()/1000，也就是到秒 

secret就是这个服务提供方给的密钥，例如上面的r3wzPd4azsHEhgDI69jubmV 


c、如果服务提供方必须要求客户端将token等放在header里，以上url简化为： 

```
http://pigeon.dper.com/xxx-service/invoke.json?url=http://service.dianping.com/com.dianping.pigeon.demo.EchoService&method=echo&parameterTypes=java.lang.String&parameters=scott 
```
在header里必须有两个key： 

Timestamp,内容为上述类似的System.currentTimeMillis()/1000值，例如：1458447031 

Authorization，内容格式例如：

pigeon=xxx-web:v5cg4EUS4c8wIjOC70VwvvgxZzg 

pigeon=为必须填的字符串，xxx-service代表客户端app名称，冒号:后边的字符串为token值


d、SecurityUtils.encrypt方法可以参考下面代码，内部采用HmacSHA1算法，通过密钥对某个字符串进行签名，然后转换为base64编码：

```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
 
public class SecurityUtils {
 
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";
 
    public static String encrypt(String data, String secret) throws SecurityException {
        String result;
        try {
            // get an hmac_sha1 key from the raw key bytes
            SecretKeySpec signingKey = new SecretKeySpec(key.getBytes(), HMAC_SHA1_ALGORITHM);
 
            // get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
 
            // compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(data.getBytes());
 
            // base64-encode the hmac
            result = Base64.encodeBase64URLSafeString(rawHmac);
        } catch (Exception e) {
            throw new SecurityException("Failed to generate HMAC : " + e.getMessage());
        }
        return result;
    }
 
}
```

e、如果是其他语言客户端，请参考以上逻辑自行加入认证token等信息

以上涉及lion的所有配置都是可以随时修改、动态生效

2、基于ip的认证 

a、默认是关闭的，需要打开，对于xxx-service这个应用来说，可以在lion配置

xxx-service.pigeon.provider.access.ip.enable为true 

b、分为3个配置：

判断逻辑是先判断白名单(xxx-service.pigeon.provider.access.ip.whitelist配置，ip网段逗号分隔)是否匹配来源ip前缀，如果匹配，直接返回true允许访问 

如果不匹配，去黑名单（xxx-service.pigeon.provider.access.ip.blacklist配置，ip网段逗号分隔）找是否匹配来源ip前缀，黑名单里匹配到了，直接返回false不允许访问 

如果都没找到，返回xxx-service.pigeon.provider.access.ip.default值，默认是true，代表默认是允许访问

3、基于app的认证

a、默认是关闭的，需要打开，对于xxx-service这个应用来说，可以在lion配置

xxx-service.pigeon.provider.access.app.enable为true

b、分为3个配置：

判断逻辑是先判断白名单(xxx-service.pigeon.provider.access.app.whitelist配置，app之间用逗号分隔)是否匹配来源app，如果匹配，直接返回true允许访问

如果不匹配，去黑名单（xxx-service.pigeon.provider.access.app.blacklist配置，app之间用逗号分隔）找是否匹配来源app，黑名单里匹配到了，直接返回false不允许访问

如果都没找到，返回xxx-service.pigeon.provider.access.app.default值，默认是true，代表默认是允许访问

### 自定义服务发布策略

如果在服务发布的过程中，想根据一些环境信息采用不同的服务发布策略，例如在北京发布服务时，屏蔽或过滤一些服务。

需要实现一个`com.dianping.pigeon.remoting.provider.publish.PublishPolicy`接口，采用jdk的ServiceLoader方式加载。下面将给出一个使用示例。

1、建议继承com.dianping.pigeon.remoting.provider.publish.AbstractPublishPolicy抽象类。例如：
```java
package com.dianping.pigeon.benchmark.customize;
 
import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.remoting.provider.config.ProviderConfig;
import com.dianping.pigeon.remoting.provider.publish.AbstractPublishPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
/**
 * Created by chenchongze on 16/11/3.
 */
public class MyPublishPolicy extends AbstractPublishPolicy {
 
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
 
    @Override
    public void doAddService(ProviderConfig providerConfig) {
        if (isBeijingHost(ConfigManagerLoader.getConfigManager().getLocalIp(), providerConfig.getUrl())) {
            logger.warn("do not publish service: " + providerConfig);
            return;
        } else {
            super.doAddService(providerConfig);
        }
    }
 
    private boolean isBeijingHost(String localIp, String serviceName) {
        // 判断本地机器是不是北京的(例如可以用lion的region判断接口)，同时服务名包含“Test”字符串
        return localIp.startsWith("172") && serviceName.contains("Test");
    }
}
```

2、在项目的resources资源文件`META-INF/services/`下，新建`com.dianping.pigeon.remoting.provider.publish.PublishPolicy`文件。

注意是`src/main/resources/META-INF/services/`文件夹，而不是webapps下的那个META-INF。

在`com.dianping.pigeon.remoting.provider.publish.PublishPolicy`文件中写入实现类，Demo中为：

com.dianping.pigeon.benchmark.customize.MyPublishPolicy

## Pigeon 常见问题
### 如何传递自定义参数
使用com.dianping.pigeon.remoting.common.util.ContextUtils类的接口

#### 简单的客户端A->服务端B的一级调用链路的参数传递
客户端：
```java
String url = "http://service.dianping.com/com.dianping.pigeon.demo.EchoService";
EchoService service = ServiceFactory.getService(url, EchoService.class);
//...
ContextUtils.putRequestContext("key1", "value1");
System.out.println("service result:" + service.echo(input));
```
服务端：
```java
public String echo(String input) {
    System.out.println(ContextUtils.getLocalContext("key1"));
    return "echo:" + input;
}
```
#### 服务端B->客户端A的参数传回

服务端：
```java
ContextUtils.putResponseContext("key1", "value1");
```
客户端：
```
ContextUtils.getResponseContext("key1");
```

#### 全链路传递

如果需要在全链路传递对象，如A->B->C->D，需要使用以下接口：

在A发送请求端：
```java
ContextUtils.putGlobalContext("key1", "value1");
```
在D接收请求端：
```java
ContextUtils.getGlobalContext("key1");
```

### 如何指定固定ip:port访问pigeon服务

1、客户端可以配置只连某台服务器进行pigeon服务调试，比如qa环境可以在你的classpath下配置config/pigeon_qa.properties文件，实现只访问192.168.0.1:4040提供的pigeon服务：
```
http://service.dianping.com/com.dianping.pigeon.demo.EchoService=192.168.0.1:4040
```
这种方式要求应用增加一个lion配置：
xxx-service.pigeon.registry.config.local设置为true，在线下默认开启，线上关闭，不建议线上开启

2、通过api方式
```
ConfigManagerLoader.getConfigManager().setLocalStringValue("http://service.dianping.com/com.dianping.pigeon.demo.EchoService", "192.168.0.1:4040");
```
需要在程序启动时，调用前设置。这种方式要求应用增加一个lion配置：

xxx-service.pigeon.registry.config.local设置为true，在线下默认开启，线上关闭，不建议线上开启

3、运行时动态指定

如果要在代码层面设置，需要在调用服务前指定以下代码，线程级别每次请求前设置：
```
InvokerHelper.setAddress("192.168.0.1:4040");
```
该方式请在非线上环境使用，一般用于测试。

### 如何定义自己的拦截器

pigeon在客户端调用和服务端调用都提供了拦截器机制，方便用户可以获取到调用参数和返回结果。

注意：请不要在拦截器当中写消耗性能的代码，因为拦截器中的代码都是同步调用，如果执行太慢会影响服务调用的执行时间，用户如果想在拦截器中实现复杂逻辑，请自行进行异步处理。

在客户端可以实现自己的拦截器：  
```java
package com.dianping.pigeon.demo.interceptor;
  
import com.dianping.pigeon.remoting.invoker.domain.InvokerContext;
import com.dianping.pigeon.remoting.invoker.process.InvokerInterceptor;
 
public class MyInvokerInterceptor implements InvokerInterceptor {
 
 
    @Override
    public void preInvoke(InvokerContext invokerContext) {
        System.out.println("preInvoke:" + invokerContext.getRequest());    
    }
 
    @Override
    public void postInvoke(InvokerContext invokerContext) {
        // TODO Auto-generated method stub
         
    }
 
    @Override
    public void afterThrowing(InvokerContext invokerContext, Throwable throwable) {
        // TODO Auto-generated method stub
         
    }
 
}
```
在classpath下META-INF下增加一个services目录，目录下放一个com.dianping.pigeon.remoting.invoker.process.InvokerInterceptor文件，文件内容如下
在系统初始化时注册到pigeon中：

com.dianping.pigeon.demo.interceptor.MyInvokerInterceptor


同样的，在服务端也可以定义类似的拦截器：  
```java
package com.dianping.pigeon.demo.interceptor;
 
import com.dianping.pigeon.remoting.provider.domain.ProviderContext;
import com.dianping.pigeon.remoting.provider.process.ProviderInterceptor;
 
public class MyProviderInterceptor implements ProviderInterceptor {
 
    @Override
    public void preInvoke(ProviderContext providerContext) {
        System.out.println("preInvoke:" + providerContext);
    }
 
    @Override
    public void postInvoke(ProviderContext providerContext) {
        // TODO Auto-generated method stub
 
    }
 
}
```
在classpath下META-INF下增加一个services目录，目录下增加一个com.dianping.pigeon.remoting.provider.process.ProviderInterceptor文件，文件内容如下，它会在系统初始化时注册到pigeon中：

com.dianping.pigeon.demo.interceptor.MyProviderInterceptor


### 如何关闭自动注册

强烈建议不要关闭自动注册，如果特殊场合比如某些服务端需要自己做预热处理后再注册服务，可能需要关闭自动注册功能：

1、在应用的classpath下放config/pigeon.properties文件（该文件的配置是所有环境都生效，包括关闭线上自动注册，请谨慎使用，如果是只设置某个环境，也可以是

pigeon_dev.properties/pigeon_alpha.properties/pigeon_qa.properties/pigeon_prelease.properties/pigeon_product.properties），内容如下:

pigeon.autoregister.enable=false

这个配置也可以放在绝对路径/data/webapps/config/pigeon/pigeon.properties文件里

如果是关闭整个应用所有机器的自动注册，可以在lion对应项目配置里加上以下配置，如shop-server这个应用：

shop-server.pigeon.autoregister.enable配置为false

2、预热完了之后，再调pigeon的api完成服务发布：

ServiceFactory.online();

建议在全部初始化完成之后再调这个方法，如果没有调用这个接口，需要自行通过管理端程序去修改注册中心状态

### 服务端如何获取客户端信息

使用com.dianping.pigeon.remoting.common.util.ContextUtils类的接口

可通过
```java
(String) ContextUtils.getLocalContext("CLIENT_IP")
```
拿到上一级调用客户端的ip地址
可通过
```java
(String) ContextUtils.getLocalContext("CLIENT_APP")
```
拿到上一级调用客户端的appname

可通过
```java
ContextUtils.getGlobalContext("SOURCE_IP")
```
拿到请求最前端发起者的ip地址

可通过
```java
ContextUtils.getGlobalContext("SOURCE_APP")
```
拿到请求最前端发起者的appname

### 如何自定义loadbalance

一般情况下使用pigeon提供的random/roundRobin/weightedAutoaware这几种策略就足够了，如果需要自己实现负载均衡策略，可以在客户端的配置里添加loadBalanceClass属性，这个class必须实现com.dianping.pigeon.remoting.invoker.route.balance.LoadBalance接口，一般可以继承pigeon提供的AbstractLoadBalance抽象类或pigeon目前已有的loadbalance类。
```java
		<bean id="echoService" class="com.dianping.pigeon.remoting.invoker.config.spring.ReferenceBean"
		init-method="init">
			<property name="url"
			value="http://service.dianping.com/com.dianping.pigeon.demo.EchoService" />
			<property name="interfaceName" value="com.dianping.pigeon.demo.EchoService" />
			<property name="callType" value="sync" />
			<property name="timeout" value="1000" />
			<property name="loadBalanceClass"
			value="com.dianping.pigeon.demo.loadbalance.MyLoadbalance" />
		</bean>
```
> MyLoadbalance.java

```java
public class MyLoadbalance extends RoundRobinLoadBalance {

    @Override
    protected Client doSelect(List<Client> clients, InvocationRequest request, int[] weights) {
        if ("http://service.dianping.com/com.dianping.pigeon.demo.EchoService".equals(request.getServiceName()) && "echo".equals(request.getMethodName())) {
            if (request.getParameters().length > 0) {
                Object p0 = request.getParameters()[0];
                if (p0 != null) {
                    return clients.get(Math.abs(p0.hashCode() % clients.size()));
                }
            }
        }
        return super.doSelect(clients, request, weights);
    }
    
}
```

### 日志及监控输出控制
#### 如何控制cat上客户端超时异常的次数

pigeon可以设置客户端发生超时异常时在cat上控制异常记录的次数，可以在lion对应项目配置里加上以下配置，如xxx这个应用（需要保证classes/META-INF/app.properties里的app.name=xxx，这里的xxx必须与lion项目名称保持一致）：
```
xxx.pigeon.invoker.log.timeout.period.apps=shop-server:0,data-server:100
```
配置内容里，可以配置多个目标服务app的日志打印间隔，以逗号分隔，目标app也必须是点评统一标准应用名，如果某个目标服务app未配置则这个app的超时异常都会记录

每个app后边的数字，默认为0代表每个超时异常都会记录，如果配置为10000则任何超时异常都不会记录到cat，如果为1代表记录一半，如果为100代表每100个超时异常记录一次，数字越大记录的异常越少


#### 如何控制异常输出到cat和控制台

pigeon可以设置客户端调用异常时是否输出到cat和控制台，可以在lion对应项目配置里加上以下配置，如xxx这个应用（需要保证classes/META-INF/app.properties里的app.name=xxx，这里的xxx必须与lion项目名称保持一致）：

xxx.pigeon.invoker.log.exception.ignored=java.lang.InterruptedException,com.xxx.xxx.XxxException

如果不使用lion，可以在pigeon.properties里设置

pigeon.invoker.log.exception.ignored=java.lang.InterruptedException,com.xxx.xxx.XxxException

以上设置代表出现这些异常时pigeon不会记录异常到cat和控制台日志，但会抛出异常

#### pigeon框架日志

pigeon默认会将ERROR日志写入SYSTEM_ERR，WARN日志会写入SYSTEM_OUT，另外，pigeon内部还会将INFO和WARN级别的日志写入/data/applogs/pigeon/pigeon.*.log，但这个日志不会写入ERROR级别日志

#### 记录服务端每个请求的详细信息

pigeon可以设在服务端记录客户端发过来的每个请求的详细信息，需要在lion相应项目里配置：
```
xxx.pigeon.provider.accesslog.enable=true
```
配置好了之后pigeon会将日志记录在本地以下位置：

/data/applogs/pigeon/pigeon-access.log

每个请求记录的日志内容为：
```
应用名称+ "@" + 来源ip+ "@" + 请求对象内容（包含请求参数值等）+ "@" + 时间区间消耗
```
如果要记录每个参数值的内容，必须添加配置： pigeon.log.parameters设置为true

#### 记录服务端业务异常详细日志

pigeon在服务端默认不会记录业务方法抛出的异常详细信息，如果需要记录这类业务异常，需要增加以下配置：
```
pigeon.provider.logserviceexception为true
```
xxx是应用的app.name，需要与lion项目名称保持一致

###	获取服务注册信息

使用pigeon客户端接口：
```java
com.dianping.pigeon.governor.service.RegistrationInfoService 
```
用法:
```java
RegistrationInfoService registrationInfoService = ServiceFactory.getService(RegistrationInfoService.class);
String app = registrationInfoService.getAppOfService("com.dianping.demo.service.XXXService");
```
依赖：
```xml
<dependency>
	<groupId>com.dianping</groupId>
	<artifactId>pigeon-governor-api</artifactId>
	<version>RELEASE</version>
<dependency>
```

接口说明：
```java
package com.dianping.pigeon.governor.service;

import java.util.List;

import com.dianping.pigeon.registry.exception.RegistryException;

/**
 * pigeon注册信息服务
 * @author xiangwu
 *
 */
public interface RegistrationInfoService {

    /**
     * 获取服务的应用名称
     * @param url 服务名称，标示一个服务的url
     * @param group 泳道名称，没有填null
     * @return 应用名称
     * @throws RegistryException
     */
    String getAppOfService(String url, String group) throws RegistryException;
    
    /**
     * 获取服务的应用名称
     * @param url 服务名称，标示一个服务的url
     * @return 应用名称
     * @throws RegistryException
     */
    String getAppOfService(String url) throws RegistryException;
    
    /**
     * 获取服务地址的权重
     * @param address 服务地址，格式ip:port
     * @return 权重
     * @throws RegistryException
     */
    String getWeightOfAddress(String address) throws RegistryException;
    
    /**
     * 获取服务地址的应用名称
     * @param address 服务地址，格式ip:port
     * @return 应用名称
     * @throws RegistryException
     */
    String getAppOfAddress(String address) throws RegistryException;
    
    /**
     * 获取服务的地址列表
     * @param url 服务名称，标示一个服务的url
     * @param group 泳道，没有填null
     * @return 逗号分隔的地址列表，地址格式ip:port
     * @throws RegistryException
     */
    List<String> getAddressListOfService(String url, String group) throws RegistryException;
    
    /**
     * 获取服务的地址列表
     * @param url 服务名称，标示一个服务的url
     * @return 逗号分隔的地址列表，地址格式ip:port
     * @throws RegistryException
     */
    List<String> getAddressListOfService(String url) throws RegistryException;
}
```

### 泳道
泳道是lion提供的支持，用于机器级别的隔离，泳道配置在机器的/data/webapps/appenv里，例如：
```
deployenv=qa
zkserver=qa.lion.dp:2181
swimlane=tg
```
swimlane代表tg这个泳道，对于pigeon来说，如果一个service的机器定义了swimlane为tg，那么这个机器只能是客户端同样为tg泳道的机器能够调用

对于客户端来说，假设配置了泳道为tg，那么这个客户端机器调用远程服务时，会优先选择服务端泳道配置同样为tg的机器，如果tg泳道的机器不可用或不存在，才会调用其他未配置泳道的机器

### 动态服务分组路由

除了支持机器级别的隔离(泳道),pigeon还支持更细粒度,灵活性更高的服务分组隔离。

分组的主要特性为:
粒度细化到单个机器的单个服务级别
group 可运行时动态调整
group 不传递，只存在于一个调用层次
group 可以 fallback
配置了swimlane的机器不支持动态group特性

group 在 pigeon 的管理端(应用配置页面)进行配置。初始 group 全部为空。

配置group时,输入机器ip、服务名、分组配置即可。即时生效。

可在ip:4080/group查看分组配置信息。

### QPS监控信息

1、可以通过ip:4080/stats.json查看QPS信息

appRequestsReceived下会显示requests-lastsecond代表服务收到的请求最近一秒的QPS

appRequestsSent下会显示requests-lastsecond代表发出的请求最近一秒的QPS

2、QPS信息输出到监控系统

如果使用了cat监控系统，可以在cat的event里可以查看：

客户端发送的QPS：pigeonCall.QPS

服务端接收的QPS：pigeonService.QPS

在cat上可以看到从0-59秒在每一分钟的QPS值

