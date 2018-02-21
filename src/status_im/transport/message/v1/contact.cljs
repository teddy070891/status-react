(ns status-im.transport.message.v1.contact
  (:require [status-im.utils.handlers :as handlers]
            [status-im.transport.message.core :as message]
            [status-im.transport.message.v1.protocol :as protocol]))

(defrecord NewContactKey [sym-key message]
  message/StatusMessage
  (send [this {:keys [db] :as cofx} chat-id]
    (protocol/send-with-pubkey db {:web3    (:web3 db)
                                   :chat-id chat-id
                                   :payload this}))
  (receive [this {:keys [db]} chat-id signature]
    (let [{:keys [web3]} db]
      {:shh/add-new-sym-key {:web3       web3
                             :sym-key    sym-key
                             :chat-id    chat-id
                             :message    message
                             :success-event ::add-new-sym-key}})))

(defrecord ContactRequest [name profile-image address fcm-token]
  message/StatusMessage
  (send [this {:keys [db] :as cofx} chat-id]
    (let [{:keys [web3]} db
          message-id (protocol/message-id this)
          new-db (-> (protocol/init-chat db chat-id)
                     (protocol/requires-ack message-id chat-id))]
      {:db db
       :shh/get-new-sym-key {:web3 web3
                             :chat-id chat-id
                             :message this
                             :success-event ::send-new-sym-key}}))
  (receive [this {:keys [db] :as cofx} chat-id signature]
    (let [message-id (protocol/message-id this)]
      (when (protocol/is-new? message-id) 
        (message/receive-contact-request (assoc cofx :db (protocol/ack db message-id chat-id))
                                         signature
                                         this)))))

(defrecord ContactRequestConfirmed [name profile-image address fcm-token]
  message/StatusMessage
  (send [this {:keys [db] :as cofx} chat-id]
    (let [message-id (protocol/message-id this)] 
      (merge {:db (protocol/requires-ack db message-id chat-id)}
             (protocol/send this))))
  (receive [this {:keys [db] :as  cofx} chat-id signature]
    (let [message-id (protocol/message-id this)]
      (when (protocol/is-new? message-id) 
        (message/receive-contact-request-confirmation (assoc cofx :db (-> (protocol/init-chat db chat-id)
                                                                          (protocol/ack message-id chat-id)))
                                                      signature
                                                      this)))))

(defrecord ContactMessage [content]
  message/StatusMessage
  (send [this cofx chat-id]
    (protocol/send cofx this))
  (receive [this cofx chat-id signature]
    {:dispatch [:pre-received-message (assoc content
                                             :chat-id chat-id
                                             :from    signature)]}))

(handlers/register-handler-fx
  ::send-new-sym-key
  (fn [{:keys [db] :as cofx} [_ {:keys [chat-id message sym-key sym-key-id]}]] 
    (message/send (NewContactKey. sym-key message)
                  (assoc-in cofx [:db :transport/chats chat-id :sym-key-id] sym-key-id)
                  chat-id)))

(handlers/register-handler-fx
  ::add-new-sym-key
  (fn [{:keys [db] :as cofx} [_ {:keys [sym-key-id chat-id message]}]] 
    (message/receive message
                     (assoc-in cofx [:db :transport/chats chat-id :sym-key-id] sym-key-id)
                     chat-id
                     chat-id)))
