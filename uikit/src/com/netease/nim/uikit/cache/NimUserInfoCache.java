package com.netease.nim.uikit.cache;

import android.text.TextUtils;
import android.util.Log;

import com.netease.nim.uikit.NimUIKit;
import com.netease.nim.uikit.UIKitLogTag;
import com.netease.nim.uikit.common.util.log.LogUtil;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallback;
import com.netease.nimlib.sdk.RequestCallbackWrapper;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.friend.model.Friend;
import com.netease.nimlib.sdk.uinfo.UserService;
import com.netease.nimlib.sdk.uinfo.UserServiceObserve;
import com.netease.nimlib.sdk.uinfo.model.NimUserInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户资料数据缓存，适用于用户体系使用网易云信用户资料托管
 * 注册缓存变更通知，请使用UserInfoHelper的registerObserver方法
 * Created by huangjun on 2015/8/20.
 */
public class NimUserInfoCache {

    public static NimUserInfoCache getInstance() {
        return InstanceHolder.instance;
    }

    private Map<String, NimUserInfo> account2UserMap = new ConcurrentHashMap<>();

    private Map<String, List<RequestCallback<NimUserInfo>>> requestUserInfoMap = new ConcurrentHashMap<>(); // 重复请求处理

    /**
     * 构建缓存与清理
     * 从本地数据库中获取所有用户资料
     获取本地数据库中所有的用户资料，一般适合在登录后构建用户资料缓存时使用，代码示例如下：
     List<NimUserInfo> users = NIMClient.getService(UserService.class).getUserInfoList(accounts);
     通过用户账号，从本地数据库获取用户资料。代码示例如下：
     NimUserInfo user = NIMClient.getService(UserService.class).getUserInfo(account);
     */
    public void buildCache() {
        List<NimUserInfo> users = NIMClient.getService(UserService.class).getAllUserInfo();
        addOrUpdateUsers(users, false);
        LogUtil.i(UIKitLogTag.USER_CACHE, "build NimUserInfoCache completed, users count = " + account2UserMap.size());
    }

    public void clear() {
        clearUserCache();
    }

    /**
     * 从云信服务器获取用户信息（重复请求处理）[异步]
     */
    public void getUserInfoFromRemote(final String account, final RequestCallback<NimUserInfo> callback) {
        if (TextUtils.isEmpty(account)) {
            return;
        }

        if (requestUserInfoMap.containsKey(account)) {
            if (callback != null) {
                requestUserInfoMap.get(account).add(callback);
            }
            return; // 已经在请求中，不要重复请求
        } else {
            List<RequestCallback<NimUserInfo>> cbs = new ArrayList<>();
            if (callback != null) {
                cbs.add(callback);
            }
            requestUserInfoMap.put(account, cbs);
        }

        List<String> accounts = new ArrayList<>(1);
        accounts.add(account);

        /**
         * 获取服务器用户资料

         从服务器获取用户资料，一般在本地用户资料不存在时调用，获取后 SDK 会负责更新本地数据库。代码示例如下：

         此接口可以批量从服务器获取用户资料，从用户体验和流量成本考虑，不建议应用频繁调用此接口。对于用户数据实时性要求不高的页面，应尽量调用读取本地缓存接口。
         */
        NIMClient.getService(UserService.class).fetchUserInfo(accounts).setCallback(new RequestCallbackWrapper<List<NimUserInfo>>() {

            @Override
            public void onResult(int code, List<NimUserInfo> users, Throwable exception) {
                if (exception != null) {
                    callback.onException(exception);
                    return;
                }

                NimUserInfo user = null;
                boolean hasCallback = requestUserInfoMap.get(account).size() > 0;
                if (code == ResponseCode.RES_SUCCESS && users != null && !users.isEmpty()) {
                    user = users.get(0);
                    // 这里不需要更新缓存，由监听用户资料变更（添加）来更新缓存
                }

                // 处理回调
                if (hasCallback) {
                    List<RequestCallback<NimUserInfo>> cbs = requestUserInfoMap.get(account);
                    for (RequestCallback<NimUserInfo> cb : cbs) {
                        if (code == ResponseCode.RES_SUCCESS) {
                            cb.onSuccess(user);
                        } else {
                            cb.onFailed(code);
                        }
                    }
                }

                requestUserInfoMap.remove(account);
            }
        });
    }

    /**
     * 从云信服务器获取批量用户信息[异步]
     */
    public void getUserInfoFromRemote(List<String> accounts, final RequestCallback<List<NimUserInfo>> callback) {
        NIMClient.getService(UserService.class).fetchUserInfo(accounts).setCallback(new RequestCallback<List<NimUserInfo>>() {
            @Override
            public void onSuccess(List<NimUserInfo> users) {
                Log.i(UIKitLogTag.USER_CACHE, "fetch userInfo completed, add users size =" + users.size());
                // 这里不需要更新缓存，由监听用户资料变更（添加）来更新缓存
                if (callback != null) {
                    callback.onSuccess(users);
                }
            }

            @Override
            public void onFailed(int code) {
                if (callback != null) {
                    callback.onFailed(code);
                }
            }

            @Override
            public void onException(Throwable exception) {
                if (callback != null) {
                    callback.onException(exception);
                }
            }
        });
    }

    /**
     * ******************************* 业务接口（获取缓存的用户信息） *********************************
     */

