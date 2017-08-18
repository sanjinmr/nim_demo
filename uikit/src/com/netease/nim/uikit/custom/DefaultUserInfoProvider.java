package com.netease.nim.uikit.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import com.netease.nim.uikit.NimUIKit;
import com.netease.nim.uikit.R;
import com.netease.nim.uikit.cache.NimUserInfoCache;
import com.netease.nim.uikit.cache.TeamDataCache;
import com.netease.nimlib.sdk.msg.constant.SessionTypeEnum;
import com.netease.nimlib.sdk.team.model.Team;
import com.netease.nimlib.sdk.uinfo.UserInfoProvider;

/**
 * UIKit默认的用户信息提供者
 * 用户资料提供者, 目前主要用于提供用户资料，用于新消息通知栏中显示消息来源的头像和昵称
 * 实现上述需要的方法，在 SDKOptions 中配置 UserInfoProvider 实例，在 SDK 初始化时传入 SDKOptions 方可生效。
 需要注意的是，上述返回头像 Bitmap 的函数，请尽可能从内存缓存里拿头像，如果读取本地头像可能导致 UI 进程阻塞，从而导致通知栏提醒延时弹出。
 * <p>
 * Created by hzchenkang on 2016/12/19.
 */

public class DefaultUserInfoProvider implements UserInfoProvider {

    private Context context;

    public DefaultUserInfoProvider(Context context) {
        this.context = context;
    }

    @Override
    public UserInfo getUserInfo(String account) {
        UserInfo user = NimUserInfoCache.getInstance().getUserInfo(account);
        if (user == null) {
            NimUserInfoCache.getInstance().getUserInfoFromRemote(account, null);
        }

        return user;
    }

    /**
     * 如果根据用户账号找不到UserInfo的avatar时，显示的默认头像资源ID
     *
     * @return 默认头像的资源ID
     */
    @Override
    public int getDefaultIconResId() {
        return R.drawable.nim_avatar_default;
    }

    /**
     * 为通知栏提供消息发送者显示名称（例如：如果是P2P聊天，可以显示备注名、昵称、帐号等；如果是群聊天，可以显示群昵称，备注名，昵称、帐号等）
     *
     * @param account     消息发送者账号
     * @param sessionId   会话ID（如果是P2P聊天，那么会话ID即为发送者账号，如果是群聊天，那么会话ID就是群号）
     * @param sessionType 会话类型
     * @return 消息发送者对应的显示名称
     */
    @Override
    public String getDisplayNameForMessageNotifier(String account, String sessionId, SessionTypeEnum sessionType) {
        String nick = null;
        if (sessionType == SessionTypeEnum.P2P) {
            nick = NimUserInfoCache.getInstance().getAlias(account);
        } else if (sessionType == SessionTypeEnum.Team) {
            nick = TeamDataCache.getInstance().getDisplayNameWithoutMe(sessionId, account);
        }
        // 返回null，交给sdk处理。如果对方有设置nick，sdk会显示nick
        if (TextUtils.isEmpty(nick)) {
            return null;
        }

        return nick;
    }

    /**
     * 为通知栏提供用户头像（一般从本地缓存中取，若未下载或本地不存在，返回null，通知栏将显示默认头像）
     *
     * @return 头像位图
     */
    @Override
    public Bitmap getAvatarForMessageNotifier(String account) {
        /*
         * 注意：这里最好从缓存里拿，如果加载时间过长会导致通知栏延迟弹出！该函数在后台线程执行！
         */
        UserInfo user = getUserInfo(account);
        return (user != null) ? NimUIKit.getImageLoaderKit().getNotificationBitmapFromCache(user.getAvatar()) : null;
    }

    /**
     * 根据群组ID获取群组头像位图。头像功能可由app自己拼接或自定义，也可以直接使用预置图片作为头像
     * 为通知栏提供群头像（一般从本地缓存中取，若未下载、未合成或者本地缓存不存，请返回预置的群头像资源ID对应的Bitmap）
     *
     * @param teamId 群组ID
     * @return 群组头像位图
     */
    @Override
    public Bitmap getTeamIcon(String teamId) {
        /*
         * 注意：这里最好从缓存里拿，如果加载时间过长会导致通知栏延迟弹出！该函数在后台线程执行！
         */
        Team team = TeamDataCache.getInstance().getTeamById(teamId);
        if (team != null) {
            Bitmap bm = NimUIKit.getImageLoaderKit().getNotificationBitmapFromCache(team.getIcon());
            if (bm != null) {
                return bm;
            }
        }

        // 默认图
        Drawable drawable = context.getResources().getDrawable(R.drawable.nim_avatar_group);
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        return null;
    }
}
