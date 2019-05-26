;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.addressables
  (:require [clojure.string :as st]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.forms :as f]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.ident :as id]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]))

(declare AddressableListEntry)

(defn reset-add-addressable* [state]
  (update-in state co/new-addressable-ident #(merge % {:name ""
                                                       :protocol "HTTP"
                                                       :address ""
                                                       :port 0
                                                       :path ""
                                                       :method :get
                                                       :publisher ""
                                                       :topic ""
                                                       :password ""})))

(defmutation reset-add-addressable
  [noparams]
  (action [{:keys [state]}]
    (swap! state (fn [state-map]
                   (-> state-map
                       (reset-add-addressable*))))))

(defn show-add-addressable-modal [comp]
  (prim/transact! comp `[(reset-add-addressable {})
                         (r/set-route {:router :root/modal-router :target ~co/new-addressable-ident})
                         (b/show-modal {:id :add-addressable-modal})]))

(defn add-addressable [comp {:keys [name protocol address port path method publisher topic user password]}]
  (let [tmp-id (prim/tempid)]
    (prim/transact! comp `[(b/hide-modal {:id :add-addressable-modal})
                           (mu/add-addressable {:tempid ~tmp-id
                                                :name ~name
                                                :protocol ~protocol
                                                :address ~address
                                                :port ~port
                                                :path ~path
                                                :method ~method
                                                :publisher ~publisher
                                                :topic ~topic
                                                :user ~user
                                                :password ~password})])))

(defn edit-addressable [comp {:keys [id protocol address port path method publisher topic user password]}]
  (prim/transact! comp `[(b/hide-modal {:id :edit-addressable-modal})
                         (mu/edit-addressable {:id ~id
                                               :protocol ~protocol
                                               :address ~address
                                               :port ~port
                                               :path ~path
                                               :method ~method
                                               :publisher ~publisher
                                               :topic ~topic
                                               :user ~user
                                               :password ~password})]))

(defn- initialize-form [state-map form-class form-ident]
  (let [map-method #(case %
                      "POST" :post
                      "PUT" :put
                      "DELETE" :delete
                      :get)
        adr (-> state-map
                (get-in form-ident)
                (update :method map-method))]
    (-> state-map
        (update-in co/edit-addressable-ident #(merge % adr)))))

(declare EditAddressableModal)

(defmutation prepare-edit-modal
  [{:keys [type id]}]
  (action [{:keys [state]}]
    (swap! state (fn [state-map]
                   (-> state-map
                       (initialize-form EditAddressableModal [type id])))))
  (refresh [env] [:edit-addressable]))

(defn show-edit-modal [comp type id]
  (prim/transact! comp `[(prepare-edit-modal {:type ~type :id ~id})
                         (r/set-route {:router :root/modal-router :target ~co/edit-addressable-ident})
                         (b/show-modal {:id :edit-addressable-modal})]))

