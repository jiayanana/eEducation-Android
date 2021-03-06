package io.agora.education.classroom;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.herewhite.sdk.domain.GlobalState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import io.agora.education.R;
import io.agora.education.api.EduCallback;
import io.agora.education.api.base.EduError;
import io.agora.education.api.message.AgoraActionMessage;
import io.agora.education.api.message.EduChatMsg;
import io.agora.education.api.message.EduMsg;
import io.agora.education.api.message.GroupMemberInfoMessage;
import io.agora.education.api.room.EduRoom;
import io.agora.education.api.room.data.EduRoomChangeType;
import io.agora.education.api.statistics.ConnectionState;
import io.agora.education.api.statistics.NetworkQuality;
import io.agora.education.api.stream.data.EduStreamEvent;
import io.agora.education.api.stream.data.EduStreamInfo;
import io.agora.education.api.stream.data.LocalStreamInitOptions;
import io.agora.education.api.stream.data.VideoSourceType;
import io.agora.education.api.user.EduStudent;
import io.agora.education.api.user.EduUser;
import io.agora.education.api.user.data.EduBaseUserInfo;
import io.agora.education.api.user.data.EduLocalUserInfo;
import io.agora.education.api.user.data.EduUserEvent;
import io.agora.education.api.user.data.EduUserInfo;
import io.agora.education.api.user.data.EduUserLeftType;
import io.agora.education.api.user.data.EduUserRole;
import io.agora.education.api.user.data.EduUserStateChangeType;
import io.agora.education.classroom.adapter.StageVideoAdapter;
import io.agora.education.classroom.bean.board.BoardBean;
import io.agora.education.classroom.bean.board.BoardInfo;
import io.agora.education.classroom.bean.channel.Room;
import io.agora.education.classroom.bean.group.GroupInfo;
import io.agora.education.classroom.bean.group.GroupMemberInfo;
import io.agora.education.classroom.bean.group.GroupStateInfo;
import io.agora.education.classroom.bean.group.RoomGroupInfo;
import io.agora.education.classroom.bean.group.StageStreamInfo;
import io.agora.education.classroom.fragment.StudentGroupListFragment;
import io.agora.education.classroom.fragment.StudentListFragment;
import io.agora.education.classroom.widget.RtcVideoView;
import io.agora.education.impl.cmd.bean.AgoraActionMsgRes;
import io.agora.raisehand.AgoraActionConfig;
import io.agora.raisehand.AgoraEduCoVideoListener;
import io.agora.raisehand.AgoraEduCoVideoView;
import kotlin.Unit;

import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.CMD;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.GROUOREWARD;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.GROUPMEDIA;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.STUDENTLISTCHANGED;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.STUDENTREWARD;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.SWITCHAUTOCOVIDEO;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.SWITCHCOVIDEO;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.SWITCHGROUP;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.SWITCHINTERACTIN;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.SWITCHINTERACTOUT;
import static io.agora.education.classroom.bean.group.IntermediateClassPropertyCauseType.UPDATEGROUP;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.GROUPS;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.GROUPSTATES;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.GROUPUUID;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.INTERACTOUTGROUPS;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.STUDENTS;
import static io.agora.education.classroom.bean.group.RoomGroupInfo.USERUUID;
import static io.agora.raisehand.AgoraActionConfig.PROCESSES;

