# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

#指定压缩级别
-optimizationpasses 5

#不跳过非公共的库的类成员
-dontskipnonpubliclibraryclassmembers

#混淆时采用的算法
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

#把混淆类中的方法名也混淆了
-useuniqueclassmembernames

#优化时允许访问并修改有修饰符的类和类的成员
-allowaccessmodification

#将文件来源重命名为“SourceFile”字符串
-renamesourcefileattribute SourceFile
#保留行号
-keepattributes SourceFile,LineNumberTable
#保持泛型
-keepattributes Signature

#保持所有实现 Serializable 接口的类成员
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

##Fragment不需要在AndroidManifest.xml中注册，需要额外保护下
#-keep public class * extends androidx.fragment.app.Fragment

-keepattributes Exceptions,InnerClasses,Signature,Deprecated,SourceFile,LineNumberTable,Annotation,EnclosingMethod,MethodParameters

-keep class *.R$ {
*;
}






#------BaseQuickAdapter混淆文件------
-keep class com.chad.library.adapter.** {
*;
}
-keep public class * extends com.chad.library.adapter.base.BaseQuickAdapter
-keep public class * extends com.chad.library.adapter.base.BaseViewHolder
-keep public class * extends com.chad.library.adapter.base.entity.MultiItemEntity
-keepclassmembers  class **$** extends com.chad.library.adapter.base.BaseViewHolder {
 <init>(...);
}
#------BaseQuickAdapter混淆文件------


-keep class io.agora.edu.classroom.bean.** { *; }

-keep public class io.agora.edu.launch.AgoraEduClassRoom { *; }

-keep public class io.agora.edu.launch.AgoraEduEvent { *; }

-keep public interface io.agora.edu.launch.AgoraEduLaunchCallback { *; }

-keep public class io.agora.edu.launch.AgoraEduLaunchConfig { *; }

-keep public class io.agora.edu.launch.AgoraEduReplay { *; }

-keep public class io.agora.edu.launch.AgoraEduReplayConfig { *; }

-keep public class io.agora.edu.launch.AgoraEduRoleType { *; }

-keep public class io.agora.edu.launch.AgoraEduRoomType { *; }

-keep public class io.agora.edu.launch.AgoraEduSDK
-keepclassmembers class io.agora.edu.launch.AgoraEduSDK {
     public static final int REQUEST_CODE_RTC;
     public static final int REQUEST_CODE_RTE;
     public static final java.lang.String CODE;
     public static final java.lang.String REASON;
     public static io.agora.edu.launch.AgoraEduLaunchCallback agoraEduLaunchCallback;
     public static java.lang.String version();
     public static void setConfig(io.agora.edu.launch.AgoraEduSDKConfig);
     public static io.agora.edu.launch.AgoraEduClassRoom launch(io.agora.edu.launch.AgoraEduLaunchConfig, io.agora.edu.launch.AgoraEduLaunchCallback);
     public static final java.lang.String WHITEBOARD_APP_ID;
     public static final java.lang.String WHITEBOARD_START_TIME;
     public static final java.lang.String WHITEBOARD_END_TIME;
     public static final java.lang.String VIDEO_URL;
     public static final java.lang.String WHITEBOARD_ID;
     public static final java.lang.String WHITEBOARD_TOKEN;
     public static io.agora.edu.launch.AgoraEduReplay replay(io.agora.edu.launch.AgoraEduReplayConfig, io.agora.edu.launch.AgoraEduLaunchCallback);
}

-keep public class io.agora.edu.launch.AgoraEduSDKConfig { *; }

-keep class io.agora.edu.common.bean.board.** { *; }
-keep class io.agora.edu.common.bean.roompre.** { *; }
-keep class io.agora.edu.common.bean.request.** { *; }
-keep class io.agora.edu.common.bean.response.** { *; }
-keep public class io.agora.edu.common.bean.ResponseBody { *; }


#指定混淆规则，否则多个module被混淆后的包名、类名、方法名会发生冲突
#指定一个混淆类名、成员变量名、方法名的字典。
-obfuscationdictionary ./confuse-name-rules.txt
#指定一个混淆类名的字典，字典的格式与-obfuscationdictionary相同；
-classobfuscationdictionary ./confuse-name-rules.txt
#指定一个混淆包名的字典，字典格式与-obfuscationdictionary相同。
-packageobfuscationdictionary ./confuse-name-rules.txt