#!/bin/bash
# 把开机首页和 Home 键恢复成创维原桌面
cd "$(dirname "$0")" || exit 1
ADB=./platform-tools/adb
TV="${TV:?先设置 TV=<电视IP>}"

"$ADB" connect "$TV:5555" >/dev/null 2>&1

# 开机首页：bootintent.xml 的 modelhome 规则
"$ADB" shell setprop persist.service.model.pkg com.tianci.movieplatform
"$ADB" shell setprop persist.service.model.cls com.coocaa.homepage.vast.HomePageActivity

# Home 键：厂商启动器读取的首页属性
"$ADB" shell setprop persist.service.homepage.pkg com.tianci.movieplatform
"$ADB" shell setprop persist.service.homepage.cls com.coocaa.homepage.vast.HomePageActivity

# 系统默认 HOME 恢复成出厂那个空壳桌面
"$ADB" shell pm set-home-activity com.example.android.home/.Home

echo "已恢复。如需彻底还原，再执行："
echo "  $ADB uninstall io.github.op7418.guizangtv"
