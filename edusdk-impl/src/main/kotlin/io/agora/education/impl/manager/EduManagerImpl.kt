package io.agora.education.impl.manager

import android.os.Build
import android.text.TextUtils
import android.util.Base64
import com.google.gson.Gson
import io.agora.Constants.Companion.APPID
import io.agora.Constants.Companion.AgoraLog
import io.agora.Constants.Companion.LOGS_DIR_NAME
import io.agora.Constants.Companion.LOG_APPSECRET
import io.agora.base.callback.ThrowableCallback
import io.agora.base.network.BusinessException
import io.agora.education.api.BuildConfig
import io.agora.education.api.BuildConfig.API_BASE_URL
import io.agora.education.api.EduCallback
import io.agora.education.api.base.EduError
import io.agora.education.api.base.EduError.Companion.communicationError
import io.agora.education.api.base.EduError.Companion.httpError
import io.agora.education.api.logger.DebugItem
import io.agora.education.api.logger.LogLevel
import io.agora.education.api.manager.EduManager
import io.agora.education.api.manager.EduManagerOptions
import io.agora.education.api.room.EduRoom
import io.agora.education.api.room.data.*
import io.agora.education.api.statistics.AgoraError
import io.agora.education.api.util.CryptoUtil
import io.agora.education.impl.ResponseBody
import io.agora.education.impl.network.RetrofitManager
import io.agora.education.impl.room.EduRoomImpl
import io.agora.education.impl.room.data.EduRoomInfoImpl
import io.agora.education.impl.room.data.RtmConnectState
import io.agora.education.impl.room.data.response.EduLoginRes
import io.agora.education.impl.room.network.RoomService
import io.agora.education.impl.util.Convert
import io.agora.education.impl.util.UnCatchExceptionHandler
import io.agora.log.LogManager
import io.agora.log.UploadManager
import io.agora.rtc.RtcEngine
import io.agora.rte.RteCallback
import io.agora.rte.RteEngineImpl
import io.agora.rte.data.RteError
import io.agora.rte.listener.RteEngineEventListener
import io.agora.rtm.RtmMessage
import io.agora.rtm.RtmStatusCode
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File

