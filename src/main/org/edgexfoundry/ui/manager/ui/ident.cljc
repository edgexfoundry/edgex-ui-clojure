;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.ident)

(defn edgex-ident [obj]
  [(:type obj) (:id obj)])
