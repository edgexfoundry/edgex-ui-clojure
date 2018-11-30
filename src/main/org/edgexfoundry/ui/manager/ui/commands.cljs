;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.commands
  (:require [clojure.string :as st]
            [goog.string :as gstring]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.client.routing :as r]
            [fulcro.ui.bootstrap3 :as b]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [org.edgexfoundry.ui.manager.ui.table :as t]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.routing :as routing]
            [org.edgexfoundry.ui.manager.ui.ident :as id]
            [org.edgexfoundry.ui.manager.api.mutations :as mu]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]))

(defn to-float [s]
  (js/parseFloat s))

(defn is-nan? [n]
  (js/isNaN n))

(defn do-format [fmt & args]
  (apply gstring/format fmt args))

(defn set-set-modal-target*
  [state id]
  (let [set-modal-state (fn [state attr val] (assoc-in state [:set-command-modal :singleton attr] val))]
    (-> state
        (set-modal-state :target [:command id]))))

(defn is-valid [{:keys [value-type min max ui/value ui/selected]}]
  (let [number? (or (= value-type "I") (= value-type "F"))
        have-max-min? (and number? (not (is-nan? min)) (not (is-nan? max)))
        out-of-range? (and have-max-min? (or (< (to-float value) (to-float min)) (> (to-float value) (to-float max))))
        invalid-number? (and number? (is-nan? value))]
    [selected (or (not selected) (and (not out-of-range?) (not invalid-number?)))]))

