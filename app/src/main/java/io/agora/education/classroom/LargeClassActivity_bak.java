package io.agora.education.classroom;

import android.content.res.Configuration;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.tabs.TabLayout;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.agora.base.ToastManager;
import io.agora.education.R;
import io.agora.education.api.EduCallback;
import io.agora.education.api.base.EduError;
import io.agora.education.api.message.EduChatMsg;
import io.agora.education.api.message.EduMsg;
import io.agora.education.api.room.EduRoom;
import io.agora.education.api.room.data.EduRoomChangeType;
import io.agora.education.api.room.data.EduRoomInfo;
import io.agora.education.api.statistics.ConnectionState;
import io.agora.education.api.statistics.NetworkQuality;
import io.agora.education.api.stream.data.EduStreamEvent;
import io.agora.education.api.stream.data.EduStreamInfo;
import io.agora.education.api.stream.data.LocalStreamInitOptions;
import io.agora.education.api.stream.data.VideoSourceType;
import io.agora.education.api.user.EduStudent;
import io.agora.education.api.user.EduUser;
import io.agora.education.api.user.data.EduBaseUserInfo;
import io.agora.education.api.user.data.EduUserEvent;
import io.agora.education.api.user.data.EduUserInfo;
import io.agora.education.api.user.data.EduUserLeftType;
import io.agora.education.api.user.data.EduUserRole;
import io.agora.education.api.user.data.EduUserStateChangeType;
import io.agora.education.classroom.bean.channel.Room;
import io.agora.education.classroom.bean.msg.PeerMsg;
import io.agora.education.classroom.widget.RtcVideoView;

import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Status.Applying;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Status.CoVideoing;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Status.DisCoVideo;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Type.ABORT;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Type.ACCEPT;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Type.CANCEL;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Type.EXIT;
import static io.agora.education.classroom.bean.msg.PeerMsg.CoVideoMsg.Type.REJECT;

public class LargeClassActivity_bak extends BaseClassActivity implements TabLayout.OnTabSelectedListener {
    private static final String TAG = "LargeClassActivity";

    @BindView(R.id.layout_video_teacher)
    protected FrameLayout layout_video_teacher;
    @BindView(R.id.layout_video_student)
    protected FrameLayout layout_video_student;
    @Nullable
    @BindView(R.id.layout_tab)
    protected TabLayout layout_tab;
    @BindView(R.id.layout_chat_room)
    protected FrameLayout layout_chat_room;
    @Nullable
    @BindView(R.id.layout_materials)
    protected FrameLayout layout_materials;
    @BindView(R.id.layout_hand_up)
    protected CardView layout_hand_up;

    private RtcVideoView video_teacher;
    private RtcVideoView video_student;
    private AppCompatTextView textView_unRead;
    private ConstraintLayout layout_unRead;

    /**
     * ????????????????????????????????????
     */
    private int localCoVideoStatus = DisCoVideo;
    /**
     * ??????????????????
     */
    private EduBaseUserInfo curLinkedUser;

    private int unReadCount = 0;

