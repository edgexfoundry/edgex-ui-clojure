;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.endpoints
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df]
            [fulcro.client.dom :as dom]
            [fulcro.ui.forms :as f :refer [form-field* defvalidator]]
            [fulcro.client.primitives :as prim :refer [defsc]]
            [fulcro.ui.bootstrap3 :as b]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.common :as co]))

(defn field-with-label
  [comp form name label & params]
  (dom/div {:className (str "form-group" (if (f/invalid? form name) " has-error" ""))}
           (dom/label {:className "control-label" :htmlFor name} label)
           (apply f/form-field comp form name params)
           (when (f/invalid? form name)
             (dom/label {:className "error"} "Invalid endpoint"))))

(defvalidator url? [sym value args]
  (seq (re-matches #"^(((?:(25[0-5]|2[0-4]\d|[01]?\d\d?)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d\d?))|((([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\-]*[a-zA-Z0-9])\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\-]*[A-Za-z0-9])))(\:\d{1,5})$" value)))

(defsc EndpointForm [this form]
  {:initial-state (fn [params] (f/build-form EndpointForm (or params {})))
   :form-fields   [(f/id-field :ui/id)
                   (f/text-input :metadata :placeholder "Metadata URL" :validator `url?)
                   (f/text-input :data :placeholder "Data URL" :validator `url?)
                   (f/text-input :command :placeholder "Command URL" :validator `url?)
                   (f/text-input :logging :placeholder "Logging URL" :validator `url?)
                   ;(f/text-input :notifications :placeholder "Notification URL" :validator `url?)
                   (f/text-input :export :placeholder "Export URL" :validator `url?)]
   :query         (fn [] [:db/id :metadata :data :command :logging :notifications :export f/form-key])
   :ident         (fn [] co/endpoint-ident)}
  (let [props (prim/props this)]
    (dom/div #js {:className "content"}
             (field-with-label this props :metadata "Metadata" :className "form-control")
             (field-with-label this props :data "Data" :className "form-control")
             (field-with-label this props :command "Command" :className "form-control")
             (field-with-label this props :logging "Logging" :className "form-control")
             ;(field-with-label this props :notifications "Notification" :className "form-control")
             (field-with-label this props :export "Export" :className "form-control"))))

(def ui-endpoint-form (prim/factory EndpointForm {:keyfn :ui/id}))

(defsc EndpointModal [this {:keys [endpoint-form modal]}]
  {:initial-state (fn [p] {:endpoint-form
                           (prim/get-initial-state EndpointForm
                                                   {:ui/id 9
                                                    :metadata "edgex-core-metadata:48081"
                                                    :export "edgex-export-client:48071"
                                                    :data "edgex-core-data:48080"
                                                    :command "edgex-core-command:48082"
                                                    :logging "edgex-support-logging:48061"
                                                    :notifications "edgex-support-notifications:48060"})
                           :modal (prim/get-initial-state b/Modal {:id :endpoint-modal :backdrop true})})
   :ident (fn [] co/endpoint-ident)
   :query [{:endpoint-form (prim/get-query EndpointForm)}
           {:modal (prim/get-query b/Modal)}]}
  (let [cancel (fn [evt] (prim/transact! this `[(f/reset-from-entity {:form-id ~co/endpoint-ident})
                                                (b/hide-modal {:id :endpoint-modal})]))
        save (fn [evt] (prim/transact! this `[(mu/save-endpoints ~endpoint-form)
                                              (b/hide-modal {:id :endpoint-modal})]))
        not-valid? (not (f/would-be-valid? endpoint-form))]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} "Edit Endpoints"))
                (b/ui-modal-body nil
                                 (dom/div #js {:className "card"}
                                          (ui-endpoint-form endpoint-form)))
                (b/ui-modal-footer nil
                                   (b/button {:key "save-button" :className "btn-fill" :kind :info
                                              :disabled not-valid?
                                              :onClick save}
                                             "Save")
                                   (b/button {:key "cancel-button" :className "btn-fill" :kind :danger
                                              :onClick cancel} "Cancel")))))

(def ui-endpoint-modal (prim/factory EndpointModal))

