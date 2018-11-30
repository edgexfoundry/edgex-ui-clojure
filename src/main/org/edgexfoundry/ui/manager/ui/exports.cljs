;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.exports
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.forms :as f]
            [fulcro.client.mutations :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [clojure.set :as set]))

(defn reset-add-export* [state]
  (update-in state co/new-export-ident #(merge % {:name ""
                                                  :format :JSON
                                                  :destination :REST_ENDPOINT
                                                  :compression :NONE
                                                  :encryptionAlgorithm :NONE
                                                  :encryptionKey ""
                                                  :initializingVector ""
                                                  :enable false})))

(defn assoc-options [state ident field opts default]
  (let [path (into ident [:fulcro.ui.forms/form :elements/by-name field])]
    (-> state
        (assoc-in (conj path :input/options) opts)
        (assoc-in (conj path :input/default-value) default)
        (assoc-in (conj ident field) default))))

(defn set-unused-addressables* [state ident current-addr]
  (let [mk-id-set (fn [m] (into #{} (map #(-> % :addressable :id) (vals m))))
        dsa-ids (mk-id-set (:device-service state))
        sa-ids (mk-id-set (:schedule-event state))
        addrs (vals (:addressable state))
        a-ids (into #{} (map :id addrs))
        unused-ids (set/difference a-ids dsa-ids sa-ids)
        unused-plus-current (if current-addr
                              (conj unused-ids current-addr)
                              unused-ids)
        selected-addr (filter #(contains? unused-plus-current (:id %)) addrs)
        opts (mapv #(f/option (:id %) (:name %)) selected-addr)
        default (or current-addr (-> selected-addr first :id))]
    (assoc-options state ident :addressable opts default)))

(defmutation prepare-add-export
  [noargs]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-unused-addressables* co/new-export-ident nil)
                                   (reset-add-export*))))))

