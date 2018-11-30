;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.logging
  (:require [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [fulcro.client.primitives :refer [defsc]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [cljs-time.core :as tc]
            [fulcro.client.primitives :as prim]))

(defsc LogSearch [this {:keys [from-dtp to-dtp]}]
  {:initial-state (fn [p] {:from-dtp (prim/get-initial-state dtp/DateTimePicker {:id :log-dtp-from
                                                                                 :time (-> (tc/time-now)
                                                                                           (tc/minus (tc/hours 1)))
                                                                                 :float-left true})
                           :to-dtp (prim/get-initial-state dtp/DateTimePicker {:id :log-dtp-to
                                                                               :time (-> (tc/today-at-midnight)
                                                                                         (tc/plus (tc/days 1)))
                                                                               :float-left true})})
   :ident         (fn [] [:log-search :singleton])
   :query         [{:from-dtp (prim/get-query dtp/DateTimePicker)}
                   {:to-dtp (prim/get-query dtp/DateTimePicker)}]}
  (dom/div #js {}
           (dtp/date-time-picker from-dtp)
           (dom/div #js {:style #js {:float "right" :margin "0 2px" :position "relative"}}
                    (dtp/date-time-picker to-dtp))))

(declare show-logs)

(deftable LogEntryList :show-logs :log-entry [[:created "Created" #(co/conv-time %2)] [:originService "Service"]
                                              [:logLevel "Level"] [:message "Message"]]
  [{:onClick #(show-logs this) :icon "refresh"}] :search-keyword :message
  :search {:comp LogSearch})

(defmutation load-logs [none]
  (action [{:keys [reconciler state] :as env}]
          (let [start (get-in @state [:date-time-picker :log-dtp-from :time])
                end (get-in @state [:date-time-picker :log-dtp-to :time])]
            (df/load-action env co/log-entry-list-ident LogEntryList {:params {:start start :end end}
                                                                      :fallback `d/show-error})))
  (remote [env]
          (df/remote-load env)))

(defn show-logs [this]
  (prim/transact! this `[(load-logs {})]))

