package io.agora.education.impl.room

import android.text.TextUtils
import android.util.Log
import androidx.annotation.NonNull
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.agora.Constants.Companion.APPID
import io.agora.Constants.Companion.AgoraLog
import io.agora.base.callback.ThrowableCallback
import io.agora.base.network.BusinessException
import io.agora.education.api.BuildConfig.API_BASE_URL
import io.agora.education.api.EduCallback
import io.agora.education.api.base.EduError
import io.agora.education.api.base.EduError.Companion.communicationError
import io.agora.education.api.base.EduError.Companion.httpError
import io.agora.education.api.base.EduError.Companion.mediaError
import io.agora.education.api.base.EduError.Companion.notJoinedRoomError
import io.agora.education.api.base.EduError.Companion.parameterError
import io.agora.education.api.logger.LogLevel
import io.agora.education.api.room.EduRoom
import io.agora.education.api.room.data.*
import io.agora.education.api.statistics.NetworkQuality
import io.agora.education.api.stream.data.*
import io.agora.education.api.user.EduStudent
import io.agora.education.api.user.EduUser
import io.agora.education.api.user.data.EduChatState
import io.agora.education.api.user.data.EduUserInfo
import io.agora.education.api.user.data.EduUserRole
import io.agora.education.impl.ResponseBody
import io.agora.education.impl.board.EduBoardImpl
import io.agora.education.impl.cmd.CMDDispatch
import io.agora.education.impl.cmd.bean.CMDResponseBody
import io.agora.education.impl.manager.EduManagerImpl
import io.agora.education.impl.network.RetrofitManager
import io.agora.education.impl.record.EduRecordImpl
import io.agora.education.impl.role.data.EduUserRoleStr
import io.agora.education.impl.room.data.EduRoomInfoImpl
import io.agora.education.impl.room.data.request.EduJoinClassroomReq
import io.agora.education.impl.room.data.response.*
import io.agora.education.impl.sync.RoomSyncHelper
import io.agora.education.impl.sync.RoomSyncSession
import io.agora.education.impl.user.EduStudentImpl
import io.agora.education.impl.user.EduUserImpl
import io.agora.education.impl.user.data.EduLocalUserInfoImpl
import io.agora.education.impl.user.network.UserService
import io.agora.education.impl.util.CommonUtil
import io.agora.education.impl.util.Convert
import io.agora.rtc.Constants.*
import io.agora.rtc.models.ChannelMediaOptions
import io.agora.rte.RteCallback
import io.agora.rte.RteEngineImpl
import io.agora.rte.data.ErrorType
import io.agora.rte.data.RteError
import io.agora.rte.listener.RteChannelEventListener
import io.agora.rtm.*
import kotlin.math.max

