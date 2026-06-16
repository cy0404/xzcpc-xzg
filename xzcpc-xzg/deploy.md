# 象掌柜后端部署步骤

## 服务器信息
- IP：119.45.162.160
- 端口：4026
- 数据库：119.45.162.160:3306 / store_Inventory
- 部署路径：/opt/xzcpc/

## 1. 打包

```bash
cd xzcpc-xzg
C:\Users\xiemg\AppData\Local\Temp\apache-maven-3.9.6\bin\mvn clean package -DskipTests
```

产物：server/target/server-1.0.0.jar

## 2. 上传

```bash
scp server/target/server-1.0.0.jar root@119.45.162.160:/opt/xzcpc/
```

## 3. 启动

```bash
ssh root@119.45.162.160
cd /opt/xzcpc
pkill -f server-1.0.0.jar
nohup java -jar server-1.0.0.jar --spring.profiles.active=prod > server.log 2>&1 &
```

## 4. 验证

```bash
curl http://localhost:4026/api/materials/all
```
