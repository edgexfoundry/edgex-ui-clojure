;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.subscriptions
  (:require [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.load :as ld]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.localized-dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.form-state :as fs]
            ["react-tag-box" :refer [TagBox]]
            ["react-widgets" :refer [Multiselect]]))

(declare SubscriptionModal)

(defn reset-subscription* [state]
  (update-in state co/new-subscription-ident #(merge % {:id nil})))

(defn prepare-subscription* [state new-mode?]
  (let [ref co/new-subscription-ident]
    (-> state
        (fs/add-form-config* SubscriptionModal ref)
        (reset-subscription*))))

(defmutation prepare-subscription
  [{:keys [new-mode?]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (prepare-subscription* s new-mode?)))))

(defn- initialize-form [state-map form-ident]
  (let [get-labels #(->> %
                         :subscribedLabels
                         (mapv (fn [l] {:label l :value l}))
                         (hash-map :selectedSLs)
                         (merge %))
        get-urls #(->> %
                       :channels
                       (filterv (fn [v] (= (:type v) "REST")))
                       (mapv (fn [l] {:label (:url l) :value (:url l)}))
                       (hash-map :selectedUrls)
                       (merge %))
        get-emails #(->> %
                         :channels
                         (filterv (fn [v] (= (:type v) "EMAIL")))
                         (first)
                         (:mailAddresses)
                         (mapv (fn [l] {:label l :value l}))
                         (hash-map :selectedEmails)
                         (merge %))
        sub (-> state-map
                (get-in form-ident)
                (set/rename-keys {:slug :sub/slug})
                get-labels
                get-urls
                get-emails)]
    (-> state-map
        (update-in co/new-subscription-ident #(merge % sub)))))



(defmutation prepare-edit-modal
  [{:keys [type id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s]
                         (-> s
                             (initialize-form [type id]))))))

(defn show-add-modal [comp]
  (prim/transact! comp `[(prepare-subscription {:new-mode? true})
                         (r/set-route {:router :root/modal-router :target ~co/new-subscription-ident})
                         (b/show-modal {:id :subscription-modal})
                         :modal-router]))

(defn show-edit-modal [comp type id]
  (prim/transact! comp `[(prepare-subscription {:new-mode? false})
                         (prepare-edit-modal {:type ~type :id ~id})
                         (r/set-route {:router :root/modal-router :target ~co/new-subscription-ident})
                         (b/show-modal {:id :subscription-modal})]))

(defn add-subscription [comp {:keys [id sub/slug description receiver subscribedCategories subscribedLabels channels] :as form}]
  (let [tmp-id (prim/tempid)
        subObj (merge (if (nil? id) {:tempid tmp-id} {:id id})
                      {:slug                 slug
                       :description          description
                       :receiver             receiver
                       :subscribedLabels     subscribedLabels
                       :subscribedCategories subscribedCategories
                       :channels channels})]
    (if (nil? id)
      (prim/transact! comp `[(b/hide-modal {:id :subscription-modal})
                             (mu/add-subscription ~subObj)
                             (fs/reset-form!)
                             (fs/clear-complete!)
                             (df/fallback {:action ld/reset-error})])
      (prim/transact! comp `[(b/hide-modal {:id :subscription-modal})
                             (mu/edit-subscription ~subObj)
                             (df/fallback {:action ld/reset-error})]))))

(defn do-delete-subscription [this id row]
  (let [slug (->> row (:content) (filterv #(= (:id %) id)) (first) (:slug))]
    (prim/transact! this `[(mu/delete-subscription {:id ~id :slug ~slug})
                           (t/reset-table-page {:id :show-subscriptions})
                           (df/fallback {:action ld/reset-error})])))

(declare conv-category)
(declare conv-sev)

(def ui-tag-box (co/factory-apply TagBox))

(def ui-multiselect (co/factory-apply Multiselect))

(s/def :sub/slug #(re-matches #"\S+" %))

(defsc SubscriptionModal [this {:keys [id modal subscribedCategories subscribedLabels selectedSLs selectedUrls selectedEmails channels] :as props}]
  {:initial-state (fn [p] {:modal         (prim/get-initial-state b/Modal {:id :subscription-modal :backdrop true})
                           :modal/page    :new-subscription
                           :sub/slug "" :description "" :receiver ""
                           :subscribedCategories []
                           :subscribedLabels []
                           :selectedSLs []
                           :channels []
                           :selectedUrls []
                           :selectedEmails []})
   :ident         (fn [] co/new-subscription-ident)
   :query         [:sub/slug :description :receiver :subscribedCategories :subscribedLabels :channels
                   fs/form-config-join
                   :id :modal/page {:modal (prim/get-query b/Modal)} :selectedSLs :selectedUrls :selectedEmails]
   :form-fields   #{:sub/slug :description :receiver :subscribedCategories :subscribedLabels :channels :selectedSLs :selectedUrls :selectedEmails}}
  (let [tags []
        ctg_opts [{ :id "SECURITY",   :name "Security" },
                  { :id "HW_HEALTH" , :name "Hardware Health" },
                  { :id "SW_HEALTH",  :name "Software Health" }]
        modal-title (str (if (nil? id) "Add" "Edit") " Subscription")
        disable-button (fs/invalid-spec? props :sub/slug)
        button-action (fn [_]
                        (prim/transact! this `[(fs/mark-complete!  {:entity-ident ~co/new-subscription-ident})])
                        (when-not (or (not (fs/checked? props)) (fs/invalid-spec? props :sub/slug))
                          (add-subscription this props)))]
    (b/ui-modal modal
      (b/ui-modal-title nil
        (dom/div #js {:key "title" :style #js {:fontSize "22px"}} modal-title))
          (b/ui-modal-body {:className "exportModal"}
            (dom/div :$card
              (dom/div :$content
                       (dom/div :$form-group
                                (co/input-with-label this :sub/slug "Slug:" "Slug is required" "Unique key of the subscription" nil {:required true})
                                (co/input-with-label this :description "Description:" "" "Description of the subscription" nil nil)
                                (co/input-with-label this :receiver "Receiver:" "" "Receiver of the subscription" nil nil)
                                (co/input-with-label this :subscribedCategories "Subscribed Categories" "" "" nil nil
                                                     (fn [attrs]
                                                       (ui-multiselect {:data     (clj->js ctg_opts) :placeholder "Type to filter subscribed categories..." :filter "contains"
                                                                        :value    (clj->js subscribedCategories) :textField :name :valueField :id
                                                                        :onChange (fn [v]
                                                                                    (let [newVec (mapv #(. % -id) v)]
                                                                                      (m/set-value! this :selectedSCs v)
                                                                                      (m/set-value! this :subscribedCategories newVec)))})))
                                (co/input-with-label this :subscribedLabels "Subscribed Labels:" "" "" nil nil
                                                     (fn [attrs]
                                                       (ui-tag-box #js {:tags (clj->js tags) :selected (clj->js selectedSLs) :selectedText "Label exists" :placeholder "Type the Label and press \"Enter\" to add multiple values..."
                                                                        :onSelect (fn [tag]
                                                                                    (m/set-value! this :selectedSLs (conj selectedSLs {:label (. tag -label) :value (. tag -label)} ))
                                                                                    (m/set-value! this :subscribedLabels (conj subscribedLabels (. tag -label))))
                                                                        :removeTag (fn [tag]
                                                                                     (m/set-value! this :selectedSLs (filterv #(not= (:value %) (. tag -value)) selectedSLs))
                                                                                     (m/set-value! this :subscribedLabels (filterv #(not= % (. tag -value)) subscribedLabels)))})))
                                (co/input-with-label this :channels "Subscribed Channels:" "" "" nil nil
                                                     (fn [attrs]
                                                       (ui-tag-box #js {:tags (clj->js tags) :selected (clj->js selectedUrls) :selectedText "URL exists" :placeholder "Type the URL and press \"Enter\" to add multiple values..."
                                                                        :onSelect (fn [tag]
                                                                                    (m/set-value! this :selectedUrls (conj selectedUrls {:label (. tag -label) :value (. tag -label)} ))
                                                                                    (m/set-value! this :channels (conj channels {:type "REST" :url (. tag -label)})))
                                                                        :removeTag (fn [tag]
                                                                                     (m/set-value! this :selectedUrls (filterv #(not= (:value %) (. tag -value)) selectedUrls))
                                                                                     (m/set-value! this :channels (filterv #(not= (:url %) (. tag -value)) channels)))})))
                                (ui-tag-box #js {:tags      (clj->js tags) :selected (clj->js selectedEmails) :selectedText "Emails exists" :placeholder "Type the Email and press \"Enter\" to add multiple values..."
                                                 :onSelect  (fn [tag]
                                                              (let [email (. tag -label)
                                                                    mailCH (filterv (fn [v] (= (:type v) "EMAIL")) channels)
                                                                    restCH (filterv (fn [v] (= (:type v) "REST")) channels)
                                                                    newMailCH (if (empty? mailCH)
                                                                                [{:type "EMAIL" :mailAddresses [email]}]
                                                                                (mapv (fn [x] (update-in x [:mailAddresses] #(conj % email))) mailCH))]
                                                                (m/set-value! this :selectedEmails (conj selectedEmails {:label email :value email}))
                                                                (m/set-value! this :channels (into [] (concat restCH newMailCH)))))
                                                 :removeTag (fn [tag]
                                                              (let [email (. tag -label)
                                                                    oldMails (:mailAddresses (first (filter #(= (:type %) "EMAIL") channels)))
                                                                    newMails (filterv #(not= % email) oldMails)
                                                                    restCH (filterv #(= (:type %) "REST") channels)
                                                                    newMailCH (if (empty? newMails)
                                                                                []
                                                                                [{:type "EMAIL" :mailAddresses newMails}])]
                                                                (m/set-value! this :selectedEmails (vec (filter #(not= (:value %) email) selectedEmails)))
                                                                (m/set-value! this :channels (concat restCH newMailCH))))})))))
          (b/ui-modal-footer nil
            (b/button {:key "ok-next-button" :className "btn-fill" :kind :info :disabled disable-button :onClick button-action} "OK")
            (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                       :onClick #(prim/transact! this `[(b/hide-modal {:id :subscription-modal})
                                                        (fs/reset-form!)
                                                        (fs/clear-complete!)])} "Cancel")))))

(deftable SubscriptionList :show-subscriptions :subscription [[:slug "Slug"]
                                                              [:created "Created" #(co/conv-time %2)]
                                                              [:receiver "Receiver"]
                                                              [:subscribedCategories "Subscribed Categories" #(co/conv-ctg-seq %2)]
                                                              [:subscribedLabels "Subscribed Labels" #(co/conv-seq %2)]
                                                              [:channels "Channels" #(co/conv-channels %2)]]
  [{:onClick #(show-add-modal this) :icon "plus"}
   {:onClick #(ld/refresh! this) :icon "refresh"}]
    :search-keyword :receiver
    :modals [{:modal d/DeleteModal :params {:modal-id :dsb-modal} :callbacks {:onDelete do-delete-subscription}}]
    :actions [{:title "Edit Subscription" :action-class :danger :symbol "edit" :onClick show-edit-modal}
              {:title "Delete Subscription" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :dsb-modal props)}])

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
