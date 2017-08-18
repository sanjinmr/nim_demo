package com.netease.nim.uikit.cache;

import android.text.TextUtils;

import com.netease.nim.uikit.NimUIKit;
import com.netease.nim.uikit.UIKitLogTag;
import com.netease.nim.uikit.common.util.log.LogUtil;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.Observer;
import com.netease.nimlib.sdk.RequestCallbackWrapper;
import com.netease.nimlib.sdk.ResponseCode;
import com.netease.nimlib.sdk.team.TeamService;
import com.netease.nimlib.sdk.team.TeamServiceObserver;
import com.netease.nimlib.sdk.team.constant.TeamTypeEnum;
import com.netease.nimlib.sdk.team.model.Team;
import com.netease.nimlib.sdk.team.model.TeamMember;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群信息/群成员数据监听&缓存
 * <p/>
 * Created by huangjun on 2015/3/1.
 */
public class TeamDataCache {
    private static TeamDataCache instance;

    public static synchronized TeamDataCache getInstance() {
        if (instance == null) {
            instance = new TeamDataCache();
        }

        return instance;
    }

    public void buildCache() {
        /**
         * 获取群组

         SDK 提供了两个获取自己加入的所有群的列表的接口，一个是获取所有群（包括高级群和普通群）的接口，另一个是根据类型获取列表的接口。开发者可根据实际产品需求选择使用。

         注意：这里获取的是所有我加入的群列表（退群、被移除群后，将不在返回列表中），如果需要自己退群或者被移出群的群资料，请使用下面的 queryTeam 接口。

         获取所有我加入的群：

         NIMClient.getService(TeamService.class).queryTeamList()
         .setCallback(new RequestCallbackWrapper<List<Team>>() { ... });
         也可以直接使用这个函数的同步版本：

         List<Team> teams = NIMClient.getService(TeamService.class).queryTeamListBlock();
         按照类型获取自己加入的群列表：

         NIMClient.getService(TeamService.class).queryTeamListByType(type)
         .setCallback(new RequestCallback<List<Team>>() { ... });
         */
        final List<Team> teams = NIMClient.getService(TeamService.class).queryTeamListBlock();
        LogUtil.i(UIKitLogTag.TEAM_CACHE, "start build TeamDataCache");

        addOrUpdateTeam(teams);

        LogUtil.i(UIKitLogTag.TEAM_CACHE, "build TeamDataCache completed, team count = " + teams.size());
    }

    public void clear() {
        clearTeamCache();
        clearTeamMemberCache();
    }

    /**
     * *
     * ******************************************** 观察者 ********************************************
     */

    public interface TeamDataChangedObserver {
        void onUpdateTeams(List<Team> teams);

        void onRemoveTeam(Team team);
    }

    public interface TeamMemberDataChangedObserver {
        void onUpdateTeamMember(List<TeamMember> members);

        void onRemoveTeamMember(TeamMember member);
    }

    private List<TeamDataChangedObserver> teamObservers = new ArrayList<>();
    private List<TeamMemberDataChangedObserver> memberObservers = new ArrayList<>();

    public void registerObservers(boolean register) {
        // 注册/注销观察者 群资料变动观察者通知
        NIMClient.getService(TeamServiceObserver.class).observeTeamUpdate(teamUpdateObserver, register);
        // 注册/注销观察者 移除群的观察者通知
        NIMClient.getService(TeamServiceObserver.class).observeTeamRemove(teamRemoveObserver, register);
        // 监听群成员资料变化
        // 群成员资料变化观察者通知
        // 由于获取群成员资料需要跨进程异步调用，开发者最好能在第三方 APP 中做好群成员资料缓存，
        // 查询群成员资料时都从本地缓存中访问。在群成员资料有变化时，SDK 会告诉注册的观察者，此时，第三方 APP 可更新缓存，并刷新界面。
        NIMClient.getService(TeamServiceObserver.class).observeMemberUpdate(memberUpdateObserver, register);
        // 移除群成员的观察者通知。
        NIMClient.getService(TeamServiceObserver.class).observeMemberRemove(memberRemoveObserver, register);
    }