(defn- initialize-form [state-map form-class form-ident]
  (let [exp (-> state-map
                (get-in form-ident))]
    (-> state-map
        (update-in co/edit-export-ident #(merge % exp)))))

(declare EditExportModal)

(defn fixup-export-addressable-id [state type id]
  (let [addr-name (get-in state [type id :addressable :name])
        match-name #(= addr-name (:name %))
        addr (->> state :addressable vals (filter match-name) first)]
    (:id addr)))

(defmutation prepare-edit-modal
  [{:keys [type id]}]
  (action [{:keys [state]}]
    (swap! state (fn [s]
                   (-> s
                       (initialize-form EditExportModal [type id])
                       (set-unused-addressables* co/edit-export-ident (fixup-export-addressable-id s type id)))))))

(defn show-add-export-modal [comp]
  (prim/transact! comp `[(prepare-add-export {})
                         (r/set-route {:router :root/modal-router :target ~co/new-export-ident})
                         (b/show-modal {:id :add-export-modal})]))

(defn show-edit-modal [comp type id]
  (prim/transact! comp `[(prepare-edit-modal {:type ~type :id ~id})
                         (r/set-route {:router :root/modal-router :target ~co/edit-export-ident})
                         (b/show-modal {:id :edit-export-modal})]))

(defn add-export [comp {:keys [name addressable format destination compression encryptionAlgorithm encryptionKey
                               initializingVector enable addressables] :as form}]
  (let [tmp-id (prim/tempid)
        addr-data (-> (filter #(= addressable (:id %)) addressables) first)]
    (prim/transact! comp `[(b/hide-modal {:id :add-export-modal})
                           (mu/add-export {:tempid ~tmp-id
                                           :name ~name
                                           :addressable ~addr-data
                                           :format ~format
                                           :destination ~destination
                                           :compression ~compression
                                           :encryptionAlgorithm ~encryptionAlgorithm
                                           :encryptionKey ~encryptionKey
                                           :initializingVector ~initializingVector
                                           :enable ~enable})])))

(defn edit-export [comp {:keys [id addressable format destination compression encryptionAlgorithm encryptionKey
                                initializingVector enable addressables] :as form}]
  (let [addr-data (-> (filter #(= addressable (:id %)) addressables) first)]
    (prim/transact! comp `[(b/hide-modal {:id :edit-export-modal})
                           (mu/edit-export {:id ~id
                                            :addressable ~addr-data
                                            :format ~format
                                            :destination ~destination
                                            :compression ~compression
                                            :encryptionAlgorithm ~encryptionAlgorithm
                                            :encryptionKey ~encryptionKey
                                            :initializingVector ~initializingVector
                                            :enable ~enable})])))

(defn do-delete-export [this id]
  (prim/transact! this `[(mu/delete-export {:id ~id})
                         (t/reset-table-page {:id :show-profiles})]))

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

(def format-options [(f/option :JSON "JSON")
                     (f/option :XML "XML")
                     (f/option :IOTCORE_JSON "JSON (Google IoT Core)")
                     (f/option :AZURE_JSON "JSON (Azure)")
                     (f/option :THINGSBOARD_JSON "JSON (ThingsBoard)")
                     (f/option :NOOP "None")])

(def destination-options [(f/option :REST_ENDPOINT "REST Endpoint")
                          ;(f/option :ZMQ_TOPIC "ZMQ Topic")])
                          (f/option :MQTT_TOPIC "MQTT Topic")
                          (f/option :IOTCORE_TOPIC "MQTT (Google IoT Core)")
                          (f/option :AZURE_TOPIC "MQTT (Azure)")
                          (f/option :XMPP_TOPIC "XMPP")
                          (f/option :AWS_TOPIC "AWS")
                          (f/option :INFLUXDB_ENDPOINT "InfluxDB")])

(def compression-options [(f/option :NONE "None")
                          (f/option :GZIP "GZIP")
                          (f/option :ZIP "ZIP")])

(def encryption-options [(f/option :NONE "None")
                         (f/option :AES "AES")])

(defsc AddExportModal [this {:keys [modal encryptionAlgorithm addressable addressables modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 6})
                                 {:addressables (prim/get-initial-state AddressableListEntry {})
                                  :modal (prim/get-initial-state b/Modal {:id :add-export-modal :backdrop true})
                                  :modal/page :new-export}))
   :ident (fn [] co/new-export-ident)
   :query [f/form-key :db/id :name :addressable :format :destination :compression :encryptionAlgorithm :encryptionKey
           :initializingVector :enable :modal/page
           {:addressables (prim/get-query AddressableListEntry)}
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Export name" :validator `f/not-empty?)
                 (f/dropdown-input :addressable [(f/option :none "No addressables available")]
                                   :default-value :none)
                 (f/dropdown-input :format format-options
                                   :default-value :JSON)
                 (f/dropdown-input :destination destination-options
                                   :default-value :REST_ENDPOINT)
                 (f/dropdown-input :compression compression-options
                                   :default-value :NONE)
                 (f/dropdown-input :encryptionAlgorithm encryption-options
                                   :default-value :NONE)
                 (f/text-input :encryptionKey :placeholder "key")
                 (f/text-input :initializingVector :placeholder "vector")
                 (f/checkbox-input :enable)]}
  (let [not-encrypted? (= encryptionAlgorithm :NONE)
        addr-data (-> (filter #(= addressable (:id %)) addressables) first)
        valid? (f/valid? (f/validate-fields props))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Add Export"))
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                          (dom/div #js {:className "content"}
               (co/field-with-label this props :name "Name" :className "form-control")
               (co/field-with-label this props :addressable "Addressable" :className "form-control")
               (address-table addr-data)
               (co/field-with-label this props :format "Export format" :className "form-control")
               (co/field-with-label this props :destination "Destination" :className "form-control")
               (co/field-with-label this props :compression "Compression method" :className "form-control")
               (co/field-with-label this props :encryptionAlgorithm "Encryption method" :className "form-control")
               (co/field-with-label this props :encryptionKey "Encryption key" :className "form-control"
                                 :disabled not-encrypted?)
               (co/field-with-label this props :initializingVector "Initializing vector" :className "form-control"
                                 :disabled not-encrypted?)
               (co/field-with-label this props :enable "Enable" :className "form-control"))))
                (b/ui-modal-footer nil
                                   (b/button {:key "add-button" :className "btn-fill" :kind :info
                                              :onClick #(add-export this props)
                                              :disabled (not valid?)}
                                             "Add")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :add-export-modal})])}
                                             "Cancel")))))

