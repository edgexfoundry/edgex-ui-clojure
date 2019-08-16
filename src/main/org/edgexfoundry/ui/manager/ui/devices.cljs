;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.devices
  (:require [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation integrate-ident*]]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.events :as evt]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.client.routing :as r]
            [fulcro.ui.forms :as f]
            [fulcro.ui.form-state :as fs]
            [fulcro.util :refer [conform!]]
            [org.edgexfoundry.ui.manager.ui.routing :as routing]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.commands :as cmds]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.file-upload :as fu]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            ["rc-switch" :as ToggleWidget]))

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

(defn set-profiles* [state]
  (let [profiles (-> state :device-profile vals)
        items (mapv #(b/dropdown-item (:id %) (:name %)) profiles)
        dropdown-id (random-uuid)
        dropdown (b/dropdown dropdown-id "Profile" items)
        default (-> profiles first :id)]
    (-> state
        (prim/merge-component b/Dropdown dropdown)
        (b/set-dropdown-item-active* dropdown-id default)
        (assoc-in (conj co/new-device-entry :ui/dropdown2) (b/dropdown-ident dropdown-id)))))

(defn set-services* [state]
  (let [services (-> state :device-service vals)
        items (mapv #(b/dropdown-item (:id %) (:name %)) services)
        dropdown-id (random-uuid)
        dropdown (b/dropdown dropdown-id "Service" items)
        default (-> services first :id)]
    (-> state
        (prim/merge-component b/Dropdown dropdown)
        (b/set-dropdown-item-active* dropdown-id default)
        (assoc-in (conj co/new-device-entry :ui/dropdown) (b/dropdown-ident dropdown-id)))))

(defn set-protocols* [state]
  (-> state
      (assoc-in [::protocols :singleton :prt-props] {})
      (assoc-in [::protocols :singleton :ui/prts] "")
      (assoc-in [::protocols :singleton :ui/prt-name] "")
      (assoc-in [::protocols :singleton :ui/prt-value] "")))

(defn set-autoEvents* [state]
  (-> state
      (assoc-in [::auto-events :singleton :ui/autoevents] [])
      (assoc-in [::auto-events :singleton :ui/frequency] "")
      (assoc-in [::auto-events :singleton :ui/resource] "")))

(defmutation prepare-update-lock-mode
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-admin-mode-target-device* id))))))

(declare NewDeviceModal)

(defmutation prepare-add-device
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (let [ref co/new-device-ident]
                                 (-> s
                                     (set-profiles*)
                                     (set-services*)
                                     (set-protocols*)
                                     (set-autoEvents*)
                                     (fs/add-form-config* NewDeviceModal ref)
                                     (fs/mark-complete* ref)))))))

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

(defmutation load-commands
  [{:keys [id]}]
  (action [{:keys [state] :as env}]
          (ld/load-action env :q/edgex-commands cmds/CommandListEntry
                          {:target (conj co/command-list-ident :commands)
                           :params {:id id}
                           :post-mutation `cmds/add-value-descriptor-refs})
          (swap! state (fn [s] (-> s
                                   (load-commands* id)
                                   (add-value-descriptor* id)))))
  (remote [env] (df/remote-load env)))

(defn do-delete-device [this id props]
  (prim/transact! this `[(mu/delete-device {:id ~id})
                         (t/reset-table-page {:id :show-devices})
                         (df/fallback {:action ld/reset-error})]))

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
                                  (dom/div {:key "title"
                                            :style #js {:fontSize "22px"}} "Change Admin Status"))
                (b/ui-modal-body nil
                                 ;(dom/div {:className "swal2-icon swal2-warning" :style #js {:display "block"}} "!")
                                 (dom/p {:key "message" :className b/text-danger} (str mode-str " " name)))
                (b/ui-modal-footer nil
                                   (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                              :onClick #(prim/transact! this `[(mu/update-lock-mode
                                                                                 {:id ~device-id :mode ~mode})
                                                                               (b/hide-modal {:id :admin-status-modal})])}
                                             "OK")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :admin-status-modal})])}
                                             "Cancel")))))

(defn key-to-string [k]
  (subs (str k) 1))

(defn protocol-prop-row [proto-key field value]
  (let [name (key-to-string field)]
    (dom/tr {:key (str "protocol-prop-" proto-key "-" name)}
            (dom/th nil name)
            (dom/td nil value))))

(defn protocol-sub-table [protocol]
  (let [protocol-props (second protocol)
        first-prop (first protocol-props)
        protocol-key-str (-> protocol first key-to-string)
        gen-protocol-prop (fn [[field val]] (protocol-prop-row protocol-key-str field val))]
    [(dom/tr {:key (str "protocol-" protocol-key-str)}
             (dom/th {:rowSpan (count protocol-props)} protocol-key-str)
             (dom/th nil (-> first-prop first key-to-string))
             (dom/td nil (second first-prop)))
     (mapv gen-protocol-prop (rest protocol-props))]))