    /**
     * 群资料变动观察者通知。新建群和群更新的通知都通过该接口传递
     * 监听群组资料变化

     由于获取群组资料需要跨进程异步调用，开发者最好能在第三方 APP 中做好群组资料缓存，查询群组资料时都从本地缓存中访问。
     在群组资料有变化时，SDK 会告诉注册的观察者，此时，第三方 APP 可更新缓存，并刷新界面。

     创建群组资料变动观察者
     */
    private Observer<List<Team>> teamUpdateObserver = new Observer<List<Team>>() {
        @Override
        public void onEvent(final List<Team> teams) {
            if (teams != null) {
                LogUtil.i(UIKitLogTag.TEAM_CACHE, "team update size:" + teams.size());
            }
            addOrUpdateTeam(teams);
            notifyTeamDataUpdate(teams);
        }
    };

    // 移除群的观察者通知。自己退群，群被解散，自己被踢出群时，会收到该通知
    private Observer<Team> teamRemoveObserver = new Observer<Team>() {
        @Override
        public void onEvent(Team team) {
            // 由于界面上可能仍需要显示群组名等信息，因此参数中会返回 Team 对象。
            // 该对象的 isMyTeam 接口返回为 false。
            // team的flag被更新，isMyTeam为false
            addOrUpdateTeam(team);
            notifyTeamDataRemove(team);
        }
    };

    // 群成员资料变化观察者通知。可通过此接口更新缓存。
    private Observer<List<TeamMember>> memberUpdateObserver = new Observer<List<TeamMember>>() {
        @Override
        public void onEvent(List<TeamMember> members) {
            addOrUpdateTeamMembers(members);
            notifyTeamMemberDataUpdate(members);
        }
    };

    // 移除群成员的观察者通知。
    private Observer<TeamMember> memberRemoveObserver = new Observer<TeamMember>() {
        @Override
        public void onEvent(TeamMember member) {
            // member的validFlag被更新，isInTeam为false
            addOrUpdateTeamMember(member);
            notifyTeamMemberRemove(member);
        }
    };

    public void registerTeamDataChangedObserver(TeamDataChangedObserver o) {
        if (teamObservers.contains(o)) {
            return;
        }

        teamObservers.add(o);
    }

    public void unregisterTeamDataChangedObserver(TeamDataChangedObserver o) {
        teamObservers.remove(o);
    }

    public void registerTeamMemberDataChangedObserver(TeamMemberDataChangedObserver o) {
        if (memberObservers.contains(o)) {
            return;
        }

        memberObservers.add(o);
    }

    public void unregisterTeamMemberDataChangedObserver(TeamMemberDataChangedObserver o) {
        memberObservers.remove(o);
    }

    private void notifyTeamDataUpdate(List<Team> teams) {
        for (TeamDataChangedObserver o : teamObservers) {
            o.onUpdateTeams(teams);
        }
    }

    private void notifyTeamDataRemove(Team team) {
        for (TeamDataChangedObserver o : teamObservers) {
            o.onRemoveTeam(team);
        }
    }

    private void notifyTeamMemberDataUpdate(List<TeamMember> members) {
        for (TeamMemberDataChangedObserver o : memberObservers) {
            o.onUpdateTeamMember(members);
        }
    }

    private void notifyTeamMemberRemove(TeamMember member) {
        for (TeamMemberDataChangedObserver o : memberObservers) {
            o.onRemoveTeamMember(member);
        }
    }

    /**
     * *
     * ******************************************** 群资料缓存 ********************************************
     */

    private Map<String, Team> id2TeamMap = new ConcurrentHashMap<>();

    public void clearTeamCache() {
        id2TeamMap.clear();
    }

    /**
     * 异步获取Team（先从SDK DB中查询，如果不存在，则去服务器查询）
     * 根据群ID查询群资料：

     如果本地没有群组资料，则去服务器查询。
     如果自己不在这个群中，该接口返回的可能是过期资料，如需最新的，请调用 searchTeam 接口去服务器查询。
     此外 queryTeam 接口也有同步版本： queryTeamBlock 。
     */
    public void fetchTeamById(final String teamId, final SimpleCallback<Team> callback) {
        NIMClient.getService(TeamService.class).queryTeam(teamId).setCallback(new RequestCallbackWrapper<Team>() {
            @Override
            public void onResult(int code, Team t, Throwable exception) {
                boolean success = true;
                if (code == ResponseCode.RES_SUCCESS) {
                    addOrUpdateTeam(t);
                } else {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamById failed, code=" + code);
                }

                if (exception != null) {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamById throw exception, e=" + exception.getMessage());
                }

                if (callback != null) {
                    callback.onResult(success, t);
                }
            }
        });
    }

