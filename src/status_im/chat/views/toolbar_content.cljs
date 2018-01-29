(ns status-im.chat.views.toolbar-content
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [clojure.string :as string]
            [status-im.ui.components.react :as react]
            [status-im.i18n :as i18n]
            [status-im.chat.styles.screen :as styles.screen]
            [status-im.ui.components.chat-icon.screen :as chat-icon-screen]
            [status-im.utils.datetime :as time]
            [status-im.utils.gfycat.core :as gfycat]
            [status-im.constants :as constants]))

(defview chat-icon []
  (letsubs [{:keys [chat-id group-chat name color]} [:get-current-chat]]
    [chat-icon-screen/chat-icon-view-action chat-id group-chat name color true]))

(defn online-text [contact chat-id]
  (cond
    (= constants/console-chat-id chat-id) (i18n/label :t/available)
    contact (let [last-online      (get contact :last-online)
                  last-online-date (time/to-date last-online)
                  now-date         (time/now)]
              (if (and (pos? last-online)
                       (<= last-online-date now-date))
                (time/time-ago last-online-date)
                (i18n/label :t/active-unknown)))
    :else (i18n/label :t/active-unknown)))

(defn in-progress-text [{:keys [highestBlock currentBlock startBlock]}]
  (let [total      (- highestBlock startBlock)
        ready      (- currentBlock startBlock)
        percentage (if (zero? ready)
                     0
                     (->> (/ ready total)
                          (* 100)
                          (.round js/Math)))]

    (str (i18n/label :t/sync-in-progress) " " percentage "% " currentBlock)))

(defview last-activity-syncing [sync-state]
  (letsubs [state [:get :sync-data]]
    [react/text {:style styles.screen/last-activity-text}
     (case sync-state
       :in-progress (in-progress-text state)
       :synced (i18n/label :t/sync-synced)
       nil)]))

(defn individual-chat-last-activity [{:keys [online-text]}]
  [react/text {:style styles.screen/last-activity-text}
   online-text])

(defn private-group-last-activity [{:keys [contacts]}]
  [react/view {:flex-direction :row}
   [react/text {:style styles.screen/members}
    (let [cnt (inc (count contacts))]
      (i18n/label-pluralize cnt :t/members-active))]])

(defn public-channel-last-activity []
  [react/view {:flex-direction :row}
   [react/text (i18n/label :t/public-group-status)]])

(defn last-activity [{:keys [sync-state public? group-chat] :as params}]
  (if (or (= sync-state :in-progress)
          (= sync-state :synced))
    [last-activity-syncing {:sync-state sync-state}]
    (cond public?    (public-channel-last-activity)
          group-chat (private-group-last-activity params)
          :else      (individual-chat-last-activity params))))

(defview toolbar-content-view []
  (letsubs [group-chat    [:chat :group-chat]
            name          [:chat :name]
            chat-id       [:chat :chat-id]
            contacts      [:chat :contacts]
            public?       [:chat :public?]
            public-key    [:chat :public-key]
            accounts      [:get-accounts]
            contact       [:get-in [:contacts/contacts @chat-id]]
            sync-state    [:sync-state]
            creating?     [:get :accounts/creating-account?]]
    [react/view styles.screen/chat-toolbar-contents
     [chat-icon]
     [react/view (styles.screen/chat-name-view (or (empty? accounts)
                                                   creating?))
      (let [chat-name (if (string/blank? name)
                        (gfycat/generate-gfy public-key)
                        (or (i18n/get-contact-translated chat-id :name name)
                            (i18n/label :t/chat-name)))]
        [react/text {:style           styles.screen/chat-name-text
                     :number-of-lines 1
                     :font            :toolbar-title}
         (if public?
           (str "#" chat-name)
           chat-name)])
      [last-activity {:online-text (online-text contact chat-id)
                      :sync-state  sync-state
                      :contacts    contacts
                      :public?     public?
                      :group-chat  group-chat}]]]))
