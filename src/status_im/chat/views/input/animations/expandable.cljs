(ns status-im.chat.views.input.animations.expandable
  (:require-macros [status-im.utils.views :refer [defview letsubs]])
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [status-im.ui.components.animation :as animation]
            [status-im.ui.components.react :as react]
            [status-im.chat.views.input.animations.responder :as responder]
            [status-im.chat.views.input.utils :as input-utils]
            [status-im.chat.styles.animations :as style]
            [status-im.chat.styles.input.input :as input-style]
            [taoensso.timbre :as log]))

(defn expandable-view-on-update [{:keys [anim-value to-changed-height chat-input-margin height]}]
  (let [to-default-height (re-frame/subscribe [:get-default-container-area-height])
        layout-height     (re-frame/subscribe [:get :layout-height])]
    (fn [component]
      ;; we're going to change the height here

      ;; by default the height can be modified by dispatching :set-expandable-height,
      ;; but there is also a way to make the height adjusted automatically — in this
      ;; case you just need to set :dynamic-height? to true
      (let [{:keys [dynamic-height?] dynamic-height :height} (reagent/props component)
            to-changed-height (if dynamic-height?
                                dynamic-height
                                @to-changed-height)
            to-value          (or to-changed-height height @to-default-height)]
        (re-frame/dispatch [:set :expandable-view-height-to-value to-value])
        (animation/start
          (animation/spring anim-value {:toValue  to-value
                                        :friction 10
                                        :tension  60}))))))

(defn expandable-view [{:keys [key height hide-overlay?]} & _]
  (let [anim-value        (animation/create-value 0)
        input-height      (re-frame/subscribe [:get-current-chat-ui-prop :input-height])
        fullscreen?       (re-frame/subscribe [:get-current-chat-ui-prop :fullscreen?])
        chat-input-margin (re-frame/subscribe [:chat-input-margin])
        to-changed-height (re-frame/subscribe [:chat-animations key :height])
        changes-counter   (re-frame/subscribe [:chat-animations key :changes-counter])
        on-update         (expandable-view-on-update {:anim-value        anim-value
                                                      :to-changed-height to-changed-height
                                                      :chat-input-margin chat-input-margin
                                                      :height            height})]
    (reagent/create-class
      {:component-did-mount
       on-update
       :component-did-update
       on-update
       :component-will-unmount
       (fn []
         (re-frame/dispatch [:set-chat-ui-props {:fullscreen? false}])
         (re-frame/dispatch [:set-expandable-height key height]))
       :display-name
       "expandable-view"
       :reagent-render
       (fn [{:keys [custom-header]} & elements]
         @to-changed-height @changes-counter
         (let [input-height (or @input-height (+ input-style/padding-vertical
                                                 input-style/min-input-height
                                                 input-style/padding-vertical
                                                 input-style/border-height))
               bottom       (+ input-height @chat-input-margin)]
           [react/view style/overlap-container
            (into [react/animated-view {:style (style/expandable-container anim-value bottom)}]
                  elements)]))})))