    @Override
    protected int getLayoutResId() {
        Configuration configuration = getResources().getConfiguration();
        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            return R.layout.activity_large_class_portrait;
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            return R.layout.activity_large_class_landscape;
        }
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
                            // disable operation in large class
                            whiteboardFragment.disableDeviceInputs(true);
                            whiteboardFragment.setWritable(false);
                        });
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
        /*??????????????????????????????*/
        title_view.hideTime();

        if (video_teacher == null) {
            video_teacher = new RtcVideoView(this);
            video_teacher.init(R.layout.layout_video_large_class, false);
        }
        removeFromParent(video_teacher);
        layout_video_teacher.addView(video_teacher, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        video_teacher.setVisibility(View.VISIBLE);

        if (video_student == null) {
            video_student = new RtcVideoView(this);
            video_student.init(R.layout.layout_video_small_class, true);
            video_student.setOnClickAudioListener(v -> {
                if (localCoVideoStatus == CoVideoing) {
                    muteLocalAudio(!video_student.isAudioMuted());
                }
            });
            video_student.setOnClickVideoListener(v -> {
                if (localCoVideoStatus == CoVideoing) {
                    muteLocalVideo(!video_student.isVideoMuted());
                }
            });
            video_student.setViewVisibility(View.GONE);
        }
        removeFromParent(video_student);
        layout_video_student.addView(video_student, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (layout_tab != null) {
            /*????????????????????????*/
            layout_tab.addOnTabSelectedListener(this);
            layout_tab.getTabAt(1).setCustomView(R.layout.layout_largeclass_chatroom);
            layout_unRead = findViewById(R.id.layout_unRead);
            textView_unRead = findViewById(R.id.textView_unRead);
        }

        getScreenShareStream(new EduCallback<EduStreamInfo>() {
            @Override
            public void onSuccess(@Nullable EduStreamInfo streamInfo) {
                if (streamInfo != null) {
                    layout_whiteboard.setVisibility(View.GONE);
                    layout_share_video.setVisibility(View.VISIBLE);
                    layout_share_video.removeAllViews();
                    renderStream(getMainEduRoom(), streamInfo, layout_share_video);
                }
            }

            @Override
            public void onFailure(@NotNull EduError error) {

            }
        });

        resetHandState();
    }

    @Override
    protected int getClassType() {
        return Room.Type.LARGE;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setContentView(getLayoutResId());
        ButterKnife.bind(this);
        initView();
        recoveryFragmentWithConfigChanged();
    }

    @OnClick(R.id.layout_hand_up)
    public void onClick(View view) {
        boolean status = localCoVideoStatus == DisCoVideo;
        if (!status) {
            /*????????????(??????????????????????????????????????????????????????????????????)*/
            cancelCoVideo(new EduCallback<EduMsg>() {
                @Override
                public void onSuccess(@Nullable EduMsg res) {
                    Log.e(TAG, "??????????????????");
                }

                @Override
                public void onFailure(@NotNull EduError error) {
                    Log.e(TAG, "??????????????????");
                }
            });
        } else {
            /*??????*/
            applyCoVideo(new EduCallback<EduMsg>() {
                @Override
                public void onSuccess(@Nullable EduMsg res) {
                    Log.e(TAG, "????????????");
                }

                @Override
                public void onFailure(@NotNull EduError error) {
                    Log.e(TAG, "????????????");
                    ToastManager.showShort(R.string.function_error, error.getType(),
                            error.getMsg());
                }
            });
        }
    }

    /**
     * ??????????????????
     */
    private void applyCoVideo(EduCallback<EduMsg> callback) {
        getLocalUser(new EduCallback<EduUser>() {
            @Override
            public void onSuccess(@Nullable EduUser user) {
                PeerMsg.CoVideoMsg coVideoMsg = new PeerMsg.CoVideoMsg(PeerMsg.CoVideoMsg.Type.APPLY,
                        user.getUserInfo().getUserUuid(), user.getUserInfo().getUserName());
                PeerMsg peerMsg = new PeerMsg(PeerMsg.Cmd.CO_VIDEO, coVideoMsg);
                getTeacher(new EduCallback<EduUserInfo>() {
                    @Override
                    public void onSuccess(@Nullable EduUserInfo teacher) {
                        if (teacher != null) {
                            localCoVideoStatus = Applying;
                            resetHandState();
                            user.sendUserMessage(peerMsg.toJsonString(), teacher, callback);
                        } else {
                            ToastManager.showShort(R.string.there_is_no_teacher_disable_covideo);
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

    /**
     * ????????????(??????????????????????????????????????????????????????????????????)
     */
    private void cancelCoVideo(EduCallback<EduMsg> callback) {
        getLocalUser(new EduCallback<EduUser>() {
            @Override
            public void onSuccess(@Nullable EduUser user) {
                PeerMsg.CoVideoMsg coVideoMsg = new PeerMsg.CoVideoMsg(
                        (localCoVideoStatus == CoVideoing) ? EXIT : CANCEL,
                        user.getUserInfo().getUserUuid(), user.getUserInfo().getUserName());
                PeerMsg peerMsg = new PeerMsg(PeerMsg.Cmd.CO_VIDEO, coVideoMsg);
                getTeacher(new EduCallback<EduUserInfo>() {
                    @Override
                    public void onSuccess(@Nullable EduUserInfo teacher) {
                        if (localCoVideoStatus == CoVideoing) {
                            /*?????????????????????
                             * 1??????????????????
                             * 2??????????????????????????????
                             * 3??????????????????????????????????????????
                             * 4????????????????????????????????????*/
                            if (getLocalCameraStream() != null) {
                                LocalStreamInitOptions options = new LocalStreamInitOptions(
                                        getLocalCameraStream().getStreamUuid(), false, false);
                                options.setStreamName(getLocalCameraStream().getStreamName());
                                user.initOrUpdateLocalStream(options, new EduCallback<EduStreamInfo>() {
                                    @Override
                                    public void onSuccess(@Nullable EduStreamInfo res) {
                                        localCoVideoStatus = DisCoVideo;
                                        curLinkedUser = null;
                                        resetHandState();
                                        renderStudentStream(getLocalCameraStream(), null);
                                        /*???????????????????????????????????????*/
                                        if (teacher != null) {
                                            user.sendUserMessage(peerMsg.toJsonString(), teacher, callback);
                                        }
                                        user.unPublishStream(res, new EduCallback<Boolean>() {
                                            @Override
                                            public void onSuccess(@Nullable Boolean res) {
                                            }

                                            @Override
                                            public void onFailure(@NotNull EduError error) {
                                                callback.onFailure(error);
                                            }
                                        });
                                    }

                                    @Override
                                    public void onFailure(@NotNull EduError error) {
                                        Log.e(TAG, "???????????????????????????");
                                        callback.onFailure(error);
                                    }
                                });
                            }
                        } else {
                            /*?????????????????????(??????????????????)??????????????????????????????????????????????????????*/
                            user.sendUserMessage(peerMsg.toJsonString(), teacher, callback);
                            localCoVideoStatus = DisCoVideo;
                            runOnUiThread(() -> {
                                resetHandState();
                            });
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

    /**
     * ????????????(???????????????)???????????????/(???????????????)
     *
     * @param agree ????????????
     */
    public void onLinkMediaChanged(boolean agree) {
        getLocalUser(new EduCallback<EduUser>() {
            @Override
            public void onSuccess(@Nullable EduUser user) {
                if (!agree) {
                    video_student.setViewVisibility(View.GONE);
                    /**??????????????????????????????????????????????????????????????????????????????????????????*/
                    if (localCoVideoStatus == CoVideoing) {
                        Log.e(TAG, "????????????????????????");
                        /**??????????????????????????????*/
                        LocalStreamInitOptions options = new LocalStreamInitOptions(
                                getLocalCameraStream().getStreamUuid(), false, false);
                        options.setStreamName(getLocalCameraStream().getStreamName());
                        user.initOrUpdateLocalStream(options, new EduCallback<EduStreamInfo>() {
                            @Override
                            public void onSuccess(@Nullable EduStreamInfo res) {
                                renderStudentStream(getLocalCameraStream(), null);
                                user.unPublishStream(res, new EduCallback<Boolean>() {
                                    @Override
                                    public void onSuccess(@Nullable Boolean res) {
                                        Log.e(TAG, "?????????????????????????????????????????????");
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
                } else {
                    /**???????????????????????????????????????????????????????????????????????????????????????????????????????????????*/
                }
                localCoVideoStatus = agree ? CoVideoing : DisCoVideo;
                curLinkedUser = agree ? user.getUserInfo() : null;
                resetHandState();
            }

            @Override
            public void onFailure(@NotNull EduError error) {

            }
        });
    }

    /**
     * ???????????????
     */
    private void resetHandState() {
        runOnUiThread(() -> {
            getLocalUserInfo(new EduCallback<EduUserInfo>() {
                @Override
                public void onSuccess(@Nullable EduUserInfo userInfo) {
//            boolean hasTeacher = getTeacher() != null;
                    /**??????????????????????????????*/
//            layout_hand_up.setVisibility(hasTeacher ? View.VISIBLE : View.GONE);
                    /**????????????????????????????????????????????????*/
                    if (curLinkedUser != null) {
//                layout_hand_up.setVisibility((curLinkedUser.equals(getLocalUserInfo()) ?
//                        View.VISIBLE : View.GONE));
                        layout_hand_up.setEnabled(curLinkedUser.equals(userInfo));
                        layout_hand_up.setSelected(true);
                    } else {
                        layout_hand_up.setEnabled(true);
                        layout_hand_up.setSelected(false);
                    }
//            if (hasTeacher) {
//                layout_hand_up.setSelected(localCoVideoStatus != DisCoVideo);
//            }
                }

                @Override
                public void onFailure(@NotNull EduError error) {

                }
            });
        });
    }

    private boolean chatRoomShowing() {
        return layout_chat_room.getVisibility() == View.VISIBLE;
    }

    private void updateUnReadCount(boolean gone) {
        if (gone) {
            unReadCount = 0;
        } else {
            unReadCount++;
            if (textView_unRead != null) {
                textView_unRead.setText(String.valueOf(unReadCount));
            }
        }
        if (textView_unRead != null) {
            textView_unRead.setVisibility(gone ? View.GONE : View.VISIBLE);
        }
    }

    private void refreshStudentVideoZOrder() {
        runOnUiThread(() -> {
            removeFromParent(video_student);
            layout_video_student.addView(video_student, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout frameLayout = video_student.getVideoLayout();
            SurfaceView surfaceView = (SurfaceView) frameLayout.getChildAt(0);
            if (surfaceView != null) {
                surfaceView.setZOrderMediaOverlay(true);
            }
        });
    }

    private void renderStudentStream(EduStreamInfo streamInfo, ViewGroup viewGroup) {
        if (viewGroup != null) {
            runOnUiThread(() -> viewGroup.removeAllViews());
        }
        video_student.setViewVisibility((viewGroup == null) ? View.GONE : View.VISIBLE);
        video_student.setName(streamInfo.getPublisher().getUserName());
        renderStream(getMainEduRoom(), streamInfo, viewGroup);
        video_student.muteVideo(!streamInfo.getHasVideo());
        video_student.muteAudio(!streamInfo.getHasAudio());
    }

    private void renderOwnCoVideoStream(EduStreamEvent streamEvent) {
        EduStreamInfo modifiedStream = streamEvent.getModifiedStream();
        setLocalCameraStream(modifiedStream);
        onLinkMediaChanged(true);
        LocalStreamInitOptions options = new LocalStreamInitOptions(modifiedStream.getStreamUuid(),
                modifiedStream.getStreamName(), modifiedStream.getHasVideo(), modifiedStream.getHasAudio());
        getLocalUser(new EduCallback<EduUser>() {
            @Override
            public void onSuccess(@Nullable EduUser user) {
                user.initOrUpdateLocalStream(options, new EduCallback<EduStreamInfo>() {
                    @Override
                    public void onSuccess(@Nullable EduStreamInfo res) {
                        final EduStreamInfo stream = getLocalCameraStream();
                        if (stream != null) {
                            getMediaRoomInfo(new EduCallback<EduRoomInfo>() {
                                @Override
                                public void onSuccess(@Nullable EduRoomInfo roomInfo) {
                                    renderStudentStream(stream, video_student.getVideoLayout());
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

            @Override
            public void onFailure(@NotNull EduError error) {

            }
        });
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (layout_materials == null) {
            return;
        }
        boolean showMaterials = tab.getPosition() == 0;
        layout_materials.setVisibility(showMaterials ? View.VISIBLE : View.GONE);
        layout_chat_room.setVisibility(showMaterials ? View.GONE : View.VISIBLE);
        if (!showMaterials) {
            updateUnReadCount(true);
        }
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }


    @Override
    public void onRemoteUsersInitialized(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        super.onRemoteUsersInitialized(users, classRoom);
        setTitleData();
        resetHandState();
    }

    @Override
    public void onRemoteUsersJoined(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        super.onRemoteUsersJoined(users, classRoom);
        setTitleData();
        /**?????????????????????????????????*/
        resetHandState();

    }

    @Override
    public void onRemoteUserLeft(@NotNull EduUserEvent userEvent, @NotNull EduRoom classRoom) {
        super.onRemoteUserLeft(userEvent, classRoom);
        setTitleData();
        /**?????????????????????????????????*/
        resetHandState();
    }

    @Override
    public void onRemoteUserUpdated(@NotNull EduUserEvent userEvent, @NotNull EduUserStateChangeType type,
                                    @NotNull EduRoom classRoom) {
        super.onRemoteUserUpdated(userEvent, type, classRoom);
    }

    @Override
    public void onRoomMessageReceived(@NotNull EduMsg message, @NotNull EduRoom classRoom) {
        super.onRoomMessageReceived(message, classRoom);
    }

    @Override
    public void onRoomChatMessageReceived(@NotNull EduChatMsg eduChatMsg, @NotNull EduRoom classRoom) {
        super.onRoomChatMessageReceived(eduChatMsg, classRoom);
        runOnUiThread(() -> updateUnReadCount(chatRoomShowing()));
    }

    @Override
    public void onRemoteStreamsInitialized(@NotNull List<? extends EduStreamInfo> streams, @NotNull EduRoom classRoom) {
        super.onRemoteStreamsInitialized(streams, classRoom);
        for (int i = 0; i < streams.size(); i++) {
            EduStreamInfo element = streams.get(i);
            if (element.getPublisher().getRole().equals(EduUserRole.STUDENT)) {
                Collections.swap(streams, streams.size() - 1, i);
                break;
            }
        }
        /**???????????????????????????????????????????????????????????????????????????*/
        for (EduStreamInfo streamInfo : streams) {
            EduBaseUserInfo publisher = streamInfo.getPublisher();
            if (publisher.getRole().equals(EduUserRole.TEACHER)) {
                switch (streamInfo.getVideoSourceType()) {
                    case CAMERA:
                        video_teacher.setName(publisher.getUserName());
                        renderStream(getMainEduRoom(), streamInfo, video_teacher.getVideoLayout());
                        video_teacher.muteVideo(!streamInfo.getHasVideo());
                        video_teacher.muteAudio(!streamInfo.getHasAudio());
                        break;
                    case SCREEN:
                        runOnUiThread(() -> {
                            layout_whiteboard.setVisibility(View.GONE);
                            layout_share_video.setVisibility(View.VISIBLE);
                            layout_share_video.removeAllViews();
                            renderStream(getMainEduRoom(), streamInfo, layout_share_video);
                        });
                        break;
                    default:
                        break;
                }
            } else {
                Log.e(TAG, "????????????????????????,????????????");
                renderStudentStream(streamInfo, video_student.getVideoLayout());
                curLinkedUser = streamInfo.getPublisher();
                resetHandState();
                refreshStudentVideoZOrder();
            }
        }
    }

    @Override
    public void onRemoteStreamsAdded(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        super.onRemoteStreamsAdded(streamEvents, classRoom);
        for (EduStreamEvent streamEvent : streamEvents) {
            EduStreamInfo streamInfo = streamEvent.getModifiedStream();
            EduBaseUserInfo userInfo = streamInfo.getPublisher();
            if (userInfo.getRole().equals(EduUserRole.TEACHER)) {
                switch (streamInfo.getVideoSourceType()) {
                    case CAMERA:
                        /**??????????????????*/
                        video_teacher.setName(userInfo.getUserName());
                        renderStream(getMainEduRoom(), streamInfo, video_teacher.getVideoLayout());
                        video_teacher.muteVideo(!streamInfo.getHasVideo());
                        video_teacher.muteAudio(!streamInfo.getHasAudio());
                        /**?????????????????????????????????*/
                        refreshStudentVideoZOrder();
                        break;
                    default:
                        break;
                }
            } else {
                /**???????????????????????????*/
                renderStudentStream(streamInfo, video_student.getVideoLayout());
                curLinkedUser = streamInfo.getPublisher();
                resetHandState();
                refreshStudentVideoZOrder();
            }
        }
    }

    @Override
    public void onRemoteStreamUpdated(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        super.onRemoteStreamUpdated(streamEvents, classRoom);
        for (EduStreamEvent streamEvent : streamEvents) {
            EduStreamInfo streamInfo = streamEvent.getModifiedStream();
            EduBaseUserInfo userInfo = streamInfo.getPublisher();
            if (userInfo.getRole().equals(EduUserRole.TEACHER)) {
                switch (streamInfo.getVideoSourceType()) {
                    case CAMERA:
                        video_teacher.setName(userInfo.getUserName());
                        renderStream(getMainEduRoom(), streamInfo, video_teacher.getVideoLayout());
                        video_teacher.muteVideo(!streamInfo.getHasVideo());
                        video_teacher.muteAudio(!streamInfo.getHasAudio());
                        /**?????????????????????????????????*/
                        refreshStudentVideoZOrder();
                        break;
                    default:
                        break;
                }
            } else {
                renderStudentStream(streamInfo, video_student.getVideoLayout());
                curLinkedUser = streamInfo.getPublisher();
                resetHandState();
                refreshStudentVideoZOrder();
            }
        }
    }

    @Override
    public void onRemoteStreamsRemoved(@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        super.onRemoteStreamsRemoved(streamEvents, classRoom);
        for (EduStreamEvent streamEvent : streamEvents) {
            EduStreamInfo streamInfo = streamEvent.getModifiedStream();
            EduBaseUserInfo userInfo = streamInfo.getPublisher();
            if (userInfo.getRole().equals(EduUserRole.TEACHER) &&
                    streamInfo.getVideoSourceType().equals(VideoSourceType.CAMERA)) {
                video_teacher.setName(streamInfo.getPublisher().getUserName());
                renderStream(getMainEduRoom(), streamInfo, null);
                video_teacher.muteVideo(!streamInfo.getHasVideo());
                video_teacher.muteAudio(!streamInfo.getHasAudio());
            } else {
                renderStudentStream(streamInfo, null);
                if (curLinkedUser != null && curLinkedUser.equals(streamInfo.getPublisher())) {
                    curLinkedUser = null;
                }
                resetHandState();
            }
        }
    }

    @Override
    public void onRoomStatusChanged(@NotNull EduRoomChangeType event, @NotNull EduUserInfo operatorUser, @NotNull EduRoom classRoom) {
        super.onRoomStatusChanged(event, operatorUser, classRoom);
    }

    @Override
    public void onRoomPropertiesChanged(@NotNull EduRoom classRoom, @Nullable Map<String, Object> cause) {
        super.onRoomPropertiesChanged(classRoom, cause);
        /*????????????????????????????????????*/
        runOnUiThread(() -> {
            if (revRecordMsg) {
                revRecordMsg = false;
                updateUnReadCount(chatRoomShowing());
            }
        });
    }

    @Override
    public void onNetworkQualityChanged(@NotNull NetworkQuality quality, @NotNull EduUserInfo user,
                                        @NotNull EduRoom classRoom) {
        super.onNetworkQualityChanged(quality, user, classRoom);
    }

    @Override
    public void onConnectionStateChanged(@NotNull ConnectionState state, @NotNull EduRoom classRoom) {
        super.onConnectionStateChanged(state, classRoom);
    }

    @Override
    public void onLocalUserUpdated(@NotNull EduUserEvent userEvent, @NotNull EduUserStateChangeType type) {
        super.onLocalUserUpdated(userEvent, type);
    }

    @Override
    public void onLocalStreamAdded(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamAdded(streamEvent);
        renderOwnCoVideoStream(streamEvent);
    }

    @Override
    public void onLocalStreamUpdated(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamUpdated(streamEvent);
        /**?????????(?????????Camera???)?????????;?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????*/
        renderOwnCoVideoStream(streamEvent);
    }

    @Override
    public void onLocalStreamRemoved(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamRemoved(streamEvent);
    }

    @Override
    public void onUserMessageReceived(@NotNull EduMsg message) {
        super.onUserMessageReceived(message);
        PeerMsg peerMsg = PeerMsg.fromJson(message.getMessage(), PeerMsg.class);
        if (peerMsg.cmd == PeerMsg.Cmd.CO_VIDEO) {
            PeerMsg.CoVideoMsg coVideoMsg = peerMsg.getMsg(PeerMsg.CoVideoMsg.class);
            switch (coVideoMsg.type) {
                case REJECT:
                    onLinkMediaChanged(false);
                    ToastManager.showShort(R.string.reject_interactive);
                    break;
                case ACCEPT:
//                    onLinkMediaChanged(true);
                    ToastManager.showShort(R.string.accept_interactive);
                    break;
                case ABORT:
                    onLinkMediaChanged(false);
                    ToastManager.showShort(R.string.abort_interactive);
                    break;
            }
        }
    }

    @Override
    public void onUserChatMessageReceived(@NotNull EduChatMsg chatMsg) {
        super.onUserChatMessageReceived(chatMsg);
    }

    @Override
    public void onLocalUserLeft(@NotNull EduUserEvent userEvent, @NotNull EduUserLeftType leftType) {

    }
}
