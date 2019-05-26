;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.transmissions
  (:require [cljs-time.core :as tc]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.table :refer [deftable]]))

(declare show-subscriptions)
(declare conv-category)
(declare conv-sev)

(defsc TransmissionSearch [this {:keys [from-dtp to-dtp]}]
  {:initial-state (fn [p] {:from-dtp (prim/get-initial-state dtp/DateTimePicker {:id :transmission-dtp-from
                                                                                 :time (tc/yesterday)
                                                                                 :float-left true})
                           :to-dtp (prim/get-initial-state dtp/DateTimePicker {:id :transmission-dtp-to
                                                                               :time (-> (tc/today-at-midnight)
                                                                                         (tc/plus (tc/days 1)))
                                                                               :float-left true})})
   :ident         (fn [] [:transmission-search :singleton])
   :query         [{:from-dtp (prim/get-query dtp/DateTimePicker)}
                   {:to-dtp (prim/get-query dtp/DateTimePicker)}]}
  (dom/div #js {}
           (dtp/date-time-picker from-dtp)
           (dom/div #js {:style #js {:float "right" :margin "0 2px" :position "relative"}}
                    (dtp/date-time-picker to-dtp))))

(defn show-transmissions [this]
  (prim/transact! this `[(load-transmissions {})]))

(deftable TransmissionList :show-transmissions :transmission [[:notification "Notification Slug" co/conv-notify-slug]
                                                              [:created "Created" #(co/conv-time %2)]
                                                              [:receiver "Receiver"]
                                                              [:status "Status" co/conv-tran-status]
                                                              [:resendcount "Resend Count"]]
  [{:onClick #(show-transmissions this) :icon "refresh"}]
    :search-keyword [:notification :slug] :search {:comp TransmissionSearch})

(defmutation load-transmissions [args]
  (action [{:keys [reconciler state] :as env}]
          (let [start (get-in @state [:date-time-picker :transmission-dtp-from :time])
                end (get-in @state [:date-time-picker :transmission-dtp-to :time])
                slug (:slug args)]
            (ld/load reconciler co/transmissions-list-ident TransmissionList {:params {:start start :end end :slug slug}})))
  (remote [env]
          (df/remote-load env)))

(defn conv-category [_ category]
  (case category
    :SECURITY "Security"
    :HW_HEALTH "Hardware Health"
    :SW_HEALTH "Software Health"
    "Unknown"))

(defn conv-sev [_ sev]
  (case sev
    :CRITICAL "Security"
    :NORMAL "Normal"
    "Unknown"))
