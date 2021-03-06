package io.agora.education.impl.util

import io.agora.education.api.message.AgoraActionType
import io.agora.education.api.message.EduFromUserInfo
import io.agora.education.api.room.EduRoom
import io.agora.education.api.room.data.*
import io.agora.education.api.statistics.ConnectionState
import io.agora.education.api.statistics.NetworkQuality
import io.agora.education.api.stream.data.*
import io.agora.education.api.user.data.EduChatState
import io.agora.education.api.user.data.EduLocalUserInfo
import io.agora.education.api.user.data.EduUserInfo
import io.agora.education.api.user.data.EduUserRole
import io.agora.education.impl.cmd.bean.*
import io.agora.education.impl.role.data.EduUserRoleStr
import io.agora.education.impl.room.data.response.*
import io.agora.education.impl.stream.EduStreamInfoImpl
import io.agora.education.impl.user.data.EduUserInfoImpl
import io.agora.education.impl.room.EduRoomImpl
import io.agora.education.impl.user.data.EduLocalUserInfoImpl
import io.agora.education.impl.user.data.request.RoleMuteConfig
import io.agora.rtc.Constants.*
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtm.RtmStatusCode.ConnectionState.CONNECTION_STATE_DISCONNECTED

internal object Convert {
    fun convertVideoEncoderConfig(videoEncoderConfig: VideoEncoderConfig): VideoEncoderConfiguration {
        var videoDimensions = VideoEncoderConfiguration.VideoDimensions(
                videoEncoderConfig.videoDimensionWidth,
                videoEncoderConfig.videoDimensionHeight)
        var videoEncoderConfiguration = VideoEncoderConfiguration()
        videoEncoderConfiguration.dimensions = videoDimensions
        videoEncoderConfiguration.frameRate = videoEncoderConfig.frameRate
        when (videoEncoderConfig.orientationMode) {
            OrientationMode.ADAPTIVE -> {
                videoEncoderConfiguration.orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_ADAPTIVE
            }
            OrientationMode.FIXED_LANDSCAPE -> {
                videoEncoderConfiguration.orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_LANDSCAPE
            }
            OrientationMode.FIXED_PORTRAIT -> {
                videoEncoderConfiguration.orientationMode = VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
            }
        }
        when (videoEncoderConfig.degradationPreference) {
            DegradationPreference.MAINTAIN_QUALITY -> {
                videoEncoderConfiguration.degradationPrefer = VideoEncoderConfiguration.DEGRADATION_PREFERENCE.MAINTAIN_QUALITY
            }
            DegradationPreference.MAINTAIN_FRAME_RATE -> {
                videoEncoderConfiguration.degradationPrefer = VideoEncoderConfiguration.DEGRADATION_PREFERENCE.MAINTAIN_FRAMERATE
            }
            DegradationPreference.MAINTAIN_BALANCED -> {
                videoEncoderConfiguration.degradationPrefer = VideoEncoderConfiguration.DEGRADATION_PREFERENCE.MAINTAIN_BALANCED
            }
        }
        return videoEncoderConfiguration
    }

    fun convertRoomType(roomType: Int): RoomType {
        return when (roomType) {
            RoomType.ONE_ON_ONE.value -> {
                RoomType.ONE_ON_ONE
            }
            RoomType.SMALL_CLASS.value -> {
                RoomType.SMALL_CLASS
            }
            else -> {
                RoomType.LARGE_CLASS
            }
        }
    }

    /**??????EduUserRole???????????????????????????*/
    fun convertUserRole(role: EduUserRole, roomType: RoomType): String {
        return when (role) {
            EduUserRole.TEACHER -> {
                EduUserRoleStr.host.name
            }
            EduUserRole.STUDENT -> {
                when (roomType) {
                    RoomType.ONE_ON_ONE -> {
                        EduUserRoleStr.broadcaster.name
                    }
                    RoomType.SMALL_CLASS -> {
                        EduUserRoleStr.broadcaster.name
                    }
                    else -> {
                        EduUserRoleStr.audience.name
                    }
                }
            }
            EduUserRole.ASSISTANT -> {
                EduUserRoleStr.assistant.name
            }
            else -> {
                EduUserRoleStr.audience.name
            }
        }
    }