(defsc EditExportModal [this {:keys [modal encryptionAlgorithm addressable addressables modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 8})
                                 {:addressables (prim/get-initial-state AddressableListEntry {})
                                  :modal (prim/get-initial-state b/Modal {:id :edit-export-modal :backdrop true})
                                  :modal/page :edit-export}))
   :ident (fn [] co/edit-export-ident)
   :query [f/form-key :db/id :id :addressable :format :destination :compression :encryptionAlgorithm :encryptionKey
           :initializingVector :enable :modal/page
           {:addressables (prim/get-query AddressableListEntry)}
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/dropdown-input :addressable [(f/option :none "No addressables available")]
                                   :default-value :none)
                 (f/dropdown-input :format format-options
                                   :default-value :JSON)
                 (f/dropdown-input :destination destination-options
                                   :default-value :REST_ENDPOINT)
                 (f/dropdown-input :compression compression-options
                                   :default-value :NONE)
                 (f/dropdown-input :encryptionAlgorithm encryption-options
                                   :default-value :NONE)
                 (f/text-input :encryptionKey :placeholder "key")
                 (f/text-input :initializingVector :placeholder "vector")
                 (f/checkbox-input :enable)]}
  (let [not-encrypted? (= encryptionAlgorithm :NONE)
        addr-data (-> (filter #(= addressable (:id %)) addressables) first)]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Edit Export"))
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                          (dom/div #js {:className "content"}
                                                   (co/field-with-label this props :addressable "Addressable" :className "form-control")
                                                   (address-table addr-data)
                                                   (co/field-with-label this props :format "Export format" :className "form-control")
                                                   (co/field-with-label this props :destination "Destination" :className "form-control")
                                                   (co/field-with-label this props :compression "Compression method" :className "form-control")
                                                   (co/field-with-label this props :encryptionAlgorithm "Encryption method"
                                                                        :className "form-control")
                                                   (co/field-with-label this props :encryptionKey "Encryption key" :className "form-control"
                                                                        :disabled not-encrypted?)
                                                   (co/field-with-label this props :initializingVector "Initializing vector"
                                                                        :className "form-control" :disabled not-encrypted?)
                                                   (co/field-with-label this props :enable "Enable" :className "form-control"))))
                (b/ui-modal-footer nil
                                   (b/button {:key "edit-button" :className "btn-fill" :kind :info
                                              :onClick #(edit-export this props)}
                                             "Edit")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :edit-export-modal})])}
                                             "Cancel")))))

(defn conv-dest [_ dest]
  (case dest
    :MQTT_TOPIC "MQTT"
    :IOTCORE_TOPIC "IoT Core"
    :AZURE_TOPIC "Azure"
    :XMPP_TOPIC "XMPP"
    :ZMQ_TOPIC "ZMQ"
    :REST_ENDPOINT "REST"
    :AWS_TOPIC "AWS"
    :INFLUXDB_ENDPOINT "InfluxDB"
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
           {:onClick #(df/refresh! this {:fallback `d/show-error}) :icon "refresh"}]
  :modals [{:modal d/DeleteModal :params {:modal-id :de-modal} :callbacks {:onDelete do-delete-export}}]
  :actions [{:title "Edit Export" :action-class :danger :symbol "edit" :onClick show-edit-modal}
            {:title "Delete Export" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :de-modal)}])