(defn validate-set-command* [state]
  (let [target (get-in state [:set-command-modal :singleton :target])
        cmd (get-in state target)
        paramNames (into #{} (-> cmd :put :parameterNames))
        param-ids (map :id (filter #(paramNames (:name %)) (-> state :value-descriptor vals)))
        descs (map #(get-in state [:value-descriptor %]) param-ids)
        validity (map is-valid descs)
        valid (and (some first validity) (every? second validity))
        param-val (fn [desc] (let [v (:ui/value desc)
                                   val (if (nil? v)
                                         (:defaultValue desc)
                                         v)]
                               [(:name desc) (:value-type desc) val]))
        values (mapv param-val (filter #(:ui/selected %) descs))]
    (-> state
        (assoc-in [:set-command-modal :singleton :valid] valid)
        (assoc-in [:set-command-modal :singleton :values] values))))

(defmutation prepare-set-command
  [{:keys [id]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (set-set-modal-target* id)
                                   (validate-set-command*)))))
  (refresh [env] [:value-descriptor :set-command-modal]))

(defmutation validate-set-command
  [{:keys [none]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (validate-set-command*))))))

(defn show-set-command-modal [comp id]
  (prim/transact! comp `[(prepare-set-command {:id ~id})
                         (r/set-route {:router :root/modal-router :target [:set-command-modal :singleton]})
                         (b/show-modal {:id :set-command-modal})]))

(defsc CommandPut [this {:keys [id name defaultValue min max value-type ui/value ui/selected]}]
  {:ident [:value-descriptor :id]
   :query [:id :name :defaultValue :min :max :value-type :ui/value :ui/selected]}
  (let [number? (or (= value-type "I") (= value-type "F"))
        have-max-min? (and number? (not (is-nan? min)) (not (is-nan? max)))
        out-of-range? (and have-max-min? (or (< (to-float value) (to-float min)) (> (to-float value) (to-float max))))
        invalid-number? (and number? (is-nan? value))
        validate #(prim/transact! this `[(validate-set-command {}) :set-command-modal])]
    (dom/div #js {:className "form-group"}
             (dom/input #js {:className "col-sm-1"
                             :type "checkbox"
                             :checked (or selected false)
                             :onChange (fn [evt] (let [new-value (.. evt -target -checked)]
                                                   (m/set-value! this :ui/selected new-value)
                                                   (validate)))})
             (dom/label #js {:className "col-sm-3 control-label"} name)
             (dom/div #js {:className "col-sm-7"}
                       (if (= value-type "B")
                         (dom/div #js {:className "form-check form-check-radio"}
                                  (dom/label nil
                                             (dom/input #js
                                                            {:className "form-check-input"
                                                             :type "radio",
                                                             :value "false"
                                                             :checked (= (or value defaultValue) "false")
                                                             :onChange (fn [evt]
                                                                         (let [new-value (.. evt -target -value)]
                                                                           (m/set-string! this :ui/value :value
                                                                                          new-value)
                                                                           (validate)))})
                                             "OFF")
                                  (dom/label nil
                                             (dom/input #js
                                                            {:className "form-check-input"
                                                             :type "radio",
                                                             :value "true"
                                                             :checked (= (or value defaultValue) "true")
                                                             :onChange (fn [evt]
                                                                         (let [new-value (.. evt -target -value)]
                                                                           (m/set-string! this :ui/value :value
                                                                                          new-value)
                                                                           (validate)))})
                                             "ON"))
                         (dom/div nil
                                  (when have-max-min?
                                    (dom/small #js {:className "form-text text-muted"} (str "min " min " max " max)))
                                  (dom/input #js
                                                 {:type "text",
                                                  :className "form-control"
                                                  :value (or value defaultValue)
                                                  :onChange (fn [evt]
                                                              (let [new-value (.. evt -target -value)]
                                                                (m/set-string! this :ui/value :value new-value)
                                                                (validate)))})
                                  (when out-of-range?
                                    (dom/label #js {:className b/text-danger} "Out of range"))
                                  (when invalid-number?
                                    (dom/label #js {:className b/text-danger} "Invalid"))))))))

(def ui-command-put (prim/factory CommandPut {:keyfn id/edgex-ident}))

(defsc CommandPuts [this {:keys [id pos name value-descriptors put]}]
  {:ident (fn [] [:command (str id "-" pos)])
   :query [:id :pos :name :put {:value-descriptors (prim/get-query CommandPut)}]})

(defsc SetCommandModal [this {:keys [valid values target modal modal/page]}]
  {:initial-state (fn [p] {:valid false
                           :modal (prim/get-initial-state b/Modal {:id :set-command-modal :backdrop true})
                           :modal/page :set-command-modal})
   :ident (fn [] [:set-command-modal :singleton])
   :query [:valid :values :modal/page
           {:target (prim/get-query CommandPuts)}
           {:modal (prim/get-query b/Modal)}]}
  (let [name (:name target)
        paramNames (into #{} (-> target :put :parameterNames))
        params (filter #(-> % :name paramNames) (:value-descriptors target))
        url (-> target :put :url)]
    (b/ui-modal modal
                (b/ui-modal-title nil
                                  (dom/div #js {:key "title"
                                                :style #js {:fontSize "22px"}} (str "Set " name)))
                (b/ui-modal-body nil
                                 (dom/div #js {:className "swal2-icon swal2-warning" :style #js {:display "block"}} "!")
                                 (dom/div #js {:className "card"}
                                          (dom/h4 nil name)
                                          (dom/div #js {:className "form-horizontal"}
                                                   (map ui-command-put params))))
                (b/ui-modal-footer nil
                                   (b/button {:key       "ok-button"
                                              :className "btn-fill"
                                              :kind      :info
                                              :disabled  (not valid)
                                              :onClick   #(prim/transact! this
                                                                          `[(b/hide-modal {:id :set-command-modal})
                                                                            (mu/issue-set-command {:url ~url
                                                                                                   :values ~values})])}
                                             "OK")
                                   (b/button {:key "cancel-button"
                                              :className "btn-fill"
                                              :kind :danger
                                              :onClick #(prim/transact! this `[(b/hide-modal {:id :set-command-modal})])}
                                             "Cancel")))))

(def ui-set-command-modal (prim/factory SetCommandModal))

(defsc CommandListEntry
       [this {:keys [id type pos size name value value-descriptors put]} {:keys [onSet]}]
       {:ident (fn [] [:command (str id "-" pos)])
        :query [:id :type :pos :size :name :value :put
                {:value-descriptors (prim/get-query CommandPut)}]}
       (let [[vdid val] value
             descriptor (-> (filter #(= (:name %) vdid) value-descriptors) first)
             label (:uomLabel descriptor)
             value-str (case (:value-type descriptor)
                         "I" (str val " " label)
                         "F" (str (do-format "%.2f" (to-float val)) " " label)
                         "B" (if (= val "true")
                               "ON"
                               "OFF")
                         val)
             set? (:parameterNames put)
             first? (= pos 0)]
         (dom/tr nil
                 (when first?
                   (dom/td {:rowSpan (str size)} name))
                 (dom/td nil vdid)
                 (dom/td nil value-str)
                 (dom/td
                   #js {:className "td-actions text-right"}
                   (when (and first? set?)
                     (dom/button {:type "button",
                                  :rel "tooltip",
                                  :title "Set Value",
                                  :className "btn btn-danger btn-simple btn-xs"
                                  :onClick #(onSet this (str id "-" pos))}
                                 (dom/i #js {:className "fa fa-edit"})))))))

(def ui-command-list-entry (prim/factory CommandListEntry {:keyfn (fn [{:keys [id pos]}] (str id "-" pos))}))

(defsc CommandList [this {:keys [ui/table-page-size ui/current-page ui/search-str ui/dropdown source-device commands
                                 ui/scm]}]
       {:initial-state (fn [p] {:ui/table-page-size 10 :ui/current-page 0 :ui/search-str ""
                                :ui/dropdown (b/dropdown :page-size-dropdown-command "10"
                                                         [(b/dropdown-item 10 "10")
                                                          (b/dropdown-item 50 "50")
                                                          (b/dropdown-item 100 "100")])
                                :source-device :none
                                :commands []
                                :ui/scm (prim/get-initial-state SetCommandModal {})})
        :ident (fn [] co/command-list-ident)
        :query (fn [] [:ui/table-page-size :ui/current-page :ui/search-str :source-device
                       {:ui/dropdown (prim/get-query b/Dropdown)}
                       {:commands (prim/get-query CommandListEntry)}
                       {:ui/scm (prim/get-query SetCommandModal)}])}
       (let [labels ["Name" "Id" "Value"]
             cmds-with-actions (fn [d] (ui-command-list-entry (prim/computed d {:onSet show-set-command-modal})))
             search-filter (fn [d] (st/includes? (st/lower-case (:name d)) (st/lower-case search-str)))
             filtered-commands (if (st/blank? search-str)
                                 commands
                                 (filter search-filter commands))
             entries (map cmds-with-actions filtered-commands)
             buttons [{:onClick #(routing/nav-to! this :main) :icon "caret-square-o-left"}
                      {:onClick #(df/refresh! this {:params {:id source-device} :fallback `d/show-error}) :icon "refresh"}]]
         (dom/div nil
                  (t/table this labels table-page-size current-page entries search-str buttons dropdown true))))
