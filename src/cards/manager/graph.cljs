;;; Copyright (c) 2018
;;; IoTech Ltd
;;; SPDX-License-Identifier: Apache-2.0

(ns org.edgexfoundry.ui.manager.graph
  (:require [devcards.core :as rc :refer-macros [defcard]]
            [fulcro.client.primitives :as prim :refer-macros [defsc]]
            [org.edgexfoundry.ui.manager.ui.components :as comp]
            [fulcro.client.dom :as dom]
            [thi.ng.geom.viz.core :as viz]
            [thi.ng.geom.svg.core :as svg]
            [thi.ng.geom.svg.adapter :as svgadapt]
            [thi.ng.color.core :as col]
            [sablono.core :refer [html]]))

(def test-data (into [] (take 500 (repeatedly #(rand-int 1000)))))

(defn format-k
  "Formats a number as rounded int using K, M or B suffixes
  (e.g. 25000 => 25K, 1000000 => 1M)"
  [x]
  (cond
    (>= x 1e9) (str (int (/ x 1e6)) "B")
    (>= x 1e6) (str (int (/ x 1e6)) "M")
    (>= x 1e3) (str (int (/ x 1e3)) "K")
    :else (str (int x))))

(defn chart [data]
  (let [len   (count data)
        min-v (reduce min data)
        max-v (reduce max data)
        maj-x (if (> len 200) 200 50)
        spec  {:x-axis (viz/linear-axis
                        {:domain      [0 len]
                         :range       [40 220]
                         :major       maj-x
                         :minor       (/ maj-x 2)
                         :pos         100
                         :label       (viz/default-svg-label int)
                         :label-style {:style {:font-size "7px"}}
                         :attribs     {:stroke-width "0.5px"}})
               :y-axis (viz/linear-axis
                        {:domain      [min-v max-v]
                         :range       [100 0]
                         :major       200
                         :minor       100
                         :pos         40
                         :label-dist  12
                         :label       (viz/default-svg-label format-k)
                         :label-style {:style {:font-size "7px"} :text-anchor "end"}
                         :label-y     3
                         :attribs     {:stroke-width "0.5px"}})
               :grid   {:attribs {:stroke "#ccc" :stroke-width "0.5px"}
                        :minor-y true}
               :data   [{:values  (map-indexed vector data)
                         :attribs {:fill "none" :stroke "#0af" :stroke-width "0.5px"}
                         :layout  viz/svg-line-plot}]}]
    (->> spec
         (viz/svg-plot2d-cartesian)
         (svg/svg
           {:width "100%" :viewBox "0 0 240 120"}
           (svg/linear-gradient
             "grad" {:gradientTransform "rotate(90)"}
             [0 (col/css "#f03")] [1 (col/css "#0af")]))
         (svgadapt/inject-element-attribs svgadapt/key-attrib-injector))))

(defsc Graph [this {:keys [data]}]
  (dom/div nil
       ;(dom/p nil (str data))
       (html (chart data))))

(def ui-graph (prim/factory Graph))

(defcard TestGraph
         (ui-graph {:data test-data}))
