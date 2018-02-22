(ns status-im.transport.message.v1.contact
  (:require [status-im.utils.handlers :as handlers]
            [status-im.transport.message.core :as message]
            [status-im.transport.message.v1.protocol :as protocol]
            [status-im.transport.utils :as transport.utils]))

(defrecord NewContactKey [sym-key message]
  message/StatusMessage
  (send [this chat-id cofx]
    (protocol/send-with-pubkey {:chat-id chat-id
                                :payload this}
                               cofx))
  (receive [this chat-id signature cofx] 
    (handlers/reduce-effects cofx
                             (partial protocol/init-chat chat-id)
                             (constantly {:shh/add-new-sym-key {:web3 (get-in cofx [:db :web3])
                                                                :sym-key sym-key
                                                                :chat-id chat-id
                                                                :message message
                                                                :success-event ::add-new-sym-key}}))))

(defrecord ContactRequest [name profile-image address fcm-token]
  message/StatusMessage
  (send [this chat-id cofx]
    (let [message-id (transport.utils/message-id this)]
      (handlers/reduce-effects cofx
                               (partial protocol/init-chat chat-id)
                               (partial protocol/requires-ack message-id chat-id)
                               (constantly {:shh/get-new-sym-key {:web3 (get-in cofx [:db :web3])
                                                                  :chat-id chat-id
                                                                  :message this
                                                                  :success-event ::send-new-sym-key}}))))
  (receive [this chat-id signature cofx]
    (let [message-id (transport.utils/message-id this)]
      (when (protocol/is-new? message-id)
        (handlers/reduce-effects cofx
                                 (partial protocol/ack message-id chat-id)
                                 (partial message/receive-contact-request signature this))))))

(defrecord ContactRequestConfirmed [name profile-image address fcm-token]
  message/StatusMessage
  (send [this chat-id cofx]
    (let [message-id (transport.utils/message-id this)]
      (handlers/reduce-effects cofx
                               (partial protocol/requires-ack message-id chat-id)
                               (partial protocol/send {:chat-id chat-id
                                                       :payload this}))))
  (receive [this chat-id signature cofx]
    (let [message-id (transport.utils/message-id this)]
      (when (protocol/is-new? message-id)
        (handlers/reduce-effects cofx
                                 (partial protocol/ack message-id chat-id)
                                 (partial message/receive-contact-request-confirmation signature this))))))

(defrecord ContactMessage [content]
  message/StatusMessage
  (send [this chat-id {:keys [db] :as cofx}]
    (protocol/send {:chat-id chat-id
                    :payload this}
                   cofx))
  (receive [this chat-id signature cofx]
    {:dispatch [:pre-received-message (assoc content
                                             :chat-id chat-id
                                             :from    signature)]}))

(handlers/register-handler-fx
  ::send-new-sym-key
  (fn [{:keys [db] :as cofx} [_ {:keys [chat-id message sym-key sym-key-id]}]]
    (let [{:keys [web3 current-public-key]} db]
      (handlers/reduce-effects cofx
                               (constantly {:db (assoc-in db [:transport/chats chat-id :sym-key-id] sym-key-id)
                                            :shh/add-filter {:web3 web3
                                                             :sym-key-id sym-key-id
                                                             :topic (transport.utils/get-topic current-public-key)
                                                             :chat-id chat-id}})
                               (partial message/send (NewContactKey. sym-key message) chat-id)))))

(handlers/register-handler-fx
  ::add-new-sym-key
  (fn [{:keys [db] :as cofx} [_ {:keys [sym-key-id chat-id message]}]]
    (let [{:keys [web3 current-public-key]} db]
      (handlers/reduce-effects cofx
                               (constantly {:db (assoc-in db [:transport/chats chat-id :sym-key-id] sym-key-id)
                                            :shh/add-filter {:web3 web3
                                                             :sym-key-id sym-key-id
                                                             :topic (transport.utils/get-topic current-public-key)
                                                             :chat-id chat-id}})
                               (partial message/receive message chat-id chat-id)))))