public class MediumClassActivity extends BaseClassActivity_bak implements TabLayout.OnTabSelectedListener,
        AgoraEduCoVideoListener {
    private static final String TAG = MediumClassActivity.class.getSimpleName();

    @BindView(R.id.layout_video_teacher)
    FrameLayout layoutVideoTeacher;
    @BindView(R.id.stage_videos_one)
    RecyclerView stageVideosOne;
    @BindView(R.id.stage_videos_two)
    RecyclerView stageVideosTwo;
    @BindView(R.id.coVideoView)
    AgoraEduCoVideoView agoraEduCoVideoView;
    @BindView(R.id.layout_tab)
    TabLayout tabLayout;

    private RtcVideoView videoTeacher;
    private StudentListFragment studentListFragment;
    private StudentGroupListFragment studentGroupListFragment;
    /*?????????????????????????????????????????????????????????*/
    private EduRoom curGroupRoom;
    /*???????????????????????????*/
    private RoomGroupInfo roomGroupInfo = new RoomGroupInfo();
    private StageVideoAdapter stageVideoAdapterOne = new StageVideoAdapter(),
            getStageVideoAdapterTwo = new StageVideoAdapter();
    private List<StageStreamInfo> stageStreamInfosOne = new ArrayList<>();
    private List<StageStreamInfo> stageStreamInfosTwo = new ArrayList<>();

    private AgoraActionConfig agoraActionConfig;

    @Override
    protected int getClassType() {
        return Room.Type.INTERMEDIATE;
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_medium_class;
    }

    @Override
    protected void initData() {
        super.initData();
        joinRoom(getMainEduRoom(), roomEntry.getUserName(), roomEntry.getUserUuid(), true, false, true,
                new EduCallback<EduStudent>() {
                    @Override
                    public void onSuccess(@org.jetbrains.annotations.Nullable EduStudent res) {
                        runOnUiThread(() -> {
                            showFragmentWithJoinSuccess();
                            /*disable operation in intermediateClass`s mainClass*/
                            whiteboardFragment.disableDeviceInputs(true);
                            whiteboardFragment.setWritable(false);
                        });
                        initTitleTimeState();
                        String processUuid = parseAgoraActionConfig(getMainEduRoom());
                        /*???????????????????????????*/
                        agoraEduCoVideoView.init(getMainEduRoom(), processUuid);
                        initParseBoardInfo(getMainEduRoom());
                        /*???????????????roomProperties??????????????????????????????*/
                        syncRoomGroupProperty(getMainEduRoom().getRoomProperties());
                        /*???????????????????????????*/
                        updateStudentList();
                        notifyUserList();
                        notifyStageVideoList();
                    }

                    @Override
                    public void onFailure(@NotNull EduError error) {
                        joinFailed(error.getType(), error.getMsg());
                    }
                });
    }

    @Override
    protected void initView() {
        super.initView();

        tabLayout.addOnTabSelectedListener(this);

        if (videoTeacher == null) {
            videoTeacher = new RtcVideoView(this);
            videoTeacher.init(R.layout.layout_video_large_class, false);
        }
        removeFromParent(videoTeacher);
        layoutVideoTeacher.addView(videoTeacher, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        studentListFragment = new StudentListFragment(roomEntry.getUserUuid());
        getSupportFragmentManager().beginTransaction()
                .add(R.id.layout_chat_room, studentListFragment)
                .show(studentListFragment)
                .hide(studentListFragment)
                .commitNowAllowingStateLoss();

        studentGroupListFragment = new StudentGroupListFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.layout_chat_room, studentGroupListFragment)
                .show(studentGroupListFragment)
                .hide(studentGroupListFragment)
                .commitNowAllowingStateLoss();

        stageVideosOne.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        stageVideosOne.setAdapter(stageVideoAdapterOne);
        stageVideosTwo.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        stageVideosTwo.setAdapter(getStageVideoAdapterTwo);
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (tab.getPosition() == 0) {
            Fragment fragment = roomGroupInfo.enableGroup() ? studentGroupListFragment : studentListFragment;
            transaction.show(fragment).hide(chatRoomFragment);
        } else {
            transaction.show(chatRoomFragment).hide(studentListFragment).hide(studentGroupListFragment);
        }
        transaction.commitNow();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
    }

    @Override
    protected void showFragmentWithJoinSuccess() {
        super.showFragmentWithJoinSuccess();
        getSupportFragmentManager().beginTransaction()
                .hide(chatRoomFragment)
                .commitNowAllowingStateLoss();
    }

    private void switchUserFragment(boolean showGroup) {
        runOnUiThread(() -> {
            if ((showGroup && studentGroupListFragment.isVisible()) ||
                    (!showGroup && studentListFragment.isVisible())) {
                return;
            }
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            if (showGroup) {
                transaction = transaction.show(studentGroupListFragment)
                        .hide(studentListFragment);
            } else {
                transaction = transaction.show(studentListFragment)
                        .hide(studentGroupListFragment);
            }
            transaction.commitNowAllowingStateLoss();
        });
    }

    private void showTeacherStream(EduStreamInfo stream, FrameLayout viewGroup) {
        switch (stream.getVideoSourceType()) {
            case CAMERA:
                videoTeacher.setName(stream.getPublisher().getUserName());
                renderStream(getMainEduRoom(), stream, viewGroup);
                videoTeacher.muteVideo(!stream.getHasVideo());
                videoTeacher.muteAudio(!stream.getHasAudio());
                break;
            case SCREEN:
                runOnUiThread(() -> {
                    if (viewGroup == null) {
                        layout_whiteboard.setVisibility(View.VISIBLE);
                        layout_share_video.setVisibility(View.GONE);
                    } else {
                        layout_whiteboard.setVisibility(View.GONE);
                        layout_share_video.setVisibility(View.VISIBLE);
                    }
                    layout_share_video.removeAllViews();
                    renderStream(getMainEduRoom(), stream, layout_share_video);
                });
                break;
            default:
                break;
        }
    }

    private void initBoard(BoardBean boardBean) {
        BoardInfo info = boardBean.getInfo();
        getLocalUserInfo(new EduCallback<EduUserInfo>() {
            @Override
            public void onSuccess(@Nullable EduUserInfo userInfo) {
                runOnUiThread(() -> whiteboardFragment.initBoardWithRoomToken(
                        info.getBoardId(), info.getBoardToken(), userInfo.getUserUuid()));
            }

            @Override
            public void onFailure(@NotNull EduError error) {
            }
        });
    }

    private void syncRoomGroupProperty(Map<String, Object> roomProperties) {
        String groupStatesJson = getProperty(roomProperties, GROUPSTATES);
        GroupStateInfo groupStateInfo = new Gson().fromJson(groupStatesJson, GroupStateInfo.class);
        roomGroupInfo.setGroupStates(groupStateInfo);
        String interactOutGroupsJson = getProperty(roomProperties, INTERACTOUTGROUPS);
        Map<String, String> interactOutGroups = new Gson().fromJson(interactOutGroupsJson,
                new TypeToken<Map<String, String>>() {
                }.getType());
        roomGroupInfo.updateInteractOutGroups(interactOutGroups);
        String groupsJson = getProperty(roomProperties, GROUPS);
        Map<String, GroupInfo> groups = new Gson().fromJson(groupsJson,
                new TypeToken<Map<String, GroupInfo>>() {
                }.getType());
        roomGroupInfo.updateGroups(groups);
        /*???????????????????????????*/
        Map<String, GroupMemberInfo> allStudent = new Gson()
                .fromJson(new Gson().toJson(roomProperties.get(STUDENTS)),
                        new TypeToken<Map<String, GroupMemberInfo>>() {
                        }.getType());
        getCurFullUser(new EduCallback<List<EduUserInfo>>() {
            @Override
            public void onSuccess(@Nullable List<EduUserInfo> onLineUsers) {
                getCurFullStream(new EduCallback<List<EduStreamInfo>>() {
                    @Override
                    public void onSuccess(@Nullable List<EduStreamInfo> streams) {
                        if (onLineUsers != null && streams != null) {
                            roomGroupInfo.updateAllStudent(allStudent, onLineUsers, streams);
                        }
                    }

                    @Override
                    public void onFailure(@NotNull EduError error) {
                    }
                });
            }

            @Override
            public void onFailure(@NotNull EduError error) {
            }
        });
    }

    private void getCurAllStudentUser(EduCallback<List<EduUserInfo>> callback) {
        getCurFullUser(new EduCallback<List<EduUserInfo>>() {
            @Override
            public void onSuccess(@Nullable List<EduUserInfo> res) {
                if (res != null) {
                    List<EduUserInfo> students = new ArrayList<>();
                    Iterator<EduUserInfo> iterator = res.iterator();
                    while (iterator.hasNext()) {
                        EduUserInfo element = iterator.next();
                        if (element.getRole().equals(EduUserRole.STUDENT)) {
                            students.add(element);
                        }
                    }
                    callback.onSuccess(students);
                } else {
                    callback.onFailure(EduError.Companion.customMsgError("current room no user!"));
                }
            }

            @Override
            public void onFailure(@NotNull EduError error) {
                callback.onFailure(error);
            }
        });
    }

    /**
     * ????????????????????????????????????
     * ?????????????????????????????????????????????????????????????????????
     */
    private void updateStudentList() {
        getLocalUser(new EduCallback<EduUser>() {
            @Override
            public void onSuccess(@Nullable EduUser user) {
                if (user != null) {
                    EduUserInfo userInfo = user.getUserInfo();
                    GroupMemberInfoMessage memberInfo = new GroupMemberInfoMessage(userInfo.getUserUuid(),
                            userInfo.getUserName(), "", 0);
                    Map<String, GroupMemberInfoMessage> memberInfoMap = new HashMap<>();
                    memberInfoMap.put(STUDENTS.concat(".").concat(memberInfo.getUuid()),
                            memberInfo);
                    Map<String, String> cause = new HashMap<>();
                    cause.put(CMD, String.valueOf(STUDENTLISTCHANGED));
                    user.setRoomProperties(memberInfoMap, cause, new EduCallback<Unit>() {
                        @Override
                        public void onSuccess(@Nullable Unit res) {
                        }

                        @Override
                        public void onFailure(@NotNull EduError error) {
                            Log.e(TAG, "????????????????????????????????????????????????->" + error.getMsg());
                        }
                    });
                }
            }

            @Override
            public void onFailure(@NotNull EduError error) {

            }
        });
    }

    /**
     * ??????????????????
     * ???????????????????????????????????????
     */
    private void notifyUserList() {
        if (roomGroupInfo.enableGroup()) {
            /*????????????????????????????????????????????????*/
            switchUserFragment(true);
            //TODO ??????????????????
//            /**????????????*/
//            List<GroupInfo> groupInfos = new ArrayList<>();
//            List<List<String>> memberUuidsList = new ArrayList<>();
//            List<String> memberIds = null;
//            for (int i = 0; i < 36; i++) {
//                if (i % 6 == 0) {
//                    memberIds = new ArrayList<>();
//                }
//                if (i == 0) {
//                    memberIds.add("1232");
//                }
//                memberIds.add("999" + i);
//                if (memberIds.size() == 6) {
//                    memberUuidsList.add(memberIds);
//                }
//            }
//            for (int i = 0; i < 6; i++) {
//                GroupInfo groupInfo = new GroupInfo("123-" + i, "???" + i, memberUuidsList.get(i), "0",
//                        new HashMap<>());
//                groupInfos.add(groupInfo);
//            }
//            /**????????????*/
//            roomGroupInfo.setGroups(groupInfos);
//            getCurAllStudentUser(new EduCallback<List<EduUserInfo>>() {
//                @Override
//                public void onSuccess(@Nullable List<EduUserInfo> onlineStudentUsers) {
//                    List<GroupMemberInfo> allStudent = new ArrayList<>();
//                    for (int i = 0; i < 35; i++) {
//                        GroupMemberInfo memberInfo = new GroupMemberInfo("999" + i, "???" + i, "", 0);
//                        for (EduUserInfo userInfo : onlineStudentUsers) {
//                            if (userInfo.getUserUuid().equals(memberInfo.getUuid())) {
//                                memberInfo.setOnline(true);
//                            }
//                        }
//                        allStudent.add(memberInfo);
//                    }
//                    GroupMemberInfo memberInfo = new GroupMemberInfo("1232", "123", "", 0);
//                    for (EduUserInfo userInfo : onlineStudentUsers) {
//                        if (userInfo.getUserUuid().equals(memberInfo.getUuid())) {
//                            memberInfo.setOnline(true);
//                        }
//                    }
//                    allStudent.add(0, memberInfo);
//                    /**????????????*/
//                    roomGroupInfo.setAllStudent(allStudent);
//                    studentGroupListFragment.updateGroupList(groupInfos, allStudent);
//                }
//
//                @Override
//                public void onFailure(@NotNull EduError error) {
//                }
//            });
            List<GroupInfo> groupInfos = roomGroupInfo.getGroups();
            List<GroupMemberInfo> allStudent = roomGroupInfo.getAllStudent();
            if (groupInfos != null && groupInfos.size() > 0 && allStudent != null
                    && allStudent.size() > 0) {
                studentGroupListFragment.updateGroupList(groupInfos, roomGroupInfo.getAllStudent());
            }
        } else {
            /*??????????????????????????????????????????*/
            switchUserFragment(false);
            studentListFragment.updateStudentList(roomGroupInfo.getAllStudent());
        }
    }

    private void notifyStageVideoList() {
        if (roomGroupInfo.enablePK()) {
            List<GroupInfo> groupInfos = roomGroupInfo.getGroups();
            List<String> stageGroupIds = roomGroupInfo.getInteractOutGroups();
            List<GroupInfo> stageGroups = new ArrayList<>(2);
            Iterator<GroupInfo> iterator = groupInfos.iterator();
            while (iterator.hasNext()) {
                GroupInfo element = iterator.next();
                if (stageGroupIds.contains(element.getGroupUuid())) {
                    stageGroups.add(element);
                }
            }
            /*??????pk,??????????????????????????????*/
            List<String> stageMemberIdsOne = stageGroups.get(0).getMembers();
            final List<String> stageMemberIdsTwo = stageGroups.size() > 1 ?
                    stageGroups.get(1).getMembers() : new ArrayList<>();
//            /**????????????*/
//            List<EduStreamInfo> curFullStreams = new ArrayList<>();
//            if (roomGroupInfo.getAllStudent() != null) {
//                for (GroupMemberInfo memberInfo : roomGroupInfo.getAllStudent()) {
//                    EduBaseUserInfo baseUserInfo = new EduBaseUserInfo(memberInfo.getUuid(),
//                            memberInfo.getUserName(), EduUserRole.STUDENT);
//                    EduStreamInfo streamInfo = new EduStreamInfo(memberInfo.getUuid().concat("000"),
//                            "stream-".concat(memberInfo.getUserName()), VideoSourceType.CAMERA,
//                            true, true, baseUserInfo);
//                    curFullStreams.add(streamInfo);
//                }
//            }
//            stageStreamInfosOne.clear();
//            stageStreamInfosTwo.clear();
//            if (curFullStreams != null && curFullStreams.size() > 0) {
//                for (EduStreamInfo stream : curFullStreams) {
//                    String userUuid = stream.getPublisher().getUserUuid();
//                    if (stageMemberIdsOne.contains(userUuid)) {
//                        StageStreamInfo stageStream = new StageStreamInfo(stream,
//                                roomGroupInfo.getStudentReward(userUuid));
//                        stageStreamInfosOne.add(stageStream);
//                    } else if (stageMemberIdsTwo.contains(userUuid)) {
//                        StageStreamInfo stageStream = new StageStreamInfo(stream,
//                                roomGroupInfo.getStudentReward(userUuid));
//                        stageStreamInfosTwo.add(stageStream);
//                    }
//                }
//                notifyStageVideoListOne();
//                notifyStageVideoListTwo();
//            }
            getCurFullStream(new EduCallback<List<EduStreamInfo>>() {
                @Override
                public void onSuccess(@Nullable List<EduStreamInfo> curFullStreams) {
                    if (curFullStreams != null && curFullStreams.size() > 0) {
                        for (EduStreamInfo stream : curFullStreams) {
                            String userUuid = stream.getPublisher().getUserUuid();
                            if (stageMemberIdsOne.contains(userUuid)) {
                                StageStreamInfo stageStream = new StageStreamInfo(stream,
                                        roomGroupInfo.getStudentReward(userUuid));
                                stageStreamInfosOne.add(stageStream);
                            } else if (stageMemberIdsTwo.contains(userUuid)) {
                                StageStreamInfo stageStream = new StageStreamInfo(stream,
                                        roomGroupInfo.getStudentReward(userUuid));
                                stageStreamInfosTwo.add(stageStream);
                            }
                        }
                        notifyStageVideoListOne();
                        notifyStageVideoListTwo();
                    }
                }

                @Override
                public void onFailure(@NotNull EduError error) {

                }
            });
        } else {
            List<EduStreamInfo> curStageStreams = new ArrayList<>();
            if (roomGroupInfo.getAllStudent() != null) {
                for (GroupMemberInfo element : roomGroupInfo.getAllStudent()) {
                    if (element.getOnStage()) {
                        EduBaseUserInfo baseUserInfo = new EduBaseUserInfo(element.getUuid(),
                                element.getUserName(), EduUserRole.STUDENT);
                        /*??????streamUuid??????????????????????????????????????????????????????*/
                        if (element.getStreamUuid() == null) {
                            getCurFullStream(new EduCallback<List<EduStreamInfo>>() {
                                @Override
                                public void onSuccess(@Nullable List<EduStreamInfo> streams) {
                                    if (streams != null) {
                                        for (EduStreamInfo streamInfo : streams) {
                                            if (streamInfo.getPublisher().getUserUuid()
                                                    .equals(element.getUuid())) {
                                                element.setStreamUuid(streamInfo.getStreamUuid());
                                                element.setStreamName(streamInfo.getStreamName());
                                            }
                                        }
                                    }
                                }

                                @Override
                                public void onFailure(@NotNull EduError error) {
                                }
                            });
                        }
                        EduStreamInfo streamInfo = new EduStreamInfo(element.getStreamUuid(),
                                element.getStreamName(),
                                VideoSourceType.CAMERA, element.getEnableVideo(), element.getEnableAudio(), baseUserInfo);
                        curStageStreams.add(streamInfo);
                    }
                }
            }
            stageStreamInfosOne.clear();
            stageStreamInfosTwo.clear();
            if (curStageStreams != null && curStageStreams.size() > 0) {
                for (EduStreamInfo stream : curStageStreams) {
                    String userUuid = stream.getPublisher().getUserUuid();
                    StageStreamInfo stageStream = new StageStreamInfo(stream,
                            roomGroupInfo.getStudentReward(userUuid));
                    stageStreamInfosOne.add(stageStream);
                }
            }
            notifyStageVideoListOne();
        }
    }

    private void notifyStageVideoListOne() {
        getLocalUserInfo(new EduCallback<EduUserInfo>() {
            @Override
            public void onSuccess(@Nullable EduUserInfo res) {
                if (res != null) {
                    runOnUiThread(() -> {
                        stageVideosOne.setVisibility(stageStreamInfosOne.size() > 0 ? View.VISIBLE : View.GONE);
                        stageVideoAdapterOne.setNewList(stageStreamInfosOne, res.getUserUuid());
                    });
                }
            }

            @Override
            public void onFailure(@NotNull EduError error) {
            }
        });
    }

    private void notifyStageVideoListTwo() {
        getLocalUserInfo(new EduCallback<EduUserInfo>() {
            @Override
            public void onSuccess(@Nullable EduUserInfo res) {
                if (res != null) {
                    runOnUiThread(() -> {
                        stageVideosTwo.setVisibility(stageStreamInfosTwo.size() > 0 ? View.VISIBLE : View.GONE);
                        getStageVideoAdapterTwo.setNewList(stageStreamInfosTwo, res.getUserUuid());
                    });
                }
            }

            @Override
            public void onFailure(@NotNull EduError error) {
            }
        });
    }


    /**
     * ????????????
     */
    private void memberOnStage(List<EduStreamEvent> streamEvents) {
        for (GroupMemberInfo memberInfo : roomGroupInfo.getAllStudent()) {
            for (EduStreamEvent streamEvent : streamEvents) {
                EduStreamInfo streamInfo = streamEvent.getModifiedStream();
                if (memberInfo.getUuid().equals(streamInfo.getPublisher().getUserUuid())) {
                    memberInfo.onStage();
                }
            }
        }
    }

    /**
     * ????????????
     */
    private void memberOffStage(List<EduStreamEvent> streamEvents) {
        for (GroupMemberInfo memberInfo : roomGroupInfo.getAllStudent()) {
            for (EduStreamEvent streamEvent : streamEvents) {
                EduStreamInfo streamInfo = streamEvent.getModifiedStream();
                if (memberInfo.getUuid().equals(streamInfo.getPublisher().getUserUuid())) {
                    memberInfo.offStage();
                }
            }
        }
    }

    private String parseAgoraActionConfig(EduRoom eduRoom) {
        Map<String, Object> map = null;
        for (Map.Entry<String, Object> entry : eduRoom.getRoomProperties().entrySet()) {
            if (entry.getKey().equals(PROCESSES)) {
                map = (Map<String, Object>) entry.getValue();
                break;
            }
        }
        if (map != null) {
            String processUuid = null;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                processUuid = entry.getKey();
                break;
            }
            if (TextUtils.isEmpty(processUuid)) {
                return null;
            }
            String json = new Gson().toJson(map.get(processUuid));
            agoraActionConfig = new Gson().fromJson(json, AgoraActionConfig.class);
            agoraActionConfig.processUuid = processUuid;
            return agoraActionConfig.processUuid;
        }
        return null;
    }

    @Override
    public void onRemoteUsersInitialized(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        if (classRoom.equals(getMainEduRoom())) {
            initParseBoardInfo(getMainEduRoom());
            /*???????????????roomProperties??????????????????????????????*/
            Map<String, Object> roomProperties = getMainEduRoom().getRoomProperties();
            syncRoomGroupProperty(roomProperties);
            /*???????????????????????????*/
            updateStudentList();
            notifyUserList();
            notifyStageVideoList();
        }
    }

    @Override
    public void onRemoteUsersJoined(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        super.onRemoteUsersJoined(users, classRoom);
        notifyUserList();
    }

    @Override
    public void onRemoteUserLeft(@NotNull EduUserEvent userEvent, @NotNull EduRoom classRoom) {
        super.onRemoteUserLeft(userEvent, classRoom);
        notifyUserList();
    }

    @Override
    public void onRemoteUserUpdated(@NotNull EduUserEvent userEvent, @NotNull EduUserStateChangeType type,
                                    @NotNull EduRoom classRoom) {
        super.onRemoteUserUpdated(userEvent, type, classRoom);
        notifyUserList();
    }

    @Override
    public void onRoomMessageReceived(@NotNull EduMsg message, @NotNull EduRoom classRoom) {
        super.onRoomMessageReceived(message, classRoom);
    }

    @Override
    public void onRoomChatMessageReceived(@NotNull EduChatMsg eduChatMsg, @NotNull EduRoom classRoom) {
        super.onRoomChatMessageReceived(eduChatMsg, classRoom);
    }

    @Override
    public void onRemoteStreamsInitialized(@NotNull List<? extends EduStreamInfo> streams, @NotNull EduRoom classRoom) {
        if (classRoom.equals(getMainEduRoom())) {
            /*??????????????????*/
            getMainEduRoom().getFullStreamList(new EduCallback<List<EduStreamInfo>>() {
                @Override
                public void onSuccess(@Nullable List<EduStreamInfo> res) {
                    if (res != null) {
                        for (EduStreamInfo streamInfo : res) {
                            EduBaseUserInfo publisher = streamInfo.getPublisher();
                            if (publisher.getRole().equals(EduUserRole.TEACHER)) {
                                showTeacherStream(streamInfo, videoTeacher.getVideoLayout());
                            }
                        }
                    }
                }

                @Override
                public void onFailure(@NotNull EduError error) {
                }
            });
        }
    }

    @Override
    public void onRemoteStreamsAdded(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        if (classRoom.equals(getMainEduRoom())) {
            super.onRemoteStreamsAdded(streamEvents, classRoom);
            memberOnStage(streamEvents);
            boolean needUpdateStudentList = false;
            for (EduStreamEvent streamEvent : streamEvents) {
                EduStreamInfo streamInfo = streamEvent.getModifiedStream();
                EduBaseUserInfo userInfo = streamInfo.getPublisher();
                if (userInfo.getRole().equals(EduUserRole.TEACHER)) {
                    showTeacherStream(streamInfo, videoTeacher.getVideoLayout());
                } else {
                    needUpdateStudentList = updateMemberInfoList(streamInfo, userInfo);
                }
            }
            if (needUpdateStudentList) {
                studentListFragment.updateStudentList(roomGroupInfo.getAllStudent());
            }
            notifyStageVideoList();
        }
    }

    private boolean updateMemberInfoList(EduStreamInfo streamInfo, EduBaseUserInfo userInfo) {
        if (roomGroupInfo.getAllStudent() != null) {
            for (GroupMemberInfo memberInfo : roomGroupInfo.getAllStudent()) {
                if (memberInfo.getUuid().equals(userInfo.getUserUuid())) {
                    memberInfo.setEnableAudio(streamInfo.getHasAudio());
                    memberInfo.setEnableVideo(streamInfo.getHasVideo());
                    memberInfo.setStreamUuid(streamInfo.getStreamUuid());
                    memberInfo.setStreamName(streamInfo.getStreamName());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onRemoteStreamUpdated(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        if (classRoom.equals(getMainEduRoom())) {
            super.onRemoteStreamUpdated(streamEvents, classRoom);
            memberOnStage(streamEvents);
            boolean needUpdateStudentList = false;
            for (EduStreamEvent streamEvent : streamEvents) {
                EduStreamInfo streamInfo = streamEvent.getModifiedStream();
                EduBaseUserInfo userInfo = streamInfo.getPublisher();
                if (userInfo.getRole().equals(EduUserRole.TEACHER)) {
                    showTeacherStream(streamInfo, videoTeacher.getVideoLayout());
                } else {
                    needUpdateStudentList = updateMemberInfoList(streamInfo, userInfo);
                }
            }
            if (needUpdateStudentList) {
                studentListFragment.updateStudentList(roomGroupInfo.getAllStudent());
            }
            notifyStageVideoList();
        }
    }

    @Override
    public void onRemoteStreamsRemoved(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        if (classRoom.equals(getMainEduRoom())) {
            super.onRemoteStreamsRemoved(streamEvents, classRoom);
            memberOffStage(streamEvents);
            notifyStageVideoList();
            boolean needUpdateStudentList = false;
            for (EduStreamEvent streamEvent : streamEvents) {
                EduStreamInfo streamInfo = streamEvent.getModifiedStream();
                EduBaseUserInfo userInfo = streamInfo.getPublisher();
                if (userInfo.getRole().equals(EduUserRole.TEACHER)) {
                    showTeacherStream(streamInfo, null);
                } else {
                    needUpdateStudentList = updateMemberInfoList(streamInfo, userInfo);
                }
            }
            if (needUpdateStudentList) {
                studentListFragment.updateStudentList(roomGroupInfo.getAllStudent());
            }
        }
    }

    @Override
    public void onRoomStatusChanged(@NotNull EduRoomChangeType event, @NotNull EduUserInfo operatorUser, @NotNull EduRoom classRoom) {
        super.onRoomStatusChanged(event, operatorUser, classRoom);
    }

    @Override
    public void onRoomPropertiesChanged(@NotNull EduRoom classRoom, @Nullable Map<String, Object> cause) {
        if (classRoom.equals(getMainEduRoom())) {
            Log.e(TAG, "??????????????????roomProperty???????????????");
            initParseBoardInfo(getMainEduRoom());
            Map<String, Object> roomProperties = classRoom.getRoomProperties();
            parseRecordMsg(roomProperties);
            /*??????????????????*/
            syncRoomGroupProperty(roomProperties);
            if (cause != null && !cause.isEmpty()) {
                int causeType = (int) Float.parseFloat(cause.get(CMD).toString());
                switch (causeType) {
                    case SWITCHGROUP:
                        /*???????????????*/
                        notifyUserList();
                        break;
                    case UPDATEGROUP:
                        /*????????????????????????????????????*/
                        notifyUserList();
                        break;
                    case SWITCHINTERACTIN:
                        break;
                    case SWITCHINTERACTOUT:
                        /*??????PK?????????????????????*/
                        notifyUserList();
                        notifyStageVideoList();
                        break;
                    case GROUPMEDIA:
                        /*??????????????????*/
                        break;
                    case GROUOREWARD:
                        /*?????????????????????????????????*/
                        String groupUuid = String.valueOf(cause.get(GROUPUUID));
                        roomGroupInfo.updateRewardByGroup(groupUuid);
                        notifyUserList();
                        notifyStageVideoList();
                        break;
                    case SWITCHCOVIDEO:
                    case SWITCHAUTOCOVIDEO:
                        /*??????????????????????????????coVideoView*/
                        String processUuid = parseAgoraActionConfig(getMainEduRoom());
                        agoraEduCoVideoView.updateProcessUuid(processUuid);
                        agoraEduCoVideoView.syncCoVideoSwitchState(roomProperties);
                        break;
                    case STUDENTLISTCHANGED:
                        /*?????????????????????????????????????????????*/
                        notifyUserList();
                        break;
                    case STUDENTREWARD:
                        /*???????????????????????????????????????*/
                        String userUuid = String.valueOf(cause.get(USERUUID));
                        roomGroupInfo.updateRewardByUser(userUuid);
                        notifyUserList();
                        notifyStageVideoList();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void onNetworkQualityChanged(@NotNull NetworkQuality quality, @NotNull EduUserInfo user, @NotNull EduRoom classRoom) {
        super.onNetworkQualityChanged(quality, user, classRoom);
    }

    @Override
    public void onConnectionStateChanged(@NotNull ConnectionState state, @NotNull EduRoom classRoom) {
        super.onConnectionStateChanged(state, classRoom);
    }

    @Override
    public void onLocalUserUpdated(@NotNull EduUserEvent userEvent, @NotNull EduUserStateChangeType type) {
    }

    private void updateLocalStreamInfo(EduStreamEvent streamEvent) {
        EduStreamInfo streamInfo = streamEvent.getModifiedStream();
        updateMemberInfoList(streamInfo, streamInfo.getPublisher());
        studentListFragment.updateStudentList(roomGroupInfo.getAllStudent());
    }

    @Override
    public void onLocalStreamAdded(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamAdded(streamEvent);
        memberOnStage(Collections.singletonList(streamEvent));
        updateLocalStreamInfo(streamEvent);
        notifyStageVideoList();
    }

    @Override
    public void onLocalStreamUpdated(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamUpdated(streamEvent);
        memberOnStage(Collections.singletonList(streamEvent));
        updateLocalStreamInfo(streamEvent);
        notifyStageVideoList();
    }

    @Override
    public void onLocalStreamRemoved(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamRemoved(streamEvent);
        /**????????????????????????????????????
         * 1:???????????????CoVideoView 2:?????????????????????*/
        agoraEduCoVideoView.onLinkMediaChanged(false);
        memberOffStage(Collections.singletonList(streamEvent));
        notifyStageVideoList();
        updateLocalStreamInfo(streamEvent);
    }

    @Override
    public void onUserActionMessageReceived(@NotNull AgoraActionMessage actionMessage) {
        super.onUserActionMessageReceived(actionMessage);
//        /*????????????????????????????????????????????????coVideoView???*/
//        agoraEduCoVideoView.syncCoVideoState(actionMessage);
    }

    @Override
    public void onUserMessageReceived(@NotNull EduMsg message) {
        super.onUserMessageReceived(message);
        String msg = message.getMessage();
        try {
            AgoraActionMsgRes msgRes = new Gson().fromJson(msg, AgoraActionMsgRes.class);
            /*????????????????????????????????????????????????coVideoView???*/
            agoraEduCoVideoView.syncCoVideoState(msgRes);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGlobalStateChanged(GlobalState state) {
        super.onGlobalStateChanged(state);
    }

    @Override
    public void onLocalUserLeft(@NotNull EduUserEvent userEvent, @NotNull EduUserLeftType leftType) {
        if (leftType == EduUserLeftType.KickOff) {
            showRemovedDialog();
        }
    }

    /**
     * ???????????????????????????
     */
    @Override
    public void onApplyCoVideoComplete() {

    }

    @Override
    public void onApplyCoVideoFailed(@NotNull EduError error) {

    }

    @Override
    public void onCancelCoVideoSuccess() {

    }

    @Override
    public void onCancelCoVideoFailed(@NotNull EduError error) {

    }

    @Override
    public void onCoVideoAborted() {

    }

    @Override
    public void onCoVideoAccepted() {
        /*???????????????????????????????????????????????????????????????*/
        if (agoraEduCoVideoView.isAutoCoVideo()) {
            getLocalUser(new EduCallback<EduUser>() {
                @Override
                public void onSuccess(@Nullable EduUser localUser) {
                    if (localUser != null) {
                        EduLocalUserInfo userInfo = localUser.getUserInfo();
                        LocalStreamInitOptions options = new LocalStreamInitOptions(userInfo.streamUuid,
                                false, true);
                        localUser.initOrUpdateLocalStream(options, new EduCallback<EduStreamInfo>() {
                            @Override
                            public void onSuccess(@Nullable EduStreamInfo streamInfo) {
                                if (streamInfo != null) {
                                    localUser.publishStream(streamInfo, new EduCallback<Boolean>() {
                                        @Override
                                        public void onSuccess(@Nullable Boolean res) {
                                        }

                                        @Override
                                        public void onFailure(@NotNull EduError error) {
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onFailure(@NotNull EduError error) {
                            }
                        });
                    }
                }

                @Override
                public void onFailure(@NotNull EduError error) {

                }
            });
        }
    }

    @Override
    public void onCoVideoRejected() {

    }
}
