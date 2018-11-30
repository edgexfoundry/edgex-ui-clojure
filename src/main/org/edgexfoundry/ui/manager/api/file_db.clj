;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.api.file-db
  (:require
    [taoensso.timbre :as timbre]))

(def file-database (atom {}))

(def last-file-id (atom 0))

(defn next-file-id [] (swap! last-file-id inc))

(defn save-file
  [{:keys [tempfile filename content-type size] :as filedesc}]
  (let [id (next-file-id)]
    (timbre/info "File to save: " filename " - stored in java.io.File " tempfile " with size " size " and content-type" content-type)
    ; If you're using form submission, you can move the tempfile then. If this is the end of the line, then you should
    ; move the image from the tempfile to a more permanent store.
    (swap! file-database assoc id filedesc)
    id))

(defn get-file
  ^java.io.File [id]
  ; Give back the file. This will be used by form submission processing.
  (get-in @file-database [id :tempfile]))

(defn delete-file [id] (swap! file-database dissoc id))