    /**
     * 同步从本地获取Team（先从缓存中查询，如果不存在再从SDK DB中查询）
     */
    public Team getTeamById(String teamId) {
        Team team = id2TeamMap.get(teamId);

        if (team == null) {
            team = NIMClient.getService(TeamService.class).queryTeamBlock(teamId);
            addOrUpdateTeam(team);
        }

        return team;
    }

    public String getTeamName(String teamId) {
        Team team = getTeamById(teamId);
        return team == null ? teamId : TextUtils.isEmpty(team.getName()) ? team.getId() : team
                .getName();
    }

    public List<Team> getAllTeams() {
        List<Team> teams = new ArrayList<>();
        for (Team t : id2TeamMap.values()) {
            if (t.isMyTeam()) {
                teams.add(t);
            }
        }
        return teams;
    }

    public List<Team> getAllAdvancedTeams() {
        return getAllTeamsByType(TeamTypeEnum.Advanced);
    }

    public List<Team> getAllNormalTeams() {
        return getAllTeamsByType(TeamTypeEnum.Normal);
    }

    private List<Team> getAllTeamsByType(TeamTypeEnum type) {
        List<Team> teams = new ArrayList<>();
        for (Team t : id2TeamMap.values()) {
            if (t.isMyTeam() && t.getType() == type) {
                teams.add(t);
            }
        }

        return teams;
    }

    public void addOrUpdateTeam(Team team) {
        if (team == null) {
            return;
        }

        id2TeamMap.put(team.getId(), team);
    }

    private void addOrUpdateTeam(List<Team> teamList) {
        if (teamList == null || teamList.isEmpty()) {
            return;
        }

        for (Team t : teamList) {
            if (t == null) {
                continue;
            }

            id2TeamMap.put(t.getId(), t);
        }
    }

    /**
     * *
     * ************************************** 群成员缓存(由App主动添加缓存) ****************************************
     */

    private Map<String, Map<String, TeamMember>> teamMemberCache = new ConcurrentHashMap<>();

    public void clearTeamMemberCache() {
        teamMemberCache.clear();
    }

    /**
     * （异步）查询群成员资料列表（先从SDK DB中查询，如果本地群成员资料已过期会去服务器获取最新的。）
     * 获取群组成员

     由于群组成员数据比较大，且除了进入群组成员列表界面外，其他地方均不需要群组成员列表的数据，
     因此 SDK 不会在登录时同步群组成员数据，而是按照按需获取的原则，
     当上层主动调用获取指定群的群组成员列表时，才判断是否需要同步。获取群组成员的示例代码如下：
     */
    public void fetchTeamMemberList(final String teamId, final SimpleCallback<List<TeamMember>> callback) {
        // 该操作有可能只是从本地数据库读取缓存数据，也有可能会从服务器同步新的数据，因此耗时可能会比较长。
        NIMClient.getService(TeamService.class).queryMemberList(teamId).setCallback(new RequestCallbackWrapper<List<TeamMember>>() {
            @Override
            public void onResult(int code, final List<TeamMember> members, Throwable exception) {
                boolean success = true;
                if (code == ResponseCode.RES_SUCCESS) {
                    replaceTeamMemberList(teamId, members);
                } else {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamMemberList failed, code=" + code);
                }

                if (exception != null) {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamMemberList throw exception, e=" + exception.getMessage());
                }

                if (callback != null) {
                    callback.onResult(success, members);
                }
            }
        });
    }

    /**
     * 在缓存中查询群成员列表
     */
    public List<TeamMember> getTeamMemberList(String teamId) {
        List<TeamMember> members = new ArrayList<>();
        Map<String, TeamMember> map = teamMemberCache.get(teamId);
        if (map != null && !map.values().isEmpty()) {
            for (TeamMember m : map.values()) {
                if (m.isInTeam()) {
                    members.add(m);
                }
            }
        }

        return members;
    }