    /**???????????????????????????EduUserRole?????????*/
    fun convertUserRole(role: String, roomType: RoomType): EduUserRole {
        when (role) {
            EduUserRoleStr.host.name -> {
                return EduUserRole.TEACHER
            }
            EduUserRoleStr.assistant.name -> {
                return EduUserRole.ASSISTANT
            }
            EduUserRoleStr.broadcaster.name -> {
                if (roomType == RoomType.ONE_ON_ONE || roomType == RoomType.SMALL_CLASS) {
                    return EduUserRole.STUDENT
                }
            }
            EduUserRoleStr.audience.name -> {
                return EduUserRole.STUDENT
            }
        }
        return EduUserRole.STUDENT
    }

    /**????????????????????????stream???????????????????????????*/
    fun getUserInfoList(eduUserListRes: EduUserListRes?, roomType: RoomType): MutableList<EduUserInfo> {
        val list = eduUserListRes?.list
        if (list?.size == 0) {
            return mutableListOf()
        }
        val userInfoList: MutableList<EduUserInfo> = mutableListOf()
        for ((index, element) in list?.withIndex()!!) {
            val eduUser = convertUserInfo(element, roomType)
            userInfoList.add(index, eduUser)
        }
        return userInfoList
    }

    fun convertUserInfo(eduUserRes: EduUserRes, roomType: RoomType): EduUserInfo {
        val role = convertUserRole(eduUserRes.role, roomType)
        return EduUserInfoImpl(eduUserRes.userUuid, eduUserRes.userName, role,
                eduUserRes.muteChat == EduChatState.Allow.value, eduUserRes.updateTime)
    }

    fun convertUserInfo(eduUserRes: EduFromUserRes, roomType: RoomType): EduUserInfo {
        val role = convertUserRole(eduUserRes.role, roomType)
        return EduUserInfoImpl(eduUserRes.userUuid, eduUserRes.userName, role, false, null)
    }

    fun convertFromUserInfo(eduUserRes: EduFromUserRes, roomType: RoomType): EduFromUserInfo {
        val role = convertUserRole(eduUserRes.role, roomType)
        return EduFromUserInfo(eduUserRes.userUuid, eduUserRes.userName, role)
    }

    fun convertFromUserInfo(localUserInfo: EduLocalUserInfo): EduFromUserInfo {
        return EduFromUserInfo(localUserInfo.userUuid, localUserInfo.userName, localUserInfo.role)
    }

    /**????????????????????????stream???????????????stream??????*/
    fun getStreamInfoList(eduStreamListRes: EduStreamListRes?, roomType: RoomType): MutableList<EduStreamInfo> {
        val userResList = eduStreamListRes?.list
        if (userResList?.size == 0) {
            return mutableListOf()
        }
        val streamInfoList: MutableList<EduStreamInfo> = mutableListOf()
        for ((index, element) in userResList?.withIndex()!!) {
            val eduUserInfo = convertUserInfo(element.fromUser, roomType)
            val videoSourceType = if (element.videoSourceType == 1) VideoSourceType.CAMERA else VideoSourceType.SCREEN
            val hasVideo = element.videoState == EduVideoState.Open.value
            val hasAudio = element.audioState == EduAudioState.Open.value
            val eduStreamInfo = EduStreamInfoImpl(element.streamUuid, element.streamName, videoSourceType,
                    hasVideo, hasAudio, eduUserInfo, element.updateTime)
            streamInfoList.add(index, eduStreamInfo)
        }
        return streamInfoList
    }

    fun convertRoomState(state: Int): EduRoomState {
        return when (state) {
            EduRoomState.INIT.value -> {
                EduRoomState.INIT
            }
            EduRoomState.START.value -> {
                EduRoomState.START
            }
            EduRoomState.END.value -> {
                EduRoomState.END
            }
            else -> {
                EduRoomState.INIT
            }
        }
    }

    fun convertStreamInfo(streamRes: CMDStreamRes, roomType: RoomType): EduStreamInfo {
        val fromUserInfo = convertUserInfo(streamRes.fromUser, roomType)
        return EduStreamInfoImpl(streamRes.streamUuid, streamRes.streamName,
                convertVideoSourceType(streamRes.videoSourceType),
                streamRes.videoState == EduVideoState.Open.value,
                streamRes.audioState == EduAudioState.Open.value,
                fromUserInfo, streamRes.updateTime)
    }

