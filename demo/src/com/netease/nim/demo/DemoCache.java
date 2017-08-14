package com.netease.nim.demo;

import android.content.Context;

import com.netease.nim.uikit.NimUIKit;
import com.netease.nimlib.sdk.StatusBarNotificationConfig;

/**
 * 使用静态属性缓存数据：上下文/用户账号/通知配置
 * Created by jezhee on 2/20/15.
 */
public class DemoCache {

    /**
     * 上下文
     */
    private static Context context;

    /**
     * 用户账号
     */
    private static String account;

    /**
     * 通知配置
     */
    private static StatusBarNotificationConfig notificationConfig;

    /**
     * 判断欢迎界面是否正在显示中
     */
    private static boolean mainTaskLaunching;

    /**
     * 清除用户账号
     */
    public static void clear() {
        account = null;
    }

    /**
     * 获取缓存的用户账号
     * @return
     */
    public static String getAccount() {
        return account;
    }

    public static void setAccount(String account) {
        DemoCache.account = account;
        NimUIKit.setAccount(account);
    }

    public static void setNotificationConfig(StatusBarNotificationConfig notificationConfig) {
        DemoCache.notificationConfig = notificationConfig;
    }

    public static StatusBarNotificationConfig getNotificationConfig() {
        return notificationConfig;
    }

    public static Context getContext() {
        return context;
    }

    public static void setContext(Context context) {
        DemoCache.context = context.getApplicationContext();
    }

    public static void setMainTaskLaunching(boolean mainTaskLaunching) {
        DemoCache.mainTaskLaunching = mainTaskLaunching;
    }

    public static boolean isMainTaskLaunching() {
        return mainTaskLaunching;
    }
}