    /**
     * （异步）查询群成员资料（先从SDK DB中查询，如果本地群成员资料已过期会去服务器获取最新的。）
     * 根据群ID和账号查询群成员资料：
     * 如果本地群成员资料已过期， SDK 会去服务器获取最新的。 queryTeamMember 还有同步版本 queryTeamMemberBlock 。
     *
     * 群成员资料 SDK 本地存储说明： 当自己退群、或者被移出群时，本地数据库会继续保留这个群成员资料，
     * 只是设置了无效标记，此时依然可以通过 queryTeamMember 查出来该群成员资料，只是 isInTeam 将返回 false 。
     */
    public void fetchTeamMember(final String teamId, final String account, final SimpleCallback<TeamMember> callback) {
        NIMClient.getService(TeamService.class).queryTeamMember(teamId, account).setCallback(new RequestCallbackWrapper<TeamMember>() {
            @Override
            public void onResult(int code, TeamMember member, Throwable exception) {
                boolean success = true;
                if (code == ResponseCode.RES_SUCCESS) {
                    addOrUpdateTeamMember(member);
                } else {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamMember failed, code=" + code);
                }

                if (exception != null) {
                    success = false;
                    LogUtil.e(UIKitLogTag.TEAM_CACHE, "fetchTeamMember throw exception, e=" + exception.getMessage());
                }

                if (callback != null) {
                    callback.onResult(success, member);
                }
            }
        });
    }

    /**
     * 查询群成员资料（先从缓存中查，如果没有则从SDK DB中查询）
     */
    public TeamMember getTeamMember(String teamId, String account) {
        Map<String, TeamMember> map = teamMemberCache.get(teamId);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            teamMemberCache.put(teamId, map);
        }

        if (!map.containsKey(account)) {
            TeamMember member = NIMClient.getService(TeamService.class).queryTeamMemberBlock(teamId, account);
            if (member != null) {
                map.put(account, member);
            }
        }

        return map.get(account);
    }

    /**
     * 获取显示名称。用户本人显示“我”
     *
     * @param tid
     * @param account
     * @return
     */
    public String getTeamMemberDisplayName(String tid, String account) {
        if (account.equals(NimUIKit.getAccount())) {
            return "我";
        }

        return getDisplayNameWithoutMe(tid, account);
    }

    /**
     * 获取显示名称。用户本人显示“你”
     *
     * @param tid
     * @param account
     * @return
     */
    public String getTeamMemberDisplayNameYou(String tid, String account) {
        if (account.equals(NimUIKit.getAccount())) {
            return "你";
        }

        return getDisplayNameWithoutMe(tid, account);
    }

    /**
     * 获取显示名称。用户本人也显示昵称
     * 备注>群昵称>昵称
     */
    public String getDisplayNameWithoutMe(String tid, String account) {

        String alias = NimUserInfoCache.getInstance().getAlias(account);
        if (!TextUtils.isEmpty(alias)) {
            return alias;
        }

        String memberNick = getTeamNick(tid, account);
        if (!TextUtils.isEmpty(memberNick)) {
            return memberNick;
        }

        return NimUserInfoCache.getInstance().getUserName(account);
    }

    public String getTeamNick(String tid, String account) {
        Team team = getTeamById(tid);
        if (team != null && team.getType() == TeamTypeEnum.Advanced) {
            TeamMember member = getTeamMember(tid, account);
            if (member != null && !TextUtils.isEmpty(member.getTeamNick())) {
                return member.getTeamNick();
            }
        }
        return null;
    }

    private void replaceTeamMemberList(String tid, List<TeamMember> members) {
        if (members == null || members.isEmpty() || TextUtils.isEmpty(tid)) {
            return;
        }

        Map<String, TeamMember> map = teamMemberCache.get(tid);
        if (map == null) {
            map = new ConcurrentHashMap<>();
            teamMemberCache.put(tid, map);
        } else {
            map.clear();
        }

        for (TeamMember m : members) {
            map.put(m.getAccount(), m);
        }
    }

    private void addOrUpdateTeamMember(TeamMember member) {
        if (member == null) {
            return;
        }

        Map<String, TeamMember> map = teamMemberCache.get(member.getTid());
        if (map == null) {
            map = new ConcurrentHashMap<>();
            teamMemberCache.put(member.getTid(), map);
        }

        map.put(member.getAccount(), member);
    }

    private void addOrUpdateTeamMembers(List<TeamMember> members) {
        for (TeamMember m : members) {
            addOrUpdateTeamMember(m);
        }
    }
}