    fun convertStreamInfo(cmdStreamActionMsg: CMDStreamActionMsg, roomType: RoomType): EduStreamInfo {
        val fromUserInfo = convertUserInfo(cmdStreamActionMsg.fromUser, roomType)
        return EduStreamInfoImpl(cmdStreamActionMsg.streamUuid, cmdStreamActionMsg.streamName,
                convertVideoSourceType(cmdStreamActionMsg.videoSourceType),
                cmdStreamActionMsg.videoState == EduVideoState.Open.value,
                cmdStreamActionMsg.audioState == EduAudioState.Open.value,
                fromUserInfo, cmdStreamActionMsg.updateTime)
    }

    fun convertStreamInfo(streamResList: MutableList<EduEntryStreamRes>, eduRoom: EduRoom): MutableList<EduStreamEvent> {
        val streamEvents = mutableListOf<EduStreamEvent>()
        val eduStreamInfos = (eduRoom as EduRoomImpl).getCurStreamList()
        synchronized(eduStreamInfos) {
            streamResList.forEach {
                val videoSourceType = convertVideoSourceType(it.videoSourceType)
                val streamInfo = EduStreamInfoImpl(it.streamUuid, it.streamName, videoSourceType,
                        it.videoState == EduVideoState.Open.value,
                        it.audioState == EduAudioState.Open.value, eduRoom.getCurLocalUserInfo(),
                        it.updateTime
                )
                /**?????????????????????????????????*/
                eduStreamInfos.add(streamInfo)
                streamEvents.add(EduStreamEvent(streamInfo, null))
            }
            return streamEvents
        }
    }

    fun convertStreamInfo(syncStreamRes: CMDSyncStreamRes, eduUserInfo: EduUserInfo): EduStreamInfo {
        val videoSourceType = convertVideoSourceType(syncStreamRes.videoSourceType)
        val hasVideo = syncStreamRes.videoState == EduVideoState.Open.value
        val hasAudio = syncStreamRes.audioState == EduAudioState.Open.value
        return EduStreamInfoImpl(syncStreamRes.streamUuid,
                syncStreamRes.streamName, videoSourceType, hasVideo, hasAudio, eduUserInfo,
                syncStreamRes.updateTime)
    }

    fun convertVideoSourceType(value: Int): VideoSourceType {
        return when (value) {
            VideoSourceType.CAMERA.value -> {
                VideoSourceType.CAMERA
            }
            VideoSourceType.SCREEN.value -> {
                VideoSourceType.SCREEN
            }
            else -> {
                VideoSourceType.CAMERA
            }
        }
    }

    fun convertUserInfo(cmdUserStateMsg: CMDUserStateMsg, roomType: RoomType): EduUserInfo {
        val role = convertUserRole(cmdUserStateMsg.role, roomType)
        return EduUserInfoImpl(cmdUserStateMsg.userUuid, cmdUserStateMsg.userName, role,
                cmdUserStateMsg.muteChat == EduChatState.Allow.value,
                cmdUserStateMsg.updateTime)
    }

    /**??????roomType??????muteChat(??????student??????)?????????*/
    fun extractStudentChatAllowState(muteChatConfig: RoleMuteConfig?, roomType: RoomType): Boolean {
        /**?????????????????????????????????????????????*/
        var allow = true
        when (roomType) {
            RoomType.ONE_ON_ONE, RoomType.SMALL_CLASS -> {
                muteChatConfig?.broadcaster?.let {
                    allow = muteChatConfig?.broadcaster?.toInt() == EduMuteState.Enable.value
                }
            }
            RoomType.LARGE_CLASS -> {
                var allow0 = true
                var allow1 = true
                muteChatConfig?.audience?.let {
                    allow0 = it.toInt() == EduMuteState.Enable.value
                }
                muteChatConfig?.broadcaster?.let {
                    allow1 = it.toInt() == EduMuteState.Enable.value
                }
                allow = allow0 || allow1
            }
        }
        return allow
    }

