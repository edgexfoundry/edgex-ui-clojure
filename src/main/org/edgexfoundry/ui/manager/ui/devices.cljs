;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.devices
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.client.routing :as r]
            [fulcro.ui.forms :as f]
            [fulcro.util :refer [conform!]]
            [org.edgexfoundry.ui.manager.ui.routing :as routing]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.labels :as lbl]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.commands :as cmds]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [clojure.set :as set]
            [clojure.string :as str]))

(defn set-admin-mode-target-device*
  [state id]
  (let [device (get-in state [:device id])
        new-mode (if (= (:adminState device) :LOCKED)
                   :UNLOCKED
                   :LOCKED)
        set-modal-state (fn [state attr val] (assoc-in state [:admin-status-modal :singleton attr] val))]
    (-> state
        (set-modal-state :device-id id)
        (set-modal-state :name (:name device))
        (set-modal-state :mode new-mode))))

(defn assoc-options [state field opts default]
  (let [path (into co/new-device-ident [:fulcro.ui.forms/form :elements/by-name field])]
    (-> state
        (assoc-in (conj path :input/options) opts)
        (assoc-in (conj path :input/default-value) (:id default))
        (assoc-in (conj co/new-device-ident field) (:id default)))))

(defn set-unused-addressables* [state]
  (let [mk-id-set (fn [m] (into #{} (map #(-> % :addressable :id) (vals m))))
        dsa-ids (mk-id-set (:device-service state))
        sa-ids (mk-id-set (:schedule-event state))
        addrs (vals (:addressable state))
        a-ids (into #{} (map :id addrs))
        unused-ids (set/difference a-ids dsa-ids sa-ids)
        selected-addr (filter #(contains? unused-ids (:id %)) addrs)
        label #(str (:protocol %) "://" (:address %) ":" (:port %) (:path %))
        opts (mapv #(f/option (:id %) (label %)) selected-addr)
        default (-> selected-addr first)]
    (assoc-options state :addressable opts default)))

(defn set-profiles* [state]
  (let [profiles (-> state :device-profile vals)
        opts (mapv #(f/option (:id %) (:name %)) profiles)
        default (-> profiles first)]
    (-> state
        (assoc-options :profile opts default))))

(defn set-services* [state]
  (let [services (-> state :device-service vals)
        opts (mapv #(f/option (:id %) (:name %)) services)
        default (-> services first)]
    (-> state
        (assoc-options :service opts default))))

(defn reset-add-device* [state]
  (-> state
      (assoc-in [:new-device :singleton :confirm?] false)
      (assoc-in (conj co/new-device-ident :name) "")
      (assoc-in (conj co/new-device-ident :description) "")
      (assoc-in (conj co/new-device-ident :labels) [])))

(defmutation prepare-update-lock-mode
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-admin-mode-target-device* id))))))

(defmutation prepare-add-device
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-unused-addressables*)
                                   (set-profiles*)
                                   (set-services*)
                                   (reset-add-device*))))))

(declare ProfileListEntry)
(declare DeviceList)

(defn show-admin-status-modal [comp id]
  (prim/transact! comp `[(prepare-update-lock-mode {:id ~id})
                         (r/set-route {:router :root/modal-router :target [:admin-status-modal :singleton]})
                         (b/show-modal {:id :admin-status-modal})
                         :modal-router]))

(defn show-add-device-modal [comp]
  (prim/transact! comp `[(prepare-add-device {})
                         (r/set-route {:router :root/modal-router :target ~co/new-device-ident})
                         (b/show-modal {:id :add-device-modal})
                         :modal-router]))

