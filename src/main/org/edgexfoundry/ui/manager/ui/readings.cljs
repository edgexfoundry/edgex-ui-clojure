;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.readings
  (:require [clojure.string :as str]
            [fulcro.client.primitives :as prim :refer [defui defsc]]
            [fulcro.i18n :refer [tr]]
            [fulcro.client.dom :as dom]
            [fulcro.client.data-fetch :as df :refer [load-field-action]]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [fulcro.ui.bootstrap3 :as b]
            [org.edgexfoundry.ui.manager.ui.table :as t :refer [deftable]]
            [org.edgexfoundry.ui.manager.ui.common :as co]
            [org.edgexfoundry.ui.manager.ui.ident :as id]
            [org.edgexfoundry.ui.manager.ui.graph :as gr]
            [org.edgexfoundry.ui.manager.ui.dialogs :as d]
            [org.edgexfoundry.ui.manager.ui.date-time-picker :as dtp]
            [clojure.set :as set]
            [goog.string :as gstring]
            [goog.object :as gobj]
            [cljs-time.coerce :as ct]
            [cljs-time.core :as tc]))

(defn to-float [s]
  (js/parseFloat s))

(defn do-format [fmt & args]
  (apply gstring/format fmt args))

(declare ReadingListEntry)

(defn pick-device* [state name]
  (let [device-name (get-in state (conj co/reading-list-ident :device-name))
        readings (->> state :reading vals (filter #(= (:device %) name)))
        attr-names (into #{} (map :name readings))
        attrs (into {} (map-indexed (fn [i v] [i {:idx i :name v :selected true}]) attr-names))
        attr-refs (into [] (map-indexed (fn [i v] [:attr i]) attr-names))]
    (if (not= device-name name)
      (-> state
          (assoc-in (conj co/reading-list-ident :device-name) name)
          (assoc :attr attrs)
          (assoc-in (conj co/reading-page-ident :ui/attr) attr-refs))
      state)))

(defmutation pick-device [{:keys [name]}]
  (action [{:keys [state]}]
          (swap! state pick-device* name))
  (refresh [env] [:reading-page]))

(defmutation load-readings-for-id [{:keys [name]}]
  (action [{:keys [state] :as env}]
          (let [start (get-in @state [:date-time-picker :readings-dtp-from :time])
                end (get-in @state [:date-time-picker :readings-dtp-to :time])]
            (df/load-action env :q/edgex-readings ReadingListEntry {:target (conj co/reading-list-ident :content)
                                                                    :params {:name name :from start :to end}
                                                                    :post-mutation `pick-device
                                                                    :post-mutation-params {:name name}
                                                                    :fallback `d/show-error})))
  (remote [env]
          (df/remote-load env)))

(defn show-readings-for-id [this devices id]
  (let [name (-> (filter #(= (:id %) id) devices) first :name)]
    (prim/transact! this `[(load-readings-for-id {:name ~name})
                           (m/set-props {:ui/current-page 0
                                         :ui/search-str ""
                                         :ui/lower-bound 0
                                         :ui/upper-bound 1.0})
                           (t/reset-table-page {:id :reading-page})])))

(defn refresh [this device-name]
  (prim/transact! this `[(load-readings-for-id {:name ~device-name})
                         (m/set-props {:ui/current-page 0
                                       :ui/search-str ""
                                       :ui/lower-bound 0
                                       :ui/upper-bound 1.0})]))

(defn auto-refresh [this device-name rate-str]
  (let [rate (js/parseInt rate-str)
        reload (fn []
                 (let [text-input (.call (gobj/get js/document "getElementById") js/document "rate")]
                   (prim/transact! this `[(load-readings-for-id {:name ~device-name})])
                   (js/console.log "text-input" text-input)
                   (if text-input
                     (auto-refresh this device-name (gobj/get text-input "value")))))]
    (if-not (js/isNaN rate)
      (js/setTimeout reload (* rate 1000)))))
                       

(defn toggle-selected* [state attr]
  (let [index (->> state :attr vals (filter #(= (:name %) attr)) first :idx)]
    (update-in state [:attr index :selected] not)))

(defmutation toggle-selected
  [{:keys [attr]}]
  (action [{:keys [state]}]
          (swap! state
                 (fn [s] (toggle-selected* s attr)))))

(defn select-show-attr [this attr]
  (prim/transact! this `[(toggle-selected {:attr ~attr})]))

(defn set-all* [state value]
  (let [set-val (fn [m idx] (assoc-in m [:attr idx :selected] value))]
    (reduce set-val state (-> state :attr keys))))

(defmutation set-all
  [{:keys [value]}]
  (action [{:keys [state]}]
          (swap! state
                 (fn [s] (set-all* s value)))))

(defn select-all [this]
  (prim/transact! this `[(set-all {:value true})]))

(defn deselect-all [this]
  (prim/transact! this `[(set-all {:value false})]))

(defsc ReadingsSearch [this {:keys [from-dtp to-dtp]}]
  {:initial-state (fn [p] {:from-dtp (prim/get-initial-state dtp/DateTimePicker {:id :readings-dtp-from
                                                                                 :time (-> (tc/time-now)
                                                                                           (tc/minus (tc/hours 1)))
                                                                                 :float-left true})
                           :to-dtp (prim/get-initial-state dtp/DateTimePicker {:id :readings-dtp-to
                                                                               :time (-> (tc/today-at-midnight)
                                                                                         (tc/plus (tc/days 1)))
                                                                               :float-left true})})
   :ident         (fn [] [:readings-search :singleton])
   :query         [{:from-dtp (prim/get-query dtp/DateTimePicker)}
                   {:to-dtp (prim/get-query dtp/DateTimePicker)}]}
  (dom/div #js {}
           (dtp/date-time-picker from-dtp)
           (dom/div #js {:style #js {:float "right" :margin "0 2px" :position "relative"}}
                    (dtp/date-time-picker to-dtp))))

(deftable ReadingList :show-readings :reading [[:created "Created" #(co/conv-time %2)] [:name "Name"] [:value "Value"]]
  [{:onClick #(refresh this (:device-name props)) :icon "refresh"}]
  :name-row-symbol ReadingListEntry
  :query [:device-name]
  :search {:comp ReadingsSearch})

(def ui-reading-list (prim/factory ReadingList))

(defsc DeviceData [this {:keys [id type name]}]
  {:ident [:device :id]
   :query [:id :type :name]})

(defsc AttrData [this {:keys [idx]}]
  {:ident [:attr :idx]
   :query [:idx :name :selected]})

(defsc ReadingsPage [this {:keys [ui/search-str ui/reading-list devices ui/attr ui/show-graph? ui/lower-bound ui/upper-bound
                                  ui/drag-start-pos ui/dragging? ui/lower-bound-start ui/upper-bound-start ui/refresh-rate]}]
  {:initial-state (fn [p] {:ui/search-str ""
                           :ui/reading-list (prim/get-initial-state ReadingList {})
                           :devices []
                           :ui/attr []
                           :ui/show-graph? false
                           :ui/lower-bound 0
                           :ui/upper-bound 1.0
                           :ui/drag-start-pos 0
                           :ui/lower-bound-start 0
                           :ui/upper-bound-start 0
                           :ui/dragging? false
                           :ui/refresh-rate ""})
   :ident         (fn [] co/reading-page-ident)
   :query         (fn [] [:ui/search-str
                          {:ui/reading-list (prim/get-query ReadingList)}
                          {:devices (prim/get-query DeviceData)}
                          {:ui/attr (prim/get-query AttrData)}
                          :ui/show-graph?
                          :ui/lower-bound
                          :ui/upper-bound
                          :ui/lower-bound-start
                          :ui/upper-bound-start
                          :ui/drag-start-pos
                          :ui/dragging?
                          :ui/refresh-rate])}
  (let [mk-opt (fn [i d] (dom/option #js {:value (-> d :id str) :key i
                                          :onClick #(show-readings-for-id this devices (:id d))} (:name d)))
        search-filter (fn [d] (str/includes? (str/lower-case (:name d)) (str/lower-case search-str)))
        filtered-devices (if (str/blank? search-str)
                           devices
                           (filter search-filter devices))
        sorted-filtered-devices (sort #(compare (:name %1) (:name %2)) filtered-devices)
        mk-li (fn [[name selected]] (dom/li {:key name :role "option" :aria-selected selected
                                             :onClick #(select-show-attr this name)} name))
        sorted-show-attr (sort #(compare (first %1) (first %2)) (map #(vector (:name %) (:selected %)) attr))
        selected-show-attr (mapv :name (filter :selected attr))
        show-attr (into #{} selected-show-attr)
        filtered-readings (update reading-list :content (fn [r] (filter #(-> % :name show-attr) r)))
        grouped-readings (group-by :name (:content filtered-readings))
        attr-names (mapv first sorted-show-attr)
        chart-data (fn [readings] (mapv (juxt :created #(-> % :value js/parseFloat)) readings))
        all-graph-data (reduce (fn [m [k v]] (assoc m k (chart-data v))) {} grouped-readings)
        graph-data (into {} (filter (fn [[k v]] (not (some #(-> % second js/isNaN) v))) all-graph-data))]
    (dom/div nil
             (dom/div #js {:className "form-group"}
                      (dom/div #js {:className "row"}
                               (dom/div #js {:className "col-md-7"}
                                        (dom/input #js {:type        "text",
                                                        :placeholder "Search",
                                                        :className   "form-control"
                                                        :value       search-str
                                                        :onChange    (fn [evt]
                                                                       (let [new-value (.. evt -target -value)]
                                                                         (m/set-string! this :ui/search-str :value new-value)
                                                                         (m/set-value! this :ui/current-page 0)))})
                                        (dom/select #js {:className "form-control" :multiple true :size 11}
                                                    (map-indexed mk-opt sorted-filtered-devices)))
                               (dom/div {:className "col-md-3"}
                                        (dom/ul
                                         {:role "listbox" :aria-labelledby "ms_av_l" :aria-multiselectable true}
                                         (map mk-li sorted-show-attr)))
                               (dom/div {:className "col-md-2"}
                                        (b/button {:as-block true
                                                   :style {:margin "2px 0"}
                                                   :onClick #(select-all this)} "Select All")
                                        (b/button {:as-block true
                                                   :style {:margin "2px 0"} :onClick #(deselect-all this)} "Deselect All")
                                        (b/button {:as-block true
                                                   :style {:margin "2px 0"} :onClick #(m/toggle! this :ui/show-graph?)}
                                                  (if show-graph? "Hide Graph" "Show Graph"))
                                        (comment (dom/input {:type "text",
                                                    :className   "form-control"
                                                    :style {:width "120px" :margin "2px 0"}
                                                    :value refresh-rate
                                                    :placeholder "Refresh rate"
                                                    :id "rate"
                                                    :onChange    (fn [evt]
                                                                   (let [new-value (.. evt -target -value)]
                                                                     (m/set-string! this :ui/refresh-rate :value new-value)
                                                                     (auto-refresh this (:device-name reading-list) new-value)))})))))
             ;(dom/p nil (str graph-data))
             (if (and show-graph? (seq graph-data))
               (dom/div {:style {:maxWidth "1000px"}}
                (gr/svg-graph this attr-names graph-data lower-bound upper-bound)
                (gr/svg-key attr-names graph-data)))
             (ui-reading-list filtered-readings))))
