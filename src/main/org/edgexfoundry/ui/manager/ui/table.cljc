;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.table
  #?(:cljs (:require-macros org.edgexfoundry.ui.manager.ui.table))
  (:require
   #?(:clj [fulcro.client.dom-server :as dom]
      :cljs [fulcro.client.dom :as dom])
   [clojure.string :as str]
   [fulcro.i18n :refer [tr]]
   [fulcro.client.mutations :as m :refer [defmutation]]
   [fulcro.client.primitives :as prim :refer [defui]]
   [fulcro.ui.html-entities :as ent]
   [fulcro.ui.bootstrap3 :as b]
   [org.edgexfoundry.ui.manager.ui.ident :as id]))

(defmutation reset-table-page
             [{:keys [id]}]
             (action [{:keys [state]}]
                     (swap! state (fn [s] (assoc-in s [id :singleton :ui/current-page] 0)))))

(defn table [this labels page-size current-page entries search-text buttons dropdown has-actions? & options]
  (let [{:keys [extra-search sort-func sort-column sort-mode]} options
        mk-button #(b/button {:onClick (:onClick %)
                              :key (str "button-" (:icon %))
                              :style #js {:margin "0 2px"}}
                             (dom/i #js {:className (str "glyphicon fa fa-" (:icon %))
                                         :key (:icon %)}))
        total-data (count entries)
        num-pages (Math/ceil (/ total-data page-size))
        prev-page-disabled (= current-page 0)
        next-page-disabled (>= (inc current-page) num-pages)
        pentry (fn [i] (b/pagination-entry {:label (str (inc i))
                                            :key (str "pag" i)
                                            :active (= current-page i)
                                            :onClick #(m/set-value! this :ui/current-page i)}))
        number-of-buttons 9
        surround-buttons (quot number-of-buttons 2)
        lower-button (max 0 (min (- current-page surround-buttons) (- num-pages number-of-buttons -1)))
        upper-button (min num-pages (max (+ current-page surround-buttons 1) number-of-buttons))
        page-buttons (map pentry (range lower-button upper-button))
        lower-bound (* current-page page-size)
        upper-bound (min (* (inc  current-page) page-size) total-data)
        display-entries (subvec (vec entries) lower-bound upper-bound)
        action (if has-actions?
                 [(dom/th #js {:key "action" :className "text-right"} "Action")]
                 [])
        sort-classes (if sort-func "sortable both" "")]
    (b/container-fluid nil
      (b/row nil
        (dom/div #js {:className "card"}
          (dom/div #js {:className "fixed-table-toolbar"}
            (dom/div #js {:className "bars pull-right"}
                     (mapv mk-button buttons))
            (dom/div #js {:className "pull-left search"}
                     (dom/div nil
                              (dom/div #js {:className "pull-left"}
                                       (dom/input #js
                                                      {:type "text",
                                                       :placeholder "Search",
                                                       :className "form-control"
                                                       :value search-text
                                                       :onChange (fn [evt]
                                                                   (let [new-value (.. evt -target -value)]
                                                                     (m/set-string! this :ui/search-str :value new-value)
                                                                     (m/set-value! this :ui/current-page 0)))}))
                              (when extra-search
                                (dom/div #js {:style #js {:float "right" :margin "0 2px" :position "relative"}}
                                         extra-search)))))
                 (dom/div #js {:className "fixed-table-container"}
                          (dom/div
                            #js {:className "table-responsive"}
                            (dom/table
                              #js {:className "table table-striped table-bordered"}
                              (dom/thead
                                #js {}
                                (dom/tr #js {}
                                        (concat
                                          (map (fn [lbl] (dom/th #js {:key lbl :onClick #(sort-func lbl)}
                                                                 (dom/div #js {:className
                                                                               (cond-> sort-classes
                                                                                       (and (= lbl sort-column) (= sort-mode :ascending))
                                                                                       (str " asc")
                                                                                       (and (= lbl sort-column) (= sort-mode :descending))
                                                                                       (str " desc"))} lbl))) labels)
                                          action)))
                              (dom/tbody nil display-entries))))
          (if (empty? entries)
            (dom/h5 nil "No data")
            (dom/div
              #js {:className "fixed-table-pagination"}
              (dom/div
                #js {:className "pull-left pagination-detail"}
                (dom/span
                  #js {:className "page-list"}
                  (dom/span #js {:className "pagination-info"})
                  (dom/span
                    #js {:className "page-list"}
                    (b/ui-dropdown dropdown :stateful? true :onSelect #(do
                                                                         (m/set-value! this :ui/table-page-size %)
                                                                         (m/set-value! this :ui/current-page 0)))
                    " rows visible")))
              (dom/div
                #js {:className "pull-right pagination"}
                (b/pagination {}
                              (concat [(b/pagination-entry
                                         {:label "First" :disabled prev-page-disabled :key "first"
                                          :onClick #(m/set-value! this :ui/current-page 0)})
                                       (b/pagination-entry
                                         {:label ent/laqao :disabled prev-page-disabled :key "prev"
                                          :onClick #(m/set-value! this :ui/current-page (dec current-page))})]
                                      page-buttons
                                      [(b/pagination-entry
                                         {:label ent/raqao, :disabled next-page-disabled :key "next"
                                          :onClick #(m/set-value! this :ui/current-page (inc current-page))})
                                       (b/pagination-entry
                                         {:label "Last", :disabled next-page-disabled :key "last"
                                          :onClick #(m/set-value! this :ui/current-page (dec num-pages))})]))))))))))

(defn row [name value] (dom/tr nil
                               (dom/th nil name)
                               (dom/td #js {:colSpan "2"} value)))

(defn subrow [name value] (dom/tr nil
                                  (dom/th nil name)
                                  (dom/td nil value)))

(defn build-button [action]
  (let [{:keys [title action-class symbol onClick]} action
        button-cls (get {:info    "info"
                         :success "success"
                         :danger  "danger"} action-class)
        classes (str "btn btn-simple btn-xs btn-" button-cls)
        image-classes (str "fa fa-" symbol)]
    `(fulcro.client.dom/button
      {:type      "button",
       :rel       "tooltip",
       :title     ~title,
       :className ~classes
       :onClick   #((get (fulcro.client.primitives/get-computed ~'this) ~title)
                    ~'this (get ~'props :type) (get ~'props :id))}
       (fulcro.client.dom/i {:className ~image-classes}))))

(defn str-conv [p v]
  (str v))

#?(:clj
(defn build-entry-sc [sym type columns actions]
  (let [column-syms (mapv #(-> % first name symbol) columns)
        keys (into `[~'id ~'type] column-syms)
        query-keys (into [:id :type] (mapv first columns))
        td-fn (fn [c]
                (let [conv-fn (if (= (count c) 3)
                                (last c)
                                'org.edgexfoundry.ui.manager.ui.table.str-conv)]
                  `(fulcro.client.dom/td (~conv-fn ~'props (get ~'props ~(first c))))))
        action-entries (if (seq actions)
                         `(fulcro.client.dom/td {:className "td-actions text-right"}
                            ~@(map build-button actions)))
        body `(fulcro.client.dom/tr nil
                      ~@(map td-fn columns) ~action-entries)]
    `(fulcro.client.primitives/defui ~(with-meta sym {:once true})
       ~'static fulcro.client.primitives/IQuery (~'query [~'this] ~query-keys)
       ~'static fulcro.client.primitives/Ident (~'ident [~'this ~'props] [~type (:id ~'props)])
       ~'Object
           (~'render [~'this]
             (let [~'props (fulcro.client.primitives/props ~'this)]
               ~body))))))

(defn next-sort [mode]
  (condp = mode
    :none :ascending
    :ascending :descending
    :descending :none))

(defn toggle-sort* [state id col]
  (-> state
      (assoc-in [id :singleton :ui/sort-column] col)
      (update-in [id :singleton :ui/sort-mode] next-sort)
      (assoc-in [id :singleton :ui/current-page] 0)))

(defmutation toggle-sort
  [{:keys [id col]}]
  (action [{:keys [state]}]
          (swap! state (fn [s] (-> s
                                   (toggle-sort* id col))))))

(defn mk-sort-toggle [comp id]
  (fn [col]
    (prim/transact! comp `[(toggle-sort {:id ~id :col ~col})])))

#?(:clj
(defn build-table-ui [sym ident columns actions buttons search-keyword modals entry-symbol extra-search query]
  (let [router-tag-keyword (keyword "ui" (name ident))
        page-size-keyword (-> "page-size" gensym str keyword)
        labels (mapv second columns)
        search-keyword (or search-keyword :name)
        modal-id #(str "modal-" %)
        mk-modal-keyword #(keyword "ui" (modal-id %))
        init-modal (fn [index modal] `[~(mk-modal-keyword index)
                                       (fulcro.client.primitives/get-initial-state ~(:modal modal)
                                                                                   ~(:params modal))])
        init-modals (into {} (map-indexed init-modal modals))
        query-modal (fn [index modal] `{~(mk-modal-keyword index)
                                        (fulcro.client.primitives/get-query ~(:modal modal))})
        query-modals (map-indexed query-modal modals)
        modal-vars (map-indexed (fn [index modal] (->> index modal-id (str "ui/") symbol)) modals)
        modal-fn (fn [m] `(fulcro.client.primitives/factory ~(:modal m)))
        invoke-modal (fn [indexed modal] `(~(modal-fn modal) (~'fulcro.client.primitives/computed
                                                               ~(->> indexed modal-id symbol)
                                                               ~(:callbacks modal))))
        invoke-modals (map-indexed invoke-modal modals)
        init-search (fn [search] `{:ui/extra-search (fulcro.client.primitives/get-initial-state ~(:comp search)
                                                                                                ~(:params search))})
        query-search (fn [search] `[{:ui/extra-search (fulcro.client.primitives/get-query ~(:comp search))}])
        search-var (when extra-search (seq ['ui/extra-search]))
        comp-fn (fn [s] `(fulcro.client.primitives/factory ~(:comp s)))
        invoke-search (fn [search] `(~(comp-fn search) (~'fulcro.client.primitives/computed
                                                         ~'extra-search ~(:callbacks search))))
        has-actions? (-> actions empty? not)
        fn-pair #(vector (:title %) (:onClick %))
        action-map (into {} (map fn-pair actions))
        col-map (into {} (map (fn [col] [(second col) (first col)]) columns))]
        `(fulcro.client.primitives/defui ~(with-meta sym {:once true})
           ~'static fulcro.client.primitives/IQuery
           (~'query [~'this] [:ui/table-page-size :ui/current-page :ui/search-str :ui/sort-column :ui/sort-mode
                              ~router-tag-keyword
                              {:ui/dropdown (fulcro.client.primitives/get-query fulcro.ui.bootstrap3/Dropdown)}
                              {:content (fulcro.client.primitives/get-query ~entry-symbol)}
                              ~@query-modals
                              ~@query
                              ~@(when extra-search (query-search extra-search))])
           ~'static fulcro.client.primitives/Ident (~'ident [~'this ~'props] [~ident :singleton])
           ~'static fulcro.client.primitives/InitialAppState
           (~'initial-state [~'class ~'params]
             (conj {:ui/table-page-size 10 :ui/current-page 0 :ui/search-str "" :ui/sort-column "" :ui/sort-mode :none
                    ~router-tag-keyword true
                    :ui/dropdown (fulcro.ui.bootstrap3/dropdown ~page-size-keyword "10"
                                                                [(fulcro.ui.bootstrap3/dropdown-item 10 "10")
                                                                 (fulcro.ui.bootstrap3/dropdown-item 50 "50")
                                                                 (fulcro.ui.bootstrap3/dropdown-item 100 "100")])
                    :content []}
                   ~init-modals
                   ~(when extra-search (init-search extra-search))))
           ~'Object
           (~'render [~'this]
             (let [{:keys [~'ui/table-page-size ~'ui/current-page ~'ui/search-str ~'ui/dropdown
                           ~'ui/sort-mode ~'ui/sort-column ~@modal-vars
                           ~@search-var ~'content]
                    :as ~'props}
                   (fulcro.client.primitives/props ~'this)
                   ~'search-filter (fn [~'d] (str/includes? (str/lower-case (~search-keyword ~'d))
                                                            (str/lower-case ~'search-str)))
                   ~'filtered-content (if (str/blank? ~'search-str)
                                        ~'content
                                        (filter ~'search-filter ~'content))
                   ~'column-map ~col-map
                   ~'sorted-content (cond->> ~'filtered-content
                                             (not= ~'sort-mode :none) (sort-by (get ~'column-map ~'sort-column))
                                             (= ~'sort-mode :ascending) reverse)
                   ~'sort-fn (org.edgexfoundry.ui.manager.ui.table/mk-sort-toggle ~'this ~ident)
                   ~'entry-fn (prim/factory ~entry-symbol {:keyfn id/edgex-ident})
                   ~'content-with-actions (fn [~'c] (~'entry-fn (prim/computed ~'c ~action-map)))
                   ~'entries (map ~'content-with-actions ~'sorted-content)]
               (fulcro.client.dom/div nil
                        (table ~'this ~labels ~'table-page-size ~'current-page ~'entries ~'search-str ~buttons
                               ~'dropdown ~has-actions? :sort-func ~'sort-fn :sort-column ~'sort-column
                               :sort-mode ~'sort-mode
                               ~@(when extra-search (seq [:extra-search (invoke-search extra-search)]))))))))))

#?(:clj
(defmacro deftable [sym ident type columns buttons & options]
  (let [{:keys [actions search-keyword modals search row-symbol name-row-symbol query]} options
        entry-symbol (or row-symbol name-row-symbol (gensym "Entry"))]
    `(do
       ~(when-not row-symbol (build-entry-sc entry-symbol type columns actions))
       ~(build-table-ui sym ident columns actions buttons search-keyword modals entry-symbol search query)))))