    fun convertConnectionState(connectionState: Int): ConnectionState {
        return when (connectionState) {
            CONNECTION_STATE_DISCONNECTED -> {
                ConnectionState.DISCONNECTED
            }
            CONNECTION_STATE_DISCONNECTED -> {
                ConnectionState.CONNECTING
            }
            CONNECTION_STATE_DISCONNECTED -> {
                ConnectionState.CONNECTED
            }
            CONNECTION_STATE_DISCONNECTED -> {
                ConnectionState.RECONNECTING
            }
            CONNECTION_STATE_DISCONNECTED -> {
                ConnectionState.ABORTED
            }
            else -> {
                ConnectionState.DISCONNECTED
            }
        }
    }

    fun convertCMDResponseBody(cmdResponseBody: CMDResponseBody<Any>): EduSequenceRes<Any> {
        return EduSequenceRes(cmdResponseBody.sequence, cmdResponseBody.cmd,
                cmdResponseBody.version, cmdResponseBody.data)
    }

    fun convertEduSequenceRes(sequence: EduSequenceRes<Any>): CMDResponseBody<Any> {
        return CMDResponseBody(sequence.cmd, sequence.version, 0, null, sequence.sequence,
                sequence.data)
    }

    fun convertUserInfo(userInfo: EduLocalUserInfo): EduUserInfoImpl {
        return EduUserInfoImpl(userInfo.userUuid, userInfo.userName, userInfo.role,
                userInfo.isChatAllowed ?: false, (userInfo as EduLocalUserInfoImpl).updateTime)
    }

    fun streamExistsInList(streamInfo: EduStreamInfo, list: MutableList<EduStreamInfo>): Int {
        var pos = -1
        streamInfo?.let {
            for ((index, element) in list.withIndex()) {
                if (element.same(it)) {
                    pos = index
                    break
                }
            }
        }
        return pos
    }

    fun convertActionMsgType(value: Int): AgoraActionType {
        return when (value) {
            AgoraActionType.AgoraActionTypeApply.value -> {
                AgoraActionType.AgoraActionTypeApply
            }
            AgoraActionType.AgoraActionTypeInvitation.value -> {
                AgoraActionType.AgoraActionTypeApply
            }
            AgoraActionType.AgoraActionTypeAccept.value -> {
                AgoraActionType.AgoraActionTypeApply
            }
            AgoraActionType.AgoraActionTypeReject.value -> {
                AgoraActionType.AgoraActionTypeApply
            }
            else -> {
                AgoraActionType.AgoraActionTypeReject
            }
        }
    }

//    fun convertEduActionMsg(text: String): EduActionMessage {
//        val cmdResponseBody = Gson().fromJson<CMDResponseBody<CMDActionMsgRes>>(text, object :
//                TypeToken<CMDResponseBody<CMDActionMsgRes>>() {}.type)
//        val msg = cmdResponseBody.data
//        return EduActionMessage(msg.processUuid, convertActionMsgType(msg.action), msg.timeout,
//                msg.fromUser, convertEduRoomInfo(msg.fromRoom), msg.payload)
//    }

    fun convertEduRoomInfo(roomInfoRes: EduSnapshotRoomInfoRes): EduRoomInfo {
        return EduRoomInfo(roomInfoRes.roomUuid, roomInfoRes.roomName)
    }

    fun convertNetworkQuality(quality: Int): NetworkQuality {
        return when (quality) {
            QUALITY_UNKNOWN, QUALITY_DETECTING -> NetworkQuality.UNKNOWN
            QUALITY_EXCELLENT, QUALITY_GOOD -> NetworkQuality.GOOD
            QUALITY_POOR, QUALITY_BAD -> NetworkQuality.POOR
            QUALITY_VBAD, QUALITY_DOWN -> NetworkQuality.BAD
            else -> NetworkQuality.UNKNOWN
        }
    }

    fun convertCMDStreamRes(streamAction: CMDStreamActionMsg): CMDStreamRes {
        return CMDStreamRes(streamAction.fromUser, streamAction.streamUuid,
                streamAction.streamName, streamAction.videoSourceType, streamAction.audioSourceType,
                streamAction.videoState, streamAction.audioState, streamAction.action,
                streamAction.updateTime);
    }
}
