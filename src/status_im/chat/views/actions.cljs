(ns status-im.chat.views.actions
  (:require [re-frame.core :as re-frame]
            [status-im.i18n :as i18n]))

(defn direct-chat-actions [chat-id]
  [{:label  (i18n/label :t/profile)
    :action #(re-frame/dispatch [:show-profile chat-id])}
   {:label  (i18n/label :t/delete-chat)
    :action #(re-frame/dispatch [:remove-chat-and-navigate-home chat-id])}])

(defn group-chat-actions []
  [{:label  (i18n/label :t/settings)
    :action #(re-frame/dispatch [:show-group-chat-settings])}
   {:label  (i18n/label :t/leave-group-chat)
    :action #(re-frame/dispatch [:leave-group-chat])}])

(defn public-chat-actions [chat-id]
  [{:label  (i18n/label :t/leave-public-chat)
    :action #(re-frame/dispatch [:remove-chat-and-navigate-home chat-id])}])

(defn chat-actions [chat-id group-chat public?]
  (cond
    public?    (public-chat-actions chat-id)
    group-chat (group-chat-actions)
    :else      (direct-chat-actions chat-id)))


