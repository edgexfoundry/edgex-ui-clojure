;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.notifications
  (:require [cljs-time.core :as tc]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.routing :as r]
            [fulcro.i18n :refer [tr]]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.form-state :as fs]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.routing :as ro]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.transmissions :as trn]
            ["react-tag-box" :refer [TagBox]]
            ["react-widgets" :refer [SelectList]]))

(defn reset-notification* [state]
  (update-in state co/new-notification-ident #(merge % {:id nil})))

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

(declare NotificationModal)

(defn prepare-notification* [state]
  (let [ref co/new-notification-ident]
    (-> state
        (fs/add-form-config* NotificationModal ref)
        (reset-notification*))))

(defmutation prepare-notification
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (prepare-notification* s)))))

(defn show-notification-modal [comp]
  (prim/transact! comp `[(prepare-notification)
                         (r/set-route {:router :root/modal-router :target ~co/new-notification-ident})
                         (b/show-modal {:id :notification-modal})
                         :modal-router]))

(defn add-notification [comp {:keys [notify/slug description sender category severity content labels] :as form}]
  (let [tmp-id (prim/tempid)
        notifyObj {:tempid tmp-id
                   :slug slug
                   :description description
                   :sender sender
                   :category category
                   :severity severity
                   :content content
                   :labels labels}]
        (prim/transact! comp `[(b/hide-modal {:id :notification-modal})
                               (mu/add-notification ~notifyObj)
                               (fs/reset-form!)
                               (fs/clear-complete!)
                               (df/fallback {:action ld/reset-error})])))

