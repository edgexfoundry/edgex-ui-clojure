;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.exports
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.routing :as r]
            [fulcro.client.data-fetch :as df]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.forms :as f]
            [fulcro.ui.form-state :as fs]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.labels :as lbl]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.devices :as devices]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            ["react-widgets" :refer [Multiselect]]
            ["rc-switch" :as ToggleWidget]))

(defn reset-add-export* [state]
  (update-in state co/new-export-ident #(merge % {:id nil
                                                  :ui/step 1})))

(declare ExportModal)

(defmutation get-devices-readings
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (let [deviceIDs (mapv #(last %) (get-in s [:show-devices :singleton :content]))
                                     deviceNames (mapv #(get-in s [:device % :name]) deviceIDs)
                                     profileIDs (mapv #(last %) (get-in s [:show-profiles :singleton :content]))
                                     readings (reduce (fn [col id]
                                                        (let [resources (get-in s [:device-profile id :deviceCommands])
                                                              profileName (get-in s [:device-profile id :name])]
                                                          (mapv #(hash-map :id (-> (:get %)(first)(:object)), :name (-> (:get %)(first)(:object)), :profile profileName) resources))) [] profileIDs)]
                                 (-> s
                                     (assoc-in  [::export-devices :singleton] deviceNames)
                                     (assoc-in  [::export-readings :singleton] readings)))))))

(defn prepare-export* [state new-mode?]
  (let [ref co/new-export-ident
        clear-name #(fs/clear-complete* % [::export-type-subform :singleton] :exp/name)
        clear-name-on-new #(cond-> % new-mode? clear-name)]
    (-> state
        (fs/add-form-config* ExportModal ref)
        (reset-add-export*)
        (fs/mark-complete* ref)
        (clear-name-on-new))))

(defmutation prepare-export
  [{:keys [new-mode?]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (prepare-export* s new-mode?)))))

(defn- initialize-form [state-map form-ident]
  (let [map-method #(case %
                      "POST" :post
                      "PUT" :put
                      "DELETE" :delete
                      :get)
        get-addr #(-> %
                      :addressable
                      (select-keys [:protocol :address :port :path :method :publisher :topic :user :password :cert
                                    :key])
                      (merge %))
        get-filter #(-> %
                        :filter
                        (set/rename-keys {:deviceIdentifiers :device-filter
                                          :valueDescriptorIdentifiers :reading-filter})
                        (merge %))
        get-encrypt #(-> %
                         :encryption
                         (select-keys [:encryptionAlgorithm])
                         (merge %))
        exp (-> state-map
                (get-in form-ident)
                get-addr
                get-filter
                get-encrypt
                (update :method map-method))
        typeEntry (-> exp
                      (select-keys [:name :destination :format :enable :encryptionAlgorithm :encryptionKey :initializingVector :compression])
                      (set/rename-keys {:name :exp/name}))
        addEntry (-> exp
                     (select-keys [:protocol :address :port :path :method :publisher :topic :user :password :cert :key])
                     (set/rename-keys {:address   :exp/address
                                       :port      :exp/port
                                       :publisher :exp/publisher
                                       :topic     :exp/topic}))
        filterEntry (-> exp
                        (select-keys [:device-filter :reading-filter]))]
    (-> state-map
        (update-in co/new-export-ident #(merge % (select-keys exp [:id])))
        (update-in [::export-type-subform :singleton] #(merge % typeEntry))
        (update-in [::export-address-subform :singleton] #(merge % addEntry))
        (update-in [::export-filter-subform :singleton] #(merge % filterEntry)))))

(defmutation prepare-edit-modal
  [{:keys [type id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s]
                         (-> s
                             (initialize-form [type id]))))))

(defn show-add-export-modal [comp]
  (prim/transact! comp `[(prepare-export {:new-mode? true})
                         (get-devices-readings {})
                         (r/set-route {:router :root/modal-router :target ~co/new-export-ident})
                         (b/show-modal {:id :add-export-modal})
                         :modal-router]))

(defn show-edit-modal [comp type id]
  (prim/transact! comp `[(prepare-export {:new-mode? false})
                         (get-devices-readings {})
                         (prepare-edit-modal {:type ~type :id ~id})
                         (r/set-route {:router :root/modal-router :target ~co/new-export-ident})
                         (b/show-modal {:id :add-export-modal})]))

(defn add-export [comp {:keys [id type-entry address-entry filter-entry] :as form}]
  (let [tmp-id (prim/tempid)
        {:keys [exp/name enable destination encryptionAlgorithm encryptionKey initializingVector compression format]} type-entry
        {:keys [protocol exp/address exp/port path method exp/publisher exp/topic user password cert key]} address-entry
        {:keys [device-filter reading-filter]} filter-entry
        exportObj (merge (if (nil? id) {:tempid tmp-id} {:id id})
                         {:name                name
                          :protocol            protocol
                          :address             address
                          :port                port
                          :path                path
                          :method              method
                          :publisher           publisher
                          :topic               topic
                          :user                user
                          :password            password
                          :cert                cert
                          :key                 key
                          :format              format
                          :destination         destination
                          :compression         compression
                          :encryptionAlgorithm encryptionAlgorithm
                          :encryptionKey       encryptionKey
                          :initializingVector  initializingVector
                          :device-filter       device-filter
                          :reading-filter      reading-filter
                          :enable              enable})]
    (if (nil? id)
      (prim/transact! comp `[(b/hide-modal {:id :add-export-modal})
                             (mu/add-export ~exportObj)
                             (fs/reset-form!)
                             (df/fallback {:action ld/reset-error})])
      (prim/transact! comp `[(b/hide-modal {:id :add-export-modal})
                             (mu/edit-export ~exportObj)
                             (df/fallback {:action ld/reset-error})]))))

(defn edit-export [comp {:keys [id name protocol address port path method publisher topic user password cert key
                                format destination compression encryptionAlgorithm encryptionKey
                                initializingVector device-filter reading-filter enable] :as form}]
  (prim/transact! comp `[(b/hide-modal {:id :edit-export-modal})
                         (mu/edit-export {:id ~id
                                          :name ~name
                                          :protocol ~protocol
                                          :address ~address
                                          :port ~port
                                          :path ~path
                                          :method ~method
                                          :publisher ~publisher
                                          :topic ~topic
                                          :user ~user
                                          :password ~password
                                          :cert ~cert
                                          :key ~key
                                          :format ~format
                                          :destination ~destination
                                          :compression ~compression
                                          :encryptionAlgorithm ~encryptionAlgorithm
                                          :encryptionKey ~encryptionKey
                                          :initializingVector ~initializingVector
                                          :device-filter ~device-filter
                                          :reading-filter ~reading-filter
                                          :enable ~enable})
                         (df/fallback {:action ld/reset-error})]))

(defn do-delete-export [this id]
  (prim/transact! this `[(mu/delete-export {:id ~id})
                         (t/reset-table-page {:id :show-profiles})
                         (df/fallback {:action ld/reset-error})]))

(defmutation update-export-dest
  [{:keys [value]}]
  (action [{:keys [state]}]
          (swap! state assoc-in [::export-type-subform :singleton :format] (condp = value
                                                                             :IOTCORE_TOPIC :IOTCORE_JSON
                                                                             :AZURE_TOPIC :AZURE_JSON
                                                                             :AWS_TOPIC :AWS_JSON
                                                                             :JSON))
          (swap! state assoc-in [::export-address-subform :singleton :protocol] (cond
                                                                                  (#{:IOTCORE_TOPIC :AZURE_TOPIC} value) "tls"
                                                                                  (#{:AWS_TOPIC} value) ""
                                                                                  :else "tcp"))
          (swap! state assoc-in [::export-address-subform :singleton :exp/port] (cond
                                                                                  (#{:IOTCORE_TOPIC :AZURE_TOPIC :AWS_TOPIC} value) 8883
                                                                                  :else "")
                 (swap! state assoc-in [::export-address-subform :singleton :user] (condp = value
                                                                                     :IOTCORE_TOPIC "unused"
                                                                                     "")))))

(defsc AddressableListEntry [this {:keys [id type origin name protocol address port path method
                                          publisher topic user password]}]
  {:ident [:addressable :id]
   :query [:id :type :origin :name :protocol :address :port :path :method :publisher :topic :user :password]})

(defn address-table [{:keys [protocol address port path method publisher topic user]}]
  (let [if-avail #(or % "N/A")]
    (dom/div #js {:className "table-responsive"}
             (dom/table #js {:className "table table-bordered"}
                        (dom/tbody nil
                                   (dom/tr nil
                                           (dom/th nil "Protocol")
                                           (dom/td nil protocol))
                                   (dom/tr nil
                                           (dom/th nil "Address")
                                           (dom/td nil address))
                                   (dom/tr nil
                                           (dom/th nil "Port")
                                           (dom/td nil port))
                                   (dom/tr nil
                                           (dom/th nil "Path")
                                           (dom/td nil path))
                                   (dom/tr nil
                                           (dom/th nil "Method")
                                           (dom/td nil method))
                                   (dom/tr nil
                                           (dom/th nil "Publisher")
                                           (dom/td nil (if-avail publisher)))
                                   (dom/tr nil
                                           (dom/th nil "Topic")
                                           (dom/td nil (if-avail topic)))
                                   (dom/tr nil
                                           (dom/th nil "User")
                                           (dom/td nil (if-avail user))))))))

(def format-dropdowns [(b/dropdown-item :CSV "CSV")
                       (b/dropdown-item :JSON "JSON")
                       (b/dropdown-item :IOTCORE_JSON "JSON (Google IoT Core)")
                       (b/dropdown-item :AWS_JSON "JSON (AWS)")
                       (b/dropdown-item :AZURE_JSON "JSON (Azure)")
                       (b/dropdown-item :THINGSBOARD_JSON "JSON (ThingsBoard)")
                       (b/dropdown-item :XML "XML")
                       (b/dropdown-item :NOOP "None")])

(def destination-dropdowns [(b/dropdown-item :REST_ENDPOINT "REST Endpoint")
                            ;(f/option :ZMQ_TOPIC "ZMQ Topic")])
                            (b/dropdown-item :IOTCORE_TOPIC "MQTT (Google IoT Core)")
                            (b/dropdown-item :AWS_TOPIC "MQTT (AWS)")
                            (b/dropdown-item :AZURE_TOPIC "MQTT (Azure)")
                            (b/dropdown-item :MQTT_TOPIC "MQTT Topic")
                            (b/dropdown-item :XMPP_TOPIC "XMPP")])

(def protocol-dropdowns [(b/dropdown-item "http" "HTTP")
                         (b/dropdown-item "https" "HTTPS")
                         (b/dropdown-item "tcp" "TCP")
                         (b/dropdown-item "tls" "TLS")])

(def method-dropdowns [(b/dropdown-item :get "GET")
                       (b/dropdown-item :post "POST")
                       (b/dropdown-item :put "PUT")
                       (b/dropdown-item :delete "DELETE")])

(def compression-dropdowns [(b/dropdown-item :NONE "None")
                            (b/dropdown-item :GZIP "GZIP")
                            (b/dropdown-item :ZIP "ZIP")])

(def encryption-drowpdowns [(b/dropdown-item :NONE "None")
                            (b/dropdown-item :AES "AES")])
(defmutation change-val
  [{:keys [key val]}]
  (action [{:keys [state]}]
          (swap! state assoc key val)))

(s/def :exp/name #(re-matches #"\S+" %))

(def ui-toggle (co/factory-apply ToggleWidget/default))

(declare isHidden)

(defsc ExpTypeEntry [this {:keys [enable destination format ui/dest-dropdown encryptionAlgorithm ui/enc-alg-dropdown ui/format-dropdown compression ui/comp-dropdown]}]
  {:initial-state (fn [p] {:exp/name "" :enable true :destination :REST_ENDPOINT :encryptionAlgorithm :NONE :format :JSON :compression :NONE
                           :ui/dest-dropdown (b/dropdown :dest-dropdown "Type" destination-dropdowns)
                           :ui/enc-alg-dropdown (b/dropdown :enc-alg-dropdown "Method" encryption-drowpdowns)
                           :ui/format-dropdown (b/dropdown :format-dropdown "" format-dropdowns)
                           :ui/comp-dropdown (b/dropdown :comp-dropdown "" compression-dropdowns)})
   :query         [:exp/name :enable :destination :encryptionAlgorithm :encryptionKey :initializingVector :format :compression fs/form-config-join
                   {:ui/dest-dropdown (prim/get-query b/Dropdown)}
                   {:ui/enc-alg-dropdown (prim/get-query b/Dropdown)}
                   {:ui/format-dropdown (prim/get-query b/Dropdown)}
                   {:ui/comp-dropdown (prim/get-query b/Dropdown)}]
   :form-fields   #{:exp/name :enable :destination :encryptionAlgorithm :encryptionKey :initializingVector :format :compression}
   :ident         (fn [] [::export-type-subform :singleton])}
  (let [hideEncValue? (= encryptionAlgorithm :NONE)
        hideFormat? (isHidden destination :format)]
    (dom/div :.form-group.exportModal
             (co/input-with-label this :destination "Destination:" "" "" nil nil
                                  (fn [attrs]
                                    (b/ui-dropdown dest-dropdown :value destination
                                                   :onSelect (fn [v] (m/set-value! this :destination v)
                                                               (prim/transact! this `[(update-export-dest ~{:value v})])))))
             (co/input-with-label this :exp/name "Name:" "Export Client name is required" "Name of the Export Client" nil {:required true})
             (co/input-with-label this :enable "Enable:" "" "" nil nil
                                  (fn [attrs]
                                    (ui-toggle #js {:checked enable :onChange (fn [v] (m/set-value! this :enable v))})))
             (co/input-with-label this :encryptionAlgorithm "Encryption method:" "" "" nil nil
                                  (fn [attrs]
                                    (b/ui-dropdown enc-alg-dropdown :value encryptionAlgorithm
                                                   :onSelect (fn [v] (m/set-value! this :encryptionAlgorithm v)))))
             (dom/div (when hideEncValue? :$hidden)
                      (co/input-with-label this :encryptionKey "Encryption key:" "" "Path of the encryption key file" nil nil)
                      (co/input-with-label this :initializingVector "Initializing vector:" "" "" nil nil))
             (co/input-with-label this :compression "Compression method:" "" "" nil nil
                                  (fn [attrs]
                                    (b/ui-dropdown comp-dropdown :value compression
                                                   :onSelect (fn [v] (m/set-value! this :compression v)))))
             (dom/div (when hideFormat? :$hidden)
                      (co/input-with-label this :format "Export format:" "" "" nil nil
                                           (fn [attrs]
                                             (b/ui-dropdown format-dropdown :value format
                                                            :onSelect (fn [v] (m/set-value! this :format v)))))))))

(def ui-exp-type-entry (prim/factory ExpTypeEntry))

; exp/address:  Hostname and IP Regex
(s/def :exp/address #(re-matches #"(^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*:?[a-zA-Z0-9\-]*@?[a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])$)|(^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$)" %))
(s/def :exp/port (s/int-in 1 65535))
(s/def :exp/publisher #(re-matches #"\S+" %))
(s/def :exp/topic #(re-matches #"\S+" %))

(defsc ExpAddressEntry [this {:keys [protocol exp/address exp/port method ui/prt-dropdown ui/method-dropdown] :as props}]
  {:initial-state (fn [p] {:protocol "tcp" :exp/address "" :exp/port 1 :path "" :method :post :exp/publisher "" :exp/topic "" :user "" :password "" :cert "" :key ""
                           :ui/prt-dropdown    (b/dropdown :prt-dropdown "Protocol Type" protocol-dropdowns)
                           :ui/method-dropdown (b/dropdown :method-dropdown "Protocol Type" method-dropdowns)})
   :query         [:protocol :exp/address :exp/port :path :method :exp/publisher :exp/topic :user :password :cert :key fs/form-config-join
                   {[::export-type-subform :singleton] (prim/get-query ExpTypeEntry)}
                   {:ui/prt-dropdown (prim/get-query b/Dropdown)}
                   {:ui/method-dropdown (prim/get-query b/Dropdown)}]
   :form-fields   #{:protocol :exp/address :exp/port  :path :method :exp/publisher :exp/topic :user :password :cert :key}
   :ident         (fn [] [::export-address-subform :singleton])}
  (let [dest (:destination (get props [::export-type-subform :singleton]))
        addressPH (condp = dest
                    :AWS_TOPIC "AWS IoT Thing API Endpoint"
                    :AZURE_TOPIC "Azure IoT Hub Hostname"
                    :REST_ENDPOINT "[username[:password]@]hostname"
                    "")
        publisherPH (condp = dest
                      :AZURE_TOPIC "Azure IoT Device ID"
                      :IOTCORE_TOPIC "projects/{project-id}/locations/{cloud-region}/registries/{registry-id}/devices/{device-id}"
                      "")
        topicPH (condp = dest
                  :AWS_TOPIC "AWS IoT Thing"
                  :AZURE_TOPIC "devices/{Azure IoT Device ID}/messages/events/"
                  :IOTCORE_TOPIC "/devices/{device-id}/events"
                  "")
        userPH (condp = dest
                 :AZURE_TOPIC "{Azure IoT Hub Hostname}/{Azure IoT Device ID}/?api-version=2018-06-30"
                 "")
        pwPH (condp = dest
               :AZURE_TOPIC "SAS token"
               :IOTCORE_TOPIC "JWT"
               "")
        hidePrtl? (isHidden dest :protocol)
        hidePort? (isHidden dest :exp/port)
        hidePath? (isHidden dest :path)
        hideMethod? true
        hidePub? (isHidden dest :exp/publisher)
        hideTopic? (isHidden dest :exp/topic)
        hideUser? (isHidden dest :user)
        hidePW? (isHidden dest :password)
        hideCertKey? (isHidden dest :cert)]
        (dom/div :.form-group
           (dom/div (when hidePrtl? :$hidden)
             (co/input-with-label this :protocol "Protocol:" "" "" nil nil
                                  (fn [attrs]
                                    (b/ui-dropdown prt-dropdown :value protocol
                                                   :onSelect (fn [v] (m/set-value! this :protocol v))))))
           (co/input-with-label this :exp/address "Address:" (if (str/blank? address) "Address is a required field." "Invalid Address.") addressPH nil nil)
           (dom/div (when hidePort? :$hidden)
             (co/input-with-label this :exp/port  "Port:" (if (str/blank? port) "Port is a required field." "Invalid port number.") "Port of the Export Client" nil (if (= dest :AZURE_TOPIC) {:disabled true} nil)))
           (dom/div (when hidePath? :$hidden)
             (co/input-with-label this :path "Path:" "" "URL Path of the Export Client" nil nil))
           (dom/div (when hideMethod? :$hidden)
             (co/input-with-label this :method "Method:" "" "" nil nil
                                  (fn [attrs]
                                    (b/ui-dropdown method-dropdown :value method
                                                   :onSelect (fn [v] (m/set-value! this :method v))))))
           (when-not hidePub? (co/input-with-label this :exp/publisher "Publisher:" (if (= dest :MQTT_TOPIC) "" "Publisher is a required field.") publisherPH nil nil))
           (when-not hideTopic? (co/input-with-label this :exp/topic "Topic:" "Topic is a required field." topicPH nil nil))
           (dom/div (when hideUser? :$hidden)
             (co/input-with-label this :user "User:" "" userPH nil nil))
           (dom/div (when hidePW? :$hidden)
             (if (#{:AZURE_TOPIC :IOTCORE_TOPIC} dest)
               (co/input-with-label this :password "Password:" "" pwPH nil nil
                                    (fn [attrs]
                                      (let [attrs (merge attrs {:onChange (fn [event] (m/set-value! this :password (.. event -target -value)))})]
                                        (dom/textarea (clj->js attrs)))))
               (co/input-with-label this :password "Password:" "" pwPH nil {:type "password"})))
           (dom/div (when hideCertKey? :$hidden)
             (co/input-with-label this :cert "Certificate path:" "" "Path of the certificate file" nil nil))
           (dom/div (when hideCertKey? :$hidden)
             (co/input-with-label this :key "Private key path:" "" "Path of the private key file" nil nil)))))

(def ui-exp-address-entry (prim/factory ExpAddressEntry))

(def ui-multiselect (co/factory-apply Multiselect))

(defsc ExpFilterEntry [this {:keys [device-filter reading-filter] :as props}]
  {:initial-state (fn [p] {:device-filter [] :reading-filter []})
   :query         [:device-filter :reading-filter fs/form-config-join
                   [::export-devices :singleton]
                   [::export-readings :singleton]]
   :form-fields   #{:device-filter :reading-filter}
   :ident         (fn [] [::export-filter-subform :singleton])}
  (let [devices (clj->js (get props [::export-devices :singleton]))
        readings (clj->js (get props [::export-readings :singleton]))]
    (dom/div :.form-group
             (co/input-with-label this :device-filter "Device filter" "" "" nil nil
                                  (fn [attrs]
                                    (ui-multiselect #js {:data devices :placeholder "Type to filter devices..." :filter "contains"
                                                         :value (clj->js device-filter)
                                                         :onChange (fn [v] (m/set-value! this :device-filter v))})))
             (co/input-with-label this :reading-filter "Reading filter" "" "" nil nil
                                  (fn [attrs]
                                    (ui-multiselect #js {:data   readings :valueField "id" :textField "name" :groupBy "profile" :placeholder "Type to filter readings..."
                                                         :filter "contains" :value (clj->js reading-filter)
                                                         :onChange (fn [v]
                                                                     (let [readings (mapv #(. % -name) v)]
                                                                       (m/set-value! this :reading-filter readings)))}))))))

(def ui-exp-filter-entry (prim/factory ExpFilterEntry))

(defn isHidden
  [dest field]
  (condp = field
    :cert (#{:REST_ENDPOINT} dest)
    :format (#{:AWS_TOPIC :AZURE_TOPIC :IOTCORE_TOPIC} dest)
    :protocol (#{:AWS_TOPIC :AZURE_TOPIC :IOTCORE_TOPIC} dest)
    :path (#{:AWS_TOPIC :AZURE_TOPIC :IOTCORE_TOPIC :XMPP_TOPIC} dest)
    :password (#{:AWS_TOPIC :REST_ENDPOINT} dest)
    :user (#{:AWS_TOPIC :IOTCORE_TOPIC :REST_ENDPOINT} dest)
    :exp/port (#{:AWS_TOPIC :AZURE_TOPIC :IOTCORE_TOPIC} dest)
    :exp/publisher (#{:AWS_TOPIC :REST_ENDPOINT :XMPP_TOPIC} dest)
    :exp/topic (#{:REST_ENDPOINT :XMPP_TOPIC} dest)))

(defn getRequiredFields
  [dest step]
  (condp = step
    1 [:exp/name]
    2 (cond
        (= :AWS_TOPIC dest) [:exp/address :exp/topic]
        (#{:AZURE_TOPIC :IOTCORE_TOPIC} dest) [:exp/address :exp/publisher :exp/topic]
        (#{:MQTT_TOPIC :REST_ENDPOINT :XMPP_TOPIC} dest) [:exp/address :exp/port]
        :else [])
    []))

(defsc ExportModal [this {:keys [id type-entry address-entry filter-entry modal ui/step] :as props}]
  {:initial-state (fn [p] {:modal         (prim/get-initial-state b/Modal {:id :add-export-modal :backdrop true})
                           :modal/page    :new-export
                           :type-entry    (prim/get-initial-state ExpTypeEntry {})
                           :address-entry (prim/get-initial-state ExpAddressEntry {})
                           :filter-entry  (prim/get-initial-state ExpFilterEntry {})
                           :ui/step       1})
   :ident         (fn [] co/new-export-ident)
   :query         [{:type-entry (prim/get-query ExpTypeEntry)}
                   {:address-entry (prim/get-query ExpAddressEntry)}
                   {:filter-entry (prim/get-query ExpFilterEntry)}
                   :ui/step
                   fs/form-config-join
                   :id :modal/page {:modal (prim/get-query b/Modal)}]
   :form-fields   #{:ui/step :type-entry :address-entry :filter-entry}}
  (let [set-step #(m/set-value! this :ui/step %)
        button-label (if (= step 3) "OK" "Next")
        form (condp = step
               1 type-entry
               2 address-entry
               3 filter-entry)
        dest (:destination type-entry)
        reqFields (getRequiredFields dest step)
        clearFields (filterv #{:exp/address :exp/port :exp/topic :exp/publisher} (getRequiredFields dest (inc step)))
        mkClearMutation (fn [field] `(fs/clear-complete! {:entity-ident [::export-address-subform :singleton] :field ~field}))
        clearMutations (mapv mkClearMutation clearFields)
        button-action (fn [_]
                        (let [ident (condp = step
                                      1 [::export-type-subform :singleton]
                                      2 [::export-address-subform :singleton]
                                      3 [::export-filter-subform :singleton])]
                          (when (nil? id) (prim/transact! this (conj `[(fs/mark-complete! {:entity-ident ~ident})
                                                                       ~@clearMutations])))
                          (if (< step 3)
                            (when (every? #(fs/valid-spec? form %) reqFields) (set-step (inc step)))
                            (add-export this props))))
        back-button-action #(set-step (dec step))
        disable-button (some #(fs/invalid-spec? form %) reqFields)
        modal-title  (if (nil? id) "Add Export" "Edit Export")]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} modal-title)
                                  (dom/ul #js {:className "progressLine"}
                                          (dom/li (when (= step 1) :$active) "Destination & Data Format")
                                          (dom/li (when (= step 2) :$active) "Address Info.")
                                          (dom/li (when (= step 3) :$active) "Filters")))
                (b/ui-modal-body {:className "exportModal"}
                                 (dom/div #js {:className "card"}
                                          (dom/div #js {:className "content"}
                                                   (case step
                                                     1 (ui-exp-type-entry type-entry)
                                                     2 (ui-exp-address-entry address-entry)
                                                     3 (ui-exp-filter-entry filter-entry)))))
                (b/ui-modal-footer nil
                                   (when (not= step 1)
                                     (b/button {:key     "back-button" :className "btn-fill" :kind :info
                                                :onClick back-button-action} "Back"))
                                   (b/button {:key      "ok-next-button" :className "btn-fill" :kind :info
                                              :onClick button-action :disabled disable-button} button-label)
                                   (b/button {:key     "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :add-export-modal})
                                                                               (fs/reset-form!)
                                                                               (fs/clear-complete!)])} "Cancel")))))

(defn conv-dest [_ dest]
  (case dest
    :MQTT_TOPIC "MQTT"
    :IOTCORE_TOPIC "IoT Core"
    :AZURE_TOPIC "Azure"
    :XMPP_TOPIC "XMPP"
    :ZMQ_TOPIC "ZMQ"
    :REST_ENDPOINT "REST"
    :AWS_TOPIC "AWS"
    "Unknown"))

(defn conv-format [_ format]
  (case format
    :JSON "JSON"
    :XML "XML"
    :IOTCORE_JSON "JSON (IoT Core)"
    :AZURE_JSON "JSON (Azure)"
    :THINGSBOARD_JSON "JSON (ThingsBoard)"
    :NOOP "None"
    "Unknown"))

(defn conv-compression [_ comp]
  (case comp
    :NONE "None"
    :GZIP "GZIP"
    :ZIP "ZIP"
    "Unknown"))

(defn conv-encryption [_ enc]
  (case (:encryptionAlgorithm enc)
    :NONE "None"
    :AES "AES"
    "Unknown"))

(deftable ExportList :show-exports :export [[:name "Name"]
                                            [:format "Format" conv-format]
                                            [:destination "Destination" conv-dest]
                                            [:encryption "Encryption" conv-encryption]
                                            [:compression "Compression" conv-compression]
                                            [:enable "Enable"]]
          [{:onClick #(show-add-export-modal this) :icon "plus"}
           {:onClick #(ld/refresh! this) :icon "refresh"}]
          :modals [{:modal d/DeleteModal :params {:modal-id :de-modal} :callbacks {:onDelete do-delete-export}}]
          :actions [{:title "Edit Export" :action-class :danger :symbol "edit" :onClick show-edit-modal}
                    {:title "Delete Export" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :de-modal)}])