(defn add-new-device [comp form dev]
  (f/reset-from-entity! comp form)
  (prim/transact! comp `[(mu/add-device ~dev)
                         (b/hide-modal {:id :add-device-modal})])
  (df/load comp co/device-list-ident DeviceList))

(defn set-new-device-data* [state]
  (let [service-id (get-in state (conj co/new-device-ident :service))
        service (-> state :device-service service-id)
        addr-id (get-in state (conj co/new-device-ident :addressable))
        addr (-> state :addressable addr-id)]
    (-> state
        (assoc-in (conj co/new-device-ident :confirm?) true)
        (assoc-in (conj co/new-device-ident :service-data) service)
        (assoc-in (conj co/new-device-ident :address-data) addr))))

(defmutation prepare-confirm-add-device
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   set-new-device-data*)))))

(defn load-commands* [state device-id]
  (let [profile-id (get-in state [:device device-id :profile :id])]
    (-> state
        (assoc-in (conj co/command-list-ident :source-device) device-id)
        (assoc-in (conj co/command-list-ident :source-profile) [:device-profile profile-id]))))

(defn add-value-descriptor* [state device-id]
  (let [profile-id (get-in state [:device device-id :profile :id])
        device-resources (get-in state [:device-profile profile-id :deviceResources])
        mk-vd (fn [dr] (let [name (:name dr)
                             pv (get-in dr [:properties :value])]
                         [name {:id name
                                :type :value-descriptor
                                :name name
                                :defaultValue (-> pv :defaultValue)
                                :descripiton (-> dr :description)
                                :formatting "%s"
                                :min (or (-> pv :minimum) "")
                                :max (or (-> pv :maximum) "")
                                :value-type (-> pv :type first)
                                :uomLabel (-> dr :properties :units :defaultValue)}]))
        vds (into {} (mapv mk-vd device-resources))]
    (-> state
        (assoc :value-descriptor vds))))

(defn add-value-descriptor-refs* [state]
  (let [vds (-> state :value-descriptor vals)
        mkref (fn [v] [:value-descriptor (:id v)])
        vd-refs (mapv #(vector :value-descriptor (:id %)) vds)
        add-refs (fn [s id] (assoc-in s [:command id :value-descriptors] vd-refs))]
    (reduce add-refs state (-> state :command keys))))

(defmutation add-value-descriptor-refs [nokeys]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   add-value-descriptor-refs*)))))

(defmutation load-commands
  [{:keys [id]}]
  (action [{:keys [state] :as env}]
          (df/load-action env :q/edgex-commands cmds/CommandListEntry
                          {:target (conj co/command-list-ident :commands)
                           :params {:id id}
                           :post-mutation `add-value-descriptor-refs})
          (swap! state (fn [s] (-> s
                                   (load-commands* id)
                                   (add-value-descriptor* id)))))
  (remote [env] (df/remote-load env)))

(defn do-delete-device [this id props]
  (prim/transact! this `[(mu/delete-device {:id ~id})
                         (t/reset-table-page {:id :show-devices})]))

(defn show-device [this _ id]
  (routing/nav-to! this :info {:id id}))

(defsc ServiceListEntry [this {:keys [id type name addressable]}]
  {:ident [:device-service :id]
   :query [:id :type :name :addressable]})

(defsc ScheduleEventListEntry [this {:keys [id type addressable]}]
  {:ident [:schedule-event :id]
   :query [:id :type :addressable]})

(defsc AddressableListEntry [this {:keys [id type name protocol address port path]}]
  {:ident [:addressable :id]
   :query [:id :type :name :protocol  :address :port :path]})

(defsc ProfileListEntry [this {:keys [id type name manufacturer model description]}]
  {:ident [:device-profile :id]
   :query [:id :type :name :manufacturer :model :description]})

(defsc AdminStatusModal [this {:keys [device-id name mode modal modal/page]}]
  {:initial-state (fn [p] {:device-id :none
                           :name      ""
                           :mode      :LOCKED
                           :modal     (prim/get-initial-state b/Modal {:id :admin-status-modal :backdrop true})
                           :modal/page :admin-status-modal})
   :ident (fn [] [:admin-status-modal :singleton])
   :query [:device-id :name :mode :modal/page
           {:modal (prim/get-query b/Modal)}]}
  (let [mode-str (if (= mode :LOCKED)
                   "Lock"
                   "Unlock")]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Change Admin Status"))
                (b/ui-modal-body nil
                                 (dom/div #js {:className "swal2-icon swal2-warning" :style #js {:display "block"}} "!")
                                 (dom/p #js {:key "message" :className b/text-danger} (str mode-str " " name)))
                (b/ui-modal-footer nil
                                   (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                              :onClick #(prim/transact! this `[(mu/update-lock-mode
                                                                                 {:id ~device-id :mode ~mode})
                                                                               (b/hide-modal {:id :admin-status-modal})])}
                                             "OK")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :admin-status-modal})])}
                                             "Cancel")))))