(defn do-delete-notification [this id row]
  (let [slug (->> row (:content) (filterv #(= (:id %) id)) (first) (:slug))]
    (prim/transact! this `[(mu/delete-notification {:id ~id :slug ~slug})
                           (t/reset-table-page {:id :show-notifications})
                           (df/fallback {:action ld/reset-error})])))

(def ui-tag-box (co/factory-apply TagBox))

(def ui-select-list (co/factory-apply SelectList))

(s/def :notify/slug #(re-matches #"\S+" %))

(defsc NotificationModal [this {:keys [id modal labels selectedItems category severity content] :as props}]
  {:initial-state (fn [p] {:modal         (prim/get-initial-state b/Modal {:id :notification-modal :backdrop true})
                           :modal/page    :new-notification
                           :notify/slug "" :description "" :sender "" :category :SECURITY :severity :CRITICAL :content ""
                           :labels []
                           :selectedItems []})
   :ident         (fn [] co/new-notification-ident)
   :query         [:notify/slug :description :sender :category :severity :content :labels
                   fs/form-config-join
                   :id :modal/page {:modal (prim/get-query b/Modal)} :selectedItems]
   :form-fields   #{:notify/slug :description :sender :category :severity :content :labels :selectedItems}}
  (let [tags []
        ctg_opts [{ :id :SECURITY,  :name "Security" },
                  { :id :HW_HEALTH, :name "Hardware Health" },
                  { :id :SW_HEALTH, :name "Software Health" }]
        sev_opts [{ :id :CRITICAL,  :name "Critical" },
                  { :id :NORMAL,    :name "Normal" }]
        set-opt! (fn [field] #(m/set-value! this field (keyword (. % -id))))
        disable-button (fs/invalid-spec? props :notify/slug)
        button-action (fn [_]
                        (prim/transact! this `[(fs/mark-complete!  {:entity-ident ~co/new-notification-ident})])
                        (when-not (or (not (fs/checked? props)) (fs/invalid-spec? props :notify/slug))
                          (add-notification this props)))]
  (b/ui-modal modal
    (b/ui-modal-title nil
      (dom/div #js {:key "title" :style #js {:fontSize "22px"}} "Add Notification"))
        (b/ui-modal-body {:className "exportModal"}
          (dom/div :$card
            (dom/div :$content
                     (dom/div :$form-group
                              (co/input-with-label this :notify/slug "Slug:" "Slug is required" "Unique key of the notification" nil {:required true})
                              (co/input-with-label this :description "Description:" "" "Description of the notification" nil nil)
                              (co/input-with-label this :sender "Sender:" "" "Sender of the notification" nil nil)
                              (co/input-with-label this :category "Category:" "" "" nil nil
                                                   (fn [attrs]
                                                     (ui-select-list  {:data (clj->js ctg_opts) :textField :name :valueField :id :value (clj->js {:id category})
                                                                       :onChange (set-opt! :category)})))
                              (co/input-with-label this :severity "Severity:" "" "" nil nil
                                                   (fn [attrs]
                                                     (ui-select-list  {:data (clj->js sev_opts) :textField :name :valueField :id :value (clj->js {:id severity})
                                                                       :onChange (set-opt! :severity)})))
                              (co/input-with-label this :content "Content:" "" "" nil nil
                                                   (fn [attrs]
                                                     (let [attrs (merge attrs {:onChange (fn [event] (m/set-value! this :content (.. event -target -value))) :value content})]
                                                       (dom/textarea (clj->js attrs)))))
                              (co/input-with-label this :labels "Labels:" "" "" nil nil
                                                   (fn [attrs]
                                                     (ui-tag-box #js {:tags (clj->js tags) :selected (clj->js selectedItems) :selectedText "Label exists"
                                                                      :onSelect (fn [tag]
                                                                                  (m/set-value! this :selectedItems (conj selectedItems {:label (. tag -label) :value (. tag -label)} ))
                                                                                  (m/set-value! this :labels (conj labels (. tag -label))))
                                                                      :removeTag (fn [tag]
                                                                                   (m/set-value! this :selectedItems (vec (filter #(not= (:value %) (. tag -value)) selectedItems)))
                                                                                   (m/set-value! this :labels (vec (filter #(not= % (. tag -value)) labels))))})))))))
        (b/ui-modal-footer nil
                           (b/button {:key "ok-next-button" :className "btn-fill" :kind :info :disabled disable-button :onClick button-action} "OK")
          (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                     :onClick #(prim/transact! this `[(b/hide-modal {:id :notification-modal})
                                                      (fs/reset-form!)
                                                      (fs/clear-complete!)])} "Cancel")))))

(defn show-transmissions [this type id slug]
  (prim/transact! this `[(trn/load-transmissions {:slug ~slug})])
  (ro/nav-to! this :transmission))

(defn conv-category [_ category]
  (case category
    :SECURITY "Security"
    :HW_HEALTH "Hardware Health"
    :SW_HEALTH "Software Health"
    "Unknown"))

(defn conv-sev [_ sev]
  (case sev
    :CRITICAL "Critical"
    :NORMAL "Normal"
    "Unknown"))

(defn show-notifications [this]
  (prim/transact! this `[(load-notifications {})]))

(deftable NotificationList :show-notifications :notification [[:slug "Slug"]
                                                              [:created "Created" #(co/conv-time %2)]
                                                              [:sender "Sender"]
                                                              [:category "Category" conv-category]
                                                              [:severity "Severity" conv-sev]
                                                              [:content "Content"]
                                                              [:labels "Labels" #(co/conv-seq %2)]]
  [{:onClick #(show-notification-modal this) :icon "plus"}
   {:onClick #(show-notifications this) :icon "refresh"}]
    :modals [{:modal d/DeleteModal :params {:modal-id :dnt-modal} :callbacks {:onDelete do-delete-notification}}]
    :actions [{:title (tr "View Transmission") :action-class :info :symbol "file" :onClick show-transmissions}
              {:title (tr "Delete Notification") :action-class :danger :symbol "times" :onClick (d/mk-show-modal :dnt-modal props)}]
    :search-keyword :content :search {:comp NotificationSearch})

(defmutation load-notifications [none]
  (action [{:keys [reconciler state] :as env}]
          (let [start (get-in @state [:date-time-picker :notification-dtp-from :time])
                end (get-in @state [:date-time-picker :notification-dtp-to :time])]
            (ld/load reconciler co/notifications-list-ident NotificationList {:params {:start start :end end}})))
  (remote [env]
          (df/remote-load env)))
