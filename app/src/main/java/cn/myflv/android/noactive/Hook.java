package cn.myflv.android.noactive;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import cn.myflv.android.noactive.entity.ClassEnum;
import cn.myflv.android.noactive.entity.MemData;
import cn.myflv.android.noactive.entity.MethodEnum;
import cn.myflv.android.noactive.hook.ANRHook;
import cn.myflv.android.noactive.hook.AppSwitchHook;
import cn.myflv.android.noactive.hook.BroadcastDeliverHook;
import cn.myflv.android.noactive.hook.OomAdjHook;
import cn.myflv.android.noactive.utils.FreezerConfig;
import cn.myflv.android.noactive.utils.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam packageParam) throws Throwable {
        // 禁用 millet
        if (packageParam.packageName.equals("com.miui.powerkeeper")) {
            try {
                XposedHelpers.findAndHookMethod(ClassEnum.MilletConfig, packageParam.classLoader, MethodEnum.getEnable, Context.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        return false;
                    }
                });
                XposedBridge.log("NoActive -> Disable millet");
            } catch (Exception ignored) {
            }
            return;
        }
        if (!packageParam.packageName.equals("android")) return;
        MemData memData = new MemData();
        ClassLoader classLoader = packageParam.classLoader;

        Log.i(Build.MANUFACTURER + " device");

//        XposedHelpers.findAndHookMethod(ClassEnum.ProcessRecord, classLoader, MethodEnum.setKilled, boolean.class, new ProcessKilledHook());
//        XposedHelpers.findAndHookMethod(ClassEnum.ProcessRecord, classLoader, MethodEnum.setKilledByAm, boolean.class, new ProcessKilledHook());

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            XposedHelpers.findAndHookMethod(ClassEnum.CachedAppOptimizer, classLoader, MethodEnum.useFreezer, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return false;
                }
            });
            Log.i("Disable cache freezer");
        }

        // Hook 切换事件
        if (Build.MANUFACTURER.equals("samsung")) {
            XposedHelpers.findAndHookMethod(ClassEnum.ActivityManagerService, classLoader,
                    MethodEnum.updateActivityUsageStats,
                    ClassEnum.ComponentName, int.class, int.class,
                    ClassEnum.IBinder, ClassEnum.ComponentName, Intent.class, new AppSwitchHook(classLoader, memData, AppSwitchHook.DIFFICULT));
        } else {
            XposedHelpers.findAndHookMethod(ClassEnum.ActivityManagerService, classLoader,
                    MethodEnum.updateActivityUsageStats,
                    ClassEnum.ComponentName, int.class, int.class,
                    ClassEnum.IBinder, ClassEnum.ComponentName, new AppSwitchHook(classLoader, memData, AppSwitchHook.DIFFICULT));
        }


        // Hook 广播分发
        XposedHelpers.findAndHookMethod(ClassEnum.BroadcastQueue, classLoader, MethodEnum.deliverToRegisteredReceiverLocked,
                ClassEnum.BroadcastRecord,
                ClassEnum.BroadcastFilter, boolean.class, int.class, new BroadcastDeliverHook(memData));

        if (!FreezerConfig.isDisableOOM()) {
            // Hook oom_adj
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                XposedHelpers.findAndHookMethod(ClassEnum.ProcessStateRecord, classLoader, MethodEnum.setCurAdj, int.class, new OomAdjHook(memData, OomAdjHook.Android_S));
                Log.i("Auto lmk");
            } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R || Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                XposedHelpers.findAndHookMethod(ClassEnum.OomAdjuster, classLoader, MethodEnum.applyOomAdjLocked, ClassEnum.ProcessRecord, boolean.class, long.class, long.class, new OomAdjHook(memData, OomAdjHook.Android_Q_R));
                Log.i("Auto lmk");
            }
        }

        // Hook ANR
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            XposedHelpers.findAndHookMethod(ClassEnum.AnrHelper, classLoader, MethodEnum.appNotResponding,
                    ClassEnum.ProcessRecord,
                    String.class,
                    ClassEnum.ApplicationInfo,
                    String.class,
                    ClassEnum.WindowProcessController,
                    boolean.class,
                    String.class, new ANRHook(classLoader, memData));
            Log.i("Auto keep process");

        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            XposedHelpers.findAndHookMethod(ClassEnum.ProcessRecord, classLoader, MethodEnum.appNotResponding,
                    String.class, ClassEnum.ApplicationInfo, String.class, ClassEnum.WindowProcessController, boolean.class, String.class, XC_MethodReplacement.DO_NOTHING);
            Log.i("Android Q");
            Log.i("Force keep process");
        } else {

            XposedHelpers.findAndHookMethod(ClassEnum.AppErrors, classLoader, MethodEnum.appNotResponding,
                    ClassEnum.ProcessRecord, ClassEnum.ActivityRecord, ClassEnum.ActivityRecord, boolean.class, String.class,
                    XC_MethodReplacement.DO_NOTHING
            );
            Log.i("Android N-P");
            Log.i("Force keep process");
        }

        Log.i("Load success");

    }

}
