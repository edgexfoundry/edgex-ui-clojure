;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.dialogs
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]))

(defsc DeleteModal [this {:keys [target-id name modal modal-id props]} callbacks]
       {:initial-state (fn [p] {:target-id :none
                                :name ""
                                :modal-id (or (:modal-id p) :none)
                                :modal (prim/get-initial-state b/Modal {:id (:modal-id p) :backdrop true})})
        :ident [:delete-modal :modal-id]
        :query [:modal-id :target-id :name :props {:modal (prim/get-query b/Modal)}]}
  (let [hide-modal (fn [modal-id] (prim/transact! this `[(b/hide-modal {:id ~modal-id})]))]
       (b/ui-modal modal
                   (b/ui-modal-title nil
                                     (dom/div {:key "title"
                                               :style {:fontSize "22px"}} "Confirm Delete"))
                   (b/ui-modal-body nil
                                    (dom/p {:key "message" :className b/text-danger} (str "Delete " name "?")))
                   (b/ui-modal-footer nil
                                      (b/button {:key "ok-button" :className "btn-fill" :kind :info
                                                 :onClick #(do
                                                             ((get callbacks modal-id) this target-id props)
                                                             (hide-modal modal-id))}
                                                "Delete")
                                      (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                                 :onClick #(hide-modal modal-id)}
                                                "Cancel")))))
(defn set-delete-target*
  [state modal-id type id props]
  (let [target (get-in state [type id])
        set-modal-state (fn [state attr val] (assoc-in state [:delete-modal modal-id attr] val))]
    (-> state
        (set-modal-state :target-id id)
        (set-modal-state :name (or (:name target) (:slug target)))
        (set-modal-state :props props))))

(defmutation prepare-delete-modal
             [{:keys [modal-id type id props]}]
             (action [{:keys [state]}]
                     (swap! state (fn [s] (-> s
                                              (set-delete-target* modal-id type id props))))))

(defn mk-show-modal
  ([modal-id]
   (mk-show-modal modal-id nil))
  ([modal-id props]
   (fn [comp type id]
     (prim/transact! comp `[(prepare-delete-modal {:modal-id ~modal-id :type ~type :id ~id :props ~props})
                            (r/set-route {:router :root/modal-router :target [:delete-modal ~modal-id]})
                            (b/show-modal {:id ~modal-id})]))))
(defn- show-modal* [modal tf]
  (assoc modal :modal/visible tf))

(defn- activate-modal* [modal tf]
  (assoc modal :modal/active tf))

(defmutation show-error [params]
  (action [{:keys [state]}]
          (swap! state update-in (b/modal-ident :error-modal) show-modal* true)
          (js/setTimeout (fn [] (swap! state update-in (b/modal-ident :error-modal) activate-modal* true)) 10)))
