# 构建

Gradle工程：JDK17、Android SDK35、AGP8.9.2、Gradle8.11.1。设置local.properties的sdk.dir；复制secrets.properties.example为secrets.properties，提供APP_LOGIN_SALT（设备登录协议配置）。占位或空配置可编译，但不能实际登录。配置不纳入版本控制。

运行gradlew.bat assembleDebug（Linux使用./gradlew assembleDebug）。自行debug签名不能覆盖交付正式包。发布签名密钥、密码及私有协议配置不在仓库中。

交付APK实际使用JDK27的Java8兼容输出、API34平台库、targetSdk35、aapt/D8/zipalign/apksigner构建。build-local.ps1接受ToolsDir、JavaHome、SigningKey、PasswordFile、LoginSaltFile与Tests参数；ToolsDir需含android.jar、r8.jar、zxing.jar和build-tools。LoginSaltFile是仅包含协议签名参数的一行文本文件。BuildConfig.java以无BOM的UTF-8写入；Windows下如执行策略阻止脚本，可用`powershell -ExecutionPolicy Bypass -File build-local.ps1`。勿提交真实配置或生成的BuildConfig.java。

独立测试源码位于app/src/androidTest，未打入正式APK。安装正式APK与tests.apk后，运行am instrument -w com.joe.waterlite.tests/com.joe.waterlite.RegressionInstrumentation；-e mode seed写入模拟出水会话，随后am force-stop com.joe.waterlite，再以-e mode cold启动测试，验证独立进程读取与恢复。测试场景只使用fixture凭据和模拟接口。深色检查前执行cmd uimode night yes，浅色使用cmd uimode night no。
