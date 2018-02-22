(ns status-im.ui.screens.profile.group-chat.styles
  (:require [status-im.ui.components.colors :as colors]
            [status-im.ui.components.styles :as components.styles]))

(def action-container
  {:background-color colors/white
   :padding-top      24})

(def action
  {:background-color colors/blue-transparent
   :border-radius    50})

(def action-label
  {:color colors/blue})

(def action-separator
  {:height           1
   :background-color colors/white-light-transparent
   :margin-left      70})

(def action-icon-opts
  {:color colors/blue})