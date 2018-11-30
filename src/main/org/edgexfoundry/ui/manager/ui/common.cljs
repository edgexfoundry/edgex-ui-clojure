;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.common
  (:require [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.ui.forms :as f]
            [cljs-time.core :as tc]
            [cljs-time.coerce :as co]
            [cljs-time.format :as ft]
            [clojure.string :as str]))

(defonce main-page-ident [:main-page :singleton])

(defonce device-list-ident [:show-devices :singleton])

(defonce device-page-ident [:device-page :singleton])

(defonce command-list-ident [:show-commands :singleton])

(defonce new-device-ident [:new-device :singleton])

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

(defonce exports-list-ident [:show-exports :singleton])

(defonce add-export-ident [:add-export :singleton])

(defonce edit-export-ident [:edit-export :singleton])

(defonce new-export-ident [:new-export :singleton])

(defonce endpoint-ident [:endpoint :singleton])

(defn conv-time [timestamp]
  (if (= 0 timestamp 0)
    (tr "Never")
    (->> timestamp co/from-long tc/to-default-time-zone (ft/unparse {:format-str "MMMM d, y H:mm:ss"}))))

(defn time-now []
  (co/to-long (tc/now)))

(defn field-with-label
  [comp form name label & params]
  (dom/div #js {:className (str "form-group" (if (f/invalid? form name) " has-error" ""))}
           (dom/label #js {:className "control-label" :htmlFor name} label)
           (apply f/form-field comp form name params)
           (when (f/invalid? form name)
             (dom/label #js {:className "error"} "Required field"))))