    public List<NimUserInfo> getAllUsersOfMyFriend() {
        List<String> accounts = FriendDataCache.getInstance().getMyFriendAccounts();
        List<NimUserInfo> users = new ArrayList<>();
        List<String> unknownAccounts = new ArrayList<>();
        for (String account : accounts) {
            if (hasUser(account)) {
                users.add(getUserInfo(account));
            } else {
                unknownAccounts.add(account);
            }
        }

        // fetch unknown userInfo，根本不会发生，再次仅作测试校验，可以删去
        if (!unknownAccounts.isEmpty()) {
            DataCacheManager.Log(unknownAccounts, "lack friend userInfo", UIKitLogTag.USER_CACHE);
            getUserInfoFromRemote(unknownAccounts, null);
        }

        return users;
    }

    public NimUserInfo getUserInfo(String account) {
        if (TextUtils.isEmpty(account) || account2UserMap == null) {
            LogUtil.e(UIKitLogTag.USER_CACHE, "getUserInfo null, account=" + account + ", account2UserMap=" + account2UserMap);
            return null;
        }

        return account2UserMap.get(account);
    }

    public boolean hasUser(String account) {
        if (TextUtils.isEmpty(account) || account2UserMap == null) {
            LogUtil.e(UIKitLogTag.USER_CACHE, "hasUser null, account=" + account + ", account2UserMap=" + account2UserMap);
            return false;
        }

        return account2UserMap.containsKey(account);
    }

    /**
     * 获取用户显示名称。
     * 若设置了备注名，则显示备注名。
     * 若没有设置备注名，用户有昵称则显示昵称，用户没有昵称则显示帐号。
     *
     * @param account 用户帐号
     * @return
     */
    public String getUserDisplayName(String account) {
        String alias = getAlias(account);
        if (!TextUtils.isEmpty(alias)) {
            return alias;
        }

        return getUserName(account);
    }

    public String getAlias(String account) {
        Friend friend = FriendDataCache.getInstance().getFriendByAccount(account);
        if (friend != null && !TextUtils.isEmpty(friend.getAlias())) {
            return friend.getAlias();
        }
        return null;
    }

    // 获取用户原本的昵称
    public String getUserName(String account) {
        NimUserInfo userInfo = getUserInfo(account);
        if (userInfo != null && !TextUtils.isEmpty(userInfo.getName())) {
            return userInfo.getName();
        } else {
            return account;
        }
    }

    public String getUserDisplayNameEx(String account) {
        if (account.equals(NimUIKit.getAccount())) {
            return "我";
        }

        return getUserDisplayName(account);
    }

    public String getUserDisplayNameYou(String account) {
        if (account.equals(NimUIKit.getAccount())) {
            return "你";  // 若为用户自己，显示“你”
        }

        return getUserDisplayName(account);
    }

    private void clearUserCache() {
        account2UserMap.clear();
    }

    /**
     * ************************************ 用户资料变更监听(监听SDK) *****************************************
     */

    /**
     * 在Application的onCreate中向SDK注册用户资料变更观察者
     *
     * 监听用户资料变更
     用户资料除自己之外，不保证其他用户资料实时更新。其他用户数据更新时机为：

     调用 fetchUserInfo 方法刷新用户
     收到此用户发来消息（如果消息发送者有资料变更，SDK 会负责更新并通知）
     程序再次启动，此时会同步好友信息
     由于用户资料变更需要跨进程异步调用，开发者最好能在第三方 APP 中做好用户资料缓存，
     查询用户资料时都从本地缓存中访问。在用户资料有变化时，SDK 会告诉注册的观察者，此时，第三方 APP 可更新缓存，并刷新界面。 代码示例如下：
     */
    public void registerObservers(boolean register) {
        NIMClient.getService(UserServiceObserve.class).observeUserInfoUpdate(userInfoUpdateObserver, register);
    }

    /**
     * 监听用户资料变更
     用户资料除自己之外，不保证其他用户资料实时更新。其他用户数据更新时机为：

     调用 fetchUserInfo 方法刷新用户
     收到此用户发来消息（如果消息发送者有资料变更，SDK 会负责更新并通知）
     程序再次启动，此时会同步好友信息
     由于用户资料变更需要跨进程异步调用，开发者最好能在第三方 APP 中做好用户资料缓存，查询用户资料时都从本地缓存中访问。
     在用户资料有变化时，SDK 会告诉注册的观察者，此时，第三方 APP 可更新缓存，并刷新界面。 代码示例如下：
     */
    private Observer<List<NimUserInfo>> userInfoUpdateObserver = new Observer<List<NimUserInfo>>() {
        @Override
        public void onEvent(List<NimUserInfo> users) {
            if (users == null || users.isEmpty()) {
                return;
            }

            addOrUpdateUsers(users, true);
        }
    };

    /**
     * *************************************** User缓存管理与变更通知 ********************************************
     */

    private void addOrUpdateUsers(final List<NimUserInfo> users, boolean notify) {
        if (users == null || users.isEmpty()) {
            return;
        }

        // update cache
        for (NimUserInfo u : users) {
            account2UserMap.put(u.getAccount(), u);
        }

        // log
        List<String> accounts = getAccounts(users);
        DataCacheManager.Log(accounts, "on userInfo changed", UIKitLogTag.USER_CACHE);

        // 通知变更
        if (notify && accounts != null && !accounts.isEmpty()) {
            NimUIKit.notifyUserInfoChanged(accounts); // 通知到UI组件
        }
    }

    private List<String> getAccounts(List<NimUserInfo> users) {
        if (users == null || users.isEmpty()) {
            return null;
        }

        List<String> accounts = new ArrayList<>(users.size());
        for (NimUserInfo user : users) {
            accounts.add(user.getAccount());
        }

        return accounts;
    }

    /**
     * ************************************ 单例 **********************************************
     */

    static class InstanceHolder {
        final static NimUserInfoCache instance = new NimUserInfoCache();
    }
}
