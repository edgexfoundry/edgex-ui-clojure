;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.client-main
  (:require [org.edgexfoundry.ui.manager.client :as client]
            [fulcro.client :as core]
            [org.edgexfoundry.ui.manager.ui.root :as root]))

; This is the production entry point. In dev mode, we do not require this file at all, and instead mount (and
; hot code reload refresh) from cljs/user.cljs
(reset! client/app (core/mount @client/app root/Root "app"))
