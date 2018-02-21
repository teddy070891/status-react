(ns status-im.chat.handlers
  (:require [re-frame.core :refer [after dispatch reg-fx]]
            [clojure.string :as string]
            [status-im.ui.components.styles :refer [default-chat-color]]
            [status-im.chat.constants :as chat-consts]
            [status-im.data-store.chats :as chats]
            [status-im.data-store.messages :as messages]
            [status-im.constants :refer [text-content-type
                                         content-type-command
                                         content-type-command-request
                                         console-chat-id]]
            [status-im.utils.random :as random]
            [status-im.utils.handlers :refer [register-handler register-handler-fx] :as u]
            status-im.chat.events))

(register-handler
  :leave-group-chat
  ;; todo order of operations tbd
  (after (fn [_ _] (dispatch [:navigation-replace :home])))
  (u/side-effect!
   (fn [{:keys [web3 current-chat-id chats current-public-key]} _]
     #_(let [{:keys [public-key private-key public?]} (chats current-chat-id)]
         (protocol/stop-watching-group!
          {:web3     web3
           :group-id current-chat-id})
         (when-not public?
           (protocol/leave-group-chat!
            {:web3     web3
             :group-id current-chat-id
             :keypair  {:public  public-key
                        :private private-key}
             :message  {:from       current-public-key
                        :message-id (random/id)}})))
     (dispatch [:remove-chat current-chat-id]))))

(register-handler :update-group-message
                  (u/side-effect!
                   (fn [{:keys [current-public-key web3 chats]}
                        [_ {:keys                                [from]
                            {:keys [group-id keypair timestamp]} :payload}]]
                     #_(let [{:keys [private public]} keypair]
                         (let [is-active (chats/is-active? group-id)
                               chat      {:chat-id     group-id
                                          :public-key  public
                                          :private-key private
                                          :updated-at  timestamp}]
                           (when (and (= from (get-in chats [group-id :group-admin]))
                                      (or (not (chats/exists? group-id))
                                          (chats/new-update? timestamp group-id)))
                             (dispatch [:update-chat! chat])
                             (when is-active
                               (protocol/start-watching-group!
                                {:web3     web3
                                 :group-id group-id
                                 :identity current-public-key
                                 :keypair  keypair
                                 :callback #(dispatch [:incoming-message %1 %2])}))))))))

(reg-fx
  ::start-watching-group
  (fn [{:keys [group-id web3 current-public-key keypair]}]
    #_(protocol/start-watching-group!
       {:web3     web3
        :group-id group-id
        :identity current-public-key
        :keypair  keypair
        :callback #(dispatch [:incoming-message %1 %2])})))

(register-handler-fx
  :create-new-public-chat
  (fn [{:keys [db]} [_ topic]]
    (let [exists? (boolean (get-in db [:chats topic]))
          chat    {:chat-id     topic
                   :name        topic
                   :color       default-chat-color
                   :group-chat  true
                   :public?     true
                   :is-active   true
                   :timestamp   (random/timestamp)}]
      (merge
       (when-not exists?
         {:db (assoc-in db [:chats (:chat-id chat)] chat)
          :save-chat chat
          ::start-watching-group (merge {:group-id topic}
                                        (select-keys db [:web3 :current-public-key]))})
       {:dispatch-n [[:navigate-to-clean :home]
                     [:navigate-to-chat topic]]}))))

(reg-fx
  ::start-listen-group
  (fn [{:keys [new-chat web3 current-public-key]}]
    #_(let [{:keys [chat-id public-key private-key contacts name]} new-chat
            identities (mapv :identity contacts)]
        (protocol/invite-to-group!
         {:web3       web3
          :group      {:id       chat-id
                       :name     name
                       :contacts (conj identities current-public-key)
                       :admin    current-public-key
                       :keypair  {:public  public-key
                                  :private private-key}}
          :identities identities
          :message    {:from       current-public-key
                       :message-id (random/id)}})
        (protocol/start-watching-group!
         {:web3     web3
          :group-id chat-id
          :identity current-public-key
          :keypair  {:public  public-key
                     :private private-key}
          :callback #(dispatch [:incoming-message %1 %2])}))))

(defn group-name-from-contacts [contacts selected-contacts username]
  (->> (select-keys contacts selected-contacts)
       vals
       (map :name)
       (cons username)
       (string/join ", ")))

(defn prepare-group-chat
  [{:keys [current-public-key username]
    :group/keys [selected-contacts]
    :contacts/keys [contacts]} group-name]
  #_(let [selected-contacts'  (mapv #(hash-map :identity %) selected-contacts)
          chat-name (if-not (string/blank? group-name)
                      group-name
                      (group-name-from-contacts contacts selected-contacts username))
          {:keys [public private]} (protocol/new-keypair!)]
      {:chat-id     (random/id)
       :public-key  public
       :private-key private
       :name        chat-name
       :color       default-chat-color
       :group-chat  true
       :group-admin current-public-key
       :is-active   true
       :timestamp   (random/timestamp)
       :contacts    selected-contacts'}))

(register-handler-fx
  :create-new-group-chat-and-open
  (fn [{:keys [db]} [_ group-name]]
    (let [new-chat (prepare-group-chat (select-keys db [:group/selected-contacts :current-public-key :username
                                                        :contacts/contacts])
                                       group-name)]
      {:db (-> db
               (assoc-in [:chats (:chat-id new-chat)] new-chat)
               (assoc :group/selected-contacts #{}))
       :save-chat new-chat
       ::start-listen-group (merge {:new-chat new-chat}
                                   (select-keys db [:web3 :current-public-key]))
       :dispatch-n [[:navigate-to-clean :home]
                    [:navigate-to-chat (:chat-id new-chat)]]})))

(register-handler-fx
  :group-chat-invite-received
  (fn [{{:keys [current-public-key] :as db} :db}
       [_ {:keys                                                    [from]
           {:keys [group-id group-name contacts keypair timestamp]} :payload}]]
    (let [{:keys [private public]} keypair]
      (let [contacts' (keep (fn [ident]
                              (when (not= ident current-public-key)
                                {:identity ident})) contacts)
            chat      (get-in db [:chats group-id])
            new-chat  {:chat-id     group-id
                       :name        group-name
                       :group-chat  true
                       :group-admin from
                       :public-key  public
                       :private-key private
                       :contacts    contacts'
                       :added-to-at timestamp
                       :timestamp   timestamp
                       :is-active   true}]
        (when (or (nil? chat)
                  (chat/new-update? chat timestamp))
          {::start-watching-group (merge {:group-id group-id
                                          :keypair keypair}
                                         (select-keys db [:web3 :current-public-key]))
           :dispatch (if chat
                       [:update-chat! new-chat]
                       [:add-chat group-id new-chat])})))))

(register-handler-fx
  :show-profile
  (fn [{db :db} [_ identity]]
    {:db (assoc db :contacts/identity identity)
     :dispatch [:navigate-forget :profile]}))