(defn device-general-table [id name description profile addressable labels]
  (let [labels-str (str/join ", " (map #(str "'" % "'" ) labels))
        key-to-string #(subs (str %) 1)]
    (dom/div
      #js {:className "table-responsive"}
      (dom/table
        #js {:className "table table-bordered"}
        (dom/tbody nil
                   (t/row "Name" name)
                   (t/row "Description" description)
                   (if id (t/row "Id" (key-to-string id)))
                   (t/row "Manufacturer" (:manufacturer profile))
                   (t/row "Model" (:model profile))
                   (t/row "Labels" labels-str)
                   (dom/tr nil
                           (dom/th #js {:rowSpan "5"} "Address")
                           (dom/th nil "Name")
                           (dom/td nil (:name addressable)))
                   (t/subrow "Protocol" (:protocol addressable))
                   (t/subrow "Address" (:address addressable))
                   (t/subrow "Port" (:port addressable))
                   (t/subrow "Path" (:path addressable)))))))

(defn device-service-table [service]
  (dom/div nil
           (dom/div #js {:className "header"}
                    (dom/h4 #js {:className "title"} "Service"))
           (dom/div
             #js {:className "table-responsive"}
             (dom/table
               #js {:className "table table-bordered"}
               (dom/tbody nil
                          (t/subrow "Host" (-> service :addressable :address))
                          (t/subrow "Port" (-> service :addressable :port)))))))

(defsc AddDeviceModal [this {:keys [confirm? name description labels profile address-data service-data profiles modal modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 1})
                                 {:confirm? false
                                  :modal (prim/get-initial-state b/Modal {:id :add-device-modal :backdrop true})
                                  :modal/page :new-device}))
   :ident (fn [] co/new-device-ident)
   :query [:confirm? :address-data :service-data
           f/form-key :db/id :name :addressable :description :labels :profile :service :modal/page
           {:profiles (prim/get-query ProfileListEntry)}
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Name of the device" :validator `f/not-empty?)
                 (f/dropdown-input :addressable [(f/option :none "No addressables available")]
                                   :default-value :none)
                 (f/text-input :description :placeholder "Short description" :validator `f/not-empty?)
                 (lbl/labels-input :labels :placeholder "Labels (one per line)")
                 (f/dropdown-input :profile [(f/option :none "No profiles available")]
                                   :default-value :none)
                 (f/dropdown-input :service [(f/option :none "No services available")]
                                   :default-value :none)]}
  (let [profile-name (-> (filter #(= profile (:id %)) profiles) first :name)
        service-name (:name service-data)
        address-name (:name address-data)
        dev {:name name
             :description description
             :labels labels
             :profile-name profile-name
             :service-name service-name
             :addressable-name address-name}
        valid? (f/valid? (f/validate-fields props))
        profile-data (-> (filter #(= profile (:id %)) profiles) first)]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Add New Device"))
                (dom/div #js {:className "header"} "Add New Device")
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                      (if confirm?
                                        (dom/div #js {:className "container-fluid"}
                                                 (dom/div #js {:className "row"}
                                                          (dom/div #js {:className "col-md-12"}
                                                                   (dom/div #js {:className "header"}
                                                                            (dom/h4 #js {:className "title"} "Device"))
                                                                   (device-general-table nil name description profile-data address-data labels)
                                                                   (device-service-table service-data))))
                                        (dom/div #js {:className "content"}
                                                 (co/field-with-label this props :name "Name" :className "form-control")
                                                 (co/field-with-label this props :addressable "Addressable" :className "form-control")
                                                 (co/field-with-label this props :description "Description" :className "form-control")
                                                 (co/field-with-label this props :labels "Labels" :className "form-control")
                                                 (co/field-with-label this props :profile "Profile" :className "form-control")
                                                 (dom/div #js {:className "table-responsive"}
                                                          (dom/table #js {:className "table table-bordered"}
                                                                     (dom/tbody nil
                                                                                (dom/tr nil
                                                                                        (dom/th nil "Manufacturer")
                                                                                        (dom/td nil (:manufacturer profile-data)))
                                                                                (dom/tr nil
                                                                                        (dom/th nil "Model")
                                                                                        (dom/td nil (:model profile-data)))
                                                                                (dom/tr nil
                                                                                        (dom/th nil "Description")
                                                                                        (dom/td nil (:description profile-data))))))
                                                 (co/field-with-label this props :service "Service" :className "form-control")))))
                (b/ui-modal-footer nil
                                   (when confirm?
                                     (b/button {:key "back-button" :className "btn-fill" :kind :info
                                                :onClick #(m/toggle! this :confirm?)}
                                               "Back"))
                                   (if confirm?
                                     (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                                :onClick #(add-new-device this props dev)}
                                               "OK")
                                     (b/button {:key "next-button" :className "btn-fill" :kind :info
                                                :onClick #(prim/transact!
                                                            this `[(prepare-confirm-add-device {})])
                                                :disabled (not valid?)}
                                               "Next"))
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :add-device-modal})])}
                                             "Cancel")))))

