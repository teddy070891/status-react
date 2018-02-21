(ns status-im.transport.message.v1.protocol
  (:require [status-im.transport.utils :as transport.utils]
            [status-im.transport.message-cache :as message-cache]
            [status-im.transport.db :as transport.db]
            [status-im.transport.core :as transport]
            [status-im.transport.message.core :as message]))

(def ttl 10000) ;; ttl of 10 sec

(defn init-chat [db chat-id]
  (assoc-in db [:transport/chats chat-id] (transport.db/create-chat (transport.utils/get-topic chat-id))))

(defn is-new? [message-id]
  (when-not (message-cache/exists? message-id)
    (message-cache/add! message-id)))

(defn requires-ack [db message-id chat-id]
  (update-in db [:transport/chats chat-id :pending-ack] conj message-id))

(defn ack [db message-id chat-id]
  (update-in db [:transport/chats chat-id :ack] conj message-id))

(defn send [{:keys [db] :as cofx} {:keys [payload chat-id]}]
  ;; we assume that the chat contains the contact public-key
  (let [{:accounts/keys [account]} db
        {:keys [identity]} account
        {:keys [sym-key-id topic]} (get-in db [:transport chat-id])]
    {:shh/post {:web3    (:web3 db)
                :message {:sig identity
                          :symKeyID sym-key-id
                          :ttl ttl
                          :powTarget 0.001
                          :powTime 1
                          :payload  payload
                          :topic topic}}}))

(defn send-with-pubkey [{:keys [db] :as cofx} {:keys [payload chat-id]}]
  (let [{:accounts/keys [account]} db
        {:keys [identity]} account
        {:keys [sym-key-id topic]} (get-in db [:transport chat-id])]
    {:shh/post {:web3    (:web3 db)
                :message {:sig identity
                          :pubKey chat-id
                          :ttl ttl
                          :powTarget 0.001
                          :powTime 1
                          :payload  payload
                          :topic topic}}}))

(defrecord Ack [message-ids]
  message/StatusMessage
  (send [this cofx chat-id])
  (receive [this db chat-id sig]))

(defrecord Seen [message-ids]
  message/StatusMessage
  (send [this cofx chat-id])
  (receive [this cofx chat-id sig]))
