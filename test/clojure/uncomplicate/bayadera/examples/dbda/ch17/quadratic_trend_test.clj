(ns ^{:author "Dragan Djuric"}
    uncomplicate.bayadera.examples.dbda.ch17.quadratic-trend-test
  (:require [midje.sweet :refer :all]
            [quil.core :as q]
            [quil.applet :as qa]
            [quil.middlewares.pause-on-error :refer [pause-on-error]]
            [uncomplicate.commons.core :refer [with-release let-release wrap-float]]
            [uncomplicate.fluokitten.core :refer [op fmap]]
            [uncomplicate.neanderthal
             [core :refer [dim]]
             [real :refer [entry entry!]]
             [math :refer [sqrt]]
             [native :refer [sv sge]]]
            [uncomplicate.bayadera
             [protocols :as p]
             [core :refer :all]
             [util :refer [bin-mapper hdi]]
             [opencl :refer [with-default-bayadera]]
             [mcmc :refer [mix! burn-in! pow-n acc-rate! info run-sampler!]]]
            [uncomplicate.bayadera.opencl.models
             :refer [cl-distribution-model cl-likelihood-model]]
            [uncomplicate.bayadera.toolbox
             [processing :refer :all]
             [plots :refer [render-sample render-histogram]]]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]))

(def all-data (atom {}))
(def state (atom nil))

(defn read-data [in-file]
  (loop [c 0 data (drop 1 (csv/read-csv in-file)) income (transient {})]
    (if data
      (let [[icm fsize st] (first data)]
        (recur (inc c) (next data)
               (let [icm-st (get income st (transient []))]
                 (assoc! income st
                        (-> icm-st
                            (conj! (double (read-string fsize)))
                            (conj! (double (read-string icm))))))))
      (let [persistent-income (into (sorted-map) (fmap persistent! (persistent! income)))
            subject-count (count persistent-income)]
        (apply op [subject-count] (map (fn [[k v]] (op [(count v)] v)) persistent-income))))))

(def params (sv (read-data (slurp (io/resource  "uncomplicate/bayadera/examples/dbda/ch17/income-famz-state.csv")))))

(def qt-prior
  (cl-distribution-model [(slurp (io/resource "uncomplicate/bayadera/opencl/distributions/gaussian.h"))
                          (slurp (io/resource "uncomplicate/bayadera/opencl/distributions/uniform.h"))
                          (slurp (io/resource "uncomplicate/bayadera/opencl/distributions/exponential.h"))
                          (slurp (io/resource "uncomplicate/bayadera/opencl/distributions/t.h"))
                          (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch17/quadratic-trend.h"))]
                         :name "qt" :mcmc-logpdf "qt_mcmc_logpdf" :params-size 315 :dimension 158))

(defn qt-likelihood [n]
  (cl-likelihood-model (slurp (io/resource "uncomplicate/bayadera/examples/dbda/ch17/quadratic-trend.h"))
                       :name "qt" :params-size n))

(defn analysis []
  (with-default-bayadera
    (with-release [prior (distribution qt-prior)
                   prior-dist (prior (sv (op [4 10000 20000] (take 312 (interleave (repeat 10000) (repeat 10000) (repeat 20000) (repeat 5000) (repeat -1000) (repeat 1000))))))
                   post (posterior "qt" (qt-likelihood (dim params)) prior-dist)
                   post-dist (post params)
                   post-sampler (sampler post-dist {:walkers (* 44 256) :limits (sge 2 158 (op [2 10 10000 20000] (take 312 (interleave (repeat 0) (repeat 2000) (repeat 10000) (repeat 30000) (repeat -2000) (repeat 0)))))})]
      (println (time (mix! post-sampler {:dimension-power 0.2 :cooling-schedule (pow-n 4)})))
      (println (info post-sampler))
      (println (time (do (burn-in! post-sampler 10000) (acc-rate! post-sampler))))
      (println (time (run-sampler! post-sampler 64)))
      (time (histogram! post-sampler 500)))))

(defn setup []
  (reset! state
          {:data @all-data
           :nu (plot2d (qa/current-applet) {:width 300 :height 300})
           :sigma (plot2d (qa/current-applet) {:width 300 :height 300})
           :betas (vec (repeatedly 50 (partial plot2d (qa/current-applet)
                                               {:width 300 :height 300})))}))

(defn draw []
  (when-not (= @all-data (:data @state))
    (swap! state assoc :data @all-data)
    (let [data @all-data]
      (q/background 0)
      (q/image (show (render-histogram (:nu @state) data 0)) 0 0)
      (q/image (show (render-histogram (:sigma @state) data 1)) 350 0)
      (dotimes [i 4]
        (dotimes [j 3]
          (let [index (+ (* i 3) j)]
            (when (< index 12)
              (q/image (show (render-histogram ((:betas @state) index) data (+ index 2)))
                       (* j 320) (+ 320 (* i 320))))))))))

(defn display-sketch []
  (q/defsketch diagrams
    :renderer :p2d
    :size :fullscreen
    :display 2
    :setup setup
    :draw draw
    :middleware [pause-on-error]))
