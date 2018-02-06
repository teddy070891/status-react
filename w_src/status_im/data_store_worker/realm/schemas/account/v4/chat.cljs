(ns status-im.data-store-worker.realm.schemas.account.v4.chat
  (:require [taoensso.timbre :as log]))

(def schema {:name       :chat
             :primaryKey :chat-id
             :properties {:chat-id          :string
                          :name             :string
                          :color            {:type    :string
                                             :default "#a187d5"}
                          :group-chat       {:type    :bool
                                             :indexed true}
                          :group-admin      {:type     :string
                                             :optional true}
                          :is-active        :bool
                          :timestamp        :int
                          :contacts         {:type       :list
                                             :objectType :chat-contact}
                          :removed-at       {:type     :int
                                             :optional true}
                          :removed-from-at  {:type     :int
                                             :optional true}
                          :added-to-at      {:type     :int
                                             :optional true}
                          :updated-at       {:type     :int
                                             :optional true}
                          :last-message-id  :string
                          :message-overhead {:type    :int
                                             :default 0}
                          :public-key       {:type     :string
                                             :optional true}
                          :private-key      {:type     :string
                                             :optional true}
                          :contact-info     {:type     :string
                                             :optional true}
                          :debug?           {:type    :bool
                                             :default false}
                          :public?          {:type    :bool
                                             :default false}}})

(defn migration [old-realm new-realm]
  (log/debug "migrating chat schema v4"))