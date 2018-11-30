;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.profiles
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.ui.forms :as f]
            [fulcro.ui.file-upload :refer [FileUploadInput file-upload-input file-upload-networking cropped-name]]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.routing :as routing]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [goog.object :as gobj]
            ["highlight.js" :as hljs]))

(declare ProfileListEntry)

(declare ProfileYAMLFile)

(defmutation prepare-add-profile [nonparams]
  (action [{:keys [state]}]
          (swap! state (fn [s]
                         (-> s
                             (assoc-in [:fulcro.ui.file-upload/by-id :pr-name :file-upload/files] []))))))

(defn show-add-profile-modal [comp]
  (prim/transact! comp `[(prepare-add-profile)
                         (r/set-route {:router :root/modal-router :target [:add-profile-modal :singleton]})
                         (b/show-modal {:id :add-profile-modal})
                         :add-profile]))

(defn upload-profile [comp upload-file-id]
  (prim/transact! comp `[(b/hide-modal {:id :add-profile-modal})
                         (mu/upload-profile {:file-id ~upload-file-id})])
  (df/load comp :q/edgex-profiles ProfileListEntry {:target (df/multiple-targets
                                                              (conj co/profile-list-ident :content)
                                                              (conj co/new-device-ident :profiles))}))

(defmutation load-profile-yaml
  [{:keys [id]}]
  (action [{:keys [state] :as env}]
          (df/load-action env :q/edgex-profile-yaml ProfileYAMLFile
                          {:target (conj co/profile-yaml-ident :profile-yaml)
                           :params {:id id}})
          (swap! state (fn [s] (-> s
                                   (assoc-in (conj co/profile-yaml-ident :ui/name) (-> s :device-profile id :name))))))
  (remote [env] (df/remote-load env)))

(defn table-entry
  [comp form name label & params]
  (b/row {}
         (b/col {:xs 4 :htmlFor name} label)
         (b/col {:xs 8} (apply f/form-field comp form name params))))

(defui ^:once AddProfileForm
  static prim/InitialAppState
  (initial-state [this params] (f/build-form this {:db/id 2 :profile-file (prim/get-initial-state FileUploadInput {:id :pr-name})}))
  static f/IForm
  (form-spec [this] [(f/id-field :db/id)
                     (file-upload-input :profile-file)])
  static prim/IQuery
  (query [this] [f/form-root-key f/form-key :db/id
                 {:profile-file (prim/get-query FileUploadInput)}])
  static prim/Ident
  (ident [this props] co/add-profile-ident)
  Object
  (render [this]
          (let [{:keys [db/id] :as props} (prim/props this)]
            (dom/div {:className "content"}
                     (table-entry this props :profile-file "Select Profile YAML File" :accept "application/x-yaml"
                                       :multiple? false
                                       :renderFile (fn [file-component]
                                                     (let [onCancel (prim/get-computed file-component :onCancel)
                                                           {:keys [file/id file/name file/size file/progress
                                                                   file/status] :as props} (prim/props file-component)
                                                           label    (cropped-name name 20)]
                                                       (dom/li {:style #js {:listStyleType "none"} :key (str "file-" id)}
                                                               (str label " (" size " bytes) ")
                                                               (b/glyphicon {:size "14pt" :onClick #(onCancel id)} :remove-circle)
                                                               (dom/br nil)
                                                               (case status
                                                                 :failed (dom/span nil "FAILED!")
                                                                 :done ""
                                                                 (b/progress-bar {:current progress})))))
                                       :renderControl (fn [onChange accept multiple?]
                                                        (let [control-id (str "add-control-" id)
                                                              attrs      (cond-> {:onChange (fn [evt] (onChange evt))
                                                                                  :id       control-id
                                                                                  :style    #js {:display "none"}
                                                                                  :value    ""
                                                                                  :type     "file"}
                                                                                 accept (assoc :accept accept)
                                                                                 multiple? (assoc :multiple "multiple")
                                                                                 :always clj->js)]
                                                          (dom/label {:htmlFor control-id} (b/glyphicon {:className "btn btn-primary"} :plus)
                                                                     (dom/input attrs)))))))))

(def ui-add-profile-form (prim/factory AddProfileForm {:keyfn :db/id}))

(defsc AddProfileModal [this {:keys [profile-form modal modal/page] :as props}]
  {:initial-state (fn [p] {:profile-form (prim/get-initial-state AddProfileForm {:db/id 2})
                           :modal (prim/get-initial-state b/Modal {:id :add-profile-modal :backdrop true})
                           :modal/page :add-profile-modal})
   :ident (fn [] [:add-profile-modal :singleton])
   :query [{:profile-form (prim/get-query AddProfileForm)}
           {:modal (prim/get-query b/Modal)}
           {[:fulcro.ui.file-upload/by-id :pr-name] [:file-upload/files]}
           :modal/page]}
  (let [files (get-in props [[:fulcro.ui.file-upload/by-id :pr-name] :file-upload/files])
        upload-file-id (if (empty? files)
                         nil
                         (-> files first second))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Upload Device Profile"))
                (b/ui-modal-body nil
                                 (ui-add-profile-form profile-form))
                (b/ui-modal-footer nil
                                   (b/button {:key "upload-button" :className "btn-fill" :kind :info
                                              :onClick #(upload-profile this upload-file-id)}
                                             "Upload")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :add-profile-modal})])}
                                             "Cancel")))))

(defsc ProfileYAMLFile [this {:keys [yaml]}]
  {:ident (fn [] [:yaml-file :singleton])
   :query [:yaml]})

(defsc ProfileYAML
  [this {:keys [ui/name ui/show-profile-yaml profile-yaml] :as props}]
  {:initial-state (fn [p] {:ui/name "" :ui/show-profile-yaml true})
   :ident (fn [] co/profile-yaml-ident)
   :query [:ui/name :ui/show-profile-yaml
           {:profile-yaml (prim/get-query ProfileYAMLFile)}]
   :componentDidMount (fn [] (let  [code (.call (goog.object/get js/document "getElementById") js/document "yaml")]
                               (hljs/highlightBlock code)))}
  (dom/div nil
           (dom/h5 nil (str "Profile " name))
           (b/button
             {:onClick #(routing/nav-to! this :profile)}
             (dom/i #js {:className "glyphicon fa fa-caret-square-o-left"}))
           (dom/pre nil
                    (dom/code {:id "yaml"} (-> profile-yaml first :yaml)))))

(defn show-profile [this type id]
  (prim/transact! this `[(load-profile-yaml {:id ~id})])
  (routing/nav-to! this :profile-yaml))

(defn do-delete-profile [this id]
  (prim/transact! this `[(mu/delete-profile {:id ~id})
                         (t/reset-table-page {:id :show-profiles})]))

(deftable ProfileList :show-profiles :device-profile [[:name "Name"] [:description "Description"]
                                                      [:modified "Last Modified" #(co/conv-time %2)]]
          [{:onClick #(show-add-profile-modal this) :icon "plus"}
           {:onClick #(df/refresh! this {:fallback `d/show-error}) :icon "refresh"}]
  :name-row-symbol ProfileListEntry
  :modals [{:modal d/DeleteModal :params {:modal-id :dp-modal} :callbacks {:onDelete do-delete-profile}}]
  :actions [{:title "View Device" :action-class :info :symbol "info" :onClick show-profile}
            {:title "Delete Profile" :action-class :danger :symbol "times" :onClick (d/mk-show-modal :dp-modal)}])
