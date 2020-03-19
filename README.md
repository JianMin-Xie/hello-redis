## Redis缓存
结合Spring Boot，有两种方式，一种是直接通过 RedisTemplate 来使用，另一种是使用 Spring Cache 集成 Redis（也就是注解的方式）

## 第一种：RedisTemplate
### POM.xml
```
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```
* spring-boot-starter-data-redis：在 Spring Boot 2.x 以后底层不再使用 Jedis，而是换成了 Lettuce。

* commons-pool2：用作 Redis 连接池，如不引入启动会报错。

* spring-session-data-redis：Spring Session 引入，用作共享 Session。
### application.yml：
```
server:
  port: 8082
  servlet:
    session:
      timeout: 30ms
spring:
  cache:
    type: redis
  redis:
    host: 127.0.0.1
    port: 6379
    password:
    # redis默认情况下有16个分片，这里配置具体使用的分片，默认为0
    database: 0
    lettuce:
      pool:
        # 连接池最大连接数(使用负数表示没有限制),默认8
        max-active: 100
```
### 创建实体类 User.java：
```
public class User implements Serializable{

    private static final long serialVersionUID = 662692455422902539L;

    private Integer id;

    private String name;

    private Integer age;

    public User() {
    }

    public User(Integer id, String name, Integer age) {
        this.id = id;
        this.name = name;
        this.age = age;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
}
```

### RedisTemplate 的使用方式
默认情况下的模板只能支持 RedisTemplate<String, String>，也就是只能存入字符串，所以自定义模板很有必要。
添加配置类`RedisCacheConfig.java`：
```
@Configuration
@AutoConfigureAfter(RedisAutoConfiguration.class)
public class RedisCacheConfig {

    @Bean
    public RedisTemplate<String, Serializable> redisCacheTemplate(LettuceConnectionFactory connectionFactory) {

        RedisTemplate<String, Serializable> template = new RedisTemplate<>();
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setConnectionFactory(connectionFactory);
        return template;
    }
}
```
测试类：
```
@RestController
@RequestMapping("/user")
public class UserController {

    public static Logger logger = LogManager.getLogger(UserController.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Serializable> redisCacheTemplate;

    @RequestMapping("/test")
    public void test() {
        redisCacheTemplate.opsForValue().set("userkey", new User(1, "张三", 25));
        User user = (User) redisCacheTemplate.opsForValue().get("userkey");
        logger.info("当前获取对象：{}", user.toString());
    }
```
然后在浏览器访问，观察后台日志 http://localhost:8082/user/test
![RedisTemplate.JPG](https://i.loli.net/2020/03/20/R7me8GUnZ1PETp4.jpg)

## 第二种：使用 Spring Cache 集成 Redis
Spring Cache 具备很好的灵活性，不仅能够使用 SPEL（spring expression language）来定义缓存的 Key 和各种 Condition，还提供了开箱即用的缓存临时存储方案，也支持和主流的专业缓存如 EhCache、Redis、Guava 的集成。

定义接口 UserService.java：
```
public interface UserService {

    User save(User user);

    void delete(int id);

    User get(Integer id);
}
```

接口实现类 UserServiceImpl.java：
```
@Service
public class UserServiceImpl implements UserService{

    public static Logger logger = LogManager.getLogger(UserServiceImpl.class);

    private static Map<Integer, User> userMap = new HashMap<>();
    static {
        userMap.put(1, new User(1, "肖战", 25));
        userMap.put(2, new User(2, "王一博", 26));
        userMap.put(3, new User(3, "杨紫", 24));
    }


    @CachePut(value ="user", key = "#user.id")
    @Override
    public User save(User user) {
        userMap.put(user.getId(), user);
        logger.info("进入save方法，当前存储对象：{}", user.toString());
        return user;
    }

    @CacheEvict(value="user", key = "#id")
    @Override
    public void delete(int id) {
        userMap.remove(id);
        logger.info("进入delete方法，删除成功");
    }

    @Cacheable(value = "user", key = "#id")
    @Override
    public User get(Integer id) {
        logger.info("进入get方法，当前获取对象：{}", userMap.get(id)==null?null:userMap.get(id).toString());
        return userMap.get(id);
    }
}
```

为了方便演示数据库的操作，这里直接定义了一个 Map<Integer,User> userMap。

这里的核心是三个注解：
* @Cachable

* @CachePut

* @CacheEvict



测试类：UserController
```
@RestController
@RequestMapping("/user")
public class UserController {

    public static Logger logger = LogManager.getLogger(UserController.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Serializable> redisCacheTemplate;

    @Autowired
    private UserService userService;

    @RequestMapping("/test")
    public void test() {
        redisCacheTemplate.opsForValue().set("userkey", new User(1, "张三", 25));
        User user = (User) redisCacheTemplate.opsForValue().get("userkey");
        logger.info("当前获取对象：{}", user.toString());
    }


    @RequestMapping("/add")
    public void add() {
        User user = userService.save(new User(4, "李现", 30));
        logger.info("添加的用户信息：{}",user.toString());
    }

    @RequestMapping("/delete")
    public void delete() {
        userService.delete(4);
    }

    @RequestMapping("/get/{id}")
    public void get(@PathVariable("id") String idStr) throws Exception{
        if (StringUtils.isBlank(idStr)) {
            throw new Exception("id为空");
        }
        Integer id = Integer.parseInt(idStr);
        User user = userService.get(id);
        logger.info("获取的用户信息：{}",user.toString());
    }
}
```
用缓存要注意，启动类要加上一个注解开启缓存：
```
@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
@EnableCaching
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
```
①先调用添加接口：http://localhost:8082/user/add

![SpringCache集成Redis.JPG](https://i.loli.net/2020/03/20/vNz8BP5iuYSk9xJ.jpg)

②再调用查询接口，查询 id=4 的用户信息：http://localhost:8082/user/get/4
![SpringCache集成Redis2.JPG](https://i.loli.net/2020/03/20/cfHLlyx5uEVYwAQ.jpg)

可以看出，这里已经从缓存中获取数据了，因为上一步 add 方法已经把 id=4 的用户数据放入了 Redis 缓存 

③调用删除方法，删除 id=4 的用户信息，同时清除缓存：http://localhost:8082/user/delete
![SpringCache集成Redis3.JPG](https://i.loli.net/2020/03/20/w9vfDxzJLGbNrhP.jpg)