(defn do-delete-addressable [this id props]
  (prim/transact! this `[(mu/delete-addressable {:id ~id}) :show-addressables]))

(defsc AddAddressableModal [this {:keys [modal modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 3})
                                 {:modal (prim/get-initial-state b/Modal {:id :add-addressable-modal :backdrop true})
                                  :modal/page :new-addressable}))
   :ident (fn [] co/new-addressable-ident)
   :query [f/form-key :modal/page :db/id :name :protocol :address :port :path :method :publisher :topic :user :password
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :name :placeholder "Addressable name" :validator `f/not-empty?)
                 (f/text-input :protocol :validator `f/not-empty?)
                 (f/text-input :address :validator `f/not-empty?)
                 (f/integer-input :port)
                 (f/text-input :path)
                 (f/dropdown-input :method [(f/option :get "GET")
                                            (f/option :post "POST")
                                            (f/option :put "PUT")
                                            (f/option :delete "DELETE")]
                                   :default-value :get)
                 (f/text-input :publisher)
                 (f/text-input :topic)
                 (f/text-input :user)
                 (f/text-input :password)]}
  (b/ui-modal modal
              (b/ui-modal-title nil
                                (dom/div #js {:key "title"
                                              :style #js {:fontSize "22px"}} "Add Addressable"))
              (b/ui-modal-body nil
                               (dom/div #js {:className "card"}
                                        (dom/div #js {:className "content"}
                                                 (co/field-with-label this props :name "Name" :className "form-control")
                                                 (co/field-with-label this props :protocol "Protocol" :className "form-control")
                                                 (co/field-with-label this props :address "Address" :className "form-control")
                                                 (co/field-with-label this props :port "Port" :className "form-control")
                                                 (co/field-with-label this props :path "Path" :className "form-control")
                                                 (co/field-with-label this props :method "Method" :className "form-control")
                                                 (co/field-with-label this props :publisher "Publisher" :className "form-control")
                                                 (co/field-with-label this props :topic "Topic" :className "form-control")
                                                 (co/field-with-label this props :user "User" :className "form-control")
                                                 (co/field-with-label this props :password "Password" :className "form-control"))))
              (b/ui-modal-footer nil
                                 (b/button {:key "add-button" :className "btn-fill" :kind :info
                                            :onClick #(add-addressable this props)
                                            :disabled (-> props f/validate-fields f/valid? not)}
                                           "Add")
                                 (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                            :onClick #(prim/transact! this `[(b/hide-modal {:id :add-addressable-modal})])}
                                           "Cancel"))))

(defsc EditAddressableModal [this {:keys [modal modal/page] :as props}]
  {:initial-state (fn [p] (merge (f/build-form this {:db/id 7})
                                 {:modal (prim/get-initial-state b/Modal {:id :edit-addressable-modal :backdrop true})
                                  :modal/page :edit-addressable}))
   :ident (fn [] co/edit-addressable-ident)
   :query [f/form-key :modal/page :db/id :id :name :protocol :address :port :path :method :publisher :topic :user :password
           {:modal (prim/get-query b/Modal)}]
   :form-fields [(f/id-field :db/id)
                 (f/text-input :protocol :validator `f/not-empty?)
                 (f/text-input :address :validator `f/not-empty?)
                 (f/integer-input :port)
                 (f/text-input :path)
                 (f/dropdown-input :method [(f/option :get "GET")
                                            (f/option :post "POST")
                                            (f/option :put "PUT")
                                            (f/option :delete "DELETE")]
                                   :default-value :get)
                 (f/text-input :publisher)
                 (f/text-input :topic)
                 (f/text-input :user)
                 (f/text-input :password)]}
  (b/ui-modal modal
              (b/ui-modal-title nil
                                (dom/div #js {:key "title"
                                              :style #js {:fontSize "22px"}} "Edit Addressable"))
              (b/ui-modal-body nil
                               (dom/div #js {:className "card"}
                                        (dom/div #js {:className "content"}
                                                 (co/field-with-label this props :protocol "Protocol" :className "form-control")
                                                 (co/field-with-label this props :address "Address" :className "form-control")
                                                 (co/field-with-label this props :port "Port" :className "form-control")
                                                 (co/field-with-label this props :path "Path" :className "form-control")
                                                 (co/field-with-label this props :method "Method" :className "form-control")
                                                 (co/field-with-label this props :publisher "Publisher" :className "form-control")
                                                 (co/field-with-label this props :topic "Topic" :className "form-control")
                                                 (co/field-with-label this props :user "User" :className "form-control")
                                                 (co/field-with-label this props :password "Password" :className "form-control"))))
              (b/ui-modal-footer nil
                                 (b/button {:key "add-button" :className "btn-fill" :kind :info
                                            :onClick #(edit-addressable this props)}
                                           "Save")
                                 (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                            :onClick #(prim/transact! this `[(b/hide-modal {:id :edit-addressable-modal})])}
                                           "Cancel"))))

(def ui-edit-addressable-modal (prim/factory EditAddressableModal {:keyfn :id}))

(deftable AddressableList :show-addressables :addressable [[:name "Name"] [:url "URL"]
                                                           [:modified "Last Modified" #(co/conv-time %2)]]
  [{:onClick #(show-add-addressable-modal this) :icon "plus"}
   {:onClick #(df/refresh! this {:fallback `d/show-error}) :icon "refresh"}]
  :modals [{:modal d/DeleteModal :params {:modal-id :da-modal} :callbacks {:onDelete do-delete-addressable}}]
  :actions [{:title "Edit Addressable" :action-class :danger :symbol "edit" :onClick show-edit-modal}
            {:title "Delete Addressable" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :da-modal)}])
