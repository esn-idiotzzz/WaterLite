# 来源及许可证

饮水接口和错误码对照改编自 [abcde2333/NcepuJw](https://github.com/abcde2333/NcepuJw) 的 `IlifeClient.kt`，原作者 abcde2333，许可证 GPL-3.0。本项目按 GPL-3.0 交付，完整许可证见 LICENSE。

钱包数据结构参考 [nocookies111/life-798](https://github.com/nocookies111/life-798) 的 `WalletModels.kt`。原项目 MIT 许可证与版权声明见 third-party/life-798-MIT.txt。

二维码解析依赖 ZXing core 3.5.3，Apache-2.0，许可证见 third-party/ZXing-Apache-2.0.txt。Gradle wrapper 来源于原公开项目，其自身采用 Apache-2.0。

本应用重写了界面和本地存储，未包含原应用教务、课表、洗衣机、积分、支付宝支付、提醒、桌面小部件或底部导航模块。相机解码依赖不调用网络。登录、设备信息、余额和出水操作连接慧生活798服务。

1.2.0的设备端3.1.9请求头、短信平台、登录签名及结束参数，对照nocookies111/life-798提交86566185aac1138e80c49ff0267d42a18f3452ec的IlifeApi.java、Signer.java；MIT原文见third-party/life-798-MIT.txt。正式构建所用客户端协议配置核对该项目公开v5.5.0发行包，配置通过本机私有构建输入提供，未把第三方APK放入仓库。