(defn auto-events-sub-table [autoEvents]
  (let [gen-frequency-row (fn [autoEvent]
                              (dom/div {:className "row eventrow"}
                                       (dom/div {:className "col-md-12"} (:frequency autoEvent))))
        gen-onchange-row (fn [autoEvent]
                            (dom/div {:className "row eventrow"}
                                     (dom/div {:className "col-md-12"} (if (:onChange autoEvent) "true" "false"))))
        gen-resource-row (fn [autoEvent]
                           (dom/div {:className "row eventrow"}
                                    (dom/div {:className "col-md-12"} (:resource autoEvent))))]
          (dom/div {:className "container-fluid"}
                   (dom/div {:className "row"}
                            (dom/div {:className "col-md-3 event-subject"}
                                     (dom/div {:className "row event-title"}
                                              (dom/div {:className "col-md-12"} (tr "Auto Events"))))
                            (dom/div {:className "col-md-3 event-col"}
                                     (dom/div {:className "row eventrow event-title"}
                                              (dom/div {:className "col-md-12"} (tr "Frequency")))
                                     (mapv #(gen-frequency-row %) autoEvents))
                            (dom/div {:className "col-md-2 event-col"}
                                     (dom/div {:className "row eventrow event-title"}
                                              (dom/div {:className "col-md-12"} (tr "On Change")))
                                     (mapv #(gen-onchange-row %) autoEvents))
                            (dom/div {:className "col-md-3 event-col last-col"}
                                     (dom/div {:className "row eventrow event-title"}
                                              (dom/div {:className "col-md-12"} (tr "Resource")))
                                     (mapv #(gen-resource-row %) autoEvents))))))

(defn device-general-table [id name description profile protocols labels]
  (let [labels-str (str/join ", " (map #(str "'" % "'" ) labels))]
    (dom/div {:className "table-responsive"}
             (dom/table {:className "table table-bordered"}
                        (dom/tbody nil
                                   (t/row "Name" name)
                                   (t/row "Description" description)
                                   (if id (t/row "Id" (key-to-string id)))
                                   (t/row "Manufacturer" (:manufacturer profile))
                                   (t/row "Model" (:model profile))
                                   (t/row "Labels" labels-str)
                                   (mapv protocol-sub-table protocols))))))

(defn device-service-table [service]
  (dom/div nil
           (dom/div {:className "header"}
                    (dom/h4 {:className "title"} "Service"))
           (dom/div {:className "table-responsive"}
                    (dom/table {:className "table table-bordered"}
                               (dom/tbody nil
                                          (t/subrow "Host" (-> service :addressable :address))
                                          (t/subrow "Port" (-> service :addressable :port)))))))

(defsc DeviceInfo [this
                   {:keys [id type name description profile labels adminState operatingState lastConnected lastReported
                           protocols service autoEvents]}]
  {:ident [:device :id]
   :query [:id :type :name :description :profile :labels :adminState :operatingState :lastConnected :lastReported
           :protocols :service :autoEvents]}
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
             (dom/div {:className "card"}
                      (dom/div {:className "fixed-table-toolbar"}
                               (dom/div {:className "bars pull-right"}
                                        (b/button
                                          {:onClick #(routing/nav-to! this :main)}
                                          (dom/i {:className "glyphicon fa fa-caret-square-o-left"}))))
                      (dom/div {:className "header"}
                               (dom/h4 {:className "title"} "Device"))
                      (device-general-table id name description profile protocols labels)
                      (auto-events-sub-table autoEvents))
             (dom/div {:className "card"}
                      (dom/div {:className "header"}
                               (dom/h4 {:className "title"} "Status"))
                      (dom/div {:className "table-responsive"}
                               (dom/table {:className "table table-bordered"}
                                          (dom/tbody nil
                                                     (t/row "Admin State" admin-str)
                                                     (t/row "Operating State" op-str)
                                                     (t/row "Last Connected" connected-str)
                                                     (t/row "Last Reported" reported-str)))))
             (dom/div {:className "card"}
                      (dom/div {:className "header"}
                               (dom/h4 {:className "title"} "Service"))
                      (dom/div {:className "table-responsive"}
                               (dom/table {:className "table table-bordered"}
                                          (dom/tbody nil
                                                     (t/row "Name" (:name service))
                                                     (t/row "Last Connected" serv-connected-str)
                                                     (t/row "Last Reported" serv-reported-str)
                                                     (t/row "Admin State" serv-admin-str)
                                                     (t/row "Operating State" serv-op-str)
                                                     (dom/tr nil
                                                             (dom/th {:rowSpan "5"} "Address")
                                                             (dom/th nil "Name")
                                                             (dom/td nil (-> service :addressable :name)))
                                                     (t/subrow "Protocol" (-> service :addressable :protocol))
                                                     (t/subrow "Address" (-> service :addressable :address))
                                                     (t/subrow "Port" (-> service :addressable :port))
                                                     (t/subrow "Path" (-> service :addressable :path)))))))))

(defn render-field
  "A helper function for rendering just the fields."
  [component field renderer]
  (let [form         (prim/props component)
        entity-ident (prim/get-ident component form)
        id           (str (first entity-ident) "-" (second entity-ident))
        is-dirty?    (fs/dirty? form field)
        clean?       (not is-dirty?)
        validity     (fs/get-spec-validity form field)
        is-invalid?  (= :invalid validity)
        value        (get form field "")]
    (renderer {:dirty?   is-dirty?
               :ident    entity-ident
               :id       id
               :clean?   clean?
               :validity validity
               :invalid? is-invalid?
               :value    value})))

(defn input-with-label
  "A non-library helper function, written by you to help lay out your form."
  ([component field field-label validation-string placeholder error-fn input-element]
   (render-field component field
                 (fn [{:keys [invalid? id dirty?]}]
                   (b/labeled-input {:error           (or (when invalid? validation-string) (when error-fn (error-fn)))
                                     :id              id
                                     :placeholder     placeholder
                                     :input-generator input-element} field-label))))
  ([component field field-label validation-string placeholder error-fn]
   (render-field component field
                 (fn [{:keys [invalid? id dirty? value invalid ident]}]
                   (b/labeled-input {:value    value
                                     :id       id
                                     :error    (or (when invalid? validation-string) (when error-fn (error-fn)))
                                     :placeholder placeholder
                                     :onBlur   #(prim/transact! component `[(fs/mark-complete! {:entity-ident ~ident
                                                                                                :field        ~field})])
                                     :onChange #(m/set-string! component field :event %)} field-label)))))

(defn mk-radio-option [this radio-name current field kind name]
  (let [key (str kind)]
    (dom/div :.radio {:key key}
             (dom/input {:type "radio",
                         :id key
                         :value kind
                         :name radio-name
                         :checked (= current kind)
                         :onChange (fn [evt]
                                     (m/set-value! this field kind))})
             (dom/label {:htmlFor key} name))))

(defsc DeviceType [this {:keys [:ui/device-type]}]
  {:query [:ui/device-type fs/form-config-join]
   :initial-state (fn [p] {:ui/device-type ::modbus})
   :form-fields #{:ui/device-type}
   :ident (fn [] [:device-type-subform :singleton])}
  (let [options [[::modbus "Modbus"] [::other "Generic"]]    ;[::opc-ua "OPC-UA"] [::bacnet-ip "BACnet/IP"] [::bacnet-mstp "BACnet/MSTP"]; [::mqtt "MQTT"]
                                                             ; remove the other protocols as the devices services are not supported yet
        gen-opt #(mk-radio-option this "device-type" device-type :ui/device-type (first %) (second %))]
    (dom/div :.form-horizontal
             (dom/fieldset nil
                           (dom/div :.form-group
                                    (dom/label :.col-sm-3.control-label "Device Type")
                                    (dom/div :.col-sm-9 (mapv gen-opt options)))))))

(def ui-device-type (prim/factory DeviceType))

(s/def :device/name #(re-matches #"\S+" %))
(s/def :device/port #(re-matches #"^([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$" %))
(s/def :modbus-device/path #(re-matches #"^(1?[0-9]{1,2}|2[0-4][0-9]|25[0-5])$" %))
(s/def :modbus-device/serial-device #(re-matches #"/dev/.+" %))

(defsc ModbusDeviceEntry [this {:keys [device/protocol device/address device/port modbus-device/unit-id
                                       modbus-device/serial-device modbus-device/baud-rate modbus-device/parity
                                       modbus-device/stop-bits]}]
  {:query [:device/protocol :device/address :device/port :modbus-device/unit-id :modbus-device/baud-rate
           :modbus-device/stop-bits :modbus-device/parity :modbus-device/serial-device fs/form-config-join]
   :initial-state (fn [p] {:device/protocol "TCP" :device/address "" :device/port "502" :modbus-device/unit-id "1"
                           :modbus-device/serial-device "/dev/ttyS1" :modbus-device/stop-bits "1"
                           :modbus-device/baud-rate "9600" :modbus-device/parity "N"})
   :form-fields #{:device/address :device/port :modbus-device/unit-id :modbus-device/serial-device
                  :modbus-device/baud-rate :modbus-device/parity}
   :ident (fn [] [::modbus :singleton])}
  (let [gen-protocol-opt #(mk-radio-option this "protocol" protocol :device/protocol % %)
        gen-stop-bits-opt #(mk-radio-option this "stop-bits" stop-bits :modbus-device/stop-bits % %)
        gen-parity-opt (fn [val name] (mk-radio-option this "parity" parity :modbus-device/parity name val))
        gen-baud-opt #(mk-radio-option this "baud" baud-rate :modbus-device/baud-rate % %)
        baud-rates ["1200" "2400" "4800" "9600" "19200" "38400" "57600" "115200"]
        baud-opts (mapv gen-baud-opt baud-rates)]
    (dom/div :.form-group
             (dom/div :.radio-btn-row
                      (dom/div :.col-sm-4
                               (dom/label :.control-label"Modbus Type:"))
                      (dom/div :.col-sm-8
                               (gen-protocol-opt "TCP")
                               (gen-protocol-opt "RTU")))
             (if (= protocol "TCP")
               (dom/div nil
                        (input-with-label this :device/address "Host:" "" "DNS name or IP address" nil)
                        (input-with-label this :device/port "Port:" "Invalid port number" "Port number (1 - 65535)" nil))
               (dom/div nil
                        (input-with-label this :modbus-device/serial-device "Serial Device:" "Invalid" "/dev/..." nil)
                        (dom/div :.form-group
                                 (dom/div :.inner-radio-btn-row
                                          (dom/div :.col-sm-4
                                                   (dom/label :.control-label "Baud rate:"))
                                          (dom/div :.col-sm-8 baud-opts))
                                 (dom/div :.inner-radio-btn-row
                                          (dom/div :.col-sm-4
                                                   (dom/label :.control-label "Parity:"))
                                          (dom/div :.col-sm-8
                                                   (gen-parity-opt "None" "N")
                                                   (gen-parity-opt "Even" "E")
                                                   (gen-parity-opt "Odd" "O")))
                                 (dom/div :.inner-radio-btn-row
                                          (dom/div :.col-sm-4
                                                   (dom/label :.control-label "Stop bits:"))
                                          (dom/div :.col-sm-8
                                                   (gen-stop-bits-opt "1")
                                                   (gen-stop-bits-opt "2"))))))
             (input-with-label this :modbus-device/unit-id "Unit Identifier:" "Invalid" "0 - 255" nil))))

(def ui-modbus-device-entry (prim/factory ModbusDeviceEntry))

(defn modbus-info [{:keys [device/protocol device/address device/port modbus-device/unit-id modbus-device/serial-device
                           modbus-device/baud-rate modbus-device/parity modbus-device/stop-bits]}]
  (let [base {:UnitID unit-id}
        info (if (= protocol "TCP")
               {:Address address
                :Port port}
               {:Address serial-device
                :BaudRate baud-rate
                :DataBits "8"
                :StopBits stop-bits
                :Parity parity})
        protocol-key (if (= protocol "TCP") :modbus-tcp :modbus-rtu)]
    {:protocols {protocol-key (merge base info)}}))

(defsc OPCUADeviceEntry [this {:keys [device/address device/port device/path device/topic]}]
  {:query [:device/address :device/port :device/path :device/topic fs/form-config-join]
   :initial-state (fn [p] {:device/address "" :device/port "1000" :device/path "" :device/topic "None"})
   :form-fields #{:device/address :device/port :device/path :device/topic}
   :ident (fn [] [::opc-ua :singleton])}
  (let [gen-security-opt #(mk-radio-option this "topic" topic :device/topic % %)]
    (dom/div :.form-group
             (input-with-label this :device/address "Host:" "" "DNS name or IP address" nil)
             (input-with-label this :device/path "Path:" "" "Path" nil)
             (input-with-label this :device/port "Port:" "Invalid port number" "Port number (1 - 65535)" nil)
             (dom/div :.form-group
                      (dom/label :.control-label "Security Policy:")
                      (dom/div nil
                               (gen-security-opt "None")
                               (gen-security-opt "Basic128Rsa15")
                               (gen-security-opt "Basic256Sha256"))))))

(defn opc-ua-info [{:keys [device/address device/port device/path device/topic]}]
  {:protocol "OTHER"
   :address address
   :port (js/parseInt port)
   :path path
   :topic topic})

(def ui-opcua-device-entry (prim/factory OPCUADeviceEntry))

(def ui-toggle (co/factory-apply ToggleWidget/default))

(defsc BACnetIPDeviceEntry [this {:keys [device/address device/port]}]
  {:query [:device/address :device/port fs/form-config-join]
   :initial-state (fn [p] {:device/address "" :device/port "47808"})
   :form-fields #{:device/address :device/port}
   :ident (fn [] [::bacnet-ip :singleton])}
  (dom/div :.form-group
           (input-with-label this :device/address "Address:" "" "BACnet device instance" nil)
           (input-with-label this :device/port "Port:" "Invalid port number" "Port number (1 - 65535)" nil)))

(def ui-bacnet-ip-device-entry (prim/factory BACnetIPDeviceEntry))

(defn bacnet-ip-info [{:keys [device/address device/port]}]
  {:address address
   :port (js/parseInt port)})

(defsc BACnetMSTPDeviceEntry [this {:keys [device/address device/path]}]
  {:query [:device/address :device/path fs/form-config-join]
   :initial-state (fn [p] {:device/address "" :device/path ""})
   :form-fields #{:device/address :device/path}
   :ident (fn [] [::bacnet-mstp :singleton])}
  (dom/div :.form-group
           (input-with-label this :device/address "Address:" "" "BACnet device instance" nil)
           (input-with-label this :device/path "Serial Device:" "Invalid" "/dev/..." nil)))

(def ui-bacnet-mstp-device-entry (prim/factory BACnetMSTPDeviceEntry))

(defn bacnet-mstp-info [{:keys [device/address device/path]}]
  {:address address
   :path path})

(declare ProtocolTable)
(declare ui-prt-table)

(defn other-info [{:keys [ui/prts prt-props]}]
  {:protocols {prts prt-props}})

(defsc DeviceTypeEntry [this {:keys [modbus opcua bacnet-ip bacnet-mstp other device-type]}]
  {:ident (fn [] [:device-type-entry :singleton])
   :query [{:modbus (prim/get-query ModbusDeviceEntry)}
           {:opcua (prim/get-query OPCUADeviceEntry)}
           {:bacnet-ip (prim/get-query BACnetIPDeviceEntry)}
           {:bacnet-mstp (prim/get-query BACnetMSTPDeviceEntry)}
           {:other (prim/get-query ProtocolTable)}
           fs/form-config-join :device-type]
   :initial-state (fn [p] {:modbus (prim/get-initial-state ModbusDeviceEntry {})
                           :opcua (prim/get-initial-state OPCUADeviceEntry {})
                           :bacnet-ip (prim/get-initial-state BACnetIPDeviceEntry {})
                           :bacnet-mstp (prim/get-initial-state BACnetMSTPDeviceEntry {})
                           :other (prim/get-initial-state ProtocolTable {})})
   :form-fields #{:modbus :opcua :bacnet-ip :bacnet-mstp :other}}
  (dom/div nil
           (case device-type
             ::modbus (ui-modbus-device-entry modbus)
             ::opc-ua (ui-opcua-device-entry opcua)
             ::bacnet-ip (ui-bacnet-ip-device-entry bacnet-ip)
             ::bacnet-mstp (ui-bacnet-mstp-device-entry bacnet-mstp)
             ::other (ui-prt-table other))))

(def ui-device-type-entry (prim/factory DeviceTypeEntry))

(def max-file-size (* 2 1024 1024))

(defmutation upload-file
  "Mutation: Start uploading a file. This mutation will always be something the app itself writes, because the UI
  will need to be updated. That said, adding the file to the client database is done via the helpers
  add-file* and file-path."
  [{::fu/keys [comp-id id size abort-id] :as file}]
  (action [{:keys [state]}]
          (swap! state (fn [s]
                         (-> s
                             ;; this part you'll always do. won't add the file if it is too big
                             (fu/add-file* file max-file-size)
                             (integrate-ident* (fu/file-path id) :append [:file-upload comp-id :files])))))
  (file-upload [{:keys [ast]}]
               (when-not (>= size max-file-size)                       ; don't even start networking if the file is too big.
                 (-> ast
                     (m/with-abort-id abort-id)
                     (m/with-progressive-updates `(fu/update-progress ~file))))))

(defn delete-file-from-list*
  "Local mutation helper to remove a file from file list."
  [state-map comp-id id]
  (update-in state-map [:file-upload comp-id :files] (fn [list] (vec (filter #(not= (fu/file-path id) %) list)))))

(defn delete-file*
  "Local mutation helper to remove a file from the file table."
  [state-map id]
  (update state-map (first (fu/file-path id)) dissoc id))

(defmutation delete-file
  "Mutation to delete file from UI and client database. Client-specific since it has to modify UI state that is unknown
  to file upload system."
  [{:keys [comp-id id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (delete-file-from-list* comp-id id)
                                   (delete-file* id))))))
;; remote side runs if the ID is real, so the server can clean it up. We rename the mutation in order to leverage
;; the pre-written delete file mutation on the server (see server portion of file-upload namespace).
;; TODO: Implement remote file deletion in Go server.
;(remote [{:keys [ast]}]
;  (when-not (prim/tempid? id)
;    (assoc ast :key `fu/delete-file :dispatch-key `fu/delete-file))))

(defmutation update-add-device-profile
  [{:keys [comp-id file-id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-profiles*)
                                   (delete-file-from-list* comp-id file-id)
                                   (delete-file* file-id)))))
  (refresh [env] [:upload-id]))

(defn show-commands [this id]
  (prim/transact! this `[(load-commands {:id ~id})])
  (routing/nav-to! this :control {:id id}))

(defsc FileUpload
  [this {:keys [upload-id id files]}]
  {:query         [:upload-id :id {:files (prim/get-query fu/File)}]
   :initial-state (fn [p] (merge p {:id 0 :files []}))
   :ident         [:file-upload :upload-id]}
  (let [file-id (->> files (map :org.edgexfoundry.ui.manager.ui.file-upload/id) (reduce max))
        disable-upload? (nil? file-id)]
    (dom/div nil
             (dom/label :.btn.btn-default
                        {:disabled file-id} (dom/i :.glyphicon.fa.fa-plus)
                        (dom/input {:id       id
                                    :onChange (fn [evt]
                                                (prim/transact! this
                                                                (mapv (fn [file] `(upload-file ~file))
                                                                      (fu/evt->upload-files upload-id evt))))
                                    :name     id
                                    :multiple false
                                    :type     "file"
                                    :accept   "application/x-yaml"
                                    :value    ""
                                    :style    {:display "none"}}))
             (b/button {:onClick (fn [evt]
                                   (prim/transact! this [`(mu/upload-profile {:file-id ~file-id})])
                                   (df/load this :q/edgex-profiles ProfileListEntry
                                            {:target (df/multiple-targets
                                                       (conj co/profile-list-ident :content)
                                                       (conj co/new-device-ident :profiles)
                                                       (conj co/new-device-entry :profiles))
                                             :marker false
                                             :fallback `d/show-error
                                             :post-mutation `update-add-device-profile
                                             :post-mutation-params {:comp-id upload-id :file-id file-id}}))
                        :style #js {:margin "0 2px"}
                        :disabled disable-upload?}
                       (dom/i :.glyphicon.fa.fa-upload))
             (map (fn [f] (fu/ui-file f {;:onCancel (fn [id abort-id]
                                         ;            (prim/transact! this `[(abort-upload {:id ~abort-id})]))
                                         :onDelete (fn [id]
                                                     (prim/transact! this `[(delete-file {:comp-id ~upload-id :id ~file-id})]))})) files))))

(def ui-file-upload (prim/factory FileUpload {:keyfn :upload-id}))

(defsc DeviceNames [this {:keys [id name]}]
  {:ident [:device :id]
   :query [:id :name]})

(defsc ProfileListEntry [this {:keys [id type name manufacturer model description]}]
  {:ident [:device-profile :id]
   :query [:id :type :name :manufacturer :model :description]})

(defsc ServiceListEntry [this {:keys [id type name]}]
  {:ident [:device-service :id]
   :query [:id :type :name]})

(defn mk-td-item [[k v]]
  (dom/tr {:key k}
          (dom/td (name k))
          (dom/td v)
          (dom/td "")))

(defsc ProtocolTable [this {:keys [ui/prts ui/prt-name ui/prt-value prt-props]}]
  {:initial-state (fn [p] {:ui/prts "" :ui/prt-name "" :ui/prt-value "" :prt-props {}})
   :query [:ui/prts :ui/prt-name :ui/prt-value :prt-props fs/form-config-join]
   :form-fields #{:ui/prts :ui/prt-name :ui/prt-value :prt-props}
   :ident (fn [] [::protocols :singleton])}
  (let [add-prt-prop (fn [] (let [new-props (conj prt-props {(keyword prt-name) prt-value})]
                              (m/set-value! this :prt-props new-props)
                              (m/set-value! this :ui/prt-name "")
                              (m/set-value! this :ui/prt-value "")))
        set-prts (fn [e] (m/set-value! this :ui/prts (keyword (.. e -target -value))))]
    (dom/div {:className "form-group"}
             (dom/div {:className "prts"}
                      (dom/h5 "Protocols")
                      (b/labeled-input {:id "prts" :value prts  :type "text" :split 3 :placeholder "Enter Protocol ..."
                                        :onChange set-prts} ""))
             (dom/div {:className "table-responsive"}
                      (dom/table {:className "table table-condensed prt-table"}
                                 (dom/thead nil
                                            (dom/tr nil
                                                    (dom/th nil "Property Name")
                                                    (dom/th nil "Property Value")
                                                    (dom/th nil "")))
                                 (dom/tbody nil
                                            (if (empty? prt-props)
                                              (dom/tr nil
                                                      (dom/td "N/A"))
                                              (map #(mk-td-item %) prt-props)))
                                 (dom/tfoot nil
                                            (dom/tr nil
                                                    (dom/th nil
                                                            (b/labeled-input {:id "prt-name" :value prt-name  :type "text" :split 3 :placeholder "Enter Name ..."
                                                                              :onKeyDown (fn [evt] (when (evt/enter-key? evt) #(add-prt-prop)))
                                                                              :onChange #(m/set-string! this :ui/prt-name :event %)} nil))
                                                    (dom/th nil
                                                            (b/labeled-input {:id "prt-value" :value prt-value  :type "text" :split 3 :placeholder "Enter Value ..."
                                                                              :onKeyDown (fn [evt] (when (evt/enter-key? evt) #(add-prt-prop)))
                                                                              :onChange #(m/set-string! this :ui/prt-value :event %)} nil))
                                                    (dom/th nil
                                                            (b/button {:key "back-button" :className "btn-fill" :kind :info
                                                                       :onClick add-prt-prop} (tr "Add"))))))))))

(def ui-prt-table (prim/factory ProtocolTable))

(defsc AutoEventTable [this {:keys [ui/frequency ui/onchange ui/resource ui/autoevents]}]
  {:initial-state (fn [p] {:ui/frequency "" :ui/onchange true :ui/resource "" :ui/autoevents []})
   :query [:ui/frequency :ui/onchange :ui/resource :ui/autoevents]
   :ident (fn [] [::auto-events :singleton])}
  (let [add-prt-prop (fn [] (let [events (conj autoevents {:temp-id (str (random-uuid)) :frequency frequency :onChange onchange :resource resource})]
                              (m/set-value! this :ui/autoevents events)
                              (m/set-value! this :ui/frequency "")
                              (m/set-value! this :ui/resource "")))
        display-item (fn [[k v]]
                       (when (not= k :temp-id)
                         (condp = v
                           true  (dom/div {:key k :className "toggle"} "true")
                           false (dom/div {:key k :className "toggle"} "false")
                           (dom/div {:key k :className "item"} v))))
        delete-event (fn [event]
                       (let [new-events  (filterv #(not= (:temp-id %) (:temp-id event)) autoevents)]
                         (m/set-value! this :ui/autoevents new-events)))
        mk-auto-event-item (fn [event]
                             (dom/div {:className "autoevent-container event-row" :key (:temp-id event)}
                                      (map #(display-item %) event)
                                      (dom/div {:key "close-btn" :className "close"}
                                               (dom/span {:className "fa fa-close"  :onClick #(delete-event event)}))))]
    (dom/div {:className "form-group"}
             (dom/div {:className "prts"}
                      (dom/h5 "Auto Events")

             (dom/div {:className "autoevent-container auto-header"}
                      (dom/div {:className "item"} (tr "Frequency"))
                      (dom/div {:className "toggle"} (tr "On Change"))
                      (dom/div {:className "item"} (tr "Resource"))
                      (dom/div nil ""))
             (mapv #(mk-auto-event-item %) autoevents)
             (dom/div {:className "autoevent-container footer"}
                      (dom/div {:className "item"}
                               (b/labeled-input {:id        "frequency" :value frequency :type "text" :split 0 :placeholder "Type frequency"
                                                 :onKeyDown (fn [evt] (when (evt/enter-key? evt) #(add-prt-prop)))
                                                 :onChange  #(m/set-string! this :ui/frequency :event %)} ""))
                      (dom/div {:className "toggle"}

                                                (ui-toggle {:checked onchange :onChange (fn [v] (m/set-value! this :ui/onchange v))}))

                      (dom/div {:className "item"}
                               (b/labeled-input {:id "resource" :value resource  :type "text" :split 0 :placeholder ""
                                                 :onKeyDown (fn [evt] (when (evt/enter-key? evt) #(add-prt-prop)))
                                                 :onChange #(m/set-string! this :ui/resource :event %)} ""))

                      (dom/div nil (b/button {:key     "back-button" :className "btn-fill" :kind :info
                                 :onClick add-prt-prop} (tr "Add"))))))))

(def ui-autoevent-table (prim/factory AutoEventTable))

(defsc DeviceEntry [this {:keys [device/name device/description device/labels device/service device/profile
                                 new-device devices profiles services ui/dropdown ui/dropdown2 device-type profile-file device/auto-events]}]
  {:query [:device/name :device/description :device/labels :device/profile :device/service fs/form-config-join :device-type
           {:new-device (prim/get-query DeviceTypeEntry)}
           {:devices (prim/get-query DeviceNames)}
           {:profiles (prim/get-query ProfileListEntry)}
           {:services (prim/get-query ServiceListEntry)}
           {:ui/dropdown (prim/get-query b/Dropdown)}
           {:ui/dropdown2 (prim/get-query b/Dropdown)}
           {:profile-file (prim/get-query FileUpload)}
           {:device/auto-events (prim/get-query AutoEventTable)}]
   :initial-state (fn [p] {:device/name  "" :device/description "" :device/labels []
                           :new-device (prim/get-initial-state DeviceTypeEntry {})
                           :ui/dropdown (prim/get-initial-state b/Dropdown {})
                           :ui/dropdown2 (prim/get-initial-state b/Dropdown {})
                           :profile-file (prim/get-initial-state FileUpload {:upload-id 0})
                           :device/auto-events (prim/get-initial-state AutoEventTable {})})
   :form-fields #{:device/name :device/description :device/labels :device/profile :device/service :new-device}
   :ident (fn [] co/new-device-entry)}
  (let [existing-name? (some #(= (:name %) name) devices)
        error-fn #(when existing-name? "Name not unique")
        get-val #(.. % -target -value)
        non-blank (fn [s] (filterv #(-> % str/blank? not) s))
        evt-to-list #(->> % get-val str/split-lines (mapv str/trim) non-blank)
        change #(m/set-value! this :device/labels (evt-to-list %))
        profile-data (-> (filter #(= profile (:id %)) profiles) first)]
    (dom/div :.form-horizontal
             (dom/fieldset nil
                           (dom/div :.col-sm-10.col-sm-offset-1
                                    (dom/div :.form-group
                                             (input-with-label this :device/name "Name:" "Device name is required"
                                                               "Name of the device" error-fn)
                                             (input-with-label this :device/description "Description:"
                                                               "Device description is required" "Short description" nil)
                                             (input-with-label this :device/labels "Labels:" "" "Labels (one per line)" nil
                                                               (fn [attrs]
                                                                 (let [attrs (merge attrs {:onChange change})]
                                                                   (dom/textarea (clj->js attrs))))))
                                    (ui-device-type-entry (assoc new-device :device-type device-type))
                                    (dom/div :.form-group
                                             (input-with-label this :device/profile "Device Profile:" "" "" nil
                                                               (fn [attrs]
                                                                 (b/ui-dropdown
                                                                   dropdown2
                                                                   :value profile
                                                                   :onSelect (fn [v]
                                                                               (m/set-value! this :device/profile v)
                                                                               (prim/transact! this `[(fs/mark-complete!
                                                                                                        {:field :device/profile})
                                                                                                      :new-device2])))))
                                             (ui-file-upload profile-file)
                                             (dom/div :.table-responsive
                                                      (dom/table :.table.table-bordered
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
                                             (input-with-label this :device/service "Device Service:" "" "" nil
                                                               (fn [attrs]
                                                                 (b/ui-dropdown
                                                                   dropdown
                                                                   :value service
                                                                   :onSelect (fn [v]
                                                                               (m/set-value! this :device/service v)
                                                                               (prim/transact! this `[(fs/mark-complete!
                                                                                                        {:field :device/service})
                                                                                                      :new-device2])))))
                                             (ui-autoevent-table auto-events)))))))

(def ui-device-entry (prim/factory DeviceEntry))

(defn device-data [device-type name description labels new-device profile-name service-name auto-events]
  (let [device {:name name
                :description description
                :labels labels
                :profile-name profile-name
                :service-name service-name}
        extra-data (case device-type
                     ::modbus (-> new-device :modbus modbus-info)
                     ::opc-ua (-> new-device :opcua opc-ua-info)
                     ::bacnet-ip (-> new-device :bacnet-ip bacnet-ip-info)
                     ::bacnet-mstp (-> new-device :bacnet-mstp bacnet-mstp-info)
                     ::other (-> new-device :other other-info))]
    (-> device
        (merge extra-data)
        (merge auto-events))))

(defn add-new-device [comp {:keys [device-entry device-type-selection] :as props}]
  (let [device-type (:ui/device-type device-type-selection)
        {:keys [device/name device/description device/labels device/profile device/service new-device profiles services device/auto-events]} device-entry
        profile-name (-> (filter #(= profile (:id %)) profiles) first :name)
        service-name (-> (filter #(= service (:id %)) services) first :name)
        {:keys [ui/autoevents]} auto-events
        final-autoevents {:autoEvents (mapv #(select-keys % [:frequency :onChange :resource]) autoevents)}
        device (device-data device-type name description labels new-device profile-name service-name final-autoevents)]
    (prim/transact! comp `[(mu/add-device ~device)
                           (fs/reset-form!)
                           (b/hide-modal {:id :add-device-modal})
                           (df/fallback {:action ld/reset-error})])
    (df/load comp co/device-list-ident DeviceList)))

(defsc NewDeviceModal [this {:keys [ui/mode device-type-selection device-entry modal modal/page] :as props}]
  {:initial-state (fn [p] {:device-type-selection (prim/get-initial-state DeviceType {})
                           :device-entry (prim/get-initial-state DeviceEntry {})
                           :ui/mode :select-type
                           :modal (prim/get-initial-state b/Modal {:id :add-device-modal :backdrop true})
                           :modal/page :new-device})
   :ident (fn [] co/new-device-ident)
   :form-fields #{:ui/mode :device-type-selection :device-entry}
   :query [:modal/page :ui/mode
           fs/form-config-join
           {:device-type-selection (prim/get-query DeviceType)}
           {:device-entry (prim/get-query DeviceEntry)}
           {:modal (prim/get-query b/Modal)}]}
  (let [existing-name? (some #(= (:name %) (:device/name device-entry)) (:devices device-entry))
        no-service-selected? (-> device-entry :device/service nil?)
        no-profile-selected? (-> device-entry :device/profile nil?)
        device-type (:ui/device-type device-type-selection)
        set-mode #(m/set-value! this :ui/mode %)
        button-label (if (= mode :enter-device) "OK" "Next")
        button-action (case mode
                        :select-type #(set-mode :enter-device)
                        :enter-device #(add-new-device this props))
        disable-button (and (= mode :enter-device) (or (not (fs/checked? props)) (fs/invalid-spec? props) existing-name?
                                                       no-service-selected? no-profile-selected?))
        back-button-action #(set-mode :select-type)]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div {:key "title" :style #js {:fontSize "22px"}} "Add New Device"))
                (dom/div :.header "Add New Device")
                (b/ui-modal-body nil
                                 (dom/div :.card
                                          (dom/div :.content
                                                   (if (= mode :select-type)
                                                     (ui-device-type device-type-selection)
                                                     (ui-device-entry (assoc device-entry :device-type device-type))))))
                (b/ui-modal-footer nil
                                   (when (not= mode :select-type)
                                     (b/button {:key "back-button" :className "btn-fill" :kind :info
                                                :onClick back-button-action}
                                               "Back"))
                                   (b/button {:key "ok-next-button" :className "btn-fill" :kind :info
                                              :onClick button-action :disabled disable-button} button-label)
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :add-device-modal})
                                                                               (fs/reset-form!)])}
                                             "Cancel")))))

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
            (dom/td {:className "text-center"}
                    (dom/i {:className op-icon :style #js {:color op-icon-color} :aria-hidden "true"}))
            (dom/td {:className "text-center"}
                    (dom/i {:className lock-icon :aria-hidden "true"}))
            (dom/td nil lastConnected-str)
            (dom/td {:className "td-actions text-right"}
                    (dom/button
                      {:type "button",
                       :rel "tooltip",
                       :title "View Device",
                       :className "btn btn-info btn-simple btn-xs"
                       :onClick #(onShow id)}
                      (dom/i {:className "fa fa-info"}))
                    (dom/button
                      {:type "button",
                       :rel "tooltip",
                       :title lock-action-tip,
                       :className "btn btn-success btn-simple btn-xs"
                       :onClick #(show-admin-status-modal this id)}
                      (dom/i {:className lock-action-icon}))
                    (dom/button
                      {:type "button",
                       :rel "tooltip",
                       :title "Control Device",
                       :className "btn btn-danger btn-simple btn-xs"
                       :onClick #(onCmds id)}
                      (dom/i {:className "fa fa-dashboard"}))
                    (dom/button
                      {:type "button",
                       :rel "tooltip",
                       :title "Delete Device",
                       :className "btn btn-danger btn-simple btn-xs"
                       :onClick #(onDelete this type id)}
                      (dom/i {:className "fa fa-times"}))))))

(deftable DeviceList :show-devices :device [[:name "Name"] [:operatingState "Op Status"] [:adminState "Admin Status"]
                                            [:lastConnected "Last Seen"]]
          [{:onClick #(show-add-device-modal this) :icon "plus"}
           {:onClick #(ld/refresh! this) :icon "refresh"}]
          :modals [{:modal d/DeleteModal :params {:modal-id :dd-modal}}]
          :actions [{:title :onShow :action-class :info :symbol "info" :onClick (fn [id] (routing/nav-to! this :info {:id id}))}
                    {:title :onCmds :action-class :danger :symbol "times"
                     :onClick (fn [id] (show-commands this id))}
                    {:title :onDelete :action-class :danger :symbol "times" :onClick (d/mk-show-modal :dd-modal)}]
          :row-symbol DeviceListEntry)
