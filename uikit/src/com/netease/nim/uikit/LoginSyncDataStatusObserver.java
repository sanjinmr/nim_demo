package com.netease.nim.uikit;

import android.os.Handler;

import com.netease.nim.uikit.common.util.log.LogUtil;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.auth.AuthServiceObserver;
import com.netease.nimlib.sdk.auth.constant.LoginSyncStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * 登录
 * Created by huangjun on 2015/10/9.
 */
public class LoginSyncDataStatusObserver {

    private static final String TAG = LoginSyncDataStatusObserver.class.getSimpleName();

    private static final int TIME_OUT_SECONDS = 10;

    private Handler uiHandler;

    private Runnable timeoutRunnable;

    /**
     * 状态
     */
    private LoginSyncStatus syncStatus = LoginSyncStatus.NO_BEGIN;

    /**
     * 监听
     */
    private List<Observer<Void>> observers = new ArrayList<>();

    /**
     * 注销时清除状态&监听
     */
    public void reset() {
        syncStatus = LoginSyncStatus.NO_BEGIN;
        observers.clear();
    }

    /**
     * 在App启动时向SDK注册登录后同步数据过程状态的通知
     * 调用时机：主进程Application onCreate中
     *
     * 数据同步状态通知

     登录成功后，SDK 会立即同步数据（用户资料、用户关系、群资料、离线消息、漫游消息等），同步开始和同步完成都会发出通知。

     注册登录同步状态通知：

     一般来说， APP 开发者在登录完成后可以开始构建数据缓存：登录完成后立即从 SDK 读取数据构建缓存，此时加载到的可能是旧数据；
      在 Application 的 onCreate 中注册 XXXServiceObserver 来监听数据变化，
      那么在同步过程中， APP 会收到数据更新通知，此时直接更新缓存。当同步完成时，缓存也就构建完成了。

     */
    public void registerLoginSyncDataStatus(boolean register) {
        LogUtil.i(TAG, "observe login sync data completed event on Application create");
        NIMClient.getService(AuthServiceObserver.class).observeLoginSyncDataStatus(loginSyncStatusObserver, register);
    }

    Observer<LoginSyncStatus> loginSyncStatusObserver = new Observer<LoginSyncStatus>() {
        @Override
        public void onEvent(LoginSyncStatus status) {
            syncStatus = status;
            if (status == LoginSyncStatus.BEGIN_SYNC) {
                // 同步开始时，SDK 数据库中的数据可能还是旧数据
                // （如果是首次登录，那么 SDK 数据库中还没有数据，重新登录时 SDK 数据库中还是上一次退出时保存的数据）。
                LogUtil.i(TAG, "login sync data begin");
            } else if (status == LoginSyncStatus.SYNC_COMPLETED) {
                // 同步完成时， SDK 数据库已完成更新。
                LogUtil.i(TAG, "login sync data completed");
                // 同步数据完成，并遍历观察集合，回调给上层调用者
                onLoginSyncDataCompleted(false);
            }
        }
    };

    /**
     * 监听登录后同步数据完成事件，缓存构建完成后自动取消监听
     * 调用时机：登录成功后
     *
     * @param observer 观察者
     * @return 返回true表示数据同步已经完成或者不进行同步，返回false表示正在同步数据
     */
    public boolean observeSyncDataCompletedEvent(Observer<Void> observer) {
        if (syncStatus == LoginSyncStatus.NO_BEGIN || syncStatus == LoginSyncStatus.SYNC_COMPLETED) {
            /*
            * NO_BEGIN 如果登录后未开始同步数据，那么可能是自动登录的情况:
            * PUSH进程已经登录同步数据完成了，此时UI进程启动后并不知道，这里直接视为同步完成
            */
            return true;
        }

        // 正在同步
        if (!observers.contains(observer)) {
            observers.add(observer);
        }

        // 超时定时器
        if (uiHandler == null) {
            uiHandler = new Handler(NimUIKit.getContext().getMainLooper());
        }

        if (timeoutRunnable == null) {
            timeoutRunnable = new Runnable() {
                @Override
                public void run() {
                    // 如果超时还处于开始同步的状态，模拟结束
                    if (syncStatus == LoginSyncStatus.BEGIN_SYNC) {
                        onLoginSyncDataCompleted(true);
                    }
                }
            };
        }

        uiHandler.removeCallbacks(timeoutRunnable);
        uiHandler.postDelayed(timeoutRunnable, TIME_OUT_SECONDS * 1000);

        return false;
    }

    /**
     * 登录同步数据完成处理
     */
    private void onLoginSyncDataCompleted(boolean timeout) {
        LogUtil.i(TAG, "onLoginSyncDataCompleted, timeout=" + timeout);

        // 移除超时任务（有可能完成包到来的时候，超时任务都还没创建）
        if (timeoutRunnable != null) {
            uiHandler.removeCallbacks(timeoutRunnable);
        }

        // 通知上层
        for (Observer<Void> o : observers) {
            o.onEvent(null);
        }

        // 重置状态
        reset();
    }


    /**
     * 单例
     */
    public static LoginSyncDataStatusObserver getInstance() {
        return InstanceHolder.instance;
    }

    static class InstanceHolder {
        final static LoginSyncDataStatusObserver instance = new LoginSyncDataStatusObserver();
    }
}