(defsc DeviceInfo [this
                   {:keys [id type name description profile labels adminState operatingState lastConnected lastReported
                           addressable service]}]
  {:ident [:device :id]
   :query [:id :type :name :description :profile :labels :adminState :operatingState :lastConnected :lastReported
           :addressable :service]}
  (let [admin-str (if (= adminState :LOCKED)
                    "Locked"
                    "Unlocked")
        op-str (if (= operatingState :ENABLED)
                 "Enabled"
                 "Disabled")
        connected-str (co/conv-time lastConnected)
        reported-str (co/conv-time lastReported)
        serv-connected-str (co/conv-time (:lastConnected service))
        serv-reported-str (co/conv-time (:lastReported service))
        serv-admin-str (if (= (:adminState service) :LOCKED)
                         "Locked"
                         "Unlocked")
        serv-op-str (if (= (:operatingState service) :ENABLED)
                      "Enabled"
                      "Disabled")]
    (dom/div nil
             (dom/div
               #js {:className "card"}
               (dom/div
                 #js {:className "fixed-table-toolbar"}
                 (dom/div #js {:className "bars pull-right"}
                          (b/button
                            {:onClick #(routing/nav-to! this :main)}
                            (dom/i #js {:className "glyphicon fa fa-caret-square-o-left"}))))
               (dom/div #js {:className "header"}
                        (dom/h4 #js {:className "title"} "Device"))
               (device-general-table id name description profile addressable labels))
             (dom/div
               #js {:className "card"}
               (dom/div #js {:className "header"}
                        (dom/h4 #js {:className "title"} "Status"))
               (dom/div
                 #js {:className "table-responsive"}
                 (dom/table
                   #js {:className "table table-bordered"}
                   (dom/tbody nil
                              (t/row "Admin State" admin-str)
                              (t/row "Operating State" op-str)
                              (t/row "Last Connected" connected-str)
                              (t/row "Last Reported" reported-str)))))
             (dom/div
               #js {:className "card"}
               (dom/div #js {:className "header"}
                        (dom/h4 #js {:className "title"} "Service"))
               (dom/div
                 #js {:className "table-responsive"}
                 (dom/table
                   #js {:className "table table-bordered"}
                   (dom/tbody nil
                              (t/row "Name" (:name service))
                              (t/row "Last Connected" serv-connected-str)
                              (t/row "Last Reported" serv-reported-str)
                              (t/row "Admin State" serv-admin-str)
                              (t/row "Operating State" serv-op-str)
                              (dom/tr nil
                                      (dom/th #js {:rowSpan "5"} "Address")
                                      (dom/th nil "Name")
                                      (dom/td nil (-> service :addressable :name)))
                              (t/subrow "Protocol" (-> service :addressable :protocol))
                              (t/subrow "Address" (-> service :addressable :address))
                              (t/subrow "Port" (-> service :addressable :port))
                              (t/subrow "Path" (-> service :addressable :path)))))))))

(defsc DeviceListEntry
  [this {:keys [id type name adminState operatingState lastConnected]} {:keys [onShow onCmds onDelete]}]
  {:ident [:device :id]
   :query [:id :type :name :adminState :operatingState :lastConnected]}
  (let [lastConnected-str (co/conv-time lastConnected)
        [op-icon op-icon-color] (if (= operatingState :ENABLED)
                                  ["fa fa-circle" "green"]
                                  ["fa fa-circle-o" "red"])
        [lock-icon lock-action-icon lock-action-tip] (if (= adminState :LOCKED)
                                                       ["fa fa-lock" "fa fa-unlock" "Unlock Device"]
                                                       ["fa fa-unlock" "fa fa-lock" "Lock Device"])]
    (dom/tr nil
            (dom/td nil name)
            (dom/td #js {:className "text-center"}
                    (dom/i #js {:className op-icon :style #js {:color op-icon-color} :aria-hidden "true"}))
            (dom/td #js {:className "text-center"}
                    (dom/i #js {:className lock-icon :aria-hidden "true"}))
            (dom/td nil lastConnected-str)
            (dom/td
              #js {:className "td-actions text-right"}
              (dom/button
                #js
                    {:type "button",
                     :rel "tooltip",
                     :title "View Device",
                     :className "btn btn-info btn-simple btn-xs"
                     :onClick #(onShow id)}
                (dom/i #js {:className "fa fa-info"}))
              (dom/button
                #js
                    {:type "button",
                     :rel "tooltip",
                     :title lock-action-tip,
                     :className "btn btn-success btn-simple btn-xs"
                     :onClick #(show-admin-status-modal this id)}
                (dom/i #js {:className lock-action-icon}))
              (dom/button
                #js
                    {:type "button",
                     :rel "tooltip",
                     :title "Control Device",
                     :className "btn btn-danger btn-simple btn-xs"
                     :onClick #(onCmds id)}
                (dom/i #js {:className "fa fa-dashboard"}))
              (dom/button
                #js
                    {:type "button",
                     :rel "tooltip",
                     :title "Delete Device",
                     :className "btn btn-danger btn-simple btn-xs"
                     :onClick #(onDelete this type id)}
                (dom/i #js {:className "fa fa-times"}))))))

(deftable DeviceList :show-devices :device [[:name "Name"] [:operatingState "Op Status"] [:adminState "Admin Status"]
                                            [:lastConnected "Last Seen"]]
  [{:onClick #(show-add-device-modal this) :icon "plus"}
   {:onClick #(df/refresh! this {:fallback `d/show-error}) :icon "refresh"}]
  :modals [{:modal d/DeleteModal :params {:modal-id :dd-modal} :callbacks {:onDelete do-delete-device}}]
  :actions [{:title :onShow :action-class :info :symbol "info" :onClick (fn [id] (routing/nav-to! this :info {:id id}))}
            {:title :onCmds :action-class :danger :symbol "times" :onClick (fn [id] (prim/transact! this `[(load-commands {:id ~id})])
                        (routing/nav-to! this :control))}
            {:title :onDelete :action-class :danger :symbol "times" :onClick (d/mk-show-modal :dd-modal)}]
  :row-symbol DeviceListEntry)
