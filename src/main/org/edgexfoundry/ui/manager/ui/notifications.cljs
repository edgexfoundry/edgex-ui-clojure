;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.notifications
  (:require [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [cljs-time.core :as tc]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [fulcro.client.data-fetch :as df]))

(defsc NotificationSearch [this {:keys [from-dtp to-dtp]}]
  {:initial-state (fn [p] {:from-dtp (prim/get-initial-state dtp/DateTimePicker {:id :notification-dtp-from
                                                                                 :time (tc/yesterday)
                                                                                 :float-left true})
                           :to-dtp (prim/get-initial-state dtp/DateTimePicker {:id :notification-dtp-to
                                                                               :time (-> (tc/today-at-midnight)
                                                                                         (tc/plus (tc/days 1)))
                                                                               :float-left true})})
   :ident         (fn [] [:notification-search :singleton])
   :query         [{:from-dtp (prim/get-query dtp/DateTimePicker)}
                   {:to-dtp (prim/get-query dtp/DateTimePicker)}]}
  (dom/div #js {}
           (dtp/date-time-picker from-dtp)
           (dom/div #js {:style #js {:float "right" :margin "0 2px" :position "relative"}}
                    (dtp/date-time-picker to-dtp))))

(declare show-notifications)

(deftable NotificationList :show-notifications :notification [[:slug "Slug"]
                                                              [:created "Created" #(co/conv-time %2)]
                                                              [:content "Content"]
                                                              [:severity "Severity"]]
  [{:onClick #(show-notifications this) :icon "refresh"}] :search-keyword :slug :search {:comp NotificationSearch})

(defmutation load-notifications [none]
  (action [{:keys [reconciler state] :as env}]
          (let [start (get-in @state [:date-time-picker :notification-dtp-from :time])
                end (get-in @state [:date-time-picker :notification-dtp-to :time])]
            (df/load reconciler co/notifications-list-ident NotificationList {:params {:start start :end end}
                                                                              :fallback `d/show-error})))
  (remote [env]
          (df/remote-load env)))

(defn show-notifications [this]
  (prim/transact! this `[(load-notifications {})]))