internal class EduManagerImpl(
        options: EduManagerOptions
) : EduManager(options), RteEngineEventListener {

    companion object {
        private const val TAG = "EduManagerImpl"

        /**????????????EduRoom???????????????*/
        private val eduRooms = mutableListOf<EduRoom>()

        fun addRoom(eduRoom: EduRoom): Boolean {
            return eduRooms.add(eduRoom)
        }

        fun removeRoom(eduRoom: EduRoom): Boolean {
            return eduRooms.remove(eduRoom)
        }
    }

    /**?????????rtm????????????*/
    private val rtmConnectState = RtmConnectState()

    init {
        /*??????UnCatchExceptionHandler*/
        UnCatchExceptionHandler.getExceptionHandler().init(options.context.applicationContext)
        /*?????????LogManager*/
        options.logFileDir?.let {
            options.logFileDir = options.context.cacheDir.toString().plus(File.separatorChar).plus(LOGS_DIR_NAME)
        }
        LogManager.init(options.logFileDir!!, "AgoraEducation")
        AgoraLog = LogManager("SDK")
        logMessage("${TAG}: Init LogManager,log path is ${options.logFileDir}", LogLevel.INFO)
        logMessage("${TAG}: Init EduManagerImpl", LogLevel.INFO)
        logMessage("${TAG}: Init RteEngineImpl", LogLevel.INFO)
        RteEngineImpl.init(options.context, options.appId, options.logFileDir!!)
        /*???RteEngine??????eventListener*/
        RteEngineImpl.eventListener = this
        APPID = options.appId
        val auth = Base64.encodeToString("${options.customerId}:${options.customerCertificate}"
                .toByteArray(Charsets.UTF_8), Base64.DEFAULT).replace("\n", "").trim()
        RetrofitManager.instance()!!.addHeader("Authorization", CryptoUtil.getAuth(auth))
        RetrofitManager.instance()!!.setLogger(object : HttpLoggingInterceptor.Logger {
            override fun log(message: String) {
                /**OKHttp???log??????SDK???log??????*/
                logMessage(message, LogLevel.INFO)
            }
        })
        logMessage("${TAG}: Init of EduManagerImpl completed", LogLevel.INFO)
    }

    override fun createClassroom(config: RoomCreateOptions): EduRoom? {
        if (TextUtils.isEmpty(config.roomUuid) || TextUtils.isEmpty(config.roomName)) {
            return null
        }
        if (!RoomType.roomTypeIsValid(config.roomType)) {
            return null
        }
        val eduRoomInfo = EduRoomInfoImpl(config.roomType, config.roomUuid, config.roomName)
        val status = EduRoomStatus(EduRoomState.INIT, 0, true, 0)
        val room = EduRoomImpl(eduRoomInfo, status)
        /**?????????????????????*/
        room.defaultUserName = options.userName
        return room
    }

    fun login(userUuid: String, callback: EduCallback<Unit>) {
        logMessage("${TAG}: Calling the login API", LogLevel.INFO)
        RetrofitManager.instance()!!.getService(API_BASE_URL, RoomService::class.java)
                .login(APPID, userUuid)
                .enqueue(RetrofitManager.Callback(0, object : ThrowableCallback<ResponseBody<EduLoginRes>> {
                    override fun onSuccess(res: ResponseBody<EduLoginRes>?) {
                        logMessage("${TAG}: Successfully called the login API->${Gson().toJson(res)}", LogLevel.INFO)
                        val loginRes = res?.data
                        loginRes?.let {
                            RteEngineImpl.loginRtm(loginRes.userUuid, loginRes.rtmToken,
                                    object : RteCallback<Unit> {
                                        override fun onSuccess(res: Unit?) {
                                            logMessage("${TAG}: Login to RTM successfully", LogLevel.INFO)
                                            callback.onSuccess(res)
                                        }

                                        override fun onFailure(error: RteError) {
                                            logMessage("${TAG}: Login to RTM failed->code:${error.errorCode}," +
                                                    "reason:${error.errorDesc}", LogLevel.ERROR)
                                            callback.onFailure(communicationError(error.errorCode,
                                                    error.errorDesc))
                                        }
                                    })
                        }
                    }

                    override fun onFailure(throwable: Throwable?) {
                        var error = throwable as? BusinessException
                        error = error ?: BusinessException(throwable?.message)
                        error?.code?.let {
                            logMessage("${TAG}: Failed to call login interface->code:${error?.code}, reason:${error?.message
                                    ?: throwable?.message}", LogLevel.ERROR)
                            callback.onFailure(httpError(error?.code, error?.message
                                    ?: throwable?.message))
                        }
                    }
                }))
    }

    override fun release() {
        logMessage("${TAG}: Call release function to exit RTM and release data", LogLevel.INFO)
        RteEngineImpl.logoutRtm()
        eduRooms.clear()
    }

    override fun logMessage(message: String, level: LogLevel): EduError {
        when (level) {
            LogLevel.NONE -> {
                AgoraLog.d(message)
            }
            LogLevel.INFO -> {
                AgoraLog.i(message)
            }
            LogLevel.WARN -> {
                AgoraLog.w(message)
            }
            LogLevel.ERROR -> {
                AgoraLog.e(message)
            }
        }
        return EduError(AgoraError.NONE.value, "")
    }

    override fun uploadDebugItem(item: DebugItem, callback: EduCallback<String>): EduError {
        val uploadParam = UploadManager.UploadParam(BuildConfig.VERSION_NAME, Build.DEVICE,
                Build.VERSION.SDK, "ZIP", "Android", null)
        logMessage("${TAG}: Call the uploadDebugItem function to upload logs???parameter->${Gson().toJson(uploadParam)}", LogLevel.INFO)
        UploadManager.upload(options.context, APPID, API_BASE_URL, options.logFileDir!!, uploadParam,
                object : ThrowableCallback<String> {
                    override fun onSuccess(res: String?) {
                        res?.let {
                            logMessage("${TAG}: Log uploaded successfully->$res", LogLevel.INFO)
                            callback.onSuccess(res)
                        }
                    }

                    override fun onFailure(throwable: Throwable?) {
                        var error = throwable as? BusinessException
                        error = error ?: BusinessException(throwable?.message)
                        error?.code?.let {
                            logMessage("${TAG}: Log upload error->code:${error?.code}, reason:${error?.message
                                    ?: throwable?.message}", LogLevel.ERROR)
                            callback.onFailure(httpError(error?.code, error?.message
                                    ?: throwable?.message))
                        }
                    }
                })
        return EduError(-1, "")
    }

    override fun onConnectionStateChanged(p0: Int, p1: Int) {
        logMessage("${TAG}: The RTM connection state has changed->state:$p0,reason:$p1", LogLevel.INFO)
        /*?????????????????????????????????????????????*/
        eduRooms?.forEach {
            if (rtmConnectState.isReconnecting() &&
                    p0 == RtmStatusCode.ConnectionState.CONNECTION_STATE_CONNECTED) {
                logMessage("${TAG}: RTM disconnection and reconnected???Request missing sequences in classroom " +
                        "${(it as EduRoomImpl).getCurRoomUuid()}", LogLevel.INFO)
                it.syncSession.fetchLostSequence(object : EduCallback<Unit> {
                    override fun onSuccess(res: Unit?) {
                        /*????????????????????????????????????????????????????????????????????????????????????*/
                        it.eventListener?.onConnectionStateChanged(Convert.convertConnectionState(p0), it)
                    }

                    override fun onFailure(error: EduError) {
                        /*???????????????????????????????????????*/
                        it.syncSession.fetchLostSequence(this)
                    }
                })
            } else {
                it.eventListener?.onConnectionStateChanged(Convert.convertConnectionState(p0), it)
            }
        }
        rtmConnectState.lastConnectionState = p0
    }

    override fun onPeerMsgReceived(p0: RtmMessage?, p1: String?) {
        logMessage("${TAG}: PeerMessage has received->${Gson().toJson(p0)}", LogLevel.INFO)
        /**RTM??????peerMsg?????????,?????????????????????(seq???????????????)*/
        p0?.text?.let {
            eduRooms?.forEach {
                (it as EduRoomImpl).cmdDispatch.dispatchPeerMsg(p0.text, eduManagerEventListener)
            }
        }
    }
}
