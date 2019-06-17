;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.schedules
  (:require [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.routing :as r]
            [fulcro.i18n :refer [tr]]
            [fulcro.ui.forms :as f]
            [fulcro.ui.form-state :as fs]
            [fulcro.ui.bootstrap3 :as b]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.routing :as rt]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [org.edgexfoundry.ui.manager.ui.devices :as dv]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
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
        selected-events (filter #(= schedule-name (:interval %)) events)
        gen-refs (fn [se] [:schedule-event (:id se)])
        content (mapv gen-refs selected-events)]
    (-> state
        (assoc-in [:show-schedule-events :singleton :content] content)
        (assoc-in [:show-schedule-events :singleton :schedule-id] id)
        (assoc-in (conj co/new-schedule-event-ident :schedule) schedule))))

(defmutation prepare-schedule-events
             [{:keys [id]}]
             (action [{:keys [state]}]
                     (swap! state (fn [s] (-> s
                                              (set-schedule-events* id))))))

(defmutation load-schedules
  [{:keys [id]}]
  (action [{:keys [state] :as env}]
          (ld/load-action env :q/edgex-schedule-events dv/ScheduleEventListEntry
                          {:target (conj co/device-list-ident :schedule-events)
                           :post-mutation `prepare-schedule-events
                           :post-mutation-params {:id id}}))
  (remote [env] (df/remote-load env)))

(defn show-schedule-events [this type id]
  (prim/transact! this `[(prepare-schedule-events {:id ~id})])
  (rt/nav-to! this :schedule-event {:id id}))

(defn show-schedule-event-info [this type id]
  (rt/nav-to! this :schedule-event-info {:id id}))

(declare ScheduleList)

(declare ScheduleEventList)

(defn add-new-schedule-event [comp {:keys [name parameters schedule service target protocol httpMethod address port path publisher topic user password] :as form}]
  (let [tmp-id (prim/tempid)]
    (f/reset-from-entity! comp form)
    (prim/transact! comp `[(mu/add-schedule-event {:tempid        ~tmp-id
                                                   :name          ~name
                                                   :parameters    ~parameters
                                                   :schedule-name ~(:name schedule)
                                                   :target        ~target
                                                   :protocol      ~protocol
                                                   :httpMethod    ~httpMethod
                                                   :address       ~address
                                                   :port          ~port
                                                   :path          ~path
                                                   :publisher     ~publisher
                                                   :topic         ~topic
                                                   :user          ~user
                                                   :password      ~password})
                           (b/hide-modal {:id :add-schedule-event-modal})
                           (df/fallback {:action ld/reset-error})])))

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
        (assoc-options :target opts default))))

(defn reset-add-schedule-event* [state]
  (-> state
      (assoc-in [:new-schedule-event :singleton :confirm?] false)
      (assoc-in (conj co/new-schedule-event-ident :name) "")
      (assoc-in (conj co/new-schedule-event-ident :parameters) "")))

(defn set-new-schedule-data* [state]
  (-> state
      (assoc-in (conj co/new-schedule-event-ident :confirm?) true)))

(defmutation prepare-add-schedule-event
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-available-addressables*)
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

(declare AddScheduleModal)

(defn reset-add-schedule* [state]
  (let [ref co/new-schedule-ident
        now (-> (tc/now) (ct/to-long))]
    (-> state
        (assoc-in [:date-time-picker :schedule-start :time] now)
        (assoc-in [:date-time-picker :schedule-end :time] now)
        (assoc-in [:date-time-picker :schedule-start :no-default?] true)
        (assoc-in [:date-time-picker :schedule-end :no-default?] true)
        (assoc-in [:new-schedule :singleton :confirm?] false)
        (assoc-in (conj ref :name) "")
        (assoc-in (conj ref :frequency) "")
        (assoc-in (conj ref :run-once) false))))

(defmutation prepare-add-schedule
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (reset-add-schedule*)
                                   )))))

(defn show-add-schedule-modal [comp]
  (prim/transact! comp `[(prepare-add-schedule {})
                         (r/set-route {:router :root/modal-router :target ~co/new-schedule-ident})
                         (b/show-modal {:id :add-schedule-modal})
                         :modal-router]))

(defn schedule-event-table [name parameters target schedule protocol httpMethod address port path publisher topic]
  (let [if-avail #(or % "N/A")]

    (dom/div :$schedule$container-fluid
             (dom/div :$row$row-no-gutters
                      (dom/div :$col-xs-4$col-md-3 (tr "Name"))
                      (dom/div :$col-xs-8$col-md-9 name))
             (dom/div :$row$row-no-gutters
                      (dom/div :$col-xs-4$col-md-3 (tr "Parameters"))
                      (dom/div :$col-xs-8$col-md-9 (if (str/blank? parameters) "N/A" parameters)))
             (dom/div :$row$row-no-gutters
                      (dom/div :$col-xs-4$col-md-3 (tr "Service"))
                      (dom/div :$col-xs-8$col-md-9 (if-avail target)))

             (dom/div :$row
                      (dom/div :$col-xs-4$col-md-3$schedule-title "Schedule")
                      (dom/div :$col-xs-4$col-md-3 "Name"
                               (dom/div :$short-div (tr "Start"))
                               (dom/div :$short-div (tr "End"))
                               (dom/div :$short-div (tr "Run Once")))
                      (dom/div :$col-xs-4$col-md-6 (:name schedule)
                               (dom/div :$short-div (if-avail (:start schedule)))
                               (dom/div :$short-div (if-avail (:end schedule)))
                               (dom/div :$short-div (if (nil? (:runOnce schedule)) "false" (-> schedule :runOnce str)))))
             (dom/div :$row
                      (dom/div :$col-xs-4$col-md-3$address-title (tr "Address"))
                      (dom/div :$col-xs-4$col-md-3 (tr "Protocol")
                               (dom/div :$short-div (tr "HTTP Method"))
                               (dom/div :$short-div (tr "Address"))
                               (dom/div :$short-div (tr "Port"))
                               (dom/div :$short-div (tr "Path"))
                               (dom/div :$short-div (tr "Publisher"))
                               (dom/div :$short-div (tr "Topic")))
                      (dom/div :$col-xs-4$col-md-6 protocol
                               (dom/div :$short-div (co/conv-http-method httpMethod))
                               (dom/div :$short-div address)
                               (dom/div :$short-div port)
                               (dom/div :$short-div path)
                               (dom/div :$short-div publisher)
                               (dom/div :$short-div topic))))))

(defsc AddScheduleEventModal [this {:keys [modal name parameters target schedule protocol httpMethod address port path publisher topic confirm? modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 4})
                                 {:confirm? false
                                  :modal (prim/get-initial-state b/Modal {:id :add-schedule-event-modal :backdrop true})
                                  :modal/page :new-schedule-event}))
   :ident (fn [] co/new-schedule-event-ident)
   :query [f/form-key :db/id :confirm? :name :parameters :schedule :target :protocol :httpMethod :address :port :path :publisher :topic :user :password
           :confirm? :modal/page
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Name of the Schedule Event" :validator `f/not-empty?)
                 (f/text-input :parameters :placeholder "Parameters")
                 (f/text-input :target :placeholder "Target")
                 (f/text-input :protocol)
                 (f/text-input :address :validator `f/not-empty?)
                 (f/integer-input :port)
                 (f/text-input :path)
                 (f/dropdown-input :httpMethod [(f/option :get "GET")
                                                (f/option :post "POST")
                                                (f/option :put "PUT")
                                                (f/option :delete "DELETE")]
                                   :default-value :get)
                 (f/text-input :publisher)
                 (f/text-input :topic)
                 (f/text-input :user)
                 (f/text-input :password)]}
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
                                                                       (schedule-event-table name parameters target schedule protocol httpMethod address port path publisher topic))))
                                            (dom/div {:className "content"}
                                                     (co/field-with-label this props :name "Name" :className "form-control")
                                                     (co/field-with-label this props :parameters "Parameters" :className "form-control")
                                                     (co/field-with-label this props :target "Target" :className "form-control")
                                                     (co/field-with-label this props :protocol "Protocol" :className "form-control")
                                                     (co/field-with-label this props :httpMethod "HTTP Method" :className "form-control")
                                                     (co/field-with-label this props :address "Address" :className "form-control")
                                                     (co/field-with-label this props :port "Port" :className "form-control")
                                                     (co/field-with-label this props :path "Path" :className "form-control")
                                                     (co/field-with-label this props :publisher "Publisher" :className "form-control")
                                                     (co/field-with-label this props :topic "Topic" :className "form-control")
                                                     (co/field-with-label this props :user "User" :className "form-control")
                                                     (co/field-with-label this props :password "Password" :className "form-control")))))
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
                          {:keys [name interval target created protocol httpMethod address port path publisher topic] :as props}]
  {:ident [:schedule-event :id]
   :query [:id :type :name :interval :target :created :protocol :httpMethod :address :port :path :publisher :topic
           [:show-schedule-events :singleton]]}
  (let [schedule-id (:schedule-id (get props [:show-schedule-events :singleton]))]
    (dom/div nil
             (dom/div {:className "card"}
               (dom/div {:className "fixed-table-toolbar"}
                 (dom/div {:className "bars pull-right"}
                          (b/button
                            {:onClick #(routing/nav-to! this :schedule-event {:id schedule-id})}
                            (dom/i {:className "glyphicon fa fa-caret-square-o-left"}))))
               (dom/div {:className "header"}
                        (dom/h4 {:className "title"} "Schedule Event"))
               (dom/div {:className "table-responsive"}
                 (dom/table {:className "table table-bordered"}
                   (dom/tbody nil
                              (t/row (tr "Name") name)
                              (t/row (tr "Schedule") interval)
                              (t/row (tr "Target") target)
                              (t/row (tr "Created") (co/conv-time created))
                              (dom/tr nil
                                      (dom/th {:rowSpan "8"} "Address"))
                              (t/subrow (tr "Protocol") protocol)
                              (t/subrow (tr "HTTP Method") httpMethod)
                              (t/subrow (tr "Address") address)
                              (t/subrow (tr "Port") port)
                              (t/subrow (tr "Path") path)
                              (t/subrow (tr "Publisher") publisher)
                              (t/subrow (tr "Topic") topic))))))))

(deftable ScheduleEventList :show-schedule-events :schedule-event [[:name "Name"]
                                                                   [:interval "Schedule"]
                                                                   [:target "Target"]]
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
