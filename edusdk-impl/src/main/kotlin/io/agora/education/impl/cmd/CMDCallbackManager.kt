package io.agora.education.impl.cmd

import io.agora.Constants
import io.agora.education.api.manager.listener.EduManagerEventListener
import io.agora.education.api.message.AgoraActionMessage
import io.agora.education.api.message.EduChatMsg
import io.agora.education.api.message.EduMsg
import io.agora.education.api.room.EduRoom
import io.agora.education.api.room.data.EduRoomChangeType
import io.agora.education.api.stream.data.EduStreamEvent
import io.agora.education.api.user.EduUser
import io.agora.education.api.user.data.EduUserEvent
import io.agora.education.api.user.data.EduUserInfo
import io.agora.education.api.user.data.EduUserLeftType
import io.agora.education.api.user.data.EduUserStateChangeType

internal class CMDCallbackManager {

    fun onRoomStatusChanged(eventEdu: EduRoomChangeType, operatorUser: EduUserInfo?, classRoom: EduRoom) {
        classRoom.eventListener?.onRoomStatusChanged(eventEdu, operatorUser, classRoom)
    }

    fun onRoomPropertyChanged(classRoom: EduRoom, cause: MutableMap<String, Any>?) {
        classRoom.eventListener?.onRoomPropertiesChanged(classRoom, cause)
    }

    fun onRoomChatMessageReceived(chatMsg: EduChatMsg, classRoom: EduRoom) {
        classRoom.eventListener?.onRoomChatMessageReceived(chatMsg, classRoom)
    }

    fun onRoomMessageReceived(message: EduMsg, classRoom: EduRoom) {
        classRoom.eventListener?.onRoomMessageReceived(message, classRoom)
    }

    fun onRemoteUsersJoined(users: List<EduUserInfo>, classRoom: EduRoom) {
        classRoom.eventListener?.onRemoteUsersJoined(users, classRoom)
    }

    fun onRemoteStreamsAdded(streamEvents: MutableList<EduStreamEvent>, classRoom: EduRoom) {
        classRoom.eventListener?.onRemoteStreamsAdded(streamEvents, classRoom)
    }

    fun onRemoteUsersLeft(userEvents: MutableList<EduUserEvent>, classRoom: EduRoom) {
        userEvents.forEach {
            classRoom.eventListener?.onRemoteUserLeft(it, classRoom)
        }
    }

    fun onRemoteStreamsRemoved(streamEvents: MutableList<EduStreamEvent>, classRoom: EduRoom) {
        classRoom.eventListener?.onRemoteStreamsRemoved(streamEvents, classRoom)
    }

    fun onRemoteStreamsUpdated(streamEvents: MutableList<EduStreamEvent>, classRoom: EduRoom) {
        classRoom.eventListener?.onRemoteStreamUpdated(streamEvents, classRoom)
    }

    fun onRemoteUserUpdated(userEvent: EduUserEvent, type: EduUserStateChangeType, classRoom: EduRoom) {
        classRoom.eventListener?.onRemoteUserUpdated(userEvent, type, classRoom)
    }

    fun onLocalUserAdded(userInfo: EduUserInfo, eduUser: EduUser) {
        /**???????????????online???????????????????????????????????????*/
    }

    fun onLocalUserUpdated(userEvent: EduUserEvent, type: EduUserStateChangeType, eduUser: EduUser) {
        eduUser.eventListener?.onLocalUserUpdated(userEvent, type)
    }

    fun onLocalUserRemoved(userEvent: EduUserEvent, eduUser: EduUser, type: Int) {
        /**???????????????offline???????????????????????????????????????EduUserEventListener?????????
         * onLocalUserLeft????????????????????????(?????????????????????)*/
        if (type == 2) {
            Constants.AgoraLog.i("Local User was removed from classroom by teacher!")
        }
        eduUser.eventListener?.onLocalUserLeft(userEvent, if (type == 1) EduUserLeftType.Normal else EduUserLeftType.KickOff)
    }

    fun onLocalStreamAdded(streamEvent: EduStreamEvent, eduUser: EduUser) {
        eduUser.eventListener?.onLocalStreamAdded(streamEvent)
    }

    fun onLocalStreamUpdated(streamEvent: EduStreamEvent, eduUser: EduUser) {
        eduUser.eventListener?.onLocalStreamUpdated(streamEvent)
    }

    fun onLocalStreamRemoved(streamEvent: EduStreamEvent, eduUser: EduUser) {
        eduUser.eventListener?.onLocalStreamRemoved(streamEvent)
    }


    fun onUserChatMessageReceived(chatMsg: EduChatMsg, listener: EduManagerEventListener?) {
        listener?.onUserChatMessageReceived(chatMsg)
    }

    fun onUserMessageReceived(message: EduMsg, listener: EduManagerEventListener?) {
        listener?.onUserMessageReceived(message)
    }

    fun onUserActionMessageReceived(actionMsg: AgoraActionMessage, listener: EduManagerEventListener?) {
        listener?.onUserActionMessageReceived(actionMsg)
    }
}