internal class EduRoomImpl(
        roomInfo: EduRoomInfo,
        roomStatus: EduRoomStatus
) : EduRoom(), RteChannelEventListener {

    private val TAG = EduRoomImpl::class.java.simpleName
    internal var syncSession: RoomSyncSession
    internal var cmdDispatch: CMDDispatch

    init {
        AgoraLog.i("$TAG->Init $TAG")
        RteEngineImpl.createChannel(roomInfo.roomUuid, this)
        syncSession = RoomSyncHelper(this, roomInfo, roomStatus, 3)
        record = EduRecordImpl()
        board = EduBoardImpl()
        cmdDispatch = CMDDispatch(this)
        /**????????????room*/
        EduManagerImpl.addRoom(this)
    }

    lateinit var rtcToken: String

    /**??????????????????join?????????????????????*/
    private var studentJoinCallback: EduCallback<EduStudent>? = null
    private lateinit var roomEntryRes: EduEntryRes
    lateinit var mediaOptions: RoomMediaOptions

    /**???????????????????????????*/
    private var leaveRoom: Boolean = false

    /**??????join????????????????????????*/
    var joinSuccess: Boolean = false

    /**??????join???????????????????????????*/
    var joining = false

    /**entry????????????????????????(??????????????????????????????????????????autoPublish???*/
    var defaultStreams: MutableList<EduStreamEvent> = mutableListOf()

    lateinit var defaultUserName: String

    internal fun getCurRoomUuid(): String {
        return syncSession.roomInfo.roomUuid
    }

    internal fun getCurRoomInfo(): EduRoomInfo {
        return syncSession.roomInfo
    }

    internal fun getCurRoomStatus(): EduRoomStatus {
        return syncSession.roomStatus
    }

    internal fun getCurLocalUser(): EduUser {
        return syncSession.localUser
    }

    internal fun getCurLocalUserInfo(): EduUserInfo {
        return syncSession.localUser.userInfo
    }

    internal fun getCurRoomType(): RoomType {
        return (syncSession.roomInfo as EduRoomInfoImpl).roomType
    }

    internal fun getCurStudentList(): MutableList<EduUserInfo> {
        val studentList = mutableListOf<EduUserInfo>()
        for (element in getCurUserList()) {
            if (element.role == EduUserRole.STUDENT) {
                studentList.add(element)
            }
        }
        return studentList
    }

    internal fun getCurTeacherList(): MutableList<EduUserInfo> {
        val teacherList = mutableListOf<EduUserInfo>()
        for (element in getCurUserList()) {
            if (element.role == EduUserRole.TEACHER) {
                teacherList.add(element)
            }
        }
        return teacherList
    }

    internal fun getCurUserList(): MutableList<EduUserInfo> {
        return syncSession.eduUserInfoList
    }

    internal fun getCurRemoteUserList(): MutableList<EduUserInfo> {
        val list = mutableListOf<EduUserInfo>()
        syncSession.eduUserInfoList?.forEach {
            if (it != syncSession.localUser.userInfo) {
                list.add(it)
            }
        }
        return list
    }

    internal fun getCurStreamList(): MutableList<EduStreamInfo> {
        return syncSession.eduStreamInfoList
    }

    internal fun getCurRemoteStreamList(): MutableList<EduStreamInfo> {
        val list = mutableListOf<EduStreamInfo>()
        syncSession.eduStreamInfoList?.forEach {
            if (it.publisher != syncSession.localUser.userInfo) {
                list.add(it)
            }
        }
        return list
    }

    /**??????????????????????????????????????????????????????;
     * join????????????????????????classroom???API???????????????rte?????????roomInfo??????????????????????????????????????????????????????????????????join??????*/
    override fun joinClassroom(options: RoomJoinOptions, callback: EduCallback<EduStudent>) {
        if (TextUtils.isEmpty(options.userUuid)) {
            callback.onFailure(parameterError("userUuid"))
            return
        }
        AgoraLog.i("$TAG->User[${options.userUuid}]is ready to join the eduRoom:${getCurRoomUuid()}")
        this.joining = true
        this.studentJoinCallback = callback
        /**??????????????????????????????*/
        if (options.userName == null) {
            AgoraLog.i("$TAG->roomJoinOptions.userName is null,user default userName:$defaultUserName")
            options.userName = defaultUserName
        }
        val localUserInfo = EduLocalUserInfoImpl(options.userUuid, options.userName!!, EduUserRole.STUDENT,
                true, null, mutableListOf(), System.currentTimeMillis())
        /**???????????????localUserInfo?????????localUser???*/
        syncSession.localUser = EduStudentImpl(localUserInfo)
        (syncSession.localUser as EduUserImpl).eduRoom = this
        /**??????????????????????????????*/
        if (getCurRoomType() == RoomType.LARGE_CLASS) {
            AgoraLog.logMsg("LargeClass force not autoPublish", LogLevel.WARN.value)
            //TODO separate Large class and middle class logic here
            options.closeAutoPublish()
        }
        mediaOptions = options.mediaOptions
        /**??????classroomType????????????????????????????????????????????????????????????????????????*/
        val role = Convert.convertUserRole(localUserInfo.role, getCurRoomType())
        val eduJoinClassroomReq = EduJoinClassroomReq(localUserInfo.userName, role,
                mediaOptions.primaryStreamId.toString(), mediaOptions.getPublishType().value)
        RetrofitManager.instance()!!.getService(API_BASE_URL, UserService::class.java)
                .joinClassroom(APPID, getCurRoomUuid(), localUserInfo.userUuid, eduJoinClassroomReq)
                .enqueue(RetrofitManager.Callback(0, object : ThrowableCallback<ResponseBody<EduEntryRes>> {
                    override fun onSuccess(res: ResponseBody<EduEntryRes>?) {
                        roomEntryRes = res?.data!!
                        /**???????????????user????????????*/
                        localUserInfo.userToken = roomEntryRes.user.userToken
                        rtcToken = roomEntryRes.user.rtcToken
                        RetrofitManager.instance()!!.addHeader("token", roomEntryRes.user.userToken)
                        localUserInfo.isChatAllowed = roomEntryRes.user.muteChat == EduChatState.Allow.value
                        localUserInfo.userProperties = roomEntryRes.user.userProperties
                        localUserInfo.streamUuid = roomEntryRes.user.streamUuid
                        /**?????????????????????????????????????????????(??????????????????)*/
                        syncSession.eduUserInfoList.add(Convert.convertUserInfo(localUserInfo))
                        /**???????????????????????????????????????join?????????????????????;*/
                        roomEntryRes.user.streams?.let {
                            /**???????????????????????????????????????*/
                            val streamEvents = Convert.convertStreamInfo(it, this@EduRoomImpl);
                            defaultStreams.addAll(streamEvents)
                        }
                        /**???????????????room????????????????????????????????????*/
                        getCurRoomStatus().startTime = roomEntryRes.room.roomState.startTime
                        getCurRoomStatus().courseState = Convert.convertRoomState(roomEntryRes.room.roomState.state)
                        getCurRoomStatus().isStudentChatAllowed = Convert.extractStudentChatAllowState(
                                roomEntryRes.room.roomState.muteChat, getCurRoomType())
                        roomEntryRes.room.roomProperties?.let {
                            roomProperties = it
                        }
                        /**??????rte(??????rtm???rtc)*/
                        joinRte(rtcToken, roomEntryRes.user.streamUuid.toLong(),
                                mediaOptions.convert(), options.tag, object : RteCallback<Void> {
                            override fun onSuccess(p0: Void?) {
                                AgoraLog.i("$TAG->joinRte success")
                                /**??????????????????*/
                                syncSession.fetchSnapshot(object : EduCallback<Unit> {
                                    override fun onSuccess(res: Unit?) {
                                        AgoraLog.i("$TAG->Full data pull and merge successfully,init localStream")
                                        initOrUpdateLocalStream(roomEntryRes, mediaOptions, object : EduCallback<Unit> {
                                            override fun onSuccess(res: Unit?) {
                                                joinSuccess(syncSession.localUser, studentJoinCallback as EduCallback<EduUser>)
                                            }

                                            override fun onFailure(error: EduError) {
                                                joinFailed(error, studentJoinCallback as EduCallback<EduUser>)
                                            }
                                        })
                                    }

                                    override fun onFailure(error: EduError) {
                                        AgoraLog.i("$TAG->Full data pull failed")
                                        joinFailed(error, callback as EduCallback<EduUser>)
                                    }
                                })
                            }

                            override fun onFailure(error: RteError) {
                                AgoraLog.i("$TAG->joinRte failed")
                                var eduError = if (error.type == ErrorType.RTC) {
                                    mediaError(error.errorCode, error.errorDesc)
                                } else {
                                    communicationError(error.errorCode, error.errorDesc)
                                }
                                joinFailed(eduError, callback as EduCallback<EduUser>)
                            }
                        })
                    }

                    override fun onFailure(throwable: Throwable?) {
                        AgoraLog.i("$TAG->Calling the entry API failed")
                        var error = throwable as? BusinessException
                        error = error ?: BusinessException(throwable?.message)
                        joinFailed(httpError(error?.code, error?.message ?: throwable?.message),
                                callback as EduCallback<EduUser>)
                    }
                }))
    }

    private fun joinRte(rtcToken: String, rtcUid: Long, channelMediaOptions: ChannelMediaOptions,
                        tag: Int?, @NonNull callback: RteCallback<Void>) {
        AgoraLog.i("$TAG->join Rtc and Rtm")
        RteEngineImpl.setClientRole(getCurRoomUuid(), CLIENT_ROLE_BROADCASTER)
        val rtcOptionalInfo: String = CommonUtil.buildRtcOptionalInfo(tag)
        RteEngineImpl[getCurRoomUuid()]?.join(rtcOptionalInfo, rtcToken, rtcUid, channelMediaOptions, callback)
    }

    private fun initOrUpdateLocalStream(classRoomEntryRes: EduEntryRes, roomMediaOptions: RoomMediaOptions,
                                        callback: EduCallback<Unit>) {
        val localStreamInitOptions = LocalStreamInitOptions(classRoomEntryRes.user.streamUuid,
                roomMediaOptions.autoPublish, roomMediaOptions.autoPublish)
        AgoraLog.i("$TAG->initOrUpdateLocalStream for localUser:${Gson().toJson(localStreamInitOptions)}")
        syncSession.localUser.initOrUpdateLocalStream(localStreamInitOptions, object : EduCallback<EduStreamInfo> {
            override fun onSuccess(streamInfo: EduStreamInfo?) {
                AgoraLog.i("$TAG->initOrUpdateLocalStream success")
                /**??????????????????????????????????????????(????????????????????????????????????????????????)*/
                val pos = Convert.streamExistsInList(streamInfo!!, getCurStreamList())
                if (pos > -1) {
                    getCurStreamList()[pos] = streamInfo!!
                }
                /**?????????????????????????????????????????????(????????????)*/
                val role = Convert.convertUserRole(syncSession.localUser.userInfo.role, getCurRoomType())
                if (role == EduUserRoleStr.audience.value) {
                    AgoraLog.i("$TAG->The role of localUser is audience, nothing to do")
                } else {
                    /**?????????????????????audience,????????????????????????broadcaster*/
                    val role = if (getCurRoomType() !=
                            RoomType.LARGE_CLASS) CLIENT_ROLE_BROADCASTER else CLIENT_ROLE_AUDIENCE
                    RteEngineImpl.setClientRole(getCurRoomUuid(), role)
                    AgoraLog.i("$TAG->The role of localUser is not audience???follow roomType:${getCurRoomType()} " +
                            "to set Rtc role is:$role")
                    if (mediaOptions.autoPublish) {
                        val code = RteEngineImpl.publish(getCurRoomUuid())
                        AgoraLog.i("$TAG->AutoPublish is true, publish results:$code")
                    }
                }
                callback.onSuccess(Unit)
            }

            override fun onFailure(error: EduError) {
                AgoraLog.e("$TAG->Failed to initOrUpdateLocalStream for localUser")
                callback.onFailure(error)
            }
        })
    }

    /**??????joining????????????????????????*/
    private fun joinSuccess(eduUser: EduUser, callback: EduCallback<EduUser>) {
        if (joining) {
            joining = false
            synchronized(joinSuccess) {
                Log.e(TAG, "Join the eduRoom successfully:${getCurRoomUuid()}")
                /**?????????????????????????????????*/
                getCurRoomStatus().onlineUsersCount = getCurUserList().size
                joinSuccess = true
                callback.onSuccess(eduUser as EduStudent)
                /**???????????????????????????????????????????????????initialized??????*/
                if (getCurRemoteUserList().size > 0) {
                    eventListener?.onRemoteUsersInitialized(getCurRemoteUserList(), this@EduRoomImpl)
                }
                if (getCurRemoteStreamList().size > 0) {
                    eventListener?.onRemoteStreamsInitialized(getCurRemoteStreamList(), this@EduRoomImpl)
                }
                /**??????????????????????????????(??????????????????)*/
                val addedStreamsIterable = defaultStreams.iterator()
                while (addedStreamsIterable.hasNext()) {
                    val element = addedStreamsIterable.next()
                    val streamInfo = element.modifiedStream
                    /**????????????????????????*/
                    if (streamInfo.publisher == syncSession.localUser.userInfo) {
                        /**?????????????????????????????????????????????????????????*/
                        syncSession.localUser.userInfo.streams.add(element)
                        /**??????????????????????????????????????????*/
                        RteEngineImpl.updateLocalStream(streamInfo.hasAudio, streamInfo.hasVideo)
                        RteEngineImpl.publish(getCurRoomUuid())
                        AgoraLog.i("$TAG->Join success???callback the added localStream to upper layer")
                        syncSession.localUser.eventListener?.onLocalStreamAdded(element)
                        /**????????????*/
                        addedStreamsIterable.remove()
                    }
                }
                /**???????????????????????????(??????CMD??????)*/
                (syncSession as RoomSyncHelper).handleCache(object : EduCallback<Unit> {
                    override fun onSuccess(res: Unit?) {
                    }

                    override fun onFailure(error: EduError) {
                    }
                })
            }
        }
    }

    /**join????????????????????????????????????????????????????????????????????????joining????????????????????????
     * ?????????rtm???rtc*/
    private fun joinFailed(error: EduError, callback: EduCallback<EduUser>) {
        AgoraLog.i("$TAG->JoinClassRoom failed, code:${error.type},msg:${error.msg}")
        if (joining) {
            joining = false
            synchronized(joinSuccess) {
                joinSuccess = false
                clearData()
                callback.onFailure(error)
            }
        }
    }

    /**???????????????????????????RTM????????????????????????RTM*/
    override fun clearData() {
        AgoraLog.w("$TAG->Clean up local cached people and stream data")
        getCurUserList().clear()
        getCurStreamList().clear()
    }

    override fun getLocalUser(callback: EduCallback<EduUser>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getLocalUser error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(syncSession.localUser)
        }
    }

    override fun getRoomInfo(callback: EduCallback<EduRoomInfo>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getRoomInfo error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(syncSession.roomInfo)
        }
    }

    override fun getRoomStatus(callback: EduCallback<EduRoomStatus>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getRoomStatus error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(syncSession.roomStatus)
        }
    }

    override fun getStudentCount(callback: EduCallback<Int>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getStudentCount error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(getCurStudentList().size)
        }
    }

    override fun getTeacherCount(callback: EduCallback<Int>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getTeacherCount error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(getCurTeacherList().size)
        }
    }

    override fun getStudentList(callback: EduCallback<MutableList<EduUserInfo>>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getStudentList error:${error.msg}")
            callback.onFailure(error)
        } else {
            val studentList = mutableListOf<EduUserInfo>()
            for (element in getCurUserList()) {
                if (element.role == EduUserRole.STUDENT) {
                    studentList.add(element)
                }
            }
            callback.onSuccess(studentList)
        }
    }

    override fun getTeacherList(callback: EduCallback<MutableList<EduUserInfo>>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getTeacherList error:${error.msg}")
            callback.onFailure(error)
        } else {
            val teacherList = mutableListOf<EduUserInfo>()
            for (element in getCurUserList()) {
                if (element.role == EduUserRole.TEACHER) {
                    teacherList.add(element)
                }
            }
            callback.onSuccess(teacherList)
        }
    }

    override fun getFullStreamList(callback: EduCallback<MutableList<EduStreamInfo>>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getFullStreamList error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(syncSession.eduStreamInfoList)
        }
    }

    /**???????????????????????????????????????*/
    override fun getFullUserList(callback: EduCallback<MutableList<EduUserInfo>>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->EduRoom[${getCurRoomUuid()}] getFullUserList error:${error.msg}")
            callback.onFailure(error)
        } else {
            callback.onSuccess(syncSession.eduUserInfoList)
        }
    }

    /**?????????????????????????????????*/
    override fun leave(callback: EduCallback<Unit>) {
        if (!joinSuccess) {
            val error = notJoinedRoomError()
            AgoraLog.e("$TAG->Leave eduRoom[${getCurRoomUuid()}] error:${error.msg}")
            callback.onFailure(error)
        } else {
            AgoraLog.w("$TAG->Leave eduRoom[${getCurRoomUuid()}] success")
            clearData()
            if (!leaveRoom) {
                AgoraLog.w("$TAG->Ready to leave the RTE channel:${getCurRoomUuid()}")
                RteEngineImpl[getCurRoomUuid()]?.leave(object : RteCallback<Unit> {
                    override fun onSuccess(res: Unit?) {
                        Log.e(TAG, "Successfully left RTE channel")
                    }

                    override fun onFailure(error: RteError) {
                        Log.e(TAG, "Failed left RTE channel:code:${error.errorCode},msg:${error.errorDesc}")
                    }
                })
                leaveRoom = true
            }
            RteEngineImpl[getCurRoomUuid()]?.release()
            eventListener = null
            syncSession.localUser.eventListener = null
            studentJoinCallback = null
            (getCurLocalUser() as EduUserImpl).removeAllSurfaceView()
            /*???????????????room*/
            val rtn = EduManagerImpl.removeRoom(this)
            AgoraLog.w("$TAG->Remove this eduRoom from eduManager:$rtn")
            callback.onSuccess(Unit)
        }
    }

    override fun getRoomUuid(): String {
        return syncSession.roomInfo.roomUuid
    }

    override fun onChannelMsgReceived(p0: RtmMessage?, p1: RtmChannelMember?) {
        p0?.text?.let {
            val cmdResponseBody = Gson().fromJson<CMDResponseBody<Any>>(p0.text, object :
                    TypeToken<CMDResponseBody<Any>>() {}.type)

//            if(cmdResponseBody.cmd == 3) {
//                return
//            }

            val pair = syncSession.updateSequenceId(cmdResponseBody)
            if (pair != null) {
                /*count??????null,???????????????????????????*/
                syncSession.fetchLostSequence(pair.first, pair.second, object : EduCallback<Unit> {
                    override fun onSuccess(res: Unit?) {
                    }

                    override fun onFailure(error: EduError) {
                    }
                })
            }
        }
    }

    override fun onNetworkQuality(uid: Int, txQuality: Int, rxQuality: Int) {
        /*?????????????????????????????????;?????????????????????????????????*/
        val value = max(txQuality, rxQuality)
        val quality: NetworkQuality = Convert.convertNetworkQuality(value)
        eventListener?.onNetworkQualityChanged(quality, getCurLocalUser().userInfo, this)
    }
}
