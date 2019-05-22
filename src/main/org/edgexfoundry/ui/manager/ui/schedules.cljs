;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.schedules
  (:require [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.dom :as dom]
            [fulcro.client.routing :as r]
            [fulcro.ui.forms :as f]
            [fulcro.ui.bootstrap3 :as b]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.routing :as rt]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.routing :as routing]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df]
            [clojure.set :as set]
            [clojure.string :as str]
            [cljs-time.coerce :as ct]
            [cljs-time.format :as ft]
            [cljs-time.core :as tc]))

(defn start-end-time-conv [p t]
  (if t
    t
    "N/A"))

(defn do-delete-schedule [this id props]
  (prim/transact! this `[(mu/delete-schedule {:id ~id})
                         (t/reset-table-page {:id :show-schedules})]))

(defn do-delete-schedule-event [this id props]
  (prim/transact! this `[(mu/delete-schedule-event {:id ~id})
                         (t/reset-table-page {:id :show-schedule-events})]))

(defn set-schedule-events*
  [state id]
  (let [schedule (-> state :schedule id)
        schedule-name (:name schedule)
        events (vals (:schedule-event state))
        selected-events (filter #(= schedule-name (:schedule %)) events)
        gen-refs (fn [se] [:schedule-event (:id se)])
        content (mapv gen-refs selected-events)]
    (-> state
        (assoc-in [:show-schedule-events :singleton :content] content)
        (assoc-in (conj co/new-schedule-event-ident :schedule) schedule))))

(defmutation prepare-schedule-events
             [{:keys [id]}]
             (action [{:keys [state]}]
                     (swap! state (fn [s] (-> s
                                              (set-schedule-events* id))))))

(defn show-schedule-events [this type id]
  (prim/transact! this `[(prepare-schedule-events {:id ~id})])
  (rt/nav-to! this :schedule-event))

(defn show-schedule-event-info [this type id]
  (rt/nav-to! this :schedule-event-info {:id id}))

(declare ScheduleList)

(declare ScheduleEventList)

(defn add-new-schedule-event [comp {:keys [name parameters schedule service address-data] :as form}]
  (let [tmp-id (prim/tempid)]
    (f/reset-from-entity! comp form)
    (prim/transact! comp `[(mu/add-schedule-event {:tempid ~tmp-id
                                                   :name ~name
                                                   :parameters ~parameters
                                                   :schedule-name ~(:name schedule)
                                                   :service-name ~(:name service)
                                                   :addressable-name ~(:name address-data)})
                           (b/hide-modal {:id :add-schedule-event-modal})])))

(defn assoc-options [state field opts default]
  (let [path (into co/new-schedule-event-ident [:fulcro.ui.forms/form :elements/by-name field])]
    (-> state
        (assoc-in (conj path :input/options) opts)
        (assoc-in (conj path :input/default-value) default)
        (assoc-in (conj co/new-schedule-event-ident field) default))))

(defn set-available-addressables* [state]
  (let [mk-id-set (fn [m] (into #{} (map #(-> % :addressable :id) (vals m))))
        dsa-ids (mk-id-set (:device-service state))
        addrs (vals (:addressable state))
        a-ids (into #{} (map :id addrs))
        unused-ids (set/difference a-ids dsa-ids)
        selected-addr (sort-by :name (filter #(unused-ids (:id %)) addrs))
        opts (if (empty? selected-addr)
               [(f/option :none "No addressable available")]
               (mapv #(f/option (:id %) (:name %)) selected-addr))
        default (or (-> selected-addr first :id) :none)]
    (assoc-options state :addressable opts default)))

(defn set-schedule-services* [state]
  (let [services (-> state :device-service vals)
        opts (if (empty? services)
               [(f/option :none "No services available")]
               (mapv #(f/option (:id %) (:name %)) services))
        default (or (-> services first) :none)]
    (-> state
        (assoc-options :service opts default))))

(defn reset-add-schedule-event* [state]
  (-> state
      (assoc-in [:new-schedule-event :singleton :confirm?] false)
      (assoc-in (conj co/new-schedule-event-ident :name) "")
      (assoc-in (conj co/new-schedule-event-ident :parameters) "")))

(defn set-new-schedule-data* [state]
  (let [service-id (get-in state (conj co/new-schedule-event-ident :service))
        service (-> state :device-service service-id)
        addr-id (get-in state (conj co/new-schedule-event-ident :addressable))
        addr (-> state :addressable addr-id)]
    (-> state
        (assoc-in (conj co/new-schedule-event-ident :confirm?) true)
        (assoc-in (conj co/new-schedule-event-ident :service-data) service)
        (assoc-in (conj co/new-schedule-event-ident :address-data) addr))))

(defmutation prepare-add-schedule-event
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-available-addressables*)
                                   (set-schedule-services*)
                                   (reset-add-schedule-event*))))))

(defmutation prepare-confirm-add-schedule-event
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   set-new-schedule-data*)))))

