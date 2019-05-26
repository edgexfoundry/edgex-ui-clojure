;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.load
  (:require [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.client.data-fetch :as df]
            [org.edgexfoundry.ui.manager.ui.common :as co]))

(defn reset-error* [state]
  (assoc-in state (conj co/main-page-ident :ui/show-error?) true))

(defmutation reset-error
  [noargs]
  (action [{:keys [state]}]
          (swap! state reset-error*)))

(defn load
  ([app-or-comp-or-reconciler server-property-or-ident class-or-factory]
   (load app-or-comp-or-reconciler server-property-or-ident class-or-factory nil))
  ([app-or-comp-or-reconciler server-property-or-ident class-or-factory config]
   (let [new-config (merge config {:post-mutation `reset-error :fallback `reset-error})]
     (df/load app-or-comp-or-reconciler server-property-or-ident class-or-factory new-config))))

(defn load-action [env server-property-or-ident SubqueryClass config]
  (df/load-action env server-property-or-ident SubqueryClass (assoc config :fallback `reset-error)))

(defn refresh!
  ([component load-options]
   (df/refresh! component (assoc load-options :fallback `reset-error)))
  ([component]
   (refresh! component nil)))
