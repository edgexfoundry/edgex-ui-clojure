;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.ui.graph
  (:require [fulcro.client.primitives :as prim :refer-macros [defsc]]
            [fulcro.client.dom :as dom]
            [fulcro.client.mutations :as m :refer [defmutation]]
            [thi.ng.geom.viz.core :as viz]
            [thi.ng.geom.svg.core :as svg]
            [thi.ng.geom.svg.adapter :as svgadapt]
            [thi.ng.geom.line :as l]
            [thi.ng.color.core :as col]
            [thi.ng.color.presets.categories :as cat]
            [sablono.core :refer [html]]
            [clojure.set :as set]
            [goog.object :as gobj]
            [org.edgexfoundry.ui.manager.ui.common :as co]))

(defn format-k
  "Formats a number as rounded int using K, M or B suffixes
  (e.g. 25000 => 25K, 1000000 => 1M)"
  [x]
  (cond
    (>= x 1e9) (str (int (/ x 1e6)) "B")
    (>= x 1e6) (str (int (/ x 1e6)) "M")
    (>= x 1e3) (str (int (/ x 1e3)) "K")
    (< x 10) (/ (js/Math.round (* x 10)) 10)
    :else (str (int x))))

(defn nth-colour [n] (->> n (get cat/cat20) col/int24 col/as-css))

(defn limit [pos] (max 0 (min pos 1)))

(defn scale-pos [pos] (limit (* (- pos 0.2) 1.25)))

(defn zoom* [state zoom-in? position]
  (let [zoom-factor 0.1
        position (scale-pos position)
        lower-bound (get-in state (conj co/reading-page-ident :ui/lower-bound))
        upper-bound (get-in state (conj co/reading-page-ident :ui/upper-bound))
        adj (* zoom-factor (- upper-bound lower-bound))
        lower-adj (* adj position)
        upper-adj (* adj (- position 1))
        new-lower (limit (if zoom-in?
                           (+ lower-bound lower-adj)
                           (- lower-bound lower-adj)))
        new-upper (limit (if zoom-in?
                           (+ upper-bound upper-adj)
                           (- upper-bound upper-adj)))]
    (-> state
        (update-in co/reading-page-ident #(merge % {:ui/lower-bound new-lower
                                                    :ui/upper-bound new-upper})))))

(defmutation zoom [{:keys [zoom-in? position]}]
  (action [{:keys [state]}]
          (swap! state zoom* zoom-in? position)))

(defn drag* [state position]
  (let [position (scale-pos position)
        start-pos (get-in state (conj co/reading-page-ident :ui/drag-start-pos))
        lower-bound (get-in state (conj co/reading-page-ident :ui/lower-bound-start))
        upper-bound (get-in state (conj co/reading-page-ident :ui/upper-bound-start))
        dragging? (get-in state (conj co/reading-page-ident :ui/dragging?))
        delta (- start-pos position)
        adj (max (min (* delta (- upper-bound lower-bound)) (- 1 upper-bound)) (- lower-bound))
        new-lower (limit (+ lower-bound adj))
        new-upper (limit (+ upper-bound adj))]
    (cond-> state
      dragging? (update-in co/reading-page-ident #(merge % {:ui/lower-bound new-lower
                                                            :ui/upper-bound new-upper})))))

(defmutation drag [{:keys [position]}]
  (action [{:keys [state]}]
          (swap! state drag* position)))

(defn start-drag* [state position]
  (let [position (scale-pos position)
        lower-bound (get-in state (conj co/reading-page-ident :ui/lower-bound))
        upper-bound (get-in state (conj co/reading-page-ident :ui/upper-bound))]
    (-> state
        (update-in co/reading-page-ident #(merge % {:ui/drag-start-pos position
                                                    :ui/lower-bound-start lower-bound
                                                    :ui/upper-bound-start upper-bound
                                                    :ui/dragging? true})))))

(defmutation start-drag [{:keys [position]}]
  (action [{:keys [state]}]
          (swap! state start-drag* position)))

(defn stop-drag* [state]
  (-> state
      (assoc-in (conj co/reading-page-ident :ui/dragging?) false)))

(defmutation stop-drag [noparams]
  (action [{:keys [state]}]
          (swap! state stop-drag*)))

(defn to-pos [evt]
  (let [svg (.call (gobj/get js/document "getElementById") js/document "graph")
        p (.call (gobj/get svg "createSVGPoint") svg)
        bb (.call (gobj/get svg "getBBox") svg)
        width (gobj/get bb "width")]
    (gobj/set p "x" (gobj/get evt  "clientX"))
    (gobj/set p "y" (gobj/get evt "clientY"))
    (let [ctm (.call (gobj/get svg "getScreenCTM") svg)
          inv (.call (gobj/get ctm "inverse") ctm)
          coord (.call (gobj/get p "matrixTransform") p inv)
          x (gobj/get coord "x")]
        (/ x width))))

