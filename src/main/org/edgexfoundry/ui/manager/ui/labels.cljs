;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.labels
  (:require [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.ui.forms :as f :refer [form-field*]]
            [clojure.string :as str]))

(defn labels-input
  "Declare a labels input on a form. See text-input for additional options.

  When rendering a text input, the params passed to the field render will be merged
  with the textarea HTML props."
  [name & options]
  (-> (apply f/textarea-input name options)
      (assoc :input/type ::labels)))

(defmethod form-field* ::labels [component form field-name & {:keys [id className] :as htmlProps}]
  (let [form-id (f/form-ident form)
        cls     (or className (f/css-class form field-name) "")
        value   (f/current-value form field-name)
        attrs   (clj->js (merge htmlProps
                                {:name        field-name
                                 :id          id
                                 :className   cls
                                 :value       (str/join "\n" value)
                                 :placeholder (f/placeholder form field-name)
                                 :onBlur      (fn [_]
                                                (prim/transact! component
                                                                `[(f/validate-field ~{:form-id form-id :field field-name})
                                                                  ~@(f/get-on-form-change-mutation form field-name :blur)
                                                                  ~f/form-root-key]))
                                 :onChange    (fn [event]
                                                (let [value      (.. event -target -value)
                                                      field-info {:form-id form-id
                                                                  :field   field-name
                                                                  :value   (->> value str/split-lines
                                                                                (filterv #(-> % str/blank? not)))}]
                                                  (prim/transact! component
                                                                  `[(f/set-field ~field-info)
                                                                    ~@(f/get-on-form-change-mutation form field-name :edit)
                                                                    ~f/form-root-key])))}))]
    (dom/textarea attrs)))