(defn show-add-schedule-event-modal [comp]
  (prim/transact! comp `[(prepare-add-schedule-event {})
                         (r/set-route {:router :root/modal-router :target ~co/new-schedule-event-ident})
                         (b/show-modal {:id :add-schedule-event-modal})
                         :modal-router]))

(def time-formatter1 (ft/formatter "yMMdd"))

(def time-formatter2 (ft/formatter "HHmmss"))

(defn get-time [tp]
  (let [time (-> tp :time (ct/from-long))
        time-str  (str (ft/unparse time-formatter1 time) "T" (ft/unparse time-formatter2 time))]
    (if (:no-default? tp) nil time-str)))

(defn add-new-schedule [comp form]
  (let [tmp-id (prim/tempid)
        {:keys [name start-time end-time frequency run-once]} form
        start (get-time start-time)
        end (get-time end-time)]
    (f/reset-from-entity! comp form)
    (prim/transact! comp `[(mu/add-schedule {:tempid ~tmp-id
                                             :name ~name
                                             :start ~start
                                             :end ~end
                                             :frequency ~frequency
                                             :run-once ~run-once})
                           (b/hide-modal {:id :add-schedule-modal})
                           :show-schedules])
    (df/load comp co/device-list-ident ScheduleList {:fallback `d/show-error})))

(defn reset-add-schedule* [state]
  (let [now (-> (tc/now) (ct/to-long))]
    (-> state
        (assoc-in [:date-time-picker :schedule-start :time] now)
        (assoc-in [:date-time-picker :schedule-end :time] now)
        (assoc-in [:date-time-picker :schedule-start :no-default?] true)
        (assoc-in [:date-time-picker :schedule-end :no-default?] true)
        (assoc-in [:new-schedule :singleton :confirm?] false)
        (assoc-in (conj co/new-schedule-ident :name) "")
        (assoc-in (conj co/new-schedule-ident :frequency) "")
        (assoc-in (conj co/new-schedule-ident :run-once) false))))

(defmutation prepare-add-schedule
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (reset-add-schedule*))))))

(defn show-add-schedule-modal [comp]
  (prim/transact! comp `[(prepare-add-schedule {})
                         (r/set-route {:router :root/modal-router :target ~co/new-schedule-ident})
                         (b/show-modal {:id :add-schedule-modal})
                         :modal-router]))

(defn schedule-event-table [name parameters service addressable schedule]
  (let [if-avail #(or % "N/A")]
    (dom/div
      #js {:className "table-responsive"}
      (dom/table
        #js {:className "table table-bordered"}
        (dom/tbody nil
                   (t/row "Name" name)
                   (t/row "Parameters" (if (str/blank? parameters) "N/A" parameters))
                   (t/row "Service" (if-avail (:name service)))
                   (t/row "Addressable" (if-avail (:url addressable)))
                   (dom/tr nil
                           (dom/th #js {:rowSpan "5"} "Schedule")
                           (dom/th nil "Name")
                           (dom/td nil (:name schedule)))
                   (t/subrow "Start" (if-avail (:start schedule)))
                   (t/subrow "End" (if-avail (:end schedule)))
                   (t/subrow "Run Once" (-> schedule :runOnce str)))))))

(defsc AddScheduleEventModal [this {:keys [modal name parameters service schedule address-data confirm? modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 4})
                                 {:confirm? false
                                  :modal (prim/get-initial-state b/Modal {:id :add-schedule-event-modal :backdrop true})
                                  :modal/page :new-schedule-event}))
   :ident (fn [] co/new-schedule-event-ident)
   :query [f/form-key :db/id :confirm? :name :parameters :schedule :service :addressable :service-data :address-data :schedule
           :confirm? :modal/page
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Name of the Schedule Event" :validator `f/not-empty?)
                 (f/text-input :parameters :placeholder "Parameters")
                 (f/dropdown-input :service [(f/option :none "No services available")])
                 (f/dropdown-input :addressable [(f/option :none "No addressable available")])]}
  (let [valid? (f/valid? (f/validate-fields props))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Add New Schedule Event"))
                (dom/div #js {:className "header"} "Add New Schedule Event")
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                          (if confirm?
                                            (dom/div #js {:className "container-fluid"}
                                                     (dom/div #js {:className "row"}
                                                              (dom/div #js {:className "col-md-12"}
                                                                       (dom/div #js {:className "header"}
                                                                                (dom/h4 #js {:className "title"} "Schedule Event"))
                                                                       (schedule-event-table name parameters service address-data
                                                                                             schedule))))
                                            (dom/div #js {:className "content"}
                                                     (co/field-with-label this props :name "Name" :className "form-control")
                                                     (co/field-with-label this props :parameters "Parameters" :className "form-control")
                                                     (co/field-with-label this props :service "Service" :className "form-control")
                                                     (co/field-with-label this props :addressable "Addressable" :className "form-control")))))
                (b/ui-modal-footer nil
                                   (when confirm?
                                     (b/button {:key "back-button" :className "btn-fill" :kind :info
                                                :onClick #(m/toggle! this :confirm?)}
                                               "Back"))
                                   (if confirm?
                                     (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                                :onClick #(add-new-schedule-event this props)}
                                               "OK")
                                     (b/button {:key "next-button" :className "btn-fill" :kind :info
                                                :onClick #(prim/transact! this `[(prepare-confirm-add-schedule-event {})])
                                                :disabled (not valid?)}
                                               "Next"))
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact!
                                                          this `[(b/hide-modal {:id :add-schedule-event-modal})])}
                                             "Cancel")))))

(defn schedule-table [name start end freq run-once]
  (let [if-time-set #(if (:no-default? %) "N/A" (->> % :time ct/from-long (ft/unparse dtp/time-formatter)))]
    (dom/div
      #js {:className "table-responsive"}
      (dom/table
        #js {:className "table table-bordered"}
        (dom/tbody nil
                   (t/row "Name" name)
                   (t/row "Start" (if-time-set start))
                   (t/row "End" (if-time-set end))
                   (t/row "Frequency" (if (-> freq str/blank? not) freq "N/A"))
                   (t/row "Run Once" (str run-once)))))))

(defsc AddScheduleModal [this {:keys [modal confirm? name frequency run-once start-time end-time modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 5})
                                 {:start-time (prim/get-initial-state dtp/DateTimePicker {:id :schedule-start
                                                                                          :time (tc/now)
                                                                                          :no-default true
                                                                                          :name "start-time"})
                                  :end-time (prim/get-initial-state dtp/DateTimePicker {:id :schedule-end
                                                                                        :time (tc/now)
                                                                                        :no-default true
                                                                                        :name "end-time"})
                                  :confirm? false
                                  :modal (prim/get-initial-state b/Modal {:id :add-schedule-modal :backdrop true})
                                  :modal/page :new-schedule}))
   :ident (fn [] co/new-schedule-ident)
   :query [f/form-key :db/id :confirm? :name :frequency :run-once :modal/page
           {:start-time (prim/get-query dtp/DateTimePicker)}
           {:end-time (prim/get-query dtp/DateTimePicker)}
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Name of the Schedule" :validator `f/not-empty?)
                 (f/text-input :frequency :placeholder "Frequency")
                 (f/checkbox-input :run-once)]}
  (let [valid? (f/valid? (f/validate-fields props))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Add New Schedule"))
                (dom/div #js {:className "header"} "Add New Schedule")
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                          (if confirm?
                                            (dom/div #js {:className "container-fluid"}
                                                     (dom/div #js {:className "row"}
                                                              (dom/div #js {:className "col-md-12"}
                                                                       (dom/div #js {:className "header"}
                                                                                (dom/h4 #js {:className "title"} "Schedule"))
                                                                       (schedule-table name start-time end-time frequency run-once))))
                                            (dom/div #js {:className "content"}
                                                     (co/field-with-label this props :name "Name" :className "form-control")
                                                     (dom/div #js {:className "form-group" :style #js {:position "relative"}}
                                                              (dom/label #js {:className "control-label" :htmlFor "start-time"} "Start")
                                                              (dtp/date-time-picker start-time))
                                                     (dom/div #js {:className "form-group" :style #js {:position "relative"}}
                                                              (dom/label #js {:className "control-label" :htmlFor "end-time"} "End")
                                                              (dtp/date-time-picker end-time))
                                                     (co/field-with-label this props :frequency "Frequency" :className "form-control")
                                                     (co/field-with-label this props :run-once "Run Once" :className "form-control")))))
                (b/ui-modal-footer nil
                                   (when confirm?
                                     (b/button {:key "back-button" :className "btn-fill" :kind :info
                                                :onClick #(m/toggle! this :confirm?)}
                                               "Back"))
                                   (if confirm?
                                     (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                                :onClick #(add-new-schedule this props)}
                                               "OK")
                                     (b/button {:key "next-button" :className "btn-fill" :kind :info
                                                :onClick #(m/toggle! this :confirm?)
                                                :disabled (not valid?)}
                                               "Next"))
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact!
                                                         this `[(b/hide-modal {:id :add-schedule-modal})])}
                                             "Cancel")))))

(defsc ScheduleEventInfo [this
                          {:keys [id type name addressable schedule service created]}]
  {:ident [:schedule-event :id]
   :query [:id :type :name :addressable :schedule :service :created]}
  (dom/div nil
           (dom/div
            #js {:className "card"}
            (dom/div
             #js {:className "fixed-table-toolbar"}
             (dom/div #js {:className "bars pull-right"}
                      (b/button
                       {:onClick #(routing/nav-to! this :schedule-event)}
                       (dom/i #js {:className "glyphicon fa fa-caret-square-o-left"}))))
            (dom/div #js {:className "header"}
                     (dom/h4 #js {:className "title"} "Schedule Event"))
            (dom/div
             #js {:className "table-responsive"}
             (dom/table
              #js {:className "table table-bordered"}
              (dom/tbody nil
                         (t/row "Name" name)
                         (t/row "Schedule" schedule)
                         (t/row "Service" service)
                         (t/row "Created" (co/conv-time created))
                         (dom/tr nil
                                 (dom/th #js {:rowSpan "5"} "Address")
                                 (dom/th nil "Name")
                                 (dom/td nil (:name addressable)))
                         (t/subrow "Protocol" (:protocol addressable))
                         (t/subrow "Address" (:address addressable))
                         (t/subrow "Port" (:port addressable))
                         (t/subrow "Path" (:path addressable))))))))

(deftable ScheduleEventList :show-schedule-events :schedule-event [[:name "Name"]
                                                                   [:addressable "Addressable" (fn [p v] (:name v))]
                                                                   [:service "Service"]]
  [{:onClick #(show-add-schedule-event-modal this) :icon "plus"}
   {:onClick #(rt/nav-to! this :schedule) :icon "caret-square-o-left"}]
  :modals [{:modal d/DeleteModal :params {:modal-id :dse-modal} :callbacks {:onDelete do-delete-schedule-event}}]
  :actions [{:title "View Schedule Event" :action-class :info :symbol "info" :onClick show-schedule-event-info}
            {:title "Delete Schedule Event" :action-class :danger :symbol "times"
             :onClick (d/mk-show-modal :dse-modal)}])

(defsc ScheduleEvents [this {:keys [id type addressable]}]
  {:ident [:schedule-event :id]
   :query [:id :type :addressable]})

(deftable ScheduleList :show-schedules :schedule [[:name "Name"] [:start "Start" start-end-time-conv]
                                                  [:end "End" start-end-time-conv] [:frequency "Frequency"]
                                                  [:runOnce "Run Once"]]
  [{:onClick #(show-add-schedule-modal this) :icon "plus"}
   {:onClick #(df/refresh! this) :icon "refresh"}]
  :query [{:events (prim/get-query ScheduleEvents)}]
  :modals [{:modal d/DeleteModal :params {:modal-id :ds-modal} :callbacks {:onDelete do-delete-schedule}}]
  :actions [{:title "Show Events" :action-class :info :symbol "cogs" :onClick show-schedule-events}
            {:title "Delete Schedule" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :ds-modal)}])