(defn mouse-zoom [this evt]
  (let [zoom-in? (> 0 (gobj/get evt "deltaY"))
        position (to-pos evt)]
    (prim/transact! this `[(zoom {:zoom-in? ~zoom-in? :position ~position})])))

(defn mouse-move [this evt]
  (let [position (to-pos evt)]
    (prim/transact! this `[(drag {:position ~position})])))

(defn mouse-down [this evt]
  (let [position (to-pos evt)]
    (prim/transact! this `[(start-drag {:position ~position})])))

(defn mouse-up [this]
  (prim/transact! this `[(stop-drag {})]))

(defn chart [this keys graph-data lower-bound upper-bound]
  (let [cap-data (into {} (take (count cat/cat20) graph-data))
        len (-> graph-data first second count)
        values (mapcat (fn [[k v]] (map second v)) cap-data)
        times (mapcat (fn [[k v]] (map first v)) cap-data)
        near10 #(-> % (/ 10) js/Math.round (* 10))
        min-v (reduce min values)
        max-v (reduce max values)
        adj-min-v (if (> min-v 0) 0 min-v)
        adj-max-v (+ (* 0.1 (- max-v adj-min-v)) max-v)
        delta-v (/ (- adj-max-v adj-min-v) 10)
        min-t (reduce min times)
        max-t (reduce max times)
        delta-t (- max-t min-t)
        rel-t #(+ min-t (* delta-t %))
        lower-t (rel-t lower-bound)
        upper-t (rel-t upper-bound)
        maj-x (if (> len 200) 200 50)
        mk-data (fn [[k v]] {:values v
                             :attribs {:fill "none" :stroke (->> k (.indexOf keys) nth-colour) :stroke-width "0.5px"}
                             :layout viz/svg-line-plot})
        spec  {:x-axis (viz/linear-axis
                         {:domain      [lower-t upper-t]
                          :range       [40 220]
                          :major       (max (/ (- upper-t lower-t) 2) 1)
                          :pos         100
                          :label       (viz/default-svg-label co/conv-time)
                          :label-style {:style {:font-size "5px"}}
                          :label-dist  15
                          :attribs     {:stroke-width "0.5px"}})
               :y-axis (viz/linear-axis
                         {:domain      [adj-min-v adj-max-v]
                          :range       [100 0]
                          :major       (max (if (< delta-v 10) (js/Math.round delta-v) (near10 delta-v)) 0.1)
                          :pos         40
                          :label-dist  12
                          :label       (viz/default-svg-label format-k)
                          :label-style {:style {:font-size "5px"} :text-anchor "end"}
                          :label-y     3
                          :attribs     {:stroke-width "0.5px"}})
               :grid   {:attribs {:stroke "#ccc" :stroke-width "0.5px"}}
               :data   (map mk-data cap-data)}
        prevent-default #(.call (gobj/get % "preventDefault") %)
        plot-with-background (fn [spec] [(svg/rect [0 0] 240 120 {:fill "white"})
                                         (viz/svg-plot2d-cartesian spec)])]
    (->> spec
         (plot-with-background)
         (svg/svg
          {:width "100%" :viewBox "0 0 240 120" :id "graph"
           :on-wheel (fn [evt]
                       (mouse-zoom this evt)
                       (prevent-default evt))
           :on-mouse-move (fn [evt]
                            (mouse-move this evt)
                            (prevent-default evt))
           :on-mouse-up (fn [evt]
                          (mouse-up this)
                          (prevent-default evt))
           :on-mouse-down (fn [evt]
                            (mouse-down this evt)
                            (prevent-default evt))}
          (svg/linear-gradient
           "grad" {:gradientTransform "rotate(90)"}
           [0 (col/css "#f03")] [1 (col/css "#0af")]))
         (svgadapt/inject-element-attribs svgadapt/key-attrib-injector))))

(defn fixup [svg]
  (update svg 1 #(set/rename-keys % {"xmlns:xlink" "xmlnsXlink"})))

(defn svg-graph [this keys data lower-bound upper-bound]
  (let [svg (-> (chart this keys data lower-bound upper-bound) fixup)]
    (dom/div nil
             (html svg))))

(defn mk-key [names data]
  (let [height (-> data count (* 25))
        mk-key-row (fn [i k]
                     (let [y (+ 20 (* 25 i))
                           ty (- y 5)]
                       [(svg/text [10 y] k {:key k})
                        (svg/line [200 ty] [380 ty] {:key (str k "-col") :stroke (->> k (.indexOf names) nth-colour)})]))]
    (->> (map-indexed mk-key-row (-> data keys sort))
         (svg/svg {:width 400 :height height :font-family "Arial" :font-size 12}
                  (svg/rect [0 0] 400 height {:fill "white"}))
         (svgadapt/all-as-svg))))

(defn svg-key [keys data]
  (dom/div nil
           (html (-> (mk-key keys data) fixup))))
