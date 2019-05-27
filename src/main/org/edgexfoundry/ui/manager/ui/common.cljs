;;; Copyright (c) 2019
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.common
  (:require [fulcro.i18n :refer [tr]]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m]
            [fulcro.ui.forms :as f]
            [fulcro.ui.form-state :as fs]
            [fulcro.ui.bootstrap3 :as b]
            [cljs-time.core :as tc]
            [cljs-time.coerce :as co]
            [cljs-time.format :as ft]
            [clojure.string :as str]))

(defonce main-page-ident [:main-page :singleton])

(defonce device-list-ident [:show-devices :singleton])

(defonce device-page-ident [:device-page :singleton])

(defonce command-list-ident [:show-commands :singleton])

(defonce new-device-ident [:new-device :singleton])

(defonce new-device-entry [:device-entry-subform :singleton])

(defonce add-profile-ident [:add-profile :singleton])

(defonce reading-list-ident [:show-readings :singleton])

(defonce reading-page-ident [:reading-page :singleton])

(defonce profile-list-ident [:show-profiles :singleton])

(defonce profile-yaml-ident [:show-profile-yaml :singleton])

(defonce addressable-list-ident [:show-addressables :singleton])

(defonce new-addressable-ident [:new-addressable :singleton])

(defonce edit-addressable-ident [:edit-addressable :singleton])

(defonce schedules-list-ident [:show-schedules :singleton])

(defonce schedule-events-list-ident [:show-schedule-events :singleton])

(defonce new-schedule-event-ident [:new-schedule-event :singleton])

(defonce new-schedule-ident [:new-schedule :singleton])

(defonce log-entry-list-ident [:show-logs :singleton])

(defonce notifications-list-ident [:show-notifications :singleton])

(defonce new-notification-ident [:new-notification :singleton])

(defonce subscriptions-list-ident [:show-subscriptions :singleton])

(defonce transmissions-list-ident [:show-transmissions :singleton])

(defonce new-subscription-ident [:new-subscription :singleton])

(defonce exports-list-ident [:show-exports :singleton])

(defonce add-export-ident [:add-export :singleton])

(defonce edit-export-ident [:edit-export :singleton])

(defonce new-export-ident [:new-export :singleton])

(defonce endpoint-ident [:endpoint :singleton])

(defn conv-time [timestamp]
  (if (= 0 timestamp 0)
    (tr "Never")
    (->> timestamp co/from-long tc/to-default-time-zone (ft/unparse {:format-str "MMMM d, y H:mm:ss"}))))

(defn conv-sub-ctg [category]
  (case category
    "SECURITY" "Security"
    "HW_HEALTH" "Hardware Health"
    "SW_HEALTH" "Software Health"
    "Unknown"))

(defn conv-ctg-seq [convec]
  (->> (mapv conv-sub-ctg convec)
       (str/join ", ")))

(defn conv-seq [convec]
  (str/join ", " convec))

(defn conv-channels [convec]
  (let [newVec (reduce (fn [new_vec value] (if-not (nil? (:url value)) (conj new_vec (:url value)) (conj new_vec (str/join ", " (:mailAddresses value))))) [] convec)]
    (str/join ", " newVec)))

(defn conv-notify-slug [_ notify]
  (js/console.log notify)
  (:slug notify))

(defn conv-tran-status [_ status]
  (case status
    :FAILED "Failed"
    :SENT "Sent"
    :ACKNOWLEDGED "Acknowledged"
    :TRXESCALATED "Trxescalated"
    "Unknown"))

(defn time-now []
  (co/to-long (tc/now)))

(defn field-with-label
  [comp form name label & params]
  (dom/div #js {:className (str "form-group" (if (f/invalid? form name) " has-error" ""))}
           (dom/label #js {:className "control-label" :htmlFor name} label)
           (apply f/form-field comp form name params)
           (when (f/invalid? form name)
             (dom/label #js {:className "error"} "Required field"))))

(defn render-field
  "A helper function for rendering just the fields."
  [component field checkValid? renderer]
  (let [form         (prim/props component)
        entity-ident (prim/get-ident component form)
        id           (str (first entity-ident) "-" (second entity-ident) "-" field)
        is-dirty?    (fs/dirty? form field)
        clean?       (not is-dirty?)
        validity     (if checkValid? (fs/get-spec-validity form field) :valid) ;
        is-invalid?  (= :invalid validity)
        value        (get form field "")]
    (renderer {:dirty?   is-dirty?
               :ident    entity-ident
               :id       id
               :clean?   clean?
               :validity validity
               :invalid? is-invalid?
               :value    value})))

(def integer-fields #{:exp/port})

(defn input-with-label
  "A non-library helper function, written by you to help lay out your form."
  ([component field field-label validation-string placeholder error-fn props input-element]
   (let [checkValid? (not (str/blank? validation-string))]
     (render-field component field checkValid?
                   (fn [{:keys [invalid? id dirty?]}]
                     (let [attr {:error           (or (when invalid? validation-string) (when error-fn (error-fn)))
                                 :id              id
                                 :placeholder     placeholder
                                 :input-generator input-element}]
                       (b/labeled-input (if (nil? props) attr (conj attr props)) field-label))))))
  ([component field field-label validation-string placeholder error-fn props]
   (let [checkValid? (not (str/blank? validation-string))
         ensure-integer #(if (js/isNaN %) "" %)]
     (render-field component field checkValid?
                   (fn [{:keys [invalid? id dirty? value invalid ident]}]
                     (let [attr {:value       value
                                 :id          id
                                 :error       (or (when invalid? validation-string) (when error-fn (error-fn)))
                                 :placeholder placeholder
                                 :onBlur      #(prim/transact! component `[(fs/mark-complete! {:entity-ident ~ident
                                                                                               :field        ~field})])
                                 :onChange    (if (integer-fields field)
                                                #(m/set-value! component field (ensure-integer (js/parseInt (.. % -target -value))))
                                                #(m/set-string! component field :event %))}]
                       (b/labeled-input (if (nil? props) attr (conj attr props)) field-label)))))))

(defn factory-apply [js-component-class]
  (fn [props & children]
    (apply js/React.createElement
           js-component-class
           (dom/convert-props props) ;; convert-props makes sure that props passed to React.createElement are plain JS object
           children)))